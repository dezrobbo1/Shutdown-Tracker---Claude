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
                                        task("2", "Remove guard", false, "1")),
                                List.of(), List.of(), List.of())));

        snapshotId = fixtures.acceptNewestSnapshot(projectId);
        leafTaskId = jdbcTemplate().queryForObject(
                "SELECT id FROM imported_tasks WHERE external_uid = ?", UUID.class, "2");

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
        TaskProgressUpdateRecord approved = eligibleUpdate();

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

    @Test
    void aVerifiedBatchRecordsThatItsUpdatesTravelledAndKeepsTheBatchId() {
        TaskProgressUpdateRecord approved = eligibleUpdate();
        UUID batchId = verifiedBatch(approved);

        assertThat(exportState(approved.id())).isEqualTo("exported");
        assertThat(exportBatchId(approved.id()))
                .describedAs("which batch carried which field change outlives the batch finishing")
                .isEqualTo(batchId);
        assertThat(progressService.exportQueue(projectId)).isEmpty();
    }

    @Test
    void anExportedUpdateIsTerminalAndCannotBeReleasedBack() {
        TaskProgressUpdateRecord approved = eligibleUpdate();
        UUID batchId = verifiedBatch(approved);

        int released = new JdbcTaskProgressRepository(new NamedParameterJdbcTemplate(dataSource()))
                .releaseFromExportBatch(batchId);

        assertThat(released)
                .describedAs("release returns claimed updates, not ones that already travelled")
                .isZero();
        assertThat(exportState(approved.id())).isEqualTo("exported");
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

    // ------------------------------------------------------------------ fixture

    /** Submitted, accepted by a supervisor, approved by a planner — the only way to reach eligible. */
    private TaskProgressUpdateRecord eligibleUpdate() {
        TaskProgressUpdateRecord submitted = progressService.submit(projectId, fieldUser, new TaskProgressSubmitRequest(
                leafTaskId, TaskExecutionState.IN_PROGRESS, new BigDecimal("50"),
                OffsetDateTime.of(2026, 8, 1, 6, 0, 0, 0, ZoneOffset.UTC),
                null, null, "Started on shift.", null, null, null));
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

    private UUID verifiedBatch(TaskProgressUpdateRecord update) {
        UUID batchId = previewFor(update).batch().id();
        previewService.approveBatch(
                projectId, batchId, new ExportBatchDecisionRequest(planner.userId(), "Ready to send.", Map.of()));
        previewService.markGenerated(projectId, batchId, new ExportBatchGeneratedRequest(
                "file:///artifacts/" + batchId + ".mspdi.xml", "c".repeat(64), planner.userId(), "Generated.", Map.of()));
        previewService.markOpenedInMicrosoftProject(projectId, batchId, new ExportBatchProjectOpenRequest(
                planner.userId(), "Opened by the planner.", Map.of()));
        previewService.verifyBatch(projectId, batchId, new ExportBatchVerificationRequest(
                planner.userId(), "Opened as a complete schedule.", Map.of()));
        return batchId;
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
