package com.shutdowntracker.api.criticalwatch;

import java.util.UUID;

/**
 * A Critical Work Package.
 *
 * <p>A reporting object, not a scheduling object. Membership is chosen by a planner from
 * summary tasks; it is never derived from critical-path or float calculations, which this
 * product does not perform.
 */
public record CriticalWorkPackageRecord(
        UUID id,
        UUID projectId,
        UUID criticalWatchlistId,
        String name,
        String description,
        String status
) {
}
