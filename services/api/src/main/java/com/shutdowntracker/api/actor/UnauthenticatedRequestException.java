package com.shutdowntracker.api.actor;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Raised when a request that must be attributed to a user carries no usable identity.
 */
public class UnauthenticatedRequestException extends ResponseStatusException {

    public UnauthenticatedRequestException(String reason) {
        super(HttpStatus.UNAUTHORIZED, reason);
    }
}
