package com.shutdowntracker.projectworker.handoff;

import com.shutdowntracker.projectimport.contract.ProjectParseSummaryRequest;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import com.shutdowntracker.projectworker.importer.ProjectImportSummary;
import com.shutdowntracker.projectworker.importer.ProjectImportSummaryService;
import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class WorkerProjectParseHandoffService {

    private static final String LOCAL_FILE_SCHEME = "file";

    private final ProjectImportSummaryService summaryService;

    public WorkerProjectParseHandoffService(ProjectImportSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    public ProjectParseSummaryResponse summarize(ProjectParseSummaryRequest request) {
        Objects.requireNonNull(request, "request is required.");
        ProjectImportSummary summary = summaryService.summarize(resolveLocalPath(request.storageUri()));
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

    private Path resolveLocalPath(String storageUri) {
        URI uri = URI.create(storageUri);
        if (uri.getScheme() == null) {
            return Path.of(storageUri);
        }
        if (LOCAL_FILE_SCHEME.equalsIgnoreCase(uri.getScheme())) {
            return Path.of(uri);
        }
        throw new IllegalArgumentException("Project worker parse handoff only supports local file storage URIs for now.");
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
