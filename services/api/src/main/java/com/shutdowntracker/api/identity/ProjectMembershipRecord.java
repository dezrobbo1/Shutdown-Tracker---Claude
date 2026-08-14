package com.shutdowntracker.api.identity;

import java.util.UUID;

/** A user's role on one project. */
public record ProjectMembershipRecord(
        UUID id,
        UUID projectId,
        UUID userId,
        ProjectRole role,
        boolean active
) {
}
