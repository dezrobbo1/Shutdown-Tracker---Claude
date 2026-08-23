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

    /**
     * Approved updates that may still travel, in submission order.
     *
     * <p>{@code export_state = 'eligible'} rather than {@code planner_review_state = 'planner_approved'}:
     * {@link #markSuperseded(UUID)} moves {@code export_state} to {@code superseded} and deliberately
     * leaves the planner's decision alone, since the planner did approve that value at the time.
     * Only {@code export_state} distinguishes a value that may still go from one that has been
     * replaced or blocked.
     *
     * <p>{@code export_batch_id IS NULL} keeps an update that a batch already carried out of the
     * list, so the same field change cannot be previewed twice. {@link #claimForExportBatch} writes
     * that column when a preview is created and {@link #releaseFromExportBatch} clears it if the
     * batch is rejected, so the clause now decides membership rather than describing an intention.
     */
    @Override
    public List<TaskProgressUpdateRecord> findExportQueue(UUID projectId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + """
                 FROM task_progress_updates
                 WHERE project_id = :projectId
                   AND export_state = 'eligible'
                   AND export_batch_id IS NULL
                 ORDER BY submitted_at
                """,
                new MapSqlParameterSource("projectId", projectId), this::map);
    }

    /**
     * The eligible progress lines of a batch, counted by the update they came from.
     *
     * <p>Derived from the sealed line set rather than from the candidate list the caller passed,
     * because the line set is what the batch actually carries. Only lines the batch can export
     * count: a line whose candidate approval is not current, whose task is not a leaf, or whose
     * field is off the whitelist is written with {@code is_export_eligible = false} and is left out
     * of the generated artifact, so an update present only through such a line never travels and
     * must not be claimed or later marked exported.
     */
    @Override
    public int countClaimableUpdates(UUID exportBatchId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT count(DISTINCT line.source_entity_id)
                FROM export_batch_lines line
                WHERE line.export_batch_id = :exportBatchId
                  AND line.source_entity_type = 'task_progress_update'
                  AND line.is_export_eligible
                """,
                new MapSqlParameterSource("exportBatchId", exportBatchId),
                Integer.class);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Guarded on {@code eligible} and an unset batch id rather than trusting the line set: a row
     * that has been superseded since the preview was assembled, or already claimed by another
     * batch, must not be swept into this one.
     *
     * <p>An update is claimed only when the batch's eligible lines cover <em>every</em> exportable
     * value on it. The binding is one row per batch, so a batch that took an update carrying two
     * reviewed values while exporting one of them would mark the row exported and strand the other
     * — the row has left the queue, and no later preview can claim it. Refusing is the reversible
     * direction: the planner adds the missing candidate and previews again, where an append-only
     * audit that recorded the wrong thing cannot be taken back. The caller turns the resulting
     * shortfall into a refusal.
     *
     * <p>{@code physical_percent_complete} is deliberately not among the fields checked. It is
     * reviewable and readable but off the MVP export whitelist, so it is not an exportable value
     * and requiring a line for it would refuse every preview.
     */
    @Override
    public int claimForExportBatch(UUID projectId, UUID exportBatchId) {
        return jdbcTemplate.update(
                """
                WITH carried AS (
                    SELECT DISTINCT line.source_entity_id, line.field_name
                    FROM export_batch_lines line
                    WHERE line.export_batch_id = :exportBatchId
                      AND line.source_entity_type = 'task_progress_update'
                      AND line.is_export_eligible
                )
                UPDATE task_progress_updates
                SET export_state = 'in_export_preview',
                    export_batch_id = :exportBatchId
                WHERE project_id = :projectId
                  AND export_state = 'eligible'
                  AND export_batch_id IS NULL
                  AND id IN (SELECT source_entity_id FROM carried)
                  AND NOT EXISTS (
                      SELECT 1
                      FROM (VALUES
                          ('percent_complete', percent_complete IS NOT NULL),
                          ('actual_start', actual_start IS NOT NULL),
                          ('actual_finish', actual_finish IS NOT NULL)
                      ) AS exportable(field_name, present)
                      WHERE exportable.present
                        AND NOT EXISTS (
                            SELECT 1 FROM carried
                            WHERE carried.source_entity_id = task_progress_updates.id
                              AND carried.field_name = exportable.field_name
                        )
                  )
                """,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("exportBatchId", exportBatchId));
    }

    /**
     * {@inheritDoc}
     *
     * <p>A row superseded while this batch held it is unlinked but not made eligible again. Its
     * value has been replaced and must never travel, so returning it to the queue would be wrong —
     * but leaving {@code export_batch_id} set would be a worse kind of wrong: the column answers
     * which batch carried this update, and a rejected batch carried nothing. Only rows still in
     * the preview go back to {@code eligible}.
     */
    @Override
    public int releaseFromExportBatch(UUID exportBatchId) {
        return jdbcTemplate.update(
                """
                UPDATE task_progress_updates
                SET export_state = CASE
                        WHEN export_state = 'in_export_preview' THEN 'eligible'
                        ELSE export_state
                    END,
                    export_batch_id = NULL
                WHERE export_batch_id = :exportBatchId
                  AND export_state IN ('in_export_preview', 'superseded')
                """,
                new MapSqlParameterSource("exportBatchId", exportBatchId));
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code export_batch_id} is deliberately left set. It is the answer to which batch carried
     * this update, and that question outlives the batch reaching its end.
     *
     * <p>Guarded on {@code in_export_preview} so a row superseded since the batch claimed it is
     * not marked as having travelled. Its value did reach the artifact, but the row's own state is
     * {@code superseded}, which is the fact the export queue reads.
     */
    @Override
    public int markExported(UUID exportBatchId) {
        return jdbcTemplate.update(
                """
                UPDATE task_progress_updates
                SET export_state = 'exported'
                WHERE export_batch_id = :exportBatchId
                  AND export_state = 'in_export_preview'
                """,
                new MapSqlParameterSource("exportBatchId", exportBatchId));
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
