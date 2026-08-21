package com.shutdowntracker.api.exportpreview.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public interface ExportArtifactStorage {

    ExportArtifactStorageLocation prepareExportArtifact(UUID projectId, UUID exportBatchId);

    /**
     * Opens a generated artifact for reading. The caller closes the stream.
     *
     * <p>The URI arrives from {@code export_batches.export_file_uri}. Confining it to the
     * configured root is what stops a row from naming a file this store never wrote.
     */
    InputStream read(String storageUri) throws IOException;
}
