package com.shutdowntracker.api.assignment;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One explicit link from a Microsoft Project resource to a Shutdown Tracker user.
 *
 * <p>{@code resourceExternalUid} is the resource's identity in the Project source and is scoped to
 * the project rather than to a snapshot, so the link survives re-import. {@code matchedInSnapshot}
 * is not stored: it is resolved against whichever snapshot the caller asked about, and says whether
 * that snapshot still carries the resource this link points at.
 */
public record ProjectResourceLinkRecord(
        UUID id,
        UUID projectId,
        UUID userId,
        String userDisplayName,
        String resourceExternalUid,
        String resourceNameAtLink,
        boolean active,
        OffsetDateTime linkedAt,
        UUID linkedByUserId,
        OffsetDateTime revokedAt,
        UUID revokedByUserId,
        boolean matchedInSnapshot,
        String resourceNameInSnapshot
) {
}
