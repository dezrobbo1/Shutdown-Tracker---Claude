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
import com.shutdowntracker.projectimport.contract.ParsedAssignment;
import com.shutdowntracker.projectimport.contract.ParsedExtendedAttribute;
import com.shutdowntracker.projectimport.contract.ParsedResource;
import com.shutdowntracker.projectimport.contract.ParsedTask;
import com.shutdowntracker.projectimport.contract.ProjectParseEntitiesResponse;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof that a parsed Microsoft Project schedule reaches the database intact.
 *
 * <p>This is the path the product was missing: the worker's parse response goes through
 * the mapper and the persistence service into real tables, and the hierarchy and
 * assignment links that arrive as file-local identifiers come out as resolved foreign
 * keys.
 */
class ImportedProjectPersistenceDatabaseTests extends AbstractDatabaseTest {

    private ImportedProjectPersistenceService service;
    private DatabaseFixtures fixtures;

    @BeforeEach
    void setUp() {
        service = new ImportedProjectPersistenceService(new JdbcImportedProjectRepository(
                new NamedParameterJdbcTemplate(dataSource()), new ObjectMapper()));
        fixtures = new DatabaseFixtures(jdbcTemplate());
    }

    @Test
    void storesAParsedScheduleWithResolvedHierarchyAndAssignments() {
        DatabaseFixtures.ImportChain chain = fixtures.createImportChain("Kiln Shutdown");

        ImportedProjectPersistenceResult result = service.persistParsedSnapshot(
                new ImportedProjectSnapshotCreateRequest(
                        chain.projectId(),
                        chain.importBatchId(),
                        "PROJ-1",
                        "Kiln Shutdown 2026",
                        OffsetDateTime.of(2026, 8, 13, 0, 0, 0, 0, ZoneOffset.UTC),
                        Map.of("parserName", "mpxj"),
                        ParsedProjectEntitiesMapper.toEntities(parseResponse())));

        assertThat(result.taskCount()).isEqualTo(3);
        assertThat(result.resourceCount()).isEqualTo(1);
        assertThat(result.assignmentCount()).isEqualTo(1);
        assertThat(result.extendedAttributeCount()).isEqualTo(1);

        // Hierarchy: both children must point at the stored parent row.
        UUID parentId = jdbcTemplate().queryForObject(
                "SELECT id FROM imported_tasks WHERE external_uid = '1'", UUID.class);
        List<UUID> childParents = jdbcTemplate().queryForList(
                "SELECT parent_imported_task_id FROM imported_tasks WHERE external_uid IN ('2','3')",
                UUID.class);

        assertThat(childParents)
                .describedAs("parent_external_uid must be resolved to a real foreign key")
                .containsOnly(parentId);

        // Assignment: both sides resolved to stored rows.
        Map<String, Object> assignment = jdbcTemplate().queryForMap(
                "SELECT imported_task_id, imported_resource_id FROM imported_assignments");
        assertThat(assignment.get("imported_task_id")).isNotNull();
        assertThat(assignment.get("imported_resource_id")).isNotNull();
    }

    @Test
    void keepsScheduleValuesExactlyAsParsed() {
        DatabaseFixtures.ImportChain chain = fixtures.createImportChain("Value Fidelity");

        service.persistParsedSnapshot(new ImportedProjectSnapshotCreateRequest(
                chain.projectId(), chain.importBatchId(), "PROJ-2", "Value Fidelity",
                null, Map.of(), ParsedProjectEntitiesMapper.toEntities(parseResponse())));

        Map<String, Object> stored = jdbcTemplate().queryForMap(
                """
                SELECT name, wbs, outline_level, is_summary, planned_start, percent_complete, notes
                FROM imported_tasks WHERE external_uid = '2'
                """);

        assertThat(stored.get("name")).isEqualTo("Remove guard");
        assertThat(stored.get("wbs")).isEqualTo("1.1");
        assertThat(stored.get("outline_level")).isEqualTo(1);
        assertThat(stored.get("is_summary")).isEqualTo(false);
        assertThat((BigDecimal) stored.get("percent_complete")).isEqualByComparingTo("50.00");
        assertThat(stored.get("planned_start")).isNotNull();
        assertThat(stored.get("notes")).isEqualTo("Permit required.");
    }

    @Test
    void storesTheAliasedCustomFieldOperationalCategoriesDependOn() {
        DatabaseFixtures.ImportChain chain = fixtures.createImportChain("Custom Fields");

        service.persistParsedSnapshot(new ImportedProjectSnapshotCreateRequest(
                chain.projectId(), chain.importBatchId(), "PROJ-3", "Custom Fields",
                null, Map.of(), ParsedProjectEntitiesMapper.toEntities(parseResponse())));

        Map<String, Object> attribute = jdbcTemplate().queryForMap(
                "SELECT entity_type, field_name, alias, value FROM imported_extended_attributes");

        assertThat(attribute.get("entity_type")).isEqualTo("task");
        assertThat(attribute.get("alias")).isEqualTo("Work Group");
        assertThat(attribute.get("value"))
                .describedAs("the imported source value must be stored unchanged")
                .isEqualTo("CVM MECH");
    }

    @Test
    void reimportingCreatesASecondSnapshotWithoutTouchingTheFirst() {
        DatabaseFixtures.ImportChain chain = fixtures.createImportChain("Re-import");
        UUID secondBatch = fixtures.createImportBatch(chain.projectId(), chain.sourceFileId());

        service.persistParsedSnapshot(new ImportedProjectSnapshotCreateRequest(
                chain.projectId(), chain.importBatchId(), "PROJ-4", "Re-import",
                null, Map.of(), ParsedProjectEntitiesMapper.toEntities(parseResponse())));
        ImportedProjectPersistenceResult second = service.persistParsedSnapshot(
                new ImportedProjectSnapshotCreateRequest(
                        chain.projectId(), secondBatch, "PROJ-4", "Re-import",
                        null, Map.of(), ParsedProjectEntitiesMapper.toEntities(parseResponse())));

        assertThat(second.snapshot().snapshotVersion()).isEqualTo(2);
        Integer totalTasks = jdbcTemplate().queryForObject(
                "SELECT count(*) FROM imported_tasks", Integer.class);
        assertThat(totalTasks)
                .describedAs("imported snapshots are immutable, so both versions coexist")
                .isEqualTo(6);
    }

    /** Mirrors what the worker returns for a small schedule with one crew assignment. */
    private ProjectParseEntitiesResponse parseResponse() {
        ProjectParseSummaryResponse summary = new ProjectParseSummaryResponse(
                UUID.randomUUID(), "mpxj", "16.4.0", "kiln.xml", "MSPDI", "Kiln Shutdown 2026",
                3, 1, 2, 1, 1, 1, 1, 0, 0, List.of());

        return new ProjectParseEntitiesResponse(
                summary,
                "PROJ-1",
                OffsetDateTime.of(2026, 8, 13, 0, 0, 0, 0, ZoneOffset.UTC),
                List.of(
                        new ParsedTask("1", "1", "Mechanical", "1", "1", 0, true, null,
                                OffsetDateTime.of(2026, 8, 1, 6, 0, 0, 0, ZoneOffset.UTC),
                                OffsetDateTime.of(2026, 8, 5, 18, 0, 0, 0, ZoneOffset.UTC),
                                null, null, null, null, null, Map.of()),
                        new ParsedTask("2", "2", "Remove guard", "1.1", "1.1", 1, false, "1",
                                OffsetDateTime.of(2026, 8, 1, 6, 0, 0, 0, ZoneOffset.UTC),
                                OffsetDateTime.of(2026, 8, 2, 18, 0, 0, 0, ZoneOffset.UTC),
                                null, null, new BigDecimal("50"), null, "Permit required.", Map.of()),
                        new ParsedTask("3", "3", "Weld repair", "1.2", "1.2", 1, false, "1",
                                null, null, null, null, null, null, null, Map.of("milestone", false))),
                List.of(new ParsedResource("10", "Boilermaker Crew", "WORK", Map.of("group", "CVM MECH"))),
                List.of(new ParsedAssignment("100", "2", "10", Map.of("units", 1.0))),
                List.of(new ParsedExtendedAttribute("task", "2", "TEXT1", "Text1",
                        "Work Group", "CVM MECH", Map.of())));
    }
}
