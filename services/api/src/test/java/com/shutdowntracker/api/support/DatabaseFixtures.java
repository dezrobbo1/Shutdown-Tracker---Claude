package com.shutdowntracker.api.support;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Creates the parent rows that repository tests need before they can insert anything.
 *
 * <p>The schema is heavily foreign-keyed: an imported task needs a snapshot, which needs
 * an import batch, which needs a source file and a project. These helpers build that
 * chain so individual tests can stay focused on the statement under test.
 */
public final class DatabaseFixtures {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseFixtures(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID createProject(String name) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO projects (name, description, status, timezone, metadata)
                VALUES (?, 'Created by a repository test.', 'active', 'UTC', '{}'::jsonb)
                RETURNING id
                """,
                UUID.class,
                name);
    }

    public UUID createSourceFile(UUID projectId) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO source_files (project_id, original_filename, file_kind, storage_uri)
                VALUES (?, 'fixture.xml', 'mspdi_xml', 'file:///fixtures/fixture.xml')
                RETURNING id
                """,
                UUID.class,
                projectId);
    }

    public UUID createImportBatch(UUID projectId, UUID sourceFileId) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO import_batches (project_id, source_file_id, status)
                VALUES (?, ?, CAST('parsed' AS import_batch_status))
                RETURNING id
                """,
                UUID.class,
                projectId,
                sourceFileId);
    }

    /** Builds project -> source file -> import batch in one call. */
    public ImportChain createImportChain(String projectName) {
        UUID projectId = createProject(projectName);
        UUID sourceFileId = createSourceFile(projectId);
        UUID importBatchId = createImportBatch(projectId, sourceFileId);
        return new ImportChain(projectId, sourceFileId, importBatchId);
    }

    public record ImportChain(UUID projectId, UUID sourceFileId, UUID importBatchId) {
    }
}
