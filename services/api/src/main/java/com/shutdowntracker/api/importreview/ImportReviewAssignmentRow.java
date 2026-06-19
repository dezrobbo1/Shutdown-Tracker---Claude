package com.shutdowntracker.api.importreview;

import java.util.UUID;

public record ImportReviewAssignmentRow(
        UUID id,
        String externalUid,
        String taskExternalUid,
        String resourceExternalUid,
        UUID importedTaskId,
        UUID importedResourceId
) {
}
