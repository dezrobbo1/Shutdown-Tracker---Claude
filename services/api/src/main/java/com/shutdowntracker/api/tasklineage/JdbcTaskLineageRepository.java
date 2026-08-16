package com.shutdowntracker.api.tasklineage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class JdbcTaskLineageRepository implements TaskLineageRepository {

    private static final String LINEAGE_SELECT = """
            SELECT tll.id,
                   tll.project_id,
                   tll.previous_snapshot_id,
                   tll.current_snapshot_id,
                   tll.previous_imported_task_id,
                   previous_task.external_uid AS previous_task_external_uid,
                   previous_task.name AS previous_task_name,
                   tll.current_imported_task_id,
                   current_task.external_uid AS current_task_external_uid,
                   current_task.name AS current_task_name,
                   tll.match_method,
                   tll.match_confidence,
                   tll.review_state,
                   tll.reviewed_by_user_id,
                   tll.reviewed_at
            FROM task_lineage_links tll
            LEFT JOIN imported_tasks previous_task ON previous_task.id = tll.previous_imported_task_id
            LEFT JOIN imported_tasks current_task ON current_task.id = tll.current_imported_task_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcTaskLineageRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public TaskLineageRecord create(UUID projectId, TaskLineageCreateRequest request) {
        String sql = """
                INSERT INTO task_lineage_links (
                    project_id,
                    previous_snapshot_id,
                    current_snapshot_id,
                    previous_imported_task_id,
                    current_imported_task_id,
                    match_method,
                    match_confidence,
                    review_state,
                    metadata
                )
                SELECT :projectId,
                       :previousSnapshotId,
                       :currentSnapshotId,
                       :previousImportedTaskId,
                       :currentImportedTaskId,
                       :matchMethod,
                       :matchConfidence,
                       :reviewState,
                       CAST(:metadata AS jsonb)
                WHERE EXISTS (
                    SELECT 1
                    FROM project_snapshots
                    WHERE id = :previousSnapshotId
                      AND project_id = :projectId
                )
                  AND EXISTS (
                    SELECT 1
                    FROM project_snapshots
                    WHERE id = :currentSnapshotId
                      AND project_id = :projectId
                )
                  AND EXISTS (
                    SELECT 1
                    FROM imported_tasks
                    WHERE id = :previousImportedTaskId
                      AND project_id = :projectId
                      AND project_snapshot_id = :previousSnapshotId
                  )
                  AND EXISTS (
                    SELECT 1
                    FROM imported_tasks
                    WHERE id = :currentImportedTaskId
                      AND project_id = :projectId
                      AND project_snapshot_id = :currentSnapshotId
                  )
                RETURNING id
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("previousSnapshotId", request.previousSnapshotId())
                .addValue("currentSnapshotId", request.currentSnapshotId())
                .addValue("previousImportedTaskId", request.previousImportedTaskId())
                .addValue("currentImportedTaskId", request.currentImportedTaskId())
                .addValue("matchMethod", request.matchMethod())
                .addValue("matchConfidence", request.matchConfidence())
                .addValue("reviewState", TaskLineageReviewState.SUGGESTED.databaseValue())
                .addValue("metadata", toJson(request.metadata()));

        List<UUID> ids = jdbcTemplate.query(sql, parameters, (rs, rowNum) -> rs.getObject("id", UUID.class));
        if (ids.isEmpty()) {
            throw new IllegalArgumentException(
                    "Task lineage link must reference snapshots and tasks in the same project."
            );
        }

        UUID id = ids.getFirst();
        return find(projectId, id).orElseThrow(() -> new IllegalStateException("Created task lineage link not found."));
    }

    @Override
    public List<TaskLineageRecord> listBySnapshotPair(
            UUID projectId,
            UUID previousSnapshotId,
            UUID currentSnapshotId
    ) {
        String sql = LINEAGE_SELECT + """
                WHERE tll.project_id = :projectId
                  AND tll.previous_snapshot_id = :previousSnapshotId
                  AND tll.current_snapshot_id = :currentSnapshotId
                ORDER BY tll.review_state, tll.match_confidence DESC NULLS LAST, tll.created_at, tll.id
                """;

        return jdbcTemplate.query(
                sql,
                Map.of(
                        "projectId", projectId,
                        "previousSnapshotId", previousSnapshotId,
                        "currentSnapshotId", currentSnapshotId
                ),
                this::mapRecord
        );
    }

    @Override
    public Optional<TaskLineageRecord> find(UUID projectId, UUID lineageLinkId) {
        String sql = LINEAGE_SELECT + """
                WHERE tll.project_id = :projectId
                  AND tll.id = :lineageLinkId
                """;

        return jdbcTemplate.query(
                sql,
                Map.of("projectId", projectId, "lineageLinkId", lineageLinkId),
                this::mapRecord
        ).stream().findFirst();
    }

    @Override
    public Optional<TaskLineageRecord> updateReviewState(
            UUID projectId,
            UUID lineageLinkId,
            TaskLineageReviewState reviewState,
            UUID reviewedByUserId
    ) {
        String sql = """
                UPDATE task_lineage_links
                SET review_state = :reviewState,
                    reviewed_at = CASE
                        WHEN :reviewState IN ('accepted', 'rejected') THEN now()
                        ELSE reviewed_at
                    END,
                    reviewed_by_user_id = CASE
                        WHEN :reviewState IN ('accepted', 'rejected') THEN :reviewedByUserId
                        ELSE reviewed_by_user_id
                    END
                WHERE project_id = :projectId
                  AND id = :lineageLinkId
                  AND review_state = 'suggested'
                RETURNING id
                """;

        List<UUID> ids = jdbcTemplate.query(
                sql,
                Map.of(
                        "projectId", projectId,
                        "lineageLinkId", lineageLinkId,
                        "reviewState", reviewState.databaseValue(),
                        "reviewedByUserId", reviewedByUserId
                ),
                (rs, rowNum) -> rs.getObject("id", UUID.class)
        );

        if (ids.isEmpty()) {
            return Optional.empty();
        }

        return find(projectId, lineageLinkId);
    }

    private TaskLineageRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
        return new TaskLineageRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("previous_snapshot_id", UUID.class),
                rs.getObject("current_snapshot_id", UUID.class),
                rs.getObject("previous_imported_task_id", UUID.class),
                rs.getString("previous_task_external_uid"),
                rs.getString("previous_task_name"),
                rs.getObject("current_imported_task_id", UUID.class),
                rs.getString("current_task_external_uid"),
                rs.getString("current_task_name"),
                rs.getString("match_method"),
                rs.getBigDecimal("match_confidence"),
                TaskLineageReviewState.fromDatabaseValue(rs.getString("review_state")),
                rs.getObject("reviewed_by_user_id", UUID.class),
                rs.getObject("reviewed_at", OffsetDateTime.class)
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize task lineage metadata.", exception);
        }
    }
}
