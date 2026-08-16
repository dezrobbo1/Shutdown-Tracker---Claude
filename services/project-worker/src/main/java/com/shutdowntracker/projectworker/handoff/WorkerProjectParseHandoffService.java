package com.shutdowntracker.projectworker.handoff;

import com.shutdowntracker.projectimport.contract.ProjectParseEntitiesResponse;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryRequest;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import com.shutdowntracker.projectworker.importer.MpxjProjectEntityExtractionService;
import com.shutdowntracker.projectworker.importer.MpxjProjectImportSummaryService;
import com.shutdowntracker.projectworker.importer.MpxjProjectParseService;
import com.shutdowntracker.projectworker.importer.ParsedProject;
import com.shutdowntracker.projectworker.importer.ProjectImportSummary;
import com.shutdowntracker.projectworker.importer.ProjectImportSummaryService;
import com.shutdowntracker.projectworker.storage.WorkerStoragePathResolver;
import com.shutdowntracker.projectworker.storage.WorkerStorageProperties;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WorkerProjectParseHandoffService {

    private final ProjectImportSummaryService summaryService;
    private final MpxjProjectParseService parseService;
    private final WorkerStoragePathResolver storagePathResolver;

    @Autowired
    public WorkerProjectParseHandoffService(
            ProjectImportSummaryService summaryService,
            MpxjProjectParseService parseService,
            WorkerStoragePathResolver storagePathResolver
    ) {
        this.summaryService = summaryService;
        this.parseService = parseService;
        this.storagePathResolver = storagePathResolver;
    }

    /** Package-private compatibility constructor for isolated unit tests only. */
    WorkerProjectParseHandoffService(ProjectImportSummaryService summaryService) {
        this(
                summaryService,
                new MpxjProjectParseService(
                        new MpxjProjectImportSummaryService(), new MpxjProjectEntityExtractionService()),
                new WorkerStoragePathResolver(new WorkerStorageProperties(
                        Path.of("").toAbsolutePath().normalize(),
                        Path.of(System.getProperty("java.io.tmpdir"))
                ))
        );
    }

    public ProjectParseSummaryResponse summarize(ProjectParseSummaryRequest request) {
        Objects.requireNonNull(request, "request is required.");
        ProjectImportSummary summary =
                summaryService.summarize(storagePathResolver.resolveSourceFile(request.storageUri()));
        return toSummaryResponse(request.importBatchId(), summary);
    }

    /**
     * Parses the file and returns its entities as well as its counts.
     *
     * <p>The summary endpoint above reports only how many tasks a file contains, which
     * left the parsed schedule nowhere to go. This carries the tasks, resources,
     * assignments, and aliased custom fields the API needs to persist a usable snapshot.
     */
    public ProjectParseEntitiesResponse parseEntities(ProjectParseSummaryRequest request) {
        Objects.requireNonNull(request, "request is required.");
        ParsedProject parsed = parseService.parse(storagePathResolver.resolveSourceFile(request.storageUri()));
        return new ProjectParseEntitiesResponse(
                toSummaryResponse(request.importBatchId(), parsed.summary()),
                parsed.externalProjectUid(),
                parsed.projectStatusDate(),
                parsed.tasks(),
                parsed.resources(),
                parsed.assignments(),
                parsed.extendedAttributes()
        );
    }

    private ProjectParseSummaryResponse toSummaryResponse(UUID importBatchId, ProjectImportSummary summary) {
        return new ProjectParseSummaryResponse(
                importBatchId,
                "mpxj",
                mpxjVersion(),
                summary.sourceFilename(),
                summary.detectedFormat(),
                summary.projectName(),
                summary.taskCount(),
                summary.summaryTaskCount(),
                summary.leafTaskCount(),
                summary.resourceCount(),
                summary.assignmentCount(),
                summary.calendarCount(),
                summary.customFieldCount(),
                warningCount(summary),
                0,
                summary.notes()
        );
    }

    private int warningCount(ProjectImportSummary summary) {
        return (int) summary.notes().stream()
                .filter(note -> note.startsWith("Ignored read issue:"))
                .count();
    }

    private String mpxjVersion() {
        Package mpxjPackage = org.mpxj.ProjectFile.class.getPackage();
        String implementationVersion = mpxjPackage.getImplementationVersion();
        return implementationVersion == null || implementationVersion.isBlank() ? "unknown" : implementationVersion;
    }
}
