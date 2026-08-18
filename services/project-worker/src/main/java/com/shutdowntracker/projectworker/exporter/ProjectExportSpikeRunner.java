package com.shutdowntracker.projectworker.exporter;

import com.shutdowntracker.projectexport.contract.ProjectExportArtifactField;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactFieldValue;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactRequest;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactSource;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactSummary;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactTask;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "shutdown-tracker.export-spike.output-path")
public class ProjectExportSpikeRunner implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectExportSpikeRunner.class);

    private final ProjectExportArtifactService exportArtifactService;
    private final String outputPath;
    private final String sourcePath;

    public ProjectExportSpikeRunner(
            ProjectExportArtifactService exportArtifactService,
            @Value("${shutdown-tracker.export-spike.output-path}") String outputPath,
            @Value("${shutdown-tracker.export-spike.source-path}") String sourcePath) {
        this.exportArtifactService = exportArtifactService;
        this.outputPath = outputPath;
        this.sourcePath = sourcePath;
    }

    @Override
    public void run(String... args) {
        Path source = Path.of(sourcePath);
        ProjectExportArtifactRequest request = new ProjectExportArtifactRequest(
                "Synthetic Export Preview",
                new ProjectExportArtifactSource(
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        source.toUri().toString(),
                        sha256(source)
                ),
                List.of(
                        new ProjectExportArtifactTask(
                                "synthetic-task-a1",
                                "2",
                                "2",
                                "Synthetic Task A1",
                                true,
                                List.of(
                                        new ProjectExportArtifactFieldValue(
                                                ProjectExportArtifactField.PERCENT_COMPLETE,
                                                "75"),
                                        new ProjectExportArtifactFieldValue(
                                                ProjectExportArtifactField.ACTUAL_START,
                                                "2026-01-05T07:00:00Z")
                                )
                        ),
                        new ProjectExportArtifactTask(
                                "synthetic-task-a2",
                                "3",
                                "3",
                                "Synthetic Task A2",
                                true,
                                List.of(
                                        new ProjectExportArtifactFieldValue(
                                                ProjectExportArtifactField.ACTUAL_FINISH,
                                                "2026-01-06T15:00:00Z")
                                )
                        )
                )
        );

        ProjectExportArtifactSummary summary =
                exportArtifactService.generate(request, source, Path.of(outputPath));
        LOGGER.info("Generated synthetic MSPDI/XML candidate schedule: {}", summary);
    }

    private String sha256(Path path) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
            );
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not hash the spike source schedule: " + path, exception);
        }
    }
}
