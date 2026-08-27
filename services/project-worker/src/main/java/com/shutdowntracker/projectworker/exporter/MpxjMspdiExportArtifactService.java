package com.shutdowntracker.projectworker.exporter;

import com.shutdowntracker.projectexport.contract.ProjectExportArtifactField;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactFieldValue;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactRequest;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactSummary;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactTask;
import com.shutdowntracker.projectexport.contract.ProjectExportValueNormalizer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Produces a candidate schedule by applying approved execution inputs to the accepted source.
 *
 * <p>The candidate is the source document with the approved fields written into it, and nothing
 * else touched. It is produced by editing the source XML directly rather than by reading it into a
 * schedule model and writing that model back out: a round trip through any intermediate model can
 * only preserve what the model represents, and anything it does not represent would be dropped from
 * a file that still looked like a schedule. Editing the document in place cannot lose a construct
 * it never parsed.
 *
 * <p>That is what makes the candidate usable. Calendars, predecessor links, WBS and outline
 * ancestry, summary structure, resources, assignments, durations and constraints all survive, so
 * Microsoft Project has a real schedule to recalculate and the planner has something they can
 * review, merge, or adopt.
 *
 * <p>Authority is enforced by {@link MspdiCandidateDifference}, which compares the generated
 * candidate against the source and requires that only approved {@code (task, field)} pairs differ.
 * This is a stronger guarantee than the element allowlist it replaces: an allowlist that deletes
 * everything else proves nothing about what it deleted, whereas differencing proves every other
 * value in the file is exactly the accepted source's. Summary-task actuals, planned dates,
 * dependencies, constraints and calendars are therefore provably unmodified by Shutdown Tracker
 * rather than merely absent.
 */
@Service
public class MpxjMspdiExportArtifactService implements ProjectExportArtifactService {

    private static final String ARTIFACT_FORMAT = "mspdi_xml";
    private static final String MSPDI_NAMESPACE = "http://schemas.microsoft.com/project";
    /** MSPDI writes date-times as {@code yyyy-MM-ddTHH:mm:ss}, seconds always present. */
    private static final DateTimeFormatter MSPDI_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final String NO_SCHEDULE_NOTE =
            "Candidate schedule derived from the accepted source; no schedule calculations or "
                    + "Microsoft Project write-back were run by Shutdown Tracker.";

    @Override
    public ProjectExportArtifactSummary generate(
            ProjectExportArtifactRequest request,
            Path sourcePath,
            Path outputPath
    ) {
        Path normalizedOutputPath = validateOutputPath(outputPath);
        request.tasks().forEach(MpxjMspdiExportArtifactService::validateLeafExportTask);

        byte[] sourceBytes = readSource(sourcePath);
        verifySourceHash(sourceBytes, request.source().contentHash(), sourcePath);

        Document candidate = parseMspdi(sourceBytes);
        requireNoResourceAssignments(candidate, request);
        int sourceTaskCount = applyApprovedInputs(candidate, request);

        try {
            Path parent = normalizedOutputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(normalizedOutputPath, serialize(candidate));

            // Re-read what was actually written rather than trusting the in-memory document.
            verifyOnlyApprovedInputsChanged(parseMspdi(sourceBytes), parseMspdi(Files.readAllBytes(normalizedOutputPath)), request);

            return new ProjectExportArtifactSummary(
                    normalizedOutputPath.getFileName().toString(),
                    ARTIFACT_FORMAT,
                    request.tasks().size(),
                    sourceTaskCount,
                    exportedFieldCount(request),
                    Files.size(normalizedOutputPath),
                    sha256(normalizedOutputPath),
                    List.of(NO_SCHEDULE_NOTE)
            );
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Failed to generate MSPDI/XML candidate schedule: " + normalizedOutputPath.getFileName(),
                    ex
            );
        }
    }

    /**
     * Refuses to export progress onto a task that carries resource assignments.
     *
     * <p>The BOILER trial proved Microsoft Project derives progress on assigned tasks from
     * assignment-level actual work, not from task-level {@code PercentComplete}: a candidate that
     * writes only task fields is self-contradictory (100% complete yet zero actual work) and
     * Project rejects or recalculates it away. Until this exporter writes the complete evidenced
     * transaction — task, assignment, and timephased fields, per
     * {@code docs/product/project-progress-field-contract.md} — exporting an assigned task would
     * silently produce a candidate that lies. Refusing loudly is the only honest behaviour.
     */
    private void requireNoResourceAssignments(Document candidate, ProjectExportArtifactRequest request) {
        Set<String> approvedUids = new TreeSet<>();
        for (ProjectExportArtifactTask task : request.tasks()) {
            approvedUids.add(Integer.toString(
                    parsePositiveInteger(task.microsoftProjectTaskUid(), "microsoftProjectTaskUid")
            ));
        }

        Set<String> assignedApprovedUids = new TreeSet<>();
        NodeList assignments = candidate.getElementsByTagNameNS(MSPDI_NAMESPACE, "Assignment");
        for (int index = 0; index < assignments.getLength(); index++) {
            Element taskUid = firstChild((Element) assignments.item(index), "TaskUID");
            if (taskUid != null && approvedUids.contains(taskUid.getTextContent().trim())) {
                assignedApprovedUids.add(taskUid.getTextContent().trim());
            }
        }

        if (!assignedApprovedUids.isEmpty()) {
            throw new IllegalStateException(
                    "Approved Microsoft Project task UIDs carry resource assignments in the accepted "
                            + "source schedule: " + String.join(", ", assignedApprovedUids)
                            + ". Task-level progress on assigned tasks is rejected by Microsoft Project "
                            + "unless assignment actual work and timephased data are written with it; "
                            + "this exporter does not yet write that transaction "
                            + "(docs/product/project-progress-field-contract.md), so these tasks cannot "
                            + "be exported."
            );
        }
    }

    private byte[] readSource(Path sourcePath) {
        try {
            return Files.readAllBytes(sourcePath);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Accepted source schedule could not be read: " + sourcePath.getFileName(),
                    exception
            );
        }
    }

    /**
     * Refuses to build a candidate from a file that no longer matches the bytes accepted at import.
     * Without this the candidate could silently be derived from a different schedule than the one
     * the planner approved against.
     */
    private void verifySourceHash(byte[] sourceBytes, String expectedHash, Path sourcePath) {
        String actualHash = sha256(sourceBytes);
        if (!actualHash.equalsIgnoreCase(expectedHash)) {
            throw new IllegalStateException(
                    "Accepted source schedule no longer matches the hash recorded at import: "
                            + sourcePath.getFileName()
            );
        }
    }

    /**
     * Writes each approved value into the task that already carries the matching Microsoft Project
     * UID.
     *
     * @return the number of tasks in the source schedule
     */
    private int applyApprovedInputs(Document candidate, ProjectExportArtifactRequest request) {
        Map<String, ProjectExportArtifactTask> approvedByUid = new LinkedHashMap<>();
        for (ProjectExportArtifactTask task : request.tasks()) {
            String uid = Integer.toString(
                    parsePositiveInteger(task.microsoftProjectTaskUid(), "microsoftProjectTaskUid")
            );
            approvedByUid.put(uid, task);
        }

        List<Element> sourceTasks = taskElements(candidate);
        for (Element taskElement : sourceTasks) {
            Element uidElement = firstChild(taskElement, "UID");
            if (uidElement == null) {
                continue;
            }
            ProjectExportArtifactTask approved = approvedByUid.remove(uidElement.getTextContent().trim());
            if (approved == null) {
                continue;
            }
            requireMatchingIdentity(taskElement, approved);
            for (ProjectExportArtifactFieldValue fieldValue : approved.fieldValues()) {
                setTaskField(candidate, taskElement, fieldValue);
            }
        }

        if (!approvedByUid.isEmpty()) {
            // Creating the task instead would invent schedule structure Microsoft Project never
            // saw, which is exactly the authoring this product must not do.
            throw new IllegalStateException(
                    "Approved Microsoft Project task UIDs are absent from the accepted source schedule: "
                            + String.join(", ", approvedByUid.keySet())
            );
        }
        return sourceTasks.size();
    }

    /**
     * The approved candidate captured the task's identity at review time. If the source no longer
     * agrees, the reviewed fact no longer describes this task.
     */
    private void requireMatchingIdentity(Element taskElement, ProjectExportArtifactTask approved) {
        String expectedId = Integer.toString(
                parsePositiveInteger(approved.microsoftProjectTaskId(), "microsoftProjectTaskId")
        );
        requireExactText(taskElement, "ID", expectedId, approved);
        requireExactText(taskElement, "Name", approved.taskName(), approved);

        Element summary = firstChild(taskElement, "Summary");
        if (summary != null && "1".equals(summary.getTextContent().trim())) {
            throw new IllegalStateException(
                    "Approved task UID " + approved.microsoftProjectTaskUid()
                            + " is a summary task in the accepted source schedule; "
                            + "summary actuals are calculated by Microsoft Project."
            );
        }
    }

    private void requireExactText(
            Element taskElement,
            String localName,
            String expected,
            ProjectExportArtifactTask approved
    ) {
        Element element = firstChild(taskElement, localName);
        String actual = element == null ? null : element.getTextContent().trim();
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                    "Accepted source schedule task UID " + approved.microsoftProjectTaskUid()
                            + " no longer matches the reviewed " + localName + "."
            );
        }
    }

    /**
     * Sets a field's value, inserting the element at its schema position when the source did not
     * carry it.
     */
    private void setTaskField(Document document, Element taskElement, ProjectExportArtifactFieldValue fieldValue) {
        String elementName = xmlElementName(fieldValue.field());
        String value = canonicalValue(fieldValue);

        Element existing = firstChild(taskElement, elementName);
        if (existing != null) {
            existing.setTextContent(value);
            return;
        }

        Element created = document.createElementNS(MSPDI_NAMESPACE, elementName);
        created.setTextContent(value);
        taskElement.insertBefore(created, insertionPointFor(taskElement, elementName));
    }

    /**
     * The first existing child that must follow the new element, or {@code null} to append.
     *
     * <p>Only elements whose schema position this binding knows can answer that question. An
     * element the binding does not model — a source written by a newer Microsoft Project may carry
     * one — has no known position, so it is skipped rather than treated as belonging last. Treating
     * it as last stopped the search at the first such element and inserted the approved field
     * immediately before it, which places the field wherever that element happens to sit rather
     * than where the schema puts it.
     */
    private Node insertionPointFor(Element taskElement, String elementName) {
        int target = MspdiTaskElementOrder.positionOf(elementName);
        Node child = taskElement.getFirstChild();
        while (child != null) {
            if (child instanceof Element element) {
                OptionalInt position = MspdiTaskElementOrder.knownPositionOf(element.getLocalName());
                if (position.isPresent() && position.getAsInt() > target) {
                    return element;
                }
            }
            child = child.getNextSibling();
        }
        return null;
    }

    /**
     * Compares the written candidate against the accepted source and requires that only approved
     * {@code (task, field)} pairs differ.
     */
    private void verifyOnlyApprovedInputsChanged(
            Document source,
            Document candidate,
            ProjectExportArtifactRequest request
    ) {
        Map<String, Map<String, String>> approved = new HashMap<>();
        for (ProjectExportArtifactTask task : request.tasks()) {
            Map<String, String> fields = new HashMap<>();
            for (ProjectExportArtifactFieldValue fieldValue : task.fieldValues()) {
                fields.put(xmlElementName(fieldValue.field()), canonicalValue(fieldValue));
            }
            approved.put(
                    Integer.toString(parsePositiveInteger(task.microsoftProjectTaskUid(), "microsoftProjectTaskUid")),
                    fields
            );
        }

        List<String> differences =
                MspdiCandidateDifference.find(source.getDocumentElement(), candidate.getDocumentElement(), approved);
        if (!differences.isEmpty()) {
            throw new IllegalStateException(
                    "Generated candidate schedule differs from the accepted source outside the approved inputs: "
                            + String.join("; ", differences)
            );
        }

        // Every approved value must actually be present in the candidate.
        for (Element taskElement : taskElements(candidate)) {
            Element uidElement = firstChild(taskElement, "UID");
            if (uidElement == null) {
                continue;
            }
            Map<String, String> fields = approved.get(uidElement.getTextContent().trim());
            if (fields == null) {
                continue;
            }
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                Element applied = firstChild(taskElement, entry.getKey());
                if (applied == null || !entry.getValue().equals(applied.getTextContent().trim())) {
                    throw new IllegalStateException(
                            "Approved " + entry.getKey() + " did not reach the generated candidate schedule for task UID "
                                    + uidElement.getTextContent().trim() + "."
                    );
                }
            }
        }
    }

    private String canonicalValue(ProjectExportArtifactFieldValue fieldValue) {
        return switch (fieldValue.field()) {
            case PERCENT_COMPLETE -> parsePercentage(fieldValue.newValue(), fieldValue.field()).toPlainString();
            // MSPDI date-times always carry seconds. LocalDateTime.toString() omits them when they
            // are zero, and Microsoft Project reads the resulting value as absent.
            case ACTUAL_START, ACTUAL_FINISH ->
                    parseDateTime(fieldValue.newValue(), fieldValue.field()).format(MSPDI_DATE_TIME);
        };
    }

    private Document parseMspdi(byte[] xml) {
        try {
            Document document = hardenedDocumentBuilderFactory().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml));
            Element root = document.getDocumentElement();
            if (!MSPDI_NAMESPACE.equals(root.getNamespaceURI()) || !"Project".equals(root.getLocalName())) {
                throw new IllegalStateException("Accepted source schedule is not an MSPDI/XML Project document.");
            }
            return document;
        } catch (ParserConfigurationException | SAXException | IOException exception) {
            throw new IllegalStateException("Accepted source schedule could not be parsed as MSPDI/XML.", exception);
        }
    }

    private DocumentBuilderFactory hardenedDocumentBuilderFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    private byte[] serialize(Document document) {
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            var output = new java.io.ByteArrayOutputStream();
            transformer.transform(new DOMSource(document), new StreamResult(output));
            return output.toByteArray();
        } catch (TransformerException exception) {
            throw new IllegalStateException("Candidate schedule could not be written as MSPDI/XML.", exception);
        }
    }

    private List<Element> taskElements(Document document) {
        List<Element> tasks = new ArrayList<>();
        NodeList nodes = document.getElementsByTagNameNS(MSPDI_NAMESPACE, "Task");
        for (int index = 0; index < nodes.getLength(); index++) {
            tasks.add((Element) nodes.item(index));
        }
        return tasks;
    }

    private Element firstChild(Element parent, String localName) {
        Node child = parent.getFirstChild();
        while (child != null) {
            if (child instanceof Element element && localName.equals(element.getLocalName())) {
                return element;
            }
            child = child.getNextSibling();
        }
        return null;
    }

    private static void validateLeafExportTask(ProjectExportArtifactTask task) {
        if (!task.leafTask()) {
            throw new IllegalArgumentException("Only leaf-task export candidates may be included in MSPDI/XML artifacts.");
        }
    }

    private BigDecimal parsePercentage(String value, ProjectExportArtifactField field) {
        return new BigDecimal(ProjectExportValueNormalizer.normalize(field, value));
    }

    private LocalDateTime parseDateTime(String value, ProjectExportArtifactField field) {
        String normalized = ProjectExportValueNormalizer.normalize(field, value);
        try {
            return OffsetDateTime.parse(normalized).toLocalDateTime();
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    field.fieldName() + " was not a canonical offset date-time.",
                    exception
            );
        }
    }

    private String xmlElementName(ProjectExportArtifactField field) {
        return switch (field) {
            case PERCENT_COMPLETE -> "PercentComplete";
            case ACTUAL_START -> "ActualStart";
            case ACTUAL_FINISH -> "ActualFinish";
        };
    }

    private int parsePositiveInteger(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required for MSPDI/XML export identity.");
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(fieldName + " must be positive.");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be a positive integer.", ex);
        }
    }

    private Path validateOutputPath(Path outputPath) {
        Path normalizedPath = outputPath.toAbsolutePath().normalize();
        String fileName = normalizedPath.getFileName() == null
                ? ""
                : normalizedPath.getFileName().toString().toLowerCase();
        if (!fileName.endsWith(".xml")) {
            throw new IllegalArgumentException("MSPDI/XML export artifact path must end with .xml.");
        }
        return normalizedPath;
    }

    private int exportedFieldCount(ProjectExportArtifactRequest request) {
        return request.tasks().stream()
                .mapToInt(task -> task.fieldValues().size())
                .sum();
    }

    private String sha256(Path path) {
        try {
            return sha256(Files.readAllBytes(path));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not hash the generated candidate schedule.", exception);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required to record artifact provenance.", exception);
        }
    }
}
