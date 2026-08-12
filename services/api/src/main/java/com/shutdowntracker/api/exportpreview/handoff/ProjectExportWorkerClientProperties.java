package com.shutdowntracker.api.exportpreview.handoff;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shutdown-tracker.project-export-worker")
public record ProjectExportWorkerClientProperties(
        String baseUrl,
        String generateArtifactPath,
        String sharedSecret
) {

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
    }
}
