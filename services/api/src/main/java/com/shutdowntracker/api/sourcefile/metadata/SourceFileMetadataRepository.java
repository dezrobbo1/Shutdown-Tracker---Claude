package com.shutdowntracker.api.sourcefile.metadata;

public interface SourceFileMetadataRepository {

    SourceFileMetadataRecord create(SourceFileMetadataCreateRequest request);
}
