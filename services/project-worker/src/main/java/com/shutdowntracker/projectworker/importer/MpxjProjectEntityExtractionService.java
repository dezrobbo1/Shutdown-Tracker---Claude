package com.shutdowntracker.projectworker.importer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.shutdowntracker.projectimport.contract.ParsedAssignment;
import com.shutdowntracker.projectimport.contract.ParsedExtendedAttribute;
import com.shutdowntracker.projectimport.contract.ParsedResource;
import com.shutdowntracker.projectimport.contract.ParsedTask;
import org.mpxj.CustomField;
import org.mpxj.FieldType;
import org.mpxj.FieldTypeClass;
import org.mpxj.ProjectFile;
import org.mpxj.Resource;
import org.mpxj.ResourceAssignment;
import org.mpxj.Task;
import org.springframework.stereotype.Service;

/**
 * Converts a parsed {@link ProjectFile} into the transferable entities the API persists.
 *
 * <p>This reports what the file contains and nothing more. No schedule value is
 * calculated, no date is moved, and no missing value is inferred: Microsoft Project
 * remains the schedule authority, and an imported snapshot has to stay faithful to the
 * file it came from.
 */
@Service
public class MpxjProjectEntityExtractionService {

    /**
     * Microsoft Project stores wall-clock times with no zone. They are read as UTC so the
     * value survives a round trip unchanged; the project's own timezone is applied for
     * presentation, not storage.
     */
    private static final ZoneOffset PROJECT_FILE_ZONE = ZoneOffset.UTC;

    public List<ParsedTask> extractTasks(ProjectFile project) {
        Objects.requireNonNull(project, "project is required.");
        List<ParsedTask> tasks = new ArrayList<>();
        // Ordered so a parent always precedes its children, which is what lets the API
        // resolve parent references as it inserts.
        for (Task task : project.getTasks()) {
            if (task == null) {
                continue;
            }
            tasks.add(toParsedTask(task));
        }
        return List.copyOf(tasks);
    }

    public List<ParsedResource> extractResources(ProjectFile project) {
        Objects.requireNonNull(project, "project is required.");
        List<ParsedResource> resources = new ArrayList<>();
        for (Resource resource : project.getResources()) {
            if (resource == null) {
                continue;
            }
            Map<String, Object> rawData = new LinkedHashMap<>();
            putIfPresent(rawData, "group", resource.getGroup());
            putIfPresent(rawData, "externalId", text(resource.getID()));
            resources.add(new ParsedResource(
                    externalUid(resource.getUniqueID()),
                    resource.getName(),
                    resource.getType() == null ? null : resource.getType().name(),
                    rawData));
        }
        return List.copyOf(resources);
    }

    public List<ParsedAssignment> extractAssignments(ProjectFile project) {
        Objects.requireNonNull(project, "project is required.");
        List<ParsedAssignment> assignments = new ArrayList<>();
        for (ResourceAssignment assignment : project.getResourceAssignments()) {
            if (assignment == null) {
                continue;
            }
            Map<String, Object> rawData = new LinkedHashMap<>();
            putIfPresent(rawData, "units", assignment.getUnits());
            if (assignment.getWork() != null) {
                putIfPresent(rawData, "work", assignment.getWork().toString());
            }
            assignments.add(new ParsedAssignment(
                    externalUid(assignment.getUniqueID()),
                    externalUid(assignment.getTaskUniqueID()),
                    externalUid(assignment.getResourceUniqueID()),
                    rawData));
        }
        return List.copyOf(assignments);
    }

    /**
     * Extracts populated task custom fields that the planner has given an alias.
     *
     * <p>An alias is the planner naming a field for their own use — "Work Group",
     * "Contractor", "Area" — which is exactly the material Operational Categories map
     * against. Unaliased custom fields are left out to avoid importing hundreds of empty
     * Text/Number slots per task.
     */
    public List<ParsedExtendedAttribute> extractExtendedAttributes(ProjectFile project) {
        Objects.requireNonNull(project, "project is required.");

        List<CustomField> aliased = new ArrayList<>();
        for (CustomField customField : project.getCustomFields()) {
            if (customField == null || customField.getFieldType() == null) {
                continue;
            }
            String alias = customField.getAlias();
            if (alias == null || alias.isBlank()) {
                continue;
            }
            if (customField.getFieldType().getFieldTypeClass() != FieldTypeClass.TASK) {
                continue;
            }
            aliased.add(customField);
        }
        if (aliased.isEmpty()) {
            return List.of();
        }

        List<ParsedExtendedAttribute> attributes = new ArrayList<>();
        for (Task task : project.getTasks()) {
            if (task == null) {
                continue;
            }
            for (CustomField customField : aliased) {
                FieldType fieldType = customField.getFieldType();
                Object value = task.get(fieldType);
                if (value == null || value.toString().isBlank()) {
                    continue;
                }
                attributes.add(new ParsedExtendedAttribute(
                        "task",
                        externalUid(task.getUniqueID()),
                        fieldType.name(),
                        fieldType.getName(),
                        customField.getAlias(),
                        value.toString(),
                        Map.of()));
            }
        }
        return List.copyOf(attributes);
    }

    private ParsedTask toParsedTask(Task task) {
        Map<String, Object> rawData = new LinkedHashMap<>();
        putIfPresent(rawData, "guid", task.getGUID());
        putIfPresent(rawData, "durationText", task.getDuration() == null ? null : task.getDuration().toString());
        putIfPresent(rawData, "milestone", task.getMilestone());

        return new ParsedTask(
                externalUid(task.getUniqueID()),
                text(task.getID()),
                task.getName(),
                task.getWBS(),
                task.getOutlineNumber(),
                task.getOutlineLevel(),
                task.getSummary(),
                externalUid(task.getParentTaskUniqueID()),
                toOffsetDateTime(task.getStart()),
                toOffsetDateTime(task.getFinish()),
                toOffsetDateTime(task.getActualStart()),
                toOffsetDateTime(task.getActualFinish()),
                toPercent(task.getPercentageComplete()),
                toPercent(task.getPhysicalPercentComplete()),
                task.getNotes(),
                rawData);
    }

    /**
     * Clamps to the 0-100 range the database enforces. Some files carry values slightly
     * outside it through rounding; rejecting the whole import for that would be worse than
     * storing the nearest legal value.
     */
    private BigDecimal toPercent(Number value) {
        if (value == null) {
            return null;
        }
        BigDecimal percent = new BigDecimal(value.toString());
        if (percent.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (percent.compareTo(new BigDecimal("100")) > 0) {
            return new BigDecimal("100");
        }
        return percent;
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atOffset(PROJECT_FILE_ZONE);
    }

    private String externalUid(Integer uniqueId) {
        return uniqueId == null ? null : uniqueId.toString();
    }

    private String text(Integer value) {
        return value == null ? null : value.toString();
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        target.put(key, value instanceof Number || value instanceof Boolean ? value : value.toString());
    }
}
