package com.shutdowntracker.api.operations.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Where evidence binaries live.
 *
 * <p>Provider-neutral on purpose: the local filesystem implementation is for development and
 * review, and production object storage replaces it without the callers changing. Nothing here
 * returns a Spring type, so the abstraction does not assume the process serving the bytes is a
 * web request.
 */
public interface EvidenceStorage {

    StoredEvidence store(EvidenceStorageRequest request) throws IOException;

    /**
     * Opens a stored evidence binary for reading.
     *
     * <p>The caller closes the stream. A {@code storageUri} this store did not write is rejected
     * rather than read: the value reaches here from a database column, and a store that will fetch
     * whatever URI it is handed turns a row into a file-read primitive.
     */
    InputStream read(String storageUri) throws IOException;
}
