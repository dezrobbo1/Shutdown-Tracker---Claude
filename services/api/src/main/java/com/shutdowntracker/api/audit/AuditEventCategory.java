package com.shutdowntracker.api.audit;

import java.util.Arrays;

public enum AuditEventCategory {
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
