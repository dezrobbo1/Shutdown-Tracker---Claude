package com.shutdowntracker.api.exportpreview.handoff;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shutdown-tracker.project-export-worker")
public record ProjectExportWorkerClientProperties(
        String baseUrl,
        String generateArtifactPath,
        String sharedSecret,
        Duration connectTimeout,
        Duration readTimeout
) {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofMinutes(2);

    public ProjectExportWorkerClientProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8081";
        }
        if (generateArtifactPath == null || generateArtifactPath.isBlank()) {
            generateArtifactPath = "/worker/project-export/generate-artifact";
        }
        if (sharedSecret != null && sharedSecret.isBlank()) {
            sharedSecret = null;
        }
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
            connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        }
        if (readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            readTimeout = DEFAULT_READ_TIMEOUT;
        }
    }
}
