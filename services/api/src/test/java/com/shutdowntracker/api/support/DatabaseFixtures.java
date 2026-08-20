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

    /**
     * Creates an active user. Attribution columns carry foreign keys to {@code users}, so
     * tests that record who did something need a real row to point at.
     */
    public UUID createUser(String email, String displayName) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO users (email, display_name, status)
                VALUES (?, ?, CAST('active' AS user_status))
                RETURNING id
                """,
                UUID.class,
                email,
                displayName);
    }

    public UUID grantMembership(UUID projectId, UUID userId, String role) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO project_memberships (project_id, user_id, role)
                VALUES (?, ?, CAST(? AS project_role))
                RETURNING id
                """,
                UUID.class,
                projectId,
                userId,
                role);
    }

    /**
     * Accepts the newest snapshot, which an export batch requires.
     *
     * <p>Separate from {@link #createExportBatchShell(UUID)} rather than folded into it, because
     * acceptance is a real reviewed decision: a fixture that quietly accepted a schedule on a
     * caller's behalf would hide the one precondition the export policy cares most about.
     */
    public UUID acceptNewestSnapshot(UUID projectId) {
        return jdbcTemplate.queryForObject(
                """
                UPDATE project_snapshots
                SET status = 'accepted', accepted_at = now()
                WHERE id = (
                    SELECT id FROM project_snapshots
                    WHERE project_id = ?
                    ORDER BY snapshot_version DESC
                    LIMIT 1
                )
                RETURNING id
                """,
                UUID.class,
                projectId);
    }

    /**
     * A minimal current-policy export batch, for tests that need a batch id to point at.
     *
     * <p>Inserted rather than driven through the export services because those tests are about
     * something else. It still has to satisfy the V007 policy trigger, which is why it names
     * policy version 1 and begins as an unsealed draft preview: a batch cannot be conjured into a
     * later state, and a fixture that tried would be testing a state the application cannot reach.
     */
    public UUID createExportBatchShell(UUID projectId) {
        UUID snapshotId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM project_snapshots
                WHERE project_id = ? AND status = 'accepted'
                ORDER BY snapshot_version DESC
                LIMIT 1
                """,
                UUID.class,
                projectId);

        return jdbcTemplate.queryForObject(
                """
                INSERT INTO export_batches (
                    project_id, project_snapshot_id, status, integrity_policy_version, line_set_sealed
                )
                VALUES (?, ?, CAST('draft_preview' AS export_batch_state), 1, false)
                RETURNING id
                """,
                UUID.class,
                projectId,
                snapshotId);
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
