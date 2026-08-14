package com.shutdowntracker.api.identity;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    UserRecord create(UserCreateRequest request);

    Optional<UserRecord> findById(UUID userId);

    Optional<UserRecord> findByEmail(String email);

    ProjectMembershipRecord grantMembership(UUID projectId, UUID userId, ProjectRole role, UUID grantedByUserId);

    Optional<ProjectMembershipRecord> findActiveMembership(UUID projectId, UUID userId);
}
