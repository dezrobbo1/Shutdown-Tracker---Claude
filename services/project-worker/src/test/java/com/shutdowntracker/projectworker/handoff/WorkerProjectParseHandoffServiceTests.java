package com.shutdowntracker.projectworker.handoff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shutdowntracker.projectimport.contract.ProjectParseSummaryRequest;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import com.shutdowntracker.projectworker.importer.ProjectImportSummary;
import com.shutdowntracker.projectworker.importer.ProjectImportSummaryService;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkerProjectParseHandoffServiceTests {

    private static final String NO_SCHEDULE_CALCULATION_NOTE = "Summary only; no schedule calculations were run.";

    @Test
    void mapsSharedHandoffRequestToImportSummaryResponse() {
        CapturingSummaryService summaryService = new CapturingSummaryService(new ProjectImportSummary(
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
                List.of(NO_SCHEDULE_CALCULATION_NOTE)
        ));
        WorkerProjectParseHandoffService service = new WorkerProjectParseHandoffService(summaryService);
        Path sourcePath = Path.of("fixtures/import-export/synthetic-basic-wbs/synthetic-basic-wbs.mspdi.xml")
                .toAbsolutePath()
                .normalize();
        UUID importBatchId = UUID.randomUUID();

        ProjectParseSummaryResponse response = service.summarize(new ProjectParseSummaryRequest(
                importBatchId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                sourcePath.toUri().toString(),
                "synthetic-basic-wbs.mspdi.xml"
        ));

        assertThat(summaryService.sourcePath).isEqualTo(sourcePath);
        assertThat(response.importBatchId()).isEqualTo(importBatchId);
        assertThat(response.parserName()).isEqualTo("mpxj");
        assertThat(response.sourceFilename()).isEqualTo("synthetic-basic-wbs.mspdi.xml");
        assertThat(response.detectedFormat()).isEqualTo("mspdi_xml");
        assertThat(response.projectName()).isEqualTo("Synthetic Basic WBS");
        assertThat(response.taskCount()).isEqualTo(6);
        assertThat(response.summaryTaskCount()).isEqualTo(2);
        assertThat(response.leafTaskCount()).isEqualTo(4);
        assertThat(response.resourceCount()).isZero();
        assertThat(response.assignmentCount()).isZero();
        assertThat(response.calendarCount()).isEqualTo(1);
        assertThat(response.customFieldCount()).isZero();
        assertThat(response.warningCount()).isZero();
        assertThat(response.errorCount()).isZero();
        assertThat(response.notes()).containsExactly(NO_SCHEDULE_CALCULATION_NOTE);
    }

    @Test
    void countsIgnoredReadIssuesAsWarnings() {
        WorkerProjectParseHandoffService service = new WorkerProjectParseHandoffService(new CapturingSummaryService(
                new ProjectImportSummary(
                        "synthetic-warning.mspdi.xml",
                        "mspdi_xml",
                        "Synthetic Warning",
                        1,
                        0,
                        1,
                        0,
                        0,
                        1,
                        0,
                        List.of(
                                NO_SCHEDULE_CALCULATION_NOTE,
                                "Ignored read issue: IllegalArgumentException: Synthetic warning"
                        )
                )
        ));

        ProjectParseSummaryResponse response = service.summarize(new ProjectParseSummaryRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Path.of("synthetic-warning.mspdi.xml").toAbsolutePath().toUri().toString(),
                "synthetic-warning.mspdi.xml"
        ));

        assertThat(response.warningCount()).isEqualTo(1);
        assertThat(response.errorCount()).isZero();
    }

    @Test
    void rejectsNonLocalStorageUriUntilQueueAndStorageContractsExist() {
        WorkerProjectParseHandoffService service = new WorkerProjectParseHandoffService(new CapturingSummaryService(
                new ProjectImportSummary("unused", "unknown", "unknown", 0, 0, 0, 0, 0, 0, 0, List.of())
        ));

        assertThatThrownBy(() -> service.summarize(new ProjectParseSummaryRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "s3://synthetic/source/synthetic-basic-wbs.mspdi.xml",
                "synthetic-basic-wbs.mspdi.xml"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Project worker parse handoff only supports local file storage URIs for now.");
    }

    private static class CapturingSummaryService implements ProjectImportSummaryService {

        private final ProjectImportSummary summary;
        private Path sourcePath;

        private CapturingSummaryService(ProjectImportSummary summary) {
            this.summary = summary;
        }

        @Override
        public ProjectImportSummary summarize(Path sourcePath) {
            this.sourcePath = sourcePath.toAbsolutePath().normalize();
            return summary;
        }
    }
}
