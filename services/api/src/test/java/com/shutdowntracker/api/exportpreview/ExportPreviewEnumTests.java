package com.shutdowntracker.api.exportpreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExportPreviewEnumTests {

    @Test
    void mapsExistingApprovalStates() {
        assertThat(ApprovalState.fromDatabaseValue("approved_for_export"))
                .isEqualTo(ApprovalState.APPROVED_FOR_EXPORT);
        assertThat(ApprovalState.fromDatabaseValue("exported")).isEqualTo(ApprovalState.EXPORTED);
    }

    @Test
    void mapsExistingExportBatchStates() {
        assertThat(ExportBatchState.fromDatabaseValue("draft_preview")).isEqualTo(ExportBatchState.DRAFT_PREVIEW);
        assertThat(ExportBatchState.fromDatabaseValue("verified")).isEqualTo(ExportBatchState.VERIFIED);
    }

    @Test
    void allowsOnlyProgressAndActualFieldsForPreviewLines() {
        assertThat(ExportPreviewField.fromFieldName("percent_complete"))
                .isEqualTo(ExportPreviewField.PERCENT_COMPLETE);
        assertThat(ExportPreviewField.fromFieldName("physical_percent_complete"))
                .isEqualTo(ExportPreviewField.PHYSICAL_PERCENT_COMPLETE);
        assertThat(ExportPreviewField.fromFieldName("actual_start")).isEqualTo(ExportPreviewField.ACTUAL_START);
        assertThat(ExportPreviewField.fromFieldName("actual_finish")).isEqualTo(ExportPreviewField.ACTUAL_FINISH);
        assertThat(ExportPreviewField.PERCENT_COMPLETE.mvpExportAuthorized()).isTrue();
        assertThat(ExportPreviewField.ACTUAL_START.mvpExportAuthorized()).isTrue();
        assertThat(ExportPreviewField.ACTUAL_FINISH.mvpExportAuthorized()).isTrue();
        assertThat(ExportPreviewField.PHYSICAL_PERCENT_COMPLETE.mvpExportAuthorized()).isFalse();
    }

    @Test
    void rejectsUnsupportedPreviewFields() {
        assertThatThrownBy(() -> ExportPreviewField.fromFieldName("planned_finish"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported export preview field: planned_finish");
    }

    @Test
    void readsOldValuesFromImportedSnapshotFactsOnly() {
        ExportPreviewTaskContext task = new ExportPreviewTaskContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SYN-TASK-1",
                "1",
                "Synthetic Task A1",
                false,
                new BigDecimal("25.50"),
                new BigDecimal("30.00"),
                OffsetDateTime.parse("2026-01-01T08:00:00Z"),
                OffsetDateTime.parse("2026-01-01T10:00:00Z")
        );

        assertThat(ExportPreviewField.PERCENT_COMPLETE.oldValue(task)).isEqualTo("25.5");
        assertThat(ExportPreviewField.PHYSICAL_PERCENT_COMPLETE.oldValue(task)).isEqualTo("30");
        assertThat(ExportPreviewField.ACTUAL_START.oldValue(task)).isEqualTo("2026-01-01T08:00:00Z");
        assertThat(ExportPreviewField.ACTUAL_FINISH.oldValue(task)).isEqualTo("2026-01-01T10:00:00Z");
    }

    @Test
    void normalizesCandidateValuesDeterministically() {
        assertThat(ExportPreviewField.PERCENT_COMPLETE.normalizeValue("075.00")).isEqualTo("75");
        assertThat(ExportPreviewField.PHYSICAL_PERCENT_COMPLETE.normalizeValue("30.500")).isEqualTo("30.5");
        assertThat(ExportPreviewField.ACTUAL_START.normalizeValue("2026-01-01T16:00:00+08:00"))
                .isEqualTo("2026-01-01T08:00:00Z");
    }

    @Test
    void rejectsFractionalOrOutOfRangeAuthorizedPercentComplete() {
        assertThatThrownBy(() -> ExportPreviewField.PERCENT_COMPLETE.normalizeValue("75.5"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid candidate value for percent_complete.");
        assertThatThrownBy(() -> ExportPreviewField.PERCENT_COMPLETE.normalizeValue("101"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid candidate value for percent_complete.");
    }
}
