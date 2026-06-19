package com.shutdowntracker.api.exportpreview;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ExportPreviewTaskContext(
        UUID id,
        UUID projectId,
        UUID projectSnapshotId,
        String externalUid,
        String name,
        boolean summary,
        BigDecimal percentComplete,
        BigDecimal physicalPercentComplete,
        OffsetDateTime actualStart,
        OffsetDateTime actualFinish
) {
    public boolean leafTask() {
        return !summary;
    }
}
