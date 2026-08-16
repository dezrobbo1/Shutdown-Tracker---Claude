package com.shutdowntracker.api.criticalwatch;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Submitting a Critical Update.
 *
 * <p>The submitter is resolved from the authenticated request. {@code idempotencyKey} lets
 * a queued offline submission be retried without producing a second report.
 */
public record CriticalUpdateSubmitRequest(
        UUID criticalWorkPackageId,
        String updateMode,
        String currentFocus,
        String currentBlockerSummary,
        String nextTarget,
        String idempotencyKey,
        String offlineLocalId,
        UUID supersedesCriticalUpdateId,
        List<CriticalUpdateLineRequest> lines
) {
    private static final List<String> UPDATE_MODES = List.of("ad_hoc", "scheduled", "shift", "event", "custom");

    public CriticalUpdateSubmitRequest {
        Objects.requireNonNull(criticalWorkPackageId, "criticalWorkPackageId is required.");
        updateMode = updateMode == null || updateMode.isBlank() ? "ad_hoc" : updateMode.trim();
        if (!UPDATE_MODES.contains(updateMode)) {
            throw new IllegalArgumentException("Unsupported update mode: " + updateMode);
        }
        lines = lines == null ? List.of() : List.copyOf(lines);
        if (idempotencyKey != null && idempotencyKey.isBlank()) {
            idempotencyKey = null;
        }
        if (offlineLocalId != null && offlineLocalId.isBlank()) {
            offlineLocalId = null;
        }
    }
}
