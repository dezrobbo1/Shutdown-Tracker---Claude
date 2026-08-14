package com.shutdowntracker.api.criticalwatch;

import java.util.UUID;

/** A named operational reporting list for one shutdown, area, or purpose. */
public record CriticalWatchlistRecord(
        UUID id,
        UUID projectId,
        String name,
        String description,
        String status
) {
}
