package com.shutdowntracker.api.mapping;

import java.util.UUID;

/** What resolving one category against one snapshot produced. */
public record CategoryResolutionSummary(
        UUID operationalCategoryId,
        String categoryName,
        CategorySourceMode sourceMode,
        int taskCount,
        int distinctValueCount,
        MappingHealth health
) {
}
