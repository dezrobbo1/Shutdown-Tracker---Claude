package com.shutdowntracker.api.audit;

import java.util.Arrays;

public enum AuditActorType {
    USER("user"),
    SERVICE("service"),
    SYSTEM("system"),
    INTEGRATION("integration");

    private final String databaseValue;

    AuditActorType(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static AuditActorType fromDatabaseValue(String databaseValue) {
        return Arrays.stream(values())
                .filter(type -> type.databaseValue.equals(databaseValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported audit actor type: " + databaseValue));
    }
}
