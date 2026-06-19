package com.shutdowntracker.api.tasklineage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TaskLineageController.class)
@TestPropertySource(properties = "shutdown-tracker.persistence.enabled=true")
class TaskLineageControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskLineageService service;

    @Test
    void listsLineageLinksForSnapshotPair() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID previousSnapshotId = UUID.randomUUID();
        UUID currentSnapshotId = UUID.randomUUID();
        TaskLineageRecord record = record(projectId, TaskLineageReviewState.SUGGESTED);
        when(service.listBySnapshotPair(projectId, previousSnapshotId, currentSnapshotId)).thenReturn(List.of(record));

        mockMvc.perform(get("/api/projects/{projectId}/import-review/lineage-links", projectId)
                        .param("previousSnapshotId", previousSnapshotId.toString())
                        .param("currentSnapshotId", currentSnapshotId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(record.id().toString()))
                .andExpect(jsonPath("$[0].reviewState").value("SUGGESTED"))
                .andExpect(jsonPath("$[0].previousTaskName").value("Synthetic Task A1"))
                .andExpect(jsonPath("$[0].currentTaskName").value("Synthetic Task A1 Revised"));

        verify(service).listBySnapshotPair(projectId, previousSnapshotId, currentSnapshotId);
    }

    @Test
    void createsSuggestedLineageLink() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID previousSnapshotId = UUID.randomUUID();
        UUID currentSnapshotId = UUID.randomUUID();
        UUID previousImportedTaskId = UUID.randomUUID();
        UUID currentImportedTaskId = UUID.randomUUID();
        TaskLineageRecord record = record(projectId, TaskLineageReviewState.SUGGESTED);
        when(service.createSuggested(eq(projectId), any(TaskLineageCreateRequest.class))).thenReturn(record);

        String body = """
                {
                  "previousSnapshotId": "%s",
                  "currentSnapshotId": "%s",
                  "previousImportedTaskId": "%s",
                  "currentImportedTaskId": "%s",
                  "matchMethod": "external_uid",
                  "matchConfidence": 95,
                  "metadata": {
                    "source": "synthetic-lineage-review"
                  }
                }
                """.formatted(
                previousSnapshotId,
                currentSnapshotId,
                previousImportedTaskId,
                currentImportedTaskId
        );

        mockMvc.perform(post("/api/projects/{projectId}/import-review/lineage-links", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewState").value("SUGGESTED"))
                .andExpect(jsonPath("$.matchMethod").value("external_uid"));

        verify(service).createSuggested(eq(projectId), any(TaskLineageCreateRequest.class));
    }

    @Test
    void acceptsLineageLinkForReviewOnly() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID lineageLinkId = UUID.randomUUID();
        TaskLineageDecisionResponse response = new TaskLineageDecisionResponse(
                record(projectId, TaskLineageReviewState.ACCEPTED),
                "Task lineage link accepted for import review only. "
                        + "No schedule calculation or Microsoft Project write-back was run."
        );
        when(service.accept(projectId, lineageLinkId)).thenReturn(response);

        mockMvc.perform(post(
                        "/api/projects/{projectId}/import-review/lineage-links/{lineageLinkId}/accept",
                        projectId,
                        lineageLinkId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineageLink.reviewState").value("ACCEPTED"))
                .andExpect(jsonPath("$.message").value(response.message()));

        verify(service).accept(projectId, lineageLinkId);
    }

    @Test
    void rejectsLineageLinkForReviewOnly() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID lineageLinkId = UUID.randomUUID();
        TaskLineageDecisionResponse response = new TaskLineageDecisionResponse(
                record(projectId, TaskLineageReviewState.REJECTED),
                "Task lineage link rejected for import review only. "
                        + "No schedule calculation or Microsoft Project write-back was run."
        );
        when(service.reject(projectId, lineageLinkId)).thenReturn(response);

        mockMvc.perform(post(
                        "/api/projects/{projectId}/import-review/lineage-links/{lineageLinkId}/reject",
                        projectId,
                        lineageLinkId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineageLink.reviewState").value("REJECTED"))
                .andExpect(jsonPath("$.message").value(response.message()));

        verify(service).reject(projectId, lineageLinkId);
    }

    private TaskLineageRecord record(UUID projectId, TaskLineageReviewState reviewState) {
        return new TaskLineageRecord(
                UUID.randomUUID(),
                projectId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SYN-TASK-1",
                "Synthetic Task A1",
                UUID.randomUUID(),
                "SYN-TASK-1",
                "Synthetic Task A1 Revised",
                "external_uid",
                BigDecimal.valueOf(95),
                reviewState,
                null,
                null
        );
    }
}
