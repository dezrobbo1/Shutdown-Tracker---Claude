package com.shutdowntracker.api.importedproject;

import java.util.List;
import java.util.UUID;

public interface ImportedProjectRepository {

    ProjectSnapshotRecord createSnapshot(ProjectSnapshotCreateRequest request);

    List<ImportedTaskRecord> createTasks(
            UUID projectId,
            UUID projectSnapshotId,
            List<ImportedTaskCreateRequest> tasks
    );

    List<ImportedResourceRecord> createResources(
            UUID projectId,
            UUID projectSnapshotId,
            List<ImportedResourceCreateRequest> resources
    );

    List<ImportedAssignmentRecord> createAssignments(
            UUID projectId,
            UUID projectSnapshotId,
            List<ImportedAssignmentCreateRequest> assignments
    );

    List<ImportedExtendedAttributeRecord> createExtendedAttributes(
            UUID projectId,
            UUID projectSnapshotId,
            List<ImportedExtendedAttributeCreateRequest> extendedAttributes
    );
}
