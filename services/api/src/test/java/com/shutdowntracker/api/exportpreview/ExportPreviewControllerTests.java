package com.shutdowntracker.api.exportpreview;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExportPreviewController.class)
@TestPropertySource(properties = "shutdown-tracker.persistence.enabled=true")
class ExportPreviewControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExportPreviewService service;

    @Test
    void createsExportPreviewOnly() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID importedTaskId = UUID.randomUUID();
        UUID sourceEntityId = UUID.randomUUID();
        ExportPreviewDetail detail = detail(projectId, snapshotId, true);
        when(service.createPreview(eq(projectId), any(ExportPreviewCreateRequest.class))).thenReturn(detail);

        String body = """
                {
                  "projectSnapshotId": "%s",
                  "lines": [
                    {
                      "importedTaskId": "%s",
                      "sourceEntityType": "task_update",
                      "sourceEntityId": "%s",
                      "fieldName": "percent_complete",
                      "newValue": "50",
                      "reason": "Synthetic approved progress update"
                    }
                  ],
                  "metadata": {
                    "source": "synthetic-export-preview"
                  }
                }
                """.formatted(snapshotId, importedTaskId, sourceEntityId);

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
                status == ExportBatchState.APPROVED || status == ExportBatchState.GENERATED
                        ? OffsetDateTime.parse("2026-01-01T01:00:00Z")
                        : null,
                null,
                status == ExportBatchState.GENERATED
                        ? OffsetDateTime.parse("2026-01-01T02:00:00Z")
                        : null,
                null,
                status == ExportBatchState.GENERATED
                        ? "object://synthetic/export-batches/export-1.mspdi.xml"
                        : null,
                status == ExportBatchState.GENERATED ? "sha256:synthetic" : null,
                null,
                1,
                eligible ? 1 : 0,
                eligible ? 0 : 1
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
                "percent_complete",
                "25",
                "50",
                UUID.randomUUID(),
                OffsetDateTime.parse("2026-01-01T07:00:00Z"),
                "Synthetic reason",
                true,
                eligible
        );
        return new ExportPreviewDetail(
                batch,
                List.of(line),
                "Export preview only. No MSPDI/XML artifact was generated and no Microsoft Project write-back was run."
        );
    }
}
