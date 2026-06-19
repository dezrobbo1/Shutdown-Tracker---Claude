package com.shutdowntracker.api.exportpreview;

import java.util.List;

public record ExportPreviewDetail(
        ExportPreviewBatchRecord batch,
        List<ExportPreviewLineRecord> lines,
        String message
) {
    public ExportPreviewDetail {
        lines = List.copyOf(lines);
    }
}
