package com.shutdowntracker.api.identity;

import java.util.UUID;

/** A person who can sign in. Authentication happens outside; this is the local identity. */
public record UserRecord(
        UUID id,
        String email,
        String displayName,
        UserStatus status,
        String externalSubject
) {
}
