package com.shutdowntracker.projectworker.handoff;

import com.shutdowntracker.projectimport.contract.ProjectParseSummaryRequest;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import com.shutdowntracker.projectworker.importer.ProjectImportSummary;
import com.shutdowntracker.projectworker.importer.ProjectImportSummaryService;
import com.shutdowntracker.projectworker.storage.WorkerStoragePathResolver;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class WorkerProjectParseHandoffService {

    private final ProjectImportSummaryService summaryService;
    private final WorkerStoragePathResolver storagePathResolver;

    public WorkerProjectParseHandoffService(
            ProjectImportSummaryService summaryService,
            WorkerStoragePathResolver storagePathResolver
    ) {
        this.summaryService = summaryService;
        this.storagePathResolver = storagePathResolver;
    }

    public ProjectParseSummaryResponse summarize(ProjectParseSummaryRequest request) {
        Objects.requireNonNull(request, "request is required.");
        ProjectImportSummary summary =
                summaryService.summarize(storagePathResolver.resolveSourceFile(request.storageUri()));
        return new ProjectParseSummaryResponse(
                request.importBatchId(),
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
        if (implementationVersion == null || implementationVersion.isBlank()) {
            return "unknown";
        }
        return implementationVersion;
    }
}
