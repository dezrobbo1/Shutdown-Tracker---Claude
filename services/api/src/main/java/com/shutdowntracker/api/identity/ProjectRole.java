package com.shutdowntracker.api.identity;

import java.util.Arrays;

/**
 * The project-scoped roles defined in {@code docs/product/roles-and-capabilities.md}.
 *
 * <p>Roles are per project, not global: the same person may be a planner on one shutdown
 * and a viewer on another.
 */
public enum ProjectRole {

    ADMIN("admin"),
    PLANNER("planner"),
    SHUTDOWN_CONTROL("shutdown_control"),
    COORDINATOR("coordinator"),
    SUPERVISOR("supervisor"),
    FIELD_USER("field_user"),
    CONTRACTOR("contractor"),
    INSPECTOR("inspector"),
    VIEWER("viewer");

    private final String databaseValue;

    ProjectRole(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static ProjectRole fromDatabaseValue(String databaseValue) {
        return Arrays.stream(values())
                .filter(role -> role.databaseValue.equals(databaseValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported project role: " + databaseValue));
    }
}
