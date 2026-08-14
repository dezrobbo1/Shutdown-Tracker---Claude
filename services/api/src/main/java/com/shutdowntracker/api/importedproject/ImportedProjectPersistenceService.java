package com.shutdowntracker.api.importedproject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class ImportedProjectPersistenceService {

    private final ImportedProjectRepository repository;

    public ImportedProjectPersistenceService(ImportedProjectRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ImportedProjectPersistenceResult persistParsedSnapshot(ImportedProjectSnapshotCreateRequest request) {
        ProjectSnapshotRecord snapshot = repository.createSnapshot(new ProjectSnapshotCreateRequest(
                request.projectId(),
                request.importBatchId(),
                ProjectSnapshotStatus.PARSED,
                request.externalProjectUid(),
                request.externalProjectName(),
                request.projectStatusDate(),
                request.metadata()
        ));

        ImportedProjectEntities entities = request.entities();
        List<ImportedTaskRecord> tasks = persistTasks(request.projectId(), snapshot.id(), entities.tasks());
        List<ImportedResourceRecord> resources = repository.createResources(
                request.projectId(),
                snapshot.id(),
                entities.resources()
        );
        List<ImportedAssignmentRecord> assignments = repository.createAssignments(
                request.projectId(),
                snapshot.id(),
                resolveAssignmentLinks(entities.assignments(), tasks, resources)
        );
        List<ImportedExtendedAttributeRecord> extendedAttributes = repository.createExtendedAttributes(
                request.projectId(),
                snapshot.id(),
                entities.extendedAttributes()
        );

        return new ImportedProjectPersistenceResult(
                snapshot,
                tasks.size(),
                resources.size(),
                assignments.size(),
                extendedAttributes.size()
        );
    }

    /**
     * Inserts tasks in the order the parser produced them, resolving each task's parent to
     * the row already stored for it.
     *
     * <p>The parser emits a parent ahead of its children, so by the time a child is
     * reached its parent's identifier is known. A parent that cannot be resolved is stored
     * with a null link rather than failing the import: the imported {@code
     * parent_external_uid} is still recorded, so the original structure is not lost and
     * the gap stays visible for review.
     */
    private List<ImportedTaskRecord> persistTasks(
            UUID projectId,
            UUID snapshotId,
            List<ImportedTaskCreateRequest> tasks
    ) {
        Map<String, UUID> idByExternalUid = new HashMap<>();
        List<ImportedTaskRecord> stored = new ArrayList<>(tasks.size());

        for (ImportedTaskCreateRequest task : tasks) {
            UUID parentId = task.parentExternalUid() == null
                    ? null
                    : idByExternalUid.get(task.parentExternalUid());

            ImportedTaskRecord record = repository
                    .createTasks(projectId, snapshotId, List.of(withParent(task, parentId)))
                    .get(0);

            stored.add(record);
            if (record.externalUid() != null) {
                idByExternalUid.put(record.externalUid(), record.id());
            }
        }
        return List.copyOf(stored);
    }

    private ImportedTaskCreateRequest withParent(ImportedTaskCreateRequest task, UUID parentId) {
        if (parentId == null) {
            return task;
        }
        return new ImportedTaskCreateRequest(
                task.externalUid(),
                task.externalId(),
                task.name(),
                task.wbs(),
                task.outlineNumber(),
                task.outlineLevel(),
                task.summary(),
                task.parentExternalUid(),
                parentId,
                task.plannedStart(),
                task.plannedFinish(),
                task.actualStart(),
                task.actualFinish(),
                task.percentComplete(),
                task.physicalPercentComplete(),
                task.notes(),
                task.rawData()
        );
    }

    /**
     * Points each assignment at the stored task and resource rows it refers to.
     *
     * <p>Assignments arrive carrying the file's own task and resource identifiers. An
     * unresolvable side is left null and the external identifier is still stored, so a
     * partially-consistent file imports rather than being rejected outright.
     */
    private List<ImportedAssignmentCreateRequest> resolveAssignmentLinks(
            List<ImportedAssignmentCreateRequest> assignments,
            List<ImportedTaskRecord> tasks,
            List<ImportedResourceRecord> resources
    ) {
        Map<String, UUID> taskIds = new HashMap<>();
        for (ImportedTaskRecord task : tasks) {
            if (task.externalUid() != null) {
                taskIds.put(task.externalUid(), task.id());
            }
        }
        Map<String, UUID> resourceIds = new HashMap<>();
        for (ImportedResourceRecord resource : resources) {
            if (resource.externalUid() != null) {
                resourceIds.put(resource.externalUid(), resource.id());
            }
        }

        List<ImportedAssignmentCreateRequest> resolved = new ArrayList<>(assignments.size());
        for (ImportedAssignmentCreateRequest assignment : assignments) {
            resolved.add(new ImportedAssignmentCreateRequest(
                    assignment.externalUid(),
                    assignment.taskExternalUid(),
                    assignment.resourceExternalUid(),
                    assignment.taskExternalUid() == null ? null : taskIds.get(assignment.taskExternalUid()),
                    assignment.resourceExternalUid() == null
                            ? null
                            : resourceIds.get(assignment.resourceExternalUid()),
                    assignment.rawData()
            ));
        }
        return resolved;
    }
}
