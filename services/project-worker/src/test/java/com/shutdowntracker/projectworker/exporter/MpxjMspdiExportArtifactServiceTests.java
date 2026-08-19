package com.shutdowntracker.projectworker.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shutdowntracker.projectexport.contract.ProjectExportArtifactField;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactFieldValue;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactRequest;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactSource;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactSummary;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactTask;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mpxj.ProjectFile;
import org.mpxj.Task;
import org.mpxj.reader.UniversalProjectReader;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

class MpxjMspdiExportArtifactServiceTests {

    private static final String NO_SCHEDULE_NOTE =
            "Candidate schedule derived from the accepted source; no schedule calculations or "
                    + "Microsoft Project write-back were run by Shutdown Tracker.";

    /** The committed synthetic schedule every candidate in these tests is derived from. */
    private static final Path SOURCE_FIXTURE = Path.of("..", "..", "fixtures", "import-export",
            "synthetic-basic-wbs", "synthetic-basic-wbs.mspdi.xml").toAbsolutePath().normalize();

    private static ProjectExportArtifactSource TEST_SOURCE;

    private final MpxjMspdiExportArtifactService service = new MpxjMspdiExportArtifactService();

    @TempDir
    private Path tempDir;

    @BeforeAll
    static void resolveSource() throws Exception {
        TEST_SOURCE = new ProjectExportArtifactSource(
                UUID.fromString("00000000-0000-0000-0000-0000000000f1"),
                SOURCE_FIXTURE.toUri().toString(),
                HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(SOURCE_FIXTURE))
                )
        );
    }

    @Test
    void generatesSyntheticMspdiArtifactWithAllowedLeafTaskUpdates() throws Exception {
        Path outputPath = tempDir.resolve("synthetic-export.mspdi.xml");

        ProjectExportArtifactSummary summary = service.generate(syntheticRequest(), SOURCE_FIXTURE, outputPath);

        assertThat(Files.isRegularFile(outputPath)).isTrue();
        assertThat(summary.outputFilename()).isEqualTo("synthetic-export.mspdi.xml");
        assertThat(summary.artifactFormat()).isEqualTo("mspdi_xml");
        assertThat(summary.taskCount()).as("tasks updated").isEqualTo(2);
        assertThat(summary.sourceTaskCount()).as("tasks in the candidate schedule").isEqualTo(6);
        assertThat(summary.exportedFieldCount()).isEqualTo(3);
        assertThat(summary.sizeBytes()).isGreaterThan(0);
        assertThat(summary.sha256()).hasSize(64);
        assertThat(summary.notes()).containsExactly(NO_SCHEDULE_NOTE);
        assertCandidatePreservesSourceSchedule(outputPath);
        assertOnlyApprovedFieldsDiffer(outputPath);

        ProjectFile exportedProject = readProject(outputPath);
        // The source schedule's own name, not the export batch label: renaming a planner's
        // project would itself be a change nobody approved.
        assertThat(exportedProject.getProjectProperties().getName()).isEqualTo("Synthetic Basic WBS");
        assertThat(exportedProject.getTasks()).hasSize(6);
        assertThat(exportedProject.getCalendars()).hasSize(1);

        Task taskA1 = taskWithUid(exportedProject, 2);
        assertThat(taskA1.getUniqueID()).isEqualTo(2);
        assertThat(taskA1.getID()).isEqualTo(2);
        assertThat(taskA1.getPercentageComplete().intValue()).isEqualTo(75);
        assertThat(taskA1.getActualStart()).isEqualTo(LocalDateTime.of(2026, 1, 5, 7, 0));
        // Structure Shutdown Tracker never authored, carried through from the source.
        assertThat(taskA1.getWBS()).isEqualTo("1.1");

        Task taskA2 = taskWithUid(exportedProject, 3);
        assertThat(taskA2.getUniqueID()).isEqualTo(3);
        assertThat(taskA2.getID()).isEqualTo(3);
        assertThat(taskA2.getActualFinish()).isEqualTo(LocalDateTime.of(2026, 1, 6, 15, 0));
        assertThat(taskA2.getPredecessors()).hasSize(1);
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
                TEST_SOURCE,
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
                TEST_SOURCE,
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
                TEST_SOURCE,
                List.of(
                        percentTask("synthetic-task-b1", "5", "5", "Synthetic Task B1", "0"),
                        percentTask("synthetic-task-b2", "6", "6", "Synthetic Task B2", "100.0")
                )
        );
        Path outputPath = tempDir.resolve("whole-number-percent.mspdi.xml");

        service.generate(request, SOURCE_FIXTURE, outputPath);

        assertThat(taskWithUid(readProject(outputPath), 5).getPercentageComplete().intValue()).isZero();
        assertThat(taskWithUid(readProject(outputPath), 6).getPercentageComplete().intValue()).isEqualTo(100);
        assertThat(request.tasks().get(1).fieldValues().getFirst().newValue()).isEqualTo("100");
    }

    @Test
    void preservesReviewedProjectWallClockForOffsetDateTime() throws Exception {
        ProjectExportArtifactRequest request = new ProjectExportArtifactRequest(
                "Synthetic Offset Wall Clock",
                TEST_SOURCE,
                List.of(new ProjectExportArtifactTask(
                        "synthetic-task-b1",
                        "5",
                        "5",
                        "Synthetic Task B1",
                        true,
                        List.of(new ProjectExportArtifactFieldValue(
                                ProjectExportArtifactField.ACTUAL_START,
                                "2026-01-05T16:00:00.000000+08:00"))
                ))
        );
        Path outputPath = tempDir.resolve("offset-wall-clock.mspdi.xml");

        service.generate(request, SOURCE_FIXTURE, outputPath);

        assertThat(request.tasks().getFirst().fieldValues().getFirst().newValue())
                .isEqualTo("2026-01-05T16:00:00+08:00");
        assertThat(taskWithUid(readProject(outputPath), 5).getActualStart())
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

        assertThatThrownBy(() -> new ProjectExportArtifactFieldValue(
                ProjectExportArtifactField.ACTUAL_FINISH,
                "2026-01-05T16:00:00.0000000+08:00"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Project date-time value must be an ISO-8601 offset date-time.");
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
                TEST_SOURCE,
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
                TEST_SOURCE,
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
                TEST_SOURCE,
                List.of(percent, actual)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Each importedTaskId must map to exactly one worker task: 'synthetic-task-shared'.");
    }

    @Test
    void rejectsNonXmlOutputPaths() {
        assertThatThrownBy(() -> service.generate(syntheticRequest(), SOURCE_FIXTURE, tempDir.resolve("synthetic-export.zip")))
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

    /**
     * The approved fields are absent from every task in the accepted source, so each one is an
     * insertion rather than an overwrite. MSPDI declares a task's children as an
     * {@code xsd:sequence}, so where the element lands is part of whether the candidate is a
     * document Microsoft Project will open.
     *
     * <p>Nothing else covers this. The element-name assertions elsewhere in this class check that a
     * field is present, and {@link MspdiCandidateDifference} matches children by name and
     * occurrence, so both are satisfied by a field written in the wrong place.
     */
    @Test
    void insertsApprovedFieldsAtTheirMspdiSchemaPositions() throws Exception {
        Path outputPath = tempDir.resolve("schema-order.mspdi.xml");

        service.generate(syntheticRequest(), SOURCE_FIXTURE, outputPath);

        Element tasks = directChild(parseDocumentElement(outputPath), "Tasks");
        // Task 2 ends at Summary, so both approved fields follow it, in schema order.
        assertThat(directElementNames(taskElement(tasks, "2")))
                .endsWith("Summary", "PercentComplete", "ActualStart");
        // Task 3 carries a dependency, which closes the sequence after the progress fields. An
        // approved field appended to the end of the task would land after it and be out of order.
        assertThat(directElementNames(taskElement(tasks, "3")))
                .endsWith("Summary", "ActualFinish", "PredecessorLink");
    }

    /**
     * A source written by a newer Microsoft Project may carry a task element this MPXJ binding does
     * not model. Its schema position is unknowable, so it cannot decide where an approved field
     * belongs — the known elements around it can.
     */
    @Test
    void placesApprovedFieldsByTheElementsTheBindingKnowsRatherThanTheOnesItDoesNot() throws Exception {
        Path unmodelledSource = tempDir.resolve("unmodelled-element-source.mspdi.xml");
        Files.writeString(unmodelledSource, Files.readString(SOURCE_FIXTURE).replace(
                "<OutlineNumber>1.2</OutlineNumber>",
                "<OutlineNumber>1.2</OutlineNumber>\n      <UnmodelledTaskElement>1</UnmodelledTaskElement>"
        ));
        ProjectExportArtifactRequest request = new ProjectExportArtifactRequest(
                "Synthetic Unmodelled Element",
                sourceDescriptorFor(unmodelledSource),
                List.of(new ProjectExportArtifactTask(
                        "synthetic-task-a2",
                        "3",
                        "3",
                        "Synthetic Task A2",
                        true,
                        List.of(new ProjectExportArtifactFieldValue(
                                ProjectExportArtifactField.ACTUAL_FINISH,
                                "2026-01-06T15:00:00Z"))
                ))
        );
        Path outputPath = tempDir.resolve("unmodelled-element-candidate.mspdi.xml");

        service.generate(request, unmodelledSource, outputPath);

        Element tasks = directChild(parseDocumentElement(outputPath), "Tasks");
        assertThat(directElementNames(taskElement(tasks, "3")))
                .containsSequence("OutlineNumber", "UnmodelledTaskElement", "OutlineLevel")
                .containsSequence("Summary", "ActualFinish", "PredecessorLink");
    }

    private ProjectExportArtifactRequest syntheticRequest() {
        return new ProjectExportArtifactRequest(
                "Synthetic Export Preview",
                TEST_SOURCE,
                List.of(
                        new ProjectExportArtifactTask(
                                "synthetic-task-a1",
                                "2",
                                "2",
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
                                "3",
                                "3",
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
                TEST_SOURCE,
                List.of(percentTask(
                        "synthetic-task-" + suffix,
                        "5",
                        "5",
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

    /**
     * The candidate must be the accepted source with the approved inputs applied, so what matters
     * is that the schedule survived, not that the file was reduced to the approved fields.
     *
     * <p>The previous assertion here required the opposite: an exact root element set of
     * {@code SaveVersion, Name, Tasks} and per-task sets of {@code UID, ID, Name} plus the approved
     * fields. That is a description of a patch document. It passed while the artifact was unusable
     * in Microsoft Project, because a file with no calendars, links or ancestry gives Project
     * nothing to recalculate.
     */
    private void assertCandidatePreservesSourceSchedule(Path outputPath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Element project = factory.newDocumentBuilder().parse(outputPath.toFile()).getDocumentElement();

        assertThat(directElementNames(project)).contains("Calendars", "Tasks", "Name");

        Element tasks = directChild(project, "Tasks");
        // Every task in the source, not only the approved ones.
        assertThat(directElementNames(tasks)).containsOnly("Task").hasSize(6);

        assertThat(directElementNames(taskElement(tasks, "2")))
                .contains("UID", "ID", "Name", "WBS", "OutlineNumber", "OutlineLevel", "Duration",
                        "PercentComplete", "ActualStart");

        // The dependency and the summary tasks are what make this a schedule rather than a patch.
        assertThat(directElementNames(taskElement(tasks, "3"))).contains("PredecessorLink");
        assertThat(taskElement(tasks, "1")).isNotNull();
        assertThat(taskElement(tasks, "4")).isNotNull();
    }

    /** Nothing outside the approved inputs may differ from the accepted source. */
    private void assertOnlyApprovedFieldsDiffer(Path outputPath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Element source = factory.newDocumentBuilder().parse(SOURCE_FIXTURE.toFile()).getDocumentElement();
        Element candidate = factory.newDocumentBuilder().parse(outputPath.toFile()).getDocumentElement();

        Element sourceTasks = directChild(source, "Tasks");
        Element candidateTasks = directChild(candidate, "Tasks");
        for (String uid : List.of("1", "4", "5", "6")) {
            assertThat(directElementNames(taskElement(candidateTasks, uid)))
                    .as("untouched task UID %s", uid)
                    .isEqualTo(directElementNames(taskElement(sourceTasks, uid)));
        }
        assertThat(directChild(candidate, "Name").getTextContent())
                .isEqualTo(directChild(source, "Name").getTextContent());
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

    private Element parseDocumentElement(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(path.toFile()).getDocumentElement();
    }

    private ProjectExportArtifactSource sourceDescriptorFor(Path path) throws Exception {
        return new ProjectExportArtifactSource(
                UUID.fromString("00000000-0000-0000-0000-0000000000f2"),
                path.toUri().toString(),
                HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
                )
        );
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
