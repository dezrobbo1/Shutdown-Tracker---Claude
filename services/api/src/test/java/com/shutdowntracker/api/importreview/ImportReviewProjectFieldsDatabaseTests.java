package com.shutdowntracker.api.importreview;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutdowntracker.api.importedproject.ImportedProjectPersistenceResult;
import com.shutdowntracker.api.importedproject.ImportedProjectPersistenceService;
import com.shutdowntracker.api.importedproject.ImportedProjectSnapshotCreateRequest;
import com.shutdowntracker.api.importedproject.JdbcImportedProjectRepository;
import com.shutdowntracker.api.importedproject.ParsedProjectEntitiesMapper;
import com.shutdowntracker.api.support.AbstractDatabaseTest;
import com.shutdowntracker.api.support.DatabaseFixtures;
import com.shutdowntracker.projectimport.contract.ParsedAssignment;
import com.shutdowntracker.projectimport.contract.ParsedResource;
import com.shutdowntracker.projectimport.contract.ParsedTask;
import com.shutdowntracker.projectimport.contract.ProjectParseEntitiesResponse;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Duration and resource group survive the trip from the parser to the task section.
 *
 * <p>Both are read out of {@code raw_data} rather than a column of their own, because the importer
 * already stores them there and adding columns for values nothing writes back would be schema for
 * display. That makes them the two fields most likely to be lost silently: a rename in the
 * importer's raw-data keys would not fail a compile, it would just empty two columns. These tests
 * fail instead.
 *
 * <p>They run against a real PostgreSQL because {@code ->>} is what is under test. A fake
 * repository would assert the mapper and prove nothing about the query.
 */
class ImportReviewProjectFieldsDatabaseTests extends AbstractDatabaseTest {

    private ImportedProjectPersistenceService persistence;
    private JdbcImportReviewRepository importReview;
    private DatabaseFixtures fixtures;

    @BeforeEach
    void setUp() {
        NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(dataSource());
        persistence = new ImportedProjectPersistenceService(
                new JdbcImportedProjectRepository(template, new ObjectMapper()));
        importReview = new JdbcImportReviewRepository(template);
        fixtures = new DatabaseFixtures(jdbcTemplate());
    }

    @Test
    void readsDurationAndResourceGroupBackOutOfRawData() {
        DatabaseFixtures.ImportChain chain = fixtures.createImportChain("Kiln Shutdown");
        ImportedProjectPersistenceResult result = persist(chain);

        List<ImportReviewTaskRow> tasks =
                importReview.listTasks(chain.projectId(), result.snapshot().id());
        List<ImportReviewResourceRow> resources =
                importReview.listResources(chain.projectId(), result.snapshot().id());

        assertThat(tasks)
                .filteredOn(task -> "2".equals(task.externalUid()))
                .singleElement()
                .extracting(ImportReviewTaskRow::durationText)
                .isEqualTo("8.0h");

        assertThat(resources)
                .singleElement()
                .extracting(ImportReviewResourceRow::resourceGroup)
                .isEqualTo("CVM MECH");
    }

    /**
     * A milestone carries no duration, so the key is simply absent from its raw data. The column
     * must come back null and render as an em dash, rather than being coalesced to an empty string
     * or a placeholder somewhere along the way — a task that reads "0" where Project says nothing
     * is a task somebody will plan around.
     */
    @Test
    void reportsAnAbsentDurationAsNullRatherThanAsAValue() {
        DatabaseFixtures.ImportChain chain = fixtures.createImportChain("Kiln Shutdown");
        ImportedProjectPersistenceResult result = persist(chain);

        List<ImportReviewTaskRow> tasks =
                importReview.listTasks(chain.projectId(), result.snapshot().id());

        assertThat(tasks)
                .filteredOn(task -> "3".equals(task.externalUid()))
                .singleElement()
                .extracting(ImportReviewTaskRow::durationText)
                .isNull();
    }

    private ImportedProjectPersistenceResult persist(DatabaseFixtures.ImportChain chain) {
        return persistence.persistParsedSnapshot(new ImportedProjectSnapshotCreateRequest(
                chain.projectId(),
                chain.importBatchId(),
                "PROJ-1",
                "Kiln Shutdown 2026",
                OffsetDateTime.of(2026, 8, 25, 0, 0, 0, 0, ZoneOffset.UTC),
                Map.of("parserName", "mpxj"),
                ParsedProjectEntitiesMapper.toEntities(parseResponse())));
    }

    private ProjectParseEntitiesResponse parseResponse() {
        ProjectParseSummaryResponse summary = new ProjectParseSummaryResponse(
                java.util.UUID.randomUUID(), "mpxj", "16.4.0", "kiln.xml", "MSPDI",
                "Kiln Shutdown 2026", 3, 1, 1, 0, 1, 1, 1, 0, 0, List.of());

        return new ProjectParseEntitiesResponse(
                summary,
                "PROJ-1",
                OffsetDateTime.of(2026, 8, 25, 0, 0, 0, 0, ZoneOffset.UTC),
                List.of(
                        new ParsedTask("1", "1", "Mechanical", "1", "1", 0, true, null,
                                null, null, null, null, null, null, null,
                                Map.of("durationText", "2.0d")),
                        new ParsedTask("2", "2", "Remove guard", "1.1", "1.1", 1, false, "1",
                                null, null, null, null, null, null, null,
                                Map.of("durationText", "8.0h")),
                        // No durationText at all: a milestone, which Project gives no duration.
                        new ParsedTask("3", "3", "Guard removed", "1.2", "1.2", 1, false, "1",
                                null, null, null, null, null, null, null,
                                Map.of("milestone", true))),
                List.of(new ParsedResource("10", "Boilermaker Crew", "WORK",
                        Map.of("group", "CVM MECH"))),
                List.of(new ParsedAssignment("100", "2", "10", Map.of("units", 1.0))),
                List.of());
    }
}
