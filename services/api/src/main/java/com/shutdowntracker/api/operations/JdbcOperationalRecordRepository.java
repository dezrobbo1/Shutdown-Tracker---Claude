package com.shutdowntracker.api.operations;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class JdbcOperationalRecordRepository implements OperationalRecordRepository {

    private static final String PROBLEM_COLUMNS = """
            id, project_id, imported_task_id, title, description, status, severity,
            blocks_execution, raised_by_user_id, assigned_to_user_id, resolved_at, resolved_by_user_id
            """;

    private static final String ACTION_COLUMNS = """
            id, project_id, problem_id, imported_task_id, title, description, status,
            assigned_to_user_id, due_at, created_by_user_id, completed_at, completed_by_user_id
            """;

    private static final String EVIDENCE_COLUMNS = """
            id, project_id, imported_task_id, problem_id, action_id, task_progress_update_id,
            original_filename, content_type, storage_uri, status, captured_by_user_id, caption
            """;

    private static final String HANDOVER_COLUMNS = """
            id, project_id, imported_task_id, problem_id, shift_label, note,
            requires_acknowledgement, created_by_user_id, acknowledged_by_user_id, acknowledged_at
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcOperationalRecordRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ProblemRecord createProblem(UUID projectId, UUID raisedByUserId, ProblemCreateRequest request) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO problems (
                    project_id, imported_task_id, title, description, severity,
                    blocks_execution, raised_by_user_id
                )
                VALUES (
                    :projectId, :importedTaskId, :title, :description,
                    CAST(:severity AS problem_severity), :blocksExecution, :raisedBy
                )
                RETURNING
                """ + PROBLEM_COLUMNS,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("importedTaskId", request.importedTaskId())
                        .addValue("title", request.title())
                        .addValue("description", request.description())
                        .addValue("severity", request.severity().databaseValue())
                        .addValue("blocksExecution", request.blocksExecution())
                        .addValue("raisedBy", raisedByUserId),
                this::mapProblem);
    }

    @Override
    public Optional<ProblemRecord> findProblem(UUID projectId, UUID problemId) {
        return jdbcTemplate.query(
                "SELECT " + PROBLEM_COLUMNS + " FROM problems WHERE id = :id AND project_id = :projectId",
                new MapSqlParameterSource().addValue("id", problemId).addValue("projectId", projectId),
                this::mapProblem).stream().findFirst();
    }

    @Override
    public ProblemRecord assignProblem(UUID problemId, UUID assigneeUserId) {
        return jdbcTemplate.queryForObject(
                """
                UPDATE problems
                SET assigned_to_user_id = :assignee,
                    status = CAST('assigned' AS problem_status),
                    updated_at = now()
                WHERE id = :id
                RETURNING
                """ + PROBLEM_COLUMNS,
                new MapSqlParameterSource().addValue("id", problemId).addValue("assignee", assigneeUserId),
                this::mapProblem);
    }

    @Override
    public ProblemRecord closeProblem(UUID problemId, UUID resolvedByUserId, String resolutionNote) {
        return jdbcTemplate.queryForObject(
                """
                UPDATE problems
                SET status = CAST('closed' AS problem_status),
                    resolved_at = now(),
                    resolved_by_user_id = :resolvedBy,
                    resolution_note = :note,
                    updated_at = now()
                WHERE id = :id
                RETURNING
                """ + PROBLEM_COLUMNS,
                new MapSqlParameterSource()
                        .addValue("id", problemId)
                        .addValue("resolvedBy", resolvedByUserId)
                        .addValue("note", resolutionNote),
                this::mapProblem);
    }

    @Override
    public List<ProblemRecord> findOpenProblems(UUID projectId) {
        return jdbcTemplate.query(
                "SELECT " + PROBLEM_COLUMNS + """
                 FROM problems
                 WHERE project_id = :projectId AND status NOT IN ('closed', 'superseded')
                 ORDER BY severity DESC, raised_at
                """,
                new MapSqlParameterSource("projectId", projectId), this::mapProblem);
    }

    @Override
    public ActionRecord createAction(UUID projectId, UUID createdByUserId, ActionCreateRequest request) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO actions (
                    project_id, problem_id, imported_task_id, title, description,
                    status, assigned_to_user_id, due_at, created_by_user_id
                )
                VALUES (
                    :projectId, :problemId, :importedTaskId, :title, :description,
                    CAST(:status AS action_status), :assignee, :dueAt, :createdBy
                )
                RETURNING
                """ + ACTION_COLUMNS,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("problemId", request.problemId())
                        .addValue("importedTaskId", request.importedTaskId())
                        .addValue("title", request.title())
                        .addValue("description", request.description())
                        // Assigning at creation moves it straight past 'open'.
                        .addValue("status", request.assignedToUserId() == null
                                ? ActionStatus.OPEN.databaseValue()
                                : ActionStatus.ASSIGNED.databaseValue())
                        .addValue("assignee", request.assignedToUserId())
                        .addValue("dueAt", request.dueAt())
                        .addValue("createdBy", createdByUserId),
                this::mapAction);
    }

    @Override
    public Optional<ActionRecord> findAction(UUID projectId, UUID actionId) {
        return jdbcTemplate.query(
                "SELECT " + ACTION_COLUMNS + " FROM actions WHERE id = :id AND project_id = :projectId",
                new MapSqlParameterSource().addValue("id", actionId).addValue("projectId", projectId),
                this::mapAction).stream().findFirst();
    }

    @Override
    public ActionRecord completeAction(UUID actionId, UUID completedByUserId) {
        return jdbcTemplate.queryForObject(
                """
                UPDATE actions
                SET status = CAST('completed' AS action_status),
                    completed_at = now(),
                    completed_by_user_id = :completedBy,
                    updated_at = now()
                WHERE id = :id
                RETURNING
                """ + ACTION_COLUMNS,
                new MapSqlParameterSource().addValue("id", actionId).addValue("completedBy", completedByUserId),
                this::mapAction);
    }

    @Override
    public List<ActionRecord> findOpenActions(UUID projectId) {
        return jdbcTemplate.query(
                "SELECT " + ACTION_COLUMNS + """
                 FROM actions
                 WHERE project_id = :projectId
                   AND status NOT IN ('completed', 'verified', 'closed', 'superseded')
                 ORDER BY due_at NULLS LAST, created_at
                """,
                new MapSqlParameterSource("projectId", projectId), this::mapAction);
    }

    @Override
    public EvidenceRecord createEvidence(UUID projectId, UUID capturedByUserId, EvidenceCreateRequest request) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO evidence (
                    project_id, imported_task_id, problem_id, action_id, task_progress_update_id,
                    original_filename, content_type, storage_uri, size_bytes, status,
                    captured_by_user_id, caption
                )
                VALUES (
                    :projectId, :importedTaskId, :problemId, :actionId, :progressUpdateId,
                    :originalFilename, :contentType, :storageUri, :sizeBytes,
                    CAST(:status AS evidence_status), :capturedBy, :caption
                )
                RETURNING
                """ + EVIDENCE_COLUMNS,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("importedTaskId", request.importedTaskId())
                        .addValue("problemId", request.problemId())
                        .addValue("actionId", request.actionId())
                        .addValue("progressUpdateId", request.taskProgressUpdateId())
                        .addValue("originalFilename", request.originalFilename())
                        .addValue("contentType", request.contentType())
                        .addValue("storageUri", request.storageUri())
                        .addValue("sizeBytes", request.sizeBytes())
                        // Without a storage location the file has not arrived yet.
                        .addValue("status", request.storageUri() == null
                                ? EvidenceStatus.PENDING_UPLOAD.databaseValue()
                                : EvidenceStatus.UPLOADED.databaseValue())
                        .addValue("capturedBy", capturedByUserId)
                        .addValue("caption", request.caption()),
                this::mapEvidence);
    }

    @Override
    public List<EvidenceRecord> findEvidenceForTask(UUID projectId, UUID importedTaskId) {
        return jdbcTemplate.query(
                "SELECT " + EVIDENCE_COLUMNS + """
                 FROM evidence
                 WHERE project_id = :projectId AND imported_task_id = :taskId
                 ORDER BY captured_at DESC
                """,
                new MapSqlParameterSource().addValue("projectId", projectId).addValue("taskId", importedTaskId),
                this::mapEvidence);
    }

    @Override
    public HandoverNoteRecord createHandoverNote(
            UUID projectId,
            UUID createdByUserId,
            HandoverNoteCreateRequest request
    ) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO handover_notes (
                    project_id, imported_task_id, problem_id, shift_label, note,
                    requires_acknowledgement, created_by_user_id
                )
                VALUES (
                    :projectId, :importedTaskId, :problemId, :shiftLabel, :note,
                    :requiresAcknowledgement, :createdBy
                )
                RETURNING
                """ + HANDOVER_COLUMNS,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("importedTaskId", request.importedTaskId())
                        .addValue("problemId", request.problemId())
                        .addValue("shiftLabel", request.shiftLabel())
                        .addValue("note", request.note())
                        .addValue("requiresAcknowledgement", request.requiresAcknowledgement())
                        .addValue("createdBy", createdByUserId),
                this::mapHandoverNote);
    }

    @Override
    public HandoverNoteRecord acknowledgeHandoverNote(UUID handoverNoteId, UUID acknowledgedByUserId) {
        return jdbcTemplate.queryForObject(
                """
                UPDATE handover_notes
                SET acknowledged_by_user_id = :acknowledgedBy,
                    acknowledged_at = now()
                WHERE id = :id
                RETURNING
                """ + HANDOVER_COLUMNS,
                new MapSqlParameterSource()
                        .addValue("id", handoverNoteId)
                        .addValue("acknowledgedBy", acknowledgedByUserId),
                this::mapHandoverNote);
    }

    @Override
    public List<HandoverNoteRecord> findUnacknowledgedHandoverNotes(UUID projectId) {
        return jdbcTemplate.query(
                "SELECT " + HANDOVER_COLUMNS + """
                 FROM handover_notes
                 WHERE project_id = :projectId
                   AND requires_acknowledgement
                   AND acknowledged_at IS NULL
                 ORDER BY created_at
                """,
                new MapSqlParameterSource("projectId", projectId), this::mapHandoverNote);
    }

    private ProblemRecord mapProblem(ResultSet rs, int rowNum) throws SQLException {
        return new ProblemRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("imported_task_id", UUID.class),
                rs.getString("title"),
                rs.getString("description"),
                ProblemStatus.fromDatabaseValue(rs.getString("status")),
                ProblemSeverity.fromDatabaseValue(rs.getString("severity")),
                rs.getBoolean("blocks_execution"),
                rs.getObject("raised_by_user_id", UUID.class),
                rs.getObject("assigned_to_user_id", UUID.class),
                rs.getObject("resolved_at", OffsetDateTime.class),
                rs.getObject("resolved_by_user_id", UUID.class));
    }

    private ActionRecord mapAction(ResultSet rs, int rowNum) throws SQLException {
        return new ActionRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("problem_id", UUID.class),
                rs.getObject("imported_task_id", UUID.class),
                rs.getString("title"),
                rs.getString("description"),
                ActionStatus.fromDatabaseValue(rs.getString("status")),
                rs.getObject("assigned_to_user_id", UUID.class),
                rs.getObject("due_at", OffsetDateTime.class),
                rs.getObject("created_by_user_id", UUID.class),
                rs.getObject("completed_at", OffsetDateTime.class),
                rs.getObject("completed_by_user_id", UUID.class));
    }

    private EvidenceRecord mapEvidence(ResultSet rs, int rowNum) throws SQLException {
        return new EvidenceRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("imported_task_id", UUID.class),
                rs.getObject("problem_id", UUID.class),
                rs.getObject("action_id", UUID.class),
                rs.getObject("task_progress_update_id", UUID.class),
                rs.getString("original_filename"),
                rs.getString("content_type"),
                rs.getString("storage_uri"),
                EvidenceStatus.fromDatabaseValue(rs.getString("status")),
                rs.getObject("captured_by_user_id", UUID.class),
                rs.getString("caption"));
    }

    private HandoverNoteRecord mapHandoverNote(ResultSet rs, int rowNum) throws SQLException {
        return new HandoverNoteRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("imported_task_id", UUID.class),
                rs.getObject("problem_id", UUID.class),
                rs.getString("shift_label"),
                rs.getString("note"),
                rs.getBoolean("requires_acknowledgement"),
                rs.getObject("created_by_user_id", UUID.class),
                rs.getObject("acknowledged_by_user_id", UUID.class),
                rs.getObject("acknowledged_at", OffsetDateTime.class));
    }
}
