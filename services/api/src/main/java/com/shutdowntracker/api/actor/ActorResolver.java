package com.shutdowntracker.api.actor;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the authenticated {@link Actor} for a request.
 *
 * <p>This is the single seam where request-borne identity enters the application. The current
 * implementation reads gateway-set headers; an OIDC token resolver should replace it here without
 * changing controllers or services.
 */
public interface ActorResolver {

    /**
     * @throws UnauthenticatedRequestException when the request carries no usable identity.
     */
    Actor resolve(HttpServletRequest request);
}
