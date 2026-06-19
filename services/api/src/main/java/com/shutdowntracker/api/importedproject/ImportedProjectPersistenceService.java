package com.shutdowntracker.api.importedproject;

import java.util.List;
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
        List<ImportedTaskRecord> tasks = repository.createTasks(
                request.projectId(),
                snapshot.id(),
                entities.tasks()
        );
        List<ImportedResourceRecord> resources = repository.createResources(
                request.projectId(),
                snapshot.id(),
                entities.resources()
        );
        List<ImportedAssignmentRecord> assignments = repository.createAssignments(
                request.projectId(),
                snapshot.id(),
                entities.assignments()
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
}
