package com.shutdowntracker.api.exportpreview;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ExportPreviewBatchRecord(
        UUID id,
        UUID projectId,
        UUID projectSnapshotId,
        ExportBatchState status,
        OffsetDateTime previewCreatedAt,
        OffsetDateTime approvedAt,
        UUID approvedByUserId,
        OffsetDateTime generatedAt,
        UUID generatedByUserId,
        OffsetDateTime openedInMicrosoftProjectAt,
        UUID openedInMicrosoftProjectByUserId,
        OffsetDateTime verifiedAt,
        UUID verifiedByUserId,
        String exportFileUri,
        String exportFileHash,
        String failureReason,
        int lineCount,
        int eligibleLineCount,
        int ineligibleLineCount,
        Integer integrityPolicyVersion,
        Boolean lineSetSealed,
        Map<String, Object> metadata
) {
    public ExportPreviewBatchRecord {
        metadata = ExportPreviewRecordValidation.immutableObjectMap(metadata, "metadata");
    }

    public ExportPreviewBatchRecord(
            UUID id,
            UUID projectId,
            UUID projectSnapshotId,
            ExportBatchState status,
            OffsetDateTime previewCreatedAt,
            OffsetDateTime approvedAt,
            UUID approvedByUserId,
            OffsetDateTime generatedAt,
            UUID generatedByUserId,
            OffsetDateTime verifiedAt,
            UUID verifiedByUserId,
            String exportFileUri,
            String exportFileHash,
            String failureReason,
            int lineCount,
            int eligibleLineCount,
            int ineligibleLineCount,
            Integer integrityPolicyVersion,
            Boolean lineSetSealed
    ) {
        this(
                id,
                projectId,
                projectSnapshotId,
                status,
                previewCreatedAt,
                approvedAt,
                approvedByUserId,
                generatedAt,
                generatedByUserId,
                null,
                null,
                verifiedAt,
                verifiedByUserId,
                exportFileUri,
                exportFileHash,
                failureReason,
                lineCount,
                eligibleLineCount,
                ineligibleLineCount,
                integrityPolicyVersion,
                lineSetSealed,
                Map.of()
        );
    }
}
