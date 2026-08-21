package com.shutdowntracker.api.identity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/**
 * The project-scoped roles defined in {@code docs/product/roles-and-capabilities.md}.
 *
 * <p>Roles are per project, not global: the same person may be a planner on one shutdown
 * and a viewer on another.
 *
 * <p>The database value is also the wire value. The enum constant name is an implementation
 * detail of this service; {@code field_user} is what the schema stores, what the permission
 * matrix is written in, and what the TypeScript client's {@code ProjectRole} union contains.
 * Letting Jackson fall back to the constant name would emit {@code FIELD_USER}, which that union
 * does not include — so a client would silently fail to recognise its own role.
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

    @JsonValue
    public String databaseValue() {
        return databaseValue;
    }

    @JsonCreator
    public static ProjectRole fromDatabaseValue(String databaseValue) {
        return Arrays.stream(values())
                .filter(role -> role.databaseValue.equals(databaseValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported project role: " + databaseValue));
    }
}
