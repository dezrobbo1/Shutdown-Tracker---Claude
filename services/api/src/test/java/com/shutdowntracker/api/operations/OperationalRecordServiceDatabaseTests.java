package com.shutdowntracker.api.operations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import com.shutdowntracker.api.operations.storage.EvidenceStorageProperties;
import com.shutdowntracker.api.operations.storage.LocalEvidenceStorage;
import com.shutdowntracker.api.support.AbstractDatabaseTest;
import com.shutdowntracker.api.support.DatabaseFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationalRecordServiceDatabaseTests extends AbstractDatabaseTest {

    private OperationalRecordService service;
    private DatabaseFixtures fixtures;
    private UUID projectId;
    private UUID taskId;
    private Actor supervisor;
    private Actor fieldUser;
    private EvidenceStorageProperties evidenceStorageProperties;

    @TempDir
    private Path evidenceRoot;

    @BeforeEach
    void setUp() {
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(dataSource());
        evidenceStorageProperties = new EvidenceStorageProperties(evidenceRoot, 1_048_576L);
        service = new OperationalRecordService(
                new JdbcOperationalRecordRepository(named),
                new JdbcAuditEventRecorder(named, new ObjectMapper()),
                new LocalEvidenceStorage(evidenceStorageProperties),
                evidenceStorageProperties);
        fixtures = new DatabaseFixtures(jdbcTemplate());

        DatabaseFixtures.ImportChain chain = fixtures.createImportChain("Kiln Shutdown");
        projectId = chain.projectId();

        new ImportedProjectPersistenceService(new JdbcImportedProjectRepository(named, new ObjectMapper()))
                .persistParsedSnapshot(new ImportedProjectSnapshotCreateRequest(
                        projectId, chain.importBatchId(), "PROJ-1", "Kiln", null, Map.of(),
                        new ImportedProjectEntities(
                                List.of(new ImportedTaskCreateRequest(
                                        "1", "1", "Remove guard", null, null, 1, false, null, null,
                                        null, null, null, null, null, null, null, Map.of())),
                                List.of(), List.of(), List.of())));

        taskId = jdbcTemplate().queryForObject(
                "SELECT id FROM imported_tasks WHERE external_uid = '1'", UUID.class);

        supervisor = actor("supervisor@example.com", "supervisor");
        fieldUser = actor("field@example.com", "field_user");
    }

    @Test
    void raisesAProblemAgainstATask() {
        ProblemRecord problem = service.raiseProblem(projectId, fieldUser, new ProblemCreateRequest(
                taskId, "Scaffold missing", "Cannot reach the valve.", ProblemSeverity.HIGH, true));

        assertThat(problem.status()).isEqualTo(ProblemStatus.OPEN);
        assertThat(problem.severity()).isEqualTo(ProblemSeverity.HIGH);
        assertThat(problem.blocksExecution()).isTrue();
        assertThat(problem.raisedByUserId()).isEqualTo(fieldUser.userId());
    }

    @Test
    void assigningMovesTheProblemOutOfOpen() {
        ProblemRecord problem = service.raiseProblem(projectId, fieldUser, problemRequest());

        ProblemRecord assigned = service.assignProblem(projectId, supervisor, problem.id(), supervisor.userId());

        assertThat(assigned.status()).isEqualTo(ProblemStatus.ASSIGNED);
        assertThat(assigned.assignedToUserId()).isEqualTo(supervisor.userId());
    }

    @Test
    void closingRecordsWhoResolvedItAndWhen() {
        ProblemRecord problem = service.raiseProblem(projectId, fieldUser, problemRequest());

        ProblemRecord closed = service.closeProblem(projectId, supervisor, problem.id(), "Scaffold erected.");

        assertThat(closed.status()).isEqualTo(ProblemStatus.CLOSED);
        assertThat(closed.resolvedByUserId()).isEqualTo(supervisor.userId());
        assertThat(closed.resolvedAt()).isNotNull();
    }

    @Test
    void aClosedProblemCannotBeClosedAgainOrReassigned() {
        ProblemRecord problem = service.raiseProblem(projectId, fieldUser, problemRequest());
        service.closeProblem(projectId, supervisor, problem.id(), "Done.");

        assertThatThrownBy(() -> service.closeProblem(projectId, supervisor, problem.id(), "Again"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() ->
                service.assignProblem(projectId, supervisor, problem.id(), fieldUser.userId()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void theDatabaseRefusesAClosedProblemWithNoResolver() {
        // The service always attributes closure; this asserts the constraint holds even if
        // a future caller writes directly.
        assertThatThrownBy(() -> jdbcTemplate().update(
                """
                INSERT INTO problems (project_id, title, status, raised_by_user_id)
                VALUES (?, 'Orphan closure', CAST('closed' AS problem_status), ?)
                """,
                projectId, fieldUser.userId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void openProblemQueueExcludesClosedOnes() {
        ProblemRecord first = service.raiseProblem(projectId, fieldUser, problemRequest());
        service.raiseProblem(projectId, fieldUser, problemRequest());
        service.closeProblem(projectId, supervisor, first.id(), "Resolved.");

        assertThat(service.openProblems(projectId)).hasSize(1);
    }

    @Test
    void createsAnActionAgainstAProblemAndCompletesIt() {
        ProblemRecord problem = service.raiseProblem(projectId, fieldUser, problemRequest());

        ActionRecord action = service.createAction(projectId, supervisor, new ActionCreateRequest(
                problem.id(), taskId, "Order scaffold", null, supervisor.userId(), null));

        assertThat(action.status())
                .describedAs("an action created with an assignee starts assigned, not open")
                .isEqualTo(ActionStatus.ASSIGNED);

        ActionRecord completed = service.completeAction(projectId, supervisor, action.id());
        assertThat(completed.status()).isEqualTo(ActionStatus.COMPLETED);
        assertThat(completed.completedByUserId()).isEqualTo(supervisor.userId());
        assertThat(service.openActions(projectId)).isEmpty();
    }

    @Test
    void anActionCannotBeCompletedTwice() {
        ActionRecord action = service.createAction(projectId, supervisor,
                new ActionCreateRequest(null, taskId, "Check valve", null, null, null));
        service.completeAction(projectId, supervisor, action.id());

        assertThatThrownBy(() -> service.completeAction(projectId, supervisor, action.id()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void evidenceWithoutAStorageLocationStaysPendingUpload() {
        EvidenceRecord evidence = service.registerEvidence(projectId, fieldUser, new EvidenceCreateRequest(
                taskId, null, null, null, "guard-removed.jpg", "image/jpeg", null, 2048L, "Guard removed."));

        assertThat(evidence.status()).isEqualTo(EvidenceStatus.PENDING_UPLOAD);
    }

    @Test
    void evidenceWithAStorageLocationIsUploaded() {
        EvidenceRecord evidence = service.registerEvidence(projectId, fieldUser, new EvidenceCreateRequest(
                taskId, null, null, null, "guard-removed.jpg", "image/jpeg",
                "s3://evidence/guard-removed.jpg", 2048L, null));

        assertThat(evidence.status()).isEqualTo(EvidenceStatus.UPLOADED);
        assertThat(service.evidenceForTask(projectId, taskId)).hasSize(1);
    }

    @Test
    void uploadingAFileCompletesAPendingEvidenceRecord() throws IOException {
        EvidenceRecord registered = registerPendingEvidence();

        EvidenceRecord uploaded = service.uploadEvidenceContent(
                projectId, fieldUser, registered.id(), photo("guard-removed.jpg", "front guard off"));

        assertThat(uploaded.status()).isEqualTo(EvidenceStatus.UPLOADED);
        assertThat(uploaded.sizeBytes()).isEqualTo("front guard off".length());
        assertThat(uploaded.contentType()).isEqualTo("image/jpeg");
        assertThat(uploaded.storageUri()).isNotNull();
        // Evidence is a verification artifact: what was stored has to be identifiable later.
        assertThat(uploaded.contentHash()).hasSize(64);

        // The record is only true if the bytes are actually there and are the ones sent.
        try (var content = service.readEvidenceContent(projectId, registered.id()).content()) {
            assertThat(new String(content.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("front guard off");
        }
    }

    /**
     * Evidence is an assertion about what was seen. Overwriting the file behind an accepted record
     * would change what a past reviewer looked at, which is what supersession exists for.
     */
    @Test
    void uploadingOverAlreadyUploadedEvidenceIsRefused() {
        EvidenceRecord registered = registerPendingEvidence();
        service.uploadEvidenceContent(projectId, fieldUser, registered.id(), photo("guard-removed.jpg", "first"));

        assertThatThrownBy(() -> service.uploadEvidenceContent(
                projectId, fieldUser, registered.id(), photo("guard-removed.jpg", "second")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already has its file");
    }

    @Test
    void uploadingAFileOfADifferentSizeThanRegisteredIsRefused() {
        EvidenceRecord registered = service.registerEvidence(projectId, fieldUser, new EvidenceCreateRequest(
                taskId, null, null, null, "guard-removed.jpg", "image/jpeg", null, 99L, null));

        assertThatThrownBy(() -> service.uploadEvidenceContent(
                projectId, fieldUser, registered.id(), photo("guard-removed.jpg", "not ninety-nine bytes")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("registered as 99 bytes");
    }

    @Test
    void uploadingAnEmptyFileIsRefused() {
        EvidenceRecord registered = registerPendingEvidence();

        assertThatThrownBy(() -> service.uploadEvidenceContent(
                projectId, fieldUser, registered.id(), photo("guard-removed.jpg", "")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void uploadingToAnotherProjectsEvidenceIsNotFound() {
        EvidenceRecord registered = registerPendingEvidence();
        UUID otherProjectId = fixtures.createImportChain("Stack Shutdown").projectId();

        assertThatThrownBy(() -> service.uploadEvidenceContent(
                otherProjectId, fieldUser, registered.id(), photo("guard-removed.jpg", "bytes")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    /**
     * A registered record whose file never arrived reads as absent rather than as a broken read:
     * the two are different states and the field user needs to know which one they are in.
     */
    @Test
    void readingEvidenceThatWasNeverUploadedReportsItAsNotUploaded() {
        EvidenceRecord registered = registerPendingEvidence();

        assertThatThrownBy(() -> service.readEvidenceContent(projectId, registered.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("has not been uploaded");
    }

    /**
     * {@code storage_uri} is a database column. A row naming a file outside the configured root
     * must not turn the evidence endpoint into a way to read it.
     */
    @Test
    void readingEvidenceStoredOutsideTheConfiguredRootIsRefused() throws IOException {
        Path outside = Files.createTempFile("outside-evidence", ".txt");
        Files.writeString(outside, "not evidence");
        EvidenceRecord registered = service.registerEvidence(projectId, fieldUser, new EvidenceCreateRequest(
                taskId, null, null, null, "guard-removed.jpg", "image/jpeg",
                outside.toUri().toString(), null, null));

        try {
            assertThatThrownBy(() -> service.readEvidenceContent(projectId, registered.id()))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("could not be read");
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    private EvidenceRecord registerPendingEvidence() {
        return service.registerEvidence(projectId, fieldUser, new EvidenceCreateRequest(
                taskId, null, null, null, "guard-removed.jpg", "image/jpeg", null, null, "Guard removed."));
    }

    private MockMultipartFile photo(String filename, String content) {
        return new MockMultipartFile("file", filename, "image/jpeg", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void evidenceMustReferenceSomething() {
        assertThatThrownBy(() -> new EvidenceCreateRequest(
                null, null, null, null, "orphan.jpg", "image/jpeg", null, 1L, null))
                .describedAs("evidence has to be evidence of something")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handoverNotesTrackAcknowledgement() {
        HandoverNoteRecord note = service.createHandoverNote(projectId, supervisor,
                new HandoverNoteCreateRequest(taskId, null, "Night shift", "Valve left isolated.", true));

        assertThat(service.unacknowledgedHandoverNotes(projectId)).hasSize(1);

        HandoverNoteRecord acknowledged =
                service.acknowledgeHandoverNote(projectId, fieldUser, note.id());

        assertThat(acknowledged.acknowledgedByUserId()).isEqualTo(fieldUser.userId());
        assertThat(acknowledged.acknowledgedAt()).isNotNull();
        assertThat(service.unacknowledgedHandoverNotes(projectId)).isEmpty();
    }

    @Test
    void aNoteThatNeedsNoAcknowledgementIsNotInTheQueue() {
        service.createHandoverNote(projectId, supervisor,
                new HandoverNoteCreateRequest(null, null, "Day shift", "Routine handover.", false));

        assertThat(service.unacknowledgedHandoverNotes(projectId)).isEmpty();
    }

    @Test
    void everyOperationIsAttributedInTheAuditTrail() {
        service.raiseProblem(projectId, fieldUser, problemRequest());

        Map<String, Object> event = jdbcTemplate().queryForMap(
                "SELECT event_type, actor_user_id, actor_type::text FROM audit_events");

        assertThat(event.get("event_type")).isEqualTo("problem.raised");
        assertThat(event.get("actor_user_id")).isEqualTo(fieldUser.userId());
        assertThat(event.get("actor_type")).isEqualTo("user");
    }

    private ProblemCreateRequest problemRequest() {
        return new ProblemCreateRequest(taskId, "Scaffold missing", null, ProblemSeverity.HIGH, true);
    }

    private Actor actor(String email, String role) {
        UUID userId = fixtures.createUser(email, role);
        fixtures.grantMembership(projectId, userId, role);
        return new Actor(userId, role, role);
    }
}
