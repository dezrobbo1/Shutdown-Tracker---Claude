package com.shutdowntracker.api.importreview;

import java.util.List;

public record ImportReviewSnapshotDetail(
        ImportReviewSnapshotSummary snapshot,
        List<ImportReviewTaskRow> tasks,
        List<ImportReviewResourceRow> resources,
        List<ImportReviewAssignmentRow> assignments,
        List<ImportReviewExtendedAttributeRow> extendedAttributes
) {
    public ImportReviewSnapshotDetail {
        tasks = List.copyOf(tasks);
        resources = List.copyOf(resources);
        assignments = List.copyOf(assignments);
        extendedAttributes = List.copyOf(extendedAttributes);
    }
}
