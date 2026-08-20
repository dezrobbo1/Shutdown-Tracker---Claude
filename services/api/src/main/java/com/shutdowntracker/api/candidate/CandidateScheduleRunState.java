package com.shutdowntracker.api.candidate;

import java.util.Arrays;

/**
 * Where one Microsoft Project candidate calculation has got to.
 *
 * <p>The lifecycle is the one in {@code docs/product/approval-export-state-model.md}. Two of its
 * states are deliberately absent: {@code not_prepared} is the absence of a run rather than a state
 * of one, and {@code calculation_pending} belongs to the planner-controlled Microsoft Project
 * companion, which does not exist here. Returning a candidate is a manual planner action, so a run
 * begins at the moment there is something to review.
 *
 * <p>{@code ACCEPTED} means a planner accepted the candidate. It never means the master schedule
 * was adopted, which is a separate record and a separate decision.
 */
public enum CandidateScheduleRunState {

    RETURNED("returned"),
    DELTA_READY("delta_ready"),
    ACCEPTED("accepted"),
    REJECTED("rejected"),
    SUPERSEDED("superseded"),
    FAILED("failed");

    private final String databaseValue;

    CandidateScheduleRunState(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static CandidateScheduleRunState fromDatabaseValue(String databaseValue) {
        return Arrays.stream(values())
                .filter(state -> state.databaseValue.equals(databaseValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported candidate schedule run state: " + databaseValue));
    }
}
