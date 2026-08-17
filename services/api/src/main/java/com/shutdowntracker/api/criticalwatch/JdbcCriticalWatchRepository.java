package com.shutdowntracker.api.criticalwatch;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class JdbcCriticalWatchRepository implements CriticalWatchRepository {

    private static final String WATCHLIST_COLUMNS = "id, project_id, name, description, status";
    private static final String PACKAGE_COLUMNS =
            "id, project_id, critical_watchlist_id, name, description, status";
    private static final String SOURCE_COLUMNS = """
            id, critical_work_package_id, project_snapshot_id, imported_task_id,
            source_type, include_descendants
            """;
    private static final String UPDATE_COLUMNS = """
            id, project_id, critical_work_package_id, status, update_mode, submitted_at,
            submitted_by_user_id, current_focus, current_blocker_summary, next_target,
            supersedes_critical_update_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCriticalWatchRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public CriticalWatchlistRecord createWatchlist(
            UUID projectId, String name, String description, UUID createdByUserId) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO critical_watchlists (project_id, name, description, created_by_user_id)
                VALUES (:projectId, :name, :description, :createdBy)
                RETURNING
                """ + WATCHLIST_COLUMNS,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("name", name)
                        .addValue("description", description)
                        .addValue("createdBy", createdByUserId),
                this::mapWatchlist);
    }

    @Override
    public List<CriticalWatchlistRecord> findActiveWatchlists(UUID projectId) {
        return jdbcTemplate.query(
                "SELECT " + WATCHLIST_COLUMNS
                        + " FROM critical_watchlists WHERE project_id = :projectId AND status = 'active'"
                        + " ORDER BY name",
                new MapSqlParameterSource("projectId", projectId),
                this::mapWatchlist);
    }

    @Override
    public CriticalWorkPackageRecord createWorkPackage(
            UUID projectId, UUID watchlistId, String name, String description, UUID createdByUserId) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO critical_work_packages (
                    project_id, critical_watchlist_id, name, description, created_by_user_id
                )
                VALUES (:projectId, :watchlistId, :name, :description, :createdBy)
                RETURNING
                """ + PACKAGE_COLUMNS,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("watchlistId", watchlistId)
                        .addValue("name", name)
                        .addValue("description", description)
                        .addValue("createdBy", createdByUserId),
                this::mapWorkPackage);
    }

    @Override
    public Optional<CriticalWorkPackageRecord> findWorkPackage(UUID projectId, UUID workPackageId) {
        return jdbcTemplate.query(
                "SELECT " + PACKAGE_COLUMNS
                        + " FROM critical_work_packages WHERE id = :id AND project_id = :projectId",
                new MapSqlParameterSource().addValue("id", workPackageId).addValue("projectId", projectId),
                this::mapWorkPackage).stream().findFirst();
    }

    @Override
    public List<CriticalWorkPackageRecord> findWorkPackages(UUID projectId, UUID watchlistId) {
        return jdbcTemplate.query(
                "SELECT " + PACKAGE_COLUMNS
                        + " FROM critical_work_packages WHERE critical_watchlist_id = :watchlistId"
                        + " AND project_id = :projectId AND status = 'active' ORDER BY name",
                new MapSqlParameterSource()
                        .addValue("watchlistId", watchlistId)
                        .addValue("projectId", projectId),
                this::mapWorkPackage);
    }

    @Override
    public CriticalWorkPackageSourceRecord addSource(
            UUID projectId,
            UUID workPackageId,
            UUID projectSnapshotId,
            UUID importedTaskId,
            String sourceType,
            boolean includeDescendants,
            UUID createdByUserId
    ) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO critical_work_package_sources (
                    project_id, critical_work_package_id, project_snapshot_id, imported_task_id,
                    source_type, include_descendants, created_by_user_id
                )
                VALUES (
                    :projectId, :workPackageId, :snapshotId, :importedTaskId,
                    :sourceType, :includeDescendants, :createdBy
                )
                RETURNING
                """ + SOURCE_COLUMNS,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("workPackageId", workPackageId)
                        .addValue("snapshotId", projectSnapshotId)
                        .addValue("importedTaskId", importedTaskId)
                        .addValue("sourceType", sourceType)
                        .addValue("includeDescendants", includeDescendants)
                        .addValue("createdBy", createdByUserId),
                this::mapSource);
    }

    @Override
    public List<CriticalWorkPackageSourceRecord> findSources(UUID workPackageId) {
        return jdbcTemplate.query(
                "SELECT " + SOURCE_COLUMNS
                        + " FROM critical_work_package_sources WHERE critical_work_package_id = :id",
                new MapSqlParameterSource("id", workPackageId),
                this::mapSource);
    }

    /**
     * Expands a package's sources into the tasks it reports on.
     *
     * <p>Walks down the imported hierarchy from each source summary task. This is reporting
     * scope only: it groups work for a report and performs no schedule calculation, no
     * rollup of dates, and no critical-path derivation.
     */
    @Override
    public List<UUID> findReportedTaskIds(UUID workPackageId) {
        return jdbcTemplate.queryForList(
                """
                WITH RECURSIVE scope AS (
                    SELECT t.id, s.include_descendants
                    FROM critical_work_package_sources s
                    JOIN imported_tasks t ON t.id = s.imported_task_id
                    WHERE s.critical_work_package_id = :workPackageId

                    UNION

                    SELECT child.id, scope.include_descendants
                    FROM scope
                    JOIN imported_tasks child ON child.parent_imported_task_id = scope.id
                    WHERE scope.include_descendants
                )
                SELECT DISTINCT id FROM scope
                """,
                new MapSqlParameterSource("workPackageId", workPackageId),
                UUID.class);
    }

    /**
     * Reporting coverage per package, in one query.
     *
     * <p>A left join, so a package nobody has reported on still appears — that is the case
     * worth surfacing. Superseded reports are excluded: the correction that replaced one is
     * itself a row here, and counting both would overstate how much reporting has happened.
     * Ordered so packages never reported on come first.
     */
    @Override
    public List<CriticalWorkPackageReportingSummary> findReportingSummaries(UUID projectId) {
        return jdbcTemplate.query(
                """
                SELECT p.id, p.critical_watchlist_id, p.name,
                       count(u.id) AS update_count,
                       max(u.submitted_at) AS last_submitted_at
                FROM critical_work_packages p
                JOIN critical_watchlists w
                  ON w.id = p.critical_watchlist_id AND w.status = 'active'
                LEFT JOIN critical_updates u
                  ON u.critical_work_package_id = p.id
                 AND u.status <> CAST('superseded' AS critical_update_status)
                WHERE p.project_id = :projectId AND p.status = 'active'
                GROUP BY p.id, p.critical_watchlist_id, p.name
                ORDER BY max(u.submitted_at) ASC NULLS FIRST, p.name
                """,
                new MapSqlParameterSource("projectId", projectId),
                (rs, rowNum) -> new CriticalWorkPackageReportingSummary(
                        rs.getObject("id", UUID.class),
                        rs.getObject("critical_watchlist_id", UUID.class),
                        rs.getString("name"),
                        rs.getInt("update_count"),
                        rs.getObject("last_submitted_at", OffsetDateTime.class)));
    }

    @Override
    public Optional<CriticalUpdateRecord> findUpdateByIdempotencyKey(UUID projectId, String idempotencyKey) {
        return jdbcTemplate.query(
                "SELECT " + UPDATE_COLUMNS
                        + " FROM critical_updates WHERE project_id = :projectId AND idempotency_key = :key",
                new MapSqlParameterSource().addValue("projectId", projectId).addValue("key", idempotencyKey),
                this::mapUpdate).stream().findFirst();
    }

    @Override
    public CriticalUpdateRecord submitUpdate(
            UUID projectId, UUID submittedByUserId, CriticalUpdateSubmitRequest request) {
        CriticalUpdateRecord update = jdbcTemplate.queryForObject(
                """
                INSERT INTO critical_updates (
                    project_id, critical_work_package_id, status, update_mode, submitted_at,
                    submitted_by_user_id, current_focus, current_blocker_summary, next_target,
                    idempotency_key, offline_local_id, supersedes_critical_update_id, review_state
                )
                VALUES (
                    :projectId, :workPackageId, CAST('submitted' AS critical_update_status), :updateMode,
                    now(), :submittedBy, :currentFocus, :blockerSummary, :nextTarget,
                    :idempotencyKey, :offlineLocalId, :supersedes, 'pending'
                )
                RETURNING
                """ + UPDATE_COLUMNS,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("workPackageId", request.criticalWorkPackageId())
                        .addValue("updateMode", request.updateMode())
                        .addValue("submittedBy", submittedByUserId)
                        .addValue("currentFocus", request.currentFocus())
                        .addValue("blockerSummary", request.currentBlockerSummary())
                        .addValue("nextTarget", request.nextTarget())
                        .addValue("idempotencyKey", request.idempotencyKey())
                        .addValue("offlineLocalId", request.offlineLocalId())
                        .addValue("supersedes", request.supersedesCriticalUpdateId()),
                this::mapUpdate);

        for (CriticalUpdateLineRequest line : request.lines()) {
            jdbcTemplate.update(
                    """
                    INSERT INTO critical_update_lines (
                        project_id, critical_update_id, imported_task_id, target_text, actual_text,
                        delay_or_issue_text, solution_or_next_action_text,
                        percent_complete, physical_percent_complete
                    )
                    VALUES (
                        :projectId, :updateId, :importedTaskId, :targetText, :actualText,
                        :delayText, :solutionText, :percentComplete, :physicalPercentComplete
                    )
                    """,
                    new MapSqlParameterSource()
                            .addValue("projectId", projectId)
                            .addValue("updateId", update.id())
                            .addValue("importedTaskId", line.importedTaskId())
                            .addValue("targetText", line.targetText())
                            .addValue("actualText", line.actualText())
                            .addValue("delayText", line.delayOrIssueText())
                            .addValue("solutionText", line.solutionOrNextActionText())
                            .addValue("percentComplete", line.percentComplete())
                            .addValue("physicalPercentComplete", line.physicalPercentComplete()));
        }
        return update;
    }

    @Override
    public int markUpdateSuperseded(UUID projectId, UUID workPackageId, UUID criticalUpdateId) {
        return jdbcTemplate.update(
                """
                UPDATE critical_updates
                SET status = CAST('superseded' AS critical_update_status), review_state = 'superseded'
                WHERE id = :id
                  AND project_id = :projectId
                  AND critical_work_package_id = :workPackageId
                """,
                new MapSqlParameterSource()
                        .addValue("id", criticalUpdateId)
                        .addValue("projectId", projectId)
                        .addValue("workPackageId", workPackageId));
    }

    @Override
    public List<CriticalUpdateRecord> findUpdates(UUID workPackageId) {
        return jdbcTemplate.query(
                "SELECT " + UPDATE_COLUMNS
                        + " FROM critical_updates WHERE critical_work_package_id = :id"
                        + " ORDER BY submitted_at DESC",
                new MapSqlParameterSource("id", workPackageId),
                this::mapUpdate);
    }

    @Override
    public int countUpdateLines(UUID criticalUpdateId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM critical_update_lines WHERE critical_update_id = :id",
                new MapSqlParameterSource("id", criticalUpdateId),
                Integer.class);
        return count == null ? 0 : count;
    }

    private CriticalWatchlistRecord mapWatchlist(ResultSet rs, int rowNum) throws SQLException {
        return new CriticalWatchlistRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("status"));
    }

    private CriticalWorkPackageRecord mapWorkPackage(ResultSet rs, int rowNum) throws SQLException {
        return new CriticalWorkPackageRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("critical_watchlist_id", UUID.class),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("status"));
    }

    private CriticalWorkPackageSourceRecord mapSource(ResultSet rs, int rowNum) throws SQLException {
        return new CriticalWorkPackageSourceRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("critical_work_package_id", UUID.class),
                rs.getObject("project_snapshot_id", UUID.class),
                rs.getObject("imported_task_id", UUID.class),
                rs.getString("source_type"),
                rs.getBoolean("include_descendants"));
    }

    private CriticalUpdateRecord mapUpdate(ResultSet rs, int rowNum) throws SQLException {
        return new CriticalUpdateRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("critical_work_package_id", UUID.class),
                rs.getString("status"),
                rs.getString("update_mode"),
                rs.getObject("submitted_at", OffsetDateTime.class),
                rs.getObject("submitted_by_user_id", UUID.class),
                rs.getString("current_focus"),
                rs.getString("current_blocker_summary"),
                rs.getString("next_target"),
                rs.getObject("supersedes_critical_update_id", UUID.class));
    }
}
