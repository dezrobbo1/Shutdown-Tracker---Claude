package com.shutdowntracker.api.importbatch.handoff;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shutdown-tracker.project-parse-worker")
public record ProjectParseWorkerClientProperties(
        String baseUrl,
        String parseSummaryPath,
        String parseEntitiesPath,
        String sharedSecret,
        Duration connectTimeout,
        Duration readTimeout
) {
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofMinutes(2);

    public ProjectParseWorkerClientProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8081";
        }
        if (parseSummaryPath == null || parseSummaryPath.isBlank()) {
            parseSummaryPath = "/worker/project-import/parse-summary";
        }
        if (parseEntitiesPath == null || parseEntitiesPath.isBlank()) {
            parseEntitiesPath = "/worker/project-import/parse-entities";
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
