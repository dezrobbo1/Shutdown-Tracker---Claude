package com.shutdowntracker.api.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class JdbcUserRepository implements UserRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcUserRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public UserRecord create(UserCreateRequest request) {
        String sql = """
                INSERT INTO users (email, display_name, status, external_subject, metadata)
                VALUES (:email, :displayName, CAST(:status AS user_status), :externalSubject,
                        CAST(:metadata AS jsonb))
                RETURNING id, email, display_name, status, external_subject
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("email", request.email())
                .addValue("displayName", request.displayName())
                .addValue("status", request.status().databaseValue())
                .addValue("externalSubject", request.externalSubject())
                .addValue("metadata", toJson(request.metadata()));

        return jdbcTemplate.queryForObject(sql, parameters, this::mapUser);
    }

    @Override
    public Optional<UserRecord> findById(UUID userId) {
        return jdbcTemplate.query(
                """
                SELECT id, email, display_name, status, external_subject
                FROM users
                WHERE id = :id
                """,
                new MapSqlParameterSource("id", userId),
                this::mapUser).stream().findFirst();
    }

    @Override
    public Optional<UserRecord> findByEmail(String email) {
        // Matches the case-insensitive unique index, so a lookup cannot miss an account
        // that a differently-cased address would still collide with on insert.
        return jdbcTemplate.query(
                """
                SELECT id, email, display_name, status, external_subject
                FROM users
                WHERE lower(email) = lower(:email)
                """,
                new MapSqlParameterSource("email", email),
                this::mapUser).stream().findFirst();
    }

    @Override
    public ProjectMembershipRecord grantMembership(
            UUID projectId,
            UUID userId,
            ProjectRole role,
            UUID grantedByUserId,
            Map<String, Object> metadata
    ) {
        String sql = """
                INSERT INTO project_memberships (project_id, user_id, role, granted_by_user_id, metadata)
                VALUES (:projectId, :userId, CAST(:role AS project_role), :grantedByUserId,
                        CAST(:metadata AS jsonb))
                RETURNING id, project_id, user_id, role, active
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("userId", userId)
                .addValue("role", role.databaseValue())
                .addValue("grantedByUserId", grantedByUserId)
                .addValue("metadata", toJson(metadata));

        return jdbcTemplate.queryForObject(sql, parameters, this::mapMembership);
    }

    @Override
    public Optional<ProjectMembershipRecord> findActiveMembership(UUID projectId, UUID userId) {
        return jdbcTemplate.query(
                """
                SELECT id, project_id, user_id, role, active
                FROM project_memberships
                WHERE project_id = :projectId
                  AND user_id = :userId
                  AND active
                """,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("userId", userId),
                this::mapMembership).stream().findFirst();
    }

    @Override
    public void revokeMembership(UUID membershipId, UUID revokedByUserId) {
        // Guarded on `active` so a second run cannot move revoked_at forward on a membership that
        // was already withdrawn, which would misdate when the access actually ended.
        jdbcTemplate.update(
                """
                UPDATE project_memberships
                   SET active = false,
                       revoked_at = now(),
                       revoked_by_user_id = :revokedByUserId
                 WHERE id = :id
                   AND active
                """,
                new MapSqlParameterSource()
                        .addValue("id", membershipId)
                        .addValue("revokedByUserId", revokedByUserId));
    }

    @Override
    public void updateStatus(UUID userId, UserStatus status) {
        jdbcTemplate.update(
                """
                UPDATE users
                   SET status = CAST(:status AS user_status),
                       updated_at = now()
                 WHERE id = :id
                   AND status IS DISTINCT FROM CAST(:status AS user_status)
                """,
                new MapSqlParameterSource()
                        .addValue("id", userId)
                        .addValue("status", status.databaseValue()));
    }

    private UserRecord mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new UserRecord(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("display_name"),
                UserStatus.fromDatabaseValue(rs.getString("status")),
                rs.getString("external_subject"));
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize identity metadata.", exception);
        }
    }

    private ProjectMembershipRecord mapMembership(ResultSet rs, int rowNum) throws SQLException {
        return new ProjectMembershipRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                ProjectRole.fromDatabaseValue(rs.getString("role")),
                rs.getBoolean("active"));
    }
}
