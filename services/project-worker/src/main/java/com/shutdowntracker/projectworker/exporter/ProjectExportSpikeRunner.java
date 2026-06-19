package com.shutdowntracker.projectworker.exporter;

import java.nio.file.Path;
import java.util.List;
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

    public ProjectExportSpikeRunner(
            ProjectExportArtifactService exportArtifactService,
            @Value("${shutdown-tracker.export-spike.output-path}") String outputPath) {
        this.exportArtifactService = exportArtifactService;
        this.outputPath = outputPath;
    }

    @Override
    public void run(String... args) {
        ProjectExportArtifactRequest request = new ProjectExportArtifactRequest(
                "Synthetic Export Preview",
                List.of(
                        new ProjectExportArtifactTask(
                                "synthetic-task-a1",
                                "1",
                                "1",
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
                                "2",
                                "2",
                                "Synthetic Task A2",
                                true,
                                List.of(
                                        new ProjectExportArtifactFieldValue(
                                                ProjectExportArtifactField.PHYSICAL_PERCENT_COMPLETE,
                                                "50"),
                                        new ProjectExportArtifactFieldValue(
                                                ProjectExportArtifactField.ACTUAL_FINISH,
                                                "2026-01-06T15:00:00Z")
                                )
                        )
                )
        );

        ProjectExportArtifactSummary summary = exportArtifactService.generate(request, Path.of(outputPath));
        LOGGER.info("Generated synthetic MSPDI/XML export artifact: {}", summary);
    }
}
