package com.shutdowntracker.projectexport.contract;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Canonical values shared by candidate review and MSPDI/XML generation. */
public final class ProjectExportValueNormalizer {

    private static final Pattern UNSIGNED_DECIMAL = Pattern.compile("^[0-9]+(?:\\.[0-9]+)?$");
    private static final Pattern OFFSET_DATE_TIME = Pattern.compile(
            "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}"
                    + "(?::[0-9]{2}(?:\\.[0-9]{1,6})?)?(?:Z|[+-][0-9]{2}(?::[0-9]{2})?)$"
    );
    private static final DateTimeFormatter WHOLE_SECOND_LOCAL_DATE_TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");

    private ProjectExportValueNormalizer() {
    }

    public static String normalize(ProjectExportArtifactField field, String value) {
        if (field == null) {
            throw new NullPointerException("field is required.");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("newValue is required.");
        }

        return switch (field) {
            case PERCENT_COMPLETE -> normalizePercentComplete(value);
            case ACTUAL_START, ACTUAL_FINISH -> normalizeProjectWallClockDateTime(value);
        };
    }

    public static String normalizePercentComplete(String value) {
        String candidate = requiredTrimmed(value);
        if (!UNSIGNED_DECIMAL.matcher(candidate).matches()) {
            throw new IllegalArgumentException("Percent complete must be numeric.");
        }

        BigDecimal normalized = new BigDecimal(candidate).stripTrailingZeros();
        if (normalized.compareTo(BigDecimal.ZERO) < 0 || normalized.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Percent complete must be between 0 and 100.");
        }
        if (normalized.scale() > 0) {
            throw new IllegalArgumentException("Percent complete must be a whole number between 0 and 100.");
        }
        return normalized.toPlainString();
    }

    public static String normalizeProjectWallClockDateTime(String value) {
        return normalizeProjectWallClockDateTime(value, true);
    }

    public static String normalizeProjectWallClockBaselineDateTime(String value) {
        return normalizeProjectWallClockDateTime(value, false);
    }

    private static String normalizeProjectWallClockDateTime(String value, boolean requireWholeSecond) {
        String candidate = requiredTrimmed(value);
        if (!OFFSET_DATE_TIME.matcher(candidate).matches()) {
            throw new IllegalArgumentException("Project date-time value must be an ISO-8601 offset date-time.");
        }
        try {
            OffsetDateTime parsed = OffsetDateTime.parse(candidate, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            if (parsed.getYear() < 1) {
                throw new IllegalArgumentException("Project date-time value must use a positive four-digit year.");
            }
            if (parsed.getNano() % 1_000 != 0) {
                throw new IllegalArgumentException("Project date-time values support at most microsecond precision.");
            }
            if (requireWholeSecond && parsed.getNano() != 0) {
                throw new IllegalArgumentException("Project date-time values support whole-second precision.");
            }

            String localDateTime = parsed.toLocalDateTime().format(WHOLE_SECOND_LOCAL_DATE_TIME);
            if (parsed.getNano() != 0) {
                String fractionalSecond = String.format(Locale.ROOT, "%09d", parsed.getNano())
                        .replaceFirst("0+$", "");
                localDateTime += "." + fractionalSecond;
            }
            String offset = parsed.getOffset().equals(ZoneOffset.UTC) ? "Z" : parsed.getOffset().getId();
            return localDateTime + offset;
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Project date-time value must be an ISO-8601 offset date-time.", exception);
        }
    }

    private static String requiredTrimmed(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("newValue is required.");
        }
        return value.trim();
    }
}
