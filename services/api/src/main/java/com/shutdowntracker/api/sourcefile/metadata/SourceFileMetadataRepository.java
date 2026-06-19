package com.shutdowntracker.api.sourcefile.metadata;

import java.util.Optional;
import java.util.UUID;

public interface SourceFileMetadataRepository {

    Optional<SourceFileMetadataRecord> findByProjectIdAndId(UUID projectId, UUID sourceFileId);

    SourceFileMetadataRecord create(SourceFileMetadataCreateRequest request);
}
