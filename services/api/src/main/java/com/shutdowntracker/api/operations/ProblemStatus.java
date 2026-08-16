package com.shutdowntracker.api.operations;

import java.util.Arrays;

public enum ProblemStatus {

    OPEN("open"),
    ASSIGNED("assigned"),
    ESCALATED("escalated"),
    CLOSED("closed"),
    REOPENED("reopened"),
    SUPERSEDED("superseded");

    private final String databaseValue;

    ProblemStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public boolean isTerminal() {
        return this == CLOSED || this == SUPERSEDED;
    }

    public static ProblemStatus fromDatabaseValue(String databaseValue) {
        return Arrays.stream(values())
                .filter(status -> status.databaseValue.equals(databaseValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported problem status: " + databaseValue));
    }
}
