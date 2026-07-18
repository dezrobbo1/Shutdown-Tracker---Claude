package com.shutdowntracker.projectexport.contract;

import java.util.Arrays;

public enum ProjectExportArtifactField {
    PERCENT_COMPLETE("percent_complete"),
    ACTUAL_START("actual_start"),
    ACTUAL_FINISH("actual_finish");

    private final String fieldName;

    ProjectExportArtifactField(String fieldName) {
        this.fieldName = fieldName;
    }

    public String fieldName() {
        return fieldName;
    }

    public static ProjectExportArtifactField fromFieldName(String fieldName) {
        return Arrays.stream(values())
                .filter(field -> field.fieldName.equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported export artifact field: " + fieldName));
    }
}
