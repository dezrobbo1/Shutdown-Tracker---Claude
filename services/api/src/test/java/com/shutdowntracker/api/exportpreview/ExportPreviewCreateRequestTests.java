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
                List.of(line()),
                null
        );

        assertThat(request.metadata()).isEmpty();
        assertThat(request.candidateIds()).hasSize(1);
    }

    @Test
    void copiesMetadataMapsAndLineLists() {
        Map<String, Object> metadata = Map.of("source", "synthetic-export-preview");
        ExportPreviewCreateRequest request = new ExportPreviewCreateRequest(
                UUID.randomUUID(),
                List.of(line()),
                metadata
        );

        assertThat(request.metadata()).containsEntry("source", "synthetic-export-preview");
        assertThatThrownBy(() -> request.metadata().put("extra", "not allowed"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.candidateIds().add(line()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsEmptyPreviewLineList() {
        assertThatThrownBy(() -> new ExportPreviewCreateRequest(UUID.randomUUID(), List.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one authoritative export candidate is required.");
    }

    @Test
    void rejectsDuplicateAuthoritativeCandidateIds() {
        UUID candidateId = UUID.randomUUID();

        assertThatThrownBy(() -> new ExportPreviewCreateRequest(
                UUID.randomUUID(),
                List.of(
                        candidateId,
                        candidateId
                ),
                Map.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Duplicate authoritative export candidate '"
                                + candidateId
                                + "'."
                );
    }

    @Test
    void allowsDifferentAuthoritativeCandidates() {

        ExportPreviewCreateRequest request = new ExportPreviewCreateRequest(
                UUID.randomUUID(),
                List.of(
                        line(),
                        line()
                ),
                Map.of()
        );

        assertThat(request.candidateIds()).hasSize(2);
    }

    @Test
    void rejectsMissingAuthoritativeCandidateId() {
        assertThatThrownBy(() -> new ExportPreviewCreateRequest(
                UUID.randomUUID(),
                java.util.Collections.singletonList(null),
                Map.of()
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("candidateIds must not contain null values.");
    }

    @Test
    void rejectsMetadataWithNullKeysWithoutDependingOnMapImplementation() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(null, "not allowed");

        assertThatThrownBy(() -> new ExportPreviewCreateRequest(UUID.randomUUID(), List.of(line()), metadata))
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

    private UUID line() {
        return UUID.randomUUID();
    }
}
