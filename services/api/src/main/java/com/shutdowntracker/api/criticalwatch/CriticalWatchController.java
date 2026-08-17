package com.shutdowntracker.api.criticalwatch;

import java.util.List;
import java.util.UUID;
import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.identity.Capability;
import com.shutdowntracker.api.identity.ProjectAuthorizationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Critical Watchlists, Critical Work Packages, and the reports made against them.
 *
 * <p>Two capabilities, because two different jobs are being done. Composing a watchlist is a
 * planning act, owned by planners and shutdown control. Reporting against one is an execution
 * act, done by the people on the work — which is why a planner may build a Critical Work
 * Package but not file a Critical Update on it.
 *
 * <p>The submitter of an update is always the resolved actor. {@link CriticalUpdateSubmitRequest}
 * carries no user id for a caller to assert, so there is nothing here to strip.
 */
@RestController
@RequestMapping("/api/projects/{projectId}")
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class CriticalWatchController {

    private final CriticalWatchService service;
    private final ProjectAuthorizationService authorization;

    public CriticalWatchController(
            CriticalWatchService service,
            ProjectAuthorizationService authorization
    ) {
        this.service = service;
        this.authorization = authorization;
    }

    @PostMapping("/critical-watchlists")
    public CriticalWatchlistRecord createWatchlist(
            @PathVariable UUID projectId, Actor actor, @RequestBody WatchlistCreateRequest request) {
        authorization.requireCapability(projectId, actor, Capability.MANAGE_CRITICAL_WATCHLIST);
        return service.createWatchlist(projectId, actor, request.name(), request.description());
    }

    @GetMapping("/critical-watchlists")
    public List<CriticalWatchlistRecord> watchlists(@PathVariable UUID projectId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.VIEW_PROJECT);
        return service.watchlists(projectId);
    }

    /** Reporting coverage per package. Coverage, not lateness — see the service. */
    @GetMapping("/critical-watch/reporting-summary")
    public List<CriticalWorkPackageReportingSummary> reportingSummary(
            @PathVariable UUID projectId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.VIEW_PROJECT);
        return service.reportingSummaries(projectId);
    }

    @PostMapping("/critical-watchlists/{watchlistId}/work-packages")
    public CriticalWorkPackageRecord createWorkPackage(
            @PathVariable UUID projectId,
            @PathVariable UUID watchlistId,
            Actor actor,
            @RequestBody WorkPackageCreateRequest request
    ) {
        authorization.requireCapability(projectId, actor, Capability.MANAGE_CRITICAL_WATCHLIST);
        return service.createWorkPackage(
                projectId, actor, watchlistId, request.name(), request.description());
    }

    @GetMapping("/critical-watchlists/{watchlistId}/work-packages")
    public List<CriticalWorkPackageRecord> workPackages(
            @PathVariable UUID projectId, @PathVariable UUID watchlistId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.VIEW_PROJECT);
        return service.workPackages(projectId, watchlistId);
    }

    /**
     * Adds a summary task as a source of work for a package.
     *
     * <p>Whether this makes the package multi-summary is decided by the server from what the
     * package already draws on, not asserted by the caller.
     */
    @PostMapping("/critical-work-packages/{workPackageId}/sources")
    public CriticalWorkPackageSourceRecord addSource(
            @PathVariable UUID projectId,
            @PathVariable UUID workPackageId,
            Actor actor,
            @RequestBody WorkPackageSourceRequest request
    ) {
        authorization.requireCapability(projectId, actor, Capability.MANAGE_CRITICAL_WATCHLIST);
        return service.addSource(
                projectId,
                actor,
                workPackageId,
                request.projectSnapshotId(),
                request.importedTaskId(),
                request.includeDescendants());
    }

    /** The tasks a package reports on. Grouping only — no dates are rolled up. */
    @GetMapping("/critical-work-packages/{workPackageId}/reported-tasks")
    public List<UUID> reportedTasks(
            @PathVariable UUID projectId, @PathVariable UUID workPackageId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.VIEW_PROJECT);
        return service.reportedTasks(projectId, workPackageId);
    }

    @PostMapping("/critical-updates")
    public CriticalUpdateRecord submitUpdate(
            @PathVariable UUID projectId, Actor actor, @RequestBody CriticalUpdateSubmitRequest request) {
        authorization.requireCapability(projectId, actor, Capability.SUBMIT_CRITICAL_UPDATE);
        return service.submitUpdate(projectId, actor, request);
    }

    @GetMapping("/critical-work-packages/{workPackageId}/updates")
    public List<CriticalUpdateRecord> updates(
            @PathVariable UUID projectId, @PathVariable UUID workPackageId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.VIEW_PROJECT);
        return service.updates(projectId, workPackageId);
    }

    public record WatchlistCreateRequest(String name, String description) {
    }

    public record WorkPackageCreateRequest(String name, String description) {
    }

    /**
     * Naming one summary task as a source of work.
     *
     * <p>{@code includeDescendants} is a primitive so an omitted value is false rather than a
     * null dereference; the common case, a summary task plus everything under it, is stated
     * explicitly by the caller.
     */
    public record WorkPackageSourceRequest(
            UUID projectSnapshotId,
            UUID importedTaskId,
            boolean includeDescendants
    ) {
    }
}
