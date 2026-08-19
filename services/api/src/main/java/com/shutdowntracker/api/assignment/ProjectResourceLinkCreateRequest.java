package com.shutdowntracker.api.assignment;

import java.util.Objects;
import java.util.UUID;

/**
 * A request to link a Project resource to a user.
 *
 * <p>The resource is named by its Project UID, not by the {@code imported_resources} row id, because
 * the row belongs to one snapshot and the link outlives it.
 */
public record ProjectResourceLinkCreateRequest(UUID userId, String resourceExternalUid) {

    public ProjectResourceLinkCreateRequest {
        Objects.requireNonNull(userId, "userId is required.");
        if (resourceExternalUid == null || resourceExternalUid.isBlank()) {
            throw new IllegalArgumentException("resourceExternalUid is required.");
        }
        resourceExternalUid = resourceExternalUid.trim();
    }
}
