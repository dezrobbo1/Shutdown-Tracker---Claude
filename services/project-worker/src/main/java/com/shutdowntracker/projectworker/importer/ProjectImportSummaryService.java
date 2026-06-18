package com.shutdowntracker.projectworker.importer;

import java.nio.file.Path;

public interface ProjectImportSummaryService {

    ProjectImportSummary summarize(Path sourcePath);
}
