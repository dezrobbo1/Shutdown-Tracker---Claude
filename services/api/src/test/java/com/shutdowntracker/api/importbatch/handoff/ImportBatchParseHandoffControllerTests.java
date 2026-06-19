package com.shutdowntracker.api.importbatch.handoff;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shutdowntracker.api.importbatch.ImportBatchRecord;
import com.shutdowntracker.api.importbatch.ImportBatchStatus;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ImportBatchParseHandoffController.class)
@TestPropertySource(properties = "shutdown-tracker.persistence.enabled=true")
class ImportBatchParseHandoffControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ImportBatchParseHandoffService service;

    @Test
    void requestsWorkerParseSummaryForImportBatch() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID importBatchId = UUID.randomUUID();
        UUID sourceFileId = UUID.randomUUID();
        ImportBatchParseHandoffResponse response = new ImportBatchParseHandoffResponse(
                new ImportBatchRecord(
                        importBatchId,
                        projectId,
                        sourceFileId,
                        ImportBatchStatus.PARSED,
                        "mpxj",
                        "16.4.0",
                        1,
                        0
                ),
                new ProjectParseSummaryResponse(
                        importBatchId,
                        "mpxj",
                        "16.4.0",
                        "synthetic-basic-wbs.mspdi.xml",
                        "mspdi_xml",
                        "Synthetic Basic WBS",
                        6,
                        2,
                        4,
                        0,
                        0,
                        1,
                        0,
                        1,
                        0,
                        List.of("Summary only; no schedule calculations were run.")
                ),
                "Worker parse summary recorded on the import batch. No imported snapshot, task/resource/assignment rows, export artifact, schedule calculation, or Microsoft Project write-back was created."
        );
        when(service.requestParseSummary(projectId, importBatchId)).thenReturn(response);

        mockMvc.perform(post(
                        "/api/projects/{projectId}/import-batches/{importBatchId}/request-parse-summary",
                        projectId,
                        importBatchId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importBatch.status").value("PARSED"))
                .andExpect(jsonPath("$.parseSummary.parserName").value("mpxj"))
                .andExpect(jsonPath("$.parseSummary.taskCount").value(6))
                .andExpect(jsonPath("$.message").value(containsString("No imported snapshot")));

        verify(service).requestParseSummary(projectId, importBatchId);
    }
}
