package com.shutdowntracker.projectworker.exporter;

import com.shutdowntracker.projectexport.contract.ProjectExportArtifactField;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactFieldValue;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactRequest;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactSummary;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactTask;
import com.shutdowntracker.projectexport.contract.ProjectExportValueNormalizer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.mpxj.ProjectFile;
import org.mpxj.Task;
import org.mpxj.mspdi.MSPDIWriter;
import org.mpxj.mspdi.SaveVersion;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

@Service
public class MpxjMspdiExportArtifactService implements ProjectExportArtifactService {

    private static final String ARTIFACT_FORMAT = "mspdi_xml";
    private static final String MSPDI_NAMESPACE = "http://schemas.microsoft.com/project";
    private static final String NO_SCHEDULE_NOTE =
            "MSPDI/XML artifact only; no schedule calculations or Microsoft Project write-back were run.";
    private static final Set<String> ROOT_ELEMENT_ALLOWLIST = Set.of("SaveVersion", "Name", "Tasks");
    private static final Set<String> TASK_IDENTITY_ELEMENT_ALLOWLIST = Set.of("UID", "ID", "Name");

    @Override
    public ProjectExportArtifactSummary generate(ProjectExportArtifactRequest request, Path outputPath) {
        Path normalizedOutputPath = validateOutputPath(outputPath);
        ProjectFile project = buildProjectFile(request);

        try {
            Path parent = normalizedOutputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.write(normalizedOutputPath, authorizedMspdiBytes(project, request));

            return new ProjectExportArtifactSummary(
                    normalizedOutputPath.getFileName().toString(),
                    ARTIFACT_FORMAT,
                    request.tasks().size(),
                    exportedFieldCount(request),
                    Files.size(normalizedOutputPath),
                    sha256(normalizedOutputPath),
                    List.of(NO_SCHEDULE_NOTE)
            );
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Failed to generate MSPDI/XML export artifact: " + normalizedOutputPath.getFileName(),
                    ex
            );
        }
    }

    ProjectFile buildProjectFile(ProjectExportArtifactRequest request) {
        ProjectFile project = new ProjectFile();
        project.getProjectProperties().setName(request.projectName());
        project.addDefaultBaseCalendar();

        for (ProjectExportArtifactTask sourceTask : request.tasks()) {
            validateLeafExportTask(sourceTask);

            Task task = project.addTask();
            task.setName(sourceTask.taskName());
            task.setUniqueID(parsePositiveInteger(sourceTask.microsoftProjectTaskUid(), "microsoftProjectTaskUid"));
            task.setID(parsePositiveInteger(sourceTask.microsoftProjectTaskId(), "microsoftProjectTaskId"));

            for (ProjectExportArtifactFieldValue fieldValue : sourceTask.fieldValues()) {
                applyFieldValue(task, fieldValue);
            }
        }

        return project;
    }

    private void validateLeafExportTask(ProjectExportArtifactTask task) {
        if (!task.leafTask()) {
            throw new IllegalArgumentException("Only leaf-task export candidates may be included in MSPDI/XML artifacts.");
        }
    }

    private void applyFieldValue(Task task, ProjectExportArtifactFieldValue fieldValue) {
        switch (fieldValue.field()) {
            case PERCENT_COMPLETE -> task.setPercentageComplete(parsePercentage(fieldValue.newValue(), fieldValue.field()));
            case ACTUAL_START -> task.setActualStart(parseDateTime(fieldValue.newValue(), fieldValue.field()));
            case ACTUAL_FINISH -> task.setActualFinish(parseDateTime(fieldValue.newValue(), fieldValue.field()));
        }
    }

    private BigDecimal parsePercentage(String value, ProjectExportArtifactField field) {
        return new BigDecimal(ProjectExportValueNormalizer.normalize(field, value));
    }

    private byte[] authorizedMspdiBytes(ProjectFile project, ProjectExportArtifactRequest request) throws IOException {
        try {
            ByteArrayOutputStream rawOutput = new ByteArrayOutputStream();
            MSPDIWriter writer = new MSPDIWriter();
            writer.setSaveVersion(SaveVersion.Project2016);
            writer.write(project, rawOutput);

            Document document = parseGeneratedXml(rawOutput.toByteArray());
            enforceArtifactAuthority(document, request);

            ByteArrayOutputStream authorizedOutput = new ByteArrayOutputStream();
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(document), new StreamResult(authorizedOutput));
            return authorizedOutput.toByteArray();
        } catch (ParserConfigurationException | SAXException | TransformerException exception) {
            throw new IOException("Failed to enforce the MSPDI/XML export authority allowlist.", exception);
        }
    }

    private Document parseGeneratedXml(byte[] xml) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private void enforceArtifactAuthority(Document document, ProjectExportArtifactRequest request) {
        Element projectElement = document.getDocumentElement();
        requireElement(projectElement, "Project");

        requireSingleDirectChild(projectElement, "SaveVersion");
        Element projectName = requireSingleDirectChild(projectElement, "Name");
        if (!request.projectName().equals(projectName.getTextContent())) {
            throw new IllegalStateException("Generated MSPDI/XML project identity did not match the approved request.");
        }

        Element tasksElement = requireSingleDirectChild(projectElement, "Tasks");
        pruneDirectChildren(tasksElement, Set.of("Task"));
        Map<String, ProjectExportArtifactTask> expectedTasks = new HashMap<>();
        for (ProjectExportArtifactTask task : request.tasks()) {
            String taskUid = Integer.toString(parsePositiveInteger(
                    task.microsoftProjectTaskUid(),
                    "microsoftProjectTaskUid"
            ));
            if (expectedTasks.put(taskUid, task) != null) {
                throw new IllegalArgumentException("Microsoft Project task UIDs must be unique within an export artifact.");
            }
        }

        List<Element> generatedTasks = directChildren(tasksElement, "Task");
        if (generatedTasks.size() != expectedTasks.size()) {
            throw new IllegalStateException("Generated MSPDI/XML task membership did not match the approved request.");
        }

        for (Element taskElement : generatedTasks) {
            String taskUid = requireSingleDirectChild(taskElement, "UID").getTextContent();
            ProjectExportArtifactTask expectedTask = expectedTasks.remove(taskUid);
            if (expectedTask == null) {
                throw new IllegalStateException("Generated MSPDI/XML contained an unapproved task identity.");
            }
            enforceTaskAuthority(taskElement, expectedTask);
        }

        if (!expectedTasks.isEmpty()) {
            throw new IllegalStateException("Generated MSPDI/XML omitted an approved task identity.");
        }

        pruneDirectChildren(projectElement, ROOT_ELEMENT_ALLOWLIST);
    }

    private void enforceTaskAuthority(Element taskElement, ProjectExportArtifactTask expectedTask) {
        requireExactText(
                taskElement,
                "UID",
                Integer.toString(parsePositiveInteger(expectedTask.microsoftProjectTaskUid(), "microsoftProjectTaskUid"))
        );
        requireExactText(
                taskElement,
                "ID",
                Integer.toString(parsePositiveInteger(expectedTask.microsoftProjectTaskId(), "microsoftProjectTaskId"))
        );
        requireExactText(taskElement, "Name", expectedTask.taskName());

        for (ProjectExportArtifactFieldValue fieldValue : expectedTask.fieldValues()) {
            Element generatedValue = requireSingleDirectChild(taskElement, xmlElementName(fieldValue.field()));
            validateGeneratedFieldValue(generatedValue.getTextContent(), fieldValue);
        }

        Set<String> allowedElements = new HashSet<>(TASK_IDENTITY_ELEMENT_ALLOWLIST);
        for (ProjectExportArtifactFieldValue fieldValue : expectedTask.fieldValues()) {
            allowedElements.add(xmlElementName(fieldValue.field()));
        }
        pruneDirectChildren(taskElement, allowedElements);
    }

    private void validateGeneratedFieldValue(String generatedValue, ProjectExportArtifactFieldValue expectedValue) {
        switch (expectedValue.field()) {
            case PERCENT_COMPLETE -> {
                String expected = parsePercentage(expectedValue.newValue(), expectedValue.field()).toPlainString();
                if (!expected.equals(generatedValue)) {
                    throw new IllegalStateException("Generated percent_complete differed from the approved value.");
                }
            }
            case ACTUAL_START, ACTUAL_FINISH -> {
                LocalDateTime expected = parseDateTime(expectedValue.newValue(), expectedValue.field());
                LocalDateTime generated;
                try {
                    generated = LocalDateTime.parse(generatedValue);
                } catch (DateTimeParseException exception) {
                    throw new IllegalStateException(
                            "Generated " + expectedValue.field().fieldName() + " was not an ISO-8601 date-time.",
                            exception
                    );
                }
                if (!expected.equals(generated)) {
                    throw new IllegalStateException(
                            "Generated " + expectedValue.field().fieldName() + " differed from the approved value."
                    );
                }
            }
        }
    }

    private String xmlElementName(ProjectExportArtifactField field) {
        return switch (field) {
            case PERCENT_COMPLETE -> "PercentComplete";
            case ACTUAL_START -> "ActualStart";
            case ACTUAL_FINISH -> "ActualFinish";
        };
    }

    private void pruneDirectChildren(Element parent, Set<String> allowedLocalNames) {
        Node child = parent.getFirstChild();
        while (child != null) {
            Node next = child.getNextSibling();
            if (child instanceof Element element
                    && (!MSPDI_NAMESPACE.equals(element.getNamespaceURI())
                    || !allowedLocalNames.contains(element.getLocalName()))) {
                parent.removeChild(child);
            }
            child = next;
        }
    }

    private Element requireSingleDirectChild(Element parent, String localName) {
        List<Element> children = directChildren(parent, localName);
        if (children.size() != 1) {
            throw new IllegalStateException(
                    "Generated MSPDI/XML requires exactly one " + localName + " element in this context."
            );
        }
        return children.getFirst();
    }

    private List<Element> directChildren(Element parent, String localName) {
        List<Element> children = new ArrayList<>();
        Node child = parent.getFirstChild();
        while (child != null) {
            if (child instanceof Element element
                    && MSPDI_NAMESPACE.equals(element.getNamespaceURI())
                    && localName.equals(element.getLocalName())) {
                children.add(element);
            }
            child = child.getNextSibling();
        }
        return children;
    }

    private void requireElement(Element element, String localName) {
        if (!MSPDI_NAMESPACE.equals(element.getNamespaceURI()) || !localName.equals(element.getLocalName())) {
            throw new IllegalStateException("Generated artifact was not an MSPDI/XML " + localName + " document.");
        }
    }

    private void requireExactText(Element parent, String localName, String expectedValue) {
        String actualValue = requireSingleDirectChild(parent, localName).getTextContent();
        if (!expectedValue.equals(actualValue)) {
            throw new IllegalStateException("Generated MSPDI/XML " + localName + " identity did not match the request.");
        }
    }

    private LocalDateTime parseDateTime(String value, ProjectExportArtifactField field) {
        String normalized = ProjectExportValueNormalizer.normalize(field, value);
        return OffsetDateTime.parse(normalized).toLocalDateTime();
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
        String fileName = normalizedPath.getFileName() == null ? "" : normalizedPath.getFileName().toString().toLowerCase();
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

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(path);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception ex) {
            throw new IOException("Failed to hash generated MSPDI/XML export artifact.", ex);
        }
    }
}
