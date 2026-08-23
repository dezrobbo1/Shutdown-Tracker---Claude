package com.shutdowntracker.api.execution;

import java.util.Arrays;

/**
 * Whether an approved value may travel to Microsoft Project, and whether it has.
 *
 * <p>Five states, not nine. The four removed in {@code V015} either mirrored
 * {@code export_batches.status} or duplicated a fact another column already owned, and none of them
 * was ever written. How far the carrying batch got is read from the batch, through
 * {@code export_batch_id}; two columns that must agree about one fact eventually disagree.
 *
 * <pre>
 *   eligible --(preview created)--&gt; IN_EXPORT_PREVIEW --(artifact generated)--&gt; EXPORTED
 *                   ^                       |
 *                   +---(batch rejected)----+
 * </pre>
 *
 * <p>{@link #EXPORTED} means this update's value was written into a generated export artifact. It
 * does not mean a planner has verified that artifact, that Shutdown Tracker saved the master
 * {@code .mpp}, that a candidate schedule was recalculated, or that anything was adopted; nothing
 * in this product writes back. The deliberate absence of a {@code verified} value here is the
 * point — verification is the batch's fact, read from its status — and
 * {@code docs/product/approval-export-state-model.md} says why.
 *
 * <p>{@link #SUPERSEDED} is set by a correction and leaves {@code planner_review_state} alone,
 * because the planner did approve that value once. Only this column distinguishes a value that may
 * still travel from one that has been replaced.
 */
public enum ProgressExportState {

    NOT_ELIGIBLE("not_eligible"),
    ELIGIBLE("eligible"),
    IN_EXPORT_PREVIEW("in_export_preview"),
    EXPORTED("exported"),
    SUPERSEDED("superseded");

    private final String databaseValue;

    ProgressExportState(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static ProgressExportState fromDatabaseValue(String databaseValue) {
        return Arrays.stream(values())
                .filter(state -> state.databaseValue.equals(databaseValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported progress export state: " + databaseValue));
    }
}
