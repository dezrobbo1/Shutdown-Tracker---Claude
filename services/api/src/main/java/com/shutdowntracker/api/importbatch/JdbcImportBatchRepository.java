package com.shutdowntracker.api.importbatch;

import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class JdbcImportBatchRepository implements ImportBatchRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcImportBatchRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ImportBatchRecord create(ImportBatchCreateRequest request) {
        String sql = """
                INSERT INTO import_batches (
                    project_id,
                    source_file_id,
                    status,
                    parse_summary
                )
                VALUES (
                    :projectId,
                    :sourceFileId,
                    CAST(:status AS import_batch_status),
                    '{}'::jsonb
                )
                RETURNING id, project_id, source_file_id, status, parser_name, parser_version, warning_count, error_count
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("projectId", request.projectId())
                .addValue("sourceFileId", request.sourceFileId())
                .addValue("status", ImportBatchStatus.PENDING.databaseValue());

        return jdbcTemplate.queryForObject(sql, parameters, this::mapRecord);
    }

    @Override
    public ImportBatchRecord updateStatus(UUID importBatchId, ImportBatchStatus status) {
        String sql = """
                UPDATE import_batches
                SET status = CAST(:status AS import_batch_status),
                    started_at = CASE
                        WHEN :status = 'parsing' THEN COALESCE(started_at, now())
                        ELSE started_at
                    END,
                    completed_at = CASE
                        WHEN :status IN ('parsed', 'accepted', 'failed', 'superseded')
                            THEN COALESCE(completed_at, now())
                        ELSE completed_at
                    END
                WHERE id = :id
                RETURNING id, project_id, source_file_id, status, parser_name, parser_version, warning_count, error_count
                """;

        return jdbcTemplate.queryForObject(
                sql,
                Map.of("id", importBatchId, "status", status.databaseValue()),
                this::mapRecord
        );
    }

    private ImportBatchRecord mapRecord(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ImportBatchRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("source_file_id", UUID.class),
                ImportBatchStatus.fromDatabaseValue(rs.getString("status")),
                rs.getString("parser_name"),
                rs.getString("parser_version"),
                rs.getInt("warning_count"),
                rs.getInt("error_count")
        );
    }
}
