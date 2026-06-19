package com.shutdowntracker.api.exportpreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExportPreviewCreateRequestTests {

    @Test
    void defaultsMissingMetadataToEmptyObjects() {
        ExportPreviewCreateRequest request = new ExportPreviewCreateRequest(
                UUID.randomUUID(),
                List.of(line(null)),
                null
        );

        assertThat(request.metadata()).isEmpty();
        assertThat(request.lines()).hasSize(1);
        assertThat(request.lines().getFirst().metadata()).isEmpty();
    }

    @Test
    void copiesMetadataMapsAndLineLists() {
        Map<String, Object> metadata = Map.of("source", "synthetic-export-preview");
        ExportPreviewCreateRequest request = new ExportPreviewCreateRequest(
                UUID.randomUUID(),
                List.of(line(metadata)),
                metadata
        );

        assertThat(request.metadata()).containsEntry("source", "synthetic-export-preview");
        assertThatThrownBy(() -> request.metadata().put("extra", "not allowed"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.lines().add(line(null)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsEmptyPreviewLineList() {
        assertThatThrownBy(() -> new ExportPreviewCreateRequest(UUID.randomUUID(), List.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one export preview line is required.");
    }

    @Test
    void rejectsUnsupportedFieldNames() {
        assertThatThrownBy(() -> new ExportPreviewLineCreateRequest(
                UUID.randomUUID(),
                "task_update",
                UUID.randomUUID(),
                "planned_start",
                "2026-01-01T08:00:00Z",
                UUID.randomUUID(),
                null,
                "Synthetic reason",
                Map.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported export preview field: planned_start");
    }

    @Test
    void rejectsMetadataWithNullKeysWithoutDependingOnMapImplementation() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(null, "not allowed");

        assertThatThrownBy(() -> line(metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("metadata must not contain null keys.");
    }

    @Test
    void defaultsExportBatchDecisionMetadataToEmptyObject() {
        ExportBatchDecisionRequest request = new ExportBatchDecisionRequest(UUID.randomUUID(), "Synthetic reason", null);

        assertThat(request.metadata()).isEmpty();
    }

    @Test
    void rejectsGeneratedRequestWithoutArtifactMetadata() {
        assertThatThrownBy(() -> new ExportBatchGeneratedRequest(
                "",
                "sha256:synthetic",
                UUID.randomUUID(),
                "Synthetic reason",
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("exportFileUri is required.");

        assertThatThrownBy(() -> new ExportBatchGeneratedRequest(
                "object://synthetic/export-batches/export-1.mspdi.xml",
                " ",
                UUID.randomUUID(),
                "Synthetic reason",
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("exportFileHash is required.");
    }

    @Test
    void rejectsProjectOpenRequestWithoutActor() {
        assertThatThrownBy(() -> new ExportBatchProjectOpenRequest(
                null,
                "Synthetic Microsoft Project reopen",
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("openedByUserId is required.");
    }

    @Test
    void rejectsVerificationRequestWithoutActor() {
        assertThatThrownBy(() -> new ExportBatchVerificationRequest(
                null,
                "Synthetic manual verification complete",
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("verifiedByUserId is required.");
    }

    private ExportPreviewLineCreateRequest line(Map<String, Object> metadata) {
        return new ExportPreviewLineCreateRequest(
                UUID.randomUUID(),
                "task_update",
                UUID.randomUUID(),
                "percent_complete",
                "50",
                UUID.randomUUID(),
                null,
                "Synthetic reason",
                metadata
        );
    }
}
