package com.shutdowntracker.api.exportpreview.handoff;

import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class JdbcAcceptedSourceFileRepository implements AcceptedSourceFileRepository {

    /**
     * Every hop here is an existing non-null foreign key, so resolving the source needs no schema
     * change and cannot disturb the export-integrity constraints.
     */
    private static final String SELECT_BY_SNAPSHOT = """
            SELECT sf.id,
                   sf.storage_uri,
                   sf.content_hash,
                   sf.file_kind
            FROM project_snapshots ps
            JOIN import_batches ib ON ib.id = ps.import_batch_id
            JOIN source_files sf ON sf.id = ib.source_file_id
            WHERE ps.id = :projectSnapshotId
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcAcceptedSourceFileRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<AcceptedSourceFile> findByProjectSnapshotId(UUID projectSnapshotId) {
        return jdbcTemplate.query(
                SELECT_BY_SNAPSHOT,
                new MapSqlParameterSource().addValue("projectSnapshotId", projectSnapshotId),
                (resultSet, rowNumber) -> new AcceptedSourceFile(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("storage_uri"),
                        resultSet.getString("content_hash"),
                        resultSet.getString("file_kind")
                )
        ).stream().findFirst();
    }
}
