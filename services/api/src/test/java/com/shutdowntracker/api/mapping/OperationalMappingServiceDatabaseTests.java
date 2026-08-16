package com.shutdowntracker.api.mapping;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.importedproject.ImportedAssignmentCreateRequest;
import com.shutdowntracker.api.importedproject.ImportedExtendedAttributeCreateRequest;
import com.shutdowntracker.api.importedproject.ImportedExtendedAttributeEntityType;
import com.shutdowntracker.api.importedproject.ImportedProjectEntities;
import com.shutdowntracker.api.importedproject.ImportedProjectPersistenceService;
import com.shutdowntracker.api.importedproject.ImportedProjectSnapshotCreateRequest;
import com.shutdowntracker.api.importedproject.ImportedResourceCreateRequest;
import com.shutdowntracker.api.importedproject.ImportedTaskCreateRequest;
import com.shutdowntracker.api.importedproject.JdbcImportedProjectRepository;
import com.shutdowntracker.api.support.AbstractDatabaseTest;
import com.shutdowntracker.api.support.DatabaseFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises Operational Category resolution against real imported data.
 *
 * <p>The fixture is a small but realistic shape: a summary task with two leaves, one
 * aliased custom field, and a task assigned to crews from two different Resource Groups.
 */
class OperationalMappingServiceDatabaseTests extends AbstractDatabaseTest {

    private OperationalMappingService service;
    private OperationalMappingRepository repository;
    private DatabaseFixtures fixtures;
    private UUID projectId;
    private UUID snapshotId;
    private UUID profileId;
    private Actor planner;

    @BeforeEach
    void setUp() {
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(dataSource());
        repository = new JdbcOperationalMappingRepository(named);
        service = new OperationalMappingService(repository);
        fixtures = new DatabaseFixtures(jdbcTemplate());

        DatabaseFixtures.ImportChain chain = fixtures.createImportChain("Kiln Shutdown");
        projectId = chain.projectId();
        UUID plannerId = fixtures.createUser("planner@example.com", "Planner");
        fixtures.grantMembership(projectId, plannerId, "planner");
        planner = new Actor(plannerId, "planner", "Planner");

        snapshotId = new ImportedProjectPersistenceService(
                new JdbcImportedProjectRepository(named, new ObjectMapper()))
                .persistParsedSnapshot(new ImportedProjectSnapshotCreateRequest(
                        projectId, chain.importBatchId(), "PROJ-1", "Kiln", null, Map.of(),
                        new ImportedProjectEntities(
                                List.of(
                                        task("1", "Mechanical", true, 0, null),
                                        task("2", "Remove guard", false, 1, "1"),
                                        task("3", "Weld repair", false, 1, "1")),
                                List.of(
                                        resource("10", "Fitter Crew", "CVM MECH"),
                                        resource("11", "Welder Crew", "CVM WELD")),
                                List.of(
                                        assignment("100", "2", "10"),
                                        assignment("101", "3", "10"),
                                        assignment("102", "3", "11")),
                                List.of(
                                        attribute("2", "Text1", "Work Group", "CVM MECH"),
                                        attribute("3", "Text1", "Work Group", "CVM WELD")))))
                .snapshot().id();

        profileId = service.createProfile(projectId, planner, "Kiln Standard", "Shared template").id();
        service.activateProfile(projectId, planner, profileId);
    }

    @Test
    void resolvesACategoryFromAnAliasedCustomField() {
        OperationalCategoryRecord category = service.addCategory(projectId, profileId,
                new OperationalCategoryCreateRequest(
                        "Work Group", CategorySourceMode.TASK_FIELD, "Work Group", null, false, false));

        CategoryResolutionSummary summary = service.resolveCategory(projectId, snapshotId, category);

        assertThat(summary.taskCount()).isEqualTo(2);
        assertThat(summary.distinctValueCount()).isEqualTo(2);
        assertThat(repository.distinctValues(category.id(), snapshotId))
                .describedAs("source values are stored exactly as imported")
                .containsExactly("CVM MECH", "CVM WELD");
    }

    @Test
    void resolvesACategoryFromSummaryTaskAncestry() {
        OperationalCategoryRecord category = service.addCategory(projectId, profileId,
                new OperationalCategoryCreateRequest(
                        "Discipline", CategorySourceMode.HIERARCHY_ANCESTOR, null, 0, false, false));

        CategoryResolutionSummary summary = service.resolveCategory(projectId, snapshotId, category);

        assertThat(summary.taskCount())
                .describedAs("both leaves inherit from the summary task; the summary does not classify itself")
                .isEqualTo(2);
        assertThat(repository.distinctValues(category.id(), snapshotId)).containsExactly("Mechanical");
    }

    @Test
    void resolvesMultipleResourceGroupsForOneTask() {
        OperationalCategoryRecord category = service.addCategory(projectId, profileId,
                new OperationalCategoryCreateRequest(
                        "Crew", CategorySourceMode.RESOURCE_GROUP, null, null, false, false));

        service.resolveCategory(projectId, snapshotId, category);

        UUID weldTask = taskIdFor("3");
        assertThat(repository.valuesForTask(category.id(), weldTask))
                .describedAs("a task assigned across two Resource Groups belongs to both")
                .containsExactly("CVM MECH", "CVM WELD");
    }

    @Test
    void aResourceGroupCategoryIsAlwaysMultiValued() {
        OperationalCategoryRecord category = service.addCategory(projectId, profileId,
                new OperationalCategoryCreateRequest(
                        "Crew", CategorySourceMode.RESOURCE_GROUP, null, null, false, false));

        assertThat(category.multiValued())
                .describedAs("the caller asked for single-valued, but the source cannot honour that")
                .isTrue();
    }

    @Test
    void recordsProvenanceForEveryResolvedValue() {
        OperationalCategoryRecord category = service.addCategory(projectId, profileId,
                new OperationalCategoryCreateRequest(
                        "Discipline", CategorySourceMode.HIERARCHY_ANCESTOR, null, 0, false, false));
        service.resolveCategory(projectId, snapshotId, category);

        Map<String, Object> stored = jdbcTemplate().queryForMap(
                """
                SELECT resolved_via::text, resolved_from_reference
                FROM task_category_values
                WHERE operational_category_id = ? LIMIT 1
                """,
                category.id());

        assertThat(stored.get("resolved_via")).isEqualTo("hierarchy_ancestor");
        assertThat(stored.get("resolved_from_reference"))
                .describedAs("why a task is in a category must survive a later re-import")
                .isEqualTo("outline level 0");
    }

    @Test
    void reportsBrokenWhenTheSourceFieldNoLongerResolves() {
        OperationalCategoryRecord category = service.addCategory(projectId, profileId,
                new OperationalCategoryCreateRequest(
                        "Area", CategorySourceMode.TASK_FIELD, "Text9 Renamed Away", null, false, false));

        CategoryResolutionSummary summary = service.resolveCategory(projectId, snapshotId, category);

        assertThat(summary.health())
                .describedAs("a source that resolves nothing must not be silently remapped")
                .isEqualTo(MappingHealth.BROKEN);
        assertThat(summary.health().needsPlannerAttention()).isTrue();
    }

    @Test
    void resolvingTwiceIsIdempotent() {
        OperationalCategoryRecord category = service.addCategory(projectId, profileId,
                new OperationalCategoryCreateRequest(
                        "Work Group", CategorySourceMode.TASK_FIELD, "Work Group", null, false, false));

        service.resolveCategory(projectId, snapshotId, category);
        CategoryResolutionSummary second = service.resolveCategory(projectId, snapshotId, category);

        assertThat(second.taskCount()).isEqualTo(2);
        assertThat(jdbcTemplate().queryForObject(
                "SELECT count(*) FROM task_category_values", Integer.class))
                .describedAs("re-running must not accumulate duplicates")
                .isEqualTo(2);
    }

    @Test
    void resolvesEveryCategoryInTheActiveProfile() {
        service.addCategory(projectId, profileId, new OperationalCategoryCreateRequest(
                "Work Group", CategorySourceMode.TASK_FIELD, "Work Group", null, false, false));
        service.addCategory(projectId, profileId, new OperationalCategoryCreateRequest(
                "Discipline", CategorySourceMode.HIERARCHY_ANCESTOR, null, 0, false, false));
        service.addCategory(projectId, profileId, new OperationalCategoryCreateRequest(
                "Crew", CategorySourceMode.RESOURCE_GROUP, null, null, false, false));

        assertThat(service.resolveSnapshot(projectId, snapshotId)).hasSize(3);
    }

    @Test
    void flagsLeafTasksMissingARequiredClassification() {
        // Only task 2 and 3 carry Work Group, and both are leaves, so nothing is missing.
        service.addCategory(projectId, profileId, new OperationalCategoryCreateRequest(
                "Work Group", CategorySourceMode.TASK_FIELD, "Work Group", null, false, true));
        service.resolveSnapshot(projectId, snapshotId);
        assertThat(service.tasksMissingRequiredCategories(projectId, snapshotId)).isEmpty();

        // A required category no source satisfies leaves every leaf task unclassified.
        service.addCategory(projectId, profileId, new OperationalCategoryCreateRequest(
                "Area", CategorySourceMode.TASK_FIELD, "Missing Field", null, false, true));
        service.resolveSnapshot(projectId, snapshotId);

        assertThat(service.tasksMissingRequiredCategories(projectId, snapshotId))
                .describedAs("summary tasks are reporting groups, not work, so they are excluded")
                .hasSize(2);
    }

    @Test
    void onlyOneProfileDrivesAProjectAtATime() {
        UUID second = service.createProfile(projectId, planner, "Alternative", null).id();
        service.activateProfile(projectId, planner, second);

        assertThat(service.requireActiveProfile(projectId).id()).isEqualTo(second);
        assertThat(jdbcTemplate().queryForObject(
                "SELECT count(*) FROM import_profiles WHERE project_id = ? AND active", Integer.class, projectId))
                .isEqualTo(1);
    }

    @Test
    void profileVersionsIncrementPerName() {
        ImportProfileRecord v2 = service.createProfile(projectId, planner, "Kiln Standard", null);

        assertThat(v2.version())
                .describedAs("a revised convention is a new version, not an overwrite")
                .isEqualTo(2);
    }

    @Test
    void resolvingWithoutAnActiveProfileIsRefused() {
        jdbcTemplate().update("UPDATE import_profiles SET active = false WHERE project_id = ?", projectId);

        assertThatThrownBy(() -> service.resolveSnapshot(projectId, snapshotId))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void aTaskFieldCategoryWithoutASourceFieldIsRejected() {
        assertThatThrownBy(() -> new OperationalCategoryCreateRequest(
                "Broken", CategorySourceMode.TASK_FIELD, null, null, false, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aHierarchyCategoryWithoutAnOutlineLevelIsRejected() {
        assertThatThrownBy(() -> new OperationalCategoryCreateRequest(
                "Broken", CategorySourceMode.HIERARCHY_ANCESTOR, null, null, false, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private UUID taskIdFor(String externalUid) {
        return jdbcTemplate().queryForObject(
                "SELECT id FROM imported_tasks WHERE external_uid = ?", UUID.class, externalUid);
    }

    private ImportedTaskCreateRequest task(
            String uid, String name, boolean summary, int outlineLevel, String parentUid) {
        return new ImportedTaskCreateRequest(
                uid, uid, name, null, null, outlineLevel, summary, parentUid, null,
                null, null, null, null, null, null, null, Map.of());
    }

    private ImportedResourceCreateRequest resource(String uid, String name, String group) {
        return new ImportedResourceCreateRequest(uid, name, "WORK", Map.of("group", group));
    }

    private ImportedAssignmentCreateRequest assignment(String uid, String taskUid, String resourceUid) {
        return new ImportedAssignmentCreateRequest(uid, taskUid, resourceUid, null, null, Map.of());
    }

    private ImportedExtendedAttributeCreateRequest attribute(
            String taskUid, String fieldName, String alias, String value) {
        return new ImportedExtendedAttributeCreateRequest(
                ImportedExtendedAttributeEntityType.TASK, taskUid, "FIELD", fieldName, alias, value, Map.of());
    }
}
