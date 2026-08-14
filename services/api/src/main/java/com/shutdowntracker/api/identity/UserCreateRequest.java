package com.shutdowntracker.api.identity;

import java.util.Objects;

public record UserCreateRequest(String email, String displayName, UserStatus status, String externalSubject) {

    public UserCreateRequest {
        email = requireText(email, "email is required.");
        displayName = requireText(displayName, "displayName is required.");
        status = status == null ? UserStatus.INVITED : status;
        if (externalSubject != null && externalSubject.isBlank()) {
            externalSubject = null;
        }
    }

    private static String requireText(String value, String message) {
        Objects.requireNonNull(value, message);
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
