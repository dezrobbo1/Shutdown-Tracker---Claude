package com.shutdowntracker.projectworker.importer;

import java.nio.file.Files;
import java.nio.file.Path;
import com.shutdowntracker.projectimport.contract.ParsedTask;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that a real Microsoft Project file yields usable entities, not just counts.
 *
 * <p>Before this path existed the parser reported that a file contained six tasks and then
 * discarded all six, so nothing downstream could reference a task. These tests read the
 * synthetic MSPDI fixture and assert the tasks themselves come back intact.
 */
class MpxjProjectParseServiceTests {

    private final MpxjProjectParseService service = new MpxjProjectParseService(
            new MpxjProjectImportSummaryService(), new MpxjProjectEntityExtractionService());

    @Test
    void returnsEveryTaskInTheFixture() {
        ParsedProject parsed = service.parse(fixture());

        assertThat(parsed.summary().taskCount()).isEqualTo(6);
        assertThat(parsed.tasks())
                .describedAs("the counted tasks must actually be returned")
                .hasSize(6);
        assertThat(parsed.tasks()).allSatisfy(task -> {
            assertThat(task.externalUid()).isNotBlank();
            assertThat(task.name()).isNotBlank();
        });
    }

    @Test
    void preservesTheSummaryFlagAndHierarchy() {
        ParsedProject parsed = service.parse(fixture());

        assertThat(parsed.tasks().stream().filter(ParsedTask::summary).count())
                .describedAs("imported summary tasks must stay distinguishable from work")
                .isEqualTo(2);

        assertThat(parsed.tasks())
                .describedAs("at least one task must carry a parent, or hierarchy was lost")
                .anySatisfy(task -> assertThat(task.parentExternalUid()).isNotNull());
    }

    @Test
    void parentsPrecedeTheirChildren() {
        ParsedProject parsed = service.parse(fixture());

        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ParsedTask task : parsed.tasks()) {
            if (task.parentExternalUid() != null) {
                assertThat(seen)
                        .describedAs("the API resolves parents as it inserts, so ordering must hold")
                        .contains(task.parentExternalUid());
            }
            seen.add(task.externalUid());
        }
    }

    @Test
    void reportsScheduleDatesWithoutInventingThem() {
        ParsedProject parsed = service.parse(fixture());

        assertThat(parsed.tasks())
                .describedAs("planned dates present in the file must survive the read")
                .anySatisfy(task -> assertThat(task.plannedStart()).isNotNull());
    }

    @Test
    void carriesProjectIdentityForTheSnapshot() {
        ParsedProject parsed = service.parse(fixture());

        assertThat(parsed.summary().projectName()).isEqualTo("Synthetic Basic WBS");
        // The fixture has no resources or assignments; the lists must be empty, not null.
        assertThat(parsed.resources()).isEmpty();
        assertThat(parsed.assignments()).isEmpty();
    }

    private Path fixture() {
        return repositoryRoot()
                .resolve("fixtures/import-export/synthetic-basic-wbs/synthetic-basic-wbs.mspdi.xml");
    }

    private Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve("fixtures").resolve("import-export"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not locate the repository root.");
    }
}
