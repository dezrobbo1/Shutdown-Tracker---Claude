package com.shutdowntracker.api.criticalwatch;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.audit.AuditEventCategory;
import com.shutdowntracker.api.audit.AuditEventCreateRequest;
import com.shutdowntracker.api.audit.AuditEventRecorder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Critical Watchlists and Critical Work Package reporting.
 *
 * <p>A Critical Work Package is a reporting object. Its membership is chosen by a planner
 * from summary tasks in the imported schedule; it is never derived from critical path,
 * float, or slack, none of which this product calculates. Reporting on a package does not
 * change any imported value.
 */
@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class CriticalWatchService {

    private static final String SOURCE_SUMMARY_TASK = "summary_task";
    private static final String SOURCE_MULTI_SUMMARY = "multi_summary";

    private final CriticalWatchRepository repository;
    private final AuditEventRecorder auditEventRecorder;

    public CriticalWatchService(CriticalWatchRepository repository, AuditEventRecorder auditEventRecorder) {
        this.repository = repository;
        this.auditEventRecorder = auditEventRecorder;
    }

    @Transactional
    public CriticalWatchlistRecord createWatchlist(
            UUID projectId, Actor actor, String name, String description) {
        CriticalWatchlistRecord watchlist =
                repository.createWatchlist(projectId, name, description, actor.userId());
        audit(projectId, actor, "critical_watchlist.created", "critical_watchlist",
                watchlist.id(), watchlist.name());
        return watchlist;
    }

    public List<CriticalWatchlistRecord> watchlists(UUID projectId) {
        return repository.findActiveWatchlists(projectId);
    }

    @Transactional
    public CriticalWorkPackageRecord createWorkPackage(
            UUID projectId, Actor actor, UUID watchlistId, String name, String description) {
        CriticalWorkPackageRecord workPackage =
                repository.createWorkPackage(projectId, watchlistId, name, description, actor.userId());
        audit(projectId, actor, "critical_work_package.created", "critical_work_package",
                workPackage.id(), workPackage.name());
        return workPackage;
    }

    public List<CriticalWorkPackageRecord> workPackages(UUID projectId, UUID watchlistId) {
        return repository.findWorkPackages(projectId, watchlistId);
    }

    /**
     * How much reporting each package has received, for the attention surface.
     *
     * <p>It reports coverage, not lateness. Until reporting policies exist there is no
     * schedule to be late against, and a package nobody has reported on is a fact this can
     * state without inventing one.
     */
    public List<CriticalWorkPackageReportingSummary> reportingSummaries(UUID projectId) {
        return repository.findReportingSummaries(projectId);
    }

    /**
     * Adds a summary task as a source of work for a package.
     *
     * <p>The source type is derived rather than taken from the caller: a package drawing on
     * more than one summary task is a multi-summary group, which is the case the product
     * calls out for reporting groups that cross schedule boundaries.
     */
    @Transactional
    public CriticalWorkPackageSourceRecord addSource(
            UUID projectId,
            Actor actor,
            UUID workPackageId,
            UUID projectSnapshotId,
            UUID importedTaskId,
            boolean includeDescendants
    ) {
        repository.findWorkPackage(projectId, workPackageId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Critical work package not found."));

        boolean alreadyHasSource = !repository.findSources(workPackageId).isEmpty();
        String sourceType = alreadyHasSource ? SOURCE_MULTI_SUMMARY : SOURCE_SUMMARY_TASK;

        CriticalWorkPackageSourceRecord source = repository.addSource(
                projectId, workPackageId, projectSnapshotId, importedTaskId,
                sourceType, includeDescendants, actor.userId());

        audit(projectId, actor, "critical_work_package.source_added", "critical_work_package",
                workPackageId, sourceType);
        return source;
    }

    /**
     * The tasks a package reports on.
     *
     * <p>Expanded from its summary-task sources through the imported hierarchy. Grouping
     * only: no dates are rolled up and no schedule value is derived.
     */
    public List<UUID> reportedTasks(UUID projectId, UUID workPackageId) {
        repository.findWorkPackage(projectId, workPackageId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Critical work package not found."));
        return repository.findReportedTaskIds(workPackageId);
    }

    /**
     * Submits a Critical Update.
     *
     * <p>Updates are immutable. A correction supersedes the earlier report rather than
     * editing it, so what was said at the time remains readable. A repeated idempotency key
     * returns the original, which is what makes the offline queue safe to retry.
     */
    @Transactional
    public CriticalUpdateRecord submitUpdate(UUID projectId, Actor actor, CriticalUpdateSubmitRequest request) {
        if (request.idempotencyKey() != null) {
            var existing = repository.findUpdateByIdempotencyKey(projectId, request.idempotencyKey());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        repository.findWorkPackage(projectId, request.criticalWorkPackageId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Critical work package not found."));

        if (request.supersedesCriticalUpdateId() != null) {
            // A correction may only retire a report on the same package. Naming an update
            // that lives elsewhere changes nothing, and is refused rather than ignored:
            // silently accepting it would report a supersession that never happened.
            int superseded = repository.markUpdateSuperseded(
                    projectId, request.criticalWorkPackageId(), request.supersedesCriticalUpdateId());
            if (superseded == 0) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "The update being corrected does not belong to this critical work package.");
            }
        }

        CriticalUpdateRecord update = repository.submitUpdate(projectId, actor.userId(), request);

        audit(projectId, actor, "critical_update.submitted", "critical_update",
                update.id(), update.updateMode());
        return update;
    }

    public List<CriticalUpdateRecord> updates(UUID projectId, UUID workPackageId) {
        repository.findWorkPackage(projectId, workPackageId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Critical work package not found."));
        return repository.findUpdates(workPackageId);
    }

    private void audit(UUID projectId, Actor actor, String eventType, String entityType,
                       UUID entityId, String displayName) {
        auditEventRecorder.record(AuditEventCreateRequest.userEvent(
                projectId, actor.userId(), actor.displayName(), actor.role(),
                AuditEventCategory.IMPORT, eventType, entityType, entityId, displayName,
                Map.of(), Map.of(), null, null, null,
                // Recorded explicitly: Critical Watch is reporting, not scheduling.
                Map.of("criticalPathCalculated", false, "projectWriteBack", false)));
    }
}
