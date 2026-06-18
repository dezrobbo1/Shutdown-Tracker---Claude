package com.shutdowntracker.api.sourcefile;

public record SourceFileValidationResponse(
        String originalFilename,
        long sizeBytes,
        String detectedExtension,
        boolean accepted,
        String rejectionReason,
        String message
) {
}
