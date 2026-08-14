package com.shutdowntracker.api.execution;

import java.util.Arrays;

/** Whether a submitted progress update has been checked operationally by a supervisor. */
public enum ProgressReviewState {

    DRAFT("draft"),
    SUBMITTED("submitted"),
    SUPERVISOR_ACCEPTED("supervisor_accepted"),
    CORRECTION_REQUESTED("correction_requested"),
    REJECTED("rejected"),
    SUPERSEDED("superseded");

    private final String databaseValue;

    ProgressReviewState(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public boolean isAwaitingSupervisor() {
        return this == SUBMITTED;
    }

    public static ProgressReviewState fromDatabaseValue(String databaseValue) {
        return Arrays.stream(values())
                .filter(state -> state.databaseValue.equals(databaseValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported progress review state: " + databaseValue));
    }
}
