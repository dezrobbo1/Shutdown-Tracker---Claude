package com.shutdowntracker.api.exportpreview.handoff;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shutdowntracker.api.exportpreview.ApprovalState;
import com.shutdowntracker.api.exportpreview.ExportBatchState;
import com.shutdowntracker.api.exportpreview.ExportIntegrityPolicy;
import com.shutdowntracker.api.exportpreview.ExportPreviewBatchRecord;
import com.shutdowntracker.api.exportpreview.ExportPreviewDetail;
import com.shutdowntracker.api.exportpreview.ExportPreviewLineRecord;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactGenerationResponse;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactSummary;
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

@WebMvcTest(ExportArtifactHandoffController.class)
@TestPropertySource(properties = "shutdown-tracker.persistence.enabled=true")
class ExportArtifactHandoffControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExportArtifactHandoffService service;

    @Test
    void requestsWorkerExportArtifactGeneration() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID exportBatchId = UUID.randomUUID();
        ExportArtifactGenerationResponse response = response(projectId, exportBatchId);
        when(service.generateArtifact(any(UUID.class), any(UUID.class), any(ExportArtifactGenerationRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post(
                        "/api/projects/{projectId}/export-preview/{exportBatchId}/generate-artifact",
                        projectId,
                        exportBatchId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Synthetic worker artifact generation"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportPreview.batch.status").value("GENERATED"))
                .andExpect(jsonPath("$.workerResponse.artifactSummary.artifactFormat").value("mspdi_xml"))
                .andExpect(jsonPath("$.message").value(response.message()));

        verify(service).generateArtifact(any(UUID.class), any(UUID.class), any(ExportArtifactGenerationRequest.class));
    }

    private ExportArtifactGenerationResponse response(UUID projectId, UUID exportBatchId) {
        UUID snapshotId = UUID.randomUUID();
        ExportPreviewBatchRecord batch = new ExportPreviewBatchRecord(
                exportBatchId,
                projectId,
                snapshotId,
                ExportBatchState.GENERATED,
                OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                OffsetDateTime.parse("2026-01-01T01:00:00Z"),
                null,
                OffsetDateTime.parse("2026-01-01T02:00:00Z"),
                null,
                null,
                null,
                "file:///synthetic/export-artifacts/export.mspdi.xml",
                "synthetic-sha256",
                null,
                1,
                1,
                0,
                ExportIntegrityPolicy.CURRENT_VERSION,
                true
        );
        ExportPreviewLineRecord line = new ExportPreviewLineRecord(
                UUID.randomUUID(),
                exportBatchId,
                projectId,
                snapshotId,
                UUID.randomUUID(),
                "101",
                "1",
                "Synthetic Task A1",
                "task_update",
                UUID.randomUUID(),
                ApprovalState.APPROVED_FOR_EXPORT,
                UUID.randomUUID(),
                "percent_complete",
                "25",
                "75",
                UUID.randomUUID(),
                OffsetDateTime.parse("2026-01-01T07:00:00Z"),
                "Synthetic reason",
                true,
                true,
                ExportIntegrityPolicy.CURRENT_VERSION,
                UUID.randomUUID(),
                "a".repeat(64)
        );
        ProjectExportArtifactGenerationResponse workerResponse = new ProjectExportArtifactGenerationResponse(
                exportBatchId,
                projectId,
                "file:///synthetic/export-artifacts/export.mspdi.xml",
                "synthetic-sha256",
                new ProjectExportArtifactSummary(
                        "export.mspdi.xml",
                        "mspdi_xml",
                        1,
                        1,
                        512,
                        "synthetic-sha256",
                        List.of("MSPDI/XML artifact only; no schedule calculations or Microsoft Project write-back were run.")
                ),
                "MSPDI/XML artifact generated by project worker. No Microsoft Project write-back was run."
        );
        return new ExportArtifactGenerationResponse(
                new ExportPreviewDetail(batch, List.of(line), "generated"),
                workerResponse,
                "Worker-generated MSPDI/XML artifact metadata recorded. No Microsoft Project write-back was run."
        );
    }
}
