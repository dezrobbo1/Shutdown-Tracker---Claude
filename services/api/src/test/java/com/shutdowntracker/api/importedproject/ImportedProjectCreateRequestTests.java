package com.shutdowntracker.api.importedproject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImportedProjectCreateRequestTests {

    @Test
    void defaultsMissingEntityListsAndMetadataToEmptyObjects() {
        ImportedProjectSnapshotCreateRequest request = new ImportedProjectSnapshotCreateRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SYNTHETIC-PROJECT-1",
                "Synthetic Basic WBS",
                null,
                null,
                null
        );

        assertThat(request.metadata()).isEmpty();
        assertThat(request.entities().tasks()).isEmpty();
        assertThat(request.entities().resources()).isEmpty();
        assertThat(request.entities().assignments()).isEmpty();
        assertThat(request.entities().extendedAttributes()).isEmpty();
    }

    @Test
    void rejectsTaskPercentOutsideImportedDatabaseConstraint() {
        assertThatThrownBy(() -> syntheticTask(new BigDecimal("100.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("percentComplete must be between 0 and 100.");
    }

    @Test
    void rejectsTaskDatesThatBreakImportedDatabaseConstraint() {
        OffsetDateTime finish = OffsetDateTime.parse("2026-01-01T08:00:00Z");
        OffsetDateTime start = OffsetDateTime.parse("2026-01-01T09:00:00Z");

        assertThatThrownBy(() -> new ImportedTaskCreateRequest(
                "SYN-TASK-1",
                "1",
                "Synthetic Task",
                "1.1",
                "1.1",
                1,
                false,
                null,
                null,
                start,
                finish,
                null,
                null,
                BigDecimal.ZERO,
                null,
                null,
                Map.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("plannedFinish must not be before plannedStart.");
    }

    @Test
    void copiesRawDataMaps() {
        Map<String, Object> rawData = Map.of("source", "synthetic-fixture");
        ImportedResourceCreateRequest resource = new ImportedResourceCreateRequest(
                "SYN-RES-1",
                "Synthetic Resource",
                "work",
                rawData
        );

        assertThat(resource.rawData()).containsEntry("source", "synthetic-fixture");
        assertThatThrownBy(() -> resource.rawData().put("extra", "not allowed"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsRawDataMapsWithNullKeysWithoutDependingOnMapImplementation() {
        Map<String, Object> rawData = new HashMap<>();
        rawData.put(null, "not allowed");

        assertThatThrownBy(() -> new ImportedResourceCreateRequest(
                "SYN-RES-1",
                "Synthetic Resource",
                "work",
                rawData
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rawData must not contain null keys.");
    }

    private ImportedTaskCreateRequest syntheticTask(BigDecimal percentComplete) {
        return new ImportedTaskCreateRequest(
                "SYN-TASK-1",
                "1",
                "Synthetic Task",
                "1.1",
                "1.1",
                1,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                percentComplete,
                null,
                null,
                Map.of("source", "synthetic-fixture")
        );
    }
}
