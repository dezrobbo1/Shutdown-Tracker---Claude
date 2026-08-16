package com.shutdowntracker.api.criticalwatch;

import java.util.UUID;

/**
 * One summary task contributing work to a Critical Work Package.
 *
 * <p>A package may draw on several summary tasks where a reporting group crosses schedule
 * boundaries, which is what {@code multi_summary} records.
 */
public record CriticalWorkPackageSourceRecord(
        UUID id,
        UUID criticalWorkPackageId,
        UUID projectSnapshotId,
        UUID importedTaskId,
        String sourceType,
        boolean includeDescendants
) {
}
