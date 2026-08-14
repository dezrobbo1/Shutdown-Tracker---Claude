package com.shutdowntracker.api.operations;

import java.util.Objects;
import java.util.UUID;

public record HandoverNoteCreateRequest(
        UUID importedTaskId,
        UUID problemId,
        String shiftLabel,
        String note,
        boolean requiresAcknowledgement
) {
    public HandoverNoteCreateRequest {
        Objects.requireNonNull(shiftLabel, "shiftLabel is required.");
        Objects.requireNonNull(note, "note is required.");
        if (shiftLabel.isBlank()) {
            throw new IllegalArgumentException("shiftLabel is required.");
        }
        if (note.isBlank()) {
            throw new IllegalArgumentException("note is required.");
        }
        shiftLabel = shiftLabel.trim();
        note = note.trim();
    }
}
