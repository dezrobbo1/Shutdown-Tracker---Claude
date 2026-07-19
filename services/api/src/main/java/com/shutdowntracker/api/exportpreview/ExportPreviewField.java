package com.shutdowntracker.api.exportpreview;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;

public enum ExportPreviewField {
    PERCENT_COMPLETE("percent_complete", true),
    PHYSICAL_PERCENT_COMPLETE("physical_percent_complete", false),
    ACTUAL_START("actual_start", true),
    ACTUAL_FINISH("actual_finish", true);

    private final String fieldName;
    private final boolean mvpExportAuthorized;

    ExportPreviewField(String fieldName, boolean mvpExportAuthorized) {
        this.fieldName = fieldName;
        this.mvpExportAuthorized = mvpExportAuthorized;
    }

    public String fieldName() {
        return fieldName;
    }

    public boolean mvpExportAuthorized() {
        return mvpExportAuthorized;
    }

    public String oldValue(ExportPreviewTaskContext task) {
        return switch (this) {
            case PERCENT_COMPLETE -> numericValue(task.percentComplete());
            case PHYSICAL_PERCENT_COMPLETE -> task.physicalPercentComplete() == null
                    ? null
                    : numericValue(task.physicalPercentComplete());
            case ACTUAL_START -> offsetDateTimeValue(task.actualStart());
            case ACTUAL_FINISH -> offsetDateTimeValue(task.actualFinish());
        };
    }

    public String normalizeValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Candidate value is required for " + fieldName + ".");
        }

        try {
            return switch (this) {
                case PERCENT_COMPLETE -> normalizePercent(value, true);
                case PHYSICAL_PERCENT_COMPLETE -> normalizePercent(value, false);
                case ACTUAL_START, ACTUAL_FINISH -> OffsetDateTime.parse(value).toInstant().toString();
            };
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid candidate value for " + fieldName + ".", exception);
        }
    }

    public static ExportPreviewField fromFieldName(String fieldName) {
        return Arrays.stream(values())
                .filter(field -> field.fieldName.equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported export preview field: " + fieldName));
    }

    private static String offsetDateTimeValue(OffsetDateTime value) {
        return value == null ? null : value.toInstant().toString();
    }

    private static String numericValue(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private String normalizePercent(String value, boolean requireIntegral) {
        BigDecimal normalized = new BigDecimal(value).stripTrailingZeros();
        if (normalized.compareTo(BigDecimal.ZERO) < 0 || normalized.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Candidate percentage must be between 0 and 100.");
        }
        if (requireIntegral && normalized.scale() > 0) {
            throw new IllegalArgumentException("Percent complete must be an integer between 0 and 100.");
        }
        return normalized.toPlainString();
    }
}
