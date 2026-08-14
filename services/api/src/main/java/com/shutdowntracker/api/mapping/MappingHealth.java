package com.shutdowntracker.api.mapping;

import java.util.Arrays;

/**
 * The outcome of revalidating a mapping against a newly imported snapshot.
 *
 * <p>The distinctions matter: a source that has simply gained new values is not the same
 * as one whose field has disappeared, and neither is silently remapped.
 */
public enum MappingHealth {

    HEALTHY("healthy"),
    HEALTHY_WITH_NEW_VALUES("healthy_with_new_values"),
    CONFIGURATION_CHANGED("configuration_changed"),
    CONFIRMATION_REQUIRED("confirmation_required"),
    BROKEN("broken"),
    PROFILE_MISMATCH("profile_mismatch");

    private final String databaseValue;

    MappingHealth(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    /** Whether a planner has to look at this before the mapping can be trusted. */
    public boolean needsPlannerAttention() {
        return this == CONFIRMATION_REQUIRED || this == BROKEN || this == PROFILE_MISMATCH;
    }

    public static MappingHealth fromDatabaseValue(String databaseValue) {
        return Arrays.stream(values())
                .filter(health -> health.databaseValue.equals(databaseValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported mapping health: " + databaseValue));
    }
}
