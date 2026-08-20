package com.shutdowntracker.api.operations.storage;

import com.shutdowntracker.api.storage.LocalFileStore;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.stereotype.Service;

/**
 * Evidence binaries on the local filesystem, for development and review.
 *
 * <p>Production object storage is a separate open item. This implementation exists so the rest of
 * the evidence path — upload, status, audit, read-back — can be built and proved against a real
 * store rather than waiting on one.
 *
 * <p>The bytes are handled by {@link LocalFileStore}, which is also what holds returned candidate
 * schedules. Root confinement and content hashing are the same problem in both places and are
 * solved once; what evidence is, and what may be done with it, stays here.
 */
@Service
public class LocalEvidenceStorage implements EvidenceStorage {

    private final LocalFileStore fileStore;

    public LocalEvidenceStorage(EvidenceStorageProperties properties) {
        this.fileStore = new LocalFileStore(
                properties.localRoot(), "Evidence storage", "uploaded evidence", "evidence");
    }

    @Override
    public StoredEvidence store(EvidenceStorageRequest request) throws IOException {
        LocalFileStore.StoredFile stored =
                fileStore.store(request.originalFilename(), request.content(), request.sizeBytes());

        return new StoredEvidence(
                stored.storageUri(),
                request.originalFilename(),
                stored.storedFilename(),
                stored.sizeBytes(),
                stored.contentHashSha256());
    }

    @Override
    public InputStream read(String storageUri) throws IOException {
        return fileStore.read(storageUri);
    }
}
