package com.shutdowntracker.projectworker.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shutdowntracker.projectexport.contract.ProjectExportArtifactField;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactFieldValue;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactRequest;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactSource;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactTask;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Holds the verdict of the BOILER round-trip trial: progress on a task that carries resource
 * assignments is only exportable as the complete evidenced transaction — 100% complete with
 * actual dates, from which assignment actuals and timephased data are derived. Anything less
 * produces a candidate Microsoft Project rejects, so the exporter must refuse it loudly.
 *
 * <p>The fixture is the real BOILER WG110 schedule and task UID 43 is one of the three tasks the
 * disproven trial actually used. See {@code docs/product/project-progress-field-contract.md}.
 */
class MspdiAssignedTaskExportGuardTests {

    private static final Path BOILER_FIXTURE = Path.of("..", "..", "fixtures", "project-files",
            "boiler", "boiler-before-no-progress.xml").toAbsolutePath().normalize();

    private static ProjectExportArtifactSource BOILER_SOURCE;

    private final MpxjMspdiExportArtifactService service = new MpxjMspdiExportArtifactService();

    @TempDir
    private Path tempDir;

    @BeforeAll
    static void resolveSource() throws Exception {
        BOILER_SOURCE = new ProjectExportArtifactSource(
                UUID.fromString("00000000-0000-0000-0000-0000000000b1"),
                BOILER_FIXTURE.toUri().toString(),
                HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(BOILER_FIXTURE))
                )
        );
    }

    @Test
    void refusesCompletionWithoutActualDatesOnAssignedTask() {
        ProjectExportArtifactRequest request = boilerTask43Request(List.of(
                fieldValue(ProjectExportArtifactField.PERCENT_COMPLETE, "100")
        ));
        Path outputPath = tempDir.resolve("boiler-guard-undated.mspdi.xml");

        assertThatThrownBy(() -> service.generate(request, BOILER_FIXTURE, outputPath))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resource assignments")
                .hasMessageContaining("43")
                .hasMessageContaining("project-progress-field-contract");

        assertThat(Files.exists(outputPath))
                .as("a refused export must not leave a candidate artifact behind")
                .isFalse();
    }

    @Test
    void refusesPartialProgressOnAssignedTask() {
        ProjectExportArtifactRequest request = boilerTask43Request(List.of(
                fieldValue(ProjectExportArtifactField.PERCENT_COMPLETE, "40"),
                fieldValue(ProjectExportArtifactField.ACTUAL_START, "2026-08-17T07:30:00Z")
        ));
        Path outputPath = tempDir.resolve("boiler-guard-partial.mspdi.xml");

        assertThatThrownBy(() -> service.generate(request, BOILER_FIXTURE, outputPath))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resource assignments")
                .hasMessageContaining("43")
                .hasMessageContaining("Partial")
                .hasMessageContaining("project-progress-field-contract");

        assertThat(Files.exists(outputPath))
                .as("a refused export must not leave a candidate artifact behind")
                .isFalse();
    }

    @Test
    void refusesActualDatesAloneOnAssignedTask() {
        ProjectExportArtifactRequest request = boilerTask43Request(List.of(
                fieldValue(ProjectExportArtifactField.ACTUAL_START, "2026-08-17T07:30:00Z"),
                fieldValue(ProjectExportArtifactField.ACTUAL_FINISH, "2026-08-17T15:30:00Z")
        ));
        Path outputPath = tempDir.resolve("boiler-guard-dates-only.mspdi.xml");

        assertThatThrownBy(() -> service.generate(request, BOILER_FIXTURE, outputPath))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resource assignments")
                .hasMessageContaining("43");

        assertThat(Files.exists(outputPath)).isFalse();
    }

    private ProjectExportArtifactRequest boilerTask43Request(List<ProjectExportArtifactFieldValue> fieldValues) {
        return new ProjectExportArtifactRequest(
                "BOILER WG110 BLB001",
                BOILER_SOURCE,
                List.of(new ProjectExportArtifactTask(
                        "boiler-task-43",
                        "43",
                        "3",
                        "Conduct all pre-work scaffold lifts",
                        true,
                        fieldValues
                ))
        );
    }

    private static ProjectExportArtifactFieldValue fieldValue(ProjectExportArtifactField field, String value) {
        return new ProjectExportArtifactFieldValue(field, value);
    }
}
