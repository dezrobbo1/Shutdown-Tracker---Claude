package com.shutdowntracker.api.identity;

import java.util.Objects;
import java.util.UUID;
import com.shutdowntracker.api.actor.Actor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Decides whether an actor may perform an operation on a project.
 *
 * <p>Authorisation is resolved from stored membership, never from the request. A caller
 * can claim any role in a header; only the {@code project_memberships} row is trusted.
 * Every failure is a refusal — an unknown user, a suspended account, no membership, or a
 * role without the capability all deny.
 */
@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class ProjectAuthorizationService {

    private final UserRepository userRepository;

    public ProjectAuthorizationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Authorises the actor and returns their real role on the project.
     *
     * @throws ResponseStatusException 403 when the actor may not perform the capability
     */
    public ProjectRole requireCapability(UUID projectId, Actor actor, Capability capability) {
        Objects.requireNonNull(projectId, "projectId is required.");
        Objects.requireNonNull(actor, "actor is required.");
        Objects.requireNonNull(capability, "capability is required.");

        UserRecord user = userRepository.findById(actor.userId())
                .orElseThrow(() -> forbidden("Actor is not a known user."));

        if (!user.status().canAct()) {
            throw forbidden("User account is " + user.status().databaseValue() + ".");
        }

        ProjectMembershipRecord membership = userRepository
                .findActiveMembership(projectId, user.id())
                .orElseThrow(() -> forbidden("User has no active membership on this project."));

        if (!capability.allows(membership.role())) {
            throw forbidden("Role %s may not %s.".formatted(
                    membership.role().databaseValue(), capability.name().toLowerCase()));
        }
        return membership.role();
    }

    private ResponseStatusException forbidden(String reason) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, reason);
    }
}
