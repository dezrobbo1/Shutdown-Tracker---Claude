package com.shutdowntracker.projectexport.contract;

import java.util.Objects;

public record ProjectExportArtifactFieldValue(
        ProjectExportArtifactField field,
        String newValue
) {
    public ProjectExportArtifactFieldValue {
        Objects.requireNonNull(field, "field is required.");
        newValue = ProjectExportValueNormalizer.normalize(field, newValue);
    }
}
