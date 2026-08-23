package com.shutdowntracker.api.support;

import com.shutdowntracker.api.execution.ExportBatchProgressBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stands in for the execution side of an export batch in tests that are about the export side.
 *
 * <p>{@link #claimForExportBatch} answers with {@link #claimCount}, which defaults to "as many as
 * you asked for" — the service compares the count against the candidates it built the batch from
 * and refuses a shortfall, so a double returning zero would fail every preview for the wrong
 * reason. Tests that are about the shortfall set it deliberately.
 *
 * <p>What the real binding does to the database is covered by {@code TaskProgressExportBindingTests}
 * against a real PostgreSQL, and end to end by {@code ProductJourneyTests}.
 */
public final class RecordingExportBatchProgressBinding implements ExportBatchProgressBinding {

    /** Set to a fixed number to simulate a claim that could not take every update. */
    public Integer claimCount;

    public final List<UUID> claimed = new ArrayList<>();
    public final List<UUID> released = new ArrayList<>();
    public final List<UUID> exported = new ArrayList<>();

    @Override
    public int claimForExportBatch(UUID projectId, UUID exportBatchId) {
        claimed.add(exportBatchId);
        return claimCount == null ? Integer.MAX_VALUE : claimCount;
    }

    @Override
    public int releaseFromExportBatch(UUID exportBatchId) {
        released.add(exportBatchId);
        return 0;
    }

    @Override
    public int markExported(UUID exportBatchId) {
        exported.add(exportBatchId);
        return 0;
    }
}
