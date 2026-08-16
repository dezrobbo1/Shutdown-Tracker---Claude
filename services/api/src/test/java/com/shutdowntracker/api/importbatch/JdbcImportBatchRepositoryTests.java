package com.shutdowntracker.api.importbatch;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutdowntracker.api.support.AbstractDatabaseTest;
import com.shutdowntracker.api.support.DatabaseFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link JdbcImportBatchRepository} against a real PostgreSQL server, covering
 * the batch lifecycle an import moves through: created, parsing, parsed, or failed.
 */
class JdbcImportBatchRepositoryTests extends AbstractDatabaseTest {

    private JdbcImportBatchRepository repository;
    private DatabaseFixtures fixtures;
    private UUID projectId;
    private UUID sourceFileId;

    @BeforeEach
    void setUp() {
        repository = new JdbcImportBatchRepository(
                new NamedParameterJdbcTemplate(dataSource()), new ObjectMapper());
        fixtures = new DatabaseFixtures(jdbcTemplate());
        projectId = fixtures.createProject("Import Batch Lifecycle");
        sourceFileId = fixtures.createSourceFile(projectId);
    }

    @Test
    void createsABatchInPendingState() {
        ImportBatchRecord created = repository.create(new ImportBatchCreateRequest(projectId, sourceFileId));

        assertThat(created.id()).isNotNull();
        assertThat(created.projectId()).isEqualTo(projectId);
        assertThat(created.sourceFileId()).isEqualTo(sourceFileId);
        assertThat(created.status()).isEqualTo(ImportBatchStatus.PENDING);
        assertThat(created.warningCount()).isZero();
        assertThat(created.errorCount()).isZero();
    }

    @Test
    void findsABatchScopedToItsProject() {
        ImportBatchRecord created = repository.create(new ImportBatchCreateRequest(projectId, sourceFileId));

        assertThat(repository.findByProjectIdAndId(projectId, created.id()))
                .map(ImportBatchRecord::id)
                .contains(created.id());
    }

    @Test
    void doesNotReturnABatchBelongingToAnotherProject() {
        ImportBatchRecord created = repository.create(new ImportBatchCreateRequest(projectId, sourceFileId));
        UUID otherProject = fixtures.createProject("Unrelated Project");

        Optional<ImportBatchRecord> found = repository.findByProjectIdAndId(otherProject, created.id());

        assertThat(found)
                .describedAs("project scoping must not leak batches across projects")
                .isEmpty();
    }

    @Test
    void movesThroughTheParsingLifecycle() {
        ImportBatchRecord created = repository.create(new ImportBatchCreateRequest(projectId, sourceFileId));

        ImportBatchRecord parsing = repository.updateStatus(created.id(), ImportBatchStatus.PARSING);
        assertThat(parsing.status()).isEqualTo(ImportBatchStatus.PARSING);

        ImportBatchRecord parsed = repository.recordParseSummary(new ImportBatchParseSummaryUpdate(
                created.id(), "mpxj", "16.4.0", 3, 0, parseSummary()));

        assertThat(parsed.status()).isEqualTo(ImportBatchStatus.PARSED);
        assertThat(parsed.parserName()).isEqualTo("mpxj");
        assertThat(parsed.parserVersion()).isEqualTo("16.4.0");
        assertThat(parsed.warningCount()).isEqualTo(3);
    }

    @Test
    void recordsATerminalParseFailure() {
        ImportBatchRecord created = repository.create(new ImportBatchCreateRequest(projectId, sourceFileId));
        repository.updateStatus(created.id(), ImportBatchStatus.PARSING);

        ImportBatchRecord failed = repository.recordParseFailure(created.id(), "Unsupported file format.");

        assertThat(failed.status()).isEqualTo(ImportBatchStatus.FAILED);

        String storedSummary = jdbcTemplate().queryForObject(
                "SELECT parse_summary::text FROM import_batches WHERE id = ?", String.class, created.id());
        assertThat(storedSummary)
                .describedAs("the failure reason must be retrievable for review")
                .contains("Unsupported file format.");
    }

    @Test
    void storesTheParseSummaryAsQueryableJsonb() {
        ImportBatchRecord created = repository.create(new ImportBatchCreateRequest(projectId, sourceFileId));
        repository.recordParseSummary(new ImportBatchParseSummaryUpdate(
                created.id(), "mpxj", "16.4.0", 0, 0, parseSummary()));

        Integer taskCount = jdbcTemplate().queryForObject(
                "SELECT (parse_summary #>> '{counts,taskCount}')::int FROM import_batches WHERE id = ?",
                Integer.class, created.id());

        assertThat(taskCount)
                .describedAs("summary values must be addressable inside jsonb, not just stored as text")
                .isEqualTo(3055);
    }

    private static ImportBatchParseSummary parseSummary() {
        return new ImportBatchParseSummary(
                "KILN-WG047K.xml",
                "MSPDI",
                "Kiln Shutdown 2026",
                new ImportBatchParseSummaryCounts(3055, 210, 2845, 159, 4174, 12, 37),
                true,
                List.of("Parsed with warnings."));
    }
}
