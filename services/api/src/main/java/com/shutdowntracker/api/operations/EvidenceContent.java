package com.shutdowntracker.api.operations;

import java.io.InputStream;

/** An evidence record together with its stored binary. The caller closes {@code content}. */
public record EvidenceContent(EvidenceRecord record, InputStream content) {
}
