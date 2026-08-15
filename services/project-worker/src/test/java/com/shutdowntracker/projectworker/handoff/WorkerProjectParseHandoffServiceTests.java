package com.shutdowntracker.projectworker.handoff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shutdowntracker.projectimport.contract.ProjectParseSummaryRequest;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import com.shutdowntracker.projectworker.importer.ProjectImportSummary;
import com.shutdowntracker.projectworker.importer.ProjectImportSummaryService;
import com.shutdowntracker.projectworker.storage.WorkerStoragePathResolver;
import com.shutdowntracker.projectworker.storage.WorkerStorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkerProjectParseHandoffServiceTests {

    private static final String NO_SCHEDULE_CALCULATION_NOTE = "Summary only; no schedule calculations were run.";

    @TempDir
    private Path sourceFileRoot;

    private WorkerProjectParseHandoffService service(ProjectImportSummaryService summaryService) {
        return new WorkerProjectParseHandoffService(summaryService, new WorkerStoragePathResolver(
                new WorkerStorageProperties(sourceFileRoot, sourceFileRoot.resolve("artifacts"))
        ));
    }

    private Path storedSourceFile(String filename) throws IOException {
        Path stored = sourceFileRoot.resolve(filename);
        Files.copy(syntheticFixture(), stored);
        return stored.toRealPath();
    }

    private Path syntheticFixture() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path fixture = current.resolve("fixtures/import-export/synthetic-basic-wbs/synthetic-basic-wbs.mspdi.xml");
            if (Files.isRegularFile(fixture)) {
                return fixture;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Repository root with synthetic MSPDI fixture was not found.");
    }

    @Test
    void mapsSharedHandoffRequestToImportSummaryResponse() throws IOException {
        CapturingSummaryService summaryService = new CapturingSummaryService(summary());
        WorkerProjectParseHandoffService service = service(summaryService);
        Path sourcePath = storedSourceFile("synthetic-basic-wbs.mspdi.xml");
        UUID importBatchId = UUID.randomUUID();

        ProjectParseSummaryResponse response = service.summarize(new ProjectParseSummaryRequest(
                importBatchId, UUID.randomUUID(), UUID.randomUUID(), sourcePath.toUri().toString(), sourcePath.getFileName().toString()
        ));

        assertThat(summaryService.sourcePath).isEqualTo(sourcePath);
        assertThat(response.importBatchId()).isEqualTo(importBatchId);
        assertThat(response.parserName()).isEqualTo("mpxj");
        assertThat(response.taskCount()).isEqualTo(6);
        assertThat(response.summaryTaskCount()).isEqualTo(2);
        assertThat(response.leafTaskCount()).isEqualTo(4);
        assertThat(response.warningCount()).isZero();
        assertThat(response.notes()).containsExactly(NO_SCHEDULE_CALCULATION_NOTE);
    }

    @Test
    void countsIgnoredReadIssuesAsWarnings() throws IOException {
        WorkerProjectParseHandoffService service = service(new CapturingSummaryService(new ProjectImportSummary(
                "synthetic-warning.mspdi.xml", "mspdi_xml", "Synthetic Warning", 1, 0, 1, 0, 0, 1, 0,
                List.of(NO_SCHEDULE_CALCULATION_NOTE, "Ignored read issue: IllegalArgumentException: Synthetic warning")
        )));
        Path sourcePath = storedSourceFile("synthetic-warning.mspdi.xml");

        ProjectParseSummaryResponse response = service.summarize(new ProjectParseSummaryRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), sourcePath.toUri().toString(), sourcePath.getFileName().toString()
        ));

        assertThat(response.warningCount()).isEqualTo(1);
        assertThat(response.errorCount()).isZero();
    }

    @Test
    void rejectsNonLocalStorageUriUntilQueueAndStorageContractsExist() {
        WorkerProjectParseHandoffService service = service(new CapturingSummaryService(summary()));
        assertThatThrownBy(() -> service.summarize(new ProjectParseSummaryRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "s3://synthetic/source/synthetic-basic-wbs.mspdi.xml", "synthetic-basic-wbs.mspdi.xml"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Project worker handoff only supports local file storage URIs for now.");
    }

    private ProjectImportSummary summary() {
        return new ProjectImportSummary(
                "synthetic-basic-wbs.mspdi.xml", "mspdi_xml", "Synthetic Basic WBS",
                6, 2, 4, 0, 0, 1, 0, List.of(NO_SCHEDULE_CALCULATION_NOTE)
        );
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
