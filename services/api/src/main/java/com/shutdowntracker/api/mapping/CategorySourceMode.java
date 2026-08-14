package com.shutdowntracker.api.mapping;

import java.util.Arrays;

/**
 * Where a category's values come from in the imported Project data.
 *
 * <p>These are the three source modes the mapping MVP supports. Each reads imported facts
 * and never rewrites them.
 */
public enum CategorySourceMode {

    /** A direct imported task field, or an aliased Project custom field. */
    TASK_FIELD("task_field"),

    /** A summary-task ancestor at a chosen outline level. */
    HIERARCHY_ANCESTOR("hierarchy_ancestor"),

    /** The assigned resource's standard Project Group field. */
    RESOURCE_GROUP("resource_group");

    private final String databaseValue;

    CategorySourceMode(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    /**
     * Whether this mode can legitimately yield several values for one task. A task may
     * carry assignments from more than one Resource Group.
     */
    public boolean canYieldMultipleValues() {
        return this == RESOURCE_GROUP;
    }

    public static CategorySourceMode fromDatabaseValue(String databaseValue) {
        return Arrays.stream(values())
                .filter(mode -> mode.databaseValue.equals(databaseValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported category source mode: " + databaseValue));
    }
}
