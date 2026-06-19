package com.shutdowntracker.api.importreview;

import com.shutdowntracker.api.importedproject.ProjectSnapshotStatus;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class ImportReviewService {

    private static final String ACCEPTED_MESSAGE = "Snapshot accepted for Shutdown Tracker review use only. "
            + "No Microsoft Project file was written back.";
    private static final String REJECTED_MESSAGE = "Snapshot rejected for Shutdown Tracker review use only. "
            + "No Microsoft Project file was written back.";

    private final ImportReviewRepository repository;

    public ImportReviewService(ImportReviewRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ImportReviewSnapshotSummary> listSnapshots(UUID projectId) {
        return repository.listSnapshots(requireUuid(projectId, "projectId is required."));
    }

    @Transactional(readOnly = true)
    public ImportReviewSnapshotDetail getSnapshot(UUID projectId, UUID snapshotId) {
        UUID requiredProjectId = requireUuid(projectId, "projectId is required.");
        UUID requiredSnapshotId = requireUuid(snapshotId, "snapshotId is required.");
        ImportReviewSnapshotSummary snapshot = findSnapshot(requiredProjectId, requiredSnapshotId);

        return new ImportReviewSnapshotDetail(
                snapshot,
                repository.listTasks(requiredProjectId, requiredSnapshotId),
                repository.listResources(requiredProjectId, requiredSnapshotId),
                repository.listAssignments(requiredProjectId, requiredSnapshotId),
                repository.listExtendedAttributes(requiredProjectId, requiredSnapshotId)
        );
    }

    @Transactional
    public ImportReviewDecisionResponse acceptSnapshot(UUID projectId, UUID snapshotId) {
        return recordDecision(projectId, snapshotId, ProjectSnapshotStatus.ACCEPTED, ACCEPTED_MESSAGE);
    }

    @Transactional
    public ImportReviewDecisionResponse rejectSnapshot(UUID projectId, UUID snapshotId) {
        return recordDecision(projectId, snapshotId, ProjectSnapshotStatus.REJECTED, REJECTED_MESSAGE);
    }

    private ImportReviewDecisionResponse recordDecision(
            UUID projectId,
            UUID snapshotId,
            ProjectSnapshotStatus targetStatus,
            String message
    ) {
        UUID requiredProjectId = requireUuid(projectId, "projectId is required.");
        UUID requiredSnapshotId = requireUuid(snapshotId, "snapshotId is required.");
        ImportReviewSnapshotSummary existing = findSnapshot(requiredProjectId, requiredSnapshotId);

        if (existing.status() != ProjectSnapshotStatus.PARSED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only parsed snapshots can be accepted or rejected."
            );
        }

        ImportReviewSnapshotSummary updated = repository
                .recordSnapshotDecision(requiredProjectId, requiredSnapshotId, targetStatus)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Snapshot decision could not be recorded because the snapshot is no longer parsed."
                ));

        return new ImportReviewDecisionResponse(updated, message);
    }

    private ImportReviewSnapshotSummary findSnapshot(UUID projectId, UUID snapshotId) {
        return repository.findSnapshot(projectId, snapshotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project snapshot not found."));
    }

    private UUID requireUuid(UUID value, String message) {
        return Objects.requireNonNull(value, message);
    }
}
