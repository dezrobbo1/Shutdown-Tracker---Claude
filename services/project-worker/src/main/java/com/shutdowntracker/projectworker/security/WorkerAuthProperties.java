package com.shutdowntracker.projectworker.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Service-to-service authentication settings for API-to-worker handoffs. */
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
