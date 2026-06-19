package com.shutdowntracker.api.sourcefile.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import com.shutdowntracker.api.sourcefile.storage.StoredSourceFile;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceFileMetadataServiceTests {

    @Test
    void createsSourceFileMetadataFromStoredSourceFile() {
        UUID projectId = UUID.randomUUID();
        FakeSourceFileMetadataRepository repository = new FakeSourceFileMetadataRepository();
        SourceFileMetadataService service = new SourceFileMetadataService(repository);
        StoredSourceFile storedSourceFile = new StoredSourceFile(
                "file:///tmp/synthetic-basic-wbs.mspdi.xml",
                "synthetic-basic-wbs.mspdi.xml",
                "synthetic-basic-wbs.mspdi.xml",
                9,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        );

        SourceFileMetadataRecord record = service.create(projectId, storedSourceFile);

        assertThat(repository.createRequest.projectId()).isEqualTo(projectId);
        assertThat(repository.createRequest.originalFilename()).isEqualTo("synthetic-basic-wbs.mspdi.xml");
        assertThat(repository.createRequest.fileKind()).isEqualTo(SourceFileKind.MSPDI_XML);
        assertThat(repository.createRequest.storageUri()).isEqualTo("file:///tmp/synthetic-basic-wbs.mspdi.xml");
        assertThat(repository.createRequest.contentHash()).isEqualTo(storedSourceFile.contentHashSha256());
        assertThat(repository.createRequest.sizeBytes()).isEqualTo(9);
        assertThat(record.projectId()).isEqualTo(projectId);
        assertThat(record.fileKind()).isEqualTo(SourceFileKind.MSPDI_XML);
    }

    private static class FakeSourceFileMetadataRepository implements SourceFileMetadataRepository {

        private SourceFileMetadataCreateRequest createRequest;

        @Override
        public Optional<SourceFileMetadataRecord> findByProjectIdAndId(UUID projectId, UUID sourceFileId) {
            return Optional.empty();
        }

        @Override
        public SourceFileMetadataRecord create(SourceFileMetadataCreateRequest request) {
            createRequest = request;
            return new SourceFileMetadataRecord(
                    UUID.randomUUID(),
                    request.projectId(),
                    request.originalFilename(),
                    request.fileKind(),
                    request.storageUri(),
                    request.contentHash(),
                    request.sizeBytes()
            );
        }
    }
}
