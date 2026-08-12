package com.shutdowntracker.projectworker.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shared-secret authentication settings for the API-to-worker handoff hop.
 *
 * <p>Authentication is enabled by default and fails closed: when it is enabled without a configured secret
 * the worker refuses to start rather than serving handoff endpoints anonymously. This is deliberately a
 * service-to-service credential only. It is not user authentication and carries no role or project scope.
 */
@ConfigurationProperties(prefix = "shutdown-tracker.worker-auth")
public record WorkerAuthProperties(Boolean enabled, String sharedSecret) {

    public WorkerAuthProperties {
        if (enabled == null) {
            enabled = Boolean.TRUE;
        }
        if (sharedSecret != null && sharedSecret.isBlank()) {
            sharedSecret = null;
        }
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }
}
