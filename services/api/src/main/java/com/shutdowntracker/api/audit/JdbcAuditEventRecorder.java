package com.shutdowntracker.api.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class JdbcAuditEventRecorder implements AuditEventRecorder {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcAuditEventRecorder(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(AuditEventCreateRequest event) {
        String sql = """
                INSERT INTO audit_events (
                    project_id,
                    event_category,
                    event_type,
                    event_version,
                    occurred_at,
                    actor_user_id,
                    actor_display_name,
                    actor_role,
                    actor_scope,
                    actor_type,
                    target_entity_type,
                    target_entity_id,
                    target_display_name,
                    old_value_summary,
                    new_value_summary,
                    reason,
                    client_context,
                    source_system,
                    correlation_id,
                    request_id,
                    idempotency_key,
                    offline_local_id,
                    project_snapshot_id,
                    export_batch_id,
                    evidence_id,
                    metadata
                )
                VALUES (
                    :projectId,
                    CAST(:eventCategory AS audit_event_category),
                    :eventType,
                    1,
                    :occurredAt,
                    :actorUserId,
                    :actorDisplayName,
                    :actorRole,
                    :actorScope,
                    CAST(:actorType AS audit_actor_type),
                    :targetEntityType,
                    :targetEntityId,
                    :targetDisplayName,
                    CAST(:oldValueSummary AS jsonb),
                    CAST(:newValueSummary AS jsonb),
                    :reason,
                    CAST(:clientContext AS jsonb),
                    :sourceSystem,
                    :correlationId,
                    :requestId,
                    :idempotencyKey,
                    :offlineLocalId,
                    :projectSnapshotId,
                    :exportBatchId,
                    :evidenceId,
                    CAST(:metadata AS jsonb)
                )
                """;

        jdbcTemplate.update(sql, parameters(event));
    }

    private MapSqlParameterSource parameters(AuditEventCreateRequest event) {
        return new MapSqlParameterSource()
                .addValue("projectId", event.projectId())
                .addValue("eventCategory", event.eventCategory().databaseValue())
                .addValue("eventType", event.eventType())
                .addValue("occurredAt", event.occurredAt())
                .addValue("actorUserId", event.actorUserId())
                .addValue("actorDisplayName", event.actorDisplayName())
                .addValue("actorRole", event.actorRole())
                .addValue("actorScope", event.actorScope())
                .addValue("actorType", event.actorType().databaseValue())
                .addValue("targetEntityType", event.targetEntityType())
                .addValue("targetEntityId", event.targetEntityId())
                .addValue("targetDisplayName", event.targetDisplayName())
                .addValue("oldValueSummary", toJson(event.oldValueSummary()))
                .addValue("newValueSummary", toJson(event.newValueSummary()))
                .addValue("reason", event.reason())
                .addValue("clientContext", toJson(event.clientContext()))
                .addValue("sourceSystem", event.sourceSystem())
                .addValue("correlationId", event.correlationId())
                .addValue("requestId", event.requestId())
                .addValue("idempotencyKey", event.idempotencyKey())
                .addValue("offlineLocalId", event.offlineLocalId())
                .addValue("projectSnapshotId", event.projectSnapshotId())
                .addValue("exportBatchId", event.exportBatchId())
                .addValue("evidenceId", event.evidenceId())
                .addValue("metadata", toJson(event.metadata()));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize audit event JSON fields.", exception);
        }
    }
}
