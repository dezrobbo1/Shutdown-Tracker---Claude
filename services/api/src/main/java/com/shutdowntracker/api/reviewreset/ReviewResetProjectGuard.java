package com.shutdowntracker.api.reviewreset;

import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Refuses to reset anything that is not the synthetic review project.
 *
 * <p>This is the blast-radius control, and it deliberately reuses a marker that already exists
 * rather than inventing an authorisation scheme. {@code ReviewProjectBootstrapService} writes
 * {@code synthetic: true} and {@code allowed_use: "review_bootstrap_only"} into the project's
 * metadata when it creates it, and no real project can acquire those: nothing else writes them, and
 * a project imported from Microsoft Project carries neither.
 *
 * <p>So even with the flag on and an administrator acting, the only project this endpoint can empty
 * is one the server created for review. That is a stronger guarantee than a permission check, which
 * would still be satisfied by an administrator pointing it at production data.
 */
@Component
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class ReviewResetProjectGuard {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ReviewResetProjectGuard(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @return the project's name, which the caller must then match against the typed confirmation
     * @throws ReviewResetRefusedException if the project is unknown or is not synthetic
     */
    public String requireSyntheticReviewProject(UUID projectId) {
        Optional<String> name = jdbcTemplate.query(
                        """
                        SELECT name
                          FROM projects
                         WHERE id = :projectId
                           AND metadata ->> 'synthetic' = 'true'
                           AND metadata ->> 'allowed_use' = 'review_bootstrap_only'
                        """,
                        new MapSqlParameterSource("projectId", projectId),
                        (rs, rowNum) -> rs.getString("name"))
                .stream()
                .findFirst();

        return name.orElseThrow(() -> new ReviewResetRefusedException(
                "This project is not a synthetic review project. Review data reset is refused."));
    }
}
