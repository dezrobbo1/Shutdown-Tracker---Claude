package com.shutdowntracker.api.execution;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class JdbcTaskProgressRepository implements TaskProgressRepository {

    private static final String COLUMNS = """
            id, project_id, project_snapshot_id, imported_task_id, execution_state,
            percent_complete, actual_start, actual_finish, physical_percent_complete,
            comment, submitted_by_user_id, progress_review_state, planner_review_state,
            export_state, supersedes_progress_update_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcTaskProgressRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public TaskProgressUpdateRecord submit(
            UUID projectId,
            UUID submittedByUserId,
            TaskProgressSubmitRequest request
    ) {
        // The snapshot is taken from the task itself so a submission is always tied to the
        // imported snapshot it was made against, even after a later re-import.
        String sql = """
                INSERT INTO task_progress_updates (
                    project_id, project_snapshot_id, imported_task_id, execution_state,
                    percent_complete, actual_start, actual_finish, physical_percent_complete,
                    comment, submitted_by_user_id, idempotency_key, offline_local_id,
                    supersedes_progress_update_id
                )
                SELECT
                    :projectId,
                    t.project_snapshot_id,
                    t.id,
                    CAST(:executionState AS task_execution_state),
                    :percentComplete,
                    :actualStart,
                    :actualFinish,
                    :physicalPercentComplete,
                    :comment,
                    :submittedByUserId,
                    :idempotencyKey,
                    :offlineLocalId,
                    :supersedes
                FROM imported_tasks t
                WHERE t.id = :importedTaskId AND t.project_id = :projectId
                RETURNING
                """ + COLUMNS;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("importedTaskId", request.importedTaskId())
                .addValue("executionState", request.executionState().databaseValue())
                .addValue("percentComplete", request.percentComplete())
                .addValue("actualStart", request.actualStart())
                .addValue("actualFinish", request.actualFinish())
                .addValue("physicalPercentComplete", request.physicalPercentComplete())
                .addValue("comment", request.comment())
                .addValue("submittedByUserId", submittedByUserId)
                .addValue("idempotencyKey", request.idempotencyKey())
                .addValue("offlineLocalId", request.offlineLocalId())
                .addValue("supersedes", request.supersedesProgressUpdateId());

        return jdbcTemplate.query(sql, parameters, this::map).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Imported task not found on this project: " + request.importedTaskId()));
    }

    @Override
    public Optional<TaskProgressUpdateRecord> find(UUID projectId, UUID progressUpdateId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM task_progress_updates WHERE id = :id AND project_id = :projectId",
                new MapSqlParameterSource().addValue("id", progressUpdateId).addValue("projectId", projectId),
                this::map).stream().findFirst();
    }

    @Override
    public Optional<TaskProgressUpdateRecord> findByIdempotencyKey(UUID projectId, String idempotencyKey) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS
                        + " FROM task_progress_updates WHERE project_id = :projectId AND idempotency_key = :key",
                new MapSqlParameterSource().addValue("projectId", projectId).addValue("key", idempotencyKey),
                this::map).stream().findFirst();
    }

    @Override
    public TaskProgressUpdateRecord recordSupervisorDecision(
            UUID progressUpdateId,
            ProgressReviewState decision,
            PlannerReviewState plannerReviewState,
            UUID reviewedByUserId,
            String note
    ) {
        String sql = """
                UPDATE task_progress_updates
                SET progress_review_state = CAST(:decision AS progress_review_state),
                    planner_review_state = CAST(:plannerReviewState AS planner_review_state),
                    supervisor_reviewed_by_user_id = :reviewedBy,
                    supervisor_reviewed_at = now(),
                    supervisor_review_note = :note
                WHERE id = :id
                RETURNING
                """ + COLUMNS;

        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("id", progressUpdateId)
                .addValue("decision", decision.databaseValue())
                .addValue("plannerReviewState", plannerReviewState.databaseValue())
                .addValue("reviewedBy", reviewedByUserId)
                .addValue("note", note), this::map);
    }

    @Override
    public TaskProgressUpdateRecord recordPlannerDecision(
            UUID progressUpdateId,
            PlannerReviewState decision,
            ProgressExportState exportState,
            UUID reviewedByUserId,
            String note
    ) {
        String sql = """
                UPDATE task_progress_updates
                SET planner_review_state = CAST(:decision AS planner_review_state),
                    export_state = CAST(:exportState AS progress_export_state),
                    planner_reviewed_by_user_id = :reviewedBy,
                    planner_reviewed_at = now(),
                    planner_review_note = :note
                WHERE id = :id
                RETURNING
                """ + COLUMNS;

        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("id", progressUpdateId)
                .addValue("decision", decision.databaseValue())
                .addValue("exportState", exportState.databaseValue())
                .addValue("reviewedBy", reviewedByUserId)
                .addValue("note", note), this::map);
    }

    @Override
    public List<TaskProgressUpdateRecord> findSupervisorQueue(UUID projectId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + """
                 FROM task_progress_updates
                 WHERE project_id = :projectId AND progress_review_state = 'submitted'
                 ORDER BY submitted_at
                """,
                new MapSqlParameterSource("projectId", projectId), this::map);
    }

    @Override
    public List<TaskProgressUpdateRecord> findPlannerQueue(UUID projectId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + """
                 FROM task_progress_updates
                 WHERE project_id = :projectId AND planner_review_state = 'needs_planner_review'
                 ORDER BY submitted_at
                """,
                new MapSqlParameterSource("projectId", projectId), this::map);
    }

    @Override
    public void markSuperseded(UUID progressUpdateId) {
        jdbcTemplate.update(
                """
                UPDATE task_progress_updates
                SET progress_review_state = 'superseded',
                    export_state = 'superseded'
                WHERE id = :id
                """,
                new MapSqlParameterSource("id", progressUpdateId));
    }

    @Override
    public boolean isSummaryTask(UUID projectId, UUID importedTaskId) {
        Boolean summary = jdbcTemplate.query(
                "SELECT is_summary FROM imported_tasks WHERE id = :id AND project_id = :projectId",
                new MapSqlParameterSource().addValue("id", importedTaskId).addValue("projectId", projectId),
                (rs, rowNum) -> rs.getBoolean("is_summary")).stream().findFirst().orElse(null);

        if (summary == null) {
            throw new IllegalArgumentException("Imported task not found on this project: " + importedTaskId);
        }
        return summary;
    }

    @Override
    public void upsertExecutionState(
            UUID projectId,
            UUID importedTaskId,
            TaskExecutionState state,
            UUID changedByUserId,
            String reason
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO task_execution_states (
                    project_id, imported_task_id, execution_state, state_changed_by_user_id, state_reason
                )
                VALUES (
                    :projectId, :importedTaskId, CAST(:state AS task_execution_state), :changedBy, :reason
                )
                ON CONFLICT (imported_task_id) DO UPDATE
                SET execution_state = EXCLUDED.execution_state,
                    state_changed_at = now(),
                    state_changed_by_user_id = EXCLUDED.state_changed_by_user_id,
                    state_reason = EXCLUDED.state_reason,
                    updated_at = now()
                """,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("importedTaskId", importedTaskId)
                        .addValue("state", state.databaseValue())
                        .addValue("changedBy", changedByUserId)
                        .addValue("reason", reason));
    }

    @Override
    public Optional<TaskExecutionState> findExecutionState(UUID importedTaskId) {
        return jdbcTemplate.query(
                "SELECT execution_state FROM task_execution_states WHERE imported_task_id = :id",
                new MapSqlParameterSource("id", importedTaskId),
                (rs, rowNum) -> TaskExecutionState.fromDatabaseValue(rs.getString("execution_state")))
                .stream().findFirst();
    }

    private TaskProgressUpdateRecord map(ResultSet rs, int rowNum) throws SQLException {
        return new TaskProgressUpdateRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("project_snapshot_id", UUID.class),
                rs.getObject("imported_task_id", UUID.class),
                TaskExecutionState.fromDatabaseValue(rs.getString("execution_state")),
                rs.getBigDecimal("percent_complete"),
                rs.getObject("actual_start", java.time.OffsetDateTime.class),
                rs.getObject("actual_finish", java.time.OffsetDateTime.class),
                rs.getBigDecimal("physical_percent_complete"),
                rs.getString("comment"),
                rs.getObject("submitted_by_user_id", UUID.class),
                ProgressReviewState.fromDatabaseValue(rs.getString("progress_review_state")),
                PlannerReviewState.fromDatabaseValue(rs.getString("planner_review_state")),
                ProgressExportState.fromDatabaseValue(rs.getString("export_state")),
                rs.getObject("supersedes_progress_update_id", UUID.class));
    }
}
