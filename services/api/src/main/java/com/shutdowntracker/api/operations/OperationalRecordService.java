package com.shutdowntracker.api.operations;

import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * Problems, actions, evidence, and handover.
 *
 * <p>These records carry operational truth around execution. None of them touch the
 * imported schedule: a problem can record that work is blocked, but it never moves a date
 * or changes an imported value.
 */
@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class OperationalRecordService {

    private final OperationalRecordRepository repository;
    private final AuditEventRecorder auditEventRecorder;

    public OperationalRecordService(
            OperationalRecordRepository repository,
            AuditEventRecorder auditEventRecorder
    ) {
        this.repository = repository;
        this.auditEventRecorder = auditEventRecorder;
    }

    @Transactional
    public ProblemRecord raiseProblem(UUID projectId, Actor actor, ProblemCreateRequest request) {
        Objects.requireNonNull(actor, "actor is required.");
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

    public List<EvidenceRecord> evidenceForTask(UUID projectId, UUID importedTaskId) {
        return repository.findEvidenceForTask(projectId, importedTaskId);
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
