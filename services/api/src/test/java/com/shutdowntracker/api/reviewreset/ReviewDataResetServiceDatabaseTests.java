package com.shutdowntracker.api.reviewreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.audit.JdbcAuditEventRecorder;
import com.shutdowntracker.api.support.AbstractDatabaseTest;
import com.shutdowntracker.api.support.DatabaseFixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * That the reset empties what it claims, keeps what the deployment needs, and is refused for
 * anything that is not a synthetic review project.
 */
class ReviewDataResetServiceDatabaseTests extends AbstractDatabaseTest {

    private DatabaseFixtures fixtures;
    private ReviewDataResetService service;
    private ReviewResetProjectGuard guard;

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbcTemplate());
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(jdbcTemplate());
        service = new ReviewDataResetService(
                jdbcTemplate(),
                new JdbcAuditEventRecorder(named, new ObjectMapper()));
        guard = new ReviewResetProjectGuard(named);

        // Created by apply-migrations.sh rather than by a migration, so it is absent here. It is on
        // the keep list because losing it makes check-schema-drift.sh fail and redeploy.sh refuse,
        // and that is the failure this test is really about.
        jdbcTemplate().execute("CREATE TABLE IF NOT EXISTS schema_migration_log (filename text PRIMARY KEY)");
        // EmbeddedDatabase.reset() caches its table list before this table exists, so it survives
        // between tests of its own accord — which is the behaviour under test anyway.
        jdbcTemplate().update(
                "INSERT INTO schema_migration_log (filename) VALUES ('V001__baseline.sql') "
                        + "ON CONFLICT (filename) DO NOTHING");
    }

    private UUID syntheticProject(String name) {
        return jdbcTemplate().queryForObject(
                """
                INSERT INTO projects (name, description, status, timezone, metadata)
                VALUES (?, 'Synthetic review project.', 'active', 'UTC',
                        '{"synthetic": true, "allowed_use": "review_bootstrap_only"}'::jsonb)
                RETURNING id
                """,
                UUID.class,
                name);
    }

    /**
     * Project -> source file -> import batch -> accepted snapshot, on a project we already have.
     *
     * <p>{@code DatabaseFixtures.createImportChain} creates its own project, and the export batch
     * shell needs an accepted snapshot on the <em>same</em> project as the reset.
     */
    private void importChainOn(UUID projectId) {
        UUID sourceFileId = fixtures.createSourceFile(projectId);
        UUID importBatchId = fixtures.createImportBatch(projectId, sourceFileId);
        jdbcTemplate().update(
                """
                INSERT INTO project_snapshots (project_id, import_batch_id, status, snapshot_version)
                VALUES (?, ?, CAST('parsed' AS project_snapshot_status), 1)
                """,
                projectId,
                importBatchId);
        fixtures.acceptNewestSnapshot(projectId);
    }

    private Actor seededAdmin(UUID projectId) {
        UUID userId = fixtures.createUser("admin@review.invalid", "Review Administrator");
        fixtures.grantMembership(projectId, userId, "admin");
        return new Actor(userId, "admin", "Review Administrator");
    }

    @Test
    @DisplayName("the append-only triggers refuse a delete, which is why this truncates")
    void truncatesPastTheAppendOnlyTriggers() {
        UUID projectId = syntheticProject("Synthetic Review Project");
        Actor admin = seededAdmin(projectId);
        importChainOn(projectId);
        fixtures.createExportBatchShell(projectId);

        assertThat(jdbcTemplate().queryForObject("SELECT count(*) FROM export_batches", Long.class))
                .isEqualTo(1L);

        // The claim the whole design rests on. If this ever stops raising, the reset could have been
        // written with DELETE and this test should be the thing that says so.
        assertThatThrownBy(() -> jdbcTemplate().update("DELETE FROM export_batches"))
                .describedAs("export batch history is append-only by trigger")
                .hasMessageContaining("cannot be deleted");

        service.reset(projectId, "Synthetic Review Project", admin);

        assertThat(jdbcTemplate().queryForObject("SELECT count(*) FROM export_batches", Long.class))
                .describedAs("TRUNCATE fires only statement-level triggers, so it gets past them")
                .isZero();
    }

    @Test
    @DisplayName("everything the deployment needs survives")
    void keepsTheProjectThePeopleAndTheMigrationLog() {
        UUID projectId = syntheticProject("Synthetic Review Project");
        Actor admin = seededAdmin(projectId);
        fixtures.createImportChain("Synthetic Review Project");

        service.reset(projectId, "Synthetic Review Project", admin);

        for (String kept : List.of("users", "project_memberships", "projects", "schema_migration_log")) {
            assertThat(jdbcTemplate().queryForObject("SELECT count(*) FROM " + kept, Long.class))
                    .describedAs("%s must survive a reset", kept)
                    .isPositive();
        }
    }

    @Test
    @DisplayName("the imported schedule is gone")
    void emptiesTheImportChain() {
        UUID projectId = syntheticProject("Synthetic Review Project");
        Actor admin = seededAdmin(projectId);
        fixtures.createImportChain("Another Project");

        assertThat(jdbcTemplate().queryForObject("SELECT count(*) FROM source_files", Long.class))
                .isPositive();

        service.reset(projectId, "Synthetic Review Project", admin);

        for (String wiped : List.of("source_files", "import_batches", "imported_tasks", "project_snapshots")) {
            assertThat(jdbcTemplate().queryForObject("SELECT count(*) FROM " + wiped, Long.class))
                    .describedAs("%s must be empty after a reset", wiped)
                    .isZero();
        }
    }

    @Test
    @DisplayName("the reset is the first entry of the trail it created")
    void writesExactlyOneAuditRowAfterTheWipe() {
        UUID projectId = syntheticProject("Synthetic Review Project");
        Actor admin = seededAdmin(projectId);
        fixtures.createImportChain("Synthetic Review Project");

        service.reset(projectId, "Synthetic Review Project", admin);

        assertThat(jdbcTemplate().queryForObject("SELECT count(*) FROM audit_events", Long.class))
                .describedAs("written after the truncate, or it would delete itself")
                .isEqualTo(1L);
        assertThat(jdbcTemplate().queryForObject(
                "SELECT event_type FROM audit_events", String.class))
                .isEqualTo("review_data_reset");
        assertThat(jdbcTemplate().queryForObject(
                "SELECT actor_user_id FROM audit_events", UUID.class))
                .isEqualTo(admin.userId());
    }

    @Test
    @DisplayName("the counts returned are what was actually there")
    void reportsWhatItDeleted() {
        UUID projectId = syntheticProject("Synthetic Review Project");
        Actor admin = seededAdmin(projectId);
        fixtures.createImportChain("Synthetic Review Project");

        long sourceFilesBefore = jdbcTemplate()
                .queryForObject("SELECT count(*) FROM source_files", Long.class);

        List<ReviewDataResetResult.TableReset> tables =
                service.reset(projectId, "Synthetic Review Project", admin);

        assertThat(tables).hasSize(ReviewDataResetScope.WIPE.size());
        assertThat(tables)
                .filteredOn(table -> table.name().equals("source_files"))
                .singleElement()
                .extracting(ReviewDataResetResult.TableReset::rowsDeleted)
                .isEqualTo(sourceFilesBefore);
    }

    @Test
    @DisplayName("a project without the synthetic marker is refused, and nothing is deleted")
    void refusesAnythingThatIsNotASyntheticReviewProject() {
        UUID realProject = fixtures.createProject("A Real Shutdown");
        fixtures.createImportChain("A Real Shutdown");
        long before = jdbcTemplate().queryForObject("SELECT count(*) FROM source_files", Long.class);

        assertThatThrownBy(() -> guard.requireSyntheticReviewProject(realProject))
                .isInstanceOf(ReviewResetRefusedException.class)
                .hasMessageContaining("not a synthetic review project");

        assertThat(jdbcTemplate().queryForObject("SELECT count(*) FROM source_files", Long.class))
                .describedAs("a refusal must not be a partial reset")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("an unknown project is refused too")
    void refusesAnUnknownProject() {
        assertThatThrownBy(() -> guard.requireSyntheticReviewProject(UUID.randomUUID()))
                .isInstanceOf(ReviewResetRefusedException.class);
    }

    @Test
    @DisplayName("the guard hands back the name the caller must type")
    void returnsTheProjectNameToConfirmAgainst() {
        UUID projectId = syntheticProject("Synthetic Review Project");

        assertThat(guard.requireSyntheticReviewProject(projectId)).isEqualTo("Synthetic Review Project");
    }
}
