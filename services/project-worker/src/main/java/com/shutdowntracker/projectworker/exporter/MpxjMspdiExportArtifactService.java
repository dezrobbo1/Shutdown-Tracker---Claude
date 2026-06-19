package com.shutdowntracker.projectworker.exporter;

import com.shutdowntracker.projectexport.contract.ProjectExportArtifactField;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactFieldValue;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactRequest;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactSummary;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactTask;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import org.mpxj.ProjectFile;
import org.mpxj.Task;
import org.mpxj.mspdi.MSPDIWriter;
import org.mpxj.mspdi.SaveVersion;
import org.springframework.stereotype.Service;

@Service
public class MpxjMspdiExportArtifactService implements ProjectExportArtifactService {

    private static final String ARTIFACT_FORMAT = "mspdi_xml";
    private static final String NO_SCHEDULE_NOTE =
            "MSPDI/XML artifact only; no schedule calculations or Microsoft Project write-back were run.";

    @Override
    public ProjectExportArtifactSummary generate(ProjectExportArtifactRequest request, Path outputPath) {
        Path normalizedOutputPath = validateOutputPath(outputPath);
        ProjectFile project = buildProjectFile(request);

        try {
            Path parent = normalizedOutputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            MSPDIWriter writer = new MSPDIWriter();
            writer.setSaveVersion(SaveVersion.Project2016);
            try (OutputStream outputStream = Files.newOutputStream(normalizedOutputPath)) {
                writer.write(project, outputStream);
            }

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
            case PHYSICAL_PERCENT_COMPLETE -> task.setPhysicalPercentComplete(parsePercentage(fieldValue.newValue(), fieldValue.field()));
            case ACTUAL_START -> task.setActualStart(parseDateTime(fieldValue.newValue(), fieldValue.field()));
            case ACTUAL_FINISH -> task.setActualFinish(parseDateTime(fieldValue.newValue(), fieldValue.field()));
        }
    }

    private BigDecimal parsePercentage(String value, ProjectExportArtifactField field) {
        try {
            BigDecimal parsed = new BigDecimal(value);
            if (parsed.compareTo(BigDecimal.ZERO) < 0 || parsed.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException(field.fieldName() + " must be between 0 and 100.");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(field.fieldName() + " must be a decimal percentage.", ex);
        }
    }

    private LocalDateTime parseDateTime(String value, ProjectExportArtifactField field) {
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException(field.fieldName() + " must be an ISO-8601 date-time value.", ex);
            }
        }
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
