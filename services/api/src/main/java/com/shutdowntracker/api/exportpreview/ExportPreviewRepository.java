package com.shutdowntracker.api.exportpreview;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ExportPreviewRepository {

    default ExportCandidateRecord createAuthoritativeCandidate(
            UUID projectId,
            ExportCandidateCreateRequest request,
            String normalizedProposedValue
    ) {
        throw new UnsupportedOperationException("Authoritative candidate creation is not implemented.");
    }

    default Optional<ExportCandidateApprovalEventRecord> createCandidateApprovalEvent(
            UUID projectId,
            UUID authoritativeExportCandidateId,
            ExportCandidateApprovalEventRequest request
    ) {
        throw new UnsupportedOperationException("Candidate approval-event creation is not implemented.");
    }

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
            UUID openedByUserId,
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

    default List<ExportCandidateRecord> lockAuthoritativeCandidatesForIntegrityValidation(
            UUID projectId,
            UUID projectSnapshotId,
            List<UUID> authoritativeExportCandidateIds
    ) {
        return authoritativeExportCandidateIds.stream()
                .distinct()
                .sorted(Comparator.comparing(UUID::toString))
                .map(candidateId -> findAuthoritativeCandidate(projectId, candidateId).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    default List<ExportPreviewTaskContext> lockTaskContextsForIntegrityValidation(
            UUID projectId,
            UUID projectSnapshotId,
            List<UUID> importedTaskIds
    ) {
        return importedTaskIds.stream()
                .distinct()
                .sorted(Comparator.comparing(UUID::toString))
                .map(taskId -> findTaskContext(projectId, projectSnapshotId, taskId).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    List<ExportPreviewApprovalRecord> findCurrentApprovalCandidates(
            UUID projectId,
            UUID authoritativeExportCandidateId
    );

    default List<ExportPreviewApprovalRecord> lockCurrentApprovalCandidatesForIntegrityValidation(
            UUID projectId,
            List<UUID> authoritativeExportCandidateIds
    ) {
        return authoritativeExportCandidateIds.stream()
                .distinct()
                .sorted(Comparator.comparing(UUID::toString))
                .flatMap(candidateId -> findCurrentApprovalCandidates(projectId, candidateId).stream())
                .toList();
    }

    ExportPreviewLineRecord createLine(
            UUID projectId,
            UUID projectSnapshotId,
            UUID exportBatchId,
            UUID authoritativeExportCandidateId
    );

    List<ExportPreviewLineRecord> listLines(UUID projectId, UUID exportBatchId);
}
