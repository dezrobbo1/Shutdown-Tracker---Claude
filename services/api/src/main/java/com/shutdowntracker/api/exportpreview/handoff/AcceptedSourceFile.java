package com.shutdowntracker.api.exportpreview.handoff;

import java.util.UUID;

/**
 * The uploaded schedule an accepted snapshot was imported from.
 *
 * <p>A candidate schedule is the accepted source with approved inputs applied, so generation needs
 * the original file rather than the imported rows. The imported snapshot is a read-and-report
 * projection and holds no dependencies, calendars, constraints or baselines to rebuild from.
 */
public record AcceptedSourceFile(
        UUID sourceFileId,
        String storageUri,
        String contentHash,
        String fileKind
) {
}
