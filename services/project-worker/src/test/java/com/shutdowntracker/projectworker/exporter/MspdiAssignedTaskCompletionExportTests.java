package com.shutdowntracker.projectworker.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import com.shutdowntracker.projectexport.contract.ProjectExportArtifactField;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactFieldValue;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactRequest;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactSource;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactSummary;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactTask;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The evidenced completion transaction for an assigned task, exactly as
 * {@code docs/product/project-progress-field-contract.md} derives it from the BOILER round-trip
 * pair: approving 100% with actual dates must write the task actuals, every assignment's actuals,
 * and the timephased remaining-to-actual conversion — and nothing else, which
 * {@link MspdiCandidateDifference} enforces inside generation itself.
 *
 * <p>Task UID 43 is one of the three tasks the disproven console trial used: source
 * {@code Duration} PT8H0M0S, {@code Work} PT16H0M0S, one assignment (UID 45, {@code Work}
 * PT16H0M0S) carrying a single {@code Type} 1 timephased block.
 */
class MspdiAssignedTaskCompletionExportTests {

    private static final String MSPDI_NS = "http://schemas.microsoft.com/project";
    private static final Path BOILER_FIXTURE = Path.of("..", "..", "fixtures", "project-files",
            "boiler", "boiler-before-no-progress.xml").toAbsolutePath().normalize();
    private static final String ACTUAL_START = "2026-08-17T07:30:00";
    private static final String ACTUAL_FINISH = "2026-08-17T15:30:00";

    private static ProjectExportArtifactSource BOILER_SOURCE;

    private final MpxjMspdiExportArtifactService service = new MpxjMspdiExportArtifactService();

    @TempDir
    private Path tempDir;

    @BeforeAll
    static void resolveSource() throws Exception {
        BOILER_SOURCE = new ProjectExportArtifactSource(
                UUID.fromString("00000000-0000-0000-0000-0000000000b2"),
                BOILER_FIXTURE.toUri().toString(),
                HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(BOILER_FIXTURE))
                )
        );
    }

    @Test
    void writesTheCompleteEvidencedTransactionForACompletedAssignedTask() throws Exception {
        ProjectExportArtifactRequest request = new ProjectExportArtifactRequest(
                "BOILER WG110 BLB001",
                BOILER_SOURCE,
                List.of(new ProjectExportArtifactTask(
                        "boiler-task-43",
                        "43",
                        "3",
                        "Conduct all pre-work scaffold lifts",
                        true,
                        List.of(
                                new ProjectExportArtifactFieldValue(
                                        ProjectExportArtifactField.PERCENT_COMPLETE, "100"),
                                new ProjectExportArtifactFieldValue(
                                        ProjectExportArtifactField.ACTUAL_START, ACTUAL_START + "Z"),
                                new ProjectExportArtifactFieldValue(
                                        ProjectExportArtifactField.ACTUAL_FINISH, ACTUAL_FINISH + "Z")
                        )
                ))
        );
        Path outputPath = tempDir.resolve("boiler-completion.mspdi.xml");

        // Generation itself verifies, via MspdiCandidateDifference, that nothing beyond the
        // approved inputs and the recorded derived completion values differs from the source.
        ProjectExportArtifactSummary summary = service.generate(request, BOILER_FIXTURE, outputPath);
        assertThat(summary.exportedFieldCount()).isEqualTo(3);

        Document candidate = parse(outputPath);
        Element task = elementWithUid(candidate, "Task", "43");

        assertThat(childText(task, "PercentComplete")).isEqualTo("100");
        assertThat(childText(task, "PercentWorkComplete")).isEqualTo("100");
        assertThat(childText(task, "ActualStart")).isEqualTo(ACTUAL_START);
        assertThat(childText(task, "ActualFinish")).isEqualTo(ACTUAL_FINISH);
        assertThat(childText(task, "ActualDuration")).isEqualTo("PT8H0M0S");
        assertThat(childText(task, "ActualWork")).isEqualTo("PT16H0M0S");
        assertThat(childText(task, "RemainingDuration")).isEqualTo("PT0H0M0S");
        assertThat(childText(task, "RemainingWork")).isEqualTo("PT0H0M0S");
        assertThat(childText(task, "Stop")).isEqualTo(ACTUAL_FINISH);
        assertThat(childText(task, "Resume")).isEqualTo(ACTUAL_FINISH);

        Element assignment = assignmentOfTask(candidate, "43");
        assertThat(childText(assignment, "PercentWorkComplete")).isEqualTo("100");
        assertThat(childText(assignment, "ActualStart")).isEqualTo(ACTUAL_START);
        assertThat(childText(assignment, "ActualFinish")).isEqualTo(ACTUAL_FINISH);
        assertThat(childText(assignment, "ActualWork")).isEqualTo("PT16H0M0S");
        assertThat(childText(assignment, "RemainingWork")).isEqualTo("PT0H0M0S");
        assertThat(childText(assignment, "Stop")).isEqualTo(ACTUAL_FINISH);
        assertThat(childText(assignment, "Resume")).isEqualTo(ACTUAL_FINISH);

        List<Element> timephased = childElements(assignment, "TimephasedData");
        assertThat(timephased).as("the single planned block must survive as the actual block").hasSize(1);
        assertThat(childText(timephased.get(0), "Type"))
                .as("planned/remaining work (Type 1) becomes actual work (Type 2)")
                .isEqualTo("2");
        assertThat(childText(timephased.get(0), "Value"))
                .as("the conversion keeps the block's window and value")
                .isEqualTo("PT16H0M0S");
    }

    @Test
    void leavesEveryOtherAssignmentUntouched() throws Exception {
        ProjectExportArtifactRequest request = new ProjectExportArtifactRequest(
                "BOILER WG110 BLB001",
                BOILER_SOURCE,
                List.of(new ProjectExportArtifactTask(
                        "boiler-task-43",
                        "43",
                        "3",
                        "Conduct all pre-work scaffold lifts",
                        true,
                        List.of(
                                new ProjectExportArtifactFieldValue(
                                        ProjectExportArtifactField.PERCENT_COMPLETE, "100"),
                                new ProjectExportArtifactFieldValue(
                                        ProjectExportArtifactField.ACTUAL_START, ACTUAL_START + "Z"),
                                new ProjectExportArtifactFieldValue(
                                        ProjectExportArtifactField.ACTUAL_FINISH, ACTUAL_FINISH + "Z")
                        )
                ))
        );
        Path outputPath = tempDir.resolve("boiler-completion-others.mspdi.xml");
        service.generate(request, BOILER_FIXTURE, outputPath);

        Document candidate = parse(outputPath);
        NodeList assignments = candidate.getElementsByTagNameNS(MSPDI_NS, "Assignment");
        int untouched = 0;
        for (int index = 0; index < assignments.getLength(); index++) {
            Element assignment = (Element) assignments.item(index);
            if ("43".equals(childText(assignment, "TaskUID"))) {
                continue;
            }
            assertThat(childText(assignment, "PercentWorkComplete"))
                    .as("assignment of another task must keep its source progress")
                    .isNotEqualTo("100");
            untouched++;
        }
        assertThat(untouched).isGreaterThan(100);
    }

    private Document parse(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(Files.readAllBytes(path)));
    }

    private Element elementWithUid(Document document, String localName, String uid) {
        NodeList nodes = document.getElementsByTagNameNS(MSPDI_NS, localName);
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            if (uid.equals(childText(element, "UID"))) {
                return element;
            }
        }
        throw new AssertionError(localName + " with UID " + uid + " not found in candidate");
    }

    private Element assignmentOfTask(Document document, String taskUid) {
        NodeList nodes = document.getElementsByTagNameNS(MSPDI_NS, "Assignment");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            if (taskUid.equals(childText(element, "TaskUID"))) {
                return element;
            }
        }
        throw new AssertionError("Assignment of task UID " + taskUid + " not found in candidate");
    }

    private String childText(Element parent, String localName) {
        Node child = parent.getFirstChild();
        while (child != null) {
            if (child instanceof Element element && localName.equals(element.getLocalName())) {
                return element.getTextContent().trim();
            }
            child = child.getNextSibling();
        }
        return null;
    }

    private List<Element> childElements(Element parent, String localName) {
        List<Element> elements = new ArrayList<>();
        Node child = parent.getFirstChild();
        while (child != null) {
            if (child instanceof Element element && localName.equals(element.getLocalName())) {
                elements.add(element);
            }
            child = child.getNextSibling();
        }
        return elements;
    }
}
