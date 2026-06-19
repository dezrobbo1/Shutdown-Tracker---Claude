package com.shutdowntracker.api.project;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class JdbcProjectRepository implements ProjectRepository {

    private static final String REVIEW_BOOTSTRAP_SOURCE = "review_project_bootstrap";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcProjectRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ProjectRecord> findReviewBootstrapProject(String projectName) {
        String sql = """
                SELECT id, name, status, timezone
                FROM projects
                WHERE name = :name
                  AND metadata ->> 'source' = :source
                  AND metadata ->> 'synthetic' = 'true'
                ORDER BY created_at ASC
                LIMIT 1
                """;

        return jdbcTemplate.query(
                sql,
                Map.of("name", projectName, "source", REVIEW_BOOTSTRAP_SOURCE),
                (rs, rowNum) -> new ProjectRecord(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getString("timezone")
                )
        ).stream().findFirst();
    }

    @Override
    public ProjectRecord createReviewBootstrapProject(ReviewProjectCreateRequest request) {
        String sql = """
                INSERT INTO projects (name, description, status, timezone, metadata)
                VALUES (:name, :description, 'active', :timezone, CAST(:metadata AS jsonb))
                RETURNING id, name, status, timezone
                """;

        String metadata = "{\"source\":\"" + REVIEW_BOOTSTRAP_SOURCE
                + "\",\"synthetic\":true,\"allowed_use\":\"review_bootstrap_only\"}";

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("name", request.name())
                .addValue("description", request.description())
                .addValue("timezone", request.timezone())
                .addValue("metadata", metadata);

        return jdbcTemplate.queryForObject(sql, parameters, (rs, rowNum) -> new ProjectRecord(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("status"),
                rs.getString("timezone")
        ));
    }
}
