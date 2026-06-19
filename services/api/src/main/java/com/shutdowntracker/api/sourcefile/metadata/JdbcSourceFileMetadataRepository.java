package com.shutdowntracker.api.sourcefile.metadata;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class JdbcSourceFileMetadataRepository implements SourceFileMetadataRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcSourceFileMetadataRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SourceFileMetadataRecord create(SourceFileMetadataCreateRequest request) {
        String sql = """
                INSERT INTO source_files (
                    project_id,
                    original_filename,
                    file_kind,
                    storage_uri,
                    content_hash,
                    size_bytes,
                    metadata
                )
                VALUES (
                    :projectId,
                    :originalFilename,
                    :fileKind,
                    :storageUri,
                    :contentHash,
                    :sizeBytes,
                    '{}'::jsonb
                )
                RETURNING id, project_id, original_filename, file_kind, storage_uri, content_hash, size_bytes
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("projectId", request.projectId())
                .addValue("originalFilename", request.originalFilename())
                .addValue("fileKind", request.fileKind().databaseValue())
                .addValue("storageUri", request.storageUri())
                .addValue("contentHash", request.contentHash())
                .addValue("sizeBytes", request.sizeBytes());

        return jdbcTemplate.queryForObject(sql, parameters, (rs, rowNum) -> new SourceFileMetadataRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("original_filename"),
                SourceFileKind.fromDatabaseValue(rs.getString("file_kind")),
                rs.getString("storage_uri"),
                rs.getString("content_hash"),
                rs.getLong("size_bytes")
        ));
    }
}
