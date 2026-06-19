package com.shutdowntracker.api.importbatch.handoff;

import com.shutdowntracker.api.importbatch.ImportBatchRecord;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileMetadataRecord;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryRequest;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ProjectParseHandoffService {

    private final ProjectParseJobClient parseJobClient;

    public ProjectParseHandoffService(ProjectParseJobClient parseJobClient) {
        this.parseJobClient = parseJobClient;
    }

    public ProjectParseSummaryResponse requestParseSummary(
            ImportBatchRecord importBatch,
            SourceFileMetadataRecord sourceFile
    ) {
        return parseJobClient.requestParseSummary(buildRequest(importBatch, sourceFile));
    }

    public ProjectParseSummaryRequest buildRequest(
            ImportBatchRecord importBatch,
            SourceFileMetadataRecord sourceFile
    ) {
        Objects.requireNonNull(importBatch, "importBatch is required.");
        Objects.requireNonNull(sourceFile, "sourceFile is required.");
        if (!importBatch.projectId().equals(sourceFile.projectId())) {
            throw new IllegalArgumentException("Import batch and source file must belong to the same project.");
        }
        if (!importBatch.sourceFileId().equals(sourceFile.id())) {
            throw new IllegalArgumentException("Import batch must reference the selected source file.");
        }

        return new ProjectParseSummaryRequest(
                importBatch.id(),
                importBatch.projectId(),
                importBatch.sourceFileId(),
                sourceFile.storageUri(),
                sourceFile.originalFilename()
        );
    }
}
