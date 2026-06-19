package com.shutdowntracker.api.importbatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ImportBatchStatusTests {

    @Test
    void matchesExistingDatabaseEnumValues() {
        assertThat(Arrays.stream(ImportBatchStatus.values()).map(ImportBatchStatus::databaseValue))
                .containsExactly("pending", "parsing", "parsed", "accepted", "failed", "superseded");
    }

    @Test
    void mapsDatabaseValuesToStatus() {
        assertThat(ImportBatchStatus.fromDatabaseValue("pending")).isEqualTo(ImportBatchStatus.PENDING);
        assertThat(ImportBatchStatus.fromDatabaseValue("parsing")).isEqualTo(ImportBatchStatus.PARSING);
        assertThat(ImportBatchStatus.fromDatabaseValue("parsed")).isEqualTo(ImportBatchStatus.PARSED);
        assertThat(ImportBatchStatus.fromDatabaseValue("accepted")).isEqualTo(ImportBatchStatus.ACCEPTED);
        assertThat(ImportBatchStatus.fromDatabaseValue("failed")).isEqualTo(ImportBatchStatus.FAILED);
        assertThat(ImportBatchStatus.fromDatabaseValue("superseded")).isEqualTo(ImportBatchStatus.SUPERSEDED);
    }

    @Test
    void rejectsStatusValuesOutsideExistingDatabaseEnum() {
        assertThatThrownBy(() -> ImportBatchStatus.fromDatabaseValue("queued"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported import batch status: queued");

        assertThatThrownBy(() -> ImportBatchStatus.fromDatabaseValue("running"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported import batch status: running");

        assertThatThrownBy(() -> ImportBatchStatus.fromDatabaseValue("completed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported import batch status: completed");
    }
}
