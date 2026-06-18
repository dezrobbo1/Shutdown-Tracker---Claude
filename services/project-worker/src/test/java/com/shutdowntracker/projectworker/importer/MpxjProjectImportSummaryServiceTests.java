package com.shutdowntracker.projectworker.importer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mpxj.ProjectFile;
import org.mpxj.Resource;
import org.mpxj.Task;

class MpxjProjectImportSummaryServiceTests {

    private final MpxjProjectImportSummaryService service = new MpxjProjectImportSummaryService();

    @Test
    void summarizesSyntheticInMemoryProject() {
        ProjectFile project = new ProjectFile();
        project.getProjectProperties().setName("Synthetic In-Memory Project");
        project.getProjectProperties().setFileType("synthetic");
        project.addDefaultBaseCalendar();

        Task summaryTask = project.addTask();
        summaryTask.setName("Synthetic Summary");
        summaryTask.addTask().setName("Synthetic Leaf A");
        Task assignedLeaf = summaryTask.addTask();
        assignedLeaf.setName("Synthetic Leaf B");
        project.addTask().setName("Synthetic Standalone Leaf");

        Resource resource = project.addResource();
        resource.setName("Synthetic Resource");
        assignedLeaf.addResourceAssignment(resource);

        ProjectImportSummary summary = service.summarize(project, "synthetic-in-memory.mspdi", "SyntheticReader");

        assertThat(summary.sourceFilename()).isEqualTo("synthetic-in-memory.mspdi");
        assertThat(summary.detectedFormat()).isEqualTo("synthetic");
        assertThat(summary.projectName()).isEqualTo("Synthetic In-Memory Project");
        assertThat(summary.taskCount()).isEqualTo(4);
        assertThat(summary.summaryTaskCount()).isEqualTo(1);
        assertThat(summary.leafTaskCount()).isEqualTo(3);
        assertThat(summary.resourceCount()).isEqualTo(1);
        assertThat(summary.assignmentCount()).isEqualTo(1);
        assertThat(summary.calendarCount()).isEqualTo(1);
        assertThat(summary.customFieldCount()).isEqualTo(0);
        assertThat(summary.notes()).contains("Summary only; no schedule calculations were run.");
    }

    @Test
    void includesIgnoredReadIssuesAsNotes() {
        ProjectFile project = new ProjectFile();
        project.addIgnoredError(new IllegalArgumentException("Synthetic warning"));

        ProjectImportSummary summary = service.summarize(project, "synthetic-warning.mspdi", "SyntheticReader");

        assertThat(summary.detectedFormat()).isEqualTo("SyntheticReader");
        assertThat(summary.projectName()).isEqualTo("unknown");
        assertThat(summary.notes()).contains("Ignored read issue: IllegalArgumentException: Synthetic warning");
    }
}
