package com.shutdowntracker.api.execution;

import java.util.Arrays;

/**
 * How far an approved value has travelled toward Microsoft Project.
 *
 * <p>Reaching {@link #VERIFIED} records that a planner manually checked the artifact in
 * Microsoft Project. It does not mean Shutdown Tracker saved the master {@code .mpp};
 * nothing in this product writes back to it.
 */
public enum ProgressExportState {

    NOT_ELIGIBLE("not_eligible"),
    ELIGIBLE("eligible"),
    EXPORT_BLOCKED("export_blocked"),
    APPROVED_FOR_EXPORT("approved_for_export"),
    IN_EXPORT_PREVIEW("in_export_preview"),
    ARTIFACT_GENERATED("artifact_generated"),
    OPENED_IN_MICROSOFT_PROJECT("opened_in_microsoft_project"),
    VERIFIED("verified"),
    SUPERSEDED("superseded");

    private final String databaseValue;

    ProgressExportState(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static ProgressExportState fromDatabaseValue(String databaseValue) {
        return Arrays.stream(values())
                .filter(state -> state.databaseValue.equals(databaseValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported progress export state: " + databaseValue));
    }
}
