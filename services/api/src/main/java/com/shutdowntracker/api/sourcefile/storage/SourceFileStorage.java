package com.shutdowntracker.api.sourcefile.storage;

import java.io.IOException;

public interface SourceFileStorage {

    StoredSourceFile store(SourceFileStorageRequest request) throws IOException;
}
