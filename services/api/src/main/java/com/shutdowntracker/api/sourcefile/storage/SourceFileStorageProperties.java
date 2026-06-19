package com.shutdowntracker.api.sourcefile.storage;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shutdown-tracker.source-file-storage")
public record SourceFileStorageProperties(Path localRoot) {

    public SourceFileStorageProperties {
        if (localRoot == null) {
            localRoot = Path.of(".shutdown-tracker", "source-files");
        }
    }
}
