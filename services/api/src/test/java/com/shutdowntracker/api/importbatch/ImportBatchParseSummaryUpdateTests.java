package com.shutdowntracker.api.importbatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImportBatchParseSummaryUpdateTests {

    @Test
    void createsPersistenceUpdateFromWorkerParseSummaryResponse() {
        UUID importBatchId = UUID.randomUUID();

        ImportBatchParseSummaryUpdate update = ImportBatchParseSummaryUpdate.from(new ProjectParseSummaryResponse(
                importBatchId,
                "mpxj",
                "16.4.0",
                "synthetic-basic-wbs.mspdi.xml",
                "mspdi_xml",
                "Synthetic Basic WBS",
                6,
                2,
                4,
                0,
                0,
                1,
                0,
                0,
                0,
                List.of("Summary only; no schedule calculations were run.")
        ));

        assertThat(update.importBatchId()).isEqualTo(importBatchId);
        assertThat(update.parserName()).isEqualTo("mpxj");
        assertThat(update.parserVersion()).isEqualTo("16.4.0");
        assertThat(update.warningCount()).isZero();
        assertThat(update.errorCount()).isZero();
        assertThat(update.parseSummary().summaryOnly()).isTrue();
        assertThat(update.parseSummary().sourceFilename()).isEqualTo("synthetic-basic-wbs.mspdi.xml");
        assertThat(update.parseSummary().counts().taskCount()).isEqualTo(6);
        assertThat(update.parseSummary().counts().summaryTaskCount()).isEqualTo(2);
        assertThat(update.parseSummary().counts().leafTaskCount()).isEqualTo(4);
        assertThat(update.parseSummary().counts().resourceCount()).isZero();
        assertThat(update.parseSummary().counts().assignmentCount()).isZero();
        assertThat(update.parseSummary().counts().calendarCount()).isEqualTo(1);
        assertThat(update.parseSummary().counts().customFieldCount()).isZero();
        assertThat(update.parseSummary().notes())
                .containsExactly("Summary only; no schedule calculations were run.");
    }

    @Test
    void serializesSummaryAsJsonObjectForImportBatchParseSummaryColumn() {
        ImportBatchParseSummaryUpdate update = ImportBatchParseSummaryUpdate.from(new ProjectParseSummaryResponse(
                UUID.randomUUID(),
                "mpxj",
                "16.4.0",
                "synthetic-basic-wbs.mspdi.xml",
                "mspdi_xml",
                "Synthetic Basic WBS",
                6,
                2,
                4,
                0,
                0,
                1,
                0,
                0,
                0,
                List.of("Summary only; no schedule calculations were run.")
        ));

        JsonNode json = new ObjectMapper().valueToTree(update.parseSummary());

        assertThat(json.isObject()).isTrue();
        assertThat(json.get("sourceFilename").asText()).isEqualTo("synthetic-basic-wbs.mspdi.xml");
        assertThat(json.get("summaryOnly").asBoolean()).isTrue();
        assertThat(json.get("counts").get("taskCount").asInt()).isEqualTo(6);
        assertThat(json.get("counts").get("summaryTaskCount").asInt()).isEqualTo(2);
        assertThat(json.get("counts").get("leafTaskCount").asInt()).isEqualTo(4);
        assertThat(json.toString())
                .doesNotContain("criticalPath")
                .doesNotContain("float")
                .doesNotContain("resourceLevelling")
                .doesNotContain("recoveryScheduling");
    }

    @Test
    void rejectsNegativeCountsBeforeDatabaseConstraint() {
        assertThatThrownBy(() -> new ImportBatchParseSummaryCounts(
                -1,
                0,
                0,
                0,
                0,
                0,
                0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("taskCount must not be negative.");
    }
}
