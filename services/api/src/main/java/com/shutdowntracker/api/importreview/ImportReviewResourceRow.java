package com.shutdowntracker.api.importreview;

import java.util.UUID;

public record ImportReviewResourceRow(
        UUID id,
        String externalUid,
        String name,
        String resourceType
) {
}
