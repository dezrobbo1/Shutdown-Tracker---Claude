package com.shutdowntracker.api.candidate.storage;

import com.shutdowntracker.api.storage.LocalFileStore;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.stereotype.Service;

/**
 * Returned candidate schedules on the local filesystem, for development and review.
 *
 * <p>The bytes are handled by {@link LocalFileStore}, which also holds evidence binaries. Root
 * confinement and content hashing are the same problem in both places and are solved once; what a
 * candidate schedule is, and what may be done with it, stays here.
 */
@Service
public class LocalCandidateScheduleStorage implements CandidateScheduleStorage {

    private final LocalFileStore fileStore;

    public LocalCandidateScheduleStorage(CandidateScheduleStorageProperties properties) {
        this.fileStore = new LocalFileStore(
                properties.localRoot(),
                "Candidate schedule storage",
                "returned candidate schedule",
                "candidate.xml");
    }

    @Override
    public StoredCandidateSchedule store(CandidateScheduleStorageRequest request) throws IOException {
        LocalFileStore.StoredFile stored =
                fileStore.store(request.originalFilename(), request.content(), request.sizeBytes());

        return new StoredCandidateSchedule(
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
