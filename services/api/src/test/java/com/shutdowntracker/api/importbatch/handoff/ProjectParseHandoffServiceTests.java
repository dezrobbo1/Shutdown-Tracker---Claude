package com.shutdowntracker.api.importbatch.handoff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shutdowntracker.api.importbatch.ImportBatchRecord;
import com.shutdowntracker.api.importbatch.ImportBatchStatus;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileKind;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileMetadataRecord;
import com.shutdowntracker.projectimport.contract.ProjectParseEntitiesResponse;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryRequest;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectParseHandoffServiceTests {

    @Test
    void buildsWorkerParseRequestFromImportBatchAndSourceFileMetadata() {
        UUID projectId = UUID.randomUUID();
        UUID sourceFileId = UUID.randomUUID();
        UUID importBatchId = UUID.randomUUID();
        CapturingProjectParseJobClient client = new CapturingProjectParseJobClient(importBatchId);
        ProjectParseHandoffService service = new ProjectParseHandoffService(client);

        ProjectParseSummaryResponse response = service.requestParseSummary(
                importBatch(importBatchId, projectId, sourceFileId),
                sourceFile(sourceFileId, projectId)
        );

        assertThat(client.request.importBatchId()).isEqualTo(importBatchId);
        assertThat(client.request.projectId()).isEqualTo(projectId);
        assertThat(client.request.sourceFileId()).isEqualTo(sourceFileId);
        assertThat(client.request.storageUri()).isEqualTo("file:///synthetic/source/synthetic-basic-wbs.mspdi.xml");
        assertThat(client.request.originalFilename()).isEqualTo("synthetic-basic-wbs.mspdi.xml");
        assertThat(response.importBatchId()).isEqualTo(importBatchId);
    }

    @Test
    void rejectsSourceFileFromDifferentProject() {
        ProjectParseHandoffService service = new ProjectParseHandoffService(new CapturingProjectParseJobClient(UUID.randomUUID()));
        UUID sourceFileId = UUID.randomUUID();

        assertThatThrownBy(() -> service.buildRequest(
                importBatch(UUID.randomUUID(), UUID.randomUUID(), sourceFileId),
                sourceFile(sourceFileId, UUID.randomUUID())
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Import batch and source file must belong to the same project.");
    }

    @Test
    void rejectsSourceFileThatIsNotReferencedByImportBatch() {
        ProjectParseHandoffService service = new ProjectParseHandoffService(new CapturingProjectParseJobClient(UUID.randomUUID()));
        UUID projectId = UUID.randomUUID();

        assertThatThrownBy(() -> service.buildRequest(
                importBatch(UUID.randomUUID(), projectId, UUID.randomUUID()),
                sourceFile(UUID.randomUUID(), projectId)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Import batch must reference the selected source file.");
    }

    @Test
    void defaultClientKeepsApiDisconnectedFromParsingAndWorkerJobs() {
        DisconnectedProjectParseJobClient client = new DisconnectedProjectParseJobClient();

        assertThatThrownBy(() -> client.requestParseSummary(new ProjectParseSummaryRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "file:///synthetic/source/synthetic-basic-wbs.mspdi.xml",
                "synthetic-basic-wbs.mspdi.xml"
        )))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("MPXJ parsing runs in services/project-worker")
                .hasMessageContaining("shutdown-tracker.project-parse-worker.enabled=true");
    }

    @Test
    void defaultClientAlsoRefusesEntityParsing() {
        DisconnectedProjectParseJobClient client = new DisconnectedProjectParseJobClient();

        assertThatThrownBy(() -> client.requestParseEntities(new ProjectParseSummaryRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "file:///synthetic/source/synthetic-basic-wbs.mspdi.xml",
                "synthetic-basic-wbs.mspdi.xml"
        )))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private ImportBatchRecord importBatch(UUID importBatchId, UUID projectId, UUID sourceFileId) {
        return new ImportBatchRecord(
                importBatchId,
                projectId,
                sourceFileId,
                ImportBatchStatus.PENDING,
                null,
                null,
                0,
                0
        );
    }

    private SourceFileMetadataRecord sourceFile(UUID sourceFileId, UUID projectId) {
        return new SourceFileMetadataRecord(
                sourceFileId,
                projectId,
                "synthetic-basic-wbs.mspdi.xml",
                SourceFileKind.MSPDI_XML,
                "file:///synthetic/source/synthetic-basic-wbs.mspdi.xml",
                "synthetic-hash",
                512
        );
    }

    private static class CapturingProjectParseJobClient implements ProjectParseJobClient {

        private final UUID importBatchId;
        private ProjectParseSummaryRequest request;

        private CapturingProjectParseJobClient(UUID importBatchId) {
            this.importBatchId = importBatchId;
        }

        @Override
        public ProjectParseSummaryResponse requestParseSummary(ProjectParseSummaryRequest request) {
            this.request = request;
            return new ProjectParseSummaryResponse(
                    importBatchId,
                    "test-client",
                    "test",
                    request.originalFilename(),
                    "mspdi_xml",
                    "Synthetic Basic WBS",
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    List.of("Summary only; no schedule calculations were run.")
            );
        }

        @Override
        public ProjectParseEntitiesResponse requestParseEntities(ProjectParseSummaryRequest request) {
            return new ProjectParseEntitiesResponse(
                    requestParseSummary(request), null, null, List.of(), List.of(), List.of(), List.of());
        }
    }
}
