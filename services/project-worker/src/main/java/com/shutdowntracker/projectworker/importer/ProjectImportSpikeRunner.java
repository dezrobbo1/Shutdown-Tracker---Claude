package com.shutdowntracker.projectworker.importer;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "shutdown-tracker.import-spike.path")
public class ProjectImportSpikeRunner implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectImportSpikeRunner.class);

    private final ProjectImportSummaryService summaryService;
    private final String sourcePath;

    public ProjectImportSpikeRunner(
            ProjectImportSummaryService summaryService,
            @Value("${shutdown-tracker.import-spike.path}") String sourcePath
    ) {
        this.summaryService = summaryService;
        this.sourcePath = sourcePath;
    }

    @Override
    public void run(String... args) {
        ProjectImportSummary summary = summaryService.summarize(Path.of(sourcePath));
        LOGGER.info("MPXJ import spike summary: {}", summary);
    }
}
