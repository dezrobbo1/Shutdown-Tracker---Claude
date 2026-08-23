package com.shutdowntracker.api.execution;

import java.util.UUID;

/**
 * The three writes an export batch makes to the field updates it carries.
 *
 * <p>Named separately from {@link TaskProgressRepository} rather than handing the export side the
 * whole repository. What an export batch legitimately does to a progress row is exactly this: claim
 * it, release it, or record that it travelled. It has no business submitting one, superseding one,
 * or reading a review queue, and an interface that offered those would eventually be used for them.
 *
 * <p>Execution owns the rows and therefore owns this contract; the dependency points from the
 * export side inward, and nothing in this package knows that export previews exist.
 */
public interface ExportBatchProgressBinding {

    /**
     * Binds every eligible update a batch's lines were built from to that batch.
     *
     * <p>This is what makes {@code export_batch_id} mean something, and with it the
     * {@code export_batch_id IS NULL} clause in the export queue: an update a batch has claimed
     * leaves the queue, so the same approved field change cannot be previewed twice.
     *
     * <p>Matched through the batch's own lines rather than a list from the caller, because the
     * lines are what the batch actually carries. A caller-supplied list could disagree with them,
     * and the disagreement would be invisible.
     *
     * @return how many updates the batch claimed
     */
    int claimForExportBatch(UUID projectId, UUID exportBatchId);

    /**
     * Returns a rejected batch's updates to the export queue.
     *
     * <p>The field work was reviewed and approved and was never carried anywhere, so it must be
     * available to a fresh batch. Leaving it claimed would discard it permanently — the queue
     * offers only updates no batch has claimed — with no way back short of a correction.
     *
     * @return how many updates were released
     */
    int releaseFromExportBatch(UUID exportBatchId);

    /**
     * Records that a verified batch's updates travelled.
     *
     * <p>Terminal for {@code export_state}. How far the batch got is the batch's own status,
     * reached through {@code export_batch_id}, and is deliberately not mirrored onto the row.
     *
     * @return how many updates were marked exported
     */
    int markExported(UUID exportBatchId);
}
