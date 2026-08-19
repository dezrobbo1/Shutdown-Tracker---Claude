package com.shutdowntracker.api.audit;

import java.util.Arrays;

public enum AuditEventCategory {
    /**
     * Project configuration, as distinct from {@code permission}. Linking a Microsoft Project
     * resource to a user is recorded here on purpose: the link decides what a work list shows and
     * grants no authority, so filing it as a permission event would misdescribe what happened.
     */
    PROJECT("project"),
    IMPORT("import"),
    REIMPORT("reimport"),
    APPROVAL("approval"),
    EXPORT("export");

    private final String databaseValue;

    AuditEventCategory(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static AuditEventCategory fromDatabaseValue(String databaseValue) {
        return Arrays.stream(values())
                .filter(category -> category.databaseValue.equals(databaseValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported audit event category: " + databaseValue));
    }
}
