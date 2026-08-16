package com.shutdowntracker.api.criticalwatch;

import java.math.BigDecimal;
import java.util.UUID;

/** Optional line-level detail on a Critical Update. Lines never update Microsoft Project. */
public record CriticalUpdateLineRequest(
        UUID importedTaskId,
        String targetText,
        String actualText,
        String delayOrIssueText,
        String solutionOrNextActionText,
        BigDecimal percentComplete,
        BigDecimal physicalPercentComplete
) {
    public CriticalUpdateLineRequest {
        requirePercent(percentComplete, "percentComplete");
        requirePercent(physicalPercentComplete, "physicalPercentComplete");
    }

    private static void requirePercent(BigDecimal value, String fieldName) {
        if (value == null) {
            return;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and 100.");
        }
    }
}
