package com.shutdowntracker.api.exportpreview.handoff;

import java.util.Optional;
import java.util.UUID;

public interface AcceptedSourceFileRepository {

    /** The source file an accepted snapshot was imported from, if the snapshot still exists. */
    Optional<AcceptedSourceFile> findByProjectSnapshotId(UUID projectSnapshotId);
}
