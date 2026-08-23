package com.shutdowntracker.api.support;

import com.shutdowntracker.api.execution.ExportBatchProgressBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stands in for the execution side of an export batch in tests that are about the export side.
 *
 * <p>{@link #expectedCount} defaults to zero — no exportable line sourced from a progress update —
 * which is what a batch built from synthetic candidates has, and the service then skips the claim
 * entirely. {@link #claimForExportBatch} answers with {@link #claimCount}, which defaults to "as
 * many as were expected", because the service refuses a shortfall and a double that under-answered
 * would fail every preview for the wrong reason. Tests about the shortfall set both deliberately.
 *
 * <p>What the real binding does to the database is covered by {@code TaskProgressExportBindingTests}
 * against a real PostgreSQL, and end to end by {@code ProductJourneyTests}.
 */
public final class RecordingExportBatchProgressBinding implements ExportBatchProgressBinding {

    /** How many progress updates the batch's exportable lines are said to come from. */
    public int expectedCount;

    /** Set to a fixed number to simulate a claim that could not take every update. */
    public Integer claimCount;

    public final List<UUID> counted = new ArrayList<>();
    public final List<UUID> claimed = new ArrayList<>();
    public final List<UUID> released = new ArrayList<>();
    public final List<UUID> exported = new ArrayList<>();

    @Override
    public int countClaimableUpdates(UUID exportBatchId) {
        counted.add(exportBatchId);
        return expectedCount;
    }

    @Override
    public int claimForExportBatch(UUID projectId, UUID exportBatchId) {
        claimed.add(exportBatchId);
        return claimCount == null ? expectedCount : claimCount;
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
