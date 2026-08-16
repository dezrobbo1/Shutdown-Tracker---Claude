package com.shutdowntracker.api.importbatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class JdbcImportBatchRepository implements ImportBatchRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcImportBatchRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ImportBatchRecord> findByProjectIdAndId(UUID projectId, UUID importBatchId) {
        String sql = """
                SELECT id, project_id, source_file_id, status, parser_name, parser_version, warning_count, error_count
                FROM import_batches
                WHERE project_id = :projectId
                  AND id = :id
                """;

        return jdbcTemplate.query(
                sql,
                Map.of("projectId", projectId, "id", importBatchId),
                this::mapRecord
        ).stream().findFirst();
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

    @Override
    public ImportBatchRecord recordParseFailure(UUID importBatchId, String failureReason) {
        // import_batches has no failure column; parse_summary is the documented home for parse metadata.
        String sql = """
                UPDATE import_batches
                SET status = CAST('failed' AS import_batch_status),
                    completed_at = COALESCE(completed_at, now()),
                    error_count = GREATEST(error_count, 1),
                    parse_summary = parse_summary || CAST(:failure AS jsonb)
                WHERE id = :id
                RETURNING id, project_id, source_file_id, status, parser_name, parser_version, warning_count, error_count
                """;

        return jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource()
                        .addValue("id", importBatchId)
                        .addValue("failure", toJson(Map.of("failureReason", failureReason))),
                this::mapRecord
        );
    }

    @Override
    public ImportBatchRecord recordParseSummary(ImportBatchParseSummaryUpdate update) {
        String sql = """
                UPDATE import_batches
                SET status = CAST(:status AS import_batch_status),
                    parser_name = :parserName,
                    parser_version = :parserVersion,
                    started_at = COALESCE(started_at, now()),
                    completed_at = COALESCE(completed_at, now()),
                    warning_count = :warningCount,
                    error_count = :errorCount,
                    parse_summary = CAST(:parseSummary AS jsonb)
                WHERE id = :id
                RETURNING id, project_id, source_file_id, status, parser_name, parser_version, warning_count, error_count
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", update.importBatchId())
                .addValue("status", ImportBatchStatus.PARSED.databaseValue())
                .addValue("parserName", update.parserName())
                .addValue("parserVersion", update.parserVersion())
                .addValue("warningCount", update.warningCount())
                .addValue("errorCount", update.errorCount())
                .addValue("parseSummary", toJson(update.parseSummary()));

        return jdbcTemplate.queryForObject(sql, parameters, this::mapRecord);
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

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize import batch failure metadata.", exception);
        }
    }

    private String toJson(ImportBatchParseSummary parseSummary) {
        try {
            return objectMapper.writeValueAsString(parseSummary);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize import batch parse summary.", exception);
        }
    }
}
