package com.shutdowntracker.api.exportpreview;

import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.requireNonNull;

import com.shutdowntracker.api.audit.AuditEventCategory;
import com.shutdowntracker.api.audit.AuditEventCreateRequest;
import com.shutdowntracker.api.audit.AuditEventRecorder;
import com.shutdowntracker.api.audit.AuditEventTypes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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

        for (ExportPreviewLineCreateRequest line : requiredRequest.lines()) {
            repository.createLine(
                    requiredProjectId,
                    requiredRequest.projectSnapshotId(),
                    batch.id(),
                    materializeLine(requiredProjectId, requiredRequest.projectSnapshotId(), line)
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
        ExportPreviewBatchRecord existing = findBatch(requiredProjectId, requiredExportBatchId);

        if (existing.status() != ExportBatchState.DRAFT_PREVIEW) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only draft preview export batches can be approved."
            );
        }
        if (existing.eligibleLineCount() == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only export batches with at least one eligible line can be approved."
            );
        }

        ExportPreviewBatchRecord updated = repository
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
        ExportPreviewBatchRecord existing = findBatch(requiredProjectId, requiredExportBatchId);

        if (existing.status() != ExportBatchState.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only approved export batches can be marked generated."
            );
        }

        ExportPreviewBatchRecord updated = repository
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

    private ExportPreviewDetail getPreview(UUID projectId, UUID exportBatchId, String message) {
        UUID requiredProjectId = requireNonNull(projectId, "projectId is required.");
        UUID requiredExportBatchId = requireNonNull(exportBatchId, "exportBatchId is required.");
        ExportPreviewBatchRecord batch = findBatch(requiredProjectId, requiredExportBatchId);
        List<ExportPreviewLineRecord> lines = repository.listLines(requiredProjectId, requiredExportBatchId);
        return new ExportPreviewDetail(batch, lines, message);
    }

    private ExportPreviewMaterializedLine materializeLine(
            UUID projectId,
            UUID projectSnapshotId,
            ExportPreviewLineCreateRequest line
    ) {
        ExportPreviewTaskContext task = repository
                .findTaskContext(projectId, projectSnapshotId, line.importedTaskId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Imported task not found for export preview."
                ));

        ExportPreviewField field = ExportPreviewField.fromFieldName(line.fieldName());
        ApprovalState approvalState = repository
                .findLatestApprovalState(projectId, line.sourceEntityType(), line.sourceEntityId())
                .orElse(null);
        boolean exportEligible = approvalState == ApprovalState.APPROVED_FOR_EXPORT && task.leafTask();

        return new ExportPreviewMaterializedLine(
                line.importedTaskId(),
                line.sourceEntityType(),
                line.sourceEntityId(),
                approvalState,
                field.fieldName(),
                field.oldValue(task),
                line.newValue(),
                line.sourceActorUserId(),
                line.sourceTimestamp(),
                line.reason(),
                task.leafTask(),
                exportEligible,
                line.metadata()
        );
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
                lifecycleMetadata(detail, artifactGenerated)
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
        return summary;
    }

    private Map<String, Object> lifecycleMetadata(ExportPreviewDetail detail, boolean artifactGenerated) {
        Map<String, Object> metadata = previewMetadata(detail);
        metadata.put("artifactGenerated", artifactGenerated);
        metadata.put("projectWriteBack", false);
        if (detail.batch().exportFileUri() != null) {
            metadata.put("exportFileUri", detail.batch().exportFileUri());
        }
        if (detail.batch().exportFileHash() != null) {
            metadata.put("exportFileHash", detail.batch().exportFileHash());
        }
        return metadata;
    }

    private ExportPreviewBatchRecord findBatch(UUID projectId, UUID exportBatchId) {
        return repository.findBatch(projectId, exportBatchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Export preview batch not found."));
    }
}
