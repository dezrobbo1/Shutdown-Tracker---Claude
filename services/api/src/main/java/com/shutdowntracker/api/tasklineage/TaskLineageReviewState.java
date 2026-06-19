package com.shutdowntracker.api.tasklineage;

import java.util.Arrays;

public enum TaskLineageReviewState {
    SUGGESTED("suggested"),
    ACCEPTED("accepted"),
    REJECTED("rejected"),
    SUPERSEDED("superseded");

    private final String databaseValue;

    TaskLineageReviewState(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static TaskLineageReviewState fromDatabaseValue(String databaseValue) {
        return Arrays.stream(values())
                .filter(state -> state.databaseValue.equals(databaseValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported task lineage review state: " + databaseValue
                ));
    }
}
