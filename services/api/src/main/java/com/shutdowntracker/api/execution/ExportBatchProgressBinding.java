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
     * Unbinds a rejected batch's updates, returning to the queue the ones that may still travel.
     *
     * <p>The field work was reviewed and approved and was never carried anywhere, so it must be
     * available to a fresh batch. Leaving it claimed would discard it permanently — the queue
     * offers only updates no batch has claimed — with no way back short of a correction.
     *
     * <p>An update superseded while the batch held it is unlinked without being made eligible: its
     * value has been replaced and must not travel, but a rejected batch carried nothing, so it must
     * not be left named as the batch that carried this one.
     *
     * @return how many updates the batch released its claim on
     */
    int releaseFromExportBatch(UUID exportBatchId);

    /**
     * Records that a generated batch's updates travelled.
     *
     * <p>Called when the artifact exists, not when a planner later verifies it. Verification is the
     * batch's fact: a generated batch nobody opens still carried these values, and how far the batch
     * itself got is its own status, reached through {@code export_batch_id} and deliberately not
     * mirrored onto the row. Terminal for {@code export_state}.
     *
     * @return how many updates were marked exported
     */
    int markExported(UUID exportBatchId);
}
