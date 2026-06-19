package com.shutdowntracker.api.exportpreview;

import java.util.Arrays;

public enum ExportBatchState {
    DRAFT_PREVIEW("draft_preview"),
    AWAITING_APPROVAL("awaiting_approval"),
    APPROVED("approved"),
    REJECTED("rejected"),
    GENERATED("generated"),
    OPENED_IN_MICROSOFT_PROJECT("opened_in_microsoft_project"),
    VERIFIED("verified"),
    SUPERSEDED("superseded"),
    FAILED("failed");

    private final String databaseValue;

    ExportBatchState(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static ExportBatchState fromDatabaseValue(String databaseValue) {
        return Arrays.stream(values())
                .filter(state -> state.databaseValue.equals(databaseValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported export batch state: " + databaseValue));
    }
}
