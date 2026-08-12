package com.shutdowntracker.projectworker.handoff;

import static org.assertj.core.api.Assertions.assertThat;

import com.shutdowntracker.projectexport.contract.ProjectExportArtifactField;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactFieldValue;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactGenerationRequest;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactGenerationResponse;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactRequest;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactSummary;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactTask;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shutdowntracker.projectworker.exporter.ProjectExportArtifactService;
import com.shutdowntracker.projectworker.storage.WorkerStoragePathResolver;
import com.shutdowntracker.projectworker.storage.WorkerStorageProperties;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkerProjectExportArtifactHandoffServiceTests {

    @TempDir
    private Path tempDir;

    @Test
    void generatesArtifactAndReturnsFileUriAndHash() {
        UUID projectId = UUID.randomUUID();
        UUID exportBatchId = UUID.randomUUID();
        CapturingProjectExportArtifactService artifactService = new CapturingProjectExportArtifactService();
        WorkerProjectExportArtifactHandoffService service =
                new WorkerProjectExportArtifactHandoffService(artifactService, resolver());
        Path outputPath = tempDir.resolve("synthetic-export.mspdi.xml");
        ProjectExportArtifactGenerationRequest request = new ProjectExportArtifactGenerationRequest(
                exportBatchId,
                projectId,
                outputPath.toString(),
                new ProjectExportArtifactRequest(
                        "Synthetic Export Preview",
                        List.of(new ProjectExportArtifactTask(
                                "synthetic-task-a1",
                                "101",
                                "1",
                                "Synthetic Task A1",
                                true,
                                List.of(new ProjectExportArtifactFieldValue(
                                        ProjectExportArtifactField.PERCENT_COMPLETE,
                                        "75"
                                ))
                        ))
                )
        );

        ProjectExportArtifactGenerationResponse response = service.generateArtifact(request);

        assertThat(artifactService.request).isEqualTo(request.artifactRequest());
        assertThat(artifactService.outputPath).isEqualTo(outputPath.toAbsolutePath().normalize());
        assertThat(response.exportBatchId()).isEqualTo(exportBatchId);
        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.exportFileUri()).isEqualTo(outputPath.toAbsolutePath().normalize().toUri().toString());
        assertThat(response.exportFileHash()).isEqualTo("synthetic-sha256");
        assertThat(response.message()).contains("No Microsoft Project write-back");
    }

    @Test
    void rejectsOutputPathOutsideTheConfiguredArtifactRoot() {
        WorkerProjectExportArtifactHandoffService service =
                new WorkerProjectExportArtifactHandoffService(
                        new CapturingProjectExportArtifactService(),
                        resolver()
                );
        ProjectExportArtifactGenerationRequest request = new ProjectExportArtifactGenerationRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                tempDir.resolve("../escaped-export.mspdi.xml").toString(),
                new ProjectExportArtifactRequest(
                        "Synthetic Export Preview",
                        List.of(new ProjectExportArtifactTask(
                                "synthetic-task-a1",
                                "101",
                                "1",
                                "Synthetic Task A1",
                                true,
                                List.of(new ProjectExportArtifactFieldValue(
                                        ProjectExportArtifactField.PERCENT_COMPLETE,
                                        "75"
                                ))
                        ))
                )
        );

        assertThatThrownBy(() -> service.generateArtifact(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside the configured storage root");
    }

    private WorkerStoragePathResolver resolver() {
        return new WorkerStoragePathResolver(
                new WorkerStorageProperties(tempDir.resolve("source-files"), tempDir)
        );
    }

    private static class CapturingProjectExportArtifactService implements ProjectExportArtifactService {

        private ProjectExportArtifactRequest request;
        private Path outputPath;

        @Override
        public ProjectExportArtifactSummary generate(ProjectExportArtifactRequest request, Path outputPath) {
            this.request = request;
            this.outputPath = outputPath;
            return new ProjectExportArtifactSummary(
                    outputPath.getFileName().toString(),
                    "mspdi_xml",
                    request.tasks().size(),
                    request.tasks().stream().mapToInt(task -> task.fieldValues().size()).sum(),
                    512,
                    "synthetic-sha256",
                    List.of("MSPDI/XML artifact only; no schedule calculations or Microsoft Project write-back were run.")
            );
        }
    }
}
