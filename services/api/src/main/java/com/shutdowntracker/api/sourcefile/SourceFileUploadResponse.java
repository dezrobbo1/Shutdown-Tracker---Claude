package com.shutdowntracker.api.sourcefile;

import com.shutdowntracker.api.importbatch.ImportBatchRecord;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileMetadataRecord;

public record SourceFileUploadResponse(
        String originalFilename,
        long sizeBytes,
        String detectedExtension,
        boolean accepted,
        String rejectionReason,
        SourceFileMetadataRecord sourceFile,
        ImportBatchRecord importBatch,
        String message
) {

    static SourceFileUploadResponse rejected(SourceFileValidationResponse validation) {
        return new SourceFileUploadResponse(
                validation.originalFilename(),
                validation.sizeBytes(),
                validation.detectedExtension(),
                false,
                validation.rejectionReason(),
                null,
                null,
                "Upload rejected before storage. No file was stored, parsed, persisted, forwarded, or imported."
        );
    }

    static SourceFileUploadResponse accepted(
            SourceFileValidationResponse validation,
            SourceFileMetadataRecord sourceFile,
            ImportBatchRecord importBatch
    ) {
        return new SourceFileUploadResponse(
                validation.originalFilename(),
                validation.sizeBytes(),
                validation.detectedExtension(),
                true,
                null,
                sourceFile,
                importBatch,
                "Source file stored and pending import batch created. No file was parsed, forwarded to the worker, imported, or written back to Microsoft Project."
        );
    }
}
