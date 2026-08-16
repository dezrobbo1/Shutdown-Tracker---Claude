package com.shutdowntracker.api.operations;

import java.util.Arrays;

public enum EvidenceStatus {

    PENDING_UPLOAD("pending_upload"),
    UPLOADED("uploaded"),
    LINKED("linked"),
    UNLINKED("unlinked"),
    SUPERSEDED("superseded"),
    FAILED("failed");

    private final String databaseValue;

    EvidenceStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    /** States that must already have a storage location recorded. */
    public boolean requiresStorageUri() {
        return this != PENDING_UPLOAD && this != FAILED;
    }

    public static EvidenceStatus fromDatabaseValue(String databaseValue) {
        return Arrays.stream(values())
                .filter(status -> status.databaseValue.equals(databaseValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported evidence status: " + databaseValue));
    }
}
