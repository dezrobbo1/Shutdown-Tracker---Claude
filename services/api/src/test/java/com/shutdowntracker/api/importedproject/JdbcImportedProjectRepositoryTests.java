package com.shutdowntracker.api.importedproject;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutdowntracker.api.support.AbstractDatabaseTest;
import com.shutdowntracker.api.support.DatabaseFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link JdbcImportedProjectRepository} against a real PostgreSQL server.
 *
 * <p>These statements carry the imported Microsoft Project snapshot into the database and
 * are the foundation the rest of the product reads from, so they are verified against the
 * actual engine rather than a mocked template: the enum casts, {@code jsonb} casts, and
 * the version-allocating subquery cannot be checked any other way.
 */
class JdbcImportedProjectRepositoryTests extends AbstractDatabaseTest {

    private JdbcImportedProjectRepository repository;
    private DatabaseFixtures fixtures;

    @BeforeEach
    void setUp() {
        repository = new JdbcImportedProjectRepository(
                new NamedParameterJdbcTemplate(dataSource()), new ObjectMapper());
        fixtures = new DatabaseFixtures(jdbcTemplate());
    }

    @Test
    void createsSnapshotAndAllocatesFirstVersion() {
        DatabaseFixtures.ImportChain chain = fixtures.createImportChain("Kiln Shutdown");

        ProjectSnapshotRecord snapshot = repository.createSnapshot(snapshotRequest(chain));

        assertThat(snapshot.id()).isNotNull();
        assertThat(snapshot.projectId()).isEqualTo(chain.projectId());
        assertThat(snapshot.status()).isEqualTo(ProjectSnapshotStatus.PARSED);
        assertThat(snapshot.externalProjectName()).isEqualTo("Kiln Shutdown 2026");
        assertThat(snapshot.snapshotVersion())
                .describedAs("the first snapshot for a project is version 1")
                .isEqualTo(1);
    }

    @Test
    void allocatesMonotonicSnapshotVersionsPerProject() {
        DatabaseFixtures.ImportChain first = fixtures.createImportChain("Calciner Shutdown");
        UUID secondBatch = fixtures.createImportBatch(first.projectId(), first.sourceFileId());

        ProjectSnapshotRecord one = repository.createSnapshot(snapshotRequest(first));
        ProjectSnapshotRecord two = repository.createSnapshot(new ProjectSnapshotCreateRequest(
                first.projectId(), secondBatch, ProjectSnapshotStatus.PARSED,
                "uid-2", "Calciner Shutdown 2026", null, Map.of()));

        assertThat(one.snapshotVersion()).isEqualTo(1);
        assertThat(two.snapshotVersion())
                .describedAs("re-importing the same project must not reuse a version")
                .isEqualTo(2);
    }

    @Test
    void snapshotVersionsAreIndependentAcrossProjects() {
        DatabaseFixtures.ImportChain kiln = fixtures.createImportChain("Kiln");
        DatabaseFixtures.ImportChain boiler = fixtures.createImportChain("Boiler");

        repository.createSnapshot(snapshotRequest(kiln));
        ProjectSnapshotRecord boilerSnapshot = repository.createSnapshot(snapshotRequest(boiler));

        assertThat(boilerSnapshot.snapshotVersion())
                .describedAs("versions are scoped per project, not global")
                .isEqualTo(1);
    }

    @Test
    void rejectsASecondSnapshotForTheSameImportBatch() {
        DatabaseFixtures.ImportChain chain = fixtures.createImportChain("Duplicate Batch");
        repository.createSnapshot(snapshotRequest(chain));

        assertThatThrownBy(() -> repository.createSnapshot(snapshotRequest(chain)))
                .describedAs("project_snapshots_import_batch_unique must hold")
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void persistsTasksWithScheduleFieldsAndRawData() {
        DatabaseFixtures.ImportChain chain = fixtures.createImportChain("Task Persistence");
        ProjectSnapshotRecord snapshot = repository.createSnapshot(snapshotRequest(chain));

        List<ImportedTaskRecord> tasks = repository.createTasks(
                chain.projectId(),
                snapshot.id(),
                List.of(
                        new ImportedTaskCreateRequest(
                                "uid-1", "1", "Erect scaffold", "1.1", "1", 1, false, null, null,
                                OffsetDateTime.of(2026, 8, 1, 6, 0, 0, 0, ZoneOffset.UTC),
                                OffsetDateTime.of(2026, 8, 3, 18, 0, 0, 0, ZoneOffset.UTC),
                                null, null,
                                new BigDecimal("25.00"), new BigDecimal("20.00"),
                                "Access permit required.",
                                Map.of("Priority", 500)),
                        new ImportedTaskCreateRequest(
                                "uid-2", "2", "Mechanical works", null, null, 0, true, null, null,
                                null, null, null, null, null, null, null, Map.of())));

        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0).name()).isEqualTo("Erect scaffold");
        assertThat(tasks.get(0).summary()).isFalse();
        assertThat(tasks.get(1).summary())
                .describedAs("the imported summary-task flag must round-trip")
                .isTrue();

        Map<String, Object> stored = jdbcTemplate().queryForMap(
                "SELECT percent_complete, notes, raw_data::text AS raw_data FROM imported_tasks WHERE external_uid = 'uid-1'");
        assertThat(((BigDecimal) stored.get("percent_complete"))).isEqualByComparingTo("25.00");
        assertThat(stored.get("notes")).isEqualTo("Access permit required.");
        assertThat((String) stored.get("raw_data")).contains("Priority");
    }

    @Test
    void enforcesThePercentCompleteRangeConstraint() {
        DatabaseFixtures.ImportChain chain = fixtures.createImportChain("Bad Percent");
        ProjectSnapshotRecord snapshot = repository.createSnapshot(snapshotRequest(chain));

        // The record guards this in Java; this asserts the database refuses it too, so a
        // future caller bypassing the record cannot store an out-of-range value.
        assertThatThrownBy(() -> jdbcTemplate().update(
                """
                INSERT INTO imported_tasks (project_id, project_snapshot_id, name, percent_complete)
                VALUES (?, ?, 'Over-complete', 150)
                """,
                chain.projectId(), snapshot.id()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void persistsResourcesAssignmentsAndExtendedAttributes() {
        DatabaseFixtures.ImportChain chain = fixtures.createImportChain("Full Snapshot");
        ProjectSnapshotRecord snapshot = repository.createSnapshot(snapshotRequest(chain));

        ImportedTaskRecord task = repository.createTasks(
                chain.projectId(), snapshot.id(),
                List.of(new ImportedTaskCreateRequest(
                        "t-1", "1", "Weld repair", null, null, 1, false, null, null,
                        null, null, null, null, null, null, null, Map.of()))).get(0);

        ImportedResourceRecord resource = repository.createResources(
                chain.projectId(), snapshot.id(),
                List.of(new ImportedResourceCreateRequest(
                        "r-1", "Boilermaker Crew", "Work", Map.of("Group", "CVM MECH")))).get(0);

        List<ImportedAssignmentRecord> assignments = repository.createAssignments(
                chain.projectId(), snapshot.id(),
                List.of(new ImportedAssignmentCreateRequest(
                        "a-1", "t-1", "r-1", task.id(), resource.id(), Map.of("Units", 1.0))));

        List<ImportedExtendedAttributeRecord> attributes = repository.createExtendedAttributes(
                chain.projectId(), snapshot.id(),
                List.of(new ImportedExtendedAttributeCreateRequest(
                        ImportedExtendedAttributeEntityType.TASK, "t-1", "188743731",
                        "Text1", "Work Group", "CVM MECH", Map.of())));

        assertThat(resource.name()).isEqualTo("Boilermaker Crew");
        assertThat(assignments).hasSize(1);
        assertThat(assignments.get(0).taskExternalUid()).isEqualTo("t-1");
        assertThat(attributes).hasSize(1);
        assertThat(attributes.get(0).entityType()).isEqualTo(ImportedExtendedAttributeEntityType.TASK);
        assertThat(attributes.get(0).fieldName()).isEqualTo("Text1");
    }

    @Test
    void rejectsAnUnknownExtendedAttributeEntityType() {
        DatabaseFixtures.ImportChain chain = fixtures.createImportChain("Bad Entity Type");
        ProjectSnapshotRecord snapshot = repository.createSnapshot(snapshotRequest(chain));

        assertThatThrownBy(() -> jdbcTemplate().update(
                """
                INSERT INTO imported_extended_attributes (project_id, project_snapshot_id, entity_type)
                VALUES (?, ?, 'not_a_real_entity')
                """,
                chain.projectId(), snapshot.id()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void linksChildTasksToTheirImportedParent() {
        DatabaseFixtures.ImportChain chain = fixtures.createImportChain("Hierarchy");
        ProjectSnapshotRecord snapshot = repository.createSnapshot(snapshotRequest(chain));

        ImportedTaskRecord parent = repository.createTasks(
                chain.projectId(), snapshot.id(),
                List.of(new ImportedTaskCreateRequest(
                        "p-1", "1", "Mechanical", null, "1", 0, true, null, null,
                        null, null, null, null, null, null, null, Map.of()))).get(0);

        ImportedTaskRecord child = repository.createTasks(
                chain.projectId(), snapshot.id(),
                List.of(new ImportedTaskCreateRequest(
                        "c-1", "2", "Remove guard", null, "1.1", 1, false, "p-1", parent.id(),
                        null, null, null, null, null, null, null, Map.of()))).get(0);

        UUID storedParent = jdbcTemplate().queryForObject(
                "SELECT parent_imported_task_id FROM imported_tasks WHERE id = ?", UUID.class, child.id());

        assertThat(storedParent)
                .describedAs("the self-referencing hierarchy foreign key must resolve")
                .isEqualTo(parent.id());
    }

    private ProjectSnapshotCreateRequest snapshotRequest(DatabaseFixtures.ImportChain chain) {
        return new ProjectSnapshotCreateRequest(
                chain.projectId(),
                chain.importBatchId(),
                ProjectSnapshotStatus.PARSED,
                "external-uid",
                "Kiln Shutdown 2026",
                OffsetDateTime.of(2026, 8, 13, 0, 0, 0, 0, ZoneOffset.UTC),
                Map.of("parser", "mpxj"));
    }
}
