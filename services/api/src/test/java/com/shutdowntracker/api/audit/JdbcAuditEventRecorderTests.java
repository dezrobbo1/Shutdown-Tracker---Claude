package com.shutdowntracker.api.audit;

import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutdowntracker.api.support.AbstractDatabaseTest;
import com.shutdowntracker.api.support.DatabaseFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link JdbcAuditEventRecorder} against a real PostgreSQL server.
 *
 * <p>The audit trail is the product's evidence of who changed what, so its insert has to
 * be verified against the real engine: it casts two enums and four {@code jsonb} columns
 * in a single statement, none of which a mocked template would catch.
 */
class JdbcAuditEventRecorderTests extends AbstractDatabaseTest {

    private JdbcAuditEventRecorder recorder;
    private UUID projectId;
    private DatabaseFixtures fixtures;

    @BeforeEach
    void setUp() {
        recorder = new JdbcAuditEventRecorder(
                new NamedParameterJdbcTemplate(dataSource()), new ObjectMapper());
        fixtures = new DatabaseFixtures(jdbcTemplate());
        projectId = fixtures.createProject("Audit Trail");
    }

    @Test
    void recordsASystemEvent() {
        recorder.record(AuditEventCreateRequest.systemEvent(
                projectId, AuditEventCategory.IMPORT, "import.batch.parsed",
                "import_batch", UUID.randomUUID(), "KILN.xml",
                Map.of(), Map.of("taskCount", 3055), null, null, null, Map.of()));

        Map<String, Object> stored = jdbcTemplate().queryForMap(
                "SELECT event_category::text, event_type, actor_type::text, actor_display_name FROM audit_events");

        assertThat(stored.get("event_category")).isEqualTo("import");
        assertThat(stored.get("event_type")).isEqualTo("import.batch.parsed");
        assertThat(stored.get("actor_type")).isEqualTo("system");
        assertThat(stored.get("actor_display_name")).isEqualTo("Shutdown Tracker API");
    }

    @Test
    void recordsAUserEventWithAttribution() {
        UUID actorId = fixtures.createUser("dana.reyes@example.com", "Dana Reyes");

        recorder.record(AuditEventCreateRequest.userEvent(
                projectId, actorId, "Dana Reyes", "planner",
                AuditEventCategory.APPROVAL, "export.batch.approved",
                "export_batch", UUID.randomUUID(), "Batch 7",
                Map.of("state", "pending"), Map.of("state", "approved"),
                "Reviewed against the field returns.", null, null, Map.of()));

        Map<String, Object> stored = jdbcTemplate().queryForMap(
                """
                SELECT actor_user_id, actor_display_name, actor_role, actor_type::text,
                       reason, old_value_summary::text AS old_value, new_value_summary::text AS new_value
                FROM audit_events
                """);

        assertThat(stored.get("actor_user_id")).isEqualTo(actorId);
        assertThat(stored.get("actor_display_name")).isEqualTo("Dana Reyes");
        assertThat(stored.get("actor_role")).isEqualTo("planner");
        assertThat(stored.get("actor_type"))
                .describedAs("an attributed event must not be recorded as a system action")
                .isEqualTo("user");
        assertThat(stored.get("reason")).isEqualTo("Reviewed against the field returns.");
        assertThat((String) stored.get("old_value")).contains("pending");
        assertThat((String) stored.get("new_value")).contains("approved");
    }

    @Test
    void recordsEveryAuditCategory() {
        for (AuditEventCategory category : AuditEventCategory.values()) {
            recorder.record(AuditEventCreateRequest.systemEvent(
                    projectId, category, "event." + category.name().toLowerCase(),
                    "thing", UUID.randomUUID(), "Thing",
                    Map.of(), Map.of(), null, null, null, Map.of()));
        }

        Integer recorded = jdbcTemplate().queryForObject(
                "SELECT count(*) FROM audit_events", Integer.class);

        assertThat(recorded)
                .describedAs("every category constant must map to a valid audit_event_category value")
                .isEqualTo(AuditEventCategory.values().length);
    }

    @Test
    void defaultsOccurredAtWhenTheCallerOmitsIt() {
        recorder.record(AuditEventCreateRequest.systemEvent(
                projectId, AuditEventCategory.EXPORT, "export.generated",
                "export_batch", UUID.randomUUID(), "Batch 1",
                Map.of(), Map.of(), null, null, null, Map.of()));

        Integer withTimestamp = jdbcTemplate().queryForObject(
                "SELECT count(*) FROM audit_events WHERE occurred_at IS NOT NULL", Integer.class);

        assertThat(withTimestamp).isEqualTo(1);
    }

    @Test
    void refusesToAttributeAnEventToAUserThatDoesNotExist() {
        // The point of the foreign key added in V007: an audit trail that can name
        // a non-existent user is not evidence of anything.
        assertThatThrownBy(() -> recorder.record(AuditEventCreateRequest.userEvent(
                projectId, UUID.randomUUID(), "Ghost", "planner",
                AuditEventCategory.APPROVAL, "export.batch.approved",
                "export_batch", UUID.randomUUID(), "Batch 9",
                Map.of(), Map.of(), null, null, null, Map.of())))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
