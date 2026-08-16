package com.shutdowntracker.api.mapping;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OperationalMappingRepository {

    ImportProfileRecord createProfile(UUID projectId, String name, String description, UUID createdByUserId);

    ImportProfileRecord activateProfile(UUID projectId, UUID importProfileId, UUID activatedByUserId);

    Optional<ImportProfileRecord> findActiveProfile(UUID projectId);

    OperationalCategoryRecord createCategory(
            UUID projectId, UUID importProfileId, OperationalCategoryCreateRequest request);

    List<OperationalCategoryRecord> findCategories(UUID importProfileId);

    void updateCategoryHealth(UUID operationalCategoryId, MappingHealth health);

    void clearResolvedValues(UUID operationalCategoryId, UUID projectSnapshotId);

    int resolveTaskFieldValues(
            UUID projectId, UUID projectSnapshotId, UUID operationalCategoryId, String sourceField);

    int resolveHierarchyValues(
            UUID projectId, UUID projectSnapshotId, UUID operationalCategoryId, int outlineLevel);

    int resolveResourceGroupValues(UUID projectId, UUID projectSnapshotId, UUID operationalCategoryId);

    int countResolvedTasks(UUID operationalCategoryId, UUID projectSnapshotId);

    List<String> distinctValues(UUID operationalCategoryId, UUID projectSnapshotId);

    List<String> valuesForTask(UUID operationalCategoryId, UUID importedTaskId);

    /** Leaf tasks with no value for a category that the project requires. */
    List<UUID> findTasksMissingRequiredCategory(UUID projectSnapshotId, UUID operationalCategoryId);
}
