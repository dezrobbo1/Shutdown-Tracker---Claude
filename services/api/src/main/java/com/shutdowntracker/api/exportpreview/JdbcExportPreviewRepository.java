package com.shutdowntracker.api.exportpreview;

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
public class JdbcExportPreviewRepository implements ExportPreviewRepository {

    private static final String BATCH_SELECT = """
            SELECT eb.id,
                   eb.project_id,
                   eb.project_snapshot_id,
                   eb.status,
                   eb.preview_created_at,
                   eb.approved_at,
                   eb.approved_by_user_id,
                   eb.generated_at,
                   eb.generated_by_user_id,
                   eb.verified_at,
                   eb.verified_by_user_id,
                   eb.export_file_uri,
                   eb.export_file_hash,
                   eb.failure_reason,
                   CAST(COUNT(ebl.id) AS int) AS line_count,
                   CAST(COUNT(ebl.id) FILTER (WHERE ebl.is_export_eligible) AS int) AS eligible_line_count,
                   CAST(COUNT(ebl.id) FILTER (WHERE NOT ebl.is_export_eligible) AS int) AS ineligible_line_count
            FROM export_batches eb
            LEFT JOIN export_batch_lines ebl ON ebl.export_batch_id = eb.id
            """;

    private static final String LINE_SELECT = """
            SELECT ebl.id,
                   ebl.export_batch_id,
                   ebl.project_id,
                   ebl.project_snapshot_id,
                   ebl.imported_task_id,
                   it.external_uid AS imported_task_external_uid,
                   it.external_id AS imported_task_external_id,
                   it.name AS imported_task_name,
                   ebl.source_entity_type,
                   ebl.source_entity_id,
                   approval.approval_state,
                   ebl.field_name,
                   ebl.old_value,
                   ebl.new_value,
                   ebl.source_actor_user_id,
                   ebl.source_timestamp,
                   ebl.reason,
                   ebl.is_leaf_task,
                   ebl.is_export_eligible
            FROM export_batch_lines ebl
            JOIN imported_tasks it ON it.id = ebl.imported_task_id
            LEFT JOIN LATERAL (
                SELECT approval_state
                FROM approval_records ar
                WHERE ar.project_id = ebl.project_id
                  AND ar.source_entity_type = ebl.source_entity_type
                  AND ar.source_entity_id = ebl.source_entity_id
                ORDER BY ar.reviewed_at DESC NULLS LAST, ar.created_at DESC, ar.id DESC
                LIMIT 1
            ) approval ON true
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcExportPreviewRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExportPreviewBatchRecord createDraftPreview(
            UUID projectId,
            UUID projectSnapshotId,
            Map<String, Object> metadata
    ) {
        String sql = """
                INSERT INTO export_batches (
                    project_id,
                    project_snapshot_id,
                    status,
                    preview_created_at,
                    metadata
                )
                SELECT :projectId,
                       :projectSnapshotId,
                       CAST(:status AS export_batch_state),
                       now(),
                       CAST(:metadata AS jsonb)
                WHERE EXISTS (
                    SELECT 1
                    FROM project_snapshots
                    WHERE id = :projectSnapshotId
                      AND project_id = :projectId
                      AND status = 'accepted'
                )
                RETURNING id
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("projectSnapshotId", projectSnapshotId)
                .addValue("status", ExportBatchState.DRAFT_PREVIEW.databaseValue())
                .addValue("metadata", toJson(metadata));

        List<UUID> ids = jdbcTemplate.query(sql, parameters, (rs, rowNum) -> rs.getObject("id", UUID.class));
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Export preview requires an accepted project snapshot.");
        }

        return findBatch(projectId, ids.getFirst())
                .orElseThrow(() -> new IllegalStateException("Created export preview batch not found."));
    }

    @Override
    public Optional<ExportPreviewBatchRecord> findBatch(UUID projectId, UUID exportBatchId) {
        String sql = BATCH_SELECT + """
                WHERE eb.project_id = :projectId
                  AND eb.id = :exportBatchId
                GROUP BY eb.id,
                         eb.project_id,
                         eb.project_snapshot_id,
                         eb.status,
                         eb.preview_created_at,
                         eb.approved_at,
                         eb.approved_by_user_id,
                         eb.generated_at,
                         eb.generated_by_user_id,
                         eb.verified_at,
                         eb.verified_by_user_id,
                         eb.export_file_uri,
                         eb.export_file_hash,
                         eb.failure_reason
                """;

        return jdbcTemplate.query(
                sql,
                Map.of("projectId", projectId, "exportBatchId", exportBatchId),
                this::mapBatch
        ).stream().findFirst();
    }

    @Override
    public Optional<ExportPreviewBatchRecord> approveBatch(
            UUID projectId,
            UUID exportBatchId,
            UUID approvedByUserId,
            Map<String, Object> metadata
    ) {
        String sql = """
                UPDATE export_batches
                SET status = CAST(:status AS export_batch_state),
                    approved_at = now(),
                    approved_by_user_id = :approvedByUserId,
                    metadata = metadata || CAST(:metadata AS jsonb)
                WHERE project_id = :projectId
                  AND id = :exportBatchId
                  AND status = 'draft_preview'
                RETURNING id
                """;

        return updateBatchState(
                sql,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("exportBatchId", exportBatchId)
                        .addValue("approvedByUserId", approvedByUserId)
                        .addValue("status", ExportBatchState.APPROVED.databaseValue())
                        .addValue("metadata", toJson(metadata)),
                projectId,
                exportBatchId
        );
    }

    @Override
    public Optional<ExportPreviewBatchRecord> rejectBatch(
            UUID projectId,
            UUID exportBatchId,
            Map<String, Object> metadata
    ) {
        String sql = """
                UPDATE export_batches
                SET status = CAST(:status AS export_batch_state),
                    metadata = metadata || CAST(:metadata AS jsonb)
                WHERE project_id = :projectId
                  AND id = :exportBatchId
                  AND status = 'draft_preview'
                RETURNING id
                """;

        return updateBatchState(
                sql,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("exportBatchId", exportBatchId)
                        .addValue("status", ExportBatchState.REJECTED.databaseValue())
                        .addValue("metadata", toJson(metadata)),
                projectId,
                exportBatchId
        );
    }

    @Override
    public Optional<ExportPreviewBatchRecord> markBatchGenerated(
            UUID projectId,
            UUID exportBatchId,
            String exportFileUri,
            String exportFileHash,
            UUID generatedByUserId,
            Map<String, Object> metadata
    ) {
        String sql = """
                UPDATE export_batches
                SET status = CAST(:status AS export_batch_state),
                    generated_at = now(),
                    generated_by_user_id = :generatedByUserId,
                    export_file_uri = :exportFileUri,
                    export_file_hash = :exportFileHash,
                    metadata = metadata || CAST(:metadata AS jsonb)
                WHERE project_id = :projectId
                  AND id = :exportBatchId
                  AND status = 'approved'
                RETURNING id
                """;

        return updateBatchState(
                sql,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("exportBatchId", exportBatchId)
                        .addValue("exportFileUri", exportFileUri)
                        .addValue("exportFileHash", exportFileHash)
                        .addValue("generatedByUserId", generatedByUserId)
                        .addValue("status", ExportBatchState.GENERATED.databaseValue())
                        .addValue("metadata", toJson(metadata)),
                projectId,
                exportBatchId
        );
    }

    @Override
    public Optional<ExportPreviewBatchRecord> markBatchOpenedInMicrosoftProject(
            UUID projectId,
            UUID exportBatchId,
            Map<String, Object> metadata
    ) {
        String sql = """
                UPDATE export_batches
                SET status = CAST(:status AS export_batch_state),
                    metadata = metadata || CAST(:metadata AS jsonb)
                WHERE project_id = :projectId
                  AND id = :exportBatchId
                  AND status = 'generated'
                RETURNING id
                """;

        return updateBatchState(
                sql,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("exportBatchId", exportBatchId)
                        .addValue("status", ExportBatchState.OPENED_IN_MICROSOFT_PROJECT.databaseValue())
                        .addValue("metadata", toJson(metadata)),
                projectId,
                exportBatchId
        );
    }

    @Override
    public Optional<ExportPreviewBatchRecord> markBatchVerified(
            UUID projectId,
            UUID exportBatchId,
            UUID verifiedByUserId,
            Map<String, Object> metadata
    ) {
        String sql = """
                UPDATE export_batches
                SET status = CAST(:status AS export_batch_state),
                    verified_at = now(),
                    verified_by_user_id = :verifiedByUserId,
                    metadata = metadata || CAST(:metadata AS jsonb)
                WHERE project_id = :projectId
                  AND id = :exportBatchId
                  AND status = 'opened_in_microsoft_project'
                RETURNING id
                """;

        return updateBatchState(
                sql,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("exportBatchId", exportBatchId)
                        .addValue("verifiedByUserId", verifiedByUserId)
                        .addValue("status", ExportBatchState.VERIFIED.databaseValue())
                        .addValue("metadata", toJson(metadata)),
                projectId,
                exportBatchId
        );
    }

    @Override
    public Optional<ExportPreviewBatchRecord> markBatchFailed(
            UUID projectId,
            UUID exportBatchId,
            String failureReason,
            Map<String, Object> metadata
    ) {
        String sql = """
                UPDATE export_batches
                SET status = CAST(:status AS export_batch_state),
                    failure_reason = :failureReason,
                    metadata = metadata || CAST(:metadata AS jsonb)
                WHERE project_id = :projectId
                  AND id = :exportBatchId
                  AND status NOT IN ('failed', 'superseded')
                RETURNING id
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("exportBatchId", exportBatchId)
                .addValue("status", ExportBatchState.FAILED.databaseValue())
                .addValue("failureReason", failureReason)
                .addValue("metadata", toJson(metadata));

        List<UUID> ids = jdbcTemplate.query(sql, parameters, (rs, rowNum) -> rs.getObject("id", UUID.class));
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return findBatch(projectId, ids.getFirst());
    }

    @Override
    public Optional<ExportPreviewTaskContext> findTaskContext(
            UUID projectId,
            UUID projectSnapshotId,
            UUID importedTaskId
    ) {
        String sql = """
                SELECT id,
                       project_id,
                       project_snapshot_id,
                       external_uid,
                       external_id,
                       name,
                       is_summary,
                       percent_complete,
                       physical_percent_complete,
                       actual_start,
                       actual_finish
                FROM imported_tasks
                WHERE project_id = :projectId
                  AND project_snapshot_id = :projectSnapshotId
                  AND id = :importedTaskId
                """;

        return jdbcTemplate.query(
                sql,
                Map.of(
                        "projectId", projectId,
                        "projectSnapshotId", projectSnapshotId,
                        "importedTaskId", importedTaskId
                ),
                (rs, rowNum) -> new ExportPreviewTaskContext(
                        rs.getObject("id", UUID.class),
                        rs.getObject("project_id", UUID.class),
                        rs.getObject("project_snapshot_id", UUID.class),
                        rs.getString("external_uid"),
                        rs.getString("external_id"),
                        rs.getString("name"),
                        rs.getBoolean("is_summary"),
                        rs.getBigDecimal("percent_complete"),
                        rs.getBigDecimal("physical_percent_complete"),
                        rs.getObject("actual_start", OffsetDateTime.class),
                        rs.getObject("actual_finish", OffsetDateTime.class)
                )
        ).stream().findFirst();
    }

    @Override
    public Optional<ApprovalState> findLatestApprovalState(
            UUID projectId,
            String sourceEntityType,
            UUID sourceEntityId
    ) {
        String sql = """
                SELECT approval_state
                FROM approval_records
                WHERE project_id = :projectId
                  AND source_entity_type = :sourceEntityType
                  AND source_entity_id = :sourceEntityId
                ORDER BY reviewed_at DESC NULLS LAST, created_at DESC, id DESC
                LIMIT 1
                """;

        return jdbcTemplate.query(
                sql,
                Map.of(
                        "projectId", projectId,
                        "sourceEntityType", sourceEntityType,
                        "sourceEntityId", sourceEntityId
                ),
                (rs, rowNum) -> ApprovalState.fromDatabaseValue(rs.getString("approval_state"))
        ).stream().findFirst();
    }

    @Override
    public ExportPreviewLineRecord createLine(
            UUID projectId,
            UUID projectSnapshotId,
            UUID exportBatchId,
            ExportPreviewMaterializedLine line
    ) {
        String sql = """
                INSERT INTO export_batch_lines (
                    export_batch_id,
                    project_id,
                    project_snapshot_id,
                    imported_task_id,
                    source_entity_type,
                    source_entity_id,
                    field_name,
                    old_value,
                    new_value,
                    source_actor_user_id,
                    source_timestamp,
                    reason,
                    is_leaf_task,
                    is_export_eligible,
                    metadata
                )
                VALUES (
                    :exportBatchId,
                    :projectId,
                    :projectSnapshotId,
                    :importedTaskId,
                    :sourceEntityType,
                    :sourceEntityId,
                    :fieldName,
                    :oldValue,
                    :newValue,
                    :sourceActorUserId,
                    :sourceTimestamp,
                    :reason,
                    :leafTask,
                    :exportEligible,
                    CAST(:metadata AS jsonb)
                )
                RETURNING id
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("exportBatchId", exportBatchId)
                .addValue("projectId", projectId)
                .addValue("projectSnapshotId", projectSnapshotId)
                .addValue("importedTaskId", line.importedTaskId())
                .addValue("sourceEntityType", line.sourceEntityType())
                .addValue("sourceEntityId", line.sourceEntityId())
                .addValue("fieldName", line.fieldName())
                .addValue("oldValue", line.oldValue())
                .addValue("newValue", line.newValue())
                .addValue("sourceActorUserId", line.sourceActorUserId())
                .addValue("sourceTimestamp", line.sourceTimestamp())
                .addValue("reason", line.reason())
                .addValue("leafTask", line.leafTask())
                .addValue("exportEligible", line.exportEligible())
                .addValue("metadata", toJson(line.metadata()));

        UUID lineId = jdbcTemplate.queryForObject(sql, parameters, UUID.class);
        return listLines(projectId, exportBatchId).stream()
                .filter(record -> record.id().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Created export preview line not found."));
    }

    @Override
    public List<ExportPreviewLineRecord> listLines(UUID projectId, UUID exportBatchId) {
        String sql = LINE_SELECT + """
                WHERE ebl.project_id = :projectId
                  AND ebl.export_batch_id = :exportBatchId
                ORDER BY ebl.created_at, ebl.id
                """;

        return jdbcTemplate.query(
                sql,
                Map.of("projectId", projectId, "exportBatchId", exportBatchId),
                this::mapLine
        );
    }

    private ExportPreviewBatchRecord mapBatch(ResultSet rs, int rowNum) throws SQLException {
        return new ExportPreviewBatchRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("project_snapshot_id", UUID.class),
                ExportBatchState.fromDatabaseValue(rs.getString("status")),
                rs.getObject("preview_created_at", OffsetDateTime.class),
                rs.getObject("approved_at", OffsetDateTime.class),
                rs.getObject("approved_by_user_id", UUID.class),
                rs.getObject("generated_at", OffsetDateTime.class),
                rs.getObject("generated_by_user_id", UUID.class),
                rs.getObject("verified_at", OffsetDateTime.class),
                rs.getObject("verified_by_user_id", UUID.class),
                rs.getString("export_file_uri"),
                rs.getString("export_file_hash"),
                rs.getString("failure_reason"),
                rs.getInt("line_count"),
                rs.getInt("eligible_line_count"),
                rs.getInt("ineligible_line_count")
        );
    }

    private ExportPreviewLineRecord mapLine(ResultSet rs, int rowNum) throws SQLException {
        String approvalState = rs.getString("approval_state");
        return new ExportPreviewLineRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("export_batch_id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("project_snapshot_id", UUID.class),
                rs.getObject("imported_task_id", UUID.class),
                rs.getString("imported_task_external_uid"),
                rs.getString("imported_task_external_id"),
                rs.getString("imported_task_name"),
                rs.getString("source_entity_type"),
                rs.getObject("source_entity_id", UUID.class),
                approvalState == null ? null : ApprovalState.fromDatabaseValue(approvalState),
                rs.getString("field_name"),
                rs.getString("old_value"),
                rs.getString("new_value"),
                rs.getObject("source_actor_user_id", UUID.class),
                rs.getObject("source_timestamp", OffsetDateTime.class),
                rs.getString("reason"),
                rs.getBoolean("is_leaf_task"),
                rs.getBoolean("is_export_eligible")
        );
    }

    private Optional<ExportPreviewBatchRecord> updateBatchState(
            String sql,
            MapSqlParameterSource parameters,
            UUID projectId,
            UUID exportBatchId
    ) {
        List<UUID> ids = jdbcTemplate.query(sql, parameters, (rs, rowNum) -> rs.getObject("id", UUID.class));
        if (ids.isEmpty()) {
            return Optional.empty();
        }

        return findBatch(projectId, exportBatchId);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize export preview metadata.", exception);
        }
    }
}
