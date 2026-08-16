package com.shutdowntracker.api.execution;

import java.util.Arrays;

/**
 * What is happening at the workfront, per docs/product/task-progress-review-export-approval.md.
 *
 * <p>Kept separate from review and export state on purpose: a task can be blocked, awaiting
 * planner review, and not export-eligible at the same time, and collapsing those into one
 * status loses information the control room needs.
 */
public enum TaskExecutionState {

    NOT_STARTED("not_started"),
    READY("ready"),
    IN_PROGRESS("in_progress"),
    PAUSED("paused"),
    BLOCKED("blocked"),
    COMPLETED("completed");

    private final String databaseValue;

    TaskExecutionState(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    /** States that require the submitter to say why, so the reason is never lost. */
    public boolean requiresReason() {
        return this == PAUSED || this == BLOCKED;
    }

    public static TaskExecutionState fromDatabaseValue(String databaseValue) {
        return Arrays.stream(values())
                .filter(state -> state.databaseValue.equals(databaseValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported task execution state: " + databaseValue));
    }
}
