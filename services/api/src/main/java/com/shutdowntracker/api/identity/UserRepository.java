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

    /**
     * Ends an active membership, recording when and by whom.
     *
     * <p>The row is kept and marked inactive rather than deleted. Membership is part of the audit
     * trail — it is what says who was entitled to do the things the trail records — so removing the
     * row would leave past events attributed to an authority nobody can see any more.
     *
     * <p>{@code revokedByUserId} may be null for the same reason {@link #grantMembership} allows
     * it: a membership withdrawn by a seeder was withdrawn by no one.
     */
    void revokeMembership(UUID membershipId, UUID revokedByUserId);

    /**
     * Changes an account's status.
     *
     * <p>Only {@link UserStatus#ACTIVE} may act, so this is how access is withdrawn from a person
     * whose rows the audit trail still references and who therefore cannot be deleted.
     */
    void updateStatus(UUID userId, UserStatus status);
}
