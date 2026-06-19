package com.shutdowntracker.api.importreview;

import com.shutdowntracker.api.importbatch.ImportBatchStatus;
import com.shutdowntracker.api.importedproject.ImportedExtendedAttributeEntityType;
import com.shutdowntracker.api.importedproject.ProjectSnapshotStatus;
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
public class JdbcImportReviewRepository implements ImportReviewRepository {

    private static final String SNAPSHOT_SUMMARY_SELECT = """
            SELECT ps.id,
                   ps.project_id,
                   ps.import_batch_id,
                   ps.status,
                   ps.external_project_uid,
                   ps.external_project_name,
                   ps.project_status_date,
                   ps.snapshot_version,
                   ib.parser_name,
                   ib.parser_version,
                   ib.warning_count,
                   ib.error_count,
                   COALESCE(task_counts.task_count, 0) AS task_count,
                   COALESCE(task_counts.summary_task_count, 0) AS summary_task_count,
                   COALESCE(task_counts.leaf_task_count, 0) AS leaf_task_count,
                   COALESCE(resource_counts.resource_count, 0) AS resource_count,
                   COALESCE(assignment_counts.assignment_count, 0) AS assignment_count,
                   COALESCE(attribute_counts.extended_attribute_count, 0) AS extended_attribute_count
            FROM project_snapshots ps
            JOIN import_batches ib ON ib.id = ps.import_batch_id
            LEFT JOIN LATERAL (
                SELECT CAST(COUNT(*) AS int) AS task_count,
                       CAST(COUNT(*) FILTER (WHERE is_summary) AS int) AS summary_task_count,
                       CAST(COUNT(*) FILTER (WHERE NOT is_summary) AS int) AS leaf_task_count
                FROM imported_tasks it
                WHERE it.project_id = ps.project_id
                  AND it.project_snapshot_id = ps.id
            ) task_counts ON true
            LEFT JOIN LATERAL (
                SELECT CAST(COUNT(*) AS int) AS resource_count
                FROM imported_resources ir
                WHERE ir.project_id = ps.project_id
                  AND ir.project_snapshot_id = ps.id
            ) resource_counts ON true
            LEFT JOIN LATERAL (
                SELECT CAST(COUNT(*) AS int) AS assignment_count
                FROM imported_assignments ia
                WHERE ia.project_id = ps.project_id
                  AND ia.project_snapshot_id = ps.id
            ) assignment_counts ON true
            LEFT JOIN LATERAL (
                SELECT CAST(COUNT(*) AS int) AS extended_attribute_count
                FROM imported_extended_attributes iea
                WHERE iea.project_id = ps.project_id
                  AND iea.project_snapshot_id = ps.id
            ) attribute_counts ON true
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcImportReviewRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ImportReviewSnapshotSummary> listSnapshots(UUID projectId) {
        String sql = SNAPSHOT_SUMMARY_SELECT + """
                WHERE ps.project_id = :projectId
                ORDER BY ps.snapshot_version DESC, ps.created_at DESC
                """;

        return jdbcTemplate.query(sql, Map.of("projectId", projectId), this::mapSnapshotSummary);
    }

    @Override
    public Optional<ImportReviewSnapshotSummary> findSnapshot(UUID projectId, UUID snapshotId) {
        String sql = SNAPSHOT_SUMMARY_SELECT + """
                WHERE ps.project_id = :projectId
                  AND ps.id = :snapshotId
                """;

        return jdbcTemplate.query(
                sql,
                Map.of("projectId", projectId, "snapshotId", snapshotId),
                this::mapSnapshotSummary
        ).stream().findFirst();
    }

    @Override
    public List<ImportReviewTaskRow> listTasks(UUID projectId, UUID snapshotId) {
        String sql = """
                SELECT id,
                       external_uid,
                       external_id,
                       name,
                       wbs,
                       outline_number,
                       outline_level,
                       is_summary,
                       parent_external_uid,
                       parent_imported_task_id,
                       planned_start,
                       planned_finish,
                       actual_start,
                       actual_finish,
                       percent_complete,
                       physical_percent_complete,
                       notes
                FROM imported_tasks
                WHERE project_id = :projectId
                  AND project_snapshot_id = :snapshotId
                ORDER BY COALESCE(outline_number, ''), COALESCE(external_id, ''), created_at, id
                """;

        return jdbcTemplate.query(
                sql,
                Map.of("projectId", projectId, "snapshotId", snapshotId),
                this::mapTask
        );
    }

    @Override
    public List<ImportReviewResourceRow> listResources(UUID projectId, UUID snapshotId) {
        String sql = """
                SELECT id, external_uid, name, resource_type
                FROM imported_resources
                WHERE project_id = :projectId
                  AND project_snapshot_id = :snapshotId
                ORDER BY COALESCE(name, ''), COALESCE(external_uid, ''), id
                """;

        return jdbcTemplate.query(
                sql,
                Map.of("projectId", projectId, "snapshotId", snapshotId),
                (rs, rowNum) -> new ImportReviewResourceRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("external_uid"),
                        rs.getString("name"),
                        rs.getString("resource_type")
                )
        );
    }

    @Override
    public List<ImportReviewAssignmentRow> listAssignments(UUID projectId, UUID snapshotId) {
        String sql = """
                SELECT id,
                       external_uid,
                       task_external_uid,
                       resource_external_uid,
                       imported_task_id,
                       imported_resource_id
                FROM imported_assignments
                WHERE project_id = :projectId
                  AND project_snapshot_id = :snapshotId
                ORDER BY COALESCE(task_external_uid, ''), COALESCE(resource_external_uid, ''), id
                """;

        return jdbcTemplate.query(
                sql,
                Map.of("projectId", projectId, "snapshotId", snapshotId),
                (rs, rowNum) -> new ImportReviewAssignmentRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("external_uid"),
                        rs.getString("task_external_uid"),
                        rs.getString("resource_external_uid"),
                        rs.getObject("imported_task_id", UUID.class),
                        rs.getObject("imported_resource_id", UUID.class)
                )
        );
    }

    @Override
    public List<ImportReviewExtendedAttributeRow> listExtendedAttributes(UUID projectId, UUID snapshotId) {
        String sql = """
                SELECT id,
                       entity_type,
                       entity_external_uid,
                       field_id,
                       field_name,
                       alias,
                       value
                FROM imported_extended_attributes
                WHERE project_id = :projectId
                  AND project_snapshot_id = :snapshotId
                ORDER BY entity_type, COALESCE(entity_external_uid, ''), COALESCE(field_id, ''), id
                """;

        return jdbcTemplate.query(
                sql,
                Map.of("projectId", projectId, "snapshotId", snapshotId),
                (rs, rowNum) -> new ImportReviewExtendedAttributeRow(
                        rs.getObject("id", UUID.class),
                        ImportedExtendedAttributeEntityType.fromDatabaseValue(rs.getString("entity_type")),
                        rs.getString("entity_external_uid"),
                        rs.getString("field_id"),
                        rs.getString("field_name"),
                        rs.getString("alias"),
                        rs.getString("value")
                )
        );
    }

    @Override
    public Optional<ImportReviewSnapshotSummary> recordSnapshotDecision(
            UUID projectId,
            UUID snapshotId,
            ProjectSnapshotStatus status
    ) {
        String sql = """
                UPDATE project_snapshots
                SET status = CAST(:status AS project_snapshot_status),
                    accepted_at = CASE
                        WHEN :status = 'accepted' THEN now()
                        ELSE accepted_at
                    END
                WHERE project_id = :projectId
                  AND id = :snapshotId
                  AND status = 'parsed'
                RETURNING import_batch_id
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("snapshotId", snapshotId)
                .addValue("status", status.databaseValue());

        List<UUID> importBatchIds = jdbcTemplate.query(
                sql,
                parameters,
                (rs, rowNum) -> rs.getObject("import_batch_id", UUID.class)
        );

        if (importBatchIds.isEmpty()) {
            return Optional.empty();
        }

        if (status == ProjectSnapshotStatus.ACCEPTED) {
            markImportBatchAccepted(importBatchIds.getFirst());
        }

        return findSnapshot(projectId, snapshotId);
    }

    private void markImportBatchAccepted(UUID importBatchId) {
        String sql = """
                UPDATE import_batches
                SET status = CAST(:status AS import_batch_status),
                    completed_at = COALESCE(completed_at, now())
                WHERE id = :importBatchId
                """;

        jdbcTemplate.update(
                sql,
                Map.of(
                        "importBatchId", importBatchId,
                        "status", ImportBatchStatus.ACCEPTED.databaseValue()
                )
        );
    }

    private ImportReviewSnapshotSummary mapSnapshotSummary(ResultSet rs, int rowNum) throws SQLException {
        return new ImportReviewSnapshotSummary(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("import_batch_id", UUID.class),
                ProjectSnapshotStatus.fromDatabaseValue(rs.getString("status")),
                rs.getString("external_project_uid"),
                rs.getString("external_project_name"),
                rs.getObject("project_status_date", OffsetDateTime.class),
                rs.getInt("snapshot_version"),
                rs.getString("parser_name"),
                rs.getString("parser_version"),
                rs.getInt("warning_count"),
                rs.getInt("error_count"),
                rs.getInt("task_count"),
                rs.getInt("summary_task_count"),
                rs.getInt("leaf_task_count"),
                rs.getInt("resource_count"),
                rs.getInt("assignment_count"),
                rs.getInt("extended_attribute_count")
        );
    }

    private ImportReviewTaskRow mapTask(ResultSet rs, int rowNum) throws SQLException {
        return new ImportReviewTaskRow(
                rs.getObject("id", UUID.class),
                rs.getString("external_uid"),
                rs.getString("external_id"),
                rs.getString("name"),
                rs.getString("wbs"),
                rs.getString("outline_number"),
                (Integer) rs.getObject("outline_level"),
                rs.getBoolean("is_summary"),
                rs.getString("parent_external_uid"),
                rs.getObject("parent_imported_task_id", UUID.class),
                rs.getObject("planned_start", OffsetDateTime.class),
                rs.getObject("planned_finish", OffsetDateTime.class),
                rs.getObject("actual_start", OffsetDateTime.class),
                rs.getObject("actual_finish", OffsetDateTime.class),
                rs.getBigDecimal("percent_complete"),
                rs.getBigDecimal("physical_percent_complete"),
                rs.getString("notes")
        );
    }
}
