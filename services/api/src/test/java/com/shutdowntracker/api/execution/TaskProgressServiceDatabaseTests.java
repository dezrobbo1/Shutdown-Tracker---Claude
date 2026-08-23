package com.shutdowntracker.api.execution;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
 * Exercises the field-to-export review chain against a real database.
 *
 * <p>The rules under test are product rules, not implementation details: supervisor
 * acceptance is not export approval, only leaf tasks produce export candidates, and only
 * the three whitelisted fields count.
 */
class TaskProgressServiceDatabaseTests extends AbstractDatabaseTest {

    private TaskProgressService service;
    private DatabaseFixtures fixtures;
    private UUID projectId;
    private UUID leafTaskId;
    private UUID summaryTaskId;
    private Actor fieldUser;
    private Actor supervisor;
    private Actor planner;

    @BeforeEach
    void setUp() {
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(dataSource());
        service = new TaskProgressService(
                new JdbcTaskProgressRepository(named),
                new JdbcAuditEventRecorder(named, new ObjectMapper()));
        fixtures = new DatabaseFixtures(jdbcTemplate());

        DatabaseFixtures.ImportChain chain = fixtures.createImportChain("Kiln Shutdown");
        projectId = chain.projectId();

        var persistence = new ImportedProjectPersistenceService(
                new JdbcImportedProjectRepository(named, new ObjectMapper()));
        persistence.persistParsedSnapshot(new ImportedProjectSnapshotCreateRequest(
                projectId, chain.importBatchId(), "PROJ-1", "Kiln Shutdown 2026", null, Map.of(),
                new ImportedProjectEntities(
                        List.of(
                                task("1", "Mechanical", true, null),
                                task("2", "Remove guard", false, "1")),
                        List.of(), List.of(), List.of())));

        summaryTaskId = taskIdFor("1");
        leafTaskId = taskIdFor("2");

        fieldUser = actor("field@example.com", "field_user");
        supervisor = actor("supervisor@example.com", "supervisor");
        planner = actor("planner@example.com", "planner");
    }

    @Test
    void recordsAFieldSubmissionAndMovesExecutionState() {
        TaskProgressUpdateRecord submitted = service.submit(projectId, fieldUser, progress(leafTaskId));

        assertThat(submitted.progressReviewState()).isEqualTo(ProgressReviewState.SUBMITTED);
        assertThat(submitted.plannerReviewState())
                .describedAs("a planner decision is not needed until a supervisor accepts")
                .isEqualTo(PlannerReviewState.NOT_REQUIRED);
        assertThat(submitted.exportState()).isEqualTo(ProgressExportState.NOT_ELIGIBLE);
        assertThat(submitted.submittedByUserId()).isEqualTo(fieldUser.userId());

        assertThat(jdbcTemplate().queryForObject(
                "SELECT execution_state FROM task_execution_states WHERE imported_task_id = ?",
                String.class, leafTaskId))
                .isEqualTo("in_progress");
    }

    @Test
    void aRetriedOfflineSubmissionDoesNotDoubleReport() {
        TaskProgressSubmitRequest request = new TaskProgressSubmitRequest(
                leafTaskId, TaskExecutionState.IN_PROGRESS, new BigDecimal("40"),
                null, null, null, null, "device-key-1", "local-1", null);

        TaskProgressUpdateRecord first = service.submit(projectId, fieldUser, request);
        TaskProgressUpdateRecord retry = service.submit(projectId, fieldUser, request);

        assertThat(retry.id())
                .describedAs("a retry over a poor connection must return the original submission")
                .isEqualTo(first.id());
        assertThat(jdbcTemplate().queryForObject(
                "SELECT count(*) FROM task_progress_updates", Integer.class)).isEqualTo(1);
    }

    @Test
    void supervisorAcceptanceRoutesALeafTaskToThePlannerQueue() {
        TaskProgressUpdateRecord submitted = service.submit(projectId, fieldUser, progress(leafTaskId));

        TaskProgressUpdateRecord reviewed = service.supervisorReview(
                projectId, submitted.id(), supervisor, ProgressReviewState.SUPERVISOR_ACCEPTED, "Checked on site.");

        assertThat(reviewed.progressReviewState()).isEqualTo(ProgressReviewState.SUPERVISOR_ACCEPTED);
        assertThat(reviewed.plannerReviewState()).isEqualTo(PlannerReviewState.NEEDS_PLANNER_REVIEW);
        assertThat(reviewed.exportState())
                .describedAs("supervisor review confirms operational validity, not export eligibility")
                .isEqualTo(ProgressExportState.NOT_ELIGIBLE);
    }

    @Test
    void summaryTaskProgressNeverReachesThePlannerQueue() {
        TaskProgressUpdateRecord submitted = service.submit(projectId, fieldUser, progress(summaryTaskId));

        TaskProgressUpdateRecord reviewed = service.supervisorReview(
                projectId, submitted.id(), supervisor, ProgressReviewState.SUPERVISOR_ACCEPTED, null);

        assertThat(reviewed.plannerReviewState())
                .describedAs("Microsoft Project rolls summary values up; they are never exported directly")
                .isEqualTo(PlannerReviewState.NOT_REQUIRED);
        assertThat(service.plannerQueue(projectId)).isEmpty();
    }

    @Test
    void anUpdateCarryingNoWhitelistedFieldNeedsNoPlannerDecision() {
        // A comment and a physical percent complete are operational context only.
        TaskProgressUpdateRecord submitted = service.submit(projectId, fieldUser, new TaskProgressSubmitRequest(
                leafTaskId, TaskExecutionState.BLOCKED, null, null, null,
                new BigDecimal("30"), "Waiting on scaffold.", null, null, null));

        TaskProgressUpdateRecord reviewed = service.supervisorReview(
                projectId, submitted.id(), supervisor, ProgressReviewState.SUPERVISOR_ACCEPTED, null);

        assertThat(reviewed.plannerReviewState()).isEqualTo(PlannerReviewState.NOT_REQUIRED);
    }

    // ------------------------------------------------------------------ the export queue

    /**
     * The defect these exist for: the console used to build its candidate list from the planner
     * queue, filtering it for updates the planner had already approved. An update leaves the
     * planner queue at the exact moment it becomes eligible, so that filter could never match and
     * no export preview could ever be created. Every test passed throughout, because each proved
     * one step and the break lived between two.
     */
    @Test
    void anApprovedUpdateLeavesThePlannerQueueAndArrivesInTheExportQueue() {
        TaskProgressUpdateRecord submitted = service.submit(projectId, fieldUser, progress(leafTaskId));
        service.supervisorReview(
                projectId, submitted.id(), supervisor, ProgressReviewState.SUPERVISOR_ACCEPTED, null);

        assertThat(service.plannerQueue(projectId)).extracting(TaskProgressUpdateRecord::id)
                .containsExactly(submitted.id());
        assertThat(service.exportQueue(projectId))
                .describedAs("nothing may be exported before the planner has decided")
                .isEmpty();

        service.plannerReview(projectId, submitted.id(), planner, true, "Safe to send.");

        assertThat(service.plannerQueue(projectId))
                .describedAs("the decision has been made, so it is no longer waiting for one")
                .isEmpty();
        assertThat(service.exportQueue(projectId)).extracting(TaskProgressUpdateRecord::id)
                .containsExactly(submitted.id());
    }

    @Test
    void aRejectedUpdateNeverReachesTheExportQueue() {
        TaskProgressUpdateRecord submitted = service.submit(projectId, fieldUser, progress(leafTaskId));
        service.supervisorReview(
                projectId, submitted.id(), supervisor, ProgressReviewState.SUPERVISOR_ACCEPTED, null);
        service.plannerReview(projectId, submitted.id(), planner, false, "Value does not match the site.");

        assertThat(service.exportQueue(projectId)).isEmpty();
    }

    /**
     * The reason the queue is keyed on {@code export_state} rather than on the planner's decision.
     *
     * <p>Superseding leaves {@code planner_review_state} saying {@code planner_approved}, because
     * the planner did approve that value, once. Only {@code export_state} records that it has since
     * been replaced — so a queue reading the decision would offer a stale value for export.
     */
    @Test
    void anUpdateSupersededAfterApprovalDropsOutOfTheExportQueue() {
        TaskProgressUpdateRecord original = service.submit(projectId, fieldUser, progress(leafTaskId));
        service.supervisorReview(
                projectId, original.id(), supervisor, ProgressReviewState.SUPERVISOR_ACCEPTED, null);
        service.plannerReview(projectId, original.id(), planner, true, null);
        assertThat(service.exportQueue(projectId)).hasSize(1);

        service.submit(projectId, fieldUser, new TaskProgressSubmitRequest(
                leafTaskId, TaskExecutionState.IN_PROGRESS, new BigDecimal("60"), null, null,
                null, "Corrected.", null, null, original.id()));

        assertThat(jdbcTemplate().queryForObject(
                "SELECT planner_review_state::text FROM task_progress_updates WHERE id = ?",
                String.class, original.id()))
                .describedAs("the planner's decision is a historical fact and is not rewritten")
                .isEqualTo("planner_approved");
        assertThat(service.exportQueue(projectId))
                .describedAs("but a superseded value must never travel")
                .isEmpty();
    }

    /**
     * The queue's side of the batch binding, set by hand so this stays a test of the queue.
     *
     * <p>What actually writes {@code export_batch_id} is an export preview claiming the updates it
     * was built from; that is proved end to end in {@link TaskProgressExportBindingTests}. Here the
     * column is set directly, so a change to the claim cannot quietly take this assertion with it.
     */
    @Test
    void anUpdateAlreadyCarriedByABatchIsNotOfferedAgain() {
        TaskProgressUpdateRecord submitted = service.submit(projectId, fieldUser, progress(leafTaskId));
        service.supervisorReview(
                projectId, submitted.id(), supervisor, ProgressReviewState.SUPERVISOR_ACCEPTED, null);
        service.plannerReview(projectId, submitted.id(), planner, true, null);
        assertThat(service.exportQueue(projectId)).hasSize(1);

        fixtures.acceptNewestSnapshot(projectId);
        UUID batchId = fixtures.createExportBatchShell(projectId);
        jdbcTemplate().update(
                "UPDATE task_progress_updates SET export_batch_id = ? WHERE id = ?", batchId, submitted.id());

        assertThat(service.exportQueue(projectId)).isEmpty();
    }

    @Test
    void plannerApprovalMakesTheValueExportEligible() {
        TaskProgressUpdateRecord submitted = service.submit(projectId, fieldUser, progress(leafTaskId));
        service.supervisorReview(
                projectId, submitted.id(), supervisor, ProgressReviewState.SUPERVISOR_ACCEPTED, null);

        TaskProgressUpdateRecord approved = service.plannerReview(
                projectId, submitted.id(), planner, true, "Safe to send.");

        assertThat(approved.plannerReviewState()).isEqualTo(PlannerReviewState.PLANNER_APPROVED);
        assertThat(approved.exportState()).isEqualTo(ProgressExportState.ELIGIBLE);
    }

    @Test
    void plannerRejectionLeavesTheValueOutOfExport() {
        TaskProgressUpdateRecord submitted = service.submit(projectId, fieldUser, progress(leafTaskId));
        service.supervisorReview(
                projectId, submitted.id(), supervisor, ProgressReviewState.SUPERVISOR_ACCEPTED, null);

        TaskProgressUpdateRecord rejected = service.plannerReview(
                projectId, submitted.id(), planner, false, "Conflicts with the imported finish.");

        assertThat(rejected.plannerReviewState()).isEqualTo(PlannerReviewState.PLANNER_REJECTED);
        assertThat(rejected.exportState()).isEqualTo(ProgressExportState.NOT_ELIGIBLE);
    }

    @Test
    void aPlannerCannotReviewAnUpdateNoSupervisorHasAccepted() {
        TaskProgressUpdateRecord submitted = service.submit(projectId, fieldUser, progress(leafTaskId));

        assertThatThrownBy(() -> service.plannerReview(projectId, submitted.id(), planner, true, null))
                .describedAs("the review chain must not be skippable")
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void aSupervisorCannotReviewTheSameUpdateTwice() {
        TaskProgressUpdateRecord submitted = service.submit(projectId, fieldUser, progress(leafTaskId));
        service.supervisorReview(
                projectId, submitted.id(), supervisor, ProgressReviewState.SUPERVISOR_ACCEPTED, null);

        assertThatThrownBy(() -> service.supervisorReview(
                projectId, submitted.id(), supervisor, ProgressReviewState.REJECTED, null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void aCorrectionSupersedesTheOriginalWithoutErasingIt() {
        TaskProgressUpdateRecord original = service.submit(projectId, fieldUser, progress(leafTaskId));

        TaskProgressUpdateRecord correction = service.submit(projectId, fieldUser, new TaskProgressSubmitRequest(
                leafTaskId, TaskExecutionState.IN_PROGRESS, new BigDecimal("60"),
                null, null, null, "Corrected after walkdown.", null, null, original.id()));

        assertThat(correction.supersedesProgressUpdateId()).isEqualTo(original.id());
        assertThat(jdbcTemplate().queryForObject(
                "SELECT progress_review_state FROM task_progress_updates WHERE id = ?",
                String.class, original.id()))
                .isEqualTo("superseded");
        assertThat(jdbcTemplate().queryForObject(
                "SELECT count(*) FROM task_progress_updates", Integer.class))
                .describedAs("the original claim stays on the record")
                .isEqualTo(2);
    }

    @Test
    void supervisorQueueHoldsOnlyUnreviewedSubmissions() {
        TaskProgressUpdateRecord first = service.submit(projectId, fieldUser, progress(leafTaskId));
        service.submit(projectId, fieldUser, progress(summaryTaskId));
        service.supervisorReview(projectId, first.id(), supervisor, ProgressReviewState.SUPERVISOR_ACCEPTED, null);

        assertThat(service.supervisorQueue(projectId)).hasSize(1);
    }

    @Test
    void aBlockedUpdateMustSayWhy() {
        assertThatThrownBy(() -> new TaskProgressSubmitRequest(
                leafTaskId, TaskExecutionState.BLOCKED, null, null, null, null, null, null, null, null))
                .describedAs("a blocker with no reason is not actionable")
                .isInstanceOf(IllegalArgumentException.class);
    }

    private TaskProgressSubmitRequest progress(UUID taskId) {
        return new TaskProgressSubmitRequest(
                taskId, TaskExecutionState.IN_PROGRESS, new BigDecimal("50"),
                OffsetDateTime.of(2026, 8, 1, 6, 0, 0, 0, ZoneOffset.UTC),
                null, null, "Started on shift.", null, null, null);
    }

    private ImportedTaskCreateRequest task(String uid, String name, boolean summary, String parentUid) {
        return new ImportedTaskCreateRequest(
                uid, uid, name, null, null, summary ? 0 : 1, summary, parentUid, null,
                null, null, null, null, null, null, null, Map.of());
    }

    private UUID taskIdFor(String externalUid) {
        return jdbcTemplate().queryForObject(
                "SELECT id FROM imported_tasks WHERE external_uid = ?", UUID.class, externalUid);
    }

    private Actor actor(String email, String role) {
        UUID userId = fixtures.createUser(email, role);
        fixtures.grantMembership(projectId, userId, role);
        return new Actor(userId, role, role);
    }
}
