package com.shutdowntracker.projectworker.importer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every approved synthetic fixture, read against the counts its own manifest declares.
 *
 * <p>This used to name {@code synthetic-basic-wbs} in eight places, including the helper that
 * located the repository root. A second fixture would have cost eight edits and, more to the point,
 * a fixture added without them would have been checked by nothing at all. The fixtures are
 * discovered instead, and each is asserted against the manifest beside it — so adding one costs
 * nothing and skipping the tests is not something a new fixture can quietly do.
 *
 * <p>The manifest is the expectation rather than a copy of it. A fixture whose file and manifest
 * disagree is exactly the defect worth catching, and asserting one against the other is the only
 * way this catches it.
 */
class SyntheticMspdiFixtureImportTests {

    private static final String NO_SCHEDULE_CALCULATION_NOTE = "Summary only; no schedule calculations were run.";

    /**
     * Terms that would mean a fixture had stopped being synthetic, or had started describing
     * scheduling this product does not do.
     */
    private static final List<String> FORBIDDEN_TERMS = List.of(
            "critical path", "float", "resource levelling", "resource leveling",
            "write-back", "work order", "vendor", "contractor");

    private final MpxjProjectImportSummaryService service = new MpxjProjectImportSummaryService();
    private final ObjectMapper objectMapper = new ObjectMapper();

    static Stream<Fixture> approvedFixtures() throws IOException {
        Path root = repositoryRoot().resolve("fixtures/import-export");
        try (Stream<Path> directories = Files.list(root)) {
            List<Fixture> fixtures = directories
                    .filter(Files::isDirectory)
                    .map(directory -> directory.resolve("fixture-manifest.json"))
                    .filter(Files::isRegularFile)
                    .map(Fixture::of)
                    .sorted((left, right) -> left.id().compareTo(right.id()))
                    .toList();
            // Discovery that finds nothing would let this whole class pass without reading a file.
            Assertions.assertFalse(fixtures.isEmpty(), "no approved fixtures were discovered");
            return fixtures.stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("approvedFixtures")
    void readsFixtureAndMatchesTheCountsItsManifestDeclares(Fixture fixture) {
        JsonNode manifest = readJson(fixture.manifestPath());
        JsonNode counts = manifest.get("expected_counts");

        ProjectImportSummary summary = service.summarize(fixture.mspdiPath());

        assertThat(summary.sourceFilename()).isEqualTo(fixture.mspdiPath().getFileName().toString());
        assertThat(summary.detectedFormat()).isNotBlank();
        assertThat(summary.projectName()).isEqualTo(manifest.get("fixture_name").asText());
        assertThat(summary.taskCount()).isEqualTo(counts.get("tasks").asInt());
        assertThat(summary.summaryTaskCount()).isEqualTo(counts.get("summary_tasks").asInt());
        assertThat(summary.leafTaskCount()).isEqualTo(counts.get("leaf_tasks").asInt());
        assertThat(summary.resourceCount()).isEqualTo(counts.get("resources").asInt());
        assertThat(summary.assignmentCount()).isEqualTo(counts.get("assignments").asInt());
        assertThat(summary.calendarCount()).isEqualTo(counts.get("calendars").asInt());
        assertThat(summary.customFieldCount()).isEqualTo(counts.get("custom_fields").asInt());
        assertThat(summary.notes()).containsExactly(NO_SCHEDULE_CALCULATION_NOTE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("approvedFixtures")
    void expectedSummaryAgreesWithTheManifestAndRecordsNoScheduleCalculations(Fixture fixture) {
        Path expectedSummaryPath = fixture.directory().resolve("expected-import-summary.json");
        assertThat(expectedSummaryPath).exists();

        JsonNode expected = readJson(expectedSummaryPath);
        JsonNode manifestCounts = readJson(fixture.manifestPath()).get("expected_counts");

        assertThat(expected.get("fixture_id").asText()).isEqualTo(fixture.id());
        // Two files describing the same fixture is one file too many unless something makes them
        // agree. This is that something.
        for (String key : List.of("tasks", "summary_tasks", "leaf_tasks", "resources",
                "assignments", "calendars", "custom_fields")) {
            assertThat(expected.get("counts").get(key).asInt())
                    .describedAs("expected-import-summary.json and fixture-manifest.json disagree on %s", key)
                    .isEqualTo(manifestCounts.get(key).asInt());
        }
        assertThat(expected.get("expected_notes").toString()).contains(NO_SCHEDULE_CALCULATION_NOTE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("approvedFixtures")
    void fixtureDoesNotContainSchedulerOrRealProjectTerms(Fixture fixture) {
        String fixtureXml = readString(fixture.mspdiPath()).toLowerCase();

        assertThat(FORBIDDEN_TERMS)
                .describedAs("%s must stay synthetic and free of scheduling this product does not do",
                        fixture.id())
                .allSatisfy(term -> assertThat(fixtureXml).doesNotContain(term));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("approvedFixtures")
    void manifestMarksFixtureAsSyntheticAndSafeToCommit(Fixture fixture) {
        JsonNode manifest = readJson(fixture.manifestPath());

        assertThat(manifest.get("fixture_id").asText()).isEqualTo(fixture.id());
        assertThat(manifest.get("synthetic_or_sanitized").asText()).isEqualTo("synthetic");
        assertThat(manifest.get("contains_real_project_data").asBoolean()).isFalse();
        assertThat(manifest.get("allowed_commit").asBoolean()).isTrue();
    }

    /** One approved fixture folder, named by the manifest inside it. */
    record Fixture(String id, Path directory, Path manifestPath, Path mspdiPath) {

        static Fixture of(Path manifestPath) {
            Path directory = manifestPath.getParent();
            String id = directory.getFileName().toString();
            return new Fixture(id, directory, manifestPath, directory.resolve(id + ".mspdi.xml"));
        }

        @Override
        public String toString() {
            return id;
        }
    }

    private JsonNode readJson(Path path) {
        try {
            return objectMapper.readTree(Files.readString(path));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read fixture JSON: " + path, exception);
        }
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read test file: " + path, exception);
        }
    }

    /**
     * The repository root, found by the fixture <em>folder</em> rather than by one named file
     * inside it — locating the root by a fixture is what tied this class to a single fixture.
     */
    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve("fixtures/import-export"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Repository root with an import/export fixture folder was not found.");
    }
}
