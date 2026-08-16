package com.shutdowntracker.projectworker.handoff;

import static org.assertj.core.api.Assertions.assertThat;

import com.shutdowntracker.projectimport.contract.ProjectParseSummaryRequest;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import com.shutdowntracker.projectworker.importer.MpxjProjectEntityExtractionService;
import com.shutdowntracker.projectworker.importer.MpxjProjectImportSummaryService;
import com.shutdowntracker.projectworker.importer.MpxjProjectParseService;
import com.shutdowntracker.projectworker.storage.WorkerStoragePathResolver;
import com.shutdowntracker.projectworker.storage.WorkerStorageProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;

class WorkerProjectParseExpectedOutputTests {

    private WorkerProjectParseHandoffService service() {
        Path fixtureRoot = repositoryRoot().resolve("fixtures");
        return new WorkerProjectParseHandoffService(
                new MpxjProjectImportSummaryService(),
                new MpxjProjectParseService(
                        new MpxjProjectImportSummaryService(), new MpxjProjectEntityExtractionService()),
                new WorkerStoragePathResolver(new WorkerStorageProperties(fixtureRoot, fixtureRoot))
        );
    }

    @Test
    void syntheticBasicWbsMatchesExpectedWorkerParseSummary() {
        Path root = repositoryRoot();
        Path fixtureRoot = root.resolve("fixtures/import-export");
        Path fixturePath = fixtureRoot.resolve("synthetic-basic-wbs/synthetic-basic-wbs.mspdi.xml");
        WorkerProjectParseHandoffService service = new WorkerProjectParseHandoffService(
                new MpxjProjectImportSummaryService(),
                new WorkerStoragePathResolver(new WorkerStorageProperties(
                        fixtureRoot,
                        fixtureRoot.resolve("_local")
                ))
        );
        Map<String, Object> expected = parseJson(root.resolve(
                "fixtures/import-export/synthetic-basic-wbs/expected-import-summary.json"
        ));
        Map<String, Object> expectedResponse = map(expected.get("worker_response"));

        ProjectParseSummaryResponse response = service.summarize(new ProjectParseSummaryRequest(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                fixturePath.toUri().toString(),
                stringValue(expected, "source_filename")
        ));

        assertThat(expected.get("summary_only")).isEqualTo(Boolean.TRUE);
        assertThat(response.parserName()).isEqualTo(stringValue(expectedResponse, "parser_name"));
        assertThat(response.sourceFilename()).isEqualTo(stringValue(expectedResponse, "source_filename"));
        assertThat(response.detectedFormat()).isEqualTo(stringValue(expectedResponse, "detected_format"));
        assertThat(response.projectName()).isEqualTo(stringValue(expectedResponse, "project_name"));
        assertThat(response.taskCount()).isEqualTo(intValue(expectedResponse, "task_count"));
        assertThat(response.summaryTaskCount()).isEqualTo(intValue(expectedResponse, "summary_task_count"));
        assertThat(response.leafTaskCount()).isEqualTo(intValue(expectedResponse, "leaf_task_count"));
        assertThat(response.resourceCount()).isEqualTo(intValue(expectedResponse, "resource_count"));
        assertThat(response.assignmentCount()).isEqualTo(intValue(expectedResponse, "assignment_count"));
        assertThat(response.calendarCount()).isEqualTo(intValue(expectedResponse, "calendar_count"));
        assertThat(response.customFieldCount()).isEqualTo(intValue(expectedResponse, "custom_field_count"));
        assertThat(response.warningCount()).isEqualTo(intValue(expectedResponse, "warning_count"));
        assertThat(response.errorCount()).isEqualTo(intValue(expectedResponse, "error_count"));
        assertThat(response.notes()).containsExactlyElementsOf(stringList(expectedResponse.get("expected_notes")));
    }

    @Test
    void syntheticExpectedOutputDocumentsSafeSummaryOnlyScope() {
        Path root = repositoryRoot();
        Map<String, Object> expected = parseJson(root.resolve(
                "fixtures/import-export/synthetic-basic-wbs/expected-import-summary.json"
        ));
        String expectedWorkerResponse = map(expected.get("worker_response")).toString().toLowerCase();

        assertThat(expected.get("fixture_id")).isEqualTo("synthetic-basic-wbs");
        assertThat(expected.get("synthetic_or_sanitized")).isEqualTo("synthetic");
        assertThat(expected.get("contains_real_project_data")).isEqualTo(Boolean.FALSE);
        assertThat(expected.get("summary_only")).isEqualTo(Boolean.TRUE);
        assertThat(expectedWorkerResponse)
                .doesNotContain("critical path")
                .doesNotContain("float")
                .doesNotContain("resource levelling")
                .doesNotContain("resource leveling")
                .doesNotContain("recovery scheduling")
                .doesNotContain("automatic date movement")
                .doesNotContain("write-back")
                .doesNotContain("work order")
                .doesNotContain("vendor")
                .doesNotContain("contractor");
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

    private Map<String, Object> parseJson(Path path) {
        return JsonParserFactory.getJsonParser().parseMap(readString(path));
    }

    private String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read test file: " + path, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Object value) {
        return (List<String>) value;
    }

    private String stringValue(Map<String, Object> values, String key) {
        return (String) values.get(key);
    }

    private int intValue(Map<String, Object> values, String key) {
        return ((Number) values.get(key)).intValue();
    }
}
