package com.shutdowntracker.api.importedproject;

import java.util.ArrayList;
import java.util.List;
import com.shutdowntracker.projectimport.contract.ParsedAssignment;
import com.shutdowntracker.projectimport.contract.ParsedExtendedAttribute;
import com.shutdowntracker.projectimport.contract.ParsedResource;
import com.shutdowntracker.projectimport.contract.ParsedTask;
import com.shutdowntracker.projectimport.contract.ProjectParseEntitiesResponse;

/**
 * Translates the worker's parse response into the create requests the repository stores.
 *
 * <p>Database identifiers are deliberately left null here. The worker knows the file's own
 * identifiers and nothing about Shutdown Tracker's primary keys, so parent and assignment
 * links are resolved during persistence, once the rows they point at exist.
 */
public final class ParsedProjectEntitiesMapper {

    private ParsedProjectEntitiesMapper() {
    }

    public static ImportedProjectEntities toEntities(ProjectParseEntitiesResponse response) {
        return new ImportedProjectEntities(
                toTasks(response.tasks()),
                toResources(response.resources()),
                toAssignments(response.assignments()),
                toExtendedAttributes(response.extendedAttributes()));
    }

    private static List<ImportedTaskCreateRequest> toTasks(List<ParsedTask> tasks) {
        List<ImportedTaskCreateRequest> mapped = new ArrayList<>(tasks.size());
        for (ParsedTask task : tasks) {
            try {
                mapped.add(new ImportedTaskCreateRequest(
                        task.externalUid(),
                        task.externalId(),
                        task.name(),
                        task.wbs(),
                        task.outlineNumber(),
                        task.outlineLevel(),
                        task.summary(),
                        task.parentExternalUid(),
                        null,
                        task.plannedStart(),
                        task.plannedFinish(),
                        task.actualStart(),
                        task.actualFinish(),
                        task.percentComplete(),
                        task.physicalPercentComplete(),
                        task.notes(),
                        task.rawData()));
            } catch (IllegalArgumentException exception) {
                // Fail loudly and name the task. Silently dropping or "correcting" a task
                // would leave the snapshot quietly disagreeing with the source file, which
                // is worse than refusing an import the planner can go and fix.
                throw new IllegalArgumentException(
                        "Imported task " + task.externalUid() + " (" + task.name() + ") is not storable: "
                                + exception.getMessage(), exception);
            }
        }
        return mapped;
    }

    private static List<ImportedResourceCreateRequest> toResources(List<ParsedResource> resources) {
        List<ImportedResourceCreateRequest> mapped = new ArrayList<>(resources.size());
        for (ParsedResource resource : resources) {
            mapped.add(new ImportedResourceCreateRequest(
                    resource.externalUid(),
                    resource.name(),
                    resource.resourceType(),
                    resource.rawData()));
        }
        return mapped;
    }

    private static List<ImportedAssignmentCreateRequest> toAssignments(List<ParsedAssignment> assignments) {
        List<ImportedAssignmentCreateRequest> mapped = new ArrayList<>(assignments.size());
        for (ParsedAssignment assignment : assignments) {
            mapped.add(new ImportedAssignmentCreateRequest(
                    assignment.externalUid(),
                    assignment.taskExternalUid(),
                    assignment.resourceExternalUid(),
                    null,
                    null,
                    assignment.rawData()));
        }
        return mapped;
    }

    private static List<ImportedExtendedAttributeCreateRequest> toExtendedAttributes(
            List<ParsedExtendedAttribute> attributes
    ) {
        List<ImportedExtendedAttributeCreateRequest> mapped = new ArrayList<>(attributes.size());
        for (ParsedExtendedAttribute attribute : attributes) {
            mapped.add(new ImportedExtendedAttributeCreateRequest(
                    ImportedExtendedAttributeEntityType.fromDatabaseValue(attribute.entityType()),
                    attribute.entityExternalUid(),
                    attribute.fieldId(),
                    attribute.fieldName(),
                    attribute.alias(),
                    attribute.value(),
                    attribute.rawData()));
        }
        return mapped;
    }
}
