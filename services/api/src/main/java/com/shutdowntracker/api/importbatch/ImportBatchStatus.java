package com.shutdowntracker.api.importbatch;

import java.util.Arrays;

public enum ImportBatchStatus {
    PENDING("pending"),
    PARSING("parsing"),
    PARSED("parsed"),
    ACCEPTED("accepted"),
    FAILED("failed"),
    SUPERSEDED("superseded");

    private final String databaseValue;

    ImportBatchStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static ImportBatchStatus fromDatabaseValue(String databaseValue) {
        return Arrays.stream(values())
                .filter(status -> status.databaseValue.equals(databaseValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported import batch status: " + databaseValue));
    }
}
