package com.shutdowntracker.projectworker.importer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mpxj.ProjectFile;
import org.mpxj.ProjectProperties;
import org.mpxj.reader.ProjectReader;
import org.mpxj.reader.UniversalProjectReader;
import org.springframework.stereotype.Service;

/**
 * Reads a Microsoft Project file once and returns both its summary and its entities.
 *
 * <p>Reading once matters at real sizes: the sample schedules this product targets run to
 * roughly 14 MB and several thousand tasks, so parsing the file a second time to collect
 * entities after summarising it would double the most expensive part of an import.
 *
 * <p>{@link UniversalProjectReader} accepts native {@code .mpp} as well as MSPDI/XML, so
 * planners are not required to export anything before importing.
 */
@Service
public class MpxjProjectParseService {

    private final MpxjProjectImportSummaryService summaryService;
    private final MpxjProjectEntityExtractionService extractionService;

    public MpxjProjectParseService(
            MpxjProjectImportSummaryService summaryService,
            MpxjProjectEntityExtractionService extractionService
    ) {
        this.summaryService = summaryService;
        this.extractionService = extractionService;
    }

    public ParsedProject parse(Path sourcePath) {
        Path normalizedPath = sourcePath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedPath)) {
            throw new IllegalArgumentException("Project file path must point to a local file: " + normalizedPath);
        }

        UniversalProjectReader universalReader = new UniversalProjectReader();
        try (UniversalProjectReader.ProjectReaderProxy proxy =
                     universalReader.getProjectReaderProxy(normalizedPath.toFile())) {
            ProjectReader reader = proxy.getProjectReader();
            ProjectFile project = proxy.read();

            ProjectImportSummary summary = summaryService.summarize(
                    project,
                    normalizedPath.getFileName().toString(),
                    reader.getClass().getSimpleName());

            ProjectProperties properties = project.getProjectProperties();
            return new ParsedProject(
                    summary,
                    externalProjectUid(properties),
                    toOffsetDateTime(properties.getStatusDate()),
                    extractionService.extractTasks(project),
                    extractionService.extractResources(project),
                    extractionService.extractAssignments(project),
                    extractionService.extractExtendedAttributes(project));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to read project file: " + normalizedPath.getFileName(), exception);
        }
    }

    private String externalProjectUid(ProjectProperties properties) {
        if (properties.getGUID() != null) {
            return properties.getGUID().toString();
        }
        return properties.getUniqueID() == null ? null : properties.getUniqueID().toString();
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
