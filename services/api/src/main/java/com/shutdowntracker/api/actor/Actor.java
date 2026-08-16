package com.shutdowntracker.api.actor;

import java.util.Objects;
import java.util.UUID;

/**
 * The authenticated identity performing a request.
 *
 * <p>An {@code Actor} is resolved from the request by an {@link ActorResolver}. It is never read from a
 * request body: a caller must not be able to assert who approved, generated, opened, or verified an export
 * batch, because those facts are recorded as audit history.
 *
 * <p>{@code role} is the actor's claimed application role. It is carried into audit events for traceability
 * but is not yet enforced as authorization. Project-scoped RBAC as described in
 * {@code docs/security/authorization-model.md} remains a later slice.
 */
public record Actor(UUID userId, String role, String displayName) {

    public Actor {
        Objects.requireNonNull(userId, "userId is required.");
        if (role != null && role.isBlank()) {
            role = null;
        }
        if (displayName != null && displayName.isBlank()) {
            displayName = null;
        }
    }
}
