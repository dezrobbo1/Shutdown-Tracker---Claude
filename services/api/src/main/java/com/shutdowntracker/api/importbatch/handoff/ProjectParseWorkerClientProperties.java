package com.shutdowntracker.api.importbatch.handoff;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shutdown-tracker.project-parse-worker")
public record ProjectParseWorkerClientProperties(
        String baseUrl,
        String parseSummaryPath,
        String sharedSecret
) {

    public ProjectParseWorkerClientProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8081";
        }
        if (parseSummaryPath == null || parseSummaryPath.isBlank()) {
            parseSummaryPath = "/worker/project-import/parse-summary";
        }
        if (sharedSecret != null && sharedSecret.isBlank()) {
            sharedSecret = null;
        }
    }
}
