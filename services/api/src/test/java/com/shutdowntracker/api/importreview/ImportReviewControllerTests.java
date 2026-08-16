package com.shutdowntracker.api.importreview;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shutdowntracker.api.importedproject.ImportedExtendedAttributeEntityType;
import com.shutdowntracker.api.importedproject.ProjectSnapshotStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.shutdowntracker.api.actor.ActorWebMvcConfiguration;
import com.shutdowntracker.api.actor.StubActorConfiguration;
import com.shutdowntracker.api.identity.Capability;
import com.shutdowntracker.api.identity.ProjectAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(ImportReviewController.class)
@Import({ActorWebMvcConfiguration.class, StubActorConfiguration.class})
@TestPropertySource(properties = "shutdown-tracker.persistence.enabled=true")
class ImportReviewControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ImportReviewService service;

    /**
     * Authorisation itself is covered by ProjectAuthorizationServiceTests against the real
     * membership store; here it is mocked so a slice test can assert the controller consults it.
     */
    @MockBean
    private ProjectAuthorizationService authorization;

    @Test
    void listsSnapshotsForProjectReview() throws Exception {
        UUID projectId = UUID.randomUUID();
        ImportReviewSnapshotSummary snapshot = snapshot(projectId, UUID.randomUUID(), ProjectSnapshotStatus.PARSED);
        when(service.listSnapshots(projectId)).thenReturn(List.of(snapshot));

        mockMvc.perform(get("/api/projects/{projectId}/import-review/snapshots", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(snapshot.id().toString()))
                .andExpect(jsonPath("$[0].status").value("PARSED"))
                .andExpect(jsonPath("$[0].taskCount").value(2))
                .andExpect(jsonPath("$[0].summaryTaskCount").value(1))
                .andExpect(jsonPath("$[0].leafTaskCount").value(1));

        verify(service).listSnapshots(projectId);
    }

    @Test
    void returnsSnapshotDetailForReview() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        when(service.getSnapshot(projectId, snapshotId)).thenReturn(detail(projectId, snapshotId));

        mockMvc.perform(get("/api/projects/{projectId}/import-review/snapshots/{snapshotId}", projectId, snapshotId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.id").value(snapshotId.toString()))
                .andExpect(jsonPath("$.tasks[0].name").value("Synthetic Summary"))
                .andExpect(jsonPath("$.tasks[0].summary").value(true))
                .andExpect(jsonPath("$.resources[0].name").value("Synthetic Resource"))
                .andExpect(jsonPath("$.assignments[0].taskExternalUid").value("SYN-TASK-1"))
                .andExpect(jsonPath("$.extendedAttributes[0].entityType").value("TASK"));

        verify(service).getSnapshot(projectId, snapshotId);
    }

    @Test
    void acceptsSnapshotForReviewWithoutWriteBack() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        ImportReviewDecisionResponse response = new ImportReviewDecisionResponse(
                snapshot(projectId, snapshotId, ProjectSnapshotStatus.ACCEPTED),
                "Snapshot accepted for Shutdown Tracker review use only. No Microsoft Project file was written back."
        );
        when(service.acceptSnapshot(projectId, snapshotId)).thenReturn(response);

        mockMvc.perform(post("/api/projects/{projectId}/import-review/snapshots/{snapshotId}/accept", projectId, snapshotId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.message").value(response.message()));

        verify(service).acceptSnapshot(projectId, snapshotId);
    }

    @Test
    void rejectsSnapshotForReviewWithoutWriteBack() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        ImportReviewDecisionResponse response = new ImportReviewDecisionResponse(
                snapshot(projectId, snapshotId, ProjectSnapshotStatus.REJECTED),
                "Snapshot rejected for Shutdown Tracker review use only. No Microsoft Project file was written back."
        );
        when(service.rejectSnapshot(projectId, snapshotId)).thenReturn(response);

        mockMvc.perform(post("/api/projects/{projectId}/import-review/snapshots/{snapshotId}/reject", projectId, snapshotId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.status").value("REJECTED"))
                .andExpect(jsonPath("$.message").value(response.message()));

        verify(service).rejectSnapshot(projectId, snapshotId);
    }

    private ImportReviewSnapshotDetail detail(UUID projectId, UUID snapshotId) {
        return new ImportReviewSnapshotDetail(
                snapshot(projectId, snapshotId, ProjectSnapshotStatus.PARSED),
                List.of(new ImportReviewTaskRow(
                        UUID.randomUUID(),
                        "SYN-SUMMARY-1",
                        "1",
                        "Synthetic Summary",
                        "1",
                        "1",
                        1,
                        true,
                        null,
                        null,
                        OffsetDateTime.parse("2026-01-01T08:00:00Z"),
                        OffsetDateTime.parse("2026-01-01T09:00:00Z"),
                        null,
                        null,
                        BigDecimal.ZERO,
                        null,
                        null
                )),
                List.of(new ImportReviewResourceRow(UUID.randomUUID(), "SYN-RES-1", "Synthetic Resource", "work")),
                List.of(new ImportReviewAssignmentRow(
                        UUID.randomUUID(),
                        "SYN-ASSIGN-1",
                        "SYN-TASK-1",
                        "SYN-RES-1",
                        null,
                        null
                )),
                List.of(new ImportReviewExtendedAttributeRow(
                        UUID.randomUUID(),
                        ImportedExtendedAttributeEntityType.TASK,
                        "SYN-TASK-1",
                        "TEXT1",
                        "Text1",
                        "Synthetic Field",
                        "Synthetic Value"
                ))
        );
    }

    private ImportReviewSnapshotSummary snapshot(UUID projectId, UUID snapshotId, ProjectSnapshotStatus status) {
        return new ImportReviewSnapshotSummary(
                snapshotId,
                projectId,
                UUID.randomUUID(),
                status,
                "SYNTHETIC-PROJECT-1",
                "Synthetic Basic WBS",
                OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                1,
                "mpxj",
                "16.4.0",
                0,
                0,
                2,
                1,
                1,
                1,
                1,
                1
        );
    }

    /** Accepting a snapshot admits a schedule into everything downstream, so it is planner-gated. */
    @Test
    void refusesToAcceptASnapshotWhenTheActorLacksTheCapability() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Role supervisor may not accept a snapshot."))
                .when(authorization)
                .requireCapability(projectId, StubActorConfiguration.ACTOR, Capability.ACCEPT_IMPORT_SNAPSHOT);

        mockMvc.perform(post(
                        "/api/projects/{projectId}/import-review/snapshots/{snapshotId}/accept", projectId, snapshotId))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void refusesToListSnapshotsWithoutProjectVisibility() throws Exception {
        UUID projectId = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this project."))
                .when(authorization)
                .requireCapability(projectId, StubActorConfiguration.ACTOR, Capability.VIEW_PROJECT);

        mockMvc.perform(get("/api/projects/{projectId}/import-review/snapshots", projectId))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

}
