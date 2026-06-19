package com.shutdowntracker.api.exportpreview;

import java.util.Arrays;

public enum ApprovalState {
    DRAFT("draft"),
    SUBMITTED("submitted"),
    AWAITING_REVIEW("awaiting_review"),
    CORRECTION_REQUESTED("correction_requested"),
    APPROVED_FOR_EXPORT("approved_for_export"),
    REJECTED("rejected"),
    SUPERSEDED("superseded"),
    EXPORTED("exported");

    private final String databaseValue;

    ApprovalState(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static ApprovalState fromDatabaseValue(String databaseValue) {
        return Arrays.stream(values())
                .filter(state -> state.databaseValue.equals(databaseValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported approval state: " + databaseValue));
    }
}
