package com.shutdowntracker.api.operations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage for the operational records that sit alongside execution: problems, actions,
 * evidence, and handover.
 *
 * <p>Kept as one interface because these are read and written together — a problem raises
 * actions, an action gathers evidence, and any of them can land in a handover note.
 */
public interface OperationalRecordRepository {

    ProblemRecord createProblem(UUID projectId, UUID raisedByUserId, ProblemCreateRequest request);

    Optional<ProblemRecord> findProblem(UUID projectId, UUID problemId);

    ProblemRecord assignProblem(UUID problemId, UUID assigneeUserId);

    ProblemRecord closeProblem(UUID problemId, UUID resolvedByUserId, String resolutionNote);

    List<ProblemRecord> findOpenProblems(UUID projectId);

    ActionRecord createAction(UUID projectId, UUID createdByUserId, ActionCreateRequest request);

    Optional<ActionRecord> findAction(UUID projectId, UUID actionId);

    ActionRecord completeAction(UUID actionId, UUID completedByUserId);

    List<ActionRecord> findOpenActions(UUID projectId);

    EvidenceRecord createEvidence(UUID projectId, UUID capturedByUserId, EvidenceCreateRequest request);

    List<EvidenceRecord> findEvidenceForTask(UUID projectId, UUID importedTaskId);

    HandoverNoteRecord createHandoverNote(UUID projectId, UUID createdByUserId, HandoverNoteCreateRequest request);

    HandoverNoteRecord acknowledgeHandoverNote(UUID handoverNoteId, UUID acknowledgedByUserId);

    List<HandoverNoteRecord> findUnacknowledgedHandoverNotes(UUID projectId);
}
