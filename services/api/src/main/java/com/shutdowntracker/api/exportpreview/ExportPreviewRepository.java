package com.shutdowntracker.api.exportpreview;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ExportPreviewRepository {

    ExportPreviewBatchRecord createDraftPreview(UUID projectId, UUID projectSnapshotId, Map<String, Object> metadata);

    Optional<ExportPreviewBatchRecord> findBatch(UUID projectId, UUID exportBatchId);

    Optional<ExportPreviewTaskContext> findTaskContext(UUID projectId, UUID projectSnapshotId, UUID importedTaskId);

    Optional<ApprovalState> findLatestApprovalState(UUID projectId, String sourceEntityType, UUID sourceEntityId);

    ExportPreviewLineRecord createLine(
            UUID projectId,
            UUID projectSnapshotId,
            UUID exportBatchId,
            ExportPreviewMaterializedLine line
    );

    List<ExportPreviewLineRecord> listLines(UUID projectId, UUID exportBatchId);
}
