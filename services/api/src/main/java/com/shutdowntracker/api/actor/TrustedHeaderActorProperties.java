package com.shutdowntracker.api.actor;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for trusted-header actor resolution.
 *
 * <p>Disabled by default and fails closed. When disabled, every endpoint that must attribute a change to a
 * user returns 401 rather than falling back to an anonymous or caller-asserted identity.
 *
 * <p>Enable this only when the service sits behind a gateway that authenticates the user and overwrites
 * these headers on every inbound request. If clients can reach the service directly, anyone can set the
 * headers and impersonate any actor. This is an interim seam, not the target authentication model; see
 * {@code docs/security/authorization-model.md}.
 */
@ConfigurationProperties(prefix = "shutdown-tracker.actor.trusted-header")
public record TrustedHeaderActorProperties(
        boolean enabled,
        String actorIdHeader,
        String actorRoleHeader,
        String actorDisplayNameHeader
) {

    public TrustedHeaderActorProperties {
        if (actorIdHeader == null || actorIdHeader.isBlank()) {
            actorIdHeader = "X-Shutdown-Tracker-Actor-Id";
        }
        if (actorRoleHeader == null || actorRoleHeader.isBlank()) {
            actorRoleHeader = "X-Shutdown-Tracker-Actor-Role";
        }
        if (actorDisplayNameHeader == null || actorDisplayNameHeader.isBlank()) {
            actorDisplayNameHeader = "X-Shutdown-Tracker-Actor-Name";
        }
    }
}
