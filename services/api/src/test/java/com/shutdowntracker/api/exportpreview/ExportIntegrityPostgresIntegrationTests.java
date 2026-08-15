package com.shutdowntracker.api.exportpreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.shutdowntracker.api.audit.AuditEventTypes;
import com.shutdowntracker.api.exportpreview.handoff.ExportArtifactGenerationRequest;
import com.shutdowntracker.api.exportpreview.handoff.ExportArtifactHandoffService;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientResponseException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local")
class ExportIntegrityPostgresIntegrationTests {

    private static final UUID PROJECT_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_FILE_ID = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID IMPORT_BATCH_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID SNAPSHOT_ID = UUID.fromString("30000000-0000-0000-0000-000000000004");
    private static final UUID TASK_ID = UUID.fromString("30000000-0000-0000-0000-000000000005");
    private static final UUID SOURCE_ENTITY_ID = UUID.fromString("30000000-0000-0000-0000-000000000006");
    private static final UUID SOURCE_ACTOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000007");
    private static final UUID APPROVER_ID = UUID.fromString("30000000-0000-0000-0000-000000000008");
    private static final UUID GENERATOR_ID = UUID.fromString("30000000-0000-0000-0000-000000000009");
    private static final UUID OPENER_ID = UUID.fromString("30000000-0000-0000-0000-000000000010");
    private static final UUID VERIFIER_ID = UUID.fromString("30000000-0000-0000-0000-000000000011");
    private static final String ARTIFACT_HASH = "a".repeat(64);

    private static final Path MIGRATIONS = Path.of(System.getProperty("user.dir"))
            .resolve("..")
            .resolve("..")
            .resolve("infra")
            .resolve("migrations")
            .normalize()
            .toAbsolutePath();
    private static final DockerPostgres POSTGRES = DockerPostgres.start();
    private static final Path STORAGE_ROOT = createStorageRoot();
    private static final HttpServer FAILING_WORKER = startFailingWorker();

    @DynamicPropertySource
    static void databaseAndWorkerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::jdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::username);
        registry.add("spring.datasource.password", POSTGRES::password);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add(
                "spring.flyway.locations",
                () -> "filesystem:" + MIGRATIONS.toString().replace('\\', '/')
        );
        registry.add("shutdown-tracker.persistence.enabled", () -> "true");
        registry.add("shutdown-tracker.project-export-worker.enabled", () -> "true");
        registry.add(
                "shutdown-tracker.project-export-worker.base-url",
                () -> "http://127.0.0.1:" + FAILING_WORKER.getAddress().getPort()
        );
        registry.add(
                "shutdown-tracker.export-artifact-storage.local-root",
                () -> STORAGE_ROOT.toString()
        );
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ExportCandidateService candidateService;

    @Autowired
    private ExportPreviewService previewService;

    @Autowired
    private ExportArtifactHandoffService handoffService;

    @Autowired
    private ExportPreviewRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE projects CASCADE");
        seedAcceptedSnapshotAndLeafTask();
    }

    @AfterAll
    static void stopWorkerAndRemoveTemporaryStorage() throws IOException {
        FAILING_WORKER.stop(0);
        POSTGRES.stop();
        if (Files.exists(STORAGE_ROOT)) {
            try (var paths = Files.walk(STORAGE_ROOT)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        throw new IllegalStateException("Failed to remove integration-test storage.", exception);
                    }
                });
            }
        }
    }

    @Test
    void httpWorkerFailureRollsBackThroughSpringProxyAndLeavesApprovedBatchRetryable() {
        ApprovedFixture fixture = createApprovedFixture();
        ExportPreviewBatchRecord beforeFailure = requiredBatch(fixture.batchId());

        assertThat(AopUtils.isAopProxy(handoffService)).isTrue();
        assertThat(repository).isInstanceOf(JdbcExportPreviewRepository.class);

        assertThatThrownBy(() -> handoffService.generateArtifact(
                PROJECT_ID,
                fixture.batchId(),
                new ExportArtifactGenerationRequest(
                        GENERATOR_ID,
                        "Controlled worker failure",
                        Map.of(
                                "generatedAt", "caller-forged",
                                "exportFileHash", "caller-forged",
                                "generation", Map.of("exportFileUri", "caller-forged")
                        )
                )
        )).isInstanceOf(RestClientResponseException.class);

        ExportPreviewBatchRecord afterFailure = requiredBatch(fixture.batchId());
        assertThat(afterFailure.status()).isEqualTo(ExportBatchState.APPROVED);
        assertThat(afterFailure.generatedAt()).isNull();
        assertThat(afterFailure.generatedByUserId()).isNull();
        assertThat(afterFailure.exportFileUri()).isNull();
        assertThat(afterFailure.exportFileHash()).isNull();
        assertThat(afterFailure.openedInMicrosoftProjectAt()).isNull();
        assertThat(afterFailure.openedInMicrosoftProjectByUserId()).isNull();
        assertThat(afterFailure.verifiedAt()).isNull();
        assertThat(afterFailure.verifiedByUserId()).isNull();
        assertThat(afterFailure.metadata()).isEqualTo(beforeFailure.metadata());
        assertThat(afterFailure.metadata()).doesNotContainKey("generation");
        assertThat(auditCount(AuditEventTypes.EXPORT_FILE_GENERATED)).isZero();

        ExportPreviewDetail retry = new TransactionTemplate(transactionManager).execute(
                status -> previewService.getApprovedPreviewForArtifactGeneration(PROJECT_ID, fixture.batchId())
        );
        assertThat(retry).isNotNull();
        assertThat(retry.batch().status()).isEqualTo(ExportBatchState.APPROVED);
        assertThat(retry.lines()).hasSize(1);
        assertThat(retry.lines().getFirst().sourceApprovalRecordId()).isEqualTo(fixture.latestApprovalId());
    }

    @Test
    void jdbcLifecycleMappingProtectsStructuredProvenanceAndEveryEstablishedFact() {
        ApprovedFixture fixture = createApprovedFixture();
        ExportPreviewBatchRecord approved = requiredBatch(fixture.batchId());

        assertThat(approved.approvedAt()).isNotNull();
        assertThat(approved.approvedByUserId()).isEqualTo(APPROVER_ID);
        assertThat(nestedMap(sectionMap(approved, "preview"), "clientMetadata"))
                .containsEntry("approval", "caller-preview-collision");
        assertThat(nestedMap(sectionMap(approved, "approval"), "clientMetadata"))
                .containsEntry("generatedAt", "caller-approval-collision");
        assertThat(section(approved, "approval", "approvedByUserId")).isEqualTo(APPROVER_ID.toString());

        assertDataFailure("""
                UPDATE export_batches
                SET approved_at = approved_at + interval '1 second'
                WHERE id = ?
                """, fixture.batchId());
        assertDataFailure("""
                UPDATE export_batches
                SET metadata = '{"approval":{"approvedByUserId":"caller-forged"}}'::jsonb
                WHERE id = ?
                """, fixture.batchId());
        assertDataFailure("""
                UPDATE export_batches
                SET status = 'generated',
                    generated_at = now(),
                    generated_by_user_id = ?,
                    export_file_uri = 'file:///tmp/forged.xml',
                    export_file_hash = ?,
                    approved_by_user_id = ?,
                    metadata = '{"clientMetadata":{}}'::jsonb
                WHERE id = ?
                """, GENERATOR_ID, ARTIFACT_HASH, UUID.randomUUID(), fixture.batchId());
        assertDataFailure("""
                UPDATE export_batches
                SET status = 'generated',
                    generated_at = now(),
                    generated_by_user_id = ?,
                    export_file_uri = 'file:///tmp/forged.xml',
                    export_file_hash = ?,
                    metadata = '{"generatedAt":"caller-forged"}'::jsonb
                WHERE id = ?
                """, GENERATOR_ID, ARTIFACT_HASH, fixture.batchId());

        ExportPreviewDetail generated = previewService.markGenerated(
                PROJECT_ID,
                fixture.batchId(),
                new ExportBatchGeneratedRequest(
                        "file:///synthetic/authorized.xml",
                        ARTIFACT_HASH,
                        GENERATOR_ID,
                        "Generated by controlled worker",
                        Map.of(
                                "generatedByUserId", "caller-forged",
                                "exportFileUri", "caller-forged"
                        ),
                        Map.of("worker", "controlled-test-worker", "projectWriteBack", false)
                )
        );
        Map<String, Object> generationSection = sectionMap(generated.batch(), "generation");
        assertThat(generated.batch().generatedAt()).isNotNull();
        assertThat(generationSection)
                .containsEntry("generatedByUserId", GENERATOR_ID.toString())
                .containsEntry("exportFileUri", "file:///synthetic/authorized.xml")
                .containsEntry("exportFileHash", ARTIFACT_HASH);
        assertThat(nestedMap(generationSection, "clientMetadata"))
                .containsEntry("generatedByUserId", "caller-forged")
                .containsEntry("exportFileUri", "caller-forged");
        assertThat(nestedMap(generationSection, "provenance"))
                .containsEntry("worker", "controlled-test-worker")
                .containsEntry("projectWriteBack", false);

        assertDataFailure("""
                UPDATE export_batches
                SET generated_at = generated_at + interval '1 second',
                    generated_by_user_id = ?,
                    export_file_uri = 'file:///rewritten.xml',
                    export_file_hash = ?
                WHERE id = ?
                """, UUID.randomUUID(), "b".repeat(64), fixture.batchId());

        ExportPreviewDetail opened = previewService.markOpenedInMicrosoftProject(
                PROJECT_ID,
                fixture.batchId(),
                new ExportBatchProjectOpenRequest(
                        OPENER_ID,
                        "Planner opened generated artifact",
                        Map.of(
                                "openedByUserId", "caller-forged",
                                "generation", Map.of("exportFileHash", "caller-forged")
                        )
                )
        );
        assertThat(opened.batch().openedInMicrosoftProjectAt()).isNotNull();
        assertThat(opened.batch().openedInMicrosoftProjectByUserId()).isEqualTo(OPENER_ID);
        assertThat(section(opened.batch(), "microsoftProjectOpen", "openedByUserId"))
                .isEqualTo(OPENER_ID.toString());
        assertThat(nestedMap(sectionMap(opened.batch(), "microsoftProjectOpen"), "clientMetadata"))
                .containsEntry("openedByUserId", "caller-forged")
                .containsEntry("generation", Map.of("exportFileHash", "caller-forged"));
        assertThat(sectionMap(opened.batch(), "generation")).isEqualTo(generationSection);

        assertDataFailure(
                """
                        UPDATE export_batches
                        SET opened_in_microsoft_project_at = opened_in_microsoft_project_at + interval '1 second',
                            opened_in_microsoft_project_by_user_id = ?
                        WHERE id = ?
                        """,
                UUID.randomUUID(),
                fixture.batchId()
        );

        ExportPreviewDetail verified = previewService.verifyBatch(
                PROJECT_ID,
                fixture.batchId(),
                new ExportBatchVerificationRequest(
                        VERIFIER_ID,
                        "Planner verified artifact",
                        Map.of(
                                "verifiedByUserId", "caller-forged",
                                "microsoftProjectOpen", Map.of("openedByUserId", "caller-forged")
                        )
                )
        );
        assertThat(verified.batch().verifiedAt()).isNotNull();
        assertThat(verified.batch().verifiedByUserId()).isEqualTo(VERIFIER_ID);
        assertThat(section(verified.batch(), "verification", "verifiedByUserId"))
                .isEqualTo(VERIFIER_ID.toString());
        assertThat(nestedMap(sectionMap(verified.batch(), "verification"), "clientMetadata"))
                .containsEntry("verifiedByUserId", "caller-forged")
                .containsEntry("microsoftProjectOpen", Map.of("openedByUserId", "caller-forged"));
        assertThat(sectionMap(verified.batch(), "generation")).isEqualTo(generationSection);
        assertThat(sectionMap(verified.batch(), "microsoftProjectOpen"))
                .isEqualTo(sectionMap(opened.batch(), "microsoftProjectOpen"));

        assertDataFailure("UPDATE export_batches SET verified_at = verified_at + interval '1 second' WHERE id = ?", fixture.batchId());
        assertDataFailure("UPDATE export_batches SET verified_by_user_id = ? WHERE id = ?", UUID.randomUUID(), fixture.batchId());
        assertDataFailure("UPDATE export_batches SET failure_reason = 'terminal rewrite' WHERE id = ?", fixture.batchId());
        assertDataFailure("UPDATE export_batches SET metadata = '{}'::jsonb WHERE id = ?", fixture.batchId());
        assertDataFailure("""
                UPDATE export_batches
                SET status = 'failed', failure_reason = 'terminal transition'
                WHERE id = ?
                """, fixture.batchId());
    }

    @Test
    void candidateApprovalAuditsUseStateSpecificImmutableEventTypes() {
        ExportCandidateRecord candidate = createCandidate();

        candidateService.recordApprovalEvent(PROJECT_ID, candidate.id(), approvalRequest(ApprovalState.APPROVED_FOR_EXPORT));
        candidateService.recordApprovalEvent(PROJECT_ID, candidate.id(), approvalRequest(ApprovalState.REJECTED));
        candidateService.recordApprovalEvent(PROJECT_ID, candidate.id(), approvalRequest(ApprovalState.CORRECTION_REQUESTED));
        candidateService.recordApprovalEvent(PROJECT_ID, candidate.id(), approvalRequest(ApprovalState.SUPERSEDED));

        List<String> eventTypes = jdbcTemplate.queryForList(
                "SELECT event_type FROM audit_events WHERE target_entity_id = ? ORDER BY occurred_at, id",
                String.class,
                candidate.id()
        );
        assertThat(eventTypes).contains(
                AuditEventTypes.EXPORT_CANDIDATE_APPROVED_FOR_EXPORT,
                AuditEventTypes.EXPORT_CANDIDATE_REJECTED,
                AuditEventTypes.EXPORT_CANDIDATE_CORRECTION_REQUESTED,
                AuditEventTypes.EXPORT_CANDIDATE_SUPERSEDED
        );
    }

    private ApprovedFixture createApprovedFixture() {
        ExportCandidateRecord candidate = createCandidate();
        candidateService.recordApprovalEvent(PROJECT_ID, candidate.id(), approvalRequest(ApprovalState.REJECTED));
        ExportCandidateApprovalEventRecord latestApproval = candidateService.recordApprovalEvent(
                PROJECT_ID,
                candidate.id(),
                approvalRequest(ApprovalState.APPROVED_FOR_EXPORT)
        );
        ExportPreviewDetail preview = previewService.createPreview(
                PROJECT_ID,
                new ExportPreviewCreateRequest(
                        SNAPSHOT_ID,
                        List.of(candidate.id()),
                        Map.of("approval", "caller-preview-collision")
                )
        );
        assertThat(preview.lines().getFirst().sourceApprovalRecordId()).isEqualTo(latestApproval.id());
        ExportPreviewDetail approved = previewService.approveBatch(
                PROJECT_ID,
                preview.batch().id(),
                new ExportBatchDecisionRequest(
                        APPROVER_ID,
                        "Approved exact candidate",
                        Map.of("generatedAt", "caller-approval-collision")
                )
        );
        return new ApprovedFixture(candidate.id(), latestApproval.id(), approved.batch().id());
    }

    private ExportCandidateRecord createCandidate() {
        return candidateService.createCandidate(
                PROJECT_ID,
                new ExportCandidateCreateRequest(
                        SNAPSHOT_ID,
                        TASK_ID,
                        "percent_complete",
                        "25",
                        "synthetic_task_update",
                        SOURCE_ENTITY_ID,
                        "source-v1",
                        SOURCE_ACTOR_ID,
                        OffsetDateTime.parse("2026-08-09T10:00:00+08:00"),
                        "Synthetic accepted leaf progress",
                        Map.of("fixture", "real-postgres")
                )
        );
    }

    private ExportCandidateApprovalEventRequest approvalRequest(ApprovalState state) {
        return new ExportCandidateApprovalEventRequest(
                state,
                OffsetDateTime.parse("2026-08-09T10:01:00+08:00"),
                APPROVER_ID,
                OffsetDateTime.parse("2026-08-09T10:02:00+08:00"),
                "Synthetic candidate review",
                Map.of("fixture", "real-postgres")
        );
    }

    private ExportPreviewBatchRecord requiredBatch(UUID batchId) {
        return repository.findBatch(PROJECT_ID, batchId).orElseThrow();
    }

    private int auditCount(String eventType) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_events WHERE project_id = ? AND event_type = ?",
                Integer.class,
                PROJECT_ID,
                eventType
        );
    }

    private void assertDataFailure(String sql, Object... arguments) {
        assertThatThrownBy(() -> jdbcTemplate.update(sql, arguments)).isInstanceOf(DataAccessException.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sectionMap(ExportPreviewBatchRecord batch, String sectionName) {
        return (Map<String, Object>) batch.metadata().get(sectionName);
    }

    private Object section(ExportPreviewBatchRecord batch, String sectionName, String key) {
        return sectionMap(batch, sectionName).get(key);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Map<String, Object> section, String key) {
        return (Map<String, Object>) section.get(key);
    }

    private void seedAcceptedSnapshotAndLeafTask() {
        jdbcTemplate.update("INSERT INTO projects (id, name, timezone) VALUES (?, 'Integration project', 'Australia/Perth')", PROJECT_ID);
        jdbcTemplate.update("""
                INSERT INTO source_files (id, project_id, original_filename, file_kind, storage_uri)
                VALUES (?, ?, 'synthetic-integration.xml', 'mspdi_xml', 'validation://integration/source')
                """, SOURCE_FILE_ID, PROJECT_ID);
        jdbcTemplate.update("""
                INSERT INTO import_batches (id, project_id, source_file_id, status, parser_name, parser_version)
                VALUES (?, ?, ?, 'accepted', 'integration-test', '1')
                """, IMPORT_BATCH_ID, PROJECT_ID, SOURCE_FILE_ID);
        jdbcTemplate.update("""
                INSERT INTO project_snapshots (
                    id, project_id, import_batch_id, status, external_project_uid,
                    external_project_name, snapshot_version, accepted_at
                )
                VALUES (?, ?, ?, 'accepted', '9901', 'Synthetic integration schedule', 1, now())
                """, SNAPSHOT_ID, PROJECT_ID, IMPORT_BATCH_ID);
        jdbcTemplate.update("""
                INSERT INTO imported_tasks (
                    id, project_id, project_snapshot_id, external_uid, external_id,
                    name, is_summary, percent_complete, physical_percent_complete
                )
                VALUES (?, ?, ?, '401', '41', 'Synthetic integration leaf task', false, 10, 10)
                """, TASK_ID, PROJECT_ID, SNAPSHOT_ID);
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("shutdown-tracker-export-integrity-");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create integration-test storage.", exception);
        }
    }

    private static HttpServer startFailingWorker() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/worker/project-export/generate-artifact", exchange -> {
                exchange.getRequestBody().readAllBytes();
                byte[] response = "controlled worker failure".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start controlled worker HTTP server.", exception);
        }
    }

    private static final class DockerPostgres {

        private static final String USERNAME = "shutdown_tracker";
        private static final String PASSWORD = "shutdown_tracker_test";
        private static final String DATABASE = "shutdown_tracker";

        private final String containerName;
        private final int port;
        private final AtomicBoolean stopped = new AtomicBoolean();

        private DockerPostgres(String containerName, int port) {
            this.containerName = containerName;
            this.port = port;
        }

        private static DockerPostgres start() {
            String containerName = "shutdown-tracker-api-it-" + UUID.randomUUID();
            runRequired(
                    "docker", "run", "--detach", "--rm",
                    "--name", containerName,
                    "--publish", "127.0.0.1::5432",
                    "--env", "POSTGRES_DB=" + DATABASE,
                    "--env", "POSTGRES_USER=" + USERNAME,
                    "--env", "POSTGRES_PASSWORD=" + PASSWORD,
                    "postgres:16-alpine"
            );
            String portOutput = runRequired("docker", "port", containerName, "5432/tcp").trim();
            int separator = portOutput.lastIndexOf(':');
            if (separator < 0) {
                stopContainer(containerName);
                throw new IllegalStateException("Docker did not report a mapped PostgreSQL port: " + portOutput);
            }
            int port = Integer.parseInt(portOutput.substring(separator + 1).trim());
            DockerPostgres postgres = new DockerPostgres(containerName, port);
            Runtime.getRuntime().addShutdownHook(new Thread(postgres::stop, "shutdown-tracker-postgres-it-cleanup"));
            postgres.waitUntilReady();
            return postgres;
        }

        private String jdbcUrl() {
            return "jdbc:postgresql://127.0.0.1:" + port + "/" + DATABASE;
        }

        private String username() {
            return USERNAME;
        }

        private String password() {
            return PASSWORD;
        }

        private void waitUntilReady() {
            for (int attempt = 0; attempt < 120; attempt++) {
                if (run("docker", "exec", containerName, "pg_isready", "-U", USERNAME, "-d", DATABASE).exitCode() == 0) {
                    return;
                }
                try {
                    Thread.sleep(250);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    stop();
                    throw new IllegalStateException("Interrupted while waiting for PostgreSQL.", exception);
                }
            }
            stop();
            throw new IllegalStateException("PostgreSQL integration container did not become ready.");
        }

        private void stop() {
            if (stopped.compareAndSet(false, true)) {
                stopContainer(containerName);
            }
        }

        private static void stopContainer(String containerName) {
            run("docker", "rm", "--force", containerName);
        }

        private static String runRequired(String... command) {
            ProcessResult result = run(command);
            if (result.exitCode() != 0) {
                throw new IllegalStateException(
                        "Docker command failed with exit code " + result.exitCode() + ": " + result.output()
                );
            }
            return result.output();
        }

        private static ProcessResult run(String... command) {
            try {
                Process process = new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .start();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                return new ProcessResult(process.waitFor(), output);
            } catch (IOException exception) {
                throw new IllegalStateException("Docker CLI is required for real PostgreSQL integration tests.", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while running Docker CLI.", exception);
            }
        }
    }

    private record ProcessResult(int exitCode, String output) {
    }

    private record ApprovedFixture(UUID candidateId, UUID latestApprovalId, UUID batchId) {
    }
}
