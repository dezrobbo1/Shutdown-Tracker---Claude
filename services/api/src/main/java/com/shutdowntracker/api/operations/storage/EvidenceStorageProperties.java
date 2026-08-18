package com.shutdowntracker.api.operations.storage;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shutdown-tracker.evidence-storage")
public record EvidenceStorageProperties(Path localRoot, long maxSizeBytes) {

    /** Matches the servlet multipart limit, so a rejected upload says which limit it hit. */
    private static final long DEFAULT_MAX_SIZE_BYTES = 52_428_800L;

    public EvidenceStorageProperties {
        if (localRoot == null) {
            localRoot = Path.of(".shutdown-tracker", "evidence");
        }
        if (maxSizeBytes <= 0) {
            maxSizeBytes = DEFAULT_MAX_SIZE_BYTES;
        }
    }
}
