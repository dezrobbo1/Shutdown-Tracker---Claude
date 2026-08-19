package com.shutdowntracker.api.assignment;

import com.shutdowntracker.api.importreview.ImportReviewTaskRow;
import java.util.List;
import java.util.UUID;

/**
 * The work Microsoft Project says belongs to the acting user, in the newest accepted snapshot.
 *
 * <p>The three empty cases are different facts and are reported as different facts, because a field
 * app that renders all of them as "no work" tells somebody standing on site that they are finished
 * when they are not:
 *
 * <ul>
 *   <li>{@code projectSnapshotId} is null — no schedule has been accepted, so nothing is assigned to
 *       anybody yet;
 *   <li>{@code linked} is false — no Project resource has been linked to this user, so the question
 *       "what is mine" has no answer rather than the answer "nothing";
 *   <li>{@code linked} is true and {@code tasks} is empty — the question was asked and answered.
 * </ul>
 *
 * <p>{@code unmatchedResourceUids} is the fourth case, and the reason the first three are not
 * enough. A link points at a resource UID in the project; a re-import can arrive without that
 * resource. The link is deliberately kept, so this reports the work list as narrowed by a link that
 * currently resolves to nothing rather than letting it look like an empty day.
 */
public record AssignedWorkView(
        UUID projectId,
        UUID projectSnapshotId,
        Integer snapshotVersion,
        boolean linked,
        List<String> linkedResourceUids,
        List<String> unmatchedResourceUids,
        List<ImportReviewTaskRow> tasks
) {

    public static AssignedWorkView noAcceptedSnapshot(UUID projectId, boolean linked, List<String> linkedResourceUids) {
        return new AssignedWorkView(projectId, null, null, linked, List.copyOf(linkedResourceUids), List.of(), List.of());
    }
}
