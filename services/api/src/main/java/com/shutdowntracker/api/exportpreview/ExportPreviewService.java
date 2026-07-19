package com.shutdowntracker.api.exportpreview;

import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.requireNonNull;

import com.shutdowntracker.api.audit.AuditEventCategory;
import com.shutdowntracker.api.audit.AuditEventCreateRequest;
import com.shutdowntracker.api.audit.AuditEventRecorder;
import com.shutdowntracker.api.audit.AuditEventTypes;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class ExportPreviewService {

    private static final String CREATED_MESSAGE = "Export preview created. "
            + "No MSPDI/XML artifact was generated and no Microsoft Project write-back was run.";
    private static final String READ_MESSAGE = "Export preview only. "
            + "No MSPDI/XML artifact was generated and no Microsoft Project write-back was run.";
    private static final String APPROVED_MESSAGE = "Export batch approved for controlled MSPDI/XML generation. "
            + "No file was generated and no Microsoft Project write-back was run.";
    private static final String REJECTED_MESSAGE = "Export batch rejected. "
            + "No MSPDI/XML artifact was generated and no Microsoft Project write-back was run.";
    private static final String GENERATED_MESSAGE = "Export batch marked generated from reviewed artifact metadata. "
            + "No Microsoft Project write-back was run.";
    private static final String OPENED_IN_PROJECT_MESSAGE = "Export artifact marked opened in Microsoft Project "
            + "for manual verification. No Microsoft Project write-back was run.";
    private static final String VERIFIED_MESSAGE = "Export artifact manually verified after Microsoft Project reopen. "
            + "No Microsoft Project write-back was run.";
    private static final String LEGACY_BATCH_CONFLICT = "This export batch predates the current integrity policy "
            + "and is read-only history. Create a fresh export preview.";
    private static final String UNSUPPORTED_POLICY_CONFLICT = "This export batch uses an unsupported integrity policy "
            + "and cannot progress. Create a fresh export preview under the current policy.";
    private static final String UNSEALED_BATCH_CONFLICT = "This export preview does not have a sealed line set "
            + "and cannot progress. Create a fresh export preview.";

    private final ExportPreviewRepository repository;
    private final AuditEventRecorder auditEventRecorder;

    public ExportPreviewService(ExportPreviewRepository repository, AuditEventRecorder auditEventRecorder) {
        this.repository = repository;
        this.auditEventRecorder = auditEventRecorder;
    }

    @Transactional
    public ExportPreviewDetail createPreview(UUID projectId, ExportPreviewCreateRequest request) {
        UUID requiredProjectId = requireNonNull(projectId, "projectId is required.");
        ExportPreviewCreateRequest requiredRequest = requireNonNull(request, "request is required.");
        List<ExportCandidateRecord> candidates = requiredRequest.lines().stream()
                .map(line -> requireCurrentCandidateAuthority(
                        requiredProjectId,
                        requiredRequest.projectSnapshotId(),
                        line.authoritativeExportCandidateId()
                ))
                .toList();
        requireUniqueTaskFieldCandidates(candidates);

        ExportPreviewBatchRecord batch;
        try {
            batch = repository.createDraftPreview(
                    requiredProjectId,
                    requiredRequest.projectSnapshotId(),
                    requiredRequest.metadata()
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }

        try {
            for (ExportCandidateRecord candidate : candidates) {
                repository.createLine(
                        requiredProjectId,
                        requiredRequest.projectSnapshotId(),
                        batch.id(),
                        candidate.id()
                );
            }
        } catch (DataIntegrityViolationException | IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Authoritative export candidate authority changed while the preview was being created. "
                            + "Review the source and create a fresh export preview.",
                    exception
            );
        }

        boolean lineSetSealed;
        try {
            lineSetSealed = repository.sealDraftPreviewLineSet(requiredProjectId, batch.id());
        } catch (DataIntegrityViolationException exception) {
            throw databaseIntegrityConflict("Export preview sealing", exception);
        }
        if (!lineSetSealed) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Export preview line membership could not be sealed. Create a fresh export preview."
            );
        }

        ExportPreviewDetail detail = getPreview(requiredProjectId, batch.id(), CREATED_MESSAGE);
        auditEventRecorder.record(AuditEventCreateRequest.systemEvent(
                requiredProjectId,
                AuditEventCategory.EXPORT,
                AuditEventTypes.EXPORT_PREVIEW_CREATED,
                "export_batch",
                detail.batch().id(),
                "Draft export preview",
                Map.of("status", "none"),
                Map.of("status", detail.batch().status().databaseValue()),
                CREATED_MESSAGE,
                detail.batch().projectSnapshotId(),
                detail.batch().id(),
                previewMetadata(detail)
        ));

        return detail;
    }

    @Transactional(readOnly = true)
    public ExportPreviewDetail getPreview(UUID projectId, UUID exportBatchId) {
        return getPreview(projectId, exportBatchId, READ_MESSAGE);
    }

    @Transactional
    public ExportPreviewDetail approveBatch(
            UUID projectId,
            UUID exportBatchId,
            ExportBatchDecisionRequest request
    ) {
        UUID requiredProjectId = requireNonNull(projectId, "projectId is required.");
        UUID requiredExportBatchId = requireNonNull(exportBatchId, "exportBatchId is required.");
        ExportBatchDecisionRequest requiredRequest = request == null ? ExportBatchDecisionRequest.empty() : request;
        lockBatchForIntegrityValidation(requiredProjectId, requiredExportBatchId);
        ExportPreviewDetail preview = getPreview(requiredProjectId, requiredExportBatchId, READ_MESSAGE);
        ExportPreviewBatchRecord existing = preview.batch();

        if (existing.status() != ExportBatchState.DRAFT_PREVIEW) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only draft preview export batches can be approved."
            );
        }
        requireCurrentPolicy(existing);
        requireSealedLineSet(existing);
        List<ExportPreviewLineRecord> eligibleLines = revalidateCandidateIntegrity(requiredProjectId, preview);
        if (eligibleLines.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only export batches with at least one eligible line can be approved."
            );
        }

        ExportPreviewBatchRecord updated;
        try {
            updated = repository
                    .approveBatch(
                            requiredProjectId,
                            requiredExportBatchId,
                            requiredRequest.reviewedByUserId(),
                            decisionMetadata(requiredRequest)
                    )
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Export batch approval could not be recorded because the batch is no longer draft preview."
                    ));
        } catch (DataIntegrityViolationException exception) {
            throw databaseIntegrityConflict("Export batch approval", exception);
        }

        ExportPreviewDetail detail = getPreview(requiredProjectId, updated.id(), APPROVED_MESSAGE);
        recordExportBatchAudit(
                requiredProjectId,
                existing,
                detail,
                AuditEventTypes.EXPORT_BATCH_APPROVED,
                auditReason(requiredRequest.reason(), APPROVED_MESSAGE),
                false
        );
        return detail;
    }

    @Transactional
    public ExportPreviewDetail rejectBatch(
            UUID projectId,
            UUID exportBatchId,
            ExportBatchDecisionRequest request
    ) {
        UUID requiredProjectId = requireNonNull(projectId, "projectId is required.");
        UUID requiredExportBatchId = requireNonNull(exportBatchId, "exportBatchId is required.");
        ExportBatchDecisionRequest requiredRequest = request == null ? ExportBatchDecisionRequest.empty() : request;
        ExportPreviewBatchRecord existing = findBatch(requiredProjectId, requiredExportBatchId);

        if (existing.status() != ExportBatchState.DRAFT_PREVIEW) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only draft preview export batches can be rejected."
            );
        }
        requireCurrentPolicy(existing);
        requireSealedLineSet(existing);

        ExportPreviewBatchRecord updated = repository
                .rejectBatch(requiredProjectId, requiredExportBatchId, decisionMetadata(requiredRequest))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Export batch rejection could not be recorded because the batch is no longer draft preview."
                ));

        ExportPreviewDetail detail = getPreview(requiredProjectId, updated.id(), REJECTED_MESSAGE);
        recordExportBatchAudit(
                requiredProjectId,
                existing,
                detail,
                AuditEventTypes.EXPORT_BATCH_REJECTED,
                auditReason(requiredRequest.reason(), REJECTED_MESSAGE),
                false
        );
        return detail;
    }

    @Transactional
    public ExportPreviewDetail markGenerated(
            UUID projectId,
            UUID exportBatchId,
            ExportBatchGeneratedRequest request
    ) {
        UUID requiredProjectId = requireNonNull(projectId, "projectId is required.");
        UUID requiredExportBatchId = requireNonNull(exportBatchId, "exportBatchId is required.");
        ExportBatchGeneratedRequest requiredRequest = requireNonNull(request, "request is required.");
        lockBatchForIntegrityValidation(requiredProjectId, requiredExportBatchId);
        ExportPreviewDetail preview = getPreview(requiredProjectId, requiredExportBatchId, READ_MESSAGE);
        ExportPreviewBatchRecord existing = preview.batch();

        if (existing.status() != ExportBatchState.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only approved export batches can be marked generated."
            );
        }
        requireCurrentPolicy(existing);
        requireSealedLineSet(existing);
        requireEligibleCandidates(requiredProjectId, preview);

        ExportPreviewBatchRecord updated;
        try {
            updated = repository
                    .markBatchGenerated(
                            requiredProjectId,
                            requiredExportBatchId,
                            requiredRequest.exportFileUri(),
                            requiredRequest.exportFileHash(),
                            requiredRequest.generatedByUserId(),
                            generatedMetadata(requiredRequest)
                    )
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Export batch generation could not be recorded because the batch is no longer approved."
                    ));
        } catch (DataIntegrityViolationException exception) {
            throw databaseIntegrityConflict("Export batch generation", exception);
        }

        ExportPreviewDetail detail = getPreview(requiredProjectId, updated.id(), GENERATED_MESSAGE);
        recordExportBatchAudit(
                requiredProjectId,
                existing,
                detail,
                AuditEventTypes.EXPORT_FILE_GENERATED,
                auditReason(requiredRequest.reason(), GENERATED_MESSAGE),
                true
        );
        return detail;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ExportPreviewDetail getApprovedPreviewForArtifactGeneration(UUID projectId, UUID exportBatchId) {
        UUID requiredProjectId = requireNonNull(projectId, "projectId is required.");
        UUID requiredExportBatchId = requireNonNull(exportBatchId, "exportBatchId is required.");
        lockBatchForIntegrityValidation(requiredProjectId, requiredExportBatchId);
        ExportPreviewDetail preview = getPreview(requiredProjectId, requiredExportBatchId, READ_MESSAGE);
        if (preview.batch().status() != ExportBatchState.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only approved export batches can request worker artifact generation."
            );
        }
        requireCurrentPolicy(preview.batch());
        requireSealedLineSet(preview.batch());
        requireEligibleCandidates(requiredProjectId, preview);
        return preview;
    }

    @Transactional
    public ExportPreviewDetail markOpenedInMicrosoftProject(
            UUID projectId,
            UUID exportBatchId,
            ExportBatchProjectOpenRequest request
    ) {
        UUID requiredProjectId = requireNonNull(projectId, "projectId is required.");
        UUID requiredExportBatchId = requireNonNull(exportBatchId, "exportBatchId is required.");
        ExportBatchProjectOpenRequest requiredRequest = requireNonNull(request, "request is required.");
        ExportPreviewBatchRecord existing = findBatch(requiredProjectId, requiredExportBatchId);

        if (existing.status() != ExportBatchState.GENERATED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only generated export batches can be marked opened in Microsoft Project."
            );
        }
        requireCurrentPolicy(existing);
        requireSealedLineSet(existing);

        ExportPreviewBatchRecord updated = repository
                .markBatchOpenedInMicrosoftProject(
                        requiredProjectId,
                        requiredExportBatchId,
                        openedInMicrosoftProjectMetadata(requiredRequest)
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Export batch open-in-Project metadata could not be recorded because the batch is no longer generated."
                ));

        ExportPreviewDetail detail = getPreview(requiredProjectId, updated.id(), OPENED_IN_PROJECT_MESSAGE);
        recordExportBatchAudit(
                requiredProjectId,
                existing,
                detail,
                AuditEventTypes.EXPORT_FILE_OPENED_IN_MICROSOFT_PROJECT,
                auditReason(requiredRequest.reason(), OPENED_IN_PROJECT_MESSAGE),
                true,
                Map.of("openedInMicrosoftProject", true, "artifactVerified", false)
        );
        return detail;
    }

    @Transactional
    public ExportPreviewDetail verifyBatch(
            UUID projectId,
            UUID exportBatchId,
            ExportBatchVerificationRequest request
    ) {
        UUID requiredProjectId = requireNonNull(projectId, "projectId is required.");
        UUID requiredExportBatchId = requireNonNull(exportBatchId, "exportBatchId is required.");
        ExportBatchVerificationRequest requiredRequest = requireNonNull(request, "request is required.");
        ExportPreviewBatchRecord existing = findBatch(requiredProjectId, requiredExportBatchId);

        if (existing.status() != ExportBatchState.OPENED_IN_MICROSOFT_PROJECT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only export batches opened in Microsoft Project can be verified."
            );
        }
        requireCurrentPolicy(existing);
        requireSealedLineSet(existing);

        ExportPreviewBatchRecord updated = repository
                .markBatchVerified(
                        requiredProjectId,
                        requiredExportBatchId,
                        requiredRequest.verifiedByUserId(),
                        verificationMetadata(requiredRequest)
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Export batch verification could not be recorded because the batch is no longer opened in Microsoft Project."
                ));

        ExportPreviewDetail detail = getPreview(requiredProjectId, updated.id(), VERIFIED_MESSAGE);
        recordExportBatchAudit(
                requiredProjectId,
                existing,
                detail,
                AuditEventTypes.EXPORT_FILE_VERIFIED,
                auditReason(requiredRequest.reason(), VERIFIED_MESSAGE),
                true,
                Map.of("openedInMicrosoftProject", true, "artifactVerified", true)
        );
        return detail;
    }

    private ExportPreviewDetail getPreview(UUID projectId, UUID exportBatchId, String message) {
        UUID requiredProjectId = requireNonNull(projectId, "projectId is required.");
        UUID requiredExportBatchId = requireNonNull(exportBatchId, "exportBatchId is required.");
        ExportPreviewBatchRecord batch = findBatch(requiredProjectId, requiredExportBatchId);
        List<ExportPreviewLineRecord> lines = repository.listLines(requiredProjectId, requiredExportBatchId);
        return new ExportPreviewDetail(batch, lines, message);
    }

    private ExportCandidateRecord requireCurrentCandidateAuthority(
            UUID projectId,
            UUID projectSnapshotId,
            UUID authoritativeExportCandidateId
    ) {
        ExportCandidateRecord candidate = repository
                .findAuthoritativeCandidate(projectId, authoritativeExportCandidateId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Authoritative export candidate is missing. Review the source and create a fresh export preview."
                ));

        if (!ExportIntegrityPolicy.isCurrent(candidate.bindingPolicyVersion())
                || !Objects.equals(candidate.projectId(), projectId)
                || !Objects.equals(candidate.projectSnapshotId(), projectSnapshotId)
                || !Objects.equals(candidate.id(), authoritativeExportCandidateId)
                || candidate.approvalState() != ApprovalState.APPROVED_FOR_EXPORT
                || candidate.sourceEventOrPayloadHash() == null
                || !candidate.sourceEventOrPayloadHash().matches("[0-9a-f]{64}")
                || !hasText(candidate.capturedTaskExternalUid())
                || !hasText(candidate.capturedTaskExternalId())
                || !hasText(candidate.capturedTaskName())) {
            throw staleCandidate(authoritativeExportCandidateId);
        }
        if (!repository.lockAcceptedSnapshotForIntegrityValidation(projectId, projectSnapshotId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The export candidate snapshot is no longer an accepted baseline. Create a fresh export preview."
            );
        }

        ExportPreviewField field;
        try {
            field = ExportPreviewField.fromFieldName(candidate.fieldName());
            if (!Objects.equals(candidate.normalizedNewValue(), field.normalizeValue(candidate.normalizedNewValue()))) {
                throw staleCandidate(authoritativeExportCandidateId);
            }
        } catch (IllegalArgumentException exception) {
            throw staleCandidate(authoritativeExportCandidateId);
        }

        ExportPreviewTaskContext task = repository
                .findTaskContext(projectId, projectSnapshotId, candidate.importedTaskId())
                .orElseThrow(() -> staleCandidate(authoritativeExportCandidateId));
        if (!Objects.equals(task.id(), candidate.importedTaskId())
                || !Objects.equals(task.projectId(), candidate.projectId())
                || !Objects.equals(task.projectSnapshotId(), candidate.projectSnapshotId())
                || !Objects.equals(task.externalUid(), candidate.capturedTaskExternalUid())
                || !Objects.equals(task.externalId(), candidate.capturedTaskExternalId())
                || !Objects.equals(task.name(), candidate.capturedTaskName())
                || task.leafTask() != candidate.capturedLeafTask()
                || !Objects.equals(field.oldValue(task), candidate.normalizedOldValue())) {
            throw staleCandidate(authoritativeExportCandidateId);
        }

        ExportPreviewApprovalRecord currentApproval = requireCurrentApproval(
                repository.findCurrentApprovalCandidates(
                        projectId,
                        candidate.sourceEntityType(),
                        candidate.sourceEntityId()
                ),
                candidate.sourceEntityId()
        );
        if (!Objects.equals(candidate.approvalRecordId(), currentApproval.id())
                || candidate.approvalState() != currentApproval.approvalState()
                || !Objects.equals(candidate.id(), currentApproval.authoritativeExportCandidateId())
                || !ExportIntegrityPolicy.isCurrent(currentApproval.candidateBindingPolicyVersion())) {
            throw staleCandidate(authoritativeExportCandidateId);
        }

        return candidate;
    }

    private void requireUniqueTaskFieldCandidates(List<ExportCandidateRecord> candidates) {
        Set<CandidateKey> candidateKeys = new HashSet<>();
        for (ExportCandidateRecord candidate : candidates) {
            CandidateKey candidateKey = new CandidateKey(candidate.importedTaskId(), candidate.fieldName());
            if (!candidateKeys.add(candidateKey)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Duplicate authoritative candidates for imported task "
                                + candidate.importedTaskId()
                                + " and field "
                                + candidate.fieldName()
                                + ". Create a fresh export preview."
                );
            }
        }
    }

    private void requireEligibleCandidates(UUID projectId, ExportPreviewDetail preview) {
        if (revalidateCandidateIntegrity(projectId, preview).isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Export batch has no currently eligible candidates. Review the sources and create a fresh export preview."
            );
        }
    }

    private List<ExportPreviewLineRecord> revalidateCandidateIntegrity(
            UUID projectId,
            ExportPreviewDetail preview
    ) {
        requireCurrentPolicy(preview.batch());
        Set<CandidateKey> candidates = new HashSet<>();
        for (ExportPreviewLineRecord line : preview.lines()) {
            if (!ExportIntegrityPolicy.isCurrent(line.integrityPolicyVersion())) {
                throw staleCandidate(line);
            }
            if (!Objects.equals(line.exportBatchId(), preview.batch().id())
                    || !Objects.equals(line.projectId(), projectId)
                    || !Objects.equals(line.projectSnapshotId(), preview.batch().projectSnapshotId())) {
                throw staleCandidate(line);
            }
            if (line.authoritativeExportCandidateId() == null) {
                throw staleCandidate(line);
            }
            ExportCandidateRecord authoritativeCandidate = requireCurrentCandidateAuthority(
                    projectId,
                    preview.batch().projectSnapshotId(),
                    line.authoritativeExportCandidateId()
            );
            CandidateKey candidateKey = new CandidateKey(
                    authoritativeCandidate.importedTaskId(),
                    authoritativeCandidate.fieldName()
            );
            if (!candidates.add(candidateKey)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Export preview contains duplicate candidates for imported task "
                                + authoritativeCandidate.importedTaskId()
                                + " and field "
                                + authoritativeCandidate.fieldName()
                                + ". Create a fresh export preview."
                );
            }
            ExportPreviewField field = ExportPreviewField.fromFieldName(authoritativeCandidate.fieldName());
            boolean currentlyEligible = authoritativeCandidate.approvalState() == ApprovalState.APPROVED_FOR_EXPORT
                    && authoritativeCandidate.capturedLeafTask()
                    && field.mvpExportAuthorized();

            if (!Objects.equals(line.importedTaskId(), authoritativeCandidate.importedTaskId())
                    || !Objects.equals(line.importedTaskExternalUid(), authoritativeCandidate.capturedTaskExternalUid())
                    || !Objects.equals(line.importedTaskExternalId(), authoritativeCandidate.capturedTaskExternalId())
                    || !Objects.equals(line.importedTaskName(), authoritativeCandidate.capturedTaskName())
                    || !Objects.equals(line.sourceEntityType(), authoritativeCandidate.sourceEntityType())
                    || !Objects.equals(line.sourceEntityId(), authoritativeCandidate.sourceEntityId())
                    || !Objects.equals(line.sourceApprovalRecordId(), authoritativeCandidate.approvalRecordId())
                    || line.approvalState() != authoritativeCandidate.approvalState()
                    || !Objects.equals(line.fieldName(), authoritativeCandidate.fieldName())
                    || !Objects.equals(line.oldValue(), authoritativeCandidate.normalizedOldValue())
                    || !Objects.equals(line.newValue(), authoritativeCandidate.normalizedNewValue())
                    || !Objects.equals(
                            line.capturedSourceEventOrPayloadHash(),
                            authoritativeCandidate.sourceEventOrPayloadHash()
                    )
                    || !Objects.equals(line.sourceActorUserId(), authoritativeCandidate.sourceActorUserId())
                    || !Objects.equals(line.sourceTimestamp(), authoritativeCandidate.sourceTimestamp())
                    || !Objects.equals(line.reason(), authoritativeCandidate.reason())
                    || line.leafTask() != authoritativeCandidate.capturedLeafTask()
                    || line.exportEligible() != currentlyEligible) {
                throw staleCandidate(line);
            }
        }

        return preview.lines().stream()
                .filter(ExportPreviewLineRecord::exportEligible)
                .toList();
    }

    private ExportPreviewApprovalRecord requireCurrentApproval(
            List<ExportPreviewApprovalRecord> candidates,
            UUID sourceEntityId
    ) {
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Export source " + sourceEntityId
                            + " has no current approval record. Record a new approval event and create a fresh export preview."
            );
        }
        if (candidates.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Export source " + sourceEntityId
                            + " has ambiguous legacy approval authority. Record a new approval event and create a fresh export preview."
            );
        }
        return candidates.getFirst();
    }

    private void requireCurrentPolicy(ExportPreviewBatchRecord batch) {
        if (batch.integrityPolicyVersion() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, LEGACY_BATCH_CONFLICT);
        }
        if (!ExportIntegrityPolicy.isCurrent(batch.integrityPolicyVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, UNSUPPORTED_POLICY_CONFLICT);
        }
    }

    private void requireSealedLineSet(ExportPreviewBatchRecord batch) {
        if (!Boolean.TRUE.equals(batch.lineSetSealed())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, UNSEALED_BATCH_CONFLICT);
        }
    }

    private void lockBatchForIntegrityValidation(UUID projectId, UUID exportBatchId) {
        if (!repository.lockBatchForIntegrityValidation(projectId, exportBatchId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Export preview batch not found.");
        }
    }

    private ResponseStatusException staleCandidate(ExportPreviewLineRecord line) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Export preview candidate authority changed for imported task "
                        + line.importedTaskId()
                        + " and field "
                        + line.fieldName()
                        + ". Review the source and create a fresh export preview."
        );
    }

    private ResponseStatusException staleCandidate(UUID authoritativeExportCandidateId) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Authoritative export candidate "
                        + authoritativeExportCandidateId
                        + " no longer matches its approval, source, accepted baseline, task identity, field, or value. "
                        + "Review the source and create a fresh export preview."
        );
    }

    private ResponseStatusException databaseIntegrityConflict(
            String operation,
            DataIntegrityViolationException exception
    ) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                operation + " was blocked because the database detected stale candidate or accepted-baseline authority. "
                        + "Review the source and create a fresh export preview.",
                exception
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record CandidateKey(UUID importedTaskId, String fieldName) {
    }

    private Map<String, Object> previewMetadata(ExportPreviewDetail detail) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("lineCount", detail.batch().lineCount());
        metadata.put("eligibleLineCount", detail.batch().eligibleLineCount());
        metadata.put("ineligibleLineCount", detail.batch().ineligibleLineCount());
        metadata.put("artifactGenerated", false);
        metadata.put("projectWriteBack", false);
        return metadata;
    }

    private Map<String, Object> decisionMetadata(ExportBatchDecisionRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>(request.metadata());
        if (request.reviewedByUserId() != null) {
            metadata.put("reviewedByUserId", request.reviewedByUserId().toString());
        }
        if (request.reason() != null && !request.reason().isBlank()) {
            metadata.put("reason", request.reason());
        }
        metadata.put("projectWriteBack", false);
        return metadata;
    }

    private Map<String, Object> generatedMetadata(ExportBatchGeneratedRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>(request.metadata());
        if (request.generatedByUserId() != null) {
            metadata.put("generatedByUserId", request.generatedByUserId().toString());
        }
        if (request.reason() != null && !request.reason().isBlank()) {
            metadata.put("reason", request.reason());
        }
        metadata.put("exportFileUri", request.exportFileUri());
        metadata.put("exportFileHash", request.exportFileHash());
        metadata.put("artifactGenerated", true);
        metadata.put("projectWriteBack", false);
        return metadata;
    }

    private Map<String, Object> openedInMicrosoftProjectMetadata(ExportBatchProjectOpenRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>(request.metadata());
        metadata.put("openedByUserId", request.openedByUserId().toString());
        if (request.reason() != null && !request.reason().isBlank()) {
            metadata.put("reason", request.reason());
        }
        metadata.put("artifactGenerated", true);
        metadata.put("openedInMicrosoftProject", true);
        metadata.put("artifactVerified", false);
        metadata.put("projectWriteBack", false);
        return metadata;
    }

    private Map<String, Object> verificationMetadata(ExportBatchVerificationRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>(request.metadata());
        metadata.put("verifiedByUserId", request.verifiedByUserId().toString());
        if (request.reason() != null && !request.reason().isBlank()) {
            metadata.put("reason", request.reason());
        }
        metadata.put("artifactGenerated", true);
        metadata.put("openedInMicrosoftProject", true);
        metadata.put("artifactVerified", true);
        metadata.put("projectWriteBack", false);
        return metadata;
    }

    private String auditReason(String requestedReason, String fallback) {
        if (requestedReason != null && !requestedReason.isBlank()) {
            return requestedReason;
        }
        return fallback;
    }

    private void recordExportBatchAudit(
            UUID projectId,
            ExportPreviewBatchRecord existing,
            ExportPreviewDetail detail,
            String eventType,
            String message,
            boolean artifactGenerated
    ) {
        recordExportBatchAudit(projectId, existing, detail, eventType, message, artifactGenerated, Map.of());
    }

    private void recordExportBatchAudit(
            UUID projectId,
            ExportPreviewBatchRecord existing,
            ExportPreviewDetail detail,
            String eventType,
            String message,
            boolean artifactGenerated,
            Map<String, Object> metadataOverrides
    ) {
        auditEventRecorder.record(AuditEventCreateRequest.systemEvent(
                projectId,
                AuditEventCategory.EXPORT,
                eventType,
                "export_batch",
                detail.batch().id(),
                "Export batch",
                Map.of("status", existing.status().databaseValue()),
                newValueSummary(detail.batch()),
                message,
                detail.batch().projectSnapshotId(),
                detail.batch().id(),
                lifecycleMetadata(detail, artifactGenerated, metadataOverrides)
        ));
    }

    private Map<String, Object> newValueSummary(ExportPreviewBatchRecord batch) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", batch.status().databaseValue());
        if (batch.exportFileUri() != null) {
            summary.put("exportFileUri", batch.exportFileUri());
        }
        if (batch.exportFileHash() != null) {
            summary.put("exportFileHash", batch.exportFileHash());
        }
        if (batch.verifiedAt() != null) {
            summary.put("verifiedAt", batch.verifiedAt().toString());
        }
        if (batch.verifiedByUserId() != null) {
            summary.put("verifiedByUserId", batch.verifiedByUserId().toString());
        }
        return summary;
    }

    private Map<String, Object> lifecycleMetadata(ExportPreviewDetail detail, boolean artifactGenerated) {
        return lifecycleMetadata(detail, artifactGenerated, Map.of());
    }

    private Map<String, Object> lifecycleMetadata(
            ExportPreviewDetail detail,
            boolean artifactGenerated,
            Map<String, Object> metadataOverrides
    ) {
        Map<String, Object> metadata = previewMetadata(detail);
        metadata.put("artifactGenerated", artifactGenerated);
        metadata.put("projectWriteBack", false);
        if (detail.batch().exportFileUri() != null) {
            metadata.put("exportFileUri", detail.batch().exportFileUri());
        }
        if (detail.batch().exportFileHash() != null) {
            metadata.put("exportFileHash", detail.batch().exportFileHash());
        }
        if (detail.batch().verifiedAt() != null) {
            metadata.put("verifiedAt", detail.batch().verifiedAt().toString());
        }
        if (detail.batch().verifiedByUserId() != null) {
            metadata.put("verifiedByUserId", detail.batch().verifiedByUserId().toString());
        }
        metadata.putAll(metadataOverrides);
        return metadata;
    }

    private ExportPreviewBatchRecord findBatch(UUID projectId, UUID exportBatchId) {
        return repository.findBatch(projectId, exportBatchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Export preview batch not found."));
    }
}
