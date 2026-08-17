package com.shutdowntracker.projectworker.handoff;

import static org.assertj.core.api.Assertions.assertThat;

import com.shutdowntracker.projectexport.contract.ProjectExportArtifactField;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactFieldValue;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactGenerationRequest;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactGenerationResponse;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactRequest;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactSource;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactSummary;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactTask;
import com.shutdowntracker.projectworker.exporter.ProjectExportArtifactService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkerProjectExportArtifactHandoffServiceTests {

    @TempDir
    private Path tempDir;

    /**
     * The handoff now resolves the accepted source through the same confinement the import path
     * uses, so a source file has to exist on disk for the request to be servable at all.
     */
    private ProjectExportArtifactSource sourceIn(Path directory) throws IOException {
        Path sourceFile = Files.writeString(
                directory.resolve("accepted-source.mspdi.xml"),
                "<Project xmlns=\"http://schemas.microsoft.com/project\"><Tasks/></Project>"
        );
        return new ProjectExportArtifactSource(
                UUID.fromString("00000000-0000-0000-0000-0000000000f1"),
                sourceFile.toUri().toString(),
                "synthetic-source-hash"
        );
    }

    @Test
    void generatesArtifactAndReturnsFileUriAndHash() throws IOException {
        ProjectExportArtifactSource TEST_SOURCE = sourceIn(tempDir);
        UUID projectId = UUID.randomUUID();
        UUID exportBatchId = UUID.randomUUID();
        CapturingProjectExportArtifactService artifactService = new CapturingProjectExportArtifactService();
        WorkerProjectExportArtifactHandoffService service =
                new WorkerProjectExportArtifactHandoffService(artifactService);
        Path outputPath = tempDir.resolve("synthetic-export.mspdi.xml");
        ProjectExportArtifactGenerationRequest request = new ProjectExportArtifactGenerationRequest(
                exportBatchId,
                projectId,
                outputPath.toString(),
                new ProjectExportArtifactRequest(
                        "Synthetic Export Preview",
                        TEST_SOURCE,
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
        assertThat(artifactService.sourcePath).isEqualTo(
                Path.of(java.net.URI.create(TEST_SOURCE.storageUri())).toAbsolutePath().normalize());
        assertThat(response.exportBatchId()).isEqualTo(exportBatchId);
        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.exportFileUri()).isEqualTo(outputPath.toAbsolutePath().normalize().toUri().toString());
        assertThat(response.exportFileHash()).isEqualTo("synthetic-sha256");
        assertThat(response.message()).contains("No Microsoft Project write-back");
    }

    private static class CapturingProjectExportArtifactService implements ProjectExportArtifactService {

        private ProjectExportArtifactRequest request;
        private Path sourcePath;
        private Path outputPath;

        @Override
        public ProjectExportArtifactSummary generate(
                ProjectExportArtifactRequest request,
                Path sourcePath,
                Path outputPath
        ) {
            this.request = request;
            this.sourcePath = sourcePath;
            this.outputPath = outputPath;
            return new ProjectExportArtifactSummary(
                    outputPath.getFileName().toString(),
                    "mspdi_xml",
                    request.tasks().size(),
                    6,
                    request.tasks().stream().mapToInt(task -> task.fieldValues().size()).sum(),
                    512,
                    "synthetic-sha256",
                    List.of("Candidate schedule derived from the accepted source; no schedule calculations "
                            + "or Microsoft Project write-back were run by Shutdown Tracker.")
            );
        }
    }
}
