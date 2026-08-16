package com.shutdowntracker.api.mapping;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class JdbcOperationalMappingRepository implements OperationalMappingRepository {

    private static final String PROFILE_COLUMNS = "id, project_id, name, version, active, description";

    private static final String CATEGORY_COLUMNS = """
            id, import_profile_id, project_id, name, source_mode, source_field,
            source_outline_level, multi_valued, required_for_execution, health
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcOperationalMappingRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ImportProfileRecord createProfile(
            UUID projectId,
            String name,
            String description,
            UUID createdByUserId
    ) {
        // The next version of a named profile is allocated in the statement so two
        // planners saving at once cannot both take the same version.
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO import_profiles (project_id, name, version, description, created_by_user_id)
                VALUES (
                    :projectId,
                    :name,
                    (SELECT COALESCE(MAX(version), 0) + 1 FROM import_profiles
                     WHERE project_id = :projectId AND name = :name),
                    :description,
                    :createdBy
                )
                RETURNING
                """ + PROFILE_COLUMNS,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("name", name)
                        .addValue("description", description)
                        .addValue("createdBy", createdByUserId),
                this::mapProfile);
    }

    @Override
    public ImportProfileRecord activateProfile(UUID projectId, UUID importProfileId, UUID activatedByUserId) {
        // Only one profile drives a project at a time, so the incumbent stands down first.
        jdbcTemplate.update(
                "UPDATE import_profiles SET active = false WHERE project_id = :projectId AND active",
                new MapSqlParameterSource("projectId", projectId));

        return jdbcTemplate.queryForObject(
                """
                UPDATE import_profiles
                SET active = true, activated_at = now(), activated_by_user_id = :activatedBy
                WHERE id = :id AND project_id = :projectId
                RETURNING
                """ + PROFILE_COLUMNS,
                new MapSqlParameterSource()
                        .addValue("id", importProfileId)
                        .addValue("projectId", projectId)
                        .addValue("activatedBy", activatedByUserId),
                this::mapProfile);
    }

    @Override
    public Optional<ImportProfileRecord> findActiveProfile(UUID projectId) {
        return jdbcTemplate.query(
                "SELECT " + PROFILE_COLUMNS + " FROM import_profiles WHERE project_id = :projectId AND active",
                new MapSqlParameterSource("projectId", projectId),
                this::mapProfile).stream().findFirst();
    }

    @Override
    public OperationalCategoryRecord createCategory(
            UUID projectId,
            UUID importProfileId,
            OperationalCategoryCreateRequest request
    ) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO operational_categories (
                    import_profile_id, project_id, name, source_mode, source_field,
                    source_outline_level, multi_valued, required_for_execution
                )
                VALUES (
                    :profileId, :projectId, :name, CAST(:sourceMode AS category_source_mode),
                    :sourceField, :outlineLevel, :multiValued, :requiredForExecution
                )
                RETURNING
                """ + CATEGORY_COLUMNS,
                new MapSqlParameterSource()
                        .addValue("profileId", importProfileId)
                        .addValue("projectId", projectId)
                        .addValue("name", request.name())
                        .addValue("sourceMode", request.sourceMode().databaseValue())
                        .addValue("sourceField", request.sourceField())
                        .addValue("outlineLevel", request.sourceOutlineLevel())
                        .addValue("multiValued", request.multiValued())
                        .addValue("requiredForExecution", request.requiredForExecution()),
                this::mapCategory);
    }

    @Override
    public List<OperationalCategoryRecord> findCategories(UUID importProfileId) {
        return jdbcTemplate.query(
                "SELECT " + CATEGORY_COLUMNS
                        + " FROM operational_categories WHERE import_profile_id = :profileId ORDER BY name",
                new MapSqlParameterSource("profileId", importProfileId),
                this::mapCategory);
    }

    @Override
    public void updateCategoryHealth(UUID operationalCategoryId, MappingHealth health) {
        jdbcTemplate.update(
                "UPDATE operational_categories SET health = CAST(:health AS mapping_health) WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("id", operationalCategoryId)
                        .addValue("health", health.databaseValue()));
    }

    @Override
    public void clearResolvedValues(UUID operationalCategoryId, UUID projectSnapshotId) {
        jdbcTemplate.update(
                """
                DELETE FROM task_category_values
                WHERE operational_category_id = :categoryId AND project_snapshot_id = :snapshotId
                """,
                new MapSqlParameterSource()
                        .addValue("categoryId", operationalCategoryId)
                        .addValue("snapshotId", projectSnapshotId));
    }

    /**
     * Resolves a category whose values come from an aliased Project custom field.
     *
     * <p>Matches the planner's own alias, which is what they named the field for. The
     * value is stored exactly as imported.
     */
    @Override
    public int resolveTaskFieldValues(
            UUID projectId,
            UUID projectSnapshotId,
            UUID operationalCategoryId,
            String sourceField
    ) {
        return jdbcTemplate.update(
                """
                INSERT INTO task_category_values (
                    project_id, project_snapshot_id, operational_category_id,
                    imported_task_id, source_value, resolved_via, resolved_from_reference
                )
                SELECT
                    :projectId, :snapshotId, :categoryId, t.id, a.value,
                    CAST('task_field' AS category_source_mode),
                    COALESCE(a.alias, a.field_name)
                FROM imported_extended_attributes a
                JOIN imported_tasks t
                  ON t.external_uid = a.entity_external_uid
                 AND t.project_snapshot_id = a.project_snapshot_id
                WHERE a.project_snapshot_id = :snapshotId
                  AND a.entity_type = 'task'
                  AND (a.alias = :sourceField OR a.field_name = :sourceField)
                  AND a.value IS NOT NULL
                  AND length(btrim(a.value)) > 0
                ON CONFLICT (operational_category_id, imported_task_id, source_value) DO NOTHING
                """,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("snapshotId", projectSnapshotId)
                        .addValue("categoryId", operationalCategoryId)
                        .addValue("sourceField", sourceField));
    }

    /**
     * Resolves a category from summary-task ancestry.
     *
     * <p>Walks up the imported hierarchy to the ancestor sitting at the configured outline
     * level and uses its name. The ancestor is recorded as the provenance reference, so
     * the answer to "why is this task in this category?" names the summary task it came
     * from.
     */
    @Override
    public int resolveHierarchyValues(
            UUID projectId,
            UUID projectSnapshotId,
            UUID operationalCategoryId,
            int outlineLevel
    ) {
        return jdbcTemplate.update(
                """
                WITH RECURSIVE ancestry AS (
                    SELECT t.id AS task_id, t.id AS ancestor_id, t.parent_imported_task_id,
                           t.outline_level, t.name
                    FROM imported_tasks t
                    WHERE t.project_snapshot_id = :snapshotId

                    UNION ALL

                    SELECT a.task_id, p.id, p.parent_imported_task_id, p.outline_level, p.name
                    FROM ancestry a
                    JOIN imported_tasks p ON p.id = a.parent_imported_task_id
                )
                INSERT INTO task_category_values (
                    project_id, project_snapshot_id, operational_category_id,
                    imported_task_id, source_value, resolved_via, resolved_from_reference
                )
                SELECT DISTINCT
                    :projectId, :snapshotId, :categoryId, a.task_id, a.name,
                    CAST('hierarchy_ancestor' AS category_source_mode),
                    'outline level ' || :outlineLevel
                FROM ancestry a
                WHERE a.outline_level = :outlineLevel
                  AND a.name IS NOT NULL
                  AND length(btrim(a.name)) > 0
                  -- A summary task is not classified by itself.
                  AND a.task_id <> a.ancestor_id
                ON CONFLICT (operational_category_id, imported_task_id, source_value) DO NOTHING
                """,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("snapshotId", projectSnapshotId)
                        .addValue("categoryId", operationalCategoryId)
                        .addValue("outlineLevel", outlineLevel));
    }

    /**
     * Resolves a category from the assigned resource's Project Group field.
     *
     * <p>Naturally multi-valued: a task with assignments from two Resource Groups belongs
     * to both, and collapsing that to one would misreport who is working on it.
     */
    @Override
    public int resolveResourceGroupValues(
            UUID projectId,
            UUID projectSnapshotId,
            UUID operationalCategoryId
    ) {
        return jdbcTemplate.update(
                """
                INSERT INTO task_category_values (
                    project_id, project_snapshot_id, operational_category_id,
                    imported_task_id, source_value, resolved_via, resolved_from_reference
                )
                SELECT DISTINCT
                    :projectId, :snapshotId, :categoryId, asg.imported_task_id,
                    r.raw_data ->> 'group',
                    CAST('resource_group' AS category_source_mode),
                    r.name
                FROM imported_assignments asg
                JOIN imported_resources r ON r.id = asg.imported_resource_id
                WHERE asg.project_snapshot_id = :snapshotId
                  AND asg.imported_task_id IS NOT NULL
                  AND r.raw_data ->> 'group' IS NOT NULL
                  AND length(btrim(r.raw_data ->> 'group')) > 0
                ON CONFLICT (operational_category_id, imported_task_id, source_value) DO NOTHING
                """,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("snapshotId", projectSnapshotId)
                        .addValue("categoryId", operationalCategoryId));
    }

    @Override
    public int countResolvedTasks(UUID operationalCategoryId, UUID projectSnapshotId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT count(DISTINCT imported_task_id)
                FROM task_category_values
                WHERE operational_category_id = :categoryId AND project_snapshot_id = :snapshotId
                """,
                new MapSqlParameterSource()
                        .addValue("categoryId", operationalCategoryId)
                        .addValue("snapshotId", projectSnapshotId),
                Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<String> distinctValues(UUID operationalCategoryId, UUID projectSnapshotId) {
        return jdbcTemplate.queryForList(
                """
                SELECT DISTINCT source_value
                FROM task_category_values
                WHERE operational_category_id = :categoryId AND project_snapshot_id = :snapshotId
                ORDER BY source_value
                """,
                new MapSqlParameterSource()
                        .addValue("categoryId", operationalCategoryId)
                        .addValue("snapshotId", projectSnapshotId),
                String.class);
    }

    @Override
    public List<String> valuesForTask(UUID operationalCategoryId, UUID importedTaskId) {
        return jdbcTemplate.queryForList(
                """
                SELECT source_value
                FROM task_category_values
                WHERE operational_category_id = :categoryId AND imported_task_id = :taskId
                ORDER BY source_value
                """,
                new MapSqlParameterSource()
                        .addValue("categoryId", operationalCategoryId)
                        .addValue("taskId", importedTaskId),
                String.class);
    }

    @Override
    public List<UUID> findTasksMissingRequiredCategory(UUID projectSnapshotId, UUID operationalCategoryId) {
        return jdbcTemplate.queryForList(
                """
                SELECT t.id
                FROM imported_tasks t
                WHERE t.project_snapshot_id = :snapshotId
                  AND NOT t.is_summary
                  AND NOT EXISTS (
                      SELECT 1 FROM task_category_values v
                      WHERE v.imported_task_id = t.id
                        AND v.operational_category_id = :categoryId
                  )
                ORDER BY t.id
                """,
                new MapSqlParameterSource()
                        .addValue("snapshotId", projectSnapshotId)
                        .addValue("categoryId", operationalCategoryId),
                UUID.class);
    }

    private ImportProfileRecord mapProfile(ResultSet rs, int rowNum) throws SQLException {
        return new ImportProfileRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("name"),
                rs.getInt("version"),
                rs.getBoolean("active"),
                rs.getString("description"));
    }

    private OperationalCategoryRecord mapCategory(ResultSet rs, int rowNum) throws SQLException {
        Integer outlineLevel = rs.getObject("source_outline_level", Integer.class);
        return new OperationalCategoryRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("import_profile_id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("name"),
                CategorySourceMode.fromDatabaseValue(rs.getString("source_mode")),
                rs.getString("source_field"),
                outlineLevel,
                rs.getBoolean("multi_valued"),
                rs.getBoolean("required_for_execution"),
                MappingHealth.fromDatabaseValue(rs.getString("health")));
    }
}
