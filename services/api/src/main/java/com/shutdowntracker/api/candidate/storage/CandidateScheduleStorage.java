package com.shutdowntracker.api.candidate.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Where the candidate schedules Microsoft Project calculated live.
 *
 * <p>Separate from the generated-artifact store and from source files, because these are three
 * different things: the artifact Shutdown Tracker wrote, the schedule a planner uploaded back, and
 * the immutable baseline a snapshot was imported from. A returned candidate becomes a baseline only
 * if a planner adopts it and imports it deliberately.
 *
 * <p>Provider-neutral, like the evidence store: the local filesystem implementation is for
 * development and review, and production object storage replaces it without callers changing.
 */
public interface CandidateScheduleStorage {

    StoredCandidateSchedule store(CandidateScheduleStorageRequest request) throws IOException;

    /**
     * Opens a stored candidate for reading. The caller closes the stream.
     *
     * <p>A {@code storageUri} this store did not write is rejected rather than read: the value
     * reaches here from a database column, and a store that will fetch whatever URI it is handed
     * turns a row into a file-read primitive.
     */
    InputStream read(String storageUri) throws IOException;
}
