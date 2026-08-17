package com.shutdowntracker.api.criticalwatch;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * How much reporting a Critical Work Package has actually received.
 *
 * <p>Deliberately not "overdue". Reporting policies — intervals, shifts, event triggers — are
 * specified but not yet built, so nothing here can say a report is late without inventing a
 * schedule the product does not hold. What it can say honestly is how many current reports
 * exist and when the last one arrived, which is enough to surface a package nobody has
 * reported on at all.
 *
 * @param updateCount current reports; a superseded report is not counted, because the
 *                    correction that replaced it is counted instead
 * @param lastSubmittedAt null when the package has never been reported on
 */
public record CriticalWorkPackageReportingSummary(
        UUID workPackageId,
        UUID criticalWatchlistId,
        String name,
        int updateCount,
        OffsetDateTime lastSubmittedAt
) {
}
