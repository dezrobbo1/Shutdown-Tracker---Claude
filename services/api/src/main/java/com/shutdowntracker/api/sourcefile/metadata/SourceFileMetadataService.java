package com.shutdowntracker.api.sourcefile.metadata;

import com.shutdowntracker.api.sourcefile.storage.StoredSourceFile;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class SourceFileMetadataService {

    private final SourceFileMetadataRepository repository;

    public SourceFileMetadataService(SourceFileMetadataRepository repository) {
        this.repository = repository;
    }

    public Optional<SourceFileMetadataRecord> find(UUID projectId, UUID sourceFileId) {
        Objects.requireNonNull(projectId, "projectId is required.");
        Objects.requireNonNull(sourceFileId, "sourceFileId is required.");
        return repository.findByProjectIdAndId(projectId, sourceFileId);
    }

    public SourceFileMetadataRecord create(UUID projectId, StoredSourceFile storedSourceFile) {
        SourceFileKind fileKind = SourceFileKind.fromOriginalFilename(storedSourceFile.originalFilename());

        return repository.create(new SourceFileMetadataCreateRequest(
                projectId,
                storedSourceFile.originalFilename(),
                fileKind,
                storedSourceFile.storageUri(),
                storedSourceFile.contentHashSha256(),
                storedSourceFile.sizeBytes()
        ));
    }
}
