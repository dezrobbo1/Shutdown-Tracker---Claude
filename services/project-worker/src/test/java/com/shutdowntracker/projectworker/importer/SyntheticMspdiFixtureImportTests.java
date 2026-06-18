package com.shutdowntracker.projectworker.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SyntheticMspdiFixtureImportTests {

    private static final String NO_SCHEDULE_CALCULATION_NOTE = "Summary only; no schedule calculations were run.";

    private final MpxjProjectImportSummaryService service = new MpxjProjectImportSummaryService();

    @Test
    void readsSyntheticBasicWbsFixtureAndMatchesExpectedSummary() {
        Path fixturePath = repositoryRoot()
                .resolve("fixtures/import-export/synthetic-basic-wbs/synthetic-basic-wbs.mspdi.xml");

        ProjectImportSummary summary = service.summarize(fixturePath);

        assertThat(summary.sourceFilename()).isEqualTo("synthetic-basic-wbs.mspdi.xml");
        assertThat(summary.detectedFormat()).isNotBlank();
        assertThat(summary.projectName()).isEqualTo("Synthetic Basic WBS");
        assertThat(summary.taskCount()).isEqualTo(6);
        assertThat(summary.summaryTaskCount()).isEqualTo(2);
        assertThat(summary.leafTaskCount()).isEqualTo(4);
        assertThat(summary.resourceCount()).isZero();
        assertThat(summary.assignmentCount()).isZero();
        assertThat(summary.calendarCount()).isEqualTo(1);
        assertThat(summary.customFieldCount()).isZero();
        assertThat(summary.notes()).containsExactly(NO_SCHEDULE_CALCULATION_NOTE);
    }

    @Test
    void expectedSummaryDocumentsNoScheduleCalculations() {
        Path expectedSummaryPath = repositoryRoot()
                .resolve("fixtures/import-export/synthetic-basic-wbs/expected-import-summary.json");

        String expectedSummary = readString(expectedSummaryPath);

        assertThat(expectedSummary).contains("\"fixture_id\": \"synthetic-basic-wbs\"");
        assertThat(expectedSummary).contains("\"project_name\": \"Synthetic Basic WBS\"");
        assertThat(expectedSummary).contains("\"tasks\": 6");
        assertThat(expectedSummary).contains("\"summary_tasks\": 2");
        assertThat(expectedSummary).contains("\"leaf_tasks\": 4");
        assertThat(expectedSummary).contains(NO_SCHEDULE_CALCULATION_NOTE);
    }

    @Test
    void syntheticFixtureDoesNotContainSchedulerOrRealProjectTerms() {
        Path fixturePath = repositoryRoot()
                .resolve("fixtures/import-export/synthetic-basic-wbs/synthetic-basic-wbs.mspdi.xml");

        String fixtureXml = readString(fixturePath).toLowerCase();

        assertThat(fixtureXml)
                .doesNotContain("critical path")
                .doesNotContain("float")
                .doesNotContain("resource levelling")
                .doesNotContain("resource leveling")
                .doesNotContain("write-back")
                .doesNotContain("work order")
                .doesNotContain("vendor")
                .doesNotContain("contractor");
    }

    @Test
    void manifestMarksFixtureAsSyntheticAndSafeToCommit() {
        Path manifestPath = repositoryRoot()
                .resolve("fixtures/import-export/synthetic-basic-wbs/fixture-manifest.json");

        String manifest = readString(manifestPath);

        assertThat(manifest).contains("\"fixture_id\": \"synthetic-basic-wbs\"");
        assertThat(manifest).contains("\"synthetic_or_sanitized\": \"synthetic\"");
        assertThat(manifest).contains("\"contains_real_project_data\": false");
        assertThat(manifest).contains("\"allowed_commit\": true");
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path fixture = current.resolve("fixtures/import-export/synthetic-basic-wbs/synthetic-basic-wbs.mspdi.xml");
            if (Files.isRegularFile(fixture)) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Repository root with synthetic MSPDI fixture was not found.");
    }

    private String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read test file: " + path, ex);
        }
    }
}
