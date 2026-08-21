package com.shutdowntracker.api.reviewdemo;

import com.shutdowntracker.api.identity.ProjectRole;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class JdbcReviewDemoIdentityRepository implements ReviewDemoIdentityRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcReviewDemoIdentityRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ReviewDemoIdentity> findSeeded(String datasetId) {
        // Every clause here is a guard rather than a filter. The marker is what the seeder wrote,
        // so a real user cannot appear in this result even if the flag is on by mistake; the
        // dataset id keeps one dataset from listing another's people; and the active/status
        // conditions mean this lists only identities that would actually be allowed to act.
        return jdbcTemplate.query(
                """
                SELECT u.id, u.display_name, m.role, m.project_id
                FROM users u
                JOIN project_memberships m ON m.user_id = u.id AND m.active
                WHERE u.metadata ->> 'synthetic' = 'true'
                  AND u.metadata ->> 'demo_dataset_id' = :datasetId
                  AND u.status = 'active'
                ORDER BY u.display_name
                """,
                new MapSqlParameterSource("datasetId", datasetId),
                this::mapIdentity);
    }

    private ReviewDemoIdentity mapIdentity(ResultSet rs, int rowNum) throws SQLException {
        return new ReviewDemoIdentity(
                rs.getObject("id", UUID.class),
                rs.getString("display_name"),
                ProjectRole.fromDatabaseValue(rs.getString("role")),
                rs.getObject("project_id", UUID.class));
    }
}
