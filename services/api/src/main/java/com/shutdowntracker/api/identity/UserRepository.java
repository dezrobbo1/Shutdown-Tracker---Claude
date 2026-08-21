package com.shutdowntracker.api.identity;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    UserRecord create(UserCreateRequest request);

    Optional<UserRecord> findById(UUID userId);

    Optional<UserRecord> findByEmail(String email);

    /**
     * Grants a membership, recording where it came from.
     *
     * <p>{@code grantedByUserId} is the person who granted it and may be null when nobody did —
     * a membership created by a seeder was granted by no one, and saying so with a null plus a
     * metadata marker is more honest than attributing it to whichever user happened to be first.
     */
    ProjectMembershipRecord grantMembership(
            UUID projectId,
            UUID userId,
            ProjectRole role,
            UUID grantedByUserId,
            Map<String, Object> metadata);

    default ProjectMembershipRecord grantMembership(
            UUID projectId,
            UUID userId,
            ProjectRole role,
            UUID grantedByUserId
    ) {
        return grantMembership(projectId, userId, role, grantedByUserId, Map.of());
    }

    Optional<ProjectMembershipRecord> findActiveMembership(UUID projectId, UUID userId);
}
