package com.shutdowntracker.api.importreview;

public record ImportReviewDecisionResponse(
        ImportReviewSnapshotSummary snapshot,
        String message
) {
}
