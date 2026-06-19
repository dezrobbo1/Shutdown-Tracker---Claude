package com.shutdowntracker.api.importedproject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ImportedExtendedAttributeEntityTypeTests {

    @Test
    void mapsExistingDatabaseValues() {
        assertThat(ImportedExtendedAttributeEntityType.fromDatabaseValue("project"))
                .isEqualTo(ImportedExtendedAttributeEntityType.PROJECT);
        assertThat(ImportedExtendedAttributeEntityType.fromDatabaseValue("task"))
                .isEqualTo(ImportedExtendedAttributeEntityType.TASK);
        assertThat(ImportedExtendedAttributeEntityType.fromDatabaseValue("resource"))
                .isEqualTo(ImportedExtendedAttributeEntityType.RESOURCE);
        assertThat(ImportedExtendedAttributeEntityType.fromDatabaseValue("assignment"))
                .isEqualTo(ImportedExtendedAttributeEntityType.ASSIGNMENT);
        assertThat(ImportedExtendedAttributeEntityType.fromDatabaseValue("calendar"))
                .isEqualTo(ImportedExtendedAttributeEntityType.CALENDAR);
        assertThat(ImportedExtendedAttributeEntityType.fromDatabaseValue("other"))
                .isEqualTo(ImportedExtendedAttributeEntityType.OTHER);
    }

    @Test
    void rejectsUnknownEntityTypes() {
        assertThatThrownBy(() -> ImportedExtendedAttributeEntityType.fromDatabaseValue("schedule_rule"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported imported extended attribute entity type: schedule_rule");
    }
}
