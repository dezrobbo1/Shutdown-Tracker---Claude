package com.shutdowntracker.projectworker.storage;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Filesystem roots the worker may read from and write to. */
@ConfigurationProperties(prefix = "shutdown-tracker.worker-storage")
public record WorkerStorageProperties(Path sourceFileRoot, Path exportArtifactRoot) {

    public WorkerStorageProperties {
        if (sourceFileRoot == null) {
            sourceFileRoot = Path.of(".shutdown-tracker", "source-files");
        }
        if (exportArtifactRoot == null) {
            exportArtifactRoot = Path.of(".shutdown-tracker", "export-artifacts");
        }
    }
}
