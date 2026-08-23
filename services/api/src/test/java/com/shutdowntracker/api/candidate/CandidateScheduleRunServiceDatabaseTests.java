package com.shutdowntracker.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.audit.JdbcAuditEventRecorder;
import com.shutdowntracker.api.candidate.storage.CandidateScheduleStorageProperties;
import com.shutdowntracker.api.candidate.storage.LocalCandidateScheduleStorage;
import com.shutdowntracker.api.exportpreview.ApprovalState;
import com.shutdowntracker.api.exportpreview.ExportBatchDecisionRequest;
import com.shutdowntracker.api.exportpreview.ExportBatchGeneratedRequest;
import com.shutdowntracker.api.exportpreview.ExportCandidateApprovalEventRequest;
import com.shutdowntracker.api.exportpreview.ExportCandidateCreateRequest;
import com.shutdowntracker.api.exportpreview.ExportCandidateRecord;
import com.shutdowntracker.api.exportpreview.ExportCandidateService;
import com.shutdowntracker.api.exportpreview.ExportPreviewCreateRequest;
import com.shutdowntracker.api.exportpreview.ExportPreviewDetail;
import com.shutdowntracker.api.exportpreview.ExportPreviewService;
import com.shutdowntracker.api.exportpreview.JdbcExportPreviewRepository;
import com.shutdowntracker.api.identity.ProjectRole;
import com.shutdowntracker.api.support.AbstractDatabaseTest;
import com.shutdowntracker.api.support.RecordingExportBatchProgressBinding;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Bringing back the schedule Microsoft Project calculated.
 *
 * <p>The export batch these run against is built the only way one can be: a candidate, an approval,
 * a preview, an approved batch and a generated artifact, through the real services and the real
 * export-integrity triggers. A hand-inserted batch would not be a batch Microsoft Project could
 * have been handed, and a test that returned a candidate against one would be proving nothing.
 */
class CandidateScheduleRunServiceDatabaseTests extends AbstractDatabaseTest {

    private static final UUID PROJECT_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_FILE_ID = UUID.fromString("40000000-0000-0000-0000-000000000002");
    private static final UUID IMPORT_BATCH_ID = UUID.fromString("40000000-0000-0000-0000-000000000003");
    private static final UUID SNAPSHOT_ID = UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final UUID TASK_ID = UUID.fromString("40000000-0000-0000-0000-000000000005");
    private static final UUID SOURCE_ENTITY_ID = UUID.fromString("40000000-0000-0000-0000-000000000006");
    private static final UUID PLANNER_ID = UUID.fromString("40000000-0000-0000-0000-000000000007");
    /** A second real user, so an attempt to rewrite an established actor names somebody who exists. */
    private static final UUID OTHER_PLANNER_ID = UUID.fromString("40000000-0000-0000-0000-000000000008");

    private static final String SOURCE_HASH = "b".repeat(64);
    private static final String ARTIFACT_HASH = "c".repeat(64);

    /** Small, but a real MSPDI root element: the upload check reads the first element and no more. */
    private static final String CANDIDATE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Project xmlns="http://schemas.microsoft.com/project">
              <Name>Recalculated candidate</Name>
              <Tasks><Task><UID>401</UID><PercentComplete>25</PercentComplete></Task></Tasks>
            </Project>
            """;

    @TempDir
    private Path storageRoot;

    private CandidateScheduleRunService service;
    private JdbcCandidateScheduleRunRepository repository;
    private ExportCandidateService candidateService;
    private ExportPreviewService previewService;
    private NamedParameterJdbcTemplate named;
    private Actor planner;

    @BeforeEach
    void setUp() {
        named = new NamedParameterJdbcTemplate(dataSource());
        ObjectMapper objectMapper = new ObjectMapper();
        JdbcAuditEventRecorder auditEventRecorder = new JdbcAuditEventRecorder(named, objectMapper);
        JdbcExportPreviewRepository exportRepository = new JdbcExportPreviewRepository(named, objectMapper);

        repository = new JdbcCandidateScheduleRunRepository(named);
        candidateService = new ExportCandidateService(exportRepository, auditEventRecorder);
        previewService = new ExportPreviewService(exportRepository, auditEventRecorder, new RecordingExportBatchProgressBinding());
        service = new CandidateScheduleRunService(
                repository,
                new LocalCandidateScheduleStorage(
                        new CandidateScheduleStorageProperties(storageRoot, 1_048_576L)),
                new CandidateScheduleStorageProperties(storageRoot, 1_048_576L),
                auditEventRecorder);

        seedProjectAndPlanner();
        planner = new Actor(PLANNER_ID, ProjectRole.PLANNER.databaseValue(), "Planner");
    }

    // ------------------------------------------------------------------ returning

    @Test
    void recordsWhatCameBackAgainstWhatWentOutAndWhatItCameFrom() {
        UUID batchId = generatedBatch();

        CandidateScheduleRunRecord run = service.returnCandidate(
                PROJECT_ID, batchId, planner, candidateFile("recalculated.xml"), "Project 2021 16.0.14332", null);

        assertThat(run.state()).isEqualTo(CandidateScheduleRunState.RETURNED);
        assertThat(run.exportBatchId()).isEqualTo(batchId);
        assertThat(run.projectSnapshotId()).isEqualTo(SNAPSHOT_ID);
        assertThat(run.acceptedSourceFileId()).isEqualTo(SOURCE_FILE_ID);

        // The three identities a review has to be able to show, and cannot reconstruct later.
        assertThat(run.acceptedSourceFileHash()).isEqualTo(SOURCE_HASH);
        assertThat(run.generatedArtifactHash()).isEqualTo(ARTIFACT_HASH);
        assertThat(run.candidateContentHash())
                .isNotEqualTo(SOURCE_HASH)
                .isNotEqualTo(ARTIFACT_HASH)
                .hasSize(64);

        assertThat(run.microsoftProjectVersion()).isEqualTo("Project 2021 16.0.14332");
        assertThat(run.returnedByUserId()).isEqualTo(PLANNER_ID);
        assertThat(run.candidateSizeBytes()).isEqualTo(CANDIDATE_XML.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void readsTheReturnedScheduleBackByteForByte() throws Exception {
        UUID batchId = generatedBatch();
        CandidateScheduleRunRecord run = service.returnCandidate(
                PROJECT_ID, batchId, planner, candidateFile("recalculated.xml"), null, null);

        CandidateScheduleRunService.CandidateScheduleContent content = service.content(PROJECT_ID, run.id());
        assertThat(content.run().id()).isEqualTo(run.id());
        try (InputStream bytes = content.content()) {
            assertThat(new String(bytes.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(CANDIDATE_XML);
        }
    }

    @Test
    void auditsTheReturnAsAnExportEventThatClaimsNoDeltaAndNoDecision() {
        UUID batchId = generatedBatch();
        CandidateScheduleRunRecord run = service.returnCandidate(
                PROJECT_ID, batchId, planner, candidateFile("recalculated.xml"), null, null);

        Map<String, Object> event = jdbcTemplate().queryForMap(
                """
                SELECT event_category::text AS event_category,
                       event_type,
                       target_entity_type,
                       target_entity_id,
                       actor_user_id,
                       export_batch_id,
                       metadata ->> 'deltaComputed' AS delta_computed,
                       metadata ->> 'plannerDecision' AS planner_decision,
                       metadata ->> 'masterAdopted' AS master_adopted,
                       metadata ->> 'candidateContentHash' AS candidate_content_hash
                FROM audit_events
                WHERE event_type = 'candidate_schedule_returned'
                """);

        assertThat(event.get("event_category")).isEqualTo("export");
        assertThat(event.get("target_entity_type")).isEqualTo("candidate_schedule_run");
        assertThat(event.get("target_entity_id")).isEqualTo(run.id());
        assertThat(event.get("actor_user_id")).isEqualTo(PLANNER_ID);
        assertThat(event.get("export_batch_id")).isEqualTo(batchId);
        assertThat(event.get("candidate_content_hash")).isEqualTo(run.candidateContentHash());
        // The whole point of the three flags: this row records a calculation coming back and
        // nothing else. A reader must not be able to take it for a delta or a decision.
        assertThat(event.get("delta_computed")).isEqualTo("false");
        assertThat(event.get("planner_decision")).isEqualTo("false");
        assertThat(event.get("master_adopted")).isEqualTo("false");
    }

    @Test
    void returningTheSameFileAgainResolvesToTheRunItAlreadyMadeAndIsNotAuditedTwice() {
        UUID batchId = generatedBatch();

        CandidateScheduleRunRecord first = service.returnCandidate(
                PROJECT_ID, batchId, planner, candidateFile("recalculated.xml"), null, null);
        CandidateScheduleRunRecord replay = service.returnCandidate(
                PROJECT_ID, batchId, planner, candidateFile("recalculated-again.xml"), null, "Second attempt");

        assertThat(replay.id()).isEqualTo(first.id());
        // Not the filename or note of the replay: the first upload is what happened.
        assertThat(replay.candidateOriginalFilename()).isEqualTo("recalculated.xml");
        assertThat(replay.plannerNote()).isNull();

        assertThat(countRuns()).isEqualTo(1);
        assertThat(jdbcTemplate().queryForObject(
                "SELECT count(*) FROM audit_events WHERE event_type = 'candidate_schedule_returned'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void aDifferentCalculationFromTheSameBatchIsASecondRun() {
        UUID batchId = generatedBatch();
        service.returnCandidate(PROJECT_ID, batchId, planner, candidateFile("first.xml"), null, null);

        CandidateScheduleRunRecord second = service.returnCandidate(
                PROJECT_ID,
                batchId,
                planner,
                new MockMultipartFile(
                        "file",
                        "second.xml",
                        "application/xml",
                        CANDIDATE_XML.replace("25", "50").getBytes(StandardCharsets.UTF_8)),
                null,
                "Recalculated after correcting the calendar");

        assertThat(countRuns()).isEqualTo(2);
        assertThat(service.runsForExportBatch(PROJECT_ID, batchId)).hasSize(2);
        assertThat(second.plannerNote()).isEqualTo("Recalculated after correcting the calendar");
    }

    // ------------------------------------------------------------------ refusals

    @Test
    void refusesACandidateAgainstABatchThatNeverGeneratedAnArtifact() {
        UUID batchId = approvedBatch();

        assertThatThrownBy(() -> service.returnCandidate(
                PROJECT_ID, batchId, planner, candidateFile("recalculated.xml"), null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).contains("never handed a candidate schedule");
                });

        assertThat(countRuns()).isZero();
    }

    @Test
    void refusesAFileThatIsNotAProjectDocumentWithoutStoringIt() {
        UUID batchId = generatedBatch();

        assertThatThrownBy(() -> service.returnCandidate(
                PROJECT_ID,
                batchId,
                planner,
                new MockMultipartFile("file", "notes.xml", "application/xml",
                        "<Notes><Note>not a schedule</Note></Notes>".getBytes(StandardCharsets.UTF_8)),
                null,
                null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).contains("root element is not <Project>");
                });

        assertThat(countRuns()).isZero();
        assertThat(storageRoot).isEmptyDirectory();
    }

    @Test
    void refusesAFileThatIsNotXmlAtAll() {
        UUID batchId = generatedBatch();

        assertThatThrownBy(() -> service.returnCandidate(
                PROJECT_ID,
                batchId,
                planner,
                new MockMultipartFile("file", "candidate.mpp", "application/octet-stream",
                        new byte[] {0x00, 0x01, 0x02}),
                null,
                null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(storageRoot).isEmptyDirectory();
    }

    @Test
    void refusesACandidateWhenTheAcceptedSourceHasNoRecordedHash() {
        UUID batchId = generatedBatch();
        jdbcTemplate().update("UPDATE source_files SET content_hash = NULL WHERE id = ?", SOURCE_FILE_ID);

        assertThatThrownBy(() -> service.returnCandidate(
                PROJECT_ID, batchId, planner, candidateFile("recalculated.xml"), null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).contains("no recorded content hash");
                });
    }

    @Test
    void refusesACandidateAgainstABatchInAnotherProject() {
        UUID batchId = generatedBatch();
        UUID otherProjectId = UUID.fromString("40000000-0000-0000-0000-0000000000ff");
        jdbcTemplate().update(
                "INSERT INTO projects (id, name, timezone) VALUES (?, 'Other shutdown', 'UTC')", otherProjectId);

        assertThatThrownBy(() -> service.returnCandidate(
                otherProjectId, batchId, planner, candidateFile("recalculated.xml"), null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ------------------------------------------------------------------ database invariants

    @Test
    void whatARunReturnedCannotBeEditedAfterwards() {
        UUID runId = returnedRun();

        assertThatThrownBy(() -> jdbcTemplate().update(
                "UPDATE candidate_schedule_runs SET candidate_content_hash = ? WHERE id = ?", "d".repeat(64), runId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("is immutable");

        assertThatThrownBy(() -> jdbcTemplate().update(
                "UPDATE candidate_schedule_runs SET accepted_source_file_hash = ? WHERE id = ?", "e".repeat(64), runId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("is immutable");

        assertThatThrownBy(() -> jdbcTemplate().update(
                "UPDATE candidate_schedule_runs SET returned_by_user_id = ? WHERE id = ?", OTHER_PLANNER_ID, runId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("is immutable");

        assertThatThrownBy(() -> jdbcTemplate().update(
                "UPDATE candidate_schedule_runs SET candidate_original_filename = ? WHERE id = ?",
                "renamed.xml", runId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aCandidateRunCannotBeDeleted() {
        UUID runId = returnedRun();

        assertThatThrownBy(() -> jdbcTemplate().update("DELETE FROM candidate_schedule_runs WHERE id = ?", runId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("cannot be deleted");
    }

    /**
     * The rule the handoff contract states: a planner decision is bound to one candidate hash and
     * one semantic delta. Accepting a schedule nothing has compared is the unreviewed adoption the
     * authority model exists to prevent, so the database refuses it rather than trusting a caller.
     */
    @Test
    void aCandidateCannotBeAcceptedBeforeAnythingHasComparedIt() {
        UUID runId = returnedRun();

        assertThatThrownBy(() -> setState(runId, "accepted"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("cannot move from returned to accepted");

        setState(runId, "delta_ready");
        setState(runId, "accepted");

        assertThat(state(runId)).isEqualTo("accepted");
    }

    @Test
    void anAcceptedCandidateStaysAccepted() {
        UUID runId = returnedRun();
        setState(runId, "delta_ready");
        setState(runId, "accepted");

        assertThatThrownBy(() -> setState(runId, "rejected"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("cannot move from accepted to rejected");
    }

    @Test
    void aRunCannotBeInsertedAlreadyDecided() {
        UUID batchId = generatedBatch();

        assertThatThrownBy(() -> jdbcTemplate().update(
                """
                INSERT INTO candidate_schedule_runs (
                    project_id, export_batch_id, project_snapshot_id, accepted_source_file_id,
                    accepted_source_file_hash, state, candidate_original_filename, candidate_storage_uri,
                    candidate_content_hash, candidate_size_bytes, returned_by_user_id
                ) VALUES (?, ?, ?, ?, ?, CAST('accepted' AS candidate_schedule_run_state),
                          'planted.xml', 'file:///planted.xml', ?, 10, ?)
                """,
                PROJECT_ID, batchId, SNAPSHOT_ID, SOURCE_FILE_ID, SOURCE_HASH, "f".repeat(64), PLANNER_ID))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("begins when a candidate is returned");
    }

    @Test
    void theSameBytesCannotBeRecordedTwiceAgainstOneBatch() {
        UUID batchId = generatedBatch();
        CandidateScheduleRunRecord run = service.returnCandidate(
                PROJECT_ID, batchId, planner, candidateFile("recalculated.xml"), null, null);

        assertThatThrownBy(() -> jdbcTemplate().update(
                """
                INSERT INTO candidate_schedule_runs (
                    project_id, export_batch_id, project_snapshot_id, accepted_source_file_id,
                    accepted_source_file_hash, candidate_original_filename, candidate_storage_uri,
                    candidate_content_hash, candidate_size_bytes, returned_by_user_id
                ) VALUES (?, ?, ?, ?, ?, 'duplicate.xml', 'file:///duplicate.xml', ?, 10, ?)
                """,
                PROJECT_ID, batchId, SNAPSHOT_ID, SOURCE_FILE_ID, SOURCE_HASH,
                run.candidateContentHash(), PLANNER_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------ fixture

    private UUID returnedRun() {
        return service.returnCandidate(
                PROJECT_ID, generatedBatch(), planner, candidateFile("recalculated.xml"), null, null).id();
    }

    private UUID generatedBatch() {
        UUID batchId = approvedBatch();
        previewService.markGenerated(
                PROJECT_ID,
                batchId,
                new ExportBatchGeneratedRequest(
                        "file:///artifacts/" + batchId + ".mspdi.xml",
                        ARTIFACT_HASH,
                        PLANNER_ID,
                        "Generated for the candidate return tests",
                        Map.of()));
        return batchId;
    }

    private UUID approvedBatch() {
        ExportCandidateRecord candidate = candidateService.createCandidate(
                PROJECT_ID,
                new ExportCandidateCreateRequest(
                        SNAPSHOT_ID,
                        TASK_ID,
                        "percent_complete",
                        "25",
                        "synthetic_task_update",
                        SOURCE_ENTITY_ID,
                        "source-v1",
                        PLANNER_ID,
                        OffsetDateTime.parse("2026-08-20T06:00:00+08:00"),
                        "Synthetic accepted leaf progress",
                        Map.of("fixture", "candidate-return")));

        candidateService.recordApprovalEvent(
                PROJECT_ID,
                candidate.id(),
                new ExportCandidateApprovalEventRequest(
                        ApprovalState.APPROVED_FOR_EXPORT,
                        OffsetDateTime.parse("2026-08-20T06:01:00+08:00"),
                        PLANNER_ID,
                        OffsetDateTime.parse("2026-08-20T06:02:00+08:00"),
                        "Synthetic candidate review",
                        Map.of()));

        ExportPreviewDetail preview = previewService.createPreview(
                PROJECT_ID, new ExportPreviewCreateRequest(SNAPSHOT_ID, List.of(candidate.id()), Map.of()));

        return previewService.approveBatch(
                        PROJECT_ID,
                        preview.batch().id(),
                        new ExportBatchDecisionRequest(PLANNER_ID, "Approved exact candidate", Map.of()))
                .batch()
                .id();
    }

    private MockMultipartFile candidateFile(String filename) {
        return new MockMultipartFile(
                "file", filename, "application/xml", CANDIDATE_XML.getBytes(StandardCharsets.UTF_8));
    }

    private void setState(UUID runId, String state) {
        jdbcTemplate().update(
                "UPDATE candidate_schedule_runs SET state = CAST(? AS candidate_schedule_run_state) WHERE id = ?",
                state, runId);
    }

    private String state(UUID runId) {
        return jdbcTemplate().queryForObject(
                "SELECT state::text FROM candidate_schedule_runs WHERE id = ?", String.class, runId);
    }

    private int countRuns() {
        return jdbcTemplate().queryForObject("SELECT count(*) FROM candidate_schedule_runs", Integer.class);
    }

    private void seedProjectAndPlanner() {
        jdbcTemplate().update("""
                INSERT INTO users (id, email, display_name, status)
                VALUES (?, 'planner@candidate.invalid', 'Planner', CAST('active' AS user_status))
                """, PLANNER_ID);
        jdbcTemplate().update("""
                INSERT INTO users (id, email, display_name, status)
                VALUES (?, 'other-planner@candidate.invalid', 'Other planner', CAST('active' AS user_status))
                """, OTHER_PLANNER_ID);
        jdbcTemplate().update(
                "INSERT INTO projects (id, name, timezone) VALUES (?, 'Candidate return project', 'Australia/Perth')",
                PROJECT_ID);
        jdbcTemplate().update("""
                INSERT INTO source_files (id, project_id, original_filename, file_kind, storage_uri, content_hash)
                VALUES (?, ?, 'accepted-source.xml', 'mspdi_xml', 'file:///sources/accepted-source.xml', ?)
                """, SOURCE_FILE_ID, PROJECT_ID, SOURCE_HASH);
        jdbcTemplate().update("""
                INSERT INTO import_batches (id, project_id, source_file_id, status, parser_name, parser_version)
                VALUES (?, ?, ?, 'accepted', 'candidate-return-test', '1')
                """, IMPORT_BATCH_ID, PROJECT_ID, SOURCE_FILE_ID);
        jdbcTemplate().update("""
                INSERT INTO project_snapshots (
                    id, project_id, import_batch_id, status, external_project_uid,
                    external_project_name, snapshot_version, accepted_at
                )
                VALUES (?, ?, ?, 'accepted', '9901', 'Synthetic accepted schedule', 1, now())
                """, SNAPSHOT_ID, PROJECT_ID, IMPORT_BATCH_ID);
        jdbcTemplate().update("""
                INSERT INTO imported_tasks (
                    id, project_id, project_snapshot_id, external_uid, external_id,
                    name, is_summary, percent_complete, physical_percent_complete
                )
                VALUES (?, ?, ?, '401', '41', 'Replace liner plate', false, 10, 10)
                """, TASK_ID, PROJECT_ID, SNAPSHOT_ID);
    }
}
