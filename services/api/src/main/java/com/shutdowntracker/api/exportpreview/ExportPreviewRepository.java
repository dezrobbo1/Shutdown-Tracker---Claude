package com.shutdowntracker.api.exportpreview;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ExportPreviewRepository {

    ExportPreviewBatchRecord createDraftPreview(UUID projectId, UUID projectSnapshotId, Map<String, Object> metadata);

    boolean sealDraftPreviewLineSet(UUID projectId, UUID exportBatchId);

    boolean lockBatchForIntegrityValidation(UUID projectId, UUID exportBatchId);

    Optional<ExportPreviewBatchRecord> findBatch(UUID projectId, UUID exportBatchId);

    Optional<ExportPreviewBatchRecord> approveBatch(
            UUID projectId,
            UUID exportBatchId,
            UUID approvedByUserId,
            Map<String, Object> metadata
    );

    Optional<ExportPreviewBatchRecord> rejectBatch(
            UUID projectId,
            UUID exportBatchId,
            Map<String, Object> metadata
    );

    Optional<ExportPreviewBatchRecord> markBatchGenerated(
            UUID projectId,
            UUID exportBatchId,
            String exportFileUri,
            String exportFileHash,
            UUID generatedByUserId,
            Map<String, Object> metadata
    );

    Optional<ExportPreviewBatchRecord> markBatchOpenedInMicrosoftProject(
            UUID projectId,
            UUID exportBatchId,
            Map<String, Object> metadata
    );

    Optional<ExportPreviewBatchRecord> markBatchVerified(
            UUID projectId,
            UUID exportBatchId,
            UUID verifiedByUserId,
            Map<String, Object> metadata
    );

    Optional<ExportPreviewTaskContext> findTaskContext(UUID projectId, UUID projectSnapshotId, UUID importedTaskId);

    boolean lockAcceptedSnapshotForIntegrityValidation(UUID projectId, UUID projectSnapshotId);

    Optional<ExportCandidateRecord> findAuthoritativeCandidate(
            UUID projectId,
            UUID authoritativeExportCandidateId
    );

    List<ExportPreviewApprovalRecord> findCurrentApprovalCandidates(
            UUID projectId,
            String sourceEntityType,
            UUID sourceEntityId
    );

    ExportPreviewLineRecord createLine(
            UUID projectId,
            UUID projectSnapshotId,
            UUID exportBatchId,
            UUID authoritativeExportCandidateId
    );

    List<ExportPreviewLineRecord> listLines(UUID projectId, UUID exportBatchId);
}
