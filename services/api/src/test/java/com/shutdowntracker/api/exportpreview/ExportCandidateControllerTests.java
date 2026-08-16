package com.shutdowntracker.api.exportpreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shutdowntracker.api.ApiStrictJsonConfiguration;
import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.actor.ActorResolver;
import com.shutdowntracker.api.actor.ActorWebMvcConfiguration;
import com.shutdowntracker.api.identity.Capability;
import com.shutdowntracker.api.identity.ProjectAuthorizationService;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(ExportCandidateController.class)
@TestPropertySource(properties = "shutdown-tracker.persistence.enabled=true")
@Import({
    ApiStrictJsonConfiguration.class,
    ActorWebMvcConfiguration.class,
    ExportCandidateControllerTests.StubActorConfiguration.class
})
class ExportCandidateControllerTests {

    private static final Actor ACTOR =
            new Actor(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "planner", "Synthetic Planner");

    private static final UUID FORGED_REVIEWER = UUID.fromString("99999999-9999-9999-9999-999999999999");

    /** Controller slice tests assert routing and delegation; header parsing is covered by the resolver tests. */
    @TestConfiguration
    static class StubActorConfiguration {

        @Bean
        ActorResolver actorResolver() {
            return request -> ACTOR;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExportCandidateService service;

    /**
     * Authorisation itself is covered by ProjectAuthorizationServiceTests against the real
     * membership store; here it is mocked so a slice test can assert the controller consults it.
     */
    @MockBean
    private ProjectAuthorizationService authorization;

    @Test
    void createsAuthoritativeCandidateWithoutImplyingApproval() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        ExportCandidateRecord response = candidate(projectId, snapshotId, taskId, sourceId);
        when(service.createCandidate(eq(projectId), any(ExportCandidateCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/projects/{projectId}/export-candidates", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(candidateJson(snapshotId, taskId, sourceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.normalizedNewValue").value("75"))
                .andExpect(jsonPath("$.sourceEventOrPayloadHash").value("a".repeat(64)));
    }

    @Test
    void rejectsUnknownCandidateProperties() throws Exception {
        UUID projectId = UUID.randomUUID();
        String body = candidateJson(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
                .replace("\"metadata\": {}", "\"untrustedApproval\": true, \"metadata\": {} ");

        mockMvc.perform(post("/api/projects/{projectId}/export-candidates", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void rejectsDuplicateCandidateProperties() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        String body = candidateJson(snapshotId, UUID.randomUUID(), UUID.randomUUID())
                .replace(
                        "\"projectSnapshotId\": \"" + snapshotId + "\"",
                        "\"projectSnapshotId\": \"" + snapshotId + "\", "
                                + "\"projectSnapshotId\": \"" + snapshotId + "\""
                );

        mockMvc.perform(post("/api/projects/{projectId}/export-candidates", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void rejectsNumericApprovalStateAliases() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();

        mockMvc.perform(post(
                        "/api/projects/{projectId}/export-candidates/{candidateId}/approval-events",
                        projectId,
                        candidateId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approvalState": 1,
                                  "reason": "Numeric enum aliases are not authority"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void recordsCandidateBoundApprovalEvent() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        ExportCandidateApprovalEventRecord response = approvalEvent(projectId, candidateId);
        when(service.recordApprovalEvent(
                eq(projectId),
                eq(candidateId),
                any(ExportCandidateApprovalEventRequest.class)
        )).thenReturn(response);

        mockMvc.perform(post(
                        "/api/projects/{projectId}/export-candidates/{candidateId}/approval-events",
                        projectId,
                        candidateId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approvalState": "APPROVED_FOR_EXPORT",
                                  "requestedAt": "2026-01-01T07:00:00Z",
                                  "reviewedAt": "2026-01-01T08:00:00Z",
                                  "reason": "Synthetic planner approval"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authoritativeExportCandidateId").value(candidateId.toString()))
                .andExpect(jsonPath("$.approvalState").value("APPROVED_FOR_EXPORT"));
    }

    @Test
    void recordsTheResolvedActorAsReviewerEvenWhenTheBodyClaimsSomeoneElse() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        when(service.recordApprovalEvent(
                        eq(projectId), eq(candidateId), any(ExportCandidateApprovalEventRequest.class)))
                .thenReturn(approvalEvent(projectId, candidateId));

        // Naming a different reviewer is the impersonation this endpoint must refuse to honour.
        mockMvc.perform(post(
                                "/api/projects/{projectId}/export-candidates/{candidateId}/approval-events",
                                projectId,
                                candidateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approvalState": "APPROVED_FOR_EXPORT",
                                  "reviewedByUserId": "99999999-9999-9999-9999-999999999999",
                                  "reason": "Synthetic approval"
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<ExportCandidateApprovalEventRequest> captured =
                ArgumentCaptor.forClass(ExportCandidateApprovalEventRequest.class);
        verify(service).recordApprovalEvent(eq(projectId), eq(candidateId), captured.capture());
        assertThat(captured.getValue().reviewedByUserId()).isEqualTo(ACTOR.userId());
        assertThat(captured.getValue().reviewedByUserId()).isNotEqualTo(FORGED_REVIEWER);
        assertThat(captured.getValue().approvalState()).isEqualTo(ApprovalState.APPROVED_FOR_EXPORT);
        assertThat(captured.getValue().reason()).isEqualTo("Synthetic approval");
    }

    /** A body that names nobody is well formed; the actor still reaches the service. */
    @Test
    void recordsTheResolvedActorWhenTheBodyNamesNobody() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        when(service.recordApprovalEvent(
                        eq(projectId), eq(candidateId), any(ExportCandidateApprovalEventRequest.class)))
                .thenReturn(approvalEvent(projectId, candidateId));

        mockMvc.perform(post(
                                "/api/projects/{projectId}/export-candidates/{candidateId}/approval-events",
                                projectId,
                                candidateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approvalState": "APPROVED_FOR_EXPORT"
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<ExportCandidateApprovalEventRequest> captured =
                ArgumentCaptor.forClass(ExportCandidateApprovalEventRequest.class);
        verify(service).recordApprovalEvent(eq(projectId), eq(candidateId), captured.capture());
        assertThat(captured.getValue().reviewedByUserId()).isEqualTo(ACTOR.userId());
    }

    @Test
    void refusesToRecordAnApprovalWhenTheActorLacksTheCapability() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Role supervisor may not record approvals."))
                .when(authorization)
                .requireCapability(projectId, ACTOR, Capability.RECORD_APPROVAL);

        mockMvc.perform(post(
                                "/api/projects/{projectId}/export-candidates/{candidateId}/approval-events",
                                projectId,
                                candidateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approvalState": "APPROVED_FOR_EXPORT"
                                }
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void refusesToCreateACandidateWhenTheActorLacksTheCapability() throws Exception {
        UUID projectId = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Role supervisor may not preview exports."))
                .when(authorization)
                .requireCapability(projectId, ACTOR, Capability.CREATE_EXPORT_PREVIEW);

        mockMvc.perform(post("/api/projects/{projectId}/export-candidates", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(candidateJson(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    private ExportCandidateApprovalEventRecord approvalEvent(UUID projectId, UUID candidateId) {
        return new ExportCandidateApprovalEventRecord(
                UUID.randomUUID(),
                projectId,
                UUID.randomUUID(),
                candidateId,
                ExportIntegrityPolicy.CURRENT_VERSION,
                ApprovalState.APPROVED_FOR_EXPORT,
                null,
                OffsetDateTime.parse("2026-01-01T07:00:00Z"),
                ACTOR.userId(),
                OffsetDateTime.parse("2026-01-01T08:00:00Z"),
                "Synthetic planner approval",
                OffsetDateTime.parse("2026-01-01T08:00:01Z"),
                Map.of()
        );
    }

    private String candidateJson(UUID snapshotId, UUID taskId, UUID sourceId) {
        return """
                {
                  "projectSnapshotId": "%s",
                  "importedTaskId": "%s",
                  "fieldName": "percent_complete",
                  "proposedValue": "075.0",
                  "sourceEntityType": "task_update",
                  "sourceEntityId": "%s",
                  "sourceVersion": "synthetic-source-version-1",
                  "sourceTimestamp": "2026-01-01T07:00:00Z",
                  "reason": "Synthetic reason",
                  "metadata": {}
                }
                """.formatted(snapshotId, taskId, sourceId);
    }

    private ExportCandidateRecord candidate(UUID projectId, UUID snapshotId, UUID taskId, UUID sourceId) {
        return new ExportCandidateRecord(
                UUID.randomUUID(),
                ExportIntegrityPolicy.CURRENT_VERSION,
                projectId,
                snapshotId,
                taskId,
                "task_update",
                sourceId,
                "synthetic-source-version-1",
                "percent_complete",
                "25",
                "75",
                "a".repeat(64),
                "101",
                "1",
                "Synthetic Task A1",
                true,
                null,
                OffsetDateTime.parse("2026-01-01T07:00:00Z"),
                "Synthetic reason",
                OffsetDateTime.parse("2026-01-01T07:00:01Z"),
                Map.of()
        );
    }
}
