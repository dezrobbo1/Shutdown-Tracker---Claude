package com.shutdowntracker.api.assignment;

import com.shutdowntracker.api.importreview.ImportReviewTaskRow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectResourceLinkRepository {

    /**
     * The newest accepted snapshot for a project, which is the one the field app works against.
     *
     * <p>Empty when the project has no accepted schedule. A parsed-but-unaccepted snapshot is not a
     * schedule anybody has agreed to work from, so it is deliberately not a fallback.
     */
    Optional<AcceptedSnapshot> findNewestAcceptedSnapshot(UUID projectId);

    /** Active links for one user, ordered by resource UID. */
    List<ProjectResourceLinkRecord> findActiveLinksForUser(UUID projectId, UUID userId);

    /**
     * Every link in the project, active and revoked, resolved against {@code snapshotId} so each row
     * can say whether the snapshot still carries the resource it points at.
     */
    List<ProjectResourceLinkRecord> findLinks(UUID projectId, UUID snapshotId);

    /** The resource UIDs among {@code resourceExternalUids} that {@code snapshotId} actually carries. */
    List<String> findMatchingResourceUids(UUID projectId, UUID snapshotId, List<String> resourceExternalUids);

    /** The snapshot's leaf tasks assigned to any of the given resources, ordered as the schedule is. */
    List<ImportReviewTaskRow> findLeafTasksAssignedToResources(
            UUID projectId, UUID snapshotId, List<String> resourceExternalUids);

    /** The resource's name in {@code snapshotId}, for recording what a new link pointed at. */
    Optional<String> findResourceName(UUID projectId, UUID snapshotId, String resourceExternalUid);

    ProjectResourceLinkRecord createLink(
            UUID projectId,
            UUID userId,
            String resourceExternalUid,
            String resourceNameAtLink,
            UUID linkedByUserId
    );

    Optional<ProjectResourceLinkRecord> findActiveLink(UUID projectId, UUID linkId);

    Optional<ProjectResourceLinkRecord> revokeLink(UUID projectId, UUID linkId, UUID revokedByUserId);

    /**
     * The project's members, as candidates to link.
     *
     * <p>Deliberately not a general "list the users" endpoint. Who may enumerate an organisation's
     * user directory is its own product question; this answers the narrower one the link form
     * actually asks, which is who is already a member of this project.
     */
    List<LinkableUser> findLinkableUsers(UUID projectId);

    /** The snapshot's resources, each saying whether a link already claims it. */
    List<LinkableResource> findLinkableResources(UUID projectId, UUID snapshotId);

    /** A snapshot the field app can work against. */
    record AcceptedSnapshot(UUID id, int snapshotVersion) {
    }

    record LinkableUser(UUID userId, String displayName, String role) {
    }

    record LinkableResource(
            String resourceExternalUid,
            String name,
            String resourceType,
            int assignedLeafTaskCount,
            UUID linkedUserId,
            String linkedUserDisplayName
    ) {
    }
}
