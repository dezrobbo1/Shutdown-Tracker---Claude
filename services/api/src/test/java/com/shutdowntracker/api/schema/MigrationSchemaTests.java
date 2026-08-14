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
 */
class MigrationSchemaTests extends AbstractDatabaseTest {

    @Test
    void everyMigrationApplies() {
        List<String> applied = jdbcTemplate().queryForList(
                "SELECT script FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);

        assertThat(applied)
                .describedAs("all six migrations should apply in order")
                .containsExactly(
                        "V001__baseline_extensions_and_enums.sql",
                        "V002__projects_snapshots_and_imports.sql",
                        "V003__imported_project_entities.sql",
                        "V004__audit_events.sql",
                        "V005__approval_and_export_batches.sql",
                        "V006__critical_watchlists_reporting.sql",
                        "V007__users_roles_and_memberships.sql",
                        "V008__task_execution_and_progress.sql",
                        "V009__problems_actions_evidence_handover.sql",
                        "V010__import_profiles_and_operational_categories.sql");
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
                        "project_memberships");
    }

    @Test
    void attributionColumnsReferenceRealUsers() {
        // Before V007 every *_by_user_id was a free UUID, so the audit trail could name a
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
