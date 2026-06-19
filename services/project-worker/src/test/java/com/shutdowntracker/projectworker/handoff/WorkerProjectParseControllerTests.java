package com.shutdowntracker.projectworker.handoff;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shutdowntracker.projectimport.contract.ProjectParseSummaryRequest;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WorkerProjectParseController.class)
class WorkerProjectParseControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkerProjectParseHandoffService handoffService;

    @Test
    void returnsWorkerParseSummaryFromSharedContractRequest() throws Exception {
        UUID importBatchId = UUID.randomUUID();
        when(handoffService.summarize(any(ProjectParseSummaryRequest.class))).thenReturn(new ProjectParseSummaryResponse(
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
                0,
                0,
                List.of("Summary only; no schedule calculations were run.")
        ));

        String body = """
                {
                  "importBatchId": "%s",
                  "projectId": "%s",
                  "sourceFileId": "%s",
                  "storageUri": "file:///synthetic/source/synthetic-basic-wbs.mspdi.xml",
                  "originalFilename": "synthetic-basic-wbs.mspdi.xml"
                }
                """.formatted(importBatchId, UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/worker/project-import/parse-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importBatchId").value(importBatchId.toString()))
                .andExpect(jsonPath("$.parserName").value("mpxj"))
                .andExpect(jsonPath("$.taskCount").value(6));

        verify(handoffService).summarize(any(ProjectParseSummaryRequest.class));
    }
}
