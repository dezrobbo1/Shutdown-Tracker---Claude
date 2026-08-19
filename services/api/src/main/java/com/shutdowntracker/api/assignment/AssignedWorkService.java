package com.shutdowntracker.api.assignment;

import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.audit.AuditEventCategory;
import com.shutdowntracker.api.audit.AuditEventCreateRequest;
import com.shutdowntracker.api.audit.AuditEventRecorder;
import com.shutdowntracker.api.audit.AuditEventTypes;
import com.shutdowntracker.api.importreview.ImportReviewTaskRow;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves what work belongs to a person, and curates the links that decide it.
 *
 * <p>A link says a Microsoft Project resource <em>is</em> a Shutdown Tracker user. It is created by
 * somebody holding {@code MANAGE_RESOURCE_LINK}, never inferred from a name, and it changes what a
 * work list <em>shows</em> and nothing else. No capability check anywhere consults it: a person with
 * no link keeps every permission their project membership grants, and linking somebody to a resource
 * grants them nothing they did not already have. That separation is the rule in {@code AGENTS.md}
 * that Project-derived membership is not application authorization, and it is why this service can
 * be read by anyone who can view the project.
 */
@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class AssignedWorkService {

    private final ProjectResourceLinkRepository repository;
    private final AuditEventRecorder auditEventRecorder;

    public AssignedWorkService(ProjectResourceLinkRepository repository, AuditEventRecorder auditEventRecorder) {
        this.repository = repository;
        this.auditEventRecorder = auditEventRecorder;
    }

    /**
     * The acting user's own work in the newest accepted snapshot.
     *
     * <p>Always the acting user: there is no user parameter, so nothing can ask for somebody else's
     * work list by changing a path. A supervisor who needs to see a crew's work reads the schedule,
     * which is a different question with a different answer.
     */
    public AssignedWorkView assignedWork(UUID projectId, Actor actor) {
        List<ProjectResourceLinkRecord> links = repository.findActiveLinksForUser(projectId, actor.userId());
        List<String> linkedUids = links.stream().map(ProjectResourceLinkRecord::resourceExternalUid).toList();
        boolean linked = !links.isEmpty();

        Optional<ProjectResourceLinkRepository.AcceptedSnapshot> snapshot =
                repository.findNewestAcceptedSnapshot(projectId);
        if (snapshot.isEmpty()) {
            return AssignedWorkView.noAcceptedSnapshot(projectId, linked, linkedUids);
        }

        ProjectResourceLinkRepository.AcceptedSnapshot accepted = snapshot.get();
        List<String> matched = repository.findMatchingResourceUids(projectId, accepted.id(), linkedUids);
        List<String> unmatched = linkedUids.stream().filter(uid -> !matched.contains(uid)).toList();

        // Only matched UIDs are queried. An unmatched one would return nothing anyway, and passing it
        // would let a stale link look like a resource with no work rather than a resource this
        // snapshot has lost.
        List<ImportReviewTaskRow> tasks =
                repository.findLeafTasksAssignedToResources(projectId, accepted.id(), matched);

        return new AssignedWorkView(
                projectId, accepted.id(), accepted.snapshotVersion(), linked, linkedUids, unmatched, tasks);
    }

    /** Every link in the project, resolved against the newest accepted snapshot. */
    public List<ProjectResourceLinkRecord> links(UUID projectId) {
        UUID snapshotId = repository.findNewestAcceptedSnapshot(projectId)
                .map(ProjectResourceLinkRepository.AcceptedSnapshot::id)
                .orElse(null);
        return repository.findLinks(projectId, snapshotId);
    }

    /**
     * What a planner can choose between when linking.
     *
     * <p>Resources come from the newest accepted snapshot, because a resource that no schedule
     * carries is not something to offer from a picker — a link to one can still be made by UID, for
     * the planner preparing ahead, but it is not proposed here as though it were known.
     */
    public LinkCandidates candidates(UUID projectId) {
        Optional<ProjectResourceLinkRepository.AcceptedSnapshot> snapshot =
                repository.findNewestAcceptedSnapshot(projectId);
        return new LinkCandidates(
                repository.findLinkableUsers(projectId),
                snapshot
                        .map(accepted -> repository.findLinkableResources(projectId, accepted.id()))
                        .orElse(List.of()),
                snapshot.map(ProjectResourceLinkRepository.AcceptedSnapshot::id).orElse(null));
    }

    @Transactional
    public ProjectResourceLinkRecord link(UUID projectId, Actor actor, ProjectResourceLinkCreateRequest request) {
        Optional<ProjectResourceLinkRepository.AcceptedSnapshot> snapshot =
                repository.findNewestAcceptedSnapshot(projectId);

        // The name is recorded, not required. Linking a resource the current snapshot does not carry
        // is allowed on purpose — a planner may prepare links before the schedule that names them
        // arrives — and the resulting link reports as unmatched until it does.
        String resourceName = snapshot
                .flatMap(accepted -> repository.findResourceName(projectId, accepted.id(), request.resourceExternalUid()))
                .orElse(null);

        ProjectResourceLinkRecord created;
        try {
            created = repository.createLink(
                    projectId, request.userId(), request.resourceExternalUid(), resourceName, actor.userId());
        } catch (DuplicateKeyException e) {
            // The partial unique index, not a read-then-write check: two planners linking the same
            // resource at once must not both succeed.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Resource '" + request.resourceExternalUid() + "' is already linked to a user in this project.");
        }

        auditEventRecorder.record(AuditEventCreateRequest.userEvent(
                projectId,
                actor.userId(),
                actor.displayName(),
                actor.role(),
                // `project`, not `permission`: this is project configuration, and recording it as a
                // permission event would claim the link grants something. It does not.
                AuditEventCategory.PROJECT,
                AuditEventTypes.PROJECT_RESOURCE_LINKED,
                "project_resource_link",
                created.id(),
                created.resourceExternalUid(),
                Map.of(),
                linkSummary(created),
                null,
                snapshot.map(ProjectResourceLinkRepository.AcceptedSnapshot::id).orElse(null),
                null,
                Map.of("grants", "relevance_only")));

        return created;
    }

    @Transactional
    public ProjectResourceLinkRecord revoke(UUID projectId, Actor actor, UUID linkId) {
        ProjectResourceLinkRecord existing = repository.findActiveLink(projectId, linkId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No active resource link with that id in this project."));

        ProjectResourceLinkRecord revoked = repository.revokeLink(projectId, linkId, actor.userId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "That resource link was revoked by someone else."));

        auditEventRecorder.record(AuditEventCreateRequest.userEvent(
                projectId,
                actor.userId(),
                actor.displayName(),
                actor.role(),
                AuditEventCategory.PROJECT,
                AuditEventTypes.PROJECT_RESOURCE_LINK_REVOKED,
                "project_resource_link",
                revoked.id(),
                revoked.resourceExternalUid(),
                linkSummary(existing),
                linkSummary(revoked),
                null,
                null,
                null,
                Map.of("grants", "relevance_only")));

        return revoked;
    }

    /** Users and resources a link can be made between, plus the snapshot the resources came from. */
    public record LinkCandidates(
            List<ProjectResourceLinkRepository.LinkableUser> users,
            List<ProjectResourceLinkRepository.LinkableResource> resources,
            UUID projectSnapshotId
    ) {
    }

    private static Map<String, Object> linkSummary(ProjectResourceLinkRecord link) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("userId", link.userId().toString());
        summary.put("resourceExternalUid", link.resourceExternalUid());
        summary.put("active", link.active());
        return Map.copyOf(summary);
    }
}
