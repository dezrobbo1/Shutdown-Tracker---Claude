package com.shutdowntracker.api.exportpreview;

import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.requireNonNull;

import com.shutdowntracker.api.audit.AuditEventCategory;
import com.shutdowntracker.api.audit.AuditEventCreateRequest;
import com.shutdowntracker.api.audit.AuditEventRecorder;
import com.shutdowntracker.api.audit.AuditEventTypes;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class ExportCandidateService {

    private static final String CREATED_MESSAGE = "Authoritative export candidate created for review. "
            + "It is not approved, no artifact was generated, and no Microsoft Project write-back was run.";
    private static final String APPROVAL_RECORDED_MESSAGE = "Candidate approval event recorded. "
            + "No export batch was approved, no artifact was generated, and no Microsoft Project write-back was run.";

    private final ExportPreviewRepository repository;
    private final AuditEventRecorder auditEventRecorder;

    public ExportCandidateService(ExportPreviewRepository repository, AuditEventRecorder auditEventRecorder) {
        this.repository = repository;
        this.auditEventRecorder = auditEventRecorder;
    }

    @Transactional
    public ExportCandidateRecord createCandidate(UUID projectId, ExportCandidateCreateRequest request) {
        UUID requiredProjectId = requireNonNull(projectId, "projectId is required.");
        ExportCandidateCreateRequest requiredRequest = requireNonNull(request, "request is required.");

        String normalizedProposedValue;
        try {
            normalizedProposedValue = ExportPreviewField.fromFieldName(requiredRequest.fieldName())
                    .normalizeValue(requiredRequest.proposedValue());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }

        ExportCandidateRecord candidate;
        try {
            candidate = repository.createAuthoritativeCandidate(
                    requiredProjectId,
                    requiredRequest,
                    normalizedProposedValue
            );
        } catch (DataIntegrityViolationException | IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Candidate creation requires an accepted snapshot, a matching imported task, and immutable source provenance.",
                    exception
            );
        }

        auditEventRecorder.record(AuditEventCreateRequest.systemEvent(
                requiredProjectId,
                AuditEventCategory.EXPORT,
                AuditEventTypes.EXPORT_CANDIDATE_CREATED,
                "export_candidate",
                candidate.id(),
                candidate.capturedTaskName(),
                Map.of("status", "none"),
                candidateSummary(candidate),
                CREATED_MESSAGE,
                candidate.projectSnapshotId(),
                null,
                authorityMetadata(candidate)
        ));
        return candidate;
    }

    @Transactional
    public ExportCandidateApprovalEventRecord recordApprovalEvent(
            UUID projectId,
            UUID candidateId,
            ExportCandidateApprovalEventRequest request
    ) {
        UUID requiredProjectId = requireNonNull(projectId, "projectId is required.");
        UUID requiredCandidateId = requireNonNull(candidateId, "candidateId is required.");
        ExportCandidateApprovalEventRequest requiredRequest = requireNonNull(request, "request is required.");

        ExportCandidateApprovalEventRecord event;
        try {
            event = repository.createCandidateApprovalEvent(
                            requiredProjectId,
                            requiredCandidateId,
                            requiredRequest
                    )
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Authoritative export candidate not found."
                    ));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Candidate approval authority changed while the event was being recorded.",
                    exception
            );
        }

        auditEventRecorder.record(AuditEventCreateRequest.systemEvent(
                requiredProjectId,
                AuditEventCategory.EXPORT,
                AuditEventTypes.EXPORT_CANDIDATE_APPROVAL_RECORDED,
                "export_candidate",
                requiredCandidateId,
                "Authoritative export candidate",
                Map.of("approvalState", "not_recorded_by_this_event"),
                Map.of(
                        "approvalRecordId", event.id().toString(),
                        "approvalState", event.approvalState().databaseValue()
                ),
                requiredRequest.reason() == null || requiredRequest.reason().isBlank()
                        ? APPROVAL_RECORDED_MESSAGE
                        : requiredRequest.reason(),
                event.projectSnapshotId(),
                null,
                approvalMetadata(event)
        ));
        return event;
    }

    private Map<String, Object> candidateSummary(ExportCandidateRecord candidate) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("fieldName", candidate.fieldName());
        summary.put("normalizedNewValue", candidate.normalizedNewValue());
        summary.put("approvalState", "not_approved");
        return summary;
    }

    private Map<String, Object> authorityMetadata(ExportCandidateRecord candidate) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("bindingPolicyVersion", candidate.bindingPolicyVersion());
        metadata.put("projectSnapshotId", candidate.projectSnapshotId().toString());
        metadata.put("importedTaskId", candidate.importedTaskId().toString());
        metadata.put("sourceEntityType", candidate.sourceEntityType());
        metadata.put("sourceEntityId", candidate.sourceEntityId().toString());
        metadata.put("sourceVersion", candidate.sourceVersion());
        metadata.put("sourceEventOrPayloadHash", candidate.sourceEventOrPayloadHash());
        metadata.put("projectWriteBack", false);
        metadata.put("artifactGenerated", false);
        return metadata;
    }

    private Map<String, Object> approvalMetadata(ExportCandidateApprovalEventRecord event) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("candidateBindingPolicyVersion", event.candidateBindingPolicyVersion());
        metadata.put("authoritativeExportCandidateId", event.authoritativeExportCandidateId().toString());
        metadata.put("projectWriteBack", false);
        metadata.put("artifactGenerated", false);
        return metadata;
    }
}
