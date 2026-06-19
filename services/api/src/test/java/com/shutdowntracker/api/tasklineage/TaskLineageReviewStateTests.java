package com.shutdowntracker.api.tasklineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TaskLineageReviewStateTests {

    @Test
    void mapsExistingDatabaseValues() {
        assertThat(TaskLineageReviewState.fromDatabaseValue("suggested")).isEqualTo(TaskLineageReviewState.SUGGESTED);
        assertThat(TaskLineageReviewState.fromDatabaseValue("accepted")).isEqualTo(TaskLineageReviewState.ACCEPTED);
        assertThat(TaskLineageReviewState.fromDatabaseValue("rejected")).isEqualTo(TaskLineageReviewState.REJECTED);
        assertThat(TaskLineageReviewState.fromDatabaseValue("superseded")).isEqualTo(TaskLineageReviewState.SUPERSEDED);
    }

    @Test
    void rejectsUnsupportedReviewStates() {
        assertThatThrownBy(() -> TaskLineageReviewState.fromDatabaseValue("matched"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported task lineage review state: matched");
    }
}
