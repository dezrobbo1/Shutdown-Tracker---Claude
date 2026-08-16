package com.shutdowntracker.api.exportpreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.actor.ActorResolver;
import com.shutdowntracker.api.actor.ActorWebMvcConfiguration;
import com.shutdowntracker.api.identity.Capability;
import com.shutdowntracker.api.identity.ProjectAuthorizationService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExportPreviewController.class)
@TestPropertySource(properties = "shutdown-tracker.persistence.enabled=true")
@Import({ActorWebMvcConfiguration.class, ExportPreviewControllerTests.StubActorConfiguration.class})
class ExportPreviewControllerTests {

    private static final Actor ACTOR =
            new Actor(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "planner", "Synthetic Planner");

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
    private ExportPreviewService service;

    /**
     * Authorisation itself is covered by ProjectAuthorizationServiceTests against the real
     * database. These slice tests assert routing and delegation, so the check is stubbed
     * to allow; the wiring is asserted in {@link #refusesWhenTheActorLacksTheCapability()}.
     */
    @MockBean
    private ProjectAuthorizationService authorization;

    @Test
    void createsExportPreviewOnly() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        ExportPreviewDetail detail = detail(projectId, snapshotId, true);
        when(service.createPreview(eq(projectId), any(ExportPreviewCreateRequest.class))).thenReturn(detail);

        // A preview selects already-accepted candidates by id; it cannot describe a change inline.
        String body = """
                {
                  "projectSnapshotId": "%s",
                  "candidateIds": ["%s"],
                  "metadata": {
                    "source": "synthetic-export-preview"
                  }
                }
                """.formatted(snapshotId, candidateId);

        mockMvc.perform(post("/api/projects/{projectId}/export-preview", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batch.status").value("DRAFT_PREVIEW"))
                .andExpect(jsonPath("$.batch.eligibleLineCount").value(1))
                .andExpect(jsonPath("$.lines[0].fieldName").value("percent_complete"))
                .andExpect(jsonPath("$.lines[0].exportEligible").value(true))
                .andExpect(jsonPath("$.message").value(detail.message()));

        verify(service).createPreview(eq(projectId), any(ExportPreviewCreateRequest.class));
    }

    @Test
    void returnsExportPreviewById() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID exportBatchId = UUID.randomUUID();
        ExportPreviewDetail detail = detail(projectId, UUID.randomUUID(), false);
        when(service.getPreview(projectId, exportBatchId)).thenReturn(detail);

        mockMvc.perform(get("/api/projects/{projectId}/export-preview/{exportBatchId}", projectId, exportBatchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batch.id").value(detail.batch().id().toString()))
                .andExpect(jsonPath("$.batch.ineligibleLineCount").value(1))
                .andExpect(jsonPath("$.lines[0].approvalState").value("AWAITING_REVIEW"))
                .andExpect(jsonPath("$.lines[0].exportEligible").value(false))
                .andExpect(jsonPath("$.message").value(detail.message()));

        verify(service).getPreview(projectId, exportBatchId);
    }

    @Test
    void approvesExportBatch() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID exportBatchId = UUID.randomUUID();
        ExportPreviewDetail detail = detail(projectId, UUID.randomUUID(), true, ExportBatchState.APPROVED);
        when(service.approveBatch(eq(projectId), eq(exportBatchId), any(ExportBatchDecisionRequest.class)))
                .thenReturn(detail);

        mockMvc.perform(post("/api/projects/{projectId}/export-preview/{exportBatchId}/approve", projectId, exportBatchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Synthetic approval"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batch.status").value("APPROVED"))
                .andExpect(jsonPath("$.message").value(detail.message()));

        verify(service).approveBatch(eq(projectId), eq(exportBatchId), any(ExportBatchDecisionRequest.class));
    }

    @Test
    void rejectsExportBatch() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID exportBatchId = UUID.randomUUID();
        ExportPreviewDetail detail = detail(projectId, UUID.randomUUID(), false, ExportBatchState.REJECTED);
        when(service.rejectBatch(eq(projectId), eq(exportBatchId), any(ExportBatchDecisionRequest.class)))
                .thenReturn(detail);

        mockMvc.perform(post("/api/projects/{projectId}/export-preview/{exportBatchId}/reject", projectId, exportBatchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Synthetic rejection"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batch.status").value("REJECTED"))
                .andExpect(jsonPath("$.message").value(detail.message()));

        verify(service).rejectBatch(eq(projectId), eq(exportBatchId), any(ExportBatchDecisionRequest.class));
    }

    @Test
    void marksExportBatchGeneratedFromArtifactMetadata() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID exportBatchId = UUID.randomUUID();
        ExportPreviewDetail detail = detail(projectId, UUID.randomUUID(), true, ExportBatchState.GENERATED);
        when(service.markGenerated(eq(projectId), eq(exportBatchId), any(ExportBatchGeneratedRequest.class)))
                .thenReturn(detail);

        mockMvc.perform(post("/api/projects/{projectId}/export-preview/{exportBatchId}/mark-generated", projectId, exportBatchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "exportFileUri": "object://synthetic/export-batches/export-1.mspdi.xml",
                                  "exportFileHash": "sha256:synthetic",
                                  "reason": "Synthetic worker artifact recorded"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batch.status").value("GENERATED"))
                .andExpect(jsonPath("$.batch.exportFileUri").value("object://synthetic/export-batches/export-1.mspdi.xml"))
                .andExpect(jsonPath("$.batch.exportFileHash").value("sha256:synthetic"))
                .andExpect(jsonPath("$.message").value(detail.message()));

        verify(service).markGenerated(eq(projectId), eq(exportBatchId), any(ExportBatchGeneratedRequest.class));
    }

    @Test
    void marksExportBatchOpenedInMicrosoftProject() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID exportBatchId = UUID.randomUUID();
        ExportPreviewDetail detail = detail(
                projectId,
                UUID.randomUUID(),
                true,
                ExportBatchState.OPENED_IN_MICROSOFT_PROJECT
        );
        when(service.markOpenedInMicrosoftProject(
                eq(projectId),
                eq(exportBatchId),
                any(ExportBatchProjectOpenRequest.class)
        )).thenReturn(detail);

        mockMvc.perform(post(
                        "/api/projects/{projectId}/export-preview/{exportBatchId}/mark-opened-in-microsoft-project",
                        projectId,
                        exportBatchId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "openedByUserId": "11111111-1111-1111-1111-111111111111",
                                  "reason": "Synthetic Microsoft Project reopen"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batch.status").value("OPENED_IN_MICROSOFT_PROJECT"))
                .andExpect(jsonPath("$.batch.exportFileUri").value("object://synthetic/export-batches/export-1.mspdi.xml"))
                .andExpect(jsonPath("$.batch.exportFileHash").value("sha256:synthetic"))
                .andExpect(jsonPath("$.message").value(detail.message()));

        verify(service).markOpenedInMicrosoftProject(
                eq(projectId),
                eq(exportBatchId),
                any(ExportBatchProjectOpenRequest.class)
        );
    }

    @Test
    void verifiesExportBatchAfterMicrosoftProjectOpen() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID exportBatchId = UUID.randomUUID();
        ExportPreviewDetail detail = detail(projectId, UUID.randomUUID(), true, ExportBatchState.VERIFIED);
        when(service.verifyBatch(eq(projectId), eq(exportBatchId), any(ExportBatchVerificationRequest.class)))
                .thenReturn(detail);

        mockMvc.perform(post("/api/projects/{projectId}/export-preview/{exportBatchId}/verify", projectId, exportBatchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "verifiedByUserId": "22222222-2222-2222-2222-222222222222",
                                  "reason": "Synthetic manual verification complete"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batch.status").value("VERIFIED"))
                .andExpect(jsonPath("$.batch.verifiedAt").value("2026-01-01T03:00:00Z"))
                .andExpect(jsonPath("$.batch.verifiedByUserId").value("22222222-2222-2222-2222-222222222222"))
                .andExpect(jsonPath("$.message").value(detail.message()));

        verify(service).verifyBatch(eq(projectId), eq(exportBatchId), any(ExportBatchVerificationRequest.class));
    }

    @Test
    void recordsTheResolvedActorAsApproverEvenWhenTheBodyClaimsSomeoneElse() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID exportBatchId = UUID.randomUUID();
        ExportPreviewDetail detail = detail(projectId, UUID.randomUUID(), true, ExportBatchState.APPROVED);
        when(service.approveBatch(eq(projectId), eq(exportBatchId), any(ExportBatchDecisionRequest.class)))
                .thenReturn(detail);

        // A caller asserting a different approver is the impersonation this endpoint must refuse to honour.
        mockMvc.perform(post("/api/projects/{projectId}/export-preview/{id}/approve", projectId, exportBatchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewedByUserId": "99999999-9999-9999-9999-999999999999",
                                  "reason": "Synthetic approval"
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<ExportBatchDecisionRequest> captured =
                ArgumentCaptor.forClass(ExportBatchDecisionRequest.class);
        verify(service).approveBatch(eq(projectId), eq(exportBatchId), captured.capture());
        assertThat(captured.getValue().reviewedByUserId()).isEqualTo(ACTOR.userId());
        assertThat(captured.getValue().reason()).isEqualTo("Synthetic approval");
    }

    @Test
    void recordsTheResolvedActorAsTheMicrosoftProjectOpenerEvenWhenTheBodyClaimsSomeoneElse() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID exportBatchId = UUID.randomUUID();
        ExportPreviewDetail detail =
                detail(projectId, UUID.randomUUID(), true, ExportBatchState.OPENED_IN_MICROSOFT_PROJECT);
        when(service.markOpenedInMicrosoftProject(
                        eq(projectId), eq(exportBatchId), any(ExportBatchProjectOpenRequest.class)))
                .thenReturn(detail);

        mockMvc.perform(post(
                                "/api/projects/{projectId}/export-preview/{id}/mark-opened-in-microsoft-project",
                                projectId,
                                exportBatchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "openedByUserId": "99999999-9999-9999-9999-999999999999",
                                  "reason": "Synthetic reopen"
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<ExportBatchProjectOpenRequest> captured =
                ArgumentCaptor.forClass(ExportBatchProjectOpenRequest.class);
        verify(service).markOpenedInMicrosoftProject(eq(projectId), eq(exportBatchId), captured.capture());
        assertThat(captured.getValue().openedByUserId()).isEqualTo(ACTOR.userId());
        assertThat(captured.getValue().reason()).isEqualTo("Synthetic reopen");
    }

    /** A body that names nobody is well formed; the actor still reaches the service. */
    @Test
    void recordsTheResolvedActorAsVerifierWhenTheBodyNamesNobody() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID exportBatchId = UUID.randomUUID();
        ExportPreviewDetail detail = detail(projectId, UUID.randomUUID(), true, ExportBatchState.VERIFIED);
        when(service.verifyBatch(eq(projectId), eq(exportBatchId), any(ExportBatchVerificationRequest.class)))
                .thenReturn(detail);

        mockMvc.perform(post("/api/projects/{projectId}/export-preview/{id}/verify", projectId, exportBatchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Checked every line in Microsoft Project"
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<ExportBatchVerificationRequest> captured =
                ArgumentCaptor.forClass(ExportBatchVerificationRequest.class);
        verify(service).verifyBatch(eq(projectId), eq(exportBatchId), captured.capture());
        assertThat(captured.getValue().verifiedByUserId()).isEqualTo(ACTOR.userId());
    }

    @Test
    void refusesWhenTheActorLacksTheCapability() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID exportBatchId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "Role supervisor may not approve."))
                .when(authorization)
                .requireCapability(projectId, ACTOR, Capability.APPROVE_EXPORT_BATCH);

        mockMvc.perform(post("/api/projects/{projectId}/export-preview/{id}/approve", projectId, exportBatchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        // The approval must not reach the service when authorisation refuses.
        verify(service, org.mockito.Mockito.never())
                .approveBatch(any(), any(), any());
    }

    private ExportPreviewDetail detail(UUID projectId, UUID snapshotId, boolean eligible) {
        return detail(projectId, snapshotId, eligible, ExportBatchState.DRAFT_PREVIEW);
    }

    private ExportPreviewDetail detail(
            UUID projectId,
            UUID snapshotId,
            boolean eligible,
            ExportBatchState status
    ) {
        UUID exportBatchId = UUID.randomUUID();
        ExportPreviewBatchRecord batch = new ExportPreviewBatchRecord(
                exportBatchId,
                projectId,
                snapshotId,
                status,
                OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                status == ExportBatchState.APPROVED
                        || status == ExportBatchState.GENERATED
                        || status == ExportBatchState.OPENED_IN_MICROSOFT_PROJECT
                        || status == ExportBatchState.VERIFIED
                        ? OffsetDateTime.parse("2026-01-01T01:00:00Z")
                        : null,
                null,
                status == ExportBatchState.GENERATED
                        || status == ExportBatchState.OPENED_IN_MICROSOFT_PROJECT
                        || status == ExportBatchState.VERIFIED
                        ? OffsetDateTime.parse("2026-01-01T02:00:00Z")
                        : null,
                null,
                status == ExportBatchState.VERIFIED
                        ? OffsetDateTime.parse("2026-01-01T03:00:00Z")
                        : null,
                status == ExportBatchState.VERIFIED
                        ? UUID.fromString("22222222-2222-2222-2222-222222222222")
                        : null,
                status == ExportBatchState.GENERATED
                        || status == ExportBatchState.OPENED_IN_MICROSOFT_PROJECT
                        || status == ExportBatchState.VERIFIED
                        ? "object://synthetic/export-batches/export-1.mspdi.xml"
                        : null,
                status == ExportBatchState.GENERATED
                        || status == ExportBatchState.OPENED_IN_MICROSOFT_PROJECT
                        || status == ExportBatchState.VERIFIED
                        ? "sha256:synthetic"
                        : null,
                null,
                1,
                eligible ? 1 : 0,
                eligible ? 0 : 1,
                ExportIntegrityPolicy.CURRENT_VERSION,
                true
        );
        ExportPreviewLineRecord line = new ExportPreviewLineRecord(
                UUID.randomUUID(),
                exportBatchId,
                projectId,
                snapshotId,
                UUID.randomUUID(),
                "SYN-TASK-1",
                "1",
                "Synthetic Task A1",
                "task_update",
                UUID.randomUUID(),
                eligible ? ApprovalState.APPROVED_FOR_EXPORT : ApprovalState.AWAITING_REVIEW,
                UUID.randomUUID(),
                "percent_complete",
                "25",
                "50",
                UUID.randomUUID(),
                OffsetDateTime.parse("2026-01-01T07:00:00Z"),
                "Synthetic reason",
                true,
                eligible,
                ExportIntegrityPolicy.CURRENT_VERSION,
                UUID.randomUUID(),
                "a".repeat(64),
                "synthetic-source-version-1"
        );
        return new ExportPreviewDetail(
                batch,
                List.of(line),
                "Export preview only. No MSPDI/XML artifact was generated and no Microsoft Project write-back was run."
        );
    }
}
