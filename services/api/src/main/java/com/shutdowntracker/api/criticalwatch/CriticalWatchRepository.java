package com.shutdowntracker.api.criticalwatch;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CriticalWatchRepository {

    CriticalWatchlistRecord createWatchlist(UUID projectId, String name, String description, UUID createdByUserId);

    List<CriticalWatchlistRecord> findActiveWatchlists(UUID projectId);

    CriticalWorkPackageRecord createWorkPackage(
            UUID projectId, UUID watchlistId, String name, String description, UUID createdByUserId);

    Optional<CriticalWorkPackageRecord> findWorkPackage(UUID projectId, UUID workPackageId);

    List<CriticalWorkPackageRecord> findWorkPackages(UUID watchlistId);

    CriticalWorkPackageSourceRecord addSource(
            UUID projectId,
            UUID workPackageId,
            UUID projectSnapshotId,
            UUID importedTaskId,
            String sourceType,
            boolean includeDescendants,
            UUID createdByUserId);

    List<CriticalWorkPackageSourceRecord> findSources(UUID workPackageId);

    /** The tasks a package reports on, expanded from its summary-task sources. */
    List<UUID> findReportedTaskIds(UUID workPackageId);

    Optional<CriticalUpdateRecord> findUpdateByIdempotencyKey(UUID projectId, String idempotencyKey);

    CriticalUpdateRecord submitUpdate(UUID projectId, UUID submittedByUserId, CriticalUpdateSubmitRequest request);

    void markUpdateSuperseded(UUID criticalUpdateId);

    List<CriticalUpdateRecord> findUpdates(UUID workPackageId);

    int countUpdateLines(UUID criticalUpdateId);
}
