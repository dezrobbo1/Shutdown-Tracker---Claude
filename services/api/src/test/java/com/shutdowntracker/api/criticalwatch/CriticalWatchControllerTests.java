package com.shutdowntracker.api.criticalwatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shutdowntracker.api.actor.ActorWebMvcConfiguration;
import com.shutdowntracker.api.actor.StubActorConfiguration;
import com.shutdowntracker.api.identity.Capability;
import com.shutdowntracker.api.identity.ProjectAuthorizationService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/**
 * Routing, authorisation wiring, and identity handling for Critical Watch.
 *
 * <p>What the endpoints do with real data is covered by
 * {@link CriticalWatchServiceDatabaseTests}. What these assert is that no endpoint reaches the
 * service before the capability check passes, and that a project id always travels with a
 * watchlist or package id — the two ways this surface could leak across projects.
 */
@WebMvcTest(CriticalWatchController.class)
@TestPropertySource(properties = "shutdown-tracker.persistence.enabled=true")
@Import({ActorWebMvcConfiguration.class, StubActorConfiguration.class})
class CriticalWatchControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CriticalWatchService service;

    @MockBean
    private ProjectAuthorizationService authorization;

    @Test
    void createsAWatchlistAsTheResolvedActor() throws Exception {
        UUID projectId = UUID.randomUUID();
        CriticalWatchlistRecord watchlist = new CriticalWatchlistRecord(
                UUID.randomUUID(), projectId, "Kiln Critical", "Shutdown watch", "active");
        when(service.createWatchlist(eq(projectId), eq(StubActorConfiguration.ACTOR), any(), any()))
                .thenReturn(watchlist);

        mockMvc.perform(post("/api/projects/{projectId}/critical-watchlists", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Kiln Critical",
                                  "description": "Shutdown watch"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kiln Critical"))
                .andExpect(jsonPath("$.status").value("active"));

        verify(authorization)
                .requireCapability(projectId, StubActorConfiguration.ACTOR, Capability.MANAGE_CRITICAL_WATCHLIST);
        verify(service).createWatchlist(
                projectId, StubActorConfiguration.ACTOR, "Kiln Critical", "Shutdown watch");
    }

    @Test
    void listsWatchlistsForTheProject() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(service.watchlists(projectId)).thenReturn(List.of(new CriticalWatchlistRecord(
                UUID.randomUUID(), projectId, "Kiln Critical", null, "active")));

        mockMvc.perform(get("/api/projects/{projectId}/critical-watchlists", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Kiln Critical"));

        verify(authorization)
                .requireCapability(projectId, StubActorConfiguration.ACTOR, Capability.VIEW_PROJECT);
    }

    @Test
    void createsAWorkPackageOnAWatchlist() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID watchlistId = UUID.randomUUID();
        when(service.createWorkPackage(
                eq(projectId), eq(StubActorConfiguration.ACTOR), eq(watchlistId), any(), any()))
                .thenReturn(workPackage(projectId, watchlistId));

        mockMvc.perform(post(
                        "/api/projects/{projectId}/critical-watchlists/{watchlistId}/work-packages",
                        projectId, watchlistId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Mechanical WP",
                                  "description": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mechanical WP"));

        verify(authorization)
                .requireCapability(projectId, StubActorConfiguration.ACTOR, Capability.MANAGE_CRITICAL_WATCHLIST);
    }

    /**
     * The project id must reach the service, not just the watchlist id.
     *
     * <p>A watchlist id appears in URLs and reports and is not a secret. If the lookup were by
     * watchlist alone, a caller who is a legitimate viewer on their own project could read
     * another project's Critical Work Packages by naming its watchlist.
     */
    @Test
    void scopesTheWorkPackageListToTheProjectAsWellAsTheWatchlist() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID watchlistId = UUID.randomUUID();
        when(service.workPackages(projectId, watchlistId))
                .thenReturn(List.of(workPackage(projectId, watchlistId)));

        mockMvc.perform(get(
                        "/api/projects/{projectId}/critical-watchlists/{watchlistId}/work-packages",
                        projectId, watchlistId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Mechanical WP"));

        verify(service).workPackages(projectId, watchlistId);
        verify(authorization)
                .requireCapability(projectId, StubActorConfiguration.ACTOR, Capability.VIEW_PROJECT);
    }

    @Test
    void addsASummaryTaskAsASourceOfWork() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID workPackageId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID importedTaskId = UUID.randomUUID();
        when(service.addSource(
                eq(projectId), eq(StubActorConfiguration.ACTOR), eq(workPackageId),
                eq(snapshotId), eq(importedTaskId), anyBoolean()))
                .thenReturn(new CriticalWorkPackageSourceRecord(
                        UUID.randomUUID(), workPackageId, snapshotId, importedTaskId,
                        "summary_task", true));

        mockMvc.perform(post(
                        "/api/projects/{projectId}/critical-work-packages/{workPackageId}/sources",
                        projectId, workPackageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectSnapshotId": "%s",
                                  "importedTaskId": "%s",
                                  "includeDescendants": true
                                }
                                """.formatted(snapshotId, importedTaskId)))
                .andExpect(status().isOk())
                // The server decides summary_task versus multi_summary; the caller cannot assert it.
                .andExpect(jsonPath("$.sourceType").value("summary_task"))
                .andExpect(jsonPath("$.includeDescendants").value(true));

        verify(service).addSource(
                projectId, StubActorConfiguration.ACTOR, workPackageId, snapshotId, importedTaskId, true);
        verify(authorization)
                .requireCapability(projectId, StubActorConfiguration.ACTOR, Capability.MANAGE_CRITICAL_WATCHLIST);
    }

    @Test
    void returnsTheTasksAPackageReportsOn() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID workPackageId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(service.reportedTasks(projectId, workPackageId)).thenReturn(List.of(taskId));

        mockMvc.perform(get(
                        "/api/projects/{projectId}/critical-work-packages/{workPackageId}/reported-tasks",
                        projectId, workPackageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(taskId.toString()));

        verify(authorization)
                .requireCapability(projectId, StubActorConfiguration.ACTOR, Capability.VIEW_PROJECT);
    }

    /** The submitter is the authenticated actor. The body has no user id to assert. */
    @Test
    void submitsACriticalUpdateAsTheResolvedActor() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID workPackageId = UUID.randomUUID();
        when(service.submitUpdate(
                eq(projectId), eq(StubActorConfiguration.ACTOR), any(CriticalUpdateSubmitRequest.class)))
                .thenReturn(update(projectId, workPackageId));

        mockMvc.perform(post("/api/projects/{projectId}/critical-updates", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "criticalWorkPackageId": "%s",
                                  "updateMode": "shift",
                                  "currentFocus": "Blanking plates fitted",
                                  "idempotencyKey": "field-capture-1",
                                  "lines": []
                                }
                                """.formatted(workPackageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("submitted"))
                .andExpect(jsonPath("$.submittedByUserId")
                        .value(StubActorConfiguration.ACTOR.userId().toString()));

        ArgumentCaptor<CriticalUpdateSubmitRequest> captured =
                ArgumentCaptor.forClass(CriticalUpdateSubmitRequest.class);
        verify(service).submitUpdate(
                eq(projectId), eq(StubActorConfiguration.ACTOR), captured.capture());
        assertThat(captured.getValue().criticalWorkPackageId()).isEqualTo(workPackageId);
        assertThat(captured.getValue().idempotencyKey())
                .describedAs("the offline capture key survives to the service, so a retry is recognised")
                .isEqualTo("field-capture-1");
        verify(authorization)
                .requireCapability(projectId, StubActorConfiguration.ACTOR, Capability.SUBMIT_CRITICAL_UPDATE);
    }

    @Test
    void listsUpdatesForAPackage() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID workPackageId = UUID.randomUUID();
        when(service.updates(projectId, workPackageId))
                .thenReturn(List.of(update(projectId, workPackageId)));

        mockMvc.perform(get(
                        "/api/projects/{projectId}/critical-work-packages/{workPackageId}/updates",
                        projectId, workPackageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentFocus").value("Blanking plates fitted"));

        verify(authorization)
                .requireCapability(projectId, StubActorConfiguration.ACTOR, Capability.VIEW_PROJECT);
    }

    @Test
    void refusesToComposeAWatchlistWithoutTheCapability() throws Exception {
        UUID projectId = UUID.randomUUID();
        Mockito.doThrow(new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Role supervisor may not manage_critical_watchlist."))
                .when(authorization)
                .requireCapability(
                        projectId, StubActorConfiguration.ACTOR, Capability.MANAGE_CRITICAL_WATCHLIST);

        mockMvc.perform(post("/api/projects/{projectId}/critical-watchlists", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Unauthorised"
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(service, never()).createWatchlist(any(), any(), any(), any());
    }

    @Test
    void refusesToSubmitAnUpdateWithoutTheCapability() throws Exception {
        UUID projectId = UUID.randomUUID();
        Mockito.doThrow(new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Role viewer may not submit_critical_update."))
                .when(authorization)
                .requireCapability(
                        projectId, StubActorConfiguration.ACTOR, Capability.SUBMIT_CRITICAL_UPDATE);

        mockMvc.perform(post("/api/projects/{projectId}/critical-updates", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "criticalWorkPackageId": "%s",
                                  "lines": []
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());

        verify(service, never()).submitUpdate(any(), any(), any());
    }

    private CriticalWorkPackageRecord workPackage(UUID projectId, UUID watchlistId) {
        return new CriticalWorkPackageRecord(
                UUID.randomUUID(), projectId, watchlistId, "Mechanical WP", null, "active");
    }

    private CriticalUpdateRecord update(UUID projectId, UUID workPackageId) {
        return new CriticalUpdateRecord(
                UUID.randomUUID(),
                projectId,
                workPackageId,
                "submitted",
                "shift",
                OffsetDateTime.parse("2026-08-17T06:00:00Z"),
                StubActorConfiguration.ACTOR.userId(),
                "Blanking plates fitted",
                null,
                null,
                null);
    }
}
