package com.shutdowntracker.api.sourcefile;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shutdown-tracker.source-file-validation")
public record SourceFileValidationProperties(long maxSizeBytes) {

    public SourceFileValidationProperties {
        if (maxSizeBytes <= 0) {
            throw new IllegalArgumentException("maxSizeBytes must be positive");
        }
    }
}
