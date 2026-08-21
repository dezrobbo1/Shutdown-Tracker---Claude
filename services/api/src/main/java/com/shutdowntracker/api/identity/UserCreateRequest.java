package com.shutdowntracker.api.identity;

import java.util.Map;
import java.util.Objects;

public record UserCreateRequest(
        String email,
        String displayName,
        UserStatus status,
        String externalSubject,
        Map<String, Object> metadata
) {

    public UserCreateRequest {
        email = requireText(email, "email is required.");
        displayName = requireText(displayName, "displayName is required.");
        status = status == null ? UserStatus.INVITED : status;
        if (externalSubject != null && externalSubject.isBlank()) {
            externalSubject = null;
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * Without metadata, which is what every caller creating a real person wants.
     *
     * <p>The column carries provenance rather than identity — currently only the marker that says a
     * row was seeded for review and is safe to treat as disposable. A caller who has nothing to say
     * about where the user came from should not have to say {@code Map.of()} to make that clear.
     */
    public UserCreateRequest(String email, String displayName, UserStatus status, String externalSubject) {
        this(email, displayName, status, externalSubject, Map.of());
    }

    private static String requireText(String value, String message) {
        Objects.requireNonNull(value, message);
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
