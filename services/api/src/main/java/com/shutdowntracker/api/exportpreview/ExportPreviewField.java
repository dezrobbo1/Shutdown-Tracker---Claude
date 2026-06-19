package com.shutdowntracker.api.exportpreview;

import java.time.OffsetDateTime;
import java.util.Arrays;

public enum ExportPreviewField {
    PERCENT_COMPLETE("percent_complete"),
    PHYSICAL_PERCENT_COMPLETE("physical_percent_complete"),
    ACTUAL_START("actual_start"),
    ACTUAL_FINISH("actual_finish");

    private final String fieldName;

    ExportPreviewField(String fieldName) {
        this.fieldName = fieldName;
    }

    public String fieldName() {
        return fieldName;
    }

    public String oldValue(ExportPreviewTaskContext task) {
        return switch (this) {
            case PERCENT_COMPLETE -> task.percentComplete() == null ? null : task.percentComplete().toPlainString();
            case PHYSICAL_PERCENT_COMPLETE -> task.physicalPercentComplete() == null
                    ? null
                    : task.physicalPercentComplete().toPlainString();
            case ACTUAL_START -> offsetDateTimeValue(task.actualStart());
            case ACTUAL_FINISH -> offsetDateTimeValue(task.actualFinish());
        };
    }

    public static ExportPreviewField fromFieldName(String fieldName) {
        return Arrays.stream(values())
                .filter(field -> field.fieldName.equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported export preview field: " + fieldName));
    }

    private static String offsetDateTimeValue(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }
}
