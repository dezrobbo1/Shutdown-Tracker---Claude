package com.shutdowntracker.api.candidate;

import com.shutdowntracker.api.exportpreview.ExportBatchState;
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
public class JdbcCandidateScheduleRunRepository implements CandidateScheduleRunRepository {

    /**
     * The run joined to the user who returned it. The storage URI is left out on purpose; it is
     * read by its own query, by the one caller that serves the bytes.
     */
    private static final String RUN_SELECT = """
            SELECT r.id,
                   r.project_id,
                   r.export_batch_id,
                   r.project_snapshot_id,
                   r.accepted_source_file_id,
                   r.accepted_source_file_hash,
                   r.generated_artifact_hash,
                   r.state,
                   r.candidate_original_filename,
                   r.candidate_content_hash,
                   r.candidate_size_bytes,
                   r.microsoft_project_version,
                   r.planner_note,
                   r.returned_at,
                   r.returned_by_user_id,
                   u.display_name AS returned_by_display_name,
                   r.superseded_by_candidate_schedule_run_id
            FROM candidate_schedule_runs r
            JOIN users u ON u.id = r.returned_by_user_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCandidateScheduleRunRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ExportBatchForReturn> findExportBatch(UUID projectId, UUID exportBatchId) {
        String sql = """
                SELECT id, project_snapshot_id, status, export_file_hash
                FROM export_batches
                WHERE id = :exportBatchId AND project_id = :projectId
                """;
        return jdbcTemplate.query(
                        sql,
                        Map.of("exportBatchId", exportBatchId, "projectId", projectId),
                        (rs, rowNum) -> new ExportBatchForReturn(
                                rs.getObject("id", UUID.class),
                                rs.getObject("project_snapshot_id", UUID.class),
                                ExportBatchState.fromDatabaseValue(rs.getString("status")),
                                rs.getString("export_file_hash")))
                .stream()
                .findFirst();
    }

    @Override
    public Optional<AcceptedSource> findAcceptedSource(UUID projectId, UUID projectSnapshotId) {
        // The same chain candidate generation resolves the source through. Every hop is an
        // existing non-null foreign key, so this needs no schema of its own.
        String sql = """
                SELECT sf.id, sf.content_hash
                FROM project_snapshots ps
                JOIN import_batches ib ON ib.id = ps.import_batch_id
                JOIN source_files sf ON sf.id = ib.source_file_id
                WHERE ps.id = :projectSnapshotId AND ps.project_id = :projectId
                """;
        return jdbcTemplate.query(
                        sql,
                        Map.of("projectSnapshotId", projectSnapshotId, "projectId", projectId),
                        (rs, rowNum) -> new AcceptedSource(
                                rs.getObject("id", UUID.class),
                                rs.getString("content_hash")))
                .stream()
                .findFirst();
    }

    @Override
    public Optional<CandidateScheduleRunRecord> findByContentHash(
            UUID projectId, UUID exportBatchId, String candidateContentHash) {
        String sql = RUN_SELECT + """
                WHERE r.project_id = :projectId
                  AND r.export_batch_id = :exportBatchId
                  AND r.candidate_content_hash = :contentHash
                """;
        return jdbcTemplate.query(
                        sql,
                        Map.of(
                                "projectId", projectId,
                                "exportBatchId", exportBatchId,
                                "contentHash", candidateContentHash),
                        this::mapRun)
                .stream()
                .findFirst();
    }

    @Override
    public CandidateScheduleRunRecord create(NewCandidateScheduleRun run) {
        String sql = """
                INSERT INTO candidate_schedule_runs (
                    project_id,
                    export_batch_id,
                    project_snapshot_id,
                    accepted_source_file_id,
                    accepted_source_file_hash,
                    generated_artifact_hash,
                    state,
                    candidate_original_filename,
                    candidate_storage_uri,
                    candidate_content_hash,
                    candidate_size_bytes,
                    microsoft_project_version,
                    planner_note,
                    returned_by_user_id
                ) VALUES (
                    :projectId,
                    :exportBatchId,
                    :projectSnapshotId,
                    :acceptedSourceFileId,
                    :acceptedSourceFileHash,
                    :generatedArtifactHash,
                    CAST(:state AS candidate_schedule_run_state),
                    :candidateOriginalFilename,
                    :candidateStorageUri,
                    :candidateContentHash,
                    :candidateSizeBytes,
                    :microsoftProjectVersion,
                    :plannerNote,
                    :returnedByUserId
                )
                RETURNING id
                """;
        UUID id = jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource()
                        .addValue("projectId", run.projectId())
                        .addValue("exportBatchId", run.exportBatchId())
                        .addValue("projectSnapshotId", run.projectSnapshotId())
                        .addValue("acceptedSourceFileId", run.acceptedSourceFileId())
                        .addValue("acceptedSourceFileHash", run.acceptedSourceFileHash())
                        .addValue("generatedArtifactHash", run.generatedArtifactHash())
                        .addValue("state", CandidateScheduleRunState.RETURNED.databaseValue())
                        .addValue("candidateOriginalFilename", run.candidateOriginalFilename())
                        .addValue("candidateStorageUri", run.candidateStorageUri())
                        .addValue("candidateContentHash", run.candidateContentHash())
                        .addValue("candidateSizeBytes", run.candidateSizeBytes())
                        .addValue("microsoftProjectVersion", run.microsoftProjectVersion())
                        .addValue("plannerNote", run.plannerNote())
                        .addValue("returnedByUserId", run.returnedByUserId()),
                UUID.class);

        return find(run.projectId(), id).orElseThrow(
                () -> new IllegalStateException("Candidate schedule run vanished immediately after it was created."));
    }

    @Override
    public List<CandidateScheduleRunRecord> findForExportBatch(UUID projectId, UUID exportBatchId) {
        String sql = RUN_SELECT + """
                WHERE r.project_id = :projectId AND r.export_batch_id = :exportBatchId
                ORDER BY r.returned_at DESC
                """;
        return jdbcTemplate.query(
                sql, Map.of("projectId", projectId, "exportBatchId", exportBatchId), this::mapRun);
    }

    @Override
    public List<CandidateScheduleRunRecord> findForProject(UUID projectId) {
        String sql = RUN_SELECT + """
                WHERE r.project_id = :projectId
                ORDER BY r.returned_at DESC
                """;
        return jdbcTemplate.query(sql, Map.of("projectId", projectId), this::mapRun);
    }

    @Override
    public Optional<CandidateScheduleRunRecord> find(UUID projectId, UUID runId) {
        String sql = RUN_SELECT + """
                WHERE r.project_id = :projectId AND r.id = :runId
                """;
        return jdbcTemplate.query(sql, Map.of("projectId", projectId, "runId", runId), this::mapRun)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<String> findStorageUri(UUID projectId, UUID runId) {
        String sql = """
                SELECT candidate_storage_uri
                FROM candidate_schedule_runs
                WHERE project_id = :projectId AND id = :runId
                """;
        return jdbcTemplate.queryForList(
                        sql, Map.of("projectId", projectId, "runId", runId), String.class)
                .stream()
                .findFirst();
    }

    private CandidateScheduleRunRecord mapRun(ResultSet rs, int rowNum) throws SQLException {
        return new CandidateScheduleRunRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("export_batch_id", UUID.class),
                rs.getObject("project_snapshot_id", UUID.class),
                rs.getObject("accepted_source_file_id", UUID.class),
                rs.getString("accepted_source_file_hash"),
                rs.getString("generated_artifact_hash"),
                CandidateScheduleRunState.fromDatabaseValue(rs.getString("state")),
                rs.getString("candidate_original_filename"),
                rs.getString("candidate_content_hash"),
                rs.getLong("candidate_size_bytes"),
                rs.getString("microsoft_project_version"),
                rs.getString("planner_note"),
                rs.getObject("returned_at", OffsetDateTime.class),
                rs.getObject("returned_by_user_id", UUID.class),
                rs.getString("returned_by_display_name"),
                rs.getObject("superseded_by_candidate_schedule_run_id", UUID.class));
    }
}
