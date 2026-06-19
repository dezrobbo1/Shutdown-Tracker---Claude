package com.shutdowntracker.api.project;

public record ReviewProjectCreateRequest(
        String name,
        String description,
        String timezone
) {

    public ReviewProjectCreateRequest {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description is required.");
        }
        if (timezone == null || timezone.isBlank()) {
            throw new IllegalArgumentException("timezone is required.");
        }
        name = name.trim();
        description = description.trim();
        timezone = timezone.trim();
    }
}
