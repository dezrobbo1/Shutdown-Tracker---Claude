package com.shutdowntracker.api.reviewreset;

/**
 * The typed confirmation.
 *
 * <p>Checked on the server, not only in the browser. The console asks for the project's name before
 * it enables its button, but this endpoint is reachable by anything that can make a request, so the
 * deliberate act has to be verified where the deletion happens.
 */
public record ReviewDataResetRequest(String confirmation) {
}
