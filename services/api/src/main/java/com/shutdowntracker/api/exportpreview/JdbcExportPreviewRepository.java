package com.shutdowntracker.api.exportpreview;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class JdbcExportPreviewRepository implements ExportPreviewRepository {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP_TYPE = new TypeReference<>() { };

    private static final String BATCH_SELECT = """
            SELECT eb.id,
                   eb.project_id,
                   eb.project_snapshot_id,
                   eb.status,
                   eb.preview_created_at,
                   eb.approved_at,
                   eb.approved_by_user_id,
                   eb.generated_at,
                   eb.generated_by_user_id,
                   eb.opened_in_microsoft_project_at,
                   eb.opened_in_microsoft_project_by_user_id,
                   eb.verified_at,
                   eb.verified_by_user_id,
                   eb.export_file_uri,
                   eb.export_file_hash,
                   eb.failure_reason,
                   eb.integrity_policy_version,
                   eb.line_set_sealed,
                   eb.metadata,
                   CAST(COUNT(ebl.id) AS int) AS line_count,
                   CAST(COUNT(ebl.id) FILTER (WHERE ebl.is_export_eligible) AS int) AS eligible_line_count,
                   CAST(COUNT(ebl.id) FILTER (WHERE NOT ebl.is_export_eligible) AS int) AS ineligible_line_count
            FROM export_batches eb
            LEFT JOIN export_batch_lines ebl ON ebl.export_batch_id = eb.id
            """;

    private static final String LINE_SELECT = """
            SELECT ebl.id,
                   ebl.export_batch_id,
                   ebl.project_id,
                   ebl.project_snapshot_id,
                   ebl.imported_task_id,
                   COALESCE(ebl.captured_task_external_uid, it.external_uid) AS imported_task_external_uid,
                   COALESCE(ebl.captured_task_external_id, it.external_id) AS imported_task_external_id,
                   COALESCE(ebl.captured_task_name, it.name) AS imported_task_name,
                   ebl.source_entity_type,
                   ebl.source_entity_id,
                   ebl.captured_approval_state AS approval_state,
                   ebl.captured_approval_record_id,
                   ebl.field_name,
                   ebl.old_value,
                   ebl.new_value,
                   ebl.source_actor_user_id,
                   ebl.source_timestamp,
                   ebl.reason,
                   ebl.is_leaf_task,
                   ebl.is_export_eligible,
                   ebl.integrity_policy_version,
                   ebl.authoritative_export_candidate_id,
                   ebl.captured_source_event_or_payload_hash,
                   ebl.captured_source_version
            FROM export_batch_lines ebl
            JOIN imported_tasks it ON it.id = ebl.imported_task_id
            """;

    private static final String CANDIDATE_SELECT = """
            SELECT id,
                   binding_policy_version,
                   project_id,
                   project_snapshot_id,
                   imported_task_id,
                   source_entity_type,
                   source_entity_id,
                   source_version,
                   field_name,
                   normalized_old_value,
                   normalized_new_value,
                   source_event_or_payload_hash,
                   captured_task_external_uid,
                   captured_task_external_id,
                   captured_task_name,
                   captured_is_leaf_task,
                   source_actor_user_id,
                   source_timestamp,
                   reason,
                   created_at,
                   metadata
            FROM export_candidate_records
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcExportPreviewRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExportPreviewBatchRecord createDraftPreview(
            UUID projectId,
            UUID projectSnapshotId,
            Map<String, Object> metadata
    ) {
        String sql = """
                INSERT INTO export_batches (
                    project_id,
                    project_snapshot_id,
                    status,
                    preview_created_at,
                    metadata
                )
                SELECT :projectId,
                       :projectSnapshotId,
                       CAST(:status AS export_batch_state),
                       now(),
                       CAST(:metadata AS jsonb)
                WHERE EXISTS (
                    SELECT 1
                    FROM project_snapshots
                    WHERE id = :projectSnapshotId
                      AND project_id = :projectId
                      AND status = 'accepted'
                )
                RETURNING id
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("projectSnapshotId", projectSnapshotId)
                .addValue("status", ExportBatchState.DRAFT_PREVIEW.databaseValue())
                .addValue("metadata", toJson(metadata));

        List<UUID> ids = jdbcTemplate.query(sql, parameters, (rs, rowNum) -> rs.getObject("id", UUID.class));
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Export preview requires an accepted project snapshot.");
        }

        return findBatch(projectId, ids.getFirst())
                .orElseThrow(() -> new IllegalStateException("Created export preview batch not found."));
    }

    @Override
    public Optional<ExportPreviewBatchRecord> findBatch(UUID projectId, UUID exportBatchId) {
        String sql = BATCH_SELECT + """
                WHERE eb.project_id = :projectId
                  AND eb.id = :exportBatchId
                GROUP BY eb.id,
                         eb.project_id,
                         eb.project_snapshot_id,
                         eb.status,
                         eb.preview_created_at,
                         eb.approved_at,
                         eb.approved_by_user_id,
                         eb.generated_at,
                         eb.generated_by_user_id,
                         eb.opened_in_microsoft_project_at,
                         eb.opened_in_microsoft_project_by_user_id,
                         eb.verified_at,
                         eb.verified_by_user_id,
                         eb.export_file_uri,
                         eb.export_file_hash,
                         eb.failure_reason,
                         eb.integrity_policy_version,
                         eb.line_set_sealed,
                         eb.metadata
                """;

        return jdbcTemplate.query(
                sql,
                Map.of("projectId", projectId, "exportBatchId", exportBatchId),
                this::mapBatch
        ).stream().findFirst();
    }

    @Override
    public boolean sealDraftPreviewLineSet(UUID projectId, UUID exportBatchId) {
        String sql = """
                UPDATE export_batches
                SET line_set_sealed = true
                WHERE project_id = :projectId
                  AND id = :exportBatchId
                  AND status = 'draft_preview'
                  AND integrity_policy_version = :integrityPolicyVersion
                  AND line_set_sealed = false
                """;

        return jdbcTemplate.update(
                sql,
                Map.of(
                        "projectId", projectId,
                        "exportBatchId", exportBatchId,
                        "integrityPolicyVersion", ExportIntegrityPolicy.CURRENT_VERSION
                )
        ) == 1;
    }

    @Override
    public boolean lockBatchForIntegrityValidation(UUID projectId, UUID exportBatchId) {
        String sql = """
                SELECT id
                FROM export_batches
                WHERE project_id = :projectId
                  AND id = :exportBatchId
                FOR UPDATE
                """;

        return !jdbcTemplate.query(
                sql,
                Map.of("projectId", projectId, "exportBatchId", exportBatchId),
                (rs, rowNum) -> rs.getObject("id", UUID.class)
        ).isEmpty();
    }

    @Override
    public Optional<ExportPreviewBatchRecord> approveBatch(
            UUID projectId,
            UUID exportBatchId,
            UUID approvedByUserId,
            Map<String, Object> metadata
    ) {
        String sql = """
                UPDATE export_batches
                SET status = CAST(:status AS export_batch_state),
                    approved_at = now(),
                    approved_by_user_id = :approvedByUserId,
                    metadata = CAST(:metadata AS jsonb)
                WHERE project_id = :projectId
                  AND id = :exportBatchId
                  AND status = 'draft_preview'
                  AND integrity_policy_version = :integrityPolicyVersion
                  AND line_set_sealed = true
                RETURNING id
                """;

        return updateBatchState(
                sql,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("exportBatchId", exportBatchId)
                        .addValue("approvedByUserId", approvedByUserId)
                        .addValue("status", ExportBatchState.APPROVED.databaseValue())
                        .addValue("integrityPolicyVersion", ExportIntegrityPolicy.CURRENT_VERSION)
                        .addValue("metadata", toJson(metadata)),
                projectId,
                exportBatchId
        );
    }

    @Override
    public Optional<ExportPreviewBatchRecord> rejectBatch(
            UUID projectId,
            UUID exportBatchId,
            Map<String, Object> metadata
    ) {
        String sql = """
                UPDATE export_batches
                SET status = CAST(:status AS export_batch_state),
                    metadata = CAST(:metadata AS jsonb)
                WHERE project_id = :projectId
                  AND id = :exportBatchId
                  AND status = 'draft_preview'
                  AND integrity_policy_version = :integrityPolicyVersion
                RETURNING id
                """;

        return updateBatchState(
                sql,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("exportBatchId", exportBatchId)
                        .addValue("status", ExportBatchState.REJECTED.databaseValue())
                        .addValue("integrityPolicyVersion", ExportIntegrityPolicy.CURRENT_VERSION)
                        .addValue("metadata", toJson(metadata)),
                projectId,
                exportBatchId
        );
    }

    @Override
    public Optional<ExportPreviewBatchRecord> markBatchGenerated(
            UUID projectId,
            UUID exportBatchId,
            String exportFileUri,
            String exportFileHash,
            UUID generatedByUserId,
            Map<String, Object> metadata
    ) {
        String sql = """
                UPDATE export_batches
                SET status = CAST(:status AS export_batch_state),
                    generated_at = now(),
                    generated_by_user_id = :generatedByUserId,
                    export_file_uri = :exportFileUri,
                    export_file_hash = :exportFileHash,
                    metadata = CAST(:metadata AS jsonb)
                WHERE project_id = :projectId
                  AND id = :exportBatchId
                  AND status = 'approved'
                  AND integrity_policy_version = :integrityPolicyVersion
                RETURNING id
                """;

        return updateBatchState(
                sql,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("exportBatchId", exportBatchId)
                        .addValue("exportFileUri", exportFileUri)
                        .addValue("exportFileHash", exportFileHash)
                        .addValue("generatedByUserId", generatedByUserId)
                        .addValue("status", ExportBatchState.GENERATED.databaseValue())
                        .addValue("integrityPolicyVersion", ExportIntegrityPolicy.CURRENT_VERSION)
                        .addValue("metadata", toJson(metadata)),
                projectId,
                exportBatchId
        );
    }

    @Override
    public Optional<ExportPreviewBatchRecord> markBatchOpenedInMicrosoftProject(
            UUID projectId,
            UUID exportBatchId,
            UUID openedByUserId,
            Map<String, Object> metadata
    ) {
        String sql = """
                UPDATE export_batches
                SET status = CAST(:status AS export_batch_state),
                    opened_in_microsoft_project_at = now(),
                    opened_in_microsoft_project_by_user_id = :openedByUserId,
                    metadata = CAST(:metadata AS jsonb)
                WHERE project_id = :projectId
                  AND id = :exportBatchId
                  AND status = 'generated'
                  AND integrity_policy_version = :integrityPolicyVersion
                RETURNING id
                """;

        return updateBatchState(
                sql,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("exportBatchId", exportBatchId)
                        .addValue("openedByUserId", openedByUserId)
                        .addValue("status", ExportBatchState.OPENED_IN_MICROSOFT_PROJECT.databaseValue())
                        .addValue("integrityPolicyVersion", ExportIntegrityPolicy.CURRENT_VERSION)
                        .addValue("metadata", toJson(metadata)),
                projectId,
                exportBatchId
        );
    }

    @Override
    public Optional<ExportPreviewBatchRecord> markBatchVerified(
            UUID projectId,
            UUID exportBatchId,
            UUID verifiedByUserId,
            Map<String, Object> metadata
    ) {
        String sql = """
                UPDATE export_batches
                SET status = CAST(:status AS export_batch_state),
                    verified_at = now(),
                    verified_by_user_id = :verifiedByUserId,
                    metadata = CAST(:metadata AS jsonb)
                WHERE project_id = :projectId
                  AND id = :exportBatchId
                  AND status = 'opened_in_microsoft_project'
                  AND integrity_policy_version = :integrityPolicyVersion
                RETURNING id
                """;

        return updateBatchState(
                sql,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("exportBatchId", exportBatchId)
                        .addValue("verifiedByUserId", verifiedByUserId)
                        .addValue("status", ExportBatchState.VERIFIED.databaseValue())
                        .addValue("integrityPolicyVersion", ExportIntegrityPolicy.CURRENT_VERSION)
                        .addValue("metadata", toJson(metadata)),
                projectId,
                exportBatchId
        );
    }

    @Override
    public Optional<ExportPreviewTaskContext> findTaskContext(
            UUID projectId,
            UUID projectSnapshotId,
            UUID importedTaskId
    ) {
        String sql = """
                SELECT id,
                       project_id,
                       project_snapshot_id,
                       external_uid,
                       external_id,
                       name,
                       is_summary,
                       percent_complete,
                       physical_percent_complete,
                       actual_start,
                       actual_finish
                FROM imported_tasks
                WHERE project_id = :projectId
                  AND project_snapshot_id = :projectSnapshotId
                  AND id = :importedTaskId
                FOR SHARE
                """;

        return jdbcTemplate.query(
                sql,
                Map.of(
                        "projectId", projectId,
                        "projectSnapshotId", projectSnapshotId,
                        "importedTaskId", importedTaskId
                ),
                (rs, rowNum) -> new ExportPreviewTaskContext(
                        rs.getObject("id", UUID.class),
                        rs.getObject("project_id", UUID.class),
                        rs.getObject("project_snapshot_id", UUID.class),
                        rs.getString("external_uid"),
                        rs.getString("external_id"),
                        rs.getString("name"),
                        rs.getBoolean("is_summary"),
                        rs.getBigDecimal("percent_complete"),
                        rs.getBigDecimal("physical_percent_complete"),
                        rs.getObject("actual_start", OffsetDateTime.class),
                        rs.getObject("actual_finish", OffsetDateTime.class)
                )
        ).stream().findFirst();
    }

    @Override
    public List<ExportPreviewTaskContext> lockTaskContextsForIntegrityValidation(
            UUID projectId,
            UUID projectSnapshotId,
            List<UUID> importedTaskIds
    ) {
        if (importedTaskIds.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT id,
                       project_id,
                       project_snapshot_id,
                       external_uid,
                       external_id,
                       name,
                       is_summary,
                       percent_complete,
                       physical_percent_complete,
                       actual_start,
                       actual_finish
                FROM imported_tasks
                WHERE project_id = :projectId
                  AND project_snapshot_id = :projectSnapshotId
                  AND id IN (:importedTaskIds)
                ORDER BY id
                FOR SHARE
                """;
        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("projectSnapshotId", projectSnapshotId)
                        .addValue("importedTaskIds", importedTaskIds),
                this::mapTaskContext
        );
    }

    @Override
    public ExportCandidateRecord createAuthoritativeCandidate(
            UUID projectId,
            ExportCandidateCreateRequest request,
            String normalizedProposedValue
    ) {
        String sql = """
                INSERT INTO export_candidate_records (
                    project_id,
                    project_snapshot_id,
                    imported_task_id,
                    source_entity_type,
                    source_entity_id,
                    source_version,
                    field_name,
                    normalized_new_value,
                    source_actor_user_id,
                    source_timestamp,
                    reason,
                    metadata
                )
                VALUES (
                    :projectId,
                    :projectSnapshotId,
                    :importedTaskId,
                    :sourceEntityType,
                    :sourceEntityId,
                    :sourceVersion,
                    :fieldName,
                    :normalizedNewValue,
                    :sourceActorUserId,
                    :sourceTimestamp,
                    :reason,
                    CAST(:metadata AS jsonb)
                )
                RETURNING id
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("projectSnapshotId", request.projectSnapshotId())
                .addValue("importedTaskId", request.importedTaskId())
                .addValue("sourceEntityType", request.sourceEntityType())
                .addValue("sourceEntityId", request.sourceEntityId())
                .addValue("sourceVersion", request.sourceVersion())
                .addValue("fieldName", request.fieldName())
                .addValue("normalizedNewValue", normalizedProposedValue)
                .addValue("sourceActorUserId", request.sourceActorUserId())
                .addValue("sourceTimestamp", request.sourceTimestamp())
                .addValue("reason", request.reason())
                .addValue("metadata", toJson(request.metadata()));

        UUID candidateId = jdbcTemplate.query(sql, parameters, (rs, rowNum) -> rs.getObject("id", UUID.class))
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Authoritative export candidate could not be created."));
        return findAuthoritativeCandidate(projectId, candidateId)
                .orElseThrow(() -> new IllegalStateException("Created authoritative export candidate not found."));
    }

    @Override
    public Optional<ExportCandidateApprovalEventRecord> createCandidateApprovalEvent(
            UUID projectId,
            UUID authoritativeExportCandidateId,
            ExportCandidateApprovalEventRequest request
    ) {
        String sql = """
                INSERT INTO approval_records (
                    project_id,
                    source_entity_type,
                    source_entity_id,
                    approval_state,
                    requested_at,
                    reviewed_by_user_id,
                    reviewed_at,
                    reason,
                    metadata,
                    authoritative_export_candidate_id,
                    candidate_binding_policy_version
                )
                SELECT candidate.project_id,
                       'export_candidate',
                       candidate.id,
                       CAST(:approvalState AS approval_state),
                       :requestedAt,
                       :reviewedByUserId,
                       :reviewedAt,
                       :reason,
                       CAST(:metadata AS jsonb),
                       candidate.id,
                       candidate.binding_policy_version
                FROM export_candidate_records candidate
                WHERE candidate.project_id = :projectId
                  AND candidate.id = :authoritativeExportCandidateId
                  AND candidate.binding_policy_version = :integrityPolicyVersion
                RETURNING id
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("authoritativeExportCandidateId", authoritativeExportCandidateId)
                .addValue("integrityPolicyVersion", ExportIntegrityPolicy.CURRENT_VERSION)
                .addValue("approvalState", request.approvalState().databaseValue())
                .addValue("requestedAt", request.requestedAt())
                .addValue("reviewedByUserId", request.reviewedByUserId())
                .addValue("reviewedAt", request.reviewedAt())
                .addValue("reason", request.reason())
                .addValue("metadata", toJson(request.metadata()));

        return jdbcTemplate.query(sql, parameters, (rs, rowNum) -> rs.getObject("id", UUID.class))
                .stream()
                .findFirst()
                .flatMap(approvalId -> findCandidateApprovalEvent(projectId, approvalId));
    }

    @Override
    public boolean lockAcceptedSnapshotForIntegrityValidation(UUID projectId, UUID projectSnapshotId) {
        String sql = """
                SELECT id
                FROM project_snapshots
                WHERE project_id = :projectId
                  AND id = :projectSnapshotId
                  AND status = 'accepted'
                FOR SHARE
                """;

        return !jdbcTemplate.query(
                sql,
                Map.of("projectId", projectId, "projectSnapshotId", projectSnapshotId),
                (rs, rowNum) -> rs.getObject("id", UUID.class)
        ).isEmpty();
    }

    @Override
    public Optional<ExportCandidateRecord> findAuthoritativeCandidate(
            UUID projectId,
            UUID authoritativeExportCandidateId
    ) {
        String sql = CANDIDATE_SELECT + """
                WHERE project_id = :projectId
                  AND id = :authoritativeExportCandidateId
                """;

        return jdbcTemplate.query(
                sql,
                Map.of(
                        "projectId", projectId,
                        "authoritativeExportCandidateId", authoritativeExportCandidateId
                ),
                this::mapCandidate
        ).stream().findFirst();
    }

    @Override
    public List<ExportCandidateRecord> lockAuthoritativeCandidatesForIntegrityValidation(
            UUID projectId,
            UUID projectSnapshotId,
            List<UUID> authoritativeExportCandidateIds
    ) {
        if (authoritativeExportCandidateIds.isEmpty()) {
            return List.of();
        }
        String sql = CANDIDATE_SELECT + """
                WHERE project_id = :projectId
                  AND project_snapshot_id = :projectSnapshotId
                  AND id IN (:authoritativeExportCandidateIds)
                ORDER BY id
                FOR SHARE
                """;
        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("projectSnapshotId", projectSnapshotId)
                        .addValue("authoritativeExportCandidateIds", authoritativeExportCandidateIds),
                this::mapCandidate
        );
    }

    @Override
    public List<ExportPreviewApprovalRecord> findCurrentApprovalCandidates(
            UUID projectId,
            UUID authoritativeExportCandidateId
    ) {
        String sql = """
                SELECT ar.id,
                       ar.approval_state,
                       ar.authoritative_export_candidate_id,
                       ar.candidate_binding_policy_version
                FROM approval_records ar
                WHERE ar.project_id = :projectId
                  AND ar.authoritative_export_candidate_id = :authoritativeExportCandidateId
                  AND ar.candidate_binding_policy_version = :integrityPolicyVersion
                  AND ar.approval_event_order = (
                      SELECT max(latest.approval_event_order)
                      FROM approval_records latest
                      WHERE latest.project_id = :projectId
                        AND latest.authoritative_export_candidate_id = :authoritativeExportCandidateId
                        AND latest.candidate_binding_policy_version = :integrityPolicyVersion
                  )
                """;

        return jdbcTemplate.query(
                sql,
                Map.of(
                        "projectId", projectId,
                        "authoritativeExportCandidateId", authoritativeExportCandidateId,
                        "integrityPolicyVersion", ExportIntegrityPolicy.CURRENT_VERSION
                ),
                (rs, rowNum) -> new ExportPreviewApprovalRecord(
                        rs.getObject("id", UUID.class),
                        ApprovalState.fromDatabaseValue(rs.getString("approval_state")),
                        rs.getObject("authoritative_export_candidate_id", UUID.class),
                        (Integer) rs.getObject("candidate_binding_policy_version")
                )
        );
    }

    @Override
    public List<ExportPreviewApprovalRecord> lockCurrentApprovalCandidatesForIntegrityValidation(
            UUID projectId,
            List<UUID> authoritativeExportCandidateIds
    ) {
        if (authoritativeExportCandidateIds.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT ar.id,
                       ar.approval_state,
                       ar.authoritative_export_candidate_id,
                       ar.candidate_binding_policy_version
                FROM approval_records ar
                JOIN (
                    SELECT authoritative_export_candidate_id,
                           max(approval_event_order) AS approval_event_order
                    FROM approval_records
                    WHERE project_id = :projectId
                      AND authoritative_export_candidate_id IN (:authoritativeExportCandidateIds)
                      AND candidate_binding_policy_version = :integrityPolicyVersion
                    GROUP BY authoritative_export_candidate_id
                ) latest
                  ON latest.authoritative_export_candidate_id = ar.authoritative_export_candidate_id
                 AND latest.approval_event_order = ar.approval_event_order
                WHERE ar.project_id = :projectId
                  AND ar.candidate_binding_policy_version = :integrityPolicyVersion
                ORDER BY ar.authoritative_export_candidate_id
                FOR SHARE OF ar
                """;
        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("authoritativeExportCandidateIds", authoritativeExportCandidateIds)
                        .addValue("integrityPolicyVersion", ExportIntegrityPolicy.CURRENT_VERSION),
                (rs, rowNum) -> new ExportPreviewApprovalRecord(
                        rs.getObject("id", UUID.class),
                        ApprovalState.fromDatabaseValue(rs.getString("approval_state")),
                        rs.getObject("authoritative_export_candidate_id", UUID.class),
                        (Integer) rs.getObject("candidate_binding_policy_version")
                )
        );
    }

    @Override
    public ExportPreviewLineRecord createLine(
            UUID projectId,
            UUID projectSnapshotId,
            UUID exportBatchId,
            UUID authoritativeExportCandidateId
    ) {
        String sql = """
                INSERT INTO export_batch_lines (
                    export_batch_id,
                    project_id,
                    project_snapshot_id,
                    imported_task_id,
                    source_entity_type,
                    source_entity_id,
                    captured_approval_record_id,
                    captured_approval_state,
                    authoritative_export_candidate_id,
                    field_name,
                    old_value,
                    new_value,
                    captured_source_event_or_payload_hash,
                    captured_source_version,
                    captured_task_external_uid,
                    captured_task_external_id,
                    captured_task_name,
                    source_actor_user_id,
                    source_timestamp,
                    reason,
                    is_leaf_task,
                    is_export_eligible,
                    integrity_policy_version,
                    metadata
                )
                SELECT
                    :exportBatchId,
                    :projectId,
                    :projectSnapshotId,
                    candidate.imported_task_id,
                    candidate.source_entity_type,
                    candidate.source_entity_id,
                    approval.id,
                    approval.approval_state,
                    candidate.id,
                    candidate.field_name,
                    candidate.normalized_old_value,
                    candidate.normalized_new_value,
                    candidate.source_event_or_payload_hash,
                    candidate.source_version,
                    candidate.captured_task_external_uid,
                    candidate.captured_task_external_id,
                    candidate.captured_task_name,
                    candidate.source_actor_user_id,
                    candidate.source_timestamp,
                    candidate.reason,
                    candidate.captured_is_leaf_task,
                    approval.approval_state = 'approved_for_export'::approval_state
                        AND candidate.captured_is_leaf_task
                        AND candidate.field_name IN ('percent_complete', 'actual_start', 'actual_finish'),
                    candidate.binding_policy_version,
                    candidate.metadata
                FROM export_candidate_records candidate
                JOIN LATERAL (
                    SELECT ar.id,
                           ar.approval_state
                    FROM approval_records ar
                    WHERE ar.project_id = candidate.project_id
                      AND ar.authoritative_export_candidate_id = candidate.id
                      AND ar.candidate_binding_policy_version = candidate.binding_policy_version
                    ORDER BY ar.approval_event_order DESC
                    LIMIT 1
                ) approval ON true
                WHERE candidate.id = :authoritativeExportCandidateId
                  AND candidate.project_id = :projectId
                  AND candidate.project_snapshot_id = :projectSnapshotId
                  AND candidate.binding_policy_version = :integrityPolicyVersion
                RETURNING id
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("exportBatchId", exportBatchId)
                .addValue("projectId", projectId)
                .addValue("projectSnapshotId", projectSnapshotId)
                .addValue("authoritativeExportCandidateId", authoritativeExportCandidateId)
                .addValue("integrityPolicyVersion", ExportIntegrityPolicy.CURRENT_VERSION);

        List<UUID> lineIds = jdbcTemplate.query(sql, parameters, (rs, rowNum) -> rs.getObject("id", UUID.class));
        if (lineIds.size() != 1) {
            throw new IllegalArgumentException("Authoritative export candidate is not available for this preview.");
        }
        UUID lineId = lineIds.getFirst();
        return listLines(projectId, exportBatchId).stream()
                .filter(record -> record.id().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Created export preview line not found."));
    }

    @Override
    public List<ExportPreviewLineRecord> listLines(UUID projectId, UUID exportBatchId) {
        String sql = LINE_SELECT + """
                WHERE ebl.project_id = :projectId
                  AND ebl.export_batch_id = :exportBatchId
                ORDER BY ebl.created_at, ebl.id
                """;

        return jdbcTemplate.query(
                sql,
                Map.of("projectId", projectId, "exportBatchId", exportBatchId),
                this::mapLine
        );
    }

    private ExportPreviewBatchRecord mapBatch(ResultSet rs, int rowNum) throws SQLException {
        return new ExportPreviewBatchRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("project_snapshot_id", UUID.class),
                ExportBatchState.fromDatabaseValue(rs.getString("status")),
                rs.getObject("preview_created_at", OffsetDateTime.class),
                rs.getObject("approved_at", OffsetDateTime.class),
                rs.getObject("approved_by_user_id", UUID.class),
                rs.getObject("generated_at", OffsetDateTime.class),
                rs.getObject("generated_by_user_id", UUID.class),
                rs.getObject("opened_in_microsoft_project_at", OffsetDateTime.class),
                rs.getObject("opened_in_microsoft_project_by_user_id", UUID.class),
                rs.getObject("verified_at", OffsetDateTime.class),
                rs.getObject("verified_by_user_id", UUID.class),
                rs.getString("export_file_uri"),
                rs.getString("export_file_hash"),
                rs.getString("failure_reason"),
                rs.getInt("line_count"),
                rs.getInt("eligible_line_count"),
                rs.getInt("ineligible_line_count"),
                (Integer) rs.getObject("integrity_policy_version"),
                (Boolean) rs.getObject("line_set_sealed"),
                fromJson(rs.getString("metadata"))
        );
    }

    private ExportCandidateRecord mapCandidate(ResultSet rs, int rowNum) throws SQLException {
        return new ExportCandidateRecord(
                rs.getObject("id", UUID.class),
                (Integer) rs.getObject("binding_policy_version"),
                rs.getObject("project_id", UUID.class),
                rs.getObject("project_snapshot_id", UUID.class),
                rs.getObject("imported_task_id", UUID.class),
                rs.getString("source_entity_type"),
                rs.getObject("source_entity_id", UUID.class),
                rs.getString("source_version"),
                rs.getString("field_name"),
                rs.getString("normalized_old_value"),
                rs.getString("normalized_new_value"),
                rs.getString("source_event_or_payload_hash"),
                rs.getString("captured_task_external_uid"),
                rs.getString("captured_task_external_id"),
                rs.getString("captured_task_name"),
                rs.getBoolean("captured_is_leaf_task"),
                rs.getObject("source_actor_user_id", UUID.class),
                rs.getObject("source_timestamp", OffsetDateTime.class),
                rs.getString("reason"),
                rs.getObject("created_at", OffsetDateTime.class),
                fromJson(rs.getString("metadata"))
        );
    }

    private ExportPreviewTaskContext mapTaskContext(ResultSet rs, int rowNum) throws SQLException {
        return new ExportPreviewTaskContext(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("project_snapshot_id", UUID.class),
                rs.getString("external_uid"),
                rs.getString("external_id"),
                rs.getString("name"),
                rs.getBoolean("is_summary"),
                rs.getBigDecimal("percent_complete"),
                rs.getBigDecimal("physical_percent_complete"),
                rs.getObject("actual_start", OffsetDateTime.class),
                rs.getObject("actual_finish", OffsetDateTime.class)
        );
    }

    private ExportPreviewLineRecord mapLine(ResultSet rs, int rowNum) throws SQLException {
        String approvalState = rs.getString("approval_state");
        return new ExportPreviewLineRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("export_batch_id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("project_snapshot_id", UUID.class),
                rs.getObject("imported_task_id", UUID.class),
                rs.getString("imported_task_external_uid"),
                rs.getString("imported_task_external_id"),
                rs.getString("imported_task_name"),
                rs.getString("source_entity_type"),
                rs.getObject("source_entity_id", UUID.class),
                approvalState == null ? null : ApprovalState.fromDatabaseValue(approvalState),
                rs.getObject("captured_approval_record_id", UUID.class),
                rs.getString("field_name"),
                rs.getString("old_value"),
                rs.getString("new_value"),
                rs.getObject("source_actor_user_id", UUID.class),
                rs.getObject("source_timestamp", OffsetDateTime.class),
                rs.getString("reason"),
                rs.getBoolean("is_leaf_task"),
                rs.getBoolean("is_export_eligible"),
                (Integer) rs.getObject("integrity_policy_version"),
                rs.getObject("authoritative_export_candidate_id", UUID.class),
                rs.getString("captured_source_event_or_payload_hash"),
                rs.getString("captured_source_version")
        );
    }

    private Optional<ExportPreviewBatchRecord> updateBatchState(
            String sql,
            MapSqlParameterSource parameters,
            UUID projectId,
            UUID exportBatchId
    ) {
        List<UUID> ids = jdbcTemplate.query(sql, parameters, (rs, rowNum) -> rs.getObject("id", UUID.class));
        if (ids.isEmpty()) {
            return Optional.empty();
        }

        return findBatch(projectId, exportBatchId);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize export preview metadata.", exception);
        }
    }

    private Map<String, Object> fromJson(String value) {
        try {
            return objectMapper.readValue(value, OBJECT_MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize export integrity metadata.", exception);
        }
    }

    private Optional<ExportCandidateApprovalEventRecord> findCandidateApprovalEvent(
            UUID projectId,
            UUID approvalRecordId
    ) {
        String sql = """
                SELECT ar.id,
                       ar.project_id,
                       candidate.project_snapshot_id,
                       ar.authoritative_export_candidate_id,
                       ar.candidate_binding_policy_version,
                       ar.approval_state,
                       ar.requested_by_user_id,
                       ar.requested_at,
                       ar.reviewed_by_user_id,
                       ar.reviewed_at,
                       ar.reason,
                       ar.created_at,
                       ar.metadata
                FROM approval_records ar
                JOIN export_candidate_records candidate
                  ON candidate.id = ar.authoritative_export_candidate_id
                 AND candidate.binding_policy_version = ar.candidate_binding_policy_version
                 AND candidate.project_id = ar.project_id
                WHERE ar.project_id = :projectId
                  AND ar.id = :approvalRecordId
                """;
        return jdbcTemplate.query(
                sql,
                Map.of("projectId", projectId, "approvalRecordId", approvalRecordId),
                (rs, rowNum) -> new ExportCandidateApprovalEventRecord(
                        rs.getObject("id", UUID.class),
                        rs.getObject("project_id", UUID.class),
                        rs.getObject("project_snapshot_id", UUID.class),
                        rs.getObject("authoritative_export_candidate_id", UUID.class),
                        (Integer) rs.getObject("candidate_binding_policy_version"),
                        ApprovalState.fromDatabaseValue(rs.getString("approval_state")),
                        rs.getObject("requested_by_user_id", UUID.class),
                        rs.getObject("requested_at", OffsetDateTime.class),
                        rs.getObject("reviewed_by_user_id", UUID.class),
                        rs.getObject("reviewed_at", OffsetDateTime.class),
                        rs.getString("reason"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        fromJson(rs.getString("metadata"))
                )
        ).stream().findFirst();
    }
}
