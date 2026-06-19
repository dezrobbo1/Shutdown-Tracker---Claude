package com.shutdowntracker.api.importedproject;

import java.util.Arrays;

public enum ProjectSnapshotStatus {
    PARSED("parsed"),
    ACCEPTED("accepted"),
    REJECTED("rejected"),
    SUPERSEDED("superseded"),
    FAILED("failed");

    private final String databaseValue;

    ProjectSnapshotStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static ProjectSnapshotStatus fromDatabaseValue(String databaseValue) {
        return Arrays.stream(values())
                .filter(status -> status.databaseValue.equals(databaseValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported project snapshot status: " + databaseValue
                ));
    }
}
