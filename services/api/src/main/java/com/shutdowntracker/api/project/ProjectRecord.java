package com.shutdowntracker.api.project;

import java.util.UUID;

public record ProjectRecord(
        UUID id,
        String name,
        String status,
        String timezone
) {
}
