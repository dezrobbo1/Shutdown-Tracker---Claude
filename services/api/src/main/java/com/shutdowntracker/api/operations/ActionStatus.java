package com.shutdowntracker.api.operations;

import java.util.Arrays;

public enum ActionStatus {

    OPEN("open"),
    ASSIGNED("assigned"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    VERIFIED("verified"),
    CLOSED("closed"),
    REOPENED("reopened"),
    SUPERSEDED("superseded");

    private final String databaseValue;

    ActionStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    /** States the database requires completion attribution for. */
    public boolean requiresCompletionAttribution() {
        return this == COMPLETED || this == VERIFIED || this == CLOSED;
    }

    public static ActionStatus fromDatabaseValue(String databaseValue) {
        return Arrays.stream(values())
                .filter(status -> status.databaseValue.equals(databaseValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported action status: " + databaseValue));
    }
}
