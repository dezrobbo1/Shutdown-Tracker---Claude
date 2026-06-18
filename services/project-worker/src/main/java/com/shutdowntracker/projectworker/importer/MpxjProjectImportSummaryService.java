package com.shutdowntracker.projectworker.importer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.mpxj.ProjectFile;
import org.mpxj.ProjectProperties;
import org.mpxj.Task;
import org.mpxj.reader.ProjectReader;
import org.mpxj.reader.UniversalProjectReader;
import org.springframework.stereotype.Service;

@Service
public class MpxjProjectImportSummaryService implements ProjectImportSummaryService {

    @Override
    public ProjectImportSummary summarize(Path sourcePath) {
        Path normalizedPath = sourcePath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedPath)) {
            throw new IllegalArgumentException("Project import spike path must point to a local file: " + normalizedPath);
        }

        UniversalProjectReader universalReader = new UniversalProjectReader();
        try (UniversalProjectReader.ProjectReaderProxy proxy =
                     universalReader.getProjectReaderProxy(normalizedPath.toFile())) {
            ProjectReader reader = proxy.getProjectReader();
            ProjectFile project = proxy.read();
            return summarize(project, normalizedPath.getFileName().toString(), reader.getClass().getSimpleName());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read project import spike file: " + normalizedPath.getFileName(), ex);
        }
    }

    ProjectImportSummary summarize(ProjectFile project, String sourceFilename, String readerName) {
        List<Task> tasks = project.getTasks().stream()
                .filter(Objects::nonNull)
                .toList();
        int summaryTaskCount = (int) tasks.stream()
                .filter(task -> !task.getChildTasks().isEmpty())
                .count();
        int taskCount = tasks.size();
        int leafTaskCount = taskCount - summaryTaskCount;

        ProjectProperties properties = project.getProjectProperties();
        List<String> notes = new ArrayList<>();
        notes.add("Summary only; no schedule calculations were run.");
        project.getIgnoredErrors().stream()
                .map(this::formatIgnoredError)
                .forEach(notes::add);

        return new ProjectImportSummary(
                sourceFilename,
                firstPresent(properties.getFileType(), readerName, "unknown"),
                firstPresent(properties.getName(), "unknown"),
                taskCount,
                summaryTaskCount,
                leafTaskCount,
                project.getResources().size(),
                project.getResourceAssignments().size(),
                project.getCalendars().size(),
                project.getCustomFields().size(),
                notes
        );
    }

    private String formatIgnoredError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return "Ignored read issue: " + error.getClass().getSimpleName();
        }
        return "Ignored read issue: " + error.getClass().getSimpleName() + ": " + message;
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "unknown";
    }
}
