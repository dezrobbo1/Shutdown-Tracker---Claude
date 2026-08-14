package com.shutdowntracker.api.mapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.shutdowntracker.api.actor.Actor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves planner-configured Operational Categories against an imported snapshot.
 *
 * <p>Every value comes from imported Project data and is stored exactly as found. Aliases
 * and roll-ups are presentation configuration layered on top; they never replace the
 * source value.
 *
 * <p>Re-import never silently remaps. A category that resolves nothing where it used to
 * resolve something is reported as broken rather than quietly emptied, and a source that
 * has merely gained values is distinguished from one that has changed shape.
 */
@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class OperationalMappingService {

    private final OperationalMappingRepository repository;

    public OperationalMappingService(OperationalMappingRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ImportProfileRecord createProfile(UUID projectId, Actor actor, String name, String description) {
        return repository.createProfile(projectId, name, description, actor.userId());
    }

    @Transactional
    public ImportProfileRecord activateProfile(UUID projectId, Actor actor, UUID importProfileId) {
        return repository.activateProfile(projectId, importProfileId, actor.userId());
    }

    public ImportProfileRecord requireActiveProfile(UUID projectId) {
        return repository.findActiveProfile(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "This project has no active import profile."));
    }

    @Transactional
    public OperationalCategoryRecord addCategory(
            UUID projectId,
            UUID importProfileId,
            OperationalCategoryCreateRequest request
    ) {
        return repository.createCategory(projectId, importProfileId, request);
    }

    public List<OperationalCategoryRecord> categories(UUID importProfileId) {
        return repository.findCategories(importProfileId);
    }

    /**
     * Resolves every category in the active profile against a snapshot.
     *
     * <p>Called after an import so the snapshot arrives already classified, and again on
     * re-import to revalidate. Existing values for the snapshot are cleared first so a
     * re-run is idempotent rather than accumulating duplicates.
     */
    @Transactional
    public List<CategoryResolutionSummary> resolveSnapshot(UUID projectId, UUID projectSnapshotId) {
        ImportProfileRecord profile = requireActiveProfile(projectId);
        List<CategoryResolutionSummary> summaries = new ArrayList<>();

        for (OperationalCategoryRecord category : repository.findCategories(profile.id())) {
            summaries.add(resolveCategory(projectId, projectSnapshotId, category));
        }
        return summaries;
    }

    /**
     * Resolves one category and judges the result.
     *
     * <p>Health is assessed by comparing what this snapshot produced against what the
     * category produced before. Producing nothing at all is treated as broken: the field
     * has most likely been renamed or removed, and guessing a replacement is exactly the
     * silent remap the product forbids.
     */
    @Transactional
    public CategoryResolutionSummary resolveCategory(
            UUID projectId,
            UUID projectSnapshotId,
            OperationalCategoryRecord category
    ) {
        Set<String> valuesBefore = new HashSet<>(repository.distinctValues(category.id(), projectSnapshotId));
        repository.clearResolvedValues(category.id(), projectSnapshotId);

        switch (category.sourceMode()) {
            case TASK_FIELD -> repository.resolveTaskFieldValues(
                    projectId, projectSnapshotId, category.id(), category.sourceField());
            case HIERARCHY_ANCESTOR -> repository.resolveHierarchyValues(
                    projectId, projectSnapshotId, category.id(), category.sourceOutlineLevel());
            case RESOURCE_GROUP -> repository.resolveResourceGroupValues(
                    projectId, projectSnapshotId, category.id());
        }

        List<String> valuesAfter = repository.distinctValues(category.id(), projectSnapshotId);
        int taskCount = repository.countResolvedTasks(category.id(), projectSnapshotId);
        MappingHealth health = assessHealth(valuesBefore, valuesAfter);

        repository.updateCategoryHealth(category.id(), health);

        return new CategoryResolutionSummary(
                category.id(), category.name(), category.sourceMode(),
                taskCount, valuesAfter.size(), health);
    }

    /**
     * Leaf tasks missing a classification the project requires, for execution-readiness
     * checks. Summary tasks are excluded: they are reporting groups, not work.
     */
    public List<UUID> tasksMissingRequiredCategories(UUID projectId, UUID projectSnapshotId) {
        ImportProfileRecord profile = requireActiveProfile(projectId);
        List<UUID> missing = new ArrayList<>();

        for (OperationalCategoryRecord category : repository.findCategories(profile.id())) {
            if (category.requiredForExecution()) {
                missing.addAll(repository.findTasksMissingRequiredCategory(projectSnapshotId, category.id()));
            }
        }
        return missing;
    }

    /** Answers "why is this task in this category?" with the stored source values. */
    public List<String> valuesForTask(UUID operationalCategoryId, UUID importedTaskId) {
        return repository.valuesForTask(operationalCategoryId, importedTaskId);
    }

    private MappingHealth assessHealth(Set<String> before, List<String> after) {
        if (after.isEmpty()) {
            // Resolving nothing usually means the source field was renamed or removed.
            // The planner has to look; the product must not pick a replacement.
            return MappingHealth.BROKEN;
        }
        if (before.isEmpty()) {
            return MappingHealth.HEALTHY;
        }
        Set<String> newValues = new HashSet<>(after);
        newValues.removeAll(before);
        return newValues.isEmpty() ? MappingHealth.HEALTHY : MappingHealth.HEALTHY_WITH_NEW_VALUES;
    }
}
