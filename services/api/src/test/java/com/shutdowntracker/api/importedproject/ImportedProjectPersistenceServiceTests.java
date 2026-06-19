package com.shutdowntracker.api.importedproject;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImportedProjectPersistenceServiceTests {

    @Test
    void persistsParsedSnapshotAndImportedEntitiesUnderCreatedSnapshot() {
        UUID projectId = UUID.randomUUID();
        UUID importBatchId = UUID.randomUUID();
        FakeImportedProjectRepository repository = new FakeImportedProjectRepository();
        ImportedProjectPersistenceService service = new ImportedProjectPersistenceService(repository);

        ImportedProjectPersistenceResult result = service.persistParsedSnapshot(new ImportedProjectSnapshotCreateRequest(
                projectId,
                importBatchId,
                "SYNTHETIC-PROJECT-1",
                "Synthetic Basic WBS",
                OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                Map.of("summaryOnly", true),
                new ImportedProjectEntities(
                        List.of(summaryTask(), leafTask()),
                        List.of(resource()),
                        List.of(assignment()),
                        List.of(extendedAttribute())
                )
        ));

        assertThat(repository.snapshotRequest.projectId()).isEqualTo(projectId);
        assertThat(repository.snapshotRequest.importBatchId()).isEqualTo(importBatchId);
        assertThat(repository.snapshotRequest.status()).isEqualTo(ProjectSnapshotStatus.PARSED);
        assertThat(repository.snapshotRequest.externalProjectName()).isEqualTo("Synthetic Basic WBS");
        assertThat(repository.taskProjectId).isEqualTo(projectId);
        assertThat(repository.taskSnapshotId).isEqualTo(repository.snapshot.id());
        assertThat(repository.resourceSnapshotId).isEqualTo(repository.snapshot.id());
        assertThat(repository.assignmentSnapshotId).isEqualTo(repository.snapshot.id());
        assertThat(repository.extendedAttributeSnapshotId).isEqualTo(repository.snapshot.id());
        assertThat(result.snapshot().id()).isEqualTo(repository.snapshot.id());
        assertThat(result.snapshot().projectId()).isEqualTo(projectId);
        assertThat(result.snapshot().importBatchId()).isEqualTo(importBatchId);
        assertThat(result.snapshot().status()).isEqualTo(ProjectSnapshotStatus.PARSED);
        assertThat(result.taskCount()).isEqualTo(2);
        assertThat(result.resourceCount()).isEqualTo(1);
        assertThat(result.assignmentCount()).isEqualTo(1);
        assertThat(result.extendedAttributeCount()).isEqualTo(1);
    }

    @Test
    void persistsEmptyEntityGroupsForParsedSnapshot() {
        FakeImportedProjectRepository repository = new FakeImportedProjectRepository();
        ImportedProjectPersistenceService service = new ImportedProjectPersistenceService(repository);

        ImportedProjectPersistenceResult result = service.persistParsedSnapshot(new ImportedProjectSnapshotCreateRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "Synthetic Empty Snapshot",
                null,
                Map.of(),
                ImportedProjectEntities.empty()
        ));

        assertThat(result.taskCount()).isZero();
        assertThat(result.resourceCount()).isZero();
        assertThat(result.assignmentCount()).isZero();
        assertThat(result.extendedAttributeCount()).isZero();
        assertThat(repository.tasks).isEmpty();
        assertThat(repository.resources).isEmpty();
        assertThat(repository.assignments).isEmpty();
        assertThat(repository.extendedAttributes).isEmpty();
    }

    private ImportedTaskCreateRequest summaryTask() {
        return new ImportedTaskCreateRequest(
                "SYN-SUMMARY-1",
                "1",
                "Synthetic Summary",
                "1",
                "1",
                1,
                true,
                null,
                null,
                OffsetDateTime.parse("2026-01-01T08:00:00Z"),
                OffsetDateTime.parse("2026-01-01T09:00:00Z"),
                null,
                null,
                BigDecimal.ZERO,
                null,
                null,
                Map.of("summaryOnly", true)
        );
    }

    private ImportedTaskCreateRequest leafTask() {
        return new ImportedTaskCreateRequest(
                "SYN-TASK-1",
                "2",
                "Synthetic Task A1",
                "1.1",
                "1.1",
                2,
                false,
                "SYN-SUMMARY-1",
                null,
                OffsetDateTime.parse("2026-01-01T09:00:00Z"),
                OffsetDateTime.parse("2026-01-01T10:00:00Z"),
                null,
                null,
                BigDecimal.ZERO,
                null,
                null,
                Map.of("summaryOnly", true)
        );
    }

    private ImportedResourceCreateRequest resource() {
        return new ImportedResourceCreateRequest(
                "SYN-RES-1",
                "Synthetic Resource",
                "work",
                Map.of("summaryOnly", true)
        );
    }

    private ImportedAssignmentCreateRequest assignment() {
        return new ImportedAssignmentCreateRequest(
                "SYN-ASSIGN-1",
                "SYN-TASK-1",
                "SYN-RES-1",
                null,
                null,
                Map.of("summaryOnly", true)
        );
    }

    private ImportedExtendedAttributeCreateRequest extendedAttribute() {
        return new ImportedExtendedAttributeCreateRequest(
                ImportedExtendedAttributeEntityType.TASK,
                "SYN-TASK-1",
                "TEXT1",
                "Text1",
                "Synthetic Field",
                "Synthetic Value",
                Map.of("summaryOnly", true)
        );
    }

    private static class FakeImportedProjectRepository implements ImportedProjectRepository {

        private final ProjectSnapshotRecord snapshot = new ProjectSnapshotRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ProjectSnapshotStatus.PARSED,
                "SYNTHETIC-PROJECT-1",
                "Synthetic Basic WBS",
                null,
                1
        );

        private ProjectSnapshotCreateRequest snapshotRequest;
        private UUID taskProjectId;
        private UUID taskSnapshotId;
        private UUID resourceSnapshotId;
        private UUID assignmentSnapshotId;
        private UUID extendedAttributeSnapshotId;
        private List<ImportedTaskCreateRequest> tasks = List.of();
        private List<ImportedResourceCreateRequest> resources = List.of();
        private List<ImportedAssignmentCreateRequest> assignments = List.of();
        private List<ImportedExtendedAttributeCreateRequest> extendedAttributes = List.of();

        @Override
        public ProjectSnapshotRecord createSnapshot(ProjectSnapshotCreateRequest request) {
            snapshotRequest = request;
            return new ProjectSnapshotRecord(
                    snapshot.id(),
                    request.projectId(),
                    request.importBatchId(),
                    request.status(),
                    request.externalProjectUid(),
                    request.externalProjectName(),
                    request.projectStatusDate(),
                    snapshot.snapshotVersion()
            );
        }

        @Override
        public List<ImportedTaskRecord> createTasks(
                UUID projectId,
                UUID projectSnapshotId,
                List<ImportedTaskCreateRequest> tasks
        ) {
            taskProjectId = projectId;
            taskSnapshotId = projectSnapshotId;
            this.tasks = List.copyOf(tasks);
            List<ImportedTaskRecord> records = new ArrayList<>();
            for (ImportedTaskCreateRequest task : tasks) {
                records.add(new ImportedTaskRecord(
                        UUID.randomUUID(),
                        projectId,
                        projectSnapshotId,
                        task.externalUid(),
                        task.name(),
                        task.summary()
                ));
            }
            return records;
        }

        @Override
        public List<ImportedResourceRecord> createResources(
                UUID projectId,
                UUID projectSnapshotId,
                List<ImportedResourceCreateRequest> resources
        ) {
            resourceSnapshotId = projectSnapshotId;
            this.resources = List.copyOf(resources);
            return resources.stream()
                    .map(resource -> new ImportedResourceRecord(
                            UUID.randomUUID(),
                            projectId,
                            projectSnapshotId,
                            resource.externalUid(),
                            resource.name(),
                            resource.resourceType()
                    ))
                    .toList();
        }

        @Override
        public List<ImportedAssignmentRecord> createAssignments(
                UUID projectId,
                UUID projectSnapshotId,
                List<ImportedAssignmentCreateRequest> assignments
        ) {
            assignmentSnapshotId = projectSnapshotId;
            this.assignments = List.copyOf(assignments);
            return assignments.stream()
                    .map(assignment -> new ImportedAssignmentRecord(
                            UUID.randomUUID(),
                            projectId,
                            projectSnapshotId,
                            assignment.externalUid(),
                            assignment.taskExternalUid(),
                            assignment.resourceExternalUid()
                    ))
                    .toList();
        }

        @Override
        public List<ImportedExtendedAttributeRecord> createExtendedAttributes(
                UUID projectId,
                UUID projectSnapshotId,
                List<ImportedExtendedAttributeCreateRequest> extendedAttributes
        ) {
            extendedAttributeSnapshotId = projectSnapshotId;
            this.extendedAttributes = List.copyOf(extendedAttributes);
            return extendedAttributes.stream()
                    .map(extendedAttribute -> new ImportedExtendedAttributeRecord(
                            UUID.randomUUID(),
                            projectId,
                            projectSnapshotId,
                            extendedAttribute.entityType(),
                            extendedAttribute.entityExternalUid(),
                            extendedAttribute.fieldId(),
                            extendedAttribute.fieldName()
                    ))
                    .toList();
        }
    }
}
