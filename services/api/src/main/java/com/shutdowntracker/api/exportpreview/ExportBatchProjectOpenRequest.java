package com.shutdowntracker.api.exportpreview;

import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.immutableObjectMap;

import java.util.Map;
import java.util.UUID;

/**
 * Who opened the artifact in Microsoft Project, and why.
 *
 * <p>{@code openedByUserId} is nullable here because a caller must not be able to assert it. The
 * controller overwrites whatever arrives with the resolved actor, so the value the service and
 * repository see is always the authenticated user rather than one the request named.
 */
public record ExportBatchProjectOpenRequest(
        UUID openedByUserId,
        String reason,
        Map<String, Object> metadata
) {
    public ExportBatchProjectOpenRequest {
        metadata = immutableObjectMap(metadata, "metadata");
    }
}
