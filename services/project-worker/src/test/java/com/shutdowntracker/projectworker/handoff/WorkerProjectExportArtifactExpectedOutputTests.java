package com.shutdowntracker.projectworker.handoff;

import static org.assertj.core.api.Assertions.assertThat;

import com.shutdowntracker.projectexport.contract.ProjectExportArtifactField;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactFieldValue;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactGenerationRequest;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactGenerationResponse;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactRequest;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactSummary;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactTask;
import com.shutdowntracker.projectworker.exporter.MpxjMspdiExportArtifactService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mpxj.ProjectFile;
import org.mpxj.Task;
import org.mpxj.reader.UniversalProjectReader;
import org.springframework.boot.json.JsonParserFactory;

class WorkerProjectExportArtifactExpectedOutputTests {

    private final WorkerProjectExportArtifactHandoffService service =
            new WorkerProjectExportArtifactHandoffService(new MpxjMspdiExportArtifactService());

    @TempDir
    private Path tempDir;

    @Test
    void syntheticExportArtifactMatchesExpectedOutputSummary() {
        Path root = repositoryRoot();
        Path expectedPath = root.resolve(
                "fixtures/import-export/synthetic-basic-wbs/expected-export-artifact-summary.json"
        );
        Map<String, Object> expected = parseJson(expectedPath);
        Map<String, Object> expectedSummary = map(expected.get("expected_summary"));
        Path outputPath = tempDir.resolve(stringValue(expected, "output_filename"));
        UUID exportBatchId = UUID.fromString("00000000-0000-0000-0000-000000000029");
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000030");

        ProjectExportArtifactGenerationResponse response = service.generateArtifact(new ProjectExportArtifactGenerationRequest(
                exportBatchId,
                projectId,
                outputPath.toString(),
                artifactRequest(expected)
        ));

        ProjectExportArtifactSummary summary = response.artifactSummary();
        assertThat(response.exportBatchId()).isEqualTo(exportBatchId);
        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.exportFileUri()).isEqualTo(outputPath.toAbsolutePath().normalize().toUri().toString());
        assertThat(response.exportFileHash()).matches(stringValue(expectedSummary, "sha256_pattern"));
        assertThat(response.message()).contains("No Microsoft Project write-back");

        assertThat(Files.isRegularFile(outputPath)).isTrue();
        assertThat(summary.outputFilename()).isEqualTo(stringValue(expected, "output_filename"));
        assertThat(summary.artifactFormat()).isEqualTo(stringValue(expected, "artifact_format"));
        assertThat(summary.taskCount()).isEqualTo(intValue(expectedSummary, "task_count"));
        assertThat(summary.exportedFieldCount()).isEqualTo(intValue(expectedSummary, "exported_field_count"));
        assertThat(summary.sizeBytes()).isGreaterThanOrEqualTo(longValue(expectedSummary, "minimum_size_bytes"));
        assertThat(summary.sha256()).matches(stringValue(expectedSummary, "sha256_pattern"));
        assertThat(summary.notes()).containsExactlyElementsOf(stringList(expectedSummary.get("expected_notes")));
        assertThat(response.exportFileHash()).isEqualTo(summary.sha256());

        assertGeneratedArtifactMatchesExpectedTasks(outputPath, expected);
        assertThat(readString(outputPath))
                .doesNotContain(
                        "<PhysicalPercentComplete>",
                        "<WBS>",
                        "<Duration>",
                        "<PredecessorLink>",
                        "<Calendars>",
                        "<Resources>",
                        "<Assignments>"
                );
        assertThat(root.resolve("fixtures/import-export/synthetic-basic-wbs/synthetic-export.mspdi.xml"))
                .doesNotExist();
    }

    @Test
    void syntheticExportExpectedOutputDocumentsSafeTemporaryScope() {
        Path root = repositoryRoot();
        Map<String, Object> expected = parseJson(root.resolve(
                "fixtures/import-export/synthetic-basic-wbs/expected-export-artifact-summary.json"
        ));

        assertThat(expected.get("fixture_id")).isEqualTo("synthetic-basic-wbs");
        assertThat(expected.get("artifact_id")).isEqualTo("synthetic-export-leaf-actuals");
        assertThat(expected.get("synthetic_or_sanitized")).isEqualTo("synthetic");
        assertThat(expected.get("contains_real_project_data")).isEqualTo(Boolean.FALSE);
        assertThat(expected.get("generated_artifact_committed")).isEqualTo(Boolean.FALSE);
        assertThat(stringList(expected.get("excluded_scope")))
                .contains(
                        "summary task exports",
                        "native MPP writing",
                        "Microsoft Project automation",
                        "Project write-back",
                        "schedule calculations"
                );
        assertThat(stringList(expected.get("notes")))
                .contains(
                        "Generated MSPDI/XML artifacts remain temporary test output and must not be committed.",
                        "No real names, work orders, sites, assets, vendors, people, locations, costs, or commercial data."
                );
    }

    private ProjectExportArtifactRequest artifactRequest(Map<String, Object> expected) {
        List<ProjectExportArtifactTask> tasks = objectList(expected.get("expected_tasks")).stream()
                .map(this::artifactTask)
                .toList();
        return new ProjectExportArtifactRequest(stringValue(expected, "project_name"), tasks);
    }

    private ProjectExportArtifactTask artifactTask(Map<String, Object> expectedTask) {
        List<ProjectExportArtifactFieldValue> fieldValues = map(expectedTask.get("expected_fields")).entrySet().stream()
                .map(entry -> new ProjectExportArtifactFieldValue(
                        ProjectExportArtifactField.fromFieldName(entry.getKey()),
                        String.valueOf(entry.getValue())
                ))
                .toList();

        return new ProjectExportArtifactTask(
                stringValue(expectedTask, "imported_task_id"),
                String.valueOf(intValue(expectedTask, "microsoft_project_task_uid")),
                String.valueOf(intValue(expectedTask, "microsoft_project_task_id")),
                stringValue(expectedTask, "task_name"),
                booleanValue(expectedTask, "leaf_task"),
                fieldValues
        );
    }

    private void assertGeneratedArtifactMatchesExpectedTasks(Path outputPath, Map<String, Object> expected) {
        ProjectFile exportedProject = readProject(outputPath);
        assertThat(exportedProject.getProjectProperties().getName()).isEqualTo(stringValue(expected, "project_name"));

        for (Map<String, Object> expectedTask : objectList(expected.get("expected_tasks"))) {
            int taskUid = intValue(expectedTask, "microsoft_project_task_uid");
            Task actualTask = taskWithUid(exportedProject, taskUid);
            assertThat(actualTask.getUniqueID()).isEqualTo(taskUid);
            assertThat(actualTask.getID()).isEqualTo(intValue(expectedTask, "microsoft_project_task_id"));
            assertThat(actualTask.getName()).isEqualTo(stringValue(expectedTask, "task_name"));

            Map<String, Object> expectedFields = map(expectedTask.get("expected_fields"));
            if (expectedFields.containsKey("percent_complete")) {
                assertThat(actualTask.getPercentageComplete().intValue()).isEqualTo(intValue(expectedFields, "percent_complete"));
            }
            if (expectedFields.containsKey("actual_start")) {
                assertThat(actualTask.getActualStart()).isEqualTo(dateTimeValue(expectedFields, "actual_start"));
            }
            if (expectedFields.containsKey("actual_finish")) {
                assertThat(actualTask.getActualFinish()).isEqualTo(dateTimeValue(expectedFields, "actual_finish"));
            }
        }
    }

    private ProjectFile readProject(Path path) {
        try {
            UniversalProjectReader reader = new UniversalProjectReader();
            try (UniversalProjectReader.ProjectReaderProxy proxy =
                         reader.getProjectReaderProxy(path.toFile())) {
                return proxy.read();
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read generated MSPDI/XML artifact.", ex);
        }
    }

    private Task taskWithUid(ProjectFile project, int uid) {
        return project.getTasks().stream()
                .filter(task -> task != null && Integer.valueOf(uid).equals(task.getUniqueID()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected task UID was not found: " + uid));
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path fixture = current.resolve(
                    "fixtures/import-export/synthetic-basic-wbs/expected-export-artifact-summary.json"
            );
            if (Files.isRegularFile(fixture)) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Repository root with export expected-output fixture was not found.");
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
    private List<Map<String, Object>> objectList(Object value) {
        return (List<Map<String, Object>>) value;
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

    private long longValue(Map<String, Object> values, String key) {
        return ((Number) values.get(key)).longValue();
    }

    private boolean booleanValue(Map<String, Object> values, String key) {
        return (Boolean) values.get(key);
    }

    private LocalDateTime dateTimeValue(Map<String, Object> values, String key) {
        return LocalDateTime.parse(stringValue(values, key));
    }
}
