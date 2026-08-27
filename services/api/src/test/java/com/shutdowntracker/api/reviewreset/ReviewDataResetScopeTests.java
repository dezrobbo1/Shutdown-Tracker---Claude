package com.shutdowntracker.api.reviewreset;

import static org.assertj.core.api.Assertions.assertThat;

import com.shutdowntracker.api.support.AbstractDatabaseTest;
import com.shutdowntracker.api.support.EmbeddedDatabase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * That the reset's blast radius is exactly what it says, and stays that way.
 *
 * <p>These assertions are the reason the wipe list is written out by hand instead of derived. A
 * derived list is always correct and always silent: add a migration and it starts truncating a new
 * table without anybody deciding that it should. A written list plus these tests is wrong loudly —
 * the next migration fails here until somebody puts the table on one side or the other.
 */
class ReviewDataResetScopeTests extends AbstractDatabaseTest {

    @Test
    @DisplayName("every table in the database is on exactly one of the two lists")
    void coversEveryTableExactlyOnce() {
        List<String> known = new ArrayList<>(ReviewDataResetScope.WIPE);
        known.addAll(ReviewDataResetScope.KEEP);

        assertThat(known)
                .describedAs("a table on both lists would be truncated and claimed as kept")
                .doesNotHaveDuplicates();

        // schema_migration_log is created by scripts/db/apply-migrations.sh, not by a migration, so
        // it is absent from a freshly migrated database. It is on the KEEP list because losing it on
        // a real deployment makes check-schema-drift.sh fail and redeploy.sh refuse.
        List<String> expected = new ArrayList<>(EmbeddedDatabase.tableNames());
        expected.add("schema_migration_log");

        assertThat(known)
                .describedAs("a new migration must be placed on the wipe or the keep list")
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    @DisplayName("no kept table points into the wiped set")
    void noKeptTableDependsOnAWipedOne() {
        // This is what caught reporting_policy_versions, which reads like project configuration and
        // is not: it carries a foreign key to critical_work_packages. Kept, the un-cascaded truncate
        // would refuse and the whole reset would fail.
        List<Map<String, Object>> offenders = jdbcTemplate().queryForList(
                """
                SELECT c.conrelid::regclass::text AS kept_table,
                       c.confrelid::regclass::text AS wiped_table
                  FROM pg_constraint c
                 WHERE c.contype = 'f'
                   AND c.conrelid::regclass::text = ANY (?)
                   AND c.confrelid::regclass::text = ANY (?)
                """,
                ReviewDataResetScope.KEEP.toArray(String[]::new),
                ReviewDataResetScope.WIPE.toArray(String[]::new));

        assertThat(offenders)
                .describedAs("a kept table referencing a wiped one must move to the wipe list")
                .isEmpty();
    }

    @Test
    @DisplayName("the statement truncates without cascading")
    void doesNotCascade() {
        // Cascade would truncate anything referencing these tables, which is how a wipe list grows
        // without anybody editing it. Without it, PostgreSQL refuses and names what it will not do.
        assertThat(ReviewDataResetScope.truncateStatement())
                .startsWith("TRUNCATE TABLE ")
                .endsWith(" RESTART IDENTITY")
                .doesNotContain("CASCADE");
    }

    @Test
    @DisplayName("the people and the project are never wiped")
    void keepsWhatTheDeploymentNeeds() {
        // redeploy.sh resolves the seeded super user out of these tables and refuses to build
        // without it, so wiping them would leave the deployment unable to deploy itself.
        assertThat(ReviewDataResetScope.KEEP)
                .contains("users", "project_memberships", "projects", "schema_migration_log");
        assertThat(ReviewDataResetScope.WIPE)
                .doesNotContain("users", "project_memberships", "projects", "schema_migration_log");
    }
}
