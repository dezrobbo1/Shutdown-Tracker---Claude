package com.shutdowntracker.api.tasklineage;

public record TaskLineageDecisionResponse(
        TaskLineageRecord lineageLink,
        String message
) {
}
