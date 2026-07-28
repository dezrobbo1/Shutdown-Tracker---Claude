package com.shutdowntracker.api.exportpreview;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shutdowntracker.api.ApiStrictJsonConfiguration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExportCandidateController.class)
@TestPropertySource(properties = "shutdown-tracker.persistence.enabled=true")
@Import(ApiStrictJsonConfiguration.class)
class ExportCandidateControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExportCandidateService service;

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
        ExportCandidateApprovalEventRecord response = new ExportCandidateApprovalEventRecord(
                UUID.randomUUID(),
                projectId,
                UUID.randomUUID(),
                candidateId,
                ExportIntegrityPolicy.CURRENT_VERSION,
                ApprovalState.APPROVED_FOR_EXPORT,
                null,
                OffsetDateTime.parse("2026-01-01T07:00:00Z"),
                UUID.randomUUID(),
                OffsetDateTime.parse("2026-01-01T08:00:00Z"),
                "Synthetic planner approval",
                OffsetDateTime.parse("2026-01-01T08:00:01Z"),
                Map.of()
        );
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
