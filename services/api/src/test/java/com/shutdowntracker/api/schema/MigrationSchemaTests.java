package com.shutdowntracker.api.schema;

import java.util.List;
import com.shutdowntracker.api.support.AbstractDatabaseTest;
import com.shutdowntracker.api.support.EmbeddedDatabase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Flyway migrations in {@code infra/migrations} apply cleanly to a real
 * PostgreSQL server and produce the schema the repositories expect.
 *
 * <p>Until this existed the migrations were only checked by a shell script that inspected
 * the SQL text, so nothing confirmed the schema could actually be created.
 *
 * <p>The expected list is read from {@code infra/migrations} rather than written down here. It was
 * written down, and it fell a migration behind: the assertion then failed because a new migration
 * existed at all, which is the opposite of what it is for.
 */
class MigrationSchemaTests extends AbstractDatabaseTest {

    @Test
    void everyMigrationApplies() {
        List<String> applied = jdbcTemplate().queryForList(
                "SELECT script FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);

        assertThat(applied)
                .describedAs("every migration should apply in order")
                .containsExactlyElementsOf(EmbeddedDatabase.migrationFileNames());
    }

    @Test
    void coreTablesExist() {
        assertThat(EmbeddedDatabase.tableNames())
                .contains(
                        "projects",
                        "project_snapshots",
                        "source_files",
                        "import_batches",
                        "imported_tasks",
                        "imported_resources",
                        "imported_assignments",
                        "imported_extended_attributes",
                        "audit_events",
                        "approval_records",
                        "export_batches",
                        "export_batch_lines",
                        "task_lineage_links",
                        "critical_watchlists",
                        "users",
                        "project_memberships",
                        "candidate_schedule_runs");
    }

    @Test
    void attributionColumnsReferenceRealUsers() {
        // Before V008 every *_by_user_id was a free UUID, so the audit trail could name a
        // user that did not exist.
        List<String> constrained = jdbcTemplate().queryForList(
                """
                SELECT tc.table_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.constraint_column_usage ccu
                  ON tc.constraint_name = ccu.constraint_name
                WHERE tc.constraint_type = 'FOREIGN KEY'
                  AND ccu.table_name = 'users'
                """,
                String.class);

        assertThat(constrained).contains("audit_events", "projects", "source_files", "export_batches");
    }

    @Test
    void migrationsAreIdempotentlyRecorded() {
        Integer failed = jdbcTemplate().queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class);

        assertThat(failed).isZero();
    }
}
