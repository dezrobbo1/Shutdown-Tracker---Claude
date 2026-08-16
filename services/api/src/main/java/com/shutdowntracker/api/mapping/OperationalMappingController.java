package com.shutdowntracker.api.mapping;

import java.util.List;
import java.util.UUID;
import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.identity.Capability;
import com.shutdowntracker.api.identity.ProjectAuthorizationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/import-profiles")
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class OperationalMappingController {

    private final OperationalMappingService service;
    private final ProjectAuthorizationService authorization;

    public OperationalMappingController(
            OperationalMappingService service,
            ProjectAuthorizationService authorization
    ) {
        this.service = service;
        this.authorization = authorization;
    }

    @PostMapping
    public ImportProfileRecord createProfile(
            @PathVariable UUID projectId, Actor actor, @RequestBody CreateProfileRequest request) {
        authorization.requireCapability(projectId, actor, Capability.MANAGE_IMPORT_PROFILE);
        return service.createProfile(projectId, actor, request.name(), request.description());
    }

    @PostMapping("/{importProfileId}/activate")
    public ImportProfileRecord activateProfile(
            @PathVariable UUID projectId, @PathVariable UUID importProfileId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.MANAGE_IMPORT_PROFILE);
        return service.activateProfile(projectId, actor, importProfileId);
    }

    @GetMapping("/active")
    public ImportProfileRecord activeProfile(@PathVariable UUID projectId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.VIEW_PROJECT);
        return service.requireActiveProfile(projectId);
    }

    @PostMapping("/{importProfileId}/categories")
    public OperationalCategoryRecord addCategory(
            @PathVariable UUID projectId,
            @PathVariable UUID importProfileId,
            Actor actor,
            @RequestBody OperationalCategoryCreateRequest request
    ) {
        authorization.requireCapability(projectId, actor, Capability.MANAGE_IMPORT_PROFILE);
        return service.addCategory(projectId, importProfileId, request);
    }

    @GetMapping("/{importProfileId}/categories")
    public List<OperationalCategoryRecord> categories(
            @PathVariable UUID projectId, @PathVariable UUID importProfileId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.VIEW_PROJECT);
        return service.categories(importProfileId);
    }

    /**
     * Resolves the active profile against a snapshot. Run after an import, and again on
     * re-import to revalidate; the response reports each category's health so a planner
     * can see what needs confirming.
     */
    @PostMapping("/resolve/{projectSnapshotId}")
    public List<CategoryResolutionSummary> resolveSnapshot(
            @PathVariable UUID projectId, @PathVariable UUID projectSnapshotId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.MANAGE_IMPORT_PROFILE);
        return service.resolveSnapshot(projectId, projectSnapshotId);
    }

    @GetMapping("/execution-readiness/{projectSnapshotId}")
    public List<UUID> tasksMissingRequiredCategories(
            @PathVariable UUID projectId, @PathVariable UUID projectSnapshotId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.VIEW_PROJECT);
        return service.tasksMissingRequiredCategories(projectId, projectSnapshotId);
    }

    public record CreateProfileRequest(String name, String description) {
    }
}
