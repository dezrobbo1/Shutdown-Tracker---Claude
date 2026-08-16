package com.shutdowntracker.api.execution;

import java.util.Arrays;

/**
 * Whether a planner has judged the update safe to send toward Microsoft Project.
 *
 * <p>{@link #NOT_REQUIRED} is the resting state for updates that carry nothing exportable,
 * so the planner queue holds only decisions a planner actually has to make.
 */
public enum PlannerReviewState {

    NOT_REQUIRED("not_required"),
    NEEDS_PLANNER_REVIEW("needs_planner_review"),
    PLANNER_APPROVED("planner_approved"),
    PLANNER_REJECTED("planner_rejected");

    private final String databaseValue;

    PlannerReviewState(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static PlannerReviewState fromDatabaseValue(String databaseValue) {
        return Arrays.stream(values())
                .filter(state -> state.databaseValue.equals(databaseValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported planner review state: " + databaseValue));
    }
}
