package com.shutdowntracker.api.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.audit.JdbcAuditEventRecorder;
import com.shutdowntracker.api.exportpreview.ApprovalState;
import com.shutdowntracker.api.exportpreview.ExportBatchDecisionRequest;
import com.shutdowntracker.api.exportpreview.ExportBatchGeneratedRequest;
import com.shutdowntracker.api.exportpreview.ExportBatchProjectOpenRequest;
import com.shutdowntracker.api.exportpreview.ExportBatchVerificationRequest;
import com.shutdowntracker.api.exportpreview.ExportCandidateApprovalEventRequest;
import com.shutdowntracker.api.exportpreview.ExportCandidateCreateRequest;
import com.shutdowntracker.api.exportpreview.ExportCandidateRecord;
import com.shutdowntracker.api.exportpreview.ExportCandidateService;
import com.shutdowntracker.api.exportpreview.ExportPreviewCreateRequest;
import com.shutdowntracker.api.exportpreview.ExportPreviewDetail;
import com.shutdowntracker.api.exportpreview.ExportPreviewService;
import com.shutdowntracker.api.exportpreview.JdbcExportPreviewRepository;
import com.shutdowntracker.api.importedproject.ImportedProjectEntities;
import com.shutdowntracker.api.importedproject.ImportedProjectPersistenceService;
import com.shutdowntracker.api.importedproject.ImportedProjectSnapshotCreateRequest;
import com.shutdowntracker.api.importedproject.ImportedTaskCreateRequest;
import com.shutdowntracker.api.importedproject.JdbcImportedProjectRepository;
import com.shutdowntracker.api.support.AbstractDatabaseTest;
import com.shutdowntracker.api.support.DatabaseFixtures;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Which export batch carried which approved field update, against a real database.
 *
 * <p>{@code export_batch_id} existed from {@code V009} and nothing ever wrote it, so the audit
 * could not answer that question and the {@code export_batch_id IS NULL} clause in the export queue
 * described an intention rather than deciding anything. These exercise the three writes an export
 * batch makes to the rows it carries — claim, release, and record that they travelled.
 *
 * <p>The batch is built the only way one can be: a candidate, an approval, a preview, through the
 * real services and the real V007 integrity triggers, with the progress update as the candidate's
 * source entity. A hand-inserted batch would not be one the application could have produced, and a
 * claim proved against it would prove nothing. {@link ExportPreviewService} is given the real
 * {@link JdbcTaskProgressRepository} as its binding here — the double other export tests use
 * answers "as many as you asked for", which is exactly what these must not assume.
 */
class TaskProgressExportBindingTests extends AbstractDatabaseTest {

    private TaskProgressService progressService;
    private ExportCandidateService candidateService;
    private ExportPreviewService previewService;
    private DatabaseFixtures fixtures;

    private UUID projectId;
    private UUID snapshotId;
    private UUID leafTaskId;
    private UUID otherLeafTaskId;
    private Actor fieldUser;
    private Actor supervisor;
    private Actor planner;

    @BeforeEach
    void setUp() {
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(dataSource());
        ObjectMapper objectMapper = new ObjectMapper();
        JdbcAuditEventRecorder auditEventRecorder = new JdbcAuditEventRecorder(named, objectMapper);
        JdbcTaskProgressRepository progressRepository = new JdbcTaskProgressRepository(named);
        JdbcExportPreviewRepository exportRepository = new JdbcExportPreviewRepository(named, objectMapper);

        progressService = new TaskProgressService(progressRepository, auditEventRecorder);
        candidateService = new ExportCandidateService(exportRepository, auditEventRecorder);
        previewService = new ExportPreviewService(exportRepository, auditEventRecorder, progressRepository);

        fixtures = new DatabaseFixtures(jdbcTemplate());
        DatabaseFixtures.ImportChain chain = fixtures.createImportChain("Kiln Shutdown");
        projectId = chain.projectId();

        new ImportedProjectPersistenceService(new JdbcImportedProjectRepository(named, objectMapper))
                .persistParsedSnapshot(new ImportedProjectSnapshotCreateRequest(
                        projectId, chain.importBatchId(), "PROJ-1", "Kiln Shutdown 2026", null, Map.of(),
                        new ImportedProjectEntities(
                                List.of(
                                        task("1", "Mechanical", true, null),
                                        task("2", "Remove guard", false, "1"),
                                        task("3", "Refit guard", false, "1")),
                                List.of(), List.of(), List.of())));

        snapshotId = fixtures.acceptNewestSnapshot(projectId);
        leafTaskId = jdbcTemplate().queryForObject(
                "SELECT id FROM imported_tasks WHERE external_uid = ?", UUID.class, "2");
        otherLeafTaskId = jdbcTemplate().queryForObject(
                "SELECT id FROM imported_tasks WHERE external_uid = ?", UUID.class, "3");

        fieldUser = actor("field@example.com", "field_user");
        supervisor = actor("supervisor@example.com", "supervisor");
        planner = actor("planner@example.com", "planner");
    }

    @Test
    void aPreviewClaimsTheUpdatesItWasBuiltFromSoTheyLeaveTheExportQueue() {
        TaskProgressUpdateRecord approved = eligibleUpdate();
        assertThat(progressService.exportQueue(projectId)).hasSize(1);

        UUID batchId = previewFor(approved).batch().id();

        assertThat(progressService.exportQueue(projectId))
                .describedAs("a batch has claimed it, so the same field change cannot be previewed twice")
                .isEmpty();
        assertThat(exportState(approved.id())).isEqualTo("in_export_preview");
        assertThat(exportBatchId(approved.id())).isEqualTo(batchId);
    }

    /**
     * The reason the claim counts distinct source entities rather than lines.
     *
     * <p>One approved update produces one candidate per whitelisted field, so a batch carrying two
     * fields of the same update has two lines and one row to claim. A shortfall check written
     * against the line count would refuse the ordinary case.
     */
    @Test
    void twoFieldsOfOneUpdateClaimItOnce() {
        TaskProgressUpdateRecord approved = eligibleUpdateWithTwoValues();

        UUID percentCandidate = candidate(approved, "percent_complete", approved.percentComplete().toPlainString());
        UUID startCandidate = candidate(approved, "actual_start", approved.actualStart().toString());
        ExportPreviewDetail preview = previewService.createPreview(
                projectId, new ExportPreviewCreateRequest(snapshotId, List.of(percentCandidate, startCandidate), Map.of()));

        assertThat(preview.lines()).hasSize(2);
        assertThat(exportState(approved.id())).isEqualTo("in_export_preview");
        assertThat(exportBatchId(approved.id())).isEqualTo(preview.batch().id());
    }

    @Test
    void aRejectedPreviewReturnsItsUpdatesToTheExportQueue() {
        TaskProgressUpdateRecord approved = eligibleUpdate();
        UUID batchId = previewFor(approved).batch().id();

        previewService.rejectBatch(
                projectId, batchId, new ExportBatchDecisionRequest(planner.userId(), "Wrong window.", Map.of()));

        assertThat(progressService.exportQueue(projectId)).extracting(TaskProgressUpdateRecord::id)
                .describedAs("the field work was approved and never carried anywhere, so it must be offerable again")
                .containsExactly(approved.id());
        assertThat(exportState(approved.id())).isEqualTo("eligible");
        assertThat(exportBatchId(approved.id()))
                .describedAs("no batch carried it, so no batch may claim to have")
                .isNull();
    }

    /**
     * Generation, not verification, is what makes an update {@code exported}.
     *
     * <p>Verification is the batch's fact. A generated batch a planner never opens still carried
     * these values, and an update left at {@code in_export_preview} because nobody clicked verify
     * would say the artifact does not contain it.
     */
    @Test
    void generationRecordsThatTheUpdatesTravelledAndKeepsTheBatchId() {
        TaskProgressUpdateRecord approved = eligibleUpdate();
        UUID batchId = generatedBatch(approved);

        assertThat(exportState(approved.id())).isEqualTo("exported");
        assertThat(exportBatchId(approved.id()))
                .describedAs("which batch carried which field change outlives the batch finishing")
                .isEqualTo(batchId);
        assertThat(progressService.exportQueue(projectId)).isEmpty();
    }

    @Test
    void verificationLeavesTheUpdateAsGenerationFoundIt() {
        TaskProgressUpdateRecord approved = eligibleUpdate();
        UUID batchId = generatedBatch(approved);

        verify(batchId);

        assertThat(exportState(approved.id()))
                .describedAs("how far the batch got is the batch's status, not a second copy here")
                .isEqualTo("exported");
        assertThat(exportBatchId(approved.id())).isEqualTo(batchId);
    }

    @Test
    void anExportedUpdateIsTerminalAndCannotBeReleasedBack() {
        TaskProgressUpdateRecord approved = eligibleUpdate();
        UUID batchId = generatedBatch(approved);

        int released = new JdbcTaskProgressRepository(new NamedParameterJdbcTemplate(dataSource()))
                .releaseFromExportBatch(batchId);

        assertThat(released)
                .describedAs("release returns claimed updates, not ones that already travelled")
                .isZero();
        assertThat(exportState(approved.id())).isEqualTo("exported");
    }

    /**
     * The stale link a release that only looked at claimed rows would leave behind.
     *
     * <p>A correction can supersede an update after a preview has claimed it. {@code markSuperseded}
     * moves the row out of {@code in_export_preview}, so a release predicated only on that state
     * skips it and leaves {@code export_batch_id} naming a batch that was rejected and carried
     * nothing — false provenance in the column whose whole purpose is provenance.
     */
    @Test
    void aRejectedBatchUnlinksAnUpdateSupersededWhileItHeldIt() {
        TaskProgressUpdateRecord approved = eligibleUpdate();
        UUID batchId = previewFor(approved).batch().id();

        progressService.submit(projectId, fieldUser, new TaskProgressSubmitRequest(
                leafTaskId, TaskExecutionState.IN_PROGRESS, new BigDecimal("60"), null, null,
                null, "Corrected after walkdown.", null, null, approved.id()));
        assertThat(exportState(approved.id())).isEqualTo("superseded");
        assertThat(exportBatchId(approved.id())).isEqualTo(batchId);

        previewService.rejectBatch(
                projectId, batchId, new ExportBatchDecisionRequest(planner.userId(), "Wrong window.", Map.of()));

        assertThat(exportState(approved.id()))
                .describedAs("a replaced value must not become offerable again")
                .isEqualTo("superseded");
        assertThat(exportBatchId(approved.id()))
                .describedAs("a rejected batch carried nothing and must not be named as this one's carrier")
                .isNull();
        assertThat(progressService.exportQueue(projectId))
                .describedAs("neither the replaced value nor the unreviewed correction may be exported")
                .isEmpty();
    }

    /**
     * The shortfall the preview refuses rather than absorbing.
     *
     * <p>A correction supersedes the approved value between the candidates being approved and the
     * preview being built. The batch would otherwise be created carrying a line for a value that is
     * no longer live, and the row it named would stay unclaimed — a preview quietly carrying fewer
     * approved changes than it lists.
     */
    @Test
    void aPreviewIsRefusedWhenAnUpdateItWasBuiltFromCanNoLongerBeClaimed() {
        TaskProgressUpdateRecord approved = eligibleUpdate();
        UUID candidateId = candidate(approved, "percent_complete", approved.percentComplete().toPlainString());

        progressService.submit(projectId, fieldUser, new TaskProgressSubmitRequest(
                leafTaskId, TaskExecutionState.IN_PROGRESS, new BigDecimal("60"), null, null,
                null, "Corrected after walkdown.", null, null, approved.id()));

        assertThatThrownBy(() -> previewService.createPreview(
                projectId, new ExportPreviewCreateRequest(snapshotId, List.of(candidateId), Map.of())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("could not claim every field update");

        assertThat(exportState(approved.id()))
                .describedAs("the superseded value was not swept into the batch")
                .isEqualTo("superseded");
    }

    /**
     * A batch takes every exportable value on an update, or none of them.
     *
     * <p>The binding is one row per batch, so a preview carrying percent complete while leaving the
     * same update's reviewed actual start behind has no truthful way to be recorded: claiming the
     * row marks it exported once the artifact exists, saying a value travelled that did not, and
     * the row has left the export queue so no later preview can carry the remainder. Refusing is
     * the reversible direction — the planner adds the missing candidate and previews again.
     */
    @Test
    void aPreviewCarryingOnlySomeOfAnUpdatesReviewedValuesIsRefused() {
        TaskProgressUpdateRecord approved = eligibleUpdateWithTwoValues();
        UUID percentOnly = candidate(approved, "percent_complete", approved.percentComplete().toPlainString());

        assertThatThrownBy(() -> previewService.createPreview(
                projectId, new ExportPreviewCreateRequest(snapshotId, List.of(percentOnly), Map.of())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("every exportable value on an update or none of them");

        assertThat(exportState(approved.id()))
                .describedAs("the update was not consumed by a batch that could not carry all of it")
                .isEqualTo("eligible");
        assertThat(exportBatchId(approved.id())).isNull();
        assertThat(progressService.exportQueue(projectId)).extracting(TaskProgressUpdateRecord::id)
                .describedAs("and it is still offered, so the remainder can be previewed properly")
                .containsExactly(approved.id());
    }

    /**
     * A line the batch cannot export does not carry its update anywhere.
     *
     * <p>A preview may hold a line whose candidate approval is not current; it is written with
     * {@code is_export_eligible = false} and left out of the generated artifact. Counting or
     * claiming its source would mark an update exported on the strength of a line that never
     * travelled.
     */
    @Test
    void anUpdatePresentOnlyThroughAnIneligibleLineIsNeitherCountedNorClaimed() {
        TaskProgressUpdateRecord carried = eligibleUpdate();
        TaskProgressUpdateRecord notCarried = eligibleUpdateOn(otherLeafTaskId);

        UUID carriedCandidate = candidate(carried, "percent_complete", carried.percentComplete().toPlainString());
        UUID awaitingCandidate = candidateAwaitingReview(
                notCarried, "percent_complete", notCarried.percentComplete().toPlainString());

        ExportPreviewDetail preview = previewService.createPreview(
                projectId,
                new ExportPreviewCreateRequest(snapshotId, List.of(carriedCandidate, awaitingCandidate), Map.of()));

        assertThat(preview.lines()).hasSize(2);
        assertThat(exportState(carried.id())).isEqualTo("in_export_preview");
        assertThat(exportState(notCarried.id()))
                .describedAs("an unapproved line carries nothing, so its update is untouched")
                .isEqualTo("eligible");
        assertThat(exportBatchId(notCarried.id())).isNull();
        assertThat(progressService.exportQueue(projectId)).extracting(TaskProgressUpdateRecord::id)
                .containsExactly(notCarried.id());
    }

    // ------------------------------------------------------------------ fixture

    /**
     * One exportable value, submitted, accepted by a supervisor and approved by a planner — the
     * only way to reach eligible.
     *
     * <p>Deliberately carries percent complete alone. A batch claims an update only when it carries
     * every exportable value on it, so an update with a second reviewed value would need a second
     * candidate in every preview below; {@link #eligibleUpdateWithTwoValues} is for the tests that
     * are about that.
     */
    private TaskProgressUpdateRecord eligibleUpdate() {
        return eligibleUpdateOn(leafTaskId);
    }

    /** The same, on a named task, for previews carrying two updates at once. */
    private TaskProgressUpdateRecord eligibleUpdateOn(UUID taskId) {
        return reviewed(new TaskProgressSubmitRequest(
                taskId, TaskExecutionState.IN_PROGRESS, new BigDecimal("50"),
                null, null, null, "Started on shift.", null, null, null));
    }

    /** Percent complete and an actual start: two values a batch must carry together or not at all. */
    private TaskProgressUpdateRecord eligibleUpdateWithTwoValues() {
        return reviewed(new TaskProgressSubmitRequest(
                leafTaskId, TaskExecutionState.IN_PROGRESS, new BigDecimal("50"),
                OffsetDateTime.of(2026, 8, 1, 6, 0, 0, 0, ZoneOffset.UTC),
                null, null, "Started on shift.", null, null, null));
    }

    private TaskProgressUpdateRecord reviewed(TaskProgressSubmitRequest request) {
        TaskProgressUpdateRecord submitted = progressService.submit(projectId, fieldUser, request);
        progressService.supervisorReview(
                projectId, submitted.id(), supervisor, ProgressReviewState.SUPERVISOR_ACCEPTED, "Checked on site.");
        TaskProgressUpdateRecord approved = progressService.plannerReview(
                projectId, submitted.id(), planner, true, "Safe to send.");

        assertThat(approved.exportState()).isEqualTo(ProgressExportState.ELIGIBLE);
        return approved;
    }

    private ExportPreviewDetail previewFor(TaskProgressUpdateRecord update) {
        UUID candidateId = candidate(update, "percent_complete", update.percentComplete().toPlainString());
        return previewService.createPreview(
                projectId, new ExportPreviewCreateRequest(snapshotId, List.of(candidateId), Map.of()));
    }

    private UUID generatedBatch(TaskProgressUpdateRecord update) {
        UUID batchId = previewFor(update).batch().id();
        previewService.approveBatch(
                projectId, batchId, new ExportBatchDecisionRequest(planner.userId(), "Ready to send.", Map.of()));
        previewService.markGenerated(projectId, batchId, new ExportBatchGeneratedRequest(
                "file:///artifacts/" + batchId + ".mspdi.xml", "c".repeat(64), planner.userId(), "Generated.", Map.of()));
        return batchId;
    }

    private void verify(UUID batchId) {
        previewService.markOpenedInMicrosoftProject(projectId, batchId, new ExportBatchProjectOpenRequest(
                planner.userId(), "Opened by the planner.", Map.of()));
        previewService.verifyBatch(projectId, batchId, new ExportBatchVerificationRequest(
                planner.userId(), "Opened as a complete schedule.", Map.of()));
    }

    /**
     * One authoritative candidate, sourced from the progress update itself.
     *
     * <p>{@code task_progress_update} is the source type the console sends and the only one with a
     * row for a batch to claim; a candidate naming anything else is left alone by the binding.
     */
    private UUID candidate(TaskProgressUpdateRecord update, String fieldName, String proposedValue) {
        ExportCandidateRecord record = candidateService.createCandidate(projectId, new ExportCandidateCreateRequest(
                snapshotId,
                update.importedTaskId(),
                fieldName,
                proposedValue,
                "task_progress_update",
                update.id(),
                "source-v1",
                planner.userId(),
                OffsetDateTime.of(2026, 8, 1, 7, 0, 0, 0, ZoneOffset.UTC),
                "Reviewed field progress",
                Map.of()));

        candidateService.recordApprovalEvent(projectId, record.id(), new ExportCandidateApprovalEventRequest(
                ApprovalState.APPROVED_FOR_EXPORT,
                OffsetDateTime.of(2026, 8, 1, 7, 1, 0, 0, ZoneOffset.UTC),
                planner.userId(),
                OffsetDateTime.of(2026, 8, 1, 7, 2, 0, 0, ZoneOffset.UTC),
                "Approved for export",
                Map.of()));
        return record.id();
    }

    /** A candidate whose current approval is not {@code approved_for_export}, so its line cannot travel. */
    private UUID candidateAwaitingReview(TaskProgressUpdateRecord update, String fieldName, String proposedValue) {
        UUID candidateId = candidate(update, fieldName, proposedValue);
        candidateService.recordApprovalEvent(projectId, candidateId, new ExportCandidateApprovalEventRequest(
                ApprovalState.AWAITING_REVIEW,
                OffsetDateTime.of(2026, 8, 1, 7, 3, 0, 0, ZoneOffset.UTC),
                planner.userId(),
                OffsetDateTime.of(2026, 8, 1, 7, 4, 0, 0, ZoneOffset.UTC),
                "Sent back for another look",
                Map.of()));
        return candidateId;
    }

    private String exportState(UUID progressUpdateId) {
        return jdbcTemplate().queryForObject(
                "SELECT export_state::text FROM task_progress_updates WHERE id = ?", String.class, progressUpdateId);
    }

    private UUID exportBatchId(UUID progressUpdateId) {
        return jdbcTemplate().queryForObject(
                "SELECT export_batch_id FROM task_progress_updates WHERE id = ?", UUID.class, progressUpdateId);
    }

    private ImportedTaskCreateRequest task(String uid, String name, boolean summary, String parentUid) {
        return new ImportedTaskCreateRequest(
                uid, uid, name, null, null, summary ? 0 : 1, summary, parentUid, null,
                null, null, null, null, null, null, null, Map.of());
    }

    private Actor actor(String email, String role) {
        UUID userId = fixtures.createUser(email, role);
        fixtures.grantMembership(projectId, userId, role);
        return new Actor(userId, role, role);
    }
}
