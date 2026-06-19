package com.shutdowntracker.api.exportpreview;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

record ExportPreviewMaterializedLine(
        UUID importedTaskId,
        String sourceEntityType,
        UUID sourceEntityId,
        ApprovalState approvalState,
        String fieldName,
        String oldValue,
        String newValue,
        UUID sourceActorUserId,
        OffsetDateTime sourceTimestamp,
        String reason,
        boolean leafTask,
        boolean exportEligible,
        Map<String, Object> metadata
) {
}
