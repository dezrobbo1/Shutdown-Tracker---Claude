package com.shutdowntracker.api.audit;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record AuditEventCreateRequest(
        UUID projectId,
        AuditEventCategory eventCategory,
        String eventType,
        OffsetDateTime occurredAt,
        UUID actorUserId,
        String actorDisplayName,
        String actorRole,
        String actorScope,
        AuditActorType actorType,
        String targetEntityType,
        UUID targetEntityId,
        String targetDisplayName,
        Map<String, Object> oldValueSummary,
        Map<String, Object> newValueSummary,
        String reason,
        Map<String, Object> clientContext,
        String sourceSystem,
        String correlationId,
        String requestId,
        String idempotencyKey,
        String offlineLocalId,
        UUID projectSnapshotId,
        UUID exportBatchId,
        UUID evidenceId,
        Map<String, Object> metadata
) {
    public AuditEventCreateRequest {
        Objects.requireNonNull(projectId, "projectId is required.");
        Objects.requireNonNull(eventCategory, "eventCategory is required.");
        eventType = requireText(eventType, "eventType is required.");
        occurredAt = occurredAt == null ? OffsetDateTime.now(ZoneOffset.UTC) : occurredAt;
        actorType = actorType == null ? AuditActorType.SYSTEM : actorType;
        actorDisplayName = actorDisplayName == null ? "Shutdown Tracker API" : actorDisplayName;
        sourceSystem = sourceSystem == null ? "shutdown_tracker_api" : sourceSystem;
        oldValueSummary = copyMap(oldValueSummary);
        newValueSummary = copyMap(newValueSummary);
        clientContext = copyMap(clientContext);
        metadata = copyMap(metadata);
    }

    public static AuditEventCreateRequest systemEvent(
            UUID projectId,
            AuditEventCategory eventCategory,
            String eventType,
            String targetEntityType,
            UUID targetEntityId,
            String targetDisplayName,
            Map<String, Object> oldValueSummary,
            Map<String, Object> newValueSummary,
            String reason,
            UUID projectSnapshotId,
            UUID exportBatchId,
            Map<String, Object> metadata
    ) {
        return new AuditEventCreateRequest(
                projectId,
                eventCategory,
                eventType,
                null,
                null,
                null,
                null,
                null,
                AuditActorType.SYSTEM,
                targetEntityType,
                targetEntityId,
                targetDisplayName,
                oldValueSummary,
                newValueSummary,
                reason,
                Map.of("entrypoint", "api"),
                null,
                null,
                null,
                null,
                null,
                projectSnapshotId,
                exportBatchId,
                null,
                metadata
        );
    }

    /**
     * Records an event attributed to an authenticated user.
     *
     * <p>Actor identity comes from the resolved request actor, never from a request body.
     */
    public static AuditEventCreateRequest userEvent(
            UUID projectId,
            UUID actorUserId,
            String actorDisplayName,
            String actorRole,
            AuditEventCategory eventCategory,
            String eventType,
            String targetEntityType,
            UUID targetEntityId,
            String targetDisplayName,
            Map<String, Object> oldValueSummary,
            Map<String, Object> newValueSummary,
            String reason,
            UUID projectSnapshotId,
            UUID exportBatchId,
            Map<String, Object> metadata
    ) {
        return new AuditEventCreateRequest(
                projectId,
                eventCategory,
                eventType,
                null,
                Objects.requireNonNull(actorUserId, "actorUserId is required."),
                actorDisplayName,
                actorRole,
                null,
                AuditActorType.USER,
                targetEntityType,
                targetEntityId,
                targetDisplayName,
                oldValueSummary,
                newValueSummary,
                reason,
                Map.of("entrypoint", "api"),
                null,
                null,
                null,
                null,
                null,
                projectSnapshotId,
                exportBatchId,
                null,
                metadata
        );
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static Map<String, Object> copyMap(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
