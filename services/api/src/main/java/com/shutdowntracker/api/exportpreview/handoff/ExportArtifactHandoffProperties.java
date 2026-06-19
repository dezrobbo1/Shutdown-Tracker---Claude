package com.shutdowntracker.api.exportpreview.handoff;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shutdown-tracker.export-artifact-handoff")
public record ExportArtifactHandoffProperties(String outputRoot) {

    public ExportArtifactHandoffProperties {
        if (outputRoot == null || outputRoot.isBlank()) {
            outputRoot = ".shutdown-tracker/export-artifacts";
        }
    }
}
