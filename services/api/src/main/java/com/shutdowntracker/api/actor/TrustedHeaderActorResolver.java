package com.shutdowntracker.api.actor;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves the actor from gateway-set request headers.
 *
 * <p>Interim implementation of {@link ActorResolver}. It trusts its headers completely, so it is only safe
 * behind a gateway that authenticates the user and overwrites those headers. It is disabled by default.
 */
@Component
public class TrustedHeaderActorResolver implements ActorResolver {

    private final TrustedHeaderActorProperties properties;

    public TrustedHeaderActorResolver(TrustedHeaderActorProperties properties) {
        this.properties = properties;
    }

    @Override
    public Actor resolve(HttpServletRequest request) {
        if (!properties.enabled()) {
            throw new UnauthenticatedRequestException(
                    "Actor resolution is not configured. This operation must be attributed to a user."
            );
        }

        String actorId = trimmedHeader(request, properties.actorIdHeader());
        if (actorId == null) {
            throw new UnauthenticatedRequestException("Request is missing an authenticated actor.");
        }

        try {
            return new Actor(
                    UUID.fromString(actorId),
                    trimmedHeader(request, properties.actorRoleHeader()),
                    trimmedHeader(request, properties.actorDisplayNameHeader())
            );
        } catch (IllegalArgumentException exception) {
            throw new UnauthenticatedRequestException("Authenticated actor id is not a valid UUID.");
        }
    }

    private String trimmedHeader(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
