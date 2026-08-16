package com.shutdowntracker.api.identity;

import java.util.Arrays;

/**
 * Account status. Only {@link #ACTIVE} users may act; everything else fails closed, so
 * revoking access is a single status change rather than a hunt through role grants.
 */
public enum UserStatus {

    INVITED("invited"),
    ACTIVE("active"),
    SUSPENDED("suspended"),
    DEACTIVATED("deactivated");

    private final String databaseValue;

    UserStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public boolean canAct() {
        return this == ACTIVE;
    }

    public static UserStatus fromDatabaseValue(String databaseValue) {
        return Arrays.stream(values())
                .filter(status -> status.databaseValue.equals(databaseValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported user status: " + databaseValue));
    }
}
