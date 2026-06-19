package com.shutdowntracker.api.importedproject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProjectSnapshotStatusTests {

    @Test
    void mapsExistingDatabaseValues() {
        assertThat(ProjectSnapshotStatus.fromDatabaseValue("parsed")).isEqualTo(ProjectSnapshotStatus.PARSED);
        assertThat(ProjectSnapshotStatus.fromDatabaseValue("accepted")).isEqualTo(ProjectSnapshotStatus.ACCEPTED);
        assertThat(ProjectSnapshotStatus.fromDatabaseValue("rejected")).isEqualTo(ProjectSnapshotStatus.REJECTED);
        assertThat(ProjectSnapshotStatus.fromDatabaseValue("superseded")).isEqualTo(ProjectSnapshotStatus.SUPERSEDED);
        assertThat(ProjectSnapshotStatus.fromDatabaseValue("failed")).isEqualTo(ProjectSnapshotStatus.FAILED);
    }

    @Test
    void rejectsImportBatchOrJobStyleStatuses() {
        assertThatThrownBy(() -> ProjectSnapshotStatus.fromDatabaseValue("pending"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported project snapshot status: pending");
        assertThatThrownBy(() -> ProjectSnapshotStatus.fromDatabaseValue("completed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported project snapshot status: completed");
    }
}
