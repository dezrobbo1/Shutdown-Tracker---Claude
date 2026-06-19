package com.shutdowntracker.api.exportpreview;

import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.requireNonNull;

import java.util.List;
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

    private final ExportPreviewRepository repository;

    public ExportPreviewService(ExportPreviewRepository repository) {
        this.repository = repository;
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

        return getPreview(requiredProjectId, batch.id(), CREATED_MESSAGE);
    }

    @Transactional(readOnly = true)
    public ExportPreviewDetail getPreview(UUID projectId, UUID exportBatchId) {
        return getPreview(projectId, exportBatchId, READ_MESSAGE);
    }

    private ExportPreviewDetail getPreview(UUID projectId, UUID exportBatchId, String message) {
        UUID requiredProjectId = requireNonNull(projectId, "projectId is required.");
        UUID requiredExportBatchId = requireNonNull(exportBatchId, "exportBatchId is required.");
        ExportPreviewBatchRecord batch = repository.findBatch(requiredProjectId, requiredExportBatchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Export preview batch not found."));
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
}
