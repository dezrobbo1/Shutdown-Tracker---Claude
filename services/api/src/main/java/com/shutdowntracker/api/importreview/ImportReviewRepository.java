package com.shutdowntracker.api.importreview;

import com.shutdowntracker.api.importedproject.ProjectSnapshotStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportReviewRepository {

    List<ImportReviewSnapshotSummary> listSnapshots(UUID projectId);

    Optional<ImportReviewSnapshotSummary> findSnapshot(UUID projectId, UUID snapshotId);

    List<ImportReviewTaskRow> listTasks(UUID projectId, UUID snapshotId);

    List<ImportReviewResourceRow> listResources(UUID projectId, UUID snapshotId);

    List<ImportReviewAssignmentRow> listAssignments(UUID projectId, UUID snapshotId);

    List<ImportReviewExtendedAttributeRow> listExtendedAttributes(UUID projectId, UUID snapshotId);

    Optional<ImportReviewSnapshotSummary> recordSnapshotDecision(
            UUID projectId,
            UUID snapshotId,
            ProjectSnapshotStatus status
    );
}
