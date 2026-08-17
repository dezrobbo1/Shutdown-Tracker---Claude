package com.shutdowntracker.api.criticalwatch;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.audit.JdbcAuditEventRecorder;
import com.shutdowntracker.api.importedproject.ImportedProjectEntities;
import com.shutdowntracker.api.importedproject.ImportedProjectPersistenceService;
import com.shutdowntracker.api.importedproject.ImportedProjectSnapshotCreateRequest;
import com.shutdowntracker.api.importedproject.ImportedTaskCreateRequest;
import com.shutdowntracker.api.importedproject.JdbcImportedProjectRepository;
import com.shutdowntracker.api.support.AbstractDatabaseTest;
import com.shutdowntracker.api.support.DatabaseFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises Critical Watch reporting against real imported data.
 *
 * <p>The fixture has two summary tasks with leaves under each, so a package sourced from
 * one summary and a package spanning both can both be checked.
 */
class CriticalWatchServiceDatabaseTests extends AbstractDatabaseTest {

    private CriticalWatchService service;
    private CriticalWatchRepository repository;
    private DatabaseFixtures fixtures;
    private UUID projectId;
    private UUID snapshotId;
    private UUID watchlistId;
    private Actor control;

    @BeforeEach
    void setUp() {
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(dataSource());
        repository = new JdbcCriticalWatchRepository(named);
        service = new CriticalWatchService(
                repository, new JdbcAuditEventRecorder(named, new ObjectMapper()));
        fixtures = new DatabaseFixtures(jdbcTemplate());

        DatabaseFixtures.ImportChain chain = fixtures.createImportChain("Kiln Shutdown");
        projectId = chain.projectId();
        UUID userId = fixtures.createUser("control@example.com", "Shutdown Control");
        fixtures.grantMembership(projectId, userId, "shutdown_control");
        control = new Actor(userId, "shutdown_control", "Shutdown Control");

        snapshotId = new ImportedProjectPersistenceService(
                new JdbcImportedProjectRepository(named, new ObjectMapper()))
                .persistParsedSnapshot(new ImportedProjectSnapshotCreateRequest(
                        projectId, chain.importBatchId(), "PROJ-1", "Kiln", null, Map.of(),
                        new ImportedProjectEntities(
                                List.of(
                                        task("1", "Mechanical", true, 0, null),
                                        task("2", "Remove guard", false, 1, "1"),
                                        task("3", "Weld repair", false, 1, "1"),
                                        task("4", "Electrical", true, 0, null),
                                        task("5", "Isolate feeder", false, 1, "4")),
                                List.of(), List.of(), List.of())))
                .snapshot().id();

        watchlistId = service.createWatchlist(projectId, control, "Kiln Critical", "Shutdown watch").id();
    }

    @Test
    void aPackageSourcedFromASummaryTaskReportsOnItsDescendants() {
        CriticalWorkPackageRecord workPackage =
                service.createWorkPackage(projectId, control, watchlistId, "Mechanical WP", null);
        service.addSource(projectId, control, workPackage.id(), snapshotId, taskId("1"), true);

        List<UUID> reported = service.reportedTasks(projectId, workPackage.id());

        assertThat(reported)
                .describedAs("the summary task and both its leaves are in reporting scope")
                .containsExactlyInAnyOrder(taskId("1"), taskId("2"), taskId("3"));
    }

    @Test
    void descendantsCanBeExcluded() {
        CriticalWorkPackageRecord workPackage =
                service.createWorkPackage(projectId, control, watchlistId, "Summary only", null);
        service.addSource(projectId, control, workPackage.id(), snapshotId, taskId("1"), false);

        assertThat(service.reportedTasks(projectId, workPackage.id()))
                .containsExactly(taskId("1"));
    }

    @Test
    void aSecondSourceMakesThePackageMultiSummary() {
        CriticalWorkPackageRecord workPackage =
                service.createWorkPackage(projectId, control, watchlistId, "Cross-discipline WP", null);

        CriticalWorkPackageSourceRecord first =
                service.addSource(projectId, control, workPackage.id(), snapshotId, taskId("1"), true);
        CriticalWorkPackageSourceRecord second =
                service.addSource(projectId, control, workPackage.id(), snapshotId, taskId("4"), true);

        assertThat(first.sourceType()).isEqualTo("summary_task");
        assertThat(second.sourceType())
                .describedAs("a reporting group crossing summary boundaries is multi_summary")
                .isEqualTo("multi_summary");

        assertThat(service.reportedTasks(projectId, workPackage.id()))
                .containsExactlyInAnyOrder(
                        taskId("1"), taskId("2"), taskId("3"), taskId("4"), taskId("5"));
    }

    @Test
    void submitsACriticalUpdateWithLines() {
        CriticalWorkPackageRecord workPackage =
                service.createWorkPackage(projectId, control, watchlistId, "Mechanical WP", null);

        CriticalUpdateRecord update = service.submitUpdate(projectId, control, new CriticalUpdateSubmitRequest(
                workPackage.id(), "scheduled", "Guard removal", "Waiting on scaffold", "Weld by 18:00",
                null, null, null,
                List.of(new CriticalUpdateLineRequest(
                        taskId("2"), "50% by noon", "40%", "Scaffold late",
                        "Chase supplier", new BigDecimal("40"), null))));

        assertThat(update.status()).isEqualTo("submitted");
        assertThat(update.updateMode()).isEqualTo("scheduled");
        assertThat(update.submittedByUserId()).isEqualTo(control.userId());
        assertThat(repository.countUpdateLines(update.id())).isEqualTo(1);
    }

    @Test
    void aRetriedOfflineUpdateDoesNotDoubleReport() {
        CriticalWorkPackageRecord workPackage =
                service.createWorkPackage(projectId, control, watchlistId, "WP", null);
        CriticalUpdateSubmitRequest request = new CriticalUpdateSubmitRequest(
                workPackage.id(), "shift", "Focus", null, null, "device-key-1", "local-1", null, List.of());

        CriticalUpdateRecord first = service.submitUpdate(projectId, control, request);
        CriticalUpdateRecord retry = service.submitUpdate(projectId, control, request);

        assertThat(retry.id()).isEqualTo(first.id());
        assertThat(service.updates(projectId, workPackage.id())).hasSize(1);
    }

    @Test
    void aCorrectionSupersedesTheEarlierReportWithoutErasingIt() {
        CriticalWorkPackageRecord workPackage =
                service.createWorkPackage(projectId, control, watchlistId, "WP", null);
        CriticalUpdateRecord original = service.submitUpdate(projectId, control,
                new CriticalUpdateSubmitRequest(
                        workPackage.id(), "ad_hoc", "First read", null, null, null, null, null, List.of()));

        CriticalUpdateRecord correction = service.submitUpdate(projectId, control,
                new CriticalUpdateSubmitRequest(
                        workPackage.id(), "ad_hoc", "Corrected read", null, null,
                        null, null, original.id(), List.of()));

        assertThat(correction.supersedesCriticalUpdateId()).isEqualTo(original.id());
        assertThat(jdbcTemplate().queryForObject(
                "SELECT status FROM critical_updates WHERE id = ?", String.class, original.id()))
                .isEqualTo("superseded");
        assertThat(service.updates(projectId, workPackage.id()))
                .describedAs("what was reported at the time stays readable")
                .hasSize(2);
    }

    @Test
    void rejectsAnUnsupportedUpdateMode() {
        assertThatThrownBy(() -> new CriticalUpdateSubmitRequest(
                UUID.randomUUID(), "whenever", null, null, null, null, null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesAnUpdateForAPackageOnAnotherProject() {
        UUID otherProject = fixtures.createProject("Boiler");

        assertThatThrownBy(() -> service.submitUpdate(otherProject, control,
                new CriticalUpdateSubmitRequest(
                        UUID.randomUUID(), "ad_hoc", null, null, null, null, null, null, List.of())))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void doesNotListWorkPackagesForAWatchlistOnAnotherProject() {
        service.createWorkPackage(projectId, control, watchlistId, "Mechanical WP", null);
        UUID otherProject = fixtures.createProject("Boiler");

        assertThat(service.workPackages(otherProject, watchlistId))
                .describedAs("a watchlist id is not a secret, so the project must be part of the lookup")
                .isEmpty();
        assertThat(service.workPackages(projectId, watchlistId))
                .describedAs("the owning project still sees its own packages")
                .hasSize(1);
    }

    @Test
    void aCorrectionCannotRetireAReportOnAnotherProject() {
        UUID otherProject = fixtures.createProject("Boiler");
        UUID otherWatchlist = service.createWatchlist(otherProject, control, "Boiler Critical", null).id();
        CriticalWorkPackageRecord theirPackage =
                service.createWorkPackage(otherProject, control, otherWatchlist, "Boiler WP", null);
        CriticalUpdateRecord theirs = service.submitUpdate(otherProject, control,
                new CriticalUpdateSubmitRequest(
                        theirPackage.id(), "ad_hoc", "Their read", null, null, null, null, null, List.of()));

        CriticalWorkPackageRecord mine =
                service.createWorkPackage(projectId, control, watchlistId, "Kiln WP", null);

        assertThatThrownBy(() -> service.submitUpdate(projectId, control,
                new CriticalUpdateSubmitRequest(
                        mine.id(), "ad_hoc", "Correction", null, null,
                        null, null, theirs.id(), List.of())))
                .describedAs("naming another project's update is refused, not quietly ignored")
                .isInstanceOf(ResponseStatusException.class);

        assertThat(jdbcTemplate().queryForObject(
                "SELECT status FROM critical_updates WHERE id = ?", String.class, theirs.id()))
                .describedAs("the other project's report is still standing")
                .isEqualTo("submitted");
    }

    /**
     * The summary counts current reports, and puts what nobody has reported on first.
     *
     * <p>A superseded report is not counted twice: the correction that replaced it is the
     * current one. Getting this wrong would show a package as covered on the strength of a
     * report that has since been withdrawn.
     */
    @Test
    void reportingSummaryCountsCurrentReportsAndLeadsWithTheUnreported() {
        CriticalWorkPackageRecord reported =
                service.createWorkPackage(projectId, control, watchlistId, "Reported WP", null);
        CriticalWorkPackageRecord silent =
                service.createWorkPackage(projectId, control, watchlistId, "Silent WP", null);

        CriticalUpdateRecord original = service.submitUpdate(projectId, control,
                new CriticalUpdateSubmitRequest(
                        reported.id(), "shift", "First read", null, null, null, null, null, List.of()));
        service.submitUpdate(projectId, control,
                new CriticalUpdateSubmitRequest(
                        reported.id(), "shift", "Corrected read", null, null,
                        null, null, original.id(), List.of()));

        List<CriticalWorkPackageReportingSummary> summaries = service.reportingSummaries(projectId);

        assertThat(summaries).hasSize(2);
        assertThat(summaries.get(0).name())
                .describedAs("a package nobody has reported on needs attention first")
                .isEqualTo("Silent WP");
        assertThat(summaries.get(0).updateCount()).isZero();
        assertThat(summaries.get(0).lastSubmittedAt()).isNull();

        CriticalWorkPackageReportingSummary covered = summaries.get(1);
        assertThat(covered.workPackageId()).isEqualTo(reported.id());
        assertThat(covered.updateCount())
                .describedAs("the correction counts; the report it replaced does not")
                .isEqualTo(1);
        assertThat(covered.lastSubmittedAt()).isNotNull();
    }

    @Test
    void recordsThatReportingDidNotCalculateCriticalPath() {
        service.createWorkPackage(projectId, control, watchlistId, "WP", null);

        String metadata = jdbcTemplate().queryForObject(
                """
                SELECT metadata::text FROM audit_events
                WHERE event_type = 'critical_work_package.created'
                """,
                String.class);

        assertThat(metadata)
                .describedAs("Critical Watch is reporting, and the audit trail says so")
                .contains("criticalPathCalculated");
    }

    private UUID taskId(String externalUid) {
        return jdbcTemplate().queryForObject(
                "SELECT id FROM imported_tasks WHERE external_uid = ?", UUID.class, externalUid);
    }

    private ImportedTaskCreateRequest task(
            String uid, String name, boolean summary, int outlineLevel, String parentUid) {
        return new ImportedTaskCreateRequest(
                uid, uid, name, null, null, outlineLevel, summary, parentUid, null,
                null, null, null, null, null, null, null, Map.of());
    }
}
