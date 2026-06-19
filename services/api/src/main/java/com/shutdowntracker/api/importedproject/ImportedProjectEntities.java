package com.shutdowntracker.api.importedproject;

import java.util.List;

public record ImportedProjectEntities(
        List<ImportedTaskCreateRequest> tasks,
        List<ImportedResourceCreateRequest> resources,
        List<ImportedAssignmentCreateRequest> assignments,
        List<ImportedExtendedAttributeCreateRequest> extendedAttributes
) {
    public ImportedProjectEntities {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        resources = resources == null ? List.of() : List.copyOf(resources);
        assignments = assignments == null ? List.of() : List.copyOf(assignments);
        extendedAttributes = extendedAttributes == null ? List.of() : List.copyOf(extendedAttributes);
    }

    public static ImportedProjectEntities empty() {
        return new ImportedProjectEntities(List.of(), List.of(), List.of(), List.of());
    }
}
