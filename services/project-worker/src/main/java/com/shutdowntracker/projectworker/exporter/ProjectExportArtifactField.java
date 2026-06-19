package com.shutdowntracker.projectworker.exporter;

public enum ProjectExportArtifactField {
    PERCENT_COMPLETE("percent_complete"),
    PHYSICAL_PERCENT_COMPLETE("physical_percent_complete"),
    ACTUAL_START("actual_start"),
    ACTUAL_FINISH("actual_finish");

    private final String fieldName;

    ProjectExportArtifactField(String fieldName) {
        this.fieldName = fieldName;
    }

    public String fieldName() {
        return fieldName;
    }
}
