package com.shutdowntracker.api.exportpreview;

import com.shutdowntracker.projectexport.contract.ProjectExportArtifactField;
import com.shutdowntracker.projectexport.contract.ProjectExportValueNormalizer;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.regex.Pattern;

public enum ExportPreviewField {
    PERCENT_COMPLETE("percent_complete", true),
    PHYSICAL_PERCENT_COMPLETE("physical_percent_complete", false),
    ACTUAL_START("actual_start", true),
    ACTUAL_FINISH("actual_finish", true);

    private final String fieldName;
    private final boolean mvpExportAuthorized;
    private static final Pattern UNSIGNED_DECIMAL = Pattern.compile("^[0-9]+(?:\\.[0-9]+)?$");

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
                case PERCENT_COMPLETE -> ProjectExportValueNormalizer.normalize(
                        ProjectExportArtifactField.PERCENT_COMPLETE,
                        value
                );
                case PHYSICAL_PERCENT_COMPLETE -> normalizePhysicalPercentComplete(value);
                case ACTUAL_START -> ProjectExportValueNormalizer.normalize(
                        ProjectExportArtifactField.ACTUAL_START,
                        value
                );
                case ACTUAL_FINISH -> ProjectExportValueNormalizer.normalize(
                        ProjectExportArtifactField.ACTUAL_FINISH,
                        value
                );
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
        return value == null
                ? null
                : ProjectExportValueNormalizer.normalizeProjectWallClockBaselineDateTime(value.toString());
    }

    private static String numericValue(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static String normalizePhysicalPercentComplete(String value) {
        String candidate = value.trim();
        if (!UNSIGNED_DECIMAL.matcher(candidate).matches()) {
            throw new IllegalArgumentException("Physical percent complete must be numeric.");
        }
        BigDecimal numericValue;
        try {
            numericValue = new BigDecimal(candidate);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Physical percent complete must be numeric.", exception);
        }
        if (numericValue.compareTo(BigDecimal.ZERO) < 0
                || numericValue.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Physical percent complete must be between 0 and 100.");
        }
        return numericValue.stripTrailingZeros().toPlainString();
    }

}
