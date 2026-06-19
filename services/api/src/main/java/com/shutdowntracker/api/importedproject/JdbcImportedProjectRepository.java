package com.shutdowntracker.api.importedproject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class JdbcImportedProjectRepository implements ImportedProjectRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcImportedProjectRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ProjectSnapshotRecord createSnapshot(ProjectSnapshotCreateRequest request) {
        String sql = """
                INSERT INTO project_snapshots (
                    project_id,
                    import_batch_id,
                    status,
                    external_project_uid,
                    external_project_name,
                    project_status_date,
                    snapshot_version,
                    metadata
                )
                VALUES (
                    :projectId,
                    :importBatchId,
                    CAST(:status AS project_snapshot_status),
                    :externalProjectUid,
                    :externalProjectName,
                    :projectStatusDate,
                    (
                        SELECT COALESCE(MAX(snapshot_version), 0) + 1
                        FROM project_snapshots
                        WHERE project_id = :projectId
                    ),
                    CAST(:metadata AS jsonb)
                )
                RETURNING id, project_id, import_batch_id, status, external_project_uid,
                          external_project_name, project_status_date, snapshot_version
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("projectId", request.projectId())
                .addValue("importBatchId", request.importBatchId())
                .addValue("status", request.status().databaseValue())
                .addValue("externalProjectUid", request.externalProjectUid())
                .addValue("externalProjectName", request.externalProjectName())
                .addValue("projectStatusDate", request.projectStatusDate())
                .addValue("metadata", toJson(request.metadata()));

        return jdbcTemplate.queryForObject(sql, parameters, this::mapSnapshot);
    }

    @Override
    public List<ImportedTaskRecord> createTasks(
            UUID projectId,
            UUID projectSnapshotId,
            List<ImportedTaskCreateRequest> tasks
    ) {
        List<ImportedTaskRecord> records = new ArrayList<>();
        for (ImportedTaskCreateRequest task : tasks) {
            records.add(createTask(projectId, projectSnapshotId, task));
        }
        return List.copyOf(records);
    }

    @Override
    public List<ImportedResourceRecord> createResources(
            UUID projectId,
            UUID projectSnapshotId,
            List<ImportedResourceCreateRequest> resources
    ) {
        List<ImportedResourceRecord> records = new ArrayList<>();
        for (ImportedResourceCreateRequest resource : resources) {
            records.add(createResource(projectId, projectSnapshotId, resource));
        }
        return List.copyOf(records);
    }

    @Override
    public List<ImportedAssignmentRecord> createAssignments(
            UUID projectId,
            UUID projectSnapshotId,
            List<ImportedAssignmentCreateRequest> assignments
    ) {
        List<ImportedAssignmentRecord> records = new ArrayList<>();
        for (ImportedAssignmentCreateRequest assignment : assignments) {
            records.add(createAssignment(projectId, projectSnapshotId, assignment));
        }
        return List.copyOf(records);
    }

    @Override
    public List<ImportedExtendedAttributeRecord> createExtendedAttributes(
            UUID projectId,
            UUID projectSnapshotId,
            List<ImportedExtendedAttributeCreateRequest> extendedAttributes
    ) {
        List<ImportedExtendedAttributeRecord> records = new ArrayList<>();
        for (ImportedExtendedAttributeCreateRequest extendedAttribute : extendedAttributes) {
            records.add(createExtendedAttribute(projectId, projectSnapshotId, extendedAttribute));
        }
        return List.copyOf(records);
    }

    private ImportedTaskRecord createTask(UUID projectId, UUID projectSnapshotId, ImportedTaskCreateRequest task) {
        String sql = """
                INSERT INTO imported_tasks (
                    project_id,
                    project_snapshot_id,
                    external_uid,
                    external_id,
                    name,
                    wbs,
                    outline_number,
                    outline_level,
                    is_summary,
                    parent_external_uid,
                    parent_imported_task_id,
                    planned_start,
                    planned_finish,
                    actual_start,
                    actual_finish,
                    percent_complete,
                    physical_percent_complete,
                    notes,
                    raw_data
                )
                VALUES (
                    :projectId,
                    :projectSnapshotId,
                    :externalUid,
                    :externalId,
                    :name,
                    :wbs,
                    :outlineNumber,
                    :outlineLevel,
                    :summary,
                    :parentExternalUid,
                    :parentImportedTaskId,
                    :plannedStart,
                    :plannedFinish,
                    :actualStart,
                    :actualFinish,
                    :percentComplete,
                    :physicalPercentComplete,
                    :notes,
                    CAST(:rawData AS jsonb)
                )
                RETURNING id, project_id, project_snapshot_id, external_uid, name, is_summary
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("projectSnapshotId", projectSnapshotId)
                .addValue("externalUid", task.externalUid())
                .addValue("externalId", task.externalId())
                .addValue("name", task.name())
                .addValue("wbs", task.wbs())
                .addValue("outlineNumber", task.outlineNumber())
                .addValue("outlineLevel", task.outlineLevel())
                .addValue("summary", task.summary())
                .addValue("parentExternalUid", task.parentExternalUid())
                .addValue("parentImportedTaskId", task.parentImportedTaskId())
                .addValue("plannedStart", task.plannedStart())
                .addValue("plannedFinish", task.plannedFinish())
                .addValue("actualStart", task.actualStart())
                .addValue("actualFinish", task.actualFinish())
                .addValue("percentComplete", task.percentComplete())
                .addValue("physicalPercentComplete", task.physicalPercentComplete())
                .addValue("notes", task.notes())
                .addValue("rawData", toJson(task.rawData()));

        return jdbcTemplate.queryForObject(sql, parameters, (rs, rowNum) -> new ImportedTaskRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("project_snapshot_id", UUID.class),
                rs.getString("external_uid"),
                rs.getString("name"),
                rs.getBoolean("is_summary")
        ));
    }

    private ImportedResourceRecord createResource(
            UUID projectId,
            UUID projectSnapshotId,
            ImportedResourceCreateRequest resource
    ) {
        String sql = """
                INSERT INTO imported_resources (
                    project_id,
                    project_snapshot_id,
                    external_uid,
                    name,
                    resource_type,
                    raw_data
                )
                VALUES (
                    :projectId,
                    :projectSnapshotId,
                    :externalUid,
                    :name,
                    :resourceType,
                    CAST(:rawData AS jsonb)
                )
                RETURNING id, project_id, project_snapshot_id, external_uid, name, resource_type
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("projectSnapshotId", projectSnapshotId)
                .addValue("externalUid", resource.externalUid())
                .addValue("name", resource.name())
                .addValue("resourceType", resource.resourceType())
                .addValue("rawData", toJson(resource.rawData()));

        return jdbcTemplate.queryForObject(sql, parameters, (rs, rowNum) -> new ImportedResourceRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("project_snapshot_id", UUID.class),
                rs.getString("external_uid"),
                rs.getString("name"),
                rs.getString("resource_type")
        ));
    }

    private ImportedAssignmentRecord createAssignment(
            UUID projectId,
            UUID projectSnapshotId,
            ImportedAssignmentCreateRequest assignment
    ) {
        String sql = """
                INSERT INTO imported_assignments (
                    project_id,
                    project_snapshot_id,
                    external_uid,
                    task_external_uid,
                    resource_external_uid,
                    imported_task_id,
                    imported_resource_id,
                    raw_data
                )
                VALUES (
                    :projectId,
                    :projectSnapshotId,
                    :externalUid,
                    :taskExternalUid,
                    :resourceExternalUid,
                    :importedTaskId,
                    :importedResourceId,
                    CAST(:rawData AS jsonb)
                )
                RETURNING id, project_id, project_snapshot_id, external_uid,
                          task_external_uid, resource_external_uid
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("projectSnapshotId", projectSnapshotId)
                .addValue("externalUid", assignment.externalUid())
                .addValue("taskExternalUid", assignment.taskExternalUid())
                .addValue("resourceExternalUid", assignment.resourceExternalUid())
                .addValue("importedTaskId", assignment.importedTaskId())
                .addValue("importedResourceId", assignment.importedResourceId())
                .addValue("rawData", toJson(assignment.rawData()));

        return jdbcTemplate.queryForObject(sql, parameters, (rs, rowNum) -> new ImportedAssignmentRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("project_snapshot_id", UUID.class),
                rs.getString("external_uid"),
                rs.getString("task_external_uid"),
                rs.getString("resource_external_uid")
        ));
    }

    private ImportedExtendedAttributeRecord createExtendedAttribute(
            UUID projectId,
            UUID projectSnapshotId,
            ImportedExtendedAttributeCreateRequest extendedAttribute
    ) {
        String sql = """
                INSERT INTO imported_extended_attributes (
                    project_id,
                    project_snapshot_id,
                    entity_type,
                    entity_external_uid,
                    field_id,
                    field_name,
                    alias,
                    value,
                    raw_data
                )
                VALUES (
                    :projectId,
                    :projectSnapshotId,
                    :entityType,
                    :entityExternalUid,
                    :fieldId,
                    :fieldName,
                    :alias,
                    :value,
                    CAST(:rawData AS jsonb)
                )
                RETURNING id, project_id, project_snapshot_id, entity_type,
                          entity_external_uid, field_id, field_name
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("projectSnapshotId", projectSnapshotId)
                .addValue("entityType", extendedAttribute.entityType().databaseValue())
                .addValue("entityExternalUid", extendedAttribute.entityExternalUid())
                .addValue("fieldId", extendedAttribute.fieldId())
                .addValue("fieldName", extendedAttribute.fieldName())
                .addValue("alias", extendedAttribute.alias())
                .addValue("value", extendedAttribute.value())
                .addValue("rawData", toJson(extendedAttribute.rawData()));

        return jdbcTemplate.queryForObject(sql, parameters, (rs, rowNum) -> new ImportedExtendedAttributeRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("project_snapshot_id", UUID.class),
                ImportedExtendedAttributeEntityType.fromDatabaseValue(rs.getString("entity_type")),
                rs.getString("entity_external_uid"),
                rs.getString("field_id"),
                rs.getString("field_name")
        ));
    }

    private ProjectSnapshotRecord mapSnapshot(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ProjectSnapshotRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("import_batch_id", UUID.class),
                ProjectSnapshotStatus.fromDatabaseValue(rs.getString("status")),
                rs.getString("external_project_uid"),
                rs.getString("external_project_name"),
                rs.getObject("project_status_date", OffsetDateTime.class),
                rs.getInt("snapshot_version")
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize imported project snapshot data.", exception);
        }
    }
}
