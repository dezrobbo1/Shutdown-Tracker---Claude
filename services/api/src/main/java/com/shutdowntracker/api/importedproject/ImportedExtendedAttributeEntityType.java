package com.shutdowntracker.api.importedproject;

import java.util.Arrays;

public enum ImportedExtendedAttributeEntityType {
    PROJECT("project"),
    TASK("task"),
    RESOURCE("resource"),
    ASSIGNMENT("assignment"),
    CALENDAR("calendar"),
    OTHER("other");

    private final String databaseValue;

    ImportedExtendedAttributeEntityType(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static ImportedExtendedAttributeEntityType fromDatabaseValue(String databaseValue) {
        return Arrays.stream(values())
                .filter(type -> type.databaseValue.equals(databaseValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported imported extended attribute entity type: " + databaseValue
                ));
    }
}
