package com.shutdowntracker.projectworker.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shutdowntracker.projectexport.contract.ProjectExportArtifactField;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactFieldValue;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactRequest;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactSummary;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactTask;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mpxj.ProjectFile;
import org.mpxj.Task;
import org.mpxj.reader.UniversalProjectReader;

class MpxjMspdiExportArtifactServiceTests {

    private static final String NO_SCHEDULE_NOTE =
            "MSPDI/XML artifact only; no schedule calculations or Microsoft Project write-back were run.";

    private final MpxjMspdiExportArtifactService service = new MpxjMspdiExportArtifactService();

    @TempDir
    private Path tempDir;

    @Test
    void generatesSyntheticMspdiArtifactWithAllowedLeafTaskUpdates() throws Exception {
        Path outputPath = tempDir.resolve("synthetic-export.mspdi.xml");

        ProjectExportArtifactSummary summary = service.generate(syntheticRequest(), outputPath);

        assertThat(Files.isRegularFile(outputPath)).isTrue();
        assertThat(summary.outputFilename()).isEqualTo("synthetic-export.mspdi.xml");
        assertThat(summary.artifactFormat()).isEqualTo("mspdi_xml");
        assertThat(summary.taskCount()).isEqualTo(2);
        assertThat(summary.exportedFieldCount()).isEqualTo(4);
        assertThat(summary.sizeBytes()).isGreaterThan(0);
        assertThat(summary.sha256()).hasSize(64);
        assertThat(summary.notes()).containsExactly(NO_SCHEDULE_NOTE);

        ProjectFile exportedProject = readProject(outputPath);
        assertThat(exportedProject.getProjectProperties().getName()).isEqualTo("Synthetic Export Preview");

        Task taskA1 = taskNamed(exportedProject, "Synthetic Task A1");
        assertThat(taskA1.getUniqueID()).isEqualTo(101);
        assertThat(taskA1.getID()).isEqualTo(1);
        assertThat(taskA1.getPercentageComplete().intValue()).isEqualTo(75);
        assertThat(taskA1.getActualStart()).isEqualTo(LocalDateTime.of(2026, 1, 5, 7, 0));

        Task taskA2 = taskNamed(exportedProject, "Synthetic Task A2");
        assertThat(taskA2.getUniqueID()).isEqualTo(102);
        assertThat(taskA2.getID()).isEqualTo(2);
        assertThat(taskA2.getPhysicalPercentComplete().intValue()).isEqualTo(50);
        assertThat(taskA2.getActualFinish()).isEqualTo(LocalDateTime.of(2026, 1, 6, 15, 0));
    }

    @Test
    void rejectsSummaryTaskExportCandidates() {
        ProjectExportArtifactRequest request = new ProjectExportArtifactRequest(
                "Synthetic Summary Rejection",
                List.of(new ProjectExportArtifactTask(
                        "synthetic-summary-a",
                        "200",
                        "20",
                        "Synthetic Summary A",
                        false,
                        List.of(new ProjectExportArtifactFieldValue(
                                ProjectExportArtifactField.ACTUAL_FINISH,
                                "2026-01-06T15:00:00Z"))
                ))
        );
        Path outputPath = tempDir.resolve("summary-rejected.mspdi.xml");

        assertThatThrownBy(() -> service.generate(request, outputPath))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only leaf-task export candidates");
        assertThat(outputPath).doesNotExist();
    }

    @Test
    void rejectsInvalidProgressPercentages() {
        ProjectExportArtifactRequest request = new ProjectExportArtifactRequest(
                "Synthetic Invalid Percent",
                List.of(new ProjectExportArtifactTask(
                        "synthetic-task-invalid",
                        "201",
                        "21",
                        "Synthetic Task Invalid",
                        true,
                        List.of(new ProjectExportArtifactFieldValue(
                                ProjectExportArtifactField.PERCENT_COMPLETE,
                                "101"))
                ))
        );

        assertThatThrownBy(() -> service.generate(request, tempDir.resolve("invalid-percent.mspdi.xml")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("percent_complete must be between 0 and 100");
    }

    @Test
    void rejectsNonXmlOutputPaths() {
        assertThatThrownBy(() -> service.generate(syntheticRequest(), tempDir.resolve("synthetic-export.zip")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must end with .xml");
    }

    @Test
    void rejectsNonNumericMicrosoftProjectTaskIdentity() {
        ProjectExportArtifactRequest request = new ProjectExportArtifactRequest(
                "Synthetic Invalid Identity",
                List.of(new ProjectExportArtifactTask(
                        "synthetic-task-invalid-id",
                        "not-a-number",
                        "22",
                        "Synthetic Task Invalid Identity",
                        true,
                        List.of(new ProjectExportArtifactFieldValue(
                                ProjectExportArtifactField.ACTUAL_START,
                                "2026-01-05T07:00:00Z"))
                ))
        );

        assertThatThrownBy(() -> service.generate(request, tempDir.resolve("invalid-identity.mspdi.xml")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("microsoftProjectTaskUid must be a positive integer");
    }

    private ProjectExportArtifactRequest syntheticRequest() {
        return new ProjectExportArtifactRequest(
                "Synthetic Export Preview",
                List.of(
                        new ProjectExportArtifactTask(
                                "synthetic-task-a1",
                                "101",
                                "1",
                                "Synthetic Task A1",
                                true,
                                List.of(
                                        new ProjectExportArtifactFieldValue(
                                                ProjectExportArtifactField.PERCENT_COMPLETE,
                                                "75"),
                                        new ProjectExportArtifactFieldValue(
                                                ProjectExportArtifactField.ACTUAL_START,
                                                "2026-01-05T07:00:00Z")
                                )
                        ),
                        new ProjectExportArtifactTask(
                                "synthetic-task-a2",
                                "102",
                                "2",
                                "Synthetic Task A2",
                                true,
                                List.of(
                                        new ProjectExportArtifactFieldValue(
                                                ProjectExportArtifactField.PHYSICAL_PERCENT_COMPLETE,
                                                "50"),
                                        new ProjectExportArtifactFieldValue(
                                                ProjectExportArtifactField.ACTUAL_FINISH,
                                                "2026-01-06T15:00:00Z")
                                )
                        )
                )
        );
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

    private Task taskNamed(ProjectFile project, String name) {
        return project.getTasks().stream()
                .filter(task -> task != null && name.equals(task.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected task was not found: " + name));
    }
}
