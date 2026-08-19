package com.shutdowntracker.api.assignment;

import com.shutdowntracker.api.importedproject.ProjectSnapshotStatus;
import com.shutdowntracker.api.importreview.ImportReviewTaskRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class JdbcProjectResourceLinkRepository implements ProjectResourceLinkRepository {

    /**
     * A link row plus the user it names.
     *
     * <p>{@code matched_in_snapshot} and {@code resource_name_in_snapshot} are resolved per query
     * against whichever snapshot is being asked about, and are left false/null where no snapshot is
     * in play. They are not columns on the table: a link is project-scoped and outlives any snapshot,
     * so storing a match would be storing an answer that goes stale on the next import.
     */
    private static final String LINK_SELECT = """
            SELECT l.id,
                   l.project_id,
                   l.user_id,
                   u.display_name AS user_display_name,
                   l.resource_external_uid,
                   l.resource_name_at_link,
                   l.active,
                   l.linked_at,
                   l.linked_by_user_id,
                   l.revoked_at,
                   l.revoked_by_user_id
            FROM project_resource_links l
            JOIN users u ON u.id = l.user_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcProjectResourceLinkRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<AcceptedSnapshot> findNewestAcceptedSnapshot(UUID projectId) {
        String sql = """
                SELECT id, snapshot_version
                FROM project_snapshots
                WHERE project_id = :projectId
                  AND status = CAST(:accepted AS project_snapshot_status)
                ORDER BY snapshot_version DESC, created_at DESC
                LIMIT 1
                """;
        return jdbcTemplate.query(
                        sql,
                        Map.of("projectId", projectId, "accepted", ProjectSnapshotStatus.ACCEPTED.databaseValue()),
                        (rs, rowNum) -> new AcceptedSnapshot(rs.getObject("id", UUID.class), rs.getInt("snapshot_version")))
                .stream()
                .findFirst();
    }

    @Override
    public List<ProjectResourceLinkRecord> findActiveLinksForUser(UUID projectId, UUID userId) {
        String sql = LINK_SELECT + """
                WHERE l.project_id = :projectId
                  AND l.user_id = :userId
                  AND l.active
                ORDER BY l.resource_external_uid
                """;
        return jdbcTemplate.query(
                sql, Map.of("projectId", projectId, "userId", userId), this::mapLink);
    }

    @Override
    public List<ProjectResourceLinkRecord> findLinks(UUID projectId, UUID snapshotId) {
        // Active links first, then most recently revoked: a planner looking at this list is almost
        // always asking who is linked now, and the revoked rows are history below it.
        String sql = LINK_SELECT + """
                WHERE l.project_id = :projectId
                ORDER BY l.active DESC, l.resource_external_uid, l.linked_at DESC
                """;
        List<ProjectResourceLinkRecord> links =
                jdbcTemplate.query(sql, Map.of("projectId", projectId), this::mapLink);
        if (snapshotId == null || links.isEmpty()) {
            return links;
        }

        Map<String, String> namesInSnapshot = resourceNames(projectId, snapshotId,
                links.stream().map(ProjectResourceLinkRecord::resourceExternalUid).distinct().toList());
        return links.stream()
                .map(link -> withSnapshotMatch(link, namesInSnapshot))
                .toList();
    }

    @Override
    public List<String> findMatchingResourceUids(UUID projectId, UUID snapshotId, List<String> resourceExternalUids) {
        if (resourceExternalUids.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT DISTINCT external_uid
                FROM imported_resources
                WHERE project_id = :projectId
                  AND project_snapshot_id = :snapshotId
                  AND external_uid IN (:uids)
                ORDER BY external_uid
                """;
        return jdbcTemplate.queryForList(
                sql,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("snapshotId", snapshotId)
                        .addValue("uids", resourceExternalUids),
                String.class);
    }

    @Override
    public List<ImportReviewTaskRow> findLeafTasksAssignedToResources(
            UUID projectId, UUID snapshotId, List<String> resourceExternalUids) {
        if (resourceExternalUids.isEmpty()) {
            return List.of();
        }
        // EXISTS rather than a join, so a task assigned to two of the reader's resources appears
        // once. Summary tasks are excluded here rather than by the caller because a summary task is
        // never executable work, and an assignment to one is a roll-up, not a job.
        String sql = """
                SELECT t.id,
                       t.external_uid,
                       t.external_id,
                       t.name,
                       t.wbs,
                       t.outline_number,
                       t.outline_level,
                       t.is_summary,
                       t.parent_external_uid,
                       t.parent_imported_task_id,
                       t.planned_start,
                       t.planned_finish,
                       t.actual_start,
                       t.actual_finish,
                       t.percent_complete,
                       t.physical_percent_complete,
                       t.notes
                FROM imported_tasks t
                WHERE t.project_id = :projectId
                  AND t.project_snapshot_id = :snapshotId
                  AND NOT t.is_summary
                  AND EXISTS (
                      SELECT 1
                      FROM imported_assignments a
                      WHERE a.project_id = t.project_id
                        AND a.project_snapshot_id = t.project_snapshot_id
                        AND a.resource_external_uid IN (:uids)
                        AND (a.imported_task_id = t.id OR a.task_external_uid = t.external_uid)
                  )
                ORDER BY COALESCE(t.outline_number, ''), COALESCE(t.external_id, ''), t.created_at, t.id
                """;
        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("snapshotId", snapshotId)
                        .addValue("uids", resourceExternalUids),
                this::mapTask);
    }

    @Override
    public Optional<String> findResourceName(UUID projectId, UUID snapshotId, String resourceExternalUid) {
        return Optional.ofNullable(
                resourceNames(projectId, snapshotId, List.of(resourceExternalUid)).get(resourceExternalUid));
    }

    @Override
    public ProjectResourceLinkRecord createLink(
            UUID projectId,
            UUID userId,
            String resourceExternalUid,
            String resourceNameAtLink,
            UUID linkedByUserId
    ) {
        String sql = """
                INSERT INTO project_resource_links (
                    project_id, user_id, resource_external_uid, resource_name_at_link, linked_by_user_id
                )
                VALUES (:projectId, :userId, :resourceExternalUid, :resourceNameAtLink, :linkedByUserId)
                RETURNING id
                """;
        UUID id = jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("userId", userId)
                        .addValue("resourceExternalUid", resourceExternalUid)
                        .addValue("resourceNameAtLink", resourceNameAtLink)
                        .addValue("linkedByUserId", linkedByUserId),
                UUID.class);
        return findLink(projectId, id).orElseThrow(
                () -> new IllegalStateException("Inserted resource link could not be read back."));
    }

    @Override
    public Optional<ProjectResourceLinkRecord> findActiveLink(UUID projectId, UUID linkId) {
        return findLink(projectId, linkId).filter(ProjectResourceLinkRecord::active);
    }

    @Override
    public Optional<ProjectResourceLinkRecord> revokeLink(UUID projectId, UUID linkId, UUID revokedByUserId) {
        // Revoking is a state change on the row, never a delete: who linked whom, and who undid it,
        // is exactly the history the audit rules exist to keep. Guarded on `active` so a second
        // revoke reports "already revoked" rather than overwriting the first revoker.
        String sql = """
                UPDATE project_resource_links
                SET active = false,
                    revoked_at = now(),
                    revoked_by_user_id = :revokedByUserId
                WHERE project_id = :projectId
                  AND id = :linkId
                  AND active
                """;
        int updated = jdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("linkId", linkId)
                        .addValue("revokedByUserId", revokedByUserId));
        return updated == 0 ? Optional.empty() : findLink(projectId, linkId);
    }

    @Override
    public List<LinkableUser> findLinkableUsers(UUID projectId) {
        String sql = """
                SELECT u.id, u.display_name, m.role
                FROM project_memberships m
                JOIN users u ON u.id = m.user_id
                WHERE m.project_id = :projectId
                  AND m.active
                  AND u.status = CAST('active' AS user_status)
                ORDER BY u.display_name, u.id
                """;
        return jdbcTemplate.query(
                sql,
                Map.of("projectId", projectId),
                (rs, rowNum) -> new LinkableUser(
                        rs.getObject("id", UUID.class), rs.getString("display_name"), rs.getString("role")));
    }

    @Override
    public List<LinkableResource> findLinkableResources(UUID projectId, UUID snapshotId) {
        // The leaf-task count is the number that makes this list usable: a shutdown schedule
        // carries plant, materials and cost resources alongside people, and the ones worth linking
        // are the ones work is actually booked against. Ordering by it puts them first.
        String sql = """
                SELECT r.external_uid,
                       MIN(r.name) AS name,
                       MIN(r.resource_type) AS resource_type,
                       CAST(COUNT(DISTINCT t.id) AS int) AS assigned_leaf_task_count,
                       MIN(CAST(l.user_id AS text)) AS linked_user_id,
                       MIN(u.display_name) AS linked_user_display_name
                FROM imported_resources r
                LEFT JOIN imported_assignments a
                       ON a.project_id = r.project_id
                      AND a.project_snapshot_id = r.project_snapshot_id
                      AND a.resource_external_uid = r.external_uid
                LEFT JOIN imported_tasks t
                       ON t.project_id = a.project_id
                      AND t.project_snapshot_id = a.project_snapshot_id
                      AND NOT t.is_summary
                      AND (a.imported_task_id = t.id OR a.task_external_uid = t.external_uid)
                LEFT JOIN project_resource_links l
                       ON l.project_id = r.project_id
                      AND l.resource_external_uid = r.external_uid
                      AND l.active
                LEFT JOIN users u ON u.id = l.user_id
                WHERE r.project_id = :projectId
                  AND r.project_snapshot_id = :snapshotId
                  AND r.external_uid IS NOT NULL
                GROUP BY r.external_uid
                ORDER BY assigned_leaf_task_count DESC, MIN(r.name), r.external_uid
                """;
        return jdbcTemplate.query(
                sql,
                Map.of("projectId", projectId, "snapshotId", snapshotId),
                (rs, rowNum) -> {
                    String linkedUserId = rs.getString("linked_user_id");
                    return new LinkableResource(
                            rs.getString("external_uid"),
                            rs.getString("name"),
                            rs.getString("resource_type"),
                            rs.getInt("assigned_leaf_task_count"),
                            linkedUserId == null ? null : UUID.fromString(linkedUserId),
                            rs.getString("linked_user_display_name"));
                });
    }

    private Optional<ProjectResourceLinkRecord> findLink(UUID projectId, UUID linkId) {
        String sql = LINK_SELECT + """
                WHERE l.project_id = :projectId
                  AND l.id = :linkId
                """;
        return jdbcTemplate
                .query(sql, Map.of("projectId", projectId, "linkId", linkId), this::mapLink)
                .stream()
                .findFirst();
    }

    private Map<String, String> resourceNames(UUID projectId, UUID snapshotId, List<String> uids) {
        if (uids.isEmpty()) {
            return Map.of();
        }
        String sql = """
                SELECT external_uid, MIN(name) AS name
                FROM imported_resources
                WHERE project_id = :projectId
                  AND project_snapshot_id = :snapshotId
                  AND external_uid IN (:uids)
                GROUP BY external_uid
                """;
        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("snapshotId", snapshotId)
                        .addValue("uids", uids),
                rs -> {
                    Map<String, String> names = new java.util.LinkedHashMap<>();
                    while (rs.next()) {
                        names.put(rs.getString("external_uid"), rs.getString("name"));
                    }
                    return names;
                });
    }

    private ProjectResourceLinkRecord withSnapshotMatch(
            ProjectResourceLinkRecord link, Map<String, String> namesInSnapshot) {
        boolean matched = namesInSnapshot.containsKey(link.resourceExternalUid());
        return new ProjectResourceLinkRecord(
                link.id(),
                link.projectId(),
                link.userId(),
                link.userDisplayName(),
                link.resourceExternalUid(),
                link.resourceNameAtLink(),
                link.active(),
                link.linkedAt(),
                link.linkedByUserId(),
                link.revokedAt(),
                link.revokedByUserId(),
                matched,
                namesInSnapshot.get(link.resourceExternalUid()));
    }

    private ProjectResourceLinkRecord mapLink(ResultSet rs, int rowNum) throws SQLException {
        return new ProjectResourceLinkRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("user_display_name"),
                rs.getString("resource_external_uid"),
                rs.getString("resource_name_at_link"),
                rs.getBoolean("active"),
                rs.getObject("linked_at", OffsetDateTime.class),
                rs.getObject("linked_by_user_id", UUID.class),
                rs.getObject("revoked_at", OffsetDateTime.class),
                rs.getObject("revoked_by_user_id", UUID.class),
                false,
                null);
    }

    private ImportReviewTaskRow mapTask(ResultSet rs, int rowNum) throws SQLException {
        return new ImportReviewTaskRow(
                rs.getObject("id", UUID.class),
                rs.getString("external_uid"),
                rs.getString("external_id"),
                rs.getString("name"),
                rs.getString("wbs"),
                rs.getString("outline_number"),
                (Integer) rs.getObject("outline_level"),
                rs.getBoolean("is_summary"),
                rs.getString("parent_external_uid"),
                rs.getObject("parent_imported_task_id", UUID.class),
                rs.getObject("planned_start", OffsetDateTime.class),
                rs.getObject("planned_finish", OffsetDateTime.class),
                rs.getObject("actual_start", OffsetDateTime.class),
                rs.getObject("actual_finish", OffsetDateTime.class),
                rs.getBigDecimal("percent_complete"),
                rs.getBigDecimal("physical_percent_complete"),
                rs.getString("notes"));
    }
}
