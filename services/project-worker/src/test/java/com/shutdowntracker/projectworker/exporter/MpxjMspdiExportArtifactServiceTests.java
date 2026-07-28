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
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mpxj.ProjectFile;
import org.mpxj.Task;
import org.mpxj.reader.UniversalProjectReader;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

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
        assertThat(summary.exportedFieldCount()).isEqualTo(3);
        assertThat(summary.sizeBytes()).isGreaterThan(0);
        assertThat(summary.sha256()).hasSize(64);
        assertThat(summary.notes()).containsExactly(NO_SCHEDULE_NOTE);
        assertArtifactAuthority(outputPath);

        ProjectFile exportedProject = readProject(outputPath);
        assertThat(exportedProject.getProjectProperties().getName()).isEqualTo("Synthetic Export Preview");

        Task taskA1 = taskWithUid(exportedProject, 101);
        assertThat(taskA1.getUniqueID()).isEqualTo(101);
        assertThat(taskA1.getID()).isEqualTo(1);
        assertThat(taskA1.getPercentageComplete().intValue()).isEqualTo(75);
        assertThat(taskA1.getActualStart()).isEqualTo(LocalDateTime.of(2026, 1, 5, 7, 0));

        Task taskA2 = taskWithUid(exportedProject, 102);
        assertThat(taskA2.getUniqueID()).isEqualTo(102);
        assertThat(taskA2.getID()).isEqualTo(2);
        assertThat(taskA2.getActualFinish()).isEqualTo(LocalDateTime.of(2026, 1, 6, 15, 0));
    }

    @Test
    void rejectsPhysicalPercentCompleteAtTheSharedContractBoundary() {
        assertThatThrownBy(() -> ProjectExportArtifactField.fromFieldName("physical_percent_complete"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported export artifact field: physical_percent_complete");
    }

    @Test
    void rejectsDuplicateTaskFieldCandidatesAtTheSharedContractBoundary() {
        ProjectExportArtifactTask first = new ProjectExportArtifactTask(
                "synthetic-task-a1",
                "101",
                "1",
                "Synthetic Task A1",
                true,
                List.of(new ProjectExportArtifactFieldValue(
                        ProjectExportArtifactField.PERCENT_COMPLETE,
                        "75"
                ))
        );
        ProjectExportArtifactTask duplicate = new ProjectExportArtifactTask(
                "synthetic-task-a1",
                "101",
                "1",
                "Synthetic Task A1",
                true,
                List.of(new ProjectExportArtifactFieldValue(
                        ProjectExportArtifactField.PERCENT_COMPLETE,
                        "75"
                ))
        );

        assertThatThrownBy(() -> new ProjectExportArtifactRequest(
                "Synthetic Duplicate Rejection",
                List.of(first, duplicate)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Duplicate export artifact candidate for importedTaskId 'synthetic-task-a1' "
                                + "and field 'percent_complete'."
                );
    }

    @Test
    void rejectsSummaryTaskExportCandidates() {
        Path outputPath = tempDir.resolve("summary-rejected.mspdi.xml");

        assertThatThrownBy(() -> new ProjectExportArtifactRequest(
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
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only leaf-task export candidates");
        assertThat(outputPath).doesNotExist();
    }

    @Test
    void rejectsInvalidProgressPercentages() {
        assertThatThrownBy(() -> requestWithPercent("101", "invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Percent complete must be between 0 and 100.");
    }

    @Test
    void rejectsFractionalProgressInsteadOfRoundingIt() {
        Path outputPath = tempDir.resolve("fractional-percent.mspdi.xml");

        assertThatThrownBy(() -> requestWithPercent("75.5", "fractional"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Percent complete must be a whole number between 0 and 100.");
        assertThat(outputPath).doesNotExist();
    }

    @Test
    void acceptsWholeNumberProgressBoundariesWithoutRounding() throws Exception {
        ProjectExportArtifactRequest request = new ProjectExportArtifactRequest(
                "Synthetic Percent Boundaries",
                List.of(
                        percentTask("synthetic-zero", "301", "31", "Synthetic Zero", "0"),
                        percentTask("synthetic-hundred", "302", "32", "Synthetic Hundred", "100.0")
                )
        );
        Path outputPath = tempDir.resolve("whole-number-percent.mspdi.xml");

        service.generate(request, outputPath);

        assertThat(taskWithUid(readProject(outputPath), 301).getPercentageComplete().intValue()).isZero();
        assertThat(taskWithUid(readProject(outputPath), 302).getPercentageComplete().intValue()).isEqualTo(100);
        assertThat(request.tasks().get(1).fieldValues().getFirst().newValue()).isEqualTo("100");
    }

    @Test
    void preservesReviewedProjectWallClockForOffsetDateTime() throws Exception {
        ProjectExportArtifactRequest request = new ProjectExportArtifactRequest(
                "Synthetic Offset Wall Clock",
                List.of(new ProjectExportArtifactTask(
                        "synthetic-offset-task",
                        "401",
                        "41",
                        "Synthetic Offset Task",
                        true,
                        List.of(new ProjectExportArtifactFieldValue(
                                ProjectExportArtifactField.ACTUAL_START,
                                "2026-01-05T16:00:00.000000+08:00"))
                ))
        );
        Path outputPath = tempDir.resolve("offset-wall-clock.mspdi.xml");

        service.generate(request, outputPath);

        assertThat(request.tasks().getFirst().fieldValues().getFirst().newValue())
                .isEqualTo("2026-01-05T16:00:00+08:00");
        assertThat(taskWithUid(readProject(outputPath), 401).getActualStart())
                .isEqualTo(LocalDateTime.of(2026, 1, 5, 16, 0));
    }

    @Test
    void rejectsInvalidOrOffsetFreeDateTimeAtTheSharedContractBoundary() {
        assertThatThrownBy(() -> new ProjectExportArtifactFieldValue(
                ProjectExportArtifactField.ACTUAL_FINISH,
                "2026-01-05T16:00:00"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Project date-time value must be an ISO-8601 offset date-time.");

        ProjectExportArtifactFieldValue minutePrecision = new ProjectExportArtifactFieldValue(
                ProjectExportArtifactField.ACTUAL_FINISH,
                "2026-01-05T16:00+08:00"
        );
        assertThat(minutePrecision.newValue()).isEqualTo("2026-01-05T16:00:00+08:00");

        assertThatThrownBy(() -> new ProjectExportArtifactFieldValue(
                ProjectExportArtifactField.ACTUAL_FINISH,
                "2026-01-05T16:00:00+0800"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Project date-time value must be an ISO-8601 offset date-time.");

        assertThatThrownBy(() -> new ProjectExportArtifactFieldValue(
                ProjectExportArtifactField.ACTUAL_FINISH,
                "2026-01-05T16:00:00.001+08:00"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Project date-time values support whole-second precision.");

        assertThatThrownBy(() -> new ProjectExportArtifactFieldValue(
                ProjectExportArtifactField.ACTUAL_FINISH,
                "2026-01-05T16:00:00.000001+08:00"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Project date-time values support whole-second precision.");
    }

    @Test
    void rejectsMissingOrInconsistentTaskIdentityAtTheSharedContractBoundary() {
        assertThatThrownBy(() -> new ProjectExportArtifactTask(
                "synthetic-missing-uid",
                " ",
                "42",
                "Synthetic Missing UID",
                true,
                List.of(new ProjectExportArtifactFieldValue(ProjectExportArtifactField.PERCENT_COMPLETE, "75"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("microsoftProjectTaskUid is required.");

        assertThatThrownBy(() -> percentTask("synthetic-leading-zero-id", "501", "01", "Task", "25"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("microsoftProjectTaskId must be a canonical positive integer.");

        assertThatThrownBy(() -> percentTask("synthetic-plus-uid", "+501", "51", "Task", "25"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("microsoftProjectTaskUid must be a canonical positive integer.");

        assertThatThrownBy(() -> percentTask("synthetic-overflow-id", "501", "2147483648", "Task", "25"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("microsoftProjectTaskId must be a canonical positive integer.");

        ProjectExportArtifactTask first = percentTask("synthetic-task-one", "501", "51", "Task One", "25");
        ProjectExportArtifactTask reusedProjectId = percentTask(
                "synthetic-task-two",
                "502",
                "51",
                "Task Two",
                "50"
        );
        assertThatThrownBy(() -> new ProjectExportArtifactRequest(
                "Synthetic Reused Identity",
                List.of(first, reusedProjectId)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Each Microsoft Project task ID must map to exactly one imported task: '51'.");

        ProjectExportArtifactTask reusedProjectUid = percentTask(
                "synthetic-task-three",
                "501",
                "52",
                "Task Three",
                "75"
        );
        assertThatThrownBy(() -> new ProjectExportArtifactRequest(
                "Synthetic Reused Identity",
                List.of(first, reusedProjectUid)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Each Microsoft Project task UID must map to exactly one imported task: '501'.");
    }

    @Test
    void rejectsSplittingOneImportedTaskAcrossDifferentWorkerTaskObjects() {
        assertThatThrownBy(() -> percentTask(" synthetic-task-shared", "600", "60", "Task", "25"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("importedTaskId must not contain leading or trailing whitespace.");

        ProjectExportArtifactTask percent = percentTask(
                "synthetic-task-shared",
                "601",
                "61",
                "Synthetic Shared Task",
                "25"
        );
        ProjectExportArtifactTask actual = new ProjectExportArtifactTask(
                "synthetic-task-shared",
                "602",
                "62",
                "Synthetic Shared Task Changed",
                true,
                List.of(new ProjectExportArtifactFieldValue(
                        ProjectExportArtifactField.ACTUAL_START,
                        "2026-01-05T16:00:00+08:00"))
        );

        assertThatThrownBy(() -> new ProjectExportArtifactRequest(
                "Synthetic Split Identity",
                List.of(percent, actual)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Each importedTaskId must map to exactly one worker task: 'synthetic-task-shared'.");
    }

    @Test
    void rejectsNonXmlOutputPaths() {
        assertThatThrownBy(() -> service.generate(syntheticRequest(), tempDir.resolve("synthetic-export.zip")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must end with .xml");
    }

    @Test
    void rejectsNonNumericMicrosoftProjectTaskIdentity() {
        assertThatThrownBy(() -> new ProjectExportArtifactTask(
                        "synthetic-task-invalid-id",
                        "not-a-number",
                        "22",
                        "Synthetic Task Invalid Identity",
                        true,
                        List.of(new ProjectExportArtifactFieldValue(
                                ProjectExportArtifactField.ACTUAL_START,
                                "2026-01-05T07:00:00Z"))
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("microsoftProjectTaskUid must be a canonical positive integer.");
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
                                                ProjectExportArtifactField.ACTUAL_FINISH,
                                                "2026-01-06T15:00:00Z")
                                )
                        )
                )
        );
    }

    private ProjectExportArtifactRequest requestWithPercent(String value, String suffix) {
        return new ProjectExportArtifactRequest(
                "Synthetic Percent " + suffix,
                List.of(percentTask(
                        "synthetic-task-" + suffix,
                        "301",
                        "31",
                        "Synthetic Task " + suffix,
                        value
                ))
        );
    }

    private ProjectExportArtifactTask percentTask(
            String importedTaskId,
            String microsoftProjectTaskUid,
            String microsoftProjectTaskId,
            String taskName,
            String value
    ) {
        return new ProjectExportArtifactTask(
                importedTaskId,
                microsoftProjectTaskUid,
                microsoftProjectTaskId,
                taskName,
                true,
                List.of(new ProjectExportArtifactFieldValue(ProjectExportArtifactField.PERCENT_COMPLETE, value))
        );
    }

    private void assertArtifactAuthority(Path outputPath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Element project = factory.newDocumentBuilder().parse(outputPath.toFile()).getDocumentElement();

        assertThat(directElementNames(project))
                .containsExactlyInAnyOrder("SaveVersion", "Name", "Tasks");
        Element tasks = directChild(project, "Tasks");
        assertThat(directElementNames(tasks)).containsOnly("Task").hasSize(2);
        assertThat(directElementNames(taskElement(tasks, "101")))
                .containsExactlyInAnyOrder("UID", "ID", "Name", "PercentComplete", "ActualStart");
        assertThat(directElementNames(taskElement(tasks, "102")))
                .containsExactlyInAnyOrder("UID", "ID", "Name", "ActualFinish");
    }

    private Element taskElement(Element tasks, String taskUid) {
        Node child = tasks.getFirstChild();
        while (child != null) {
            if (child instanceof Element element
                    && "Task".equals(element.getLocalName())
                    && taskUid.equals(directChild(element, "UID").getTextContent())) {
                return element;
            }
            child = child.getNextSibling();
        }
        throw new AssertionError("Expected XML task UID was not found: " + taskUid);
    }

    private Element directChild(Element parent, String localName) {
        Node child = parent.getFirstChild();
        while (child != null) {
            if (child instanceof Element element && localName.equals(element.getLocalName())) {
                return element;
            }
            child = child.getNextSibling();
        }
        throw new AssertionError("Expected XML element was not found: " + localName);
    }

    private List<String> directElementNames(Element parent) {
        List<String> names = new ArrayList<>();
        Node child = parent.getFirstChild();
        while (child != null) {
            if (child instanceof Element element) {
                names.add(element.getLocalName());
            }
            child = child.getNextSibling();
        }
        return names;
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
}
