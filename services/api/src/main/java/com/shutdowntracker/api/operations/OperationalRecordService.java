package com.shutdowntracker.api.operations;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.audit.AuditEventCategory;
import com.shutdowntracker.api.audit.AuditEventCreateRequest;
import com.shutdowntracker.api.audit.AuditEventRecorder;
import com.shutdowntracker.api.operations.storage.EvidenceStorage;
import com.shutdowntracker.api.operations.storage.EvidenceStorageProperties;
import com.shutdowntracker.api.operations.storage.EvidenceStorageRequest;
import com.shutdowntracker.api.operations.storage.StoredEvidence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Problems, actions, evidence, and handover.
 *
 * <p>These records carry operational truth around execution. None of them touch the
 * imported schedule: a problem can record that work is blocked, but it never moves a date
 * or changes an imported value.
 */
@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class OperationalRecordService {

    /** How much of a project's evidence one read returns. */
    public static final int PROJECT_EVIDENCE_LIMIT = 200;

    private final OperationalRecordRepository repository;
    private final AuditEventRecorder auditEventRecorder;
    private final EvidenceStorage evidenceStorage;
    private final EvidenceStorageProperties evidenceStorageProperties;

    public OperationalRecordService(
            OperationalRecordRepository repository,
            AuditEventRecorder auditEventRecorder,
            EvidenceStorage evidenceStorage,
            EvidenceStorageProperties evidenceStorageProperties
    ) {
        this.repository = repository;
        this.auditEventRecorder = auditEventRecorder;
        this.evidenceStorage = evidenceStorage;
        this.evidenceStorageProperties = evidenceStorageProperties;
    }

    /**
     * Raises a problem.
     *
     * <p>A repeated idempotency key returns the problem the first capture raised, which is what
     * makes a problem safe to hold in the field app's offline queue: a retry over a bad
     * connection cannot produce a second record of the same thing. The replay is not audited
     * again — the problem was raised once, and an audit trail saying otherwise would be wrong.
     */
    @Transactional
    public ProblemRecord raiseProblem(UUID projectId, Actor actor, ProblemCreateRequest request) {
        Objects.requireNonNull(actor, "actor is required.");
        if (request.idempotencyKey() != null) {
            var existing = repository.findProblemByIdempotencyKey(projectId, request.idempotencyKey());
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        ProblemRecord problem = repository.createProblem(projectId, actor.userId(), request);
        audit(projectId, actor, "problem.raised", "problem", problem.id(), problem.title(),
                Map.of("severity", problem.severity().databaseValue(),
                        "blocksExecution", problem.blocksExecution()));
        return problem;
    }

    @Transactional
    public ProblemRecord assignProblem(UUID projectId, Actor actor, UUID problemId, UUID assigneeUserId) {
        ProblemRecord existing = requireProblem(projectId, problemId);
        if (existing.status().isTerminal()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A closed problem cannot be reassigned.");
        }
        ProblemRecord assigned = repository.assignProblem(problemId, assigneeUserId);
        audit(projectId, actor, "problem.assigned", "problem", problemId, assigned.title(),
                Map.of("assignedTo", String.valueOf(assigneeUserId)));
        return assigned;
    }

    @Transactional
    public ProblemRecord closeProblem(UUID projectId, Actor actor, UUID problemId, String resolutionNote) {
        ProblemRecord existing = requireProblem(projectId, problemId);
        if (existing.status().isTerminal()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This problem is already closed.");
        }
        ProblemRecord closed = repository.closeProblem(problemId, actor.userId(), resolutionNote);
        audit(projectId, actor, "problem.closed", "problem", problemId, closed.title(),
                Map.of("resolutionNote", resolutionNote == null ? "" : resolutionNote));
        return closed;
    }

    public List<ProblemRecord> openProblems(UUID projectId) {
        return repository.findOpenProblems(projectId);
    }

    @Transactional
    public ActionRecord createAction(UUID projectId, Actor actor, ActionCreateRequest request) {
        if (request.problemId() != null) {
            requireProblem(projectId, request.problemId());
        }
        ActionRecord action = repository.createAction(projectId, actor.userId(), request);
        audit(projectId, actor, "action.created", "action", action.id(), action.title(), Map.of());
        return action;
    }

    @Transactional
    public ActionRecord completeAction(UUID projectId, Actor actor, UUID actionId) {
        ActionRecord existing = repository.findAction(projectId, actionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Action not found."));
        if (existing.status().requiresCompletionAttribution()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This action is already complete.");
        }
        ActionRecord completed = repository.completeAction(actionId, actor.userId());
        audit(projectId, actor, "action.completed", "action", actionId, completed.title(), Map.of());
        return completed;
    }

    public List<ActionRecord> openActions(UUID projectId) {
        return repository.findOpenActions(projectId);
    }

    /**
     * Registers evidence metadata. The binary never passes through here; it goes to object
     * storage and the record points at it.
     */
    @Transactional
    public EvidenceRecord registerEvidence(UUID projectId, Actor actor, EvidenceCreateRequest request) {
        EvidenceRecord evidence = repository.createEvidence(projectId, actor.userId(), request);
        audit(projectId, actor, "evidence.registered", "evidence", evidence.id(),
                evidence.originalFilename(), Map.of("status", evidence.status().databaseValue()));
        return evidence;
    }

    /**
     * The project's evidence, newest first.
     *
     * <p>Bounded, because a shutdown accumulates evidence for as long as it runs. The caller is
     * handed at most {@link #PROJECT_EVIDENCE_LIMIT} records and knows the limit, so a list that
     * was cut can say so rather than appearing to be all of it.
     */
    public List<EvidenceRecord> evidenceForProject(UUID projectId) {
        return repository.findEvidenceForProject(projectId, PROJECT_EVIDENCE_LIMIT);
    }

    public List<EvidenceRecord> evidenceForTask(UUID projectId, UUID importedTaskId) {
        return repository.findEvidenceForTask(projectId, importedTaskId);
    }

    /**
     * Stores the binary an evidence record was registered for, and moves the record to
     * {@code uploaded}.
     *
     * <p>Registration and upload are separate calls because they can be separated in time: a record
     * captured with no connection is registered when one returns, and the file follows. Until the
     * file follows, {@code pending_upload} is the record saying so, which is the difference between
     * evidence that exists and a note that it is outstanding.
     *
     * <p>The binary is written before the row is updated. Either order can fail in the middle; this
     * one leaves an unreferenced file behind, and the other leaves a row naming a file that is not
     * there. A row that lies about its own evidence is the worse of the two.
     */
    @Transactional
    public EvidenceRecord uploadEvidenceContent(
            UUID projectId,
            Actor actor,
            UUID evidenceId,
            MultipartFile file
    ) {
        Objects.requireNonNull(actor, "actor is required.");
        Objects.requireNonNull(file, "file is required.");

        EvidenceRecord registered = repository.findEvidence(projectId, evidenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found."));
        requireUploadable(registered, file);

        StoredEvidence stored = store(file, registered.originalFilename());
        EvidenceRecord uploaded = repository
                .attachEvidenceContent(projectId, evidenceId, stored.storageUri(), contentType(file, registered),
                        stored.sizeBytes(), stored.contentHashSha256())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Evidence content has already been uploaded. Register a new record to supersede it."));

        audit(projectId, actor, "evidence.uploaded", "evidence", uploaded.id(), uploaded.originalFilename(),
                Map.of(
                        "status", uploaded.status().databaseValue(),
                        "sizeBytes", stored.sizeBytes(),
                        "contentHash", stored.contentHashSha256()));
        return uploaded;
    }

    /**
     * Opens the stored binary for an evidence record. The caller closes the stream.
     */
    public EvidenceContent readEvidenceContent(UUID projectId, UUID evidenceId) {
        EvidenceRecord record = repository.findEvidence(projectId, evidenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found."));
        if (record.storageUri() == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Evidence has been registered but its file has not been uploaded.");
        }
        try {
            InputStream content = evidenceStorage.read(record.storageUri());
            return new EvidenceContent(record, content);
        } catch (IOException exception) {
            // The row survives a missing file; reporting it as absent is more useful than a 500
            // that says nothing about which of the two is wrong.
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Evidence file could not be read from storage.", exception);
        }
    }

    private void requireUploadable(EvidenceRecord registered, MultipartFile file) {
        if (registered.status() != EvidenceStatus.PENDING_UPLOAD) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Evidence is " + registered.status().databaseValue()
                            + " and already has its file. Register a new record to supersede it.");
        }
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evidence file is empty.");
        }
        if (file.getSize() > evidenceStorageProperties.maxSizeBytes()) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Evidence file exceeds the limit of " + evidenceStorageProperties.maxSizeBytes() + " bytes.");
        }
        // A size declared at registration described the file the record was raised for. A different
        // file is not that evidence, whatever it shows.
        if (registered.sizeBytes() != null && registered.sizeBytes() != file.getSize()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Uploaded file is " + file.getSize() + " bytes; this evidence was registered as "
                            + registered.sizeBytes() + " bytes.");
        }
    }

    private StoredEvidence store(MultipartFile file, String originalFilename) {
        try {
            return evidenceStorage.store(
                    new EvidenceStorageRequest(originalFilename, file.getInputStream(), file.getSize()));
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Evidence file could not be stored.", exception);
        }
    }

    private String contentType(MultipartFile file, EvidenceRecord registered) {
        String uploaded = file.getContentType();
        if (uploaded != null && !uploaded.isBlank()) {
            return uploaded;
        }
        return registered.contentType() == null ? "application/octet-stream" : registered.contentType();
    }

    @Transactional
    public HandoverNoteRecord createHandoverNote(
            UUID projectId,
            Actor actor,
            HandoverNoteCreateRequest request
    ) {
        HandoverNoteRecord note = repository.createHandoverNote(projectId, actor.userId(), request);
        audit(projectId, actor, "handover.note_created", "handover_note", note.id(), note.shiftLabel(),
                Map.of("requiresAcknowledgement", note.requiresAcknowledgement()));
        return note;
    }

    @Transactional
    public HandoverNoteRecord acknowledgeHandoverNote(UUID projectId, Actor actor, UUID handoverNoteId) {
        HandoverNoteRecord acknowledged = repository.acknowledgeHandoverNote(handoverNoteId, actor.userId());
        audit(projectId, actor, "handover.note_acknowledged", "handover_note", handoverNoteId,
                acknowledged.shiftLabel(), Map.of());
        return acknowledged;
    }

    public List<HandoverNoteRecord> unacknowledgedHandoverNotes(UUID projectId) {
        return repository.findUnacknowledgedHandoverNotes(projectId);
    }

    private ProblemRecord requireProblem(UUID projectId, UUID problemId) {
        return repository.findProblem(projectId, problemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Problem not found."));
    }

    private void audit(
            UUID projectId,
            Actor actor,
            String eventType,
            String entityType,
            UUID entityId,
            String displayName,
            Map<String, Object> newValues
    ) {
        auditEventRecorder.record(AuditEventCreateRequest.userEvent(
                projectId, actor.userId(), actor.displayName(), actor.role(),
                AuditEventCategory.IMPORT, eventType,
                entityType, entityId, displayName,
                Map.of(), newValues, null, null, null,
                Map.of("projectWriteBack", false)));
    }
}
