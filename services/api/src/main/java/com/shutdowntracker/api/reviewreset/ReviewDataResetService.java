package com.shutdowntracker.api.reviewreset;

import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.audit.AuditEventCategory;
import com.shutdowntracker.api.audit.AuditEventCreateRequest;
import com.shutdowntracker.api.audit.AuditEventRecorder;
import com.shutdowntracker.api.audit.AuditEventTypes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Empties a synthetic review project back to nothing, so a trial can be walked again from a known
 * start.
 *
 * <p><strong>The audit trail is wiped, and that needs saying out loud.</strong> AGENTS.md asks that
 * append-only history be preserved, and this deletes all of it. It is defensible here on three
 * conditions, all of which must hold: the project carries the synthetic marker, so no real history
 * is reachable; the feature is behind a flag that is off by default, so a production deployment does
 * not have the route at all; and the reset writes its own record as the first entry of the trail it
 * created, so the wipe is itself audited. If any of those three stops being true, this stops being
 * defensible.
 *
 * <p>The audit row is written <em>after</em> the truncate, inside the same transaction. Written
 * before, it would be deleted by the statement that follows it.
 */
@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class ReviewDataResetService {

    private final JdbcTemplate jdbcTemplate;
    private final AuditEventRecorder auditEventRecorder;

    public ReviewDataResetService(JdbcTemplate jdbcTemplate, AuditEventRecorder auditEventRecorder) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditEventRecorder = auditEventRecorder;
    }

    @Transactional
    public List<ReviewDataResetResult.TableReset> reset(UUID projectId, String projectName, Actor actor) {
        // TRUNCATE takes ACCESS EXCLUSIVE on every table at once. Without a timeout, pressing this
        // while an import parse is in flight parks a pool connection until that finishes; with one,
        // it fails quickly and says so.
        jdbcTemplate.execute("SET LOCAL lock_timeout = '5s'");

        List<ReviewDataResetResult.TableReset> counted = new ArrayList<>();
        for (String table : ReviewDataResetScope.WIPE) {
            Long rows = jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
            counted.add(new ReviewDataResetResult.TableReset(table, rows == null ? 0L : rows));
        }

        jdbcTemplate.execute(ReviewDataResetScope.truncateStatement());

        auditEventRecorder.record(AuditEventCreateRequest.userEvent(
                projectId,
                actor.userId(),
                actor.displayName(),
                actor.role(),
                AuditEventCategory.PROJECT,
                AuditEventTypes.REVIEW_DATA_RESET,
                "project",
                projectId,
                projectName,
                Map.of("rowsByTable", rowsByTable(counted)),
                Map.of(),
                "Synthetic review data cleared so the trial can be walked from a known start.",
                null,
                null,
                Map.of("keptTables", ReviewDataResetScope.KEEP)));

        return List.copyOf(counted);
    }

    /**
     * Records that the database was reset but its files were not, so a half-cleared deployment is
     * discoverable later rather than only in whoever was watching the response.
     */
    public void recordIncompleteBlobCleanup(
            UUID projectId,
            String projectName,
            Actor actor,
            List<ReviewDataResetResult.BlobReset> failures
    ) {
        Map<String, Object> detail = new LinkedHashMap<>();
        for (ReviewDataResetResult.BlobReset failure : failures) {
            detail.put(failure.root(), failure.error());
        }
        auditEventRecorder.record(AuditEventCreateRequest.userEvent(
                projectId,
                actor.userId(),
                actor.displayName(),
                actor.role(),
                AuditEventCategory.PROJECT,
                AuditEventTypes.REVIEW_DATA_RESET_BLOBS_INCOMPLETE,
                "project",
                projectId,
                projectName,
                Map.of(),
                Map.of("roots", detail),
                "Stored files were left behind. Nothing references them, but the disk is not clear.",
                null,
                null,
                Map.of()));
    }

    private static Map<String, Object> rowsByTable(List<ReviewDataResetResult.TableReset> counted) {
        Map<String, Object> rows = new LinkedHashMap<>();
        for (ReviewDataResetResult.TableReset table : counted) {
            if (table.rowsDeleted() > 0) {
                rows.put(table.name(), table.rowsDeleted());
            }
        }
        return rows;
    }
}
