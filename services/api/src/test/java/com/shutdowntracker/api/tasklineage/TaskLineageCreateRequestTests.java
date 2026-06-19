package com.shutdowntracker.api.tasklineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskLineageCreateRequestTests {

    @Test
    void defaultsMissingMetadataToEmptyObject() {
        TaskLineageCreateRequest request = request(null);

        assertThat(request.metadata()).isEmpty();
    }

    @Test
    void copiesMetadataMaps() {
        Map<String, Object> metadata = Map.of("source", "synthetic-lineage-review");
        TaskLineageCreateRequest request = request(metadata);

        assertThat(request.metadata()).containsEntry("source", "synthetic-lineage-review");
        assertThatThrownBy(() -> request.metadata().put("extra", "not allowed"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsSameSnapshotPair() {
        UUID snapshotId = UUID.randomUUID();

        assertThatThrownBy(() -> new TaskLineageCreateRequest(
                snapshotId,
                snapshotId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "external_uid",
                BigDecimal.valueOf(95),
                Map.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("previousSnapshotId and currentSnapshotId must be different.");
    }

    @Test
    void rejectsMissingTaskIdsForConcreteLineageLinks() {
        assertThatThrownBy(() -> new TaskLineageCreateRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                "external_uid",
                BigDecimal.valueOf(95),
                Map.of()
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("previousImportedTaskId is required.");
    }

    @Test
    void rejectsConfidenceOutsideDatabaseConstraint() {
        assertThatThrownBy(() -> new TaskLineageCreateRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "external_uid",
                new BigDecimal("100.01"),
                Map.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("matchConfidence must be between 0 and 100.");
    }

    @Test
    void rejectsMetadataWithNullKeysWithoutDependingOnMapImplementation() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(null, "not allowed");

        assertThatThrownBy(() -> request(metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("metadata must not contain null keys.");
    }

    private TaskLineageCreateRequest request(Map<String, Object> metadata) {
        return new TaskLineageCreateRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "external_uid",
                BigDecimal.valueOf(95),
                metadata
        );
    }
}
