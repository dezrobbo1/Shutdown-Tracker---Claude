package com.shutdowntracker.api.exportpreview.storage;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shutdown-tracker.export-artifact-storage")
public record ExportArtifactStorageProperties(Path localRoot) {

    public ExportArtifactStorageProperties {
        if (localRoot == null) {
            localRoot = Path.of(".shutdown-tracker", "export-artifacts");
        }
    }
}
