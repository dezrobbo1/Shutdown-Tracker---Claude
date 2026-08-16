package com.shutdowntracker.api.tasklineage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskLineageRepository {

    TaskLineageRecord create(UUID projectId, TaskLineageCreateRequest request);

    List<TaskLineageRecord> listBySnapshotPair(UUID projectId, UUID previousSnapshotId, UUID currentSnapshotId);

    Optional<TaskLineageRecord> find(UUID projectId, UUID lineageLinkId);

    Optional<TaskLineageRecord> updateReviewState(
            UUID projectId,
            UUID lineageLinkId,
            TaskLineageReviewState reviewState,
            UUID reviewedByUserId
    );
}
