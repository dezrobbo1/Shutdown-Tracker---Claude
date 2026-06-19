package com.shutdowntracker.projectexport.contract;

import java.util.Objects;

public record ProjectExportArtifactFieldValue(
        ProjectExportArtifactField field,
        String newValue
) {
    public ProjectExportArtifactFieldValue {
        Objects.requireNonNull(field, "field is required.");
        if (newValue == null || newValue.isBlank()) {
            throw new IllegalArgumentException("newValue is required.");
        }
    }
}
