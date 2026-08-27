package com.shutdowntracker.api.reviewreset;

/**
 * The project exists, or does not, but either way it is not one this endpoint may empty.
 *
 * <p>Surfaced as 409 rather than 403: a different role could not make this succeed, so describing it
 * as a permission problem would send somebody looking for the wrong fix.
 */
public class ReviewResetRefusedException extends RuntimeException {

    public ReviewResetRefusedException(String message) {
        super(message);
    }
}
