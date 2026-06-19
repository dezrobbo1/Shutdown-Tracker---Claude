package com.shutdowntracker.api.project;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shutdown-tracker.review-project-bootstrap")
public record ReviewProjectBootstrapProperties(
        boolean enabled,
        String projectName,
        String description,
        String timezone
) {

    public ReviewProjectBootstrapProperties {
        if (projectName == null || projectName.isBlank()) {
            projectName = "Synthetic Review Project";
        }
        if (description == null || description.isBlank()) {
            description = "Synthetic review project for local and review-environment setup only.";
        }
        if (timezone == null || timezone.isBlank()) {
            timezone = "UTC";
        }
    }
}
