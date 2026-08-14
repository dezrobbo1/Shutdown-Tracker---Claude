package com.shutdowntracker.api.mapping;

import java.util.UUID;

public record OperationalCategoryRecord(
        UUID id,
        UUID importProfileId,
        UUID projectId,
        String name,
        CategorySourceMode sourceMode,
        String sourceField,
        Integer sourceOutlineLevel,
        boolean multiValued,
        boolean requiredForExecution,
        MappingHealth health
) {
}
