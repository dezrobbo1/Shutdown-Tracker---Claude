package com.shutdowntracker.api.exportpreview;

import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.immutableObjectMap;

import java.util.Map;
import java.util.UUID;

/**
 * Who verified the batch against Microsoft Project, and why.
 *
 * <p>{@code verifiedByUserId} is nullable here for the same reason as
 * {@link ExportBatchProjectOpenRequest#openedByUserId()}: verification is audit history, so the
 * controller supplies the resolved actor rather than trusting the request body.
 */
public record ExportBatchVerificationRequest(
        UUID verifiedByUserId,
        String reason,
        Map<String, Object> metadata
) {
    public ExportBatchVerificationRequest {
        metadata = immutableObjectMap(metadata, "metadata");
    }
}
