package com.shutdowntracker.api.approval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutdowntracker.api.exportpreview.ApprovalState;
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
public class JdbcApprovalRepository implements ApprovalRepository {

    private static final String APPROVAL_SELECT = """
            SELECT id,
                   project_id,
                   source_entity_type,
                   source_entity_id,
                   approval_state,
                   requested_by_user_id,
                   requested_at,
                   reviewed_by_user_id,
                   reviewed_at,
                   reason
            FROM approval_records
            """;

    /** States that no longer carry active meaning and must not be superseded again. */
    private static final String TERMINAL_STATES = "('superseded', 'rejected', 'exported')";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcApprovalRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public int supersedeActiveApprovals(UUID projectId, String sourceEntityType, UUID sourceEntityId) {
        String sql = """
                UPDATE approval_records
                SET approval_state = CAST('superseded' AS approval_state)
                WHERE project_id = :projectId
                  AND source_entity_type = :sourceEntityType
                  AND source_entity_id = :sourceEntityId
                  AND approval_state NOT IN
                """ + TERMINAL_STATES;

        return jdbcTemplate.update(sql, Map.of(
                "projectId", projectId,
                "sourceEntityType", sourceEntityType,
                "sourceEntityId", sourceEntityId
        ));
    }

    @Override
    public ApprovalRecord create(
            UUID projectId,
            UUID reviewedByUserId,
            ApprovalRecordCreateRequest request,
            Map<String, Object> metadata
    ) {
        String sql = """
                INSERT INTO approval_records (
                    project_id,
                    source_entity_type,
                    source_entity_id,
                    approval_state,
                    requested_by_user_id,
                    requested_at,
                    reviewed_by_user_id,
                    reviewed_at,
                    reason,
                    metadata
                )
                VALUES (
                    :projectId,
                    :sourceEntityType,
                    :sourceEntityId,
                    CAST(:approvalState AS approval_state),
                    :reviewedByUserId,
                    now(),
                    :reviewedByUserId,
                    now(),
                    :reason,
                    CAST(:metadata AS jsonb)
                )
                RETURNING id
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("sourceEntityType", request.sourceEntityType())
                .addValue("sourceEntityId", request.sourceEntityId())
                .addValue("approvalState", request.approvalState().databaseValue())
                .addValue("reviewedByUserId", reviewedByUserId)
                .addValue("reason", request.reason())
                .addValue("metadata", toJson(metadata));

        UUID id = jdbcTemplate.queryForObject(sql, parameters, UUID.class);
        return findById(projectId, id)
                .orElseThrow(() -> new IllegalStateException("Created approval record not found."));
    }

    @Override
    public Optional<ApprovalRecord> findLatest(UUID projectId, String sourceEntityType, UUID sourceEntityId) {
        String sql = APPROVAL_SELECT + """
                WHERE project_id = :projectId
                  AND source_entity_type = :sourceEntityType
                  AND source_entity_id = :sourceEntityId
                ORDER BY reviewed_at DESC NULLS LAST, created_at DESC, id DESC
                LIMIT 1
                """;

        return jdbcTemplate.query(sql, Map.of(
                "projectId", projectId,
                "sourceEntityType", sourceEntityType,
                "sourceEntityId", sourceEntityId
        ), this::mapApproval).stream().findFirst();
    }

    @Override
    public List<ApprovalRecord> listBySourceEntity(UUID projectId, String sourceEntityType, UUID sourceEntityId) {
        String sql = APPROVAL_SELECT + """
                WHERE project_id = :projectId
                  AND source_entity_type = :sourceEntityType
                  AND source_entity_id = :sourceEntityId
                ORDER BY created_at, id
                """;

        return jdbcTemplate.query(sql, Map.of(
                "projectId", projectId,
                "sourceEntityType", sourceEntityType,
                "sourceEntityId", sourceEntityId
        ), this::mapApproval);
    }

    private Optional<ApprovalRecord> findById(UUID projectId, UUID id) {
        String sql = APPROVAL_SELECT + """
                WHERE project_id = :projectId
                  AND id = :id
                """;

        return jdbcTemplate.query(sql, Map.of("projectId", projectId, "id", id), this::mapApproval)
                .stream()
                .findFirst();
    }

    private ApprovalRecord mapApproval(ResultSet rs, int rowNum) throws SQLException {
        return new ApprovalRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("source_entity_type"),
                rs.getObject("source_entity_id", UUID.class),
                ApprovalState.fromDatabaseValue(rs.getString("approval_state")),
                rs.getObject("requested_by_user_id", UUID.class),
                rs.getObject("requested_at", OffsetDateTime.class),
                rs.getObject("reviewed_by_user_id", UUID.class),
                rs.getObject("reviewed_at", OffsetDateTime.class),
                rs.getString("reason")
        );
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize approval record metadata.", exception);
        }
    }
}
