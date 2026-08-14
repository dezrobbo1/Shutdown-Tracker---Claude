package com.shutdowntracker.api.mapping;

import java.util.Objects;

/**
 * Defines one planner-named category and the Project source it reads.
 *
 * <p>The configuration each mode needs is validated here as well as in the database, so a
 * misconfigured category is rejected with a useful message rather than a constraint error.
 */
public record OperationalCategoryCreateRequest(
        String name,
        CategorySourceMode sourceMode,
        String sourceField,
        Integer sourceOutlineLevel,
        boolean multiValued,
        boolean requiredForExecution
) {
    public OperationalCategoryCreateRequest {
        Objects.requireNonNull(name, "name is required.");
        Objects.requireNonNull(sourceMode, "sourceMode is required.");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name is required.");
        }
        name = name.trim();

        switch (sourceMode) {
            case TASK_FIELD -> {
                if (sourceField == null || sourceField.isBlank()) {
                    throw new IllegalArgumentException("A task-field category needs a source field or alias.");
                }
                sourceField = sourceField.trim();
                sourceOutlineLevel = null;
            }
            case HIERARCHY_ANCESTOR -> {
                if (sourceOutlineLevel == null || sourceOutlineLevel < 0) {
                    throw new IllegalArgumentException(
                            "A hierarchy category needs the outline level of the ancestor to read.");
                }
                sourceField = null;
            }
            case RESOURCE_GROUP -> {
                // The Project Group field is the fixed source, so no configuration is taken.
                sourceField = null;
                sourceOutlineLevel = null;
                // A task can carry assignments from several Resource Groups, so this mode
                // is always multi-valued regardless of what the caller asked for.
                multiValued = true;
            }
        }
    }
}
