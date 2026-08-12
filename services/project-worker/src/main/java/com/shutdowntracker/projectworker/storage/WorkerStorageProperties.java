package com.shutdowntracker.projectworker.storage;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Local filesystem roots the worker is allowed to read source files from and write export artifacts to.
 *
 * <p>The worker must never resolve a caller-supplied path outside these roots. Defaults align with the
 * API service storage defaults because the current synchronous handoff assumes a shared local filesystem.
 */
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
