package com.shutdowntracker.api.journey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutdowntracker.api.exportpreview.handoff.ProjectExportArtifactJobClient;
import com.shutdowntracker.api.importbatch.handoff.ProjectParseJobClient;
import com.shutdowntracker.api.support.DatabaseFixtures;
import com.shutdowntracker.api.support.EmbeddedDatabase;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactGenerationRequest;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * The whole product, walked once, through the controllers, against a real database.
 *
 * <p>Every other test in this service proves one step. This one proves the joins between them, and
 * it exists because proving the steps was not enough: the export chain was severed for the entire
 * time its unit tests were green, behind four stacked defects that each lived <em>between</em> two
 * working steps. The console built its candidate list from an intersection that was empty by
 * construction, and no test noticed, because every test built its own fixture in a state the
 * endpoint it stood for could never return. That is the failure mode this test is shaped against:
 * each step here is fed only what the previous step actually returned, so a link that stops being
 * a legal input to the next one fails here rather than in a deployment.
 *
 * <p>Three identities walk it, not one. The chain is built so that two of them cannot be the same
 * person — a planner may not submit progress, and a supervisor may not approve an export — so a
 * single actor with every capability would walk a journey the product does not have. Actor
 * resolution is the real {@code TrustedHeaderActorResolver}, driven by the same headers a gateway
 * would set.
 *
 * <p>What is stubbed, and only this: the two declared client interfaces the API uses to reach
 * {@code services/project-worker}. See {@link StubProjectWorker}. Controllers, capability checks,
 * services, JDBC repositories, the migrations and PostgreSQL itself are all real.
 *
 * <p>This is not the manual Microsoft Project gate and does not stand in for it. Nothing here
 * recalculates anything; the candidate returned in the last step is the artifact downloaded two
 * steps earlier, which is why the run it produces is asserted to record both hashes rather than to
 * prove a round trip.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductJourneyTests {

    private static final String ACTOR_ID_HEADER = "X-Shutdown-Tracker-Actor-Id";
    private static final String ACTOR_ROLE_HEADER = "X-Shutdown-Tracker-Actor-Role";
    private static final String ACTOR_NAME_HEADER = "X-Shutdown-Tracker-Actor-Name";

    @TempDir
    static Path storageRoot;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectParseJobClient parseJobClient;

    @MockBean
    private ProjectExportArtifactJobClient exportArtifactJobClient;

    private DatabaseFixtures fixtures;
    private UUID projectId;
    private UUID plannerId;
    private UUID supervisorId;
    private UUID fieldUserId;

    /**
     * Points the application at the shared embedded server and at scratch storage roots.
     *
     * <p>The roots matter more than they look. Two of them were unset in the review deployment and
     * resolved under a directory the service user could not write to, so the first evidence or
     * candidate upload failed at request time while the health check stayed green. Giving each run
     * a real writable directory is what lets the download and return steps below be walked at all.
     */
    @DynamicPropertySource
    static void applicationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", EmbeddedDatabase::jdbcUrl);
        registry.add("spring.datasource.username", EmbeddedDatabase::username);
        registry.add("spring.datasource.password", EmbeddedDatabase::password);
        registry.add("spring.flyway.enabled", () -> false);
        registry.add("shutdown-tracker.persistence.enabled", () -> true);
        registry.add("shutdown-tracker.actor.trusted-header.enabled", () -> true);
        registry.add("shutdown-tracker.source-file-storage.local-root", () -> storageRoot.resolve("source-files"));
        registry.add("shutdown-tracker.evidence-storage.local-root", () -> storageRoot.resolve("evidence"));
        registry.add("shutdown-tracker.export-artifact-storage.local-root", () -> storageRoot.resolve("artifacts"));
        registry.add(
                "shutdown-tracker.candidate-schedule-storage.local-root",
                () -> storageRoot.resolve("candidates"));
    }

    @BeforeEach
    void seedTheThreePeople() {
        EmbeddedDatabase.reset();
        JdbcTemplate jdbcTemplate = EmbeddedDatabase.jdbcTemplate();
        fixtures = new DatabaseFixtures(jdbcTemplate);

        projectId = fixtures.createProject("Journey");
        plannerId = fixtures.createUser("planner@journey.invalid", "Journey Planner");
        supervisorId = fixtures.createUser("supervisor@journey.invalid", "Journey Supervisor");
        fieldUserId = fixtures.createUser("field@journey.invalid", "Journey Field User");
        fixtures.grantMembership(projectId, plannerId, "planner");
        fixtures.grantMembership(projectId, supervisorId, "supervisor");
        fixtures.grantMembership(projectId, fieldUserId, "field_user");

        when(parseJobClient.requestParseEntities(any()))
                .thenAnswer(call -> StubProjectWorker.parse(call.getArgument(0, ProjectParseSummaryRequest.class)));
        when(exportArtifactJobClient.generateArtifact(any()))
                .thenAnswer(call -> StubProjectWorker.generate(
                        call.getArgument(0, ProjectExportArtifactGenerationRequest.class)));
    }

    @Test
    @DisplayName("a field update reaches Microsoft Project and a candidate comes back, as three people")
    void theJourney() throws Exception {
        // 1. The planner brings a schedule in. One upload produces both the stored file and the
        //    pending import batch the parse step needs, so the batch id below is the server's.
        JsonNode upload = json(multipart("/api/projects/{projectId}/source-files", projectId)
                .file(new MockMultipartFile(
                        "file",
                        "synthetic-journey.mspdi.xml",
                        MediaType.APPLICATION_XML_VALUE,
                        "<?xml version=\"1.0\"?><Project><Name>Synthetic Shutdown</Name></Project>"
                                .getBytes(StandardCharsets.UTF_8)))
                .headers(as(plannerId, "planner", "Journey Planner")));
        assertThat(upload.path("accepted").asBoolean()).isTrue();
        assertThat(upload.path("importBatch").path("status").asText()).isEqualTo("PENDING");
        UUID importBatchId = uuid(upload.path("importBatch").path("id"));

        // 2. Parsing it stores a snapshot. The counts are the worker's, and the snapshot the
        //    review list returns is the one this batch produced.
        JsonNode parsed = json(post(
                "/api/projects/{projectId}/import-batches/{importBatchId}/request-parse-summary",
                projectId,
                importBatchId)
                .headers(as(plannerId, "planner", "Journey Planner")));
        assertThat(parsed.path("parseSummary").path("taskCount").asInt()).isEqualTo(3);
        assertThat(parsed.path("parseSummary").path("leafTaskCount").asInt()).isEqualTo(2);
        assertThat(parsed.path("importBatch").path("status").asText()).isEqualTo("PARSED");

        JsonNode snapshots = json(get("/api/projects/{projectId}/import-review/snapshots", projectId)
                .headers(as(plannerId, "planner", "Journey Planner")));
        assertThat(snapshots).hasSize(1);
        JsonNode snapshot = snapshots.get(0);
        assertThat(uuid(snapshot.path("importBatchId"))).isEqualTo(importBatchId);
        assertThat(snapshot.path("status").asText()).isEqualTo("PARSED");
        UUID snapshotId = uuid(snapshot.path("id"));

        // 3. Accepting it is what makes execution refer to this version. Every step after this
        //    reads the accepted snapshot, and V007 refuses an export built on any other.
        JsonNode accepted = json(post(
                "/api/projects/{projectId}/import-review/snapshots/{snapshotId}/accept", projectId, snapshotId)
                .headers(as(plannerId, "planner", "Journey Planner")));
        assertThat(accepted.path("snapshot").path("status").asText()).isEqualTo("ACCEPTED");

        // 4. Without a resource link the field user's work list is empty, and that is correct
        //    rather than broken. Asserting it before the link is what makes the link meaningful.
        JsonNode workBeforeLink = json(get("/api/projects/{projectId}/assigned-work", projectId)
                .headers(as(fieldUserId, "field_user", "Journey Field User")));
        assertThat(workBeforeLink.path("linked").asBoolean()).isFalse();
        assertThat(workBeforeLink.path("tasks")).isEmpty();

        json(post("/api/projects/{projectId}/resource-links", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "userId", fieldUserId.toString(),
                        "resourceExternalUid", StubProjectWorker.CREW_RESOURCE_UID)))
                .headers(as(plannerId, "planner", "Journey Planner")));

        // 5. Now the same request returns the two leaf tasks the crew is assigned, and only those.
        //    The summary task is absent, which is the boundary the export policy depends on.
        JsonNode work = json(get("/api/projects/{projectId}/assigned-work", projectId)
                .headers(as(fieldUserId, "field_user", "Journey Field User")));
        assertThat(work.path("linked").asBoolean()).isTrue();
        assertThat(work.path("tasks")).hasSize(2);
        JsonNode firstTask = work.path("tasks").get(0);
        assertThat(firstTask.path("summary").asBoolean()).isFalse();
        UUID importedTaskId = uuid(firstTask.path("id"));

        // 6. The field user reports against a task from their own list.
        JsonNode submitted = json(post("/api/projects/{projectId}/task-progress", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "importedTaskId", importedTaskId.toString(),
                        "executionState", "IN_PROGRESS",
                        "percentComplete", 40,
                        "actualStart", "2026-03-02T07:00:00Z",
                        "comment", "Isolation complete, stripping started.")))
                .headers(as(fieldUserId, "field_user", "Journey Field User")));
        UUID progressUpdateId = uuid(submitted.path("id"));
        assertThat(submitted.path("submittedByUserId").asText()).isEqualTo(fieldUserId.toString());
        assertThat(submitted.path("progressReviewState").asText()).isEqualTo("SUBMITTED");
        assertThat(submitted.path("exportState").asText()).isEqualTo("NOT_ELIGIBLE");

        // 7. It is in the supervisor's queue, and accepting it moves it to the planner's.
        JsonNode supervisorQueue = json(get("/api/projects/{projectId}/task-progress/supervisor-queue", projectId)
                .headers(as(supervisorId, "supervisor", "Journey Supervisor")));
        assertThat(ids(supervisorQueue)).containsExactly(progressUpdateId);

        JsonNode supervisorAccepted = json(post(
                "/api/projects/{projectId}/task-progress/{id}/supervisor-review", projectId, progressUpdateId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"SUPERVISOR_ACCEPTED\",\"note\":\"Matches the isolation permit.\"}")
                .headers(as(supervisorId, "supervisor", "Journey Supervisor")));
        assertThat(supervisorAccepted.path("progressReviewState").asText()).isEqualTo("SUPERVISOR_ACCEPTED");

        // 8. Planner approval is what makes it eligible to travel.
        JsonNode plannerQueue = json(get("/api/projects/{projectId}/task-progress/planner-queue", projectId)
                .headers(as(plannerId, "planner", "Journey Planner")));
        assertThat(ids(plannerQueue)).containsExactly(progressUpdateId);

        JsonNode plannerApproved = json(post(
                "/api/projects/{projectId}/task-progress/{id}/planner-review", projectId, progressUpdateId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"approved\":true,\"note\":\"Approved for the next candidate.\"}")
                .headers(as(plannerId, "planner", "Journey Planner")));
        assertThat(plannerApproved.path("plannerReviewState").asText()).isEqualTo("PLANNER_APPROVED");
        assertThat(plannerApproved.path("exportState").asText()).isEqualTo("ELIGIBLE");

        // 9. The export queue is the join that was severed. An update leaves the planner queue at
        //    the moment it becomes eligible, so a list built from the planner queue and filtered
        //    for approved updates was empty by construction. This asserts the queue that answers
        //    the question actually being asked, and that the update has left the planner's.
        assertThat(ids(json(get("/api/projects/{projectId}/task-progress/planner-queue", projectId)
                .headers(as(plannerId, "planner", "Journey Planner")))))
                .isEmpty();
        JsonNode exportQueue = json(get("/api/projects/{projectId}/task-progress/export-queue", projectId)
                .headers(as(plannerId, "planner", "Journey Planner")));
        assertThat(ids(exportQueue)).containsExactly(progressUpdateId);

        // 10. The console turns each approved update into one candidate per whitelisted field. Two
        //     fields were submitted, so two candidates: physical percent complete is deliberately
        //     never emitted, because the server can only mark such a line permanently ineligible.
        JsonNode queued = exportQueue.get(0);
        UUID percentCandidateId = createCandidate(
                snapshotId, importedTaskId, queued, "percent_complete", queued.path("percentComplete").asText());
        UUID startCandidateId = createCandidate(
                snapshotId, importedTaskId, queued, "actual_start", queued.path("actualStart").asText());

        // 11. Approving each candidate is a separate recorded fact. Creating a preview without it
        //     is refused, which is why this step exists rather than being folded into the next.
        recordApproval(percentCandidateId);
        recordApproval(startCandidateId);

        // 12. The preview is built from those candidate ids, and every line it carries is eligible.
        JsonNode preview = json(post("/api/projects/{projectId}/export-preview", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "projectSnapshotId", snapshotId.toString(),
                        "candidateIds", List.of(
                                percentCandidateId.toString(), startCandidateId.toString()))))
                .headers(as(plannerId, "planner", "Journey Planner")));
        UUID exportBatchId = uuid(preview.path("batch").path("id"));
        assertThat(preview.path("lines")).hasSize(2);
        for (JsonNode line : preview.path("lines")) {
            assertThat(line.path("exportEligible").asBoolean()).isTrue();
            assertThat(line.path("leafTask").asBoolean()).isTrue();
        }

        //     Creating it is also what binds the update to the batch. `export_batch_id` was carried
        //     unwritten from V009, so the audit could not say which batch had carried which
        //     approved field change, and the `export_batch_id IS NULL` clause in the export queue
        //     decided nothing. Two candidates were built from this one update, and it is claimed
        //     once — a shortfall against what the batch was built from is refused, not absorbed.
        assertThat(ids(json(get("/api/projects/{projectId}/task-progress/export-queue", projectId)
                .headers(as(plannerId, "planner", "Journey Planner")))))
                .describedAs("a claimed update cannot be swept into a second batch")
                .isEmpty();
        assertThat(exportStateOf(progressUpdateId)).isEqualTo("in_export_preview");
        assertThat(exportBatchIdOf(progressUpdateId)).isEqualTo(exportBatchId);

        // 13. Approving the batch, then generating the artifact from it.
        JsonNode approvedBatch = json(post(
                "/api/projects/{projectId}/export-preview/{exportBatchId}/approve", projectId, exportBatchId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Ready for Microsoft Project.\"}")
                .headers(as(plannerId, "planner", "Journey Planner")));
        assertThat(approvedBatch.path("batch").path("status").asText()).isEqualTo("APPROVED");

        JsonNode generated = json(post(
                "/api/projects/{projectId}/export-preview/{exportBatchId}/generate-artifact",
                projectId, exportBatchId)
                .headers(as(plannerId, "planner", "Journey Planner")));
        assertThat(generated.path("exportPreview").path("batch").path("status").asText())
                .isEqualTo("GENERATED");
        assertThat(generated.path("workerResponse").path("artifactSummary").path("exportedFieldCount").asInt())
                .isEqualTo(2);
        String generatedHash = generated.path("workerResponse").path("exportFileHash").asText();

        //     The artifact exists, so the update it carried has travelled. `export_batch_id` was
        //     carried unwritten from V009, so the audit could not say which batch carried which
        //     approved field change; this is that answer. Recorded at generation rather than at
        //     verification, because verification is the batch's fact and a generated batch nobody
        //     opens still carried this value.
        assertThat(exportStateOf(progressUpdateId)).isEqualTo("exported");
        assertThat(exportBatchIdOf(progressUpdateId)).isEqualTo(exportBatchId);

        // 14. Downloading it is the step that had no door. The bytes must be the ones generated,
        //     or the file a planner opens in Microsoft Project is not the one this batch approved.
        MvcResult download = mockMvc.perform(get(
                "/api/projects/{projectId}/export-preview/{exportBatchId}/artifact", projectId, exportBatchId)
                .headers(as(plannerId, "planner", "Journey Planner")))
                .andExpect(status().isOk())
                .andReturn();
        byte[] artifact = download.getResponse().getContentAsByteArray();
        assertThat(StubProjectWorker.sha256(artifact)).isEqualTo(generatedHash);
        assertThat(download.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains(exportBatchId + ".mspdi.xml");

        // 15. The open and the verification are recorded separately, and the batch reaches Verified.
        json(post(
                "/api/projects/{projectId}/export-preview/{exportBatchId}/mark-opened-in-microsoft-project",
                projectId, exportBatchId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Opened by the planner.\"}")
                .headers(as(plannerId, "planner", "Journey Planner")));
        JsonNode verified = json(post(
                "/api/projects/{projectId}/export-preview/{exportBatchId}/verify", projectId, exportBatchId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Opened as a complete schedule.\"}")
                .headers(as(plannerId, "planner", "Journey Planner")));
        assertThat(verified.path("batch").path("status").asText()).isEqualTo("VERIFIED");

        //     The update it carried is untouched by that. How far the batch got is the batch's own
        //     status, read through `export_batch_id`, and mirroring it onto the row would be the
        //     duplication V015 removed. The batch id stays set: which batch carried which field
        //     change outlives the batch finishing.
        assertThat(exportStateOf(progressUpdateId)).isEqualTo("exported");
        assertThat(exportBatchIdOf(progressUpdateId)).isEqualTo(exportBatchId);

        // 16. The candidate comes back, bound to the batch whose artifact it was calculated from.
        //     Nothing recalculated it here — this is the downloaded artifact handed straight back —
        //     so the run is asserted on the lineage it records, not on a schedule having changed.
        JsonNode run = json(multipart(
                "/api/projects/{projectId}/export-preview/{exportBatchId}/candidate-runs", projectId, exportBatchId)
                .file(new MockMultipartFile(
                        "file", "candidate.mspdi.xml", MediaType.APPLICATION_XML_VALUE, artifact))
                .param("microsoftProjectVersion", "Microsoft Project 2021")
                .param("plannerNote", "Returned unchanged by the journey test.")
                .headers(as(plannerId, "planner", "Journey Planner")));
        assertThat(uuid(run.path("exportBatchId"))).isEqualTo(exportBatchId);
        assertThat(uuid(run.path("projectSnapshotId"))).isEqualTo(snapshotId);
        assertThat(run.path("generatedArtifactHash").asText()).isEqualTo(generatedHash);
        assertThat(run.path("candidateContentHash").asText()).isEqualTo(generatedHash);
        assertThat(run.path("acceptedSourceFileHash").asText()).isNotBlank();
        assertThat(run.path("returnedByUserId").asText()).isEqualTo(plannerId.toString());

        // The chain holds end to end, and the record can answer which batch carried this update.
        JsonNode runsForBatch = json(get(
                "/api/projects/{projectId}/export-preview/{exportBatchId}/candidate-runs", projectId, exportBatchId)
                .headers(as(supervisorId, "supervisor", "Journey Supervisor")));
        assertThat(runsForBatch).hasSize(1);
    }

    @Test
    @DisplayName("no one person can walk both halves of the two-step review")
    void theSeparationHolds() throws Exception {
        // The journey is only meaningful as three people if the server actually refuses the other
        // two. These are the two crossings the review separation exists to prevent, asserted at the
        // server rather than at the interface that hides the controls.
        mockMvc.perform(post("/api/projects/{projectId}/task-progress", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"importedTaskId\":\"" + UUID.randomUUID()
                                + "\",\"executionState\":\"IN_PROGRESS\"}")
                        .headers(as(plannerId, "planner", "Journey Planner")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(
                        "/api/projects/{projectId}/export-preview/{exportBatchId}/approve",
                        projectId, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .headers(as(supervisorId, "supervisor", "Journey Supervisor")))
                .andExpect(status().isForbidden());

        // And a request carrying no actor at all is refused before it reaches any of them.
        mockMvc.perform(get("/api/projects/{projectId}/task-progress/export-queue", projectId))
                .andExpect(status().isUnauthorized());
    }

    private UUID createCandidate(
            UUID snapshotId, UUID importedTaskId, JsonNode queued, String fieldName, String proposedValue)
            throws Exception {
        JsonNode candidate = json(post("/api/projects/{projectId}/export-candidates", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "projectSnapshotId", snapshotId.toString(),
                        "importedTaskId", importedTaskId.toString(),
                        "fieldName", fieldName,
                        "proposedValue", proposedValue,
                        "sourceEntityType", "task_progress_update",
                        "sourceEntityId", queued.path("id").asText(),
                        "sourceVersion", queued.path("id").asText())))
                .headers(as(plannerId, "planner", "Journey Planner")));
        assertThat(candidate.path("fieldName").asText()).isEqualTo(fieldName);
        return uuid(candidate.path("id"));
    }

    private void recordApproval(UUID candidateId) throws Exception {
        JsonNode approval = json(post(
                "/api/projects/{projectId}/export-candidates/{candidateId}/approval-events",
                projectId, candidateId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"approvalState\":\"APPROVED_FOR_EXPORT\",\"reason\":\"Planner approved for export.\"}")
                .headers(as(plannerId, "planner", "Journey Planner")));
        // The reviewing identity is the resolved actor, never one the request named.
        assertThat(approval.path("reviewedByUserId").asText()).isEqualTo(plannerId.toString());
    }

    /** The headers a gateway sets. Who the request is from is never in the body. */
    private HttpHeaders as(UUID userId, String role, String displayName) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ACTOR_ID_HEADER, userId.toString());
        headers.set(ACTOR_ROLE_HEADER, role);
        headers.set(ACTOR_NAME_HEADER, displayName);
        return headers;
    }

    private JsonNode json(RequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static UUID uuid(JsonNode node) {
        return UUID.fromString(node.asText());
    }

    private static List<UUID> ids(JsonNode array) {
        List<UUID> ids = new ArrayList<>();
        array.forEach(node -> ids.add(uuid(node.path("id"))));
        return ids;
    }

    /**
     * Read from the column rather than from a response, because no endpoint returns it.
     *
     * <p>A claimed update has left every queue by design, so the only place the walk can ask what
     * became of it is the row itself. That is the fact the audit needs and the reason these two
     * columns exist.
     */
    private static String exportStateOf(UUID progressUpdateId) {
        return EmbeddedDatabase.jdbcTemplate().queryForObject(
                "SELECT export_state::text FROM task_progress_updates WHERE id = ?", String.class, progressUpdateId);
    }

    private static UUID exportBatchIdOf(UUID progressUpdateId) {
        return EmbeddedDatabase.jdbcTemplate().queryForObject(
                "SELECT export_batch_id FROM task_progress_updates WHERE id = ?", UUID.class, progressUpdateId);
    }
}
