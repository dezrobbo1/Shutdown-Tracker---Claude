package com.shutdowntracker.api.mapping;

import java.util.UUID;

public record ImportProfileRecord(
        UUID id,
        UUID projectId,
        String name,
        int version,
        boolean active,
        String description
) {
}
