package com.shutdowntracker.api.operations;

import java.util.Arrays;

public enum ProblemSeverity {

    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    CRITICAL("critical");

    private final String databaseValue;

    ProblemSeverity(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static ProblemSeverity fromDatabaseValue(String databaseValue) {
        return Arrays.stream(values())
                .filter(severity -> severity.databaseValue.equals(databaseValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported problem severity: " + databaseValue));
    }
}
