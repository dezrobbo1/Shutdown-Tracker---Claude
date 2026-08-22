package com.shutdowntracker.api.journey;

import com.shutdowntracker.projectexport.contract.ProjectExportArtifactGenerationRequest;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactGenerationResponse;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactSummary;
import com.shutdowntracker.projectimport.contract.ParsedAssignment;
import com.shutdowntracker.projectimport.contract.ParsedResource;
import com.shutdowntracker.projectimport.contract.ParsedTask;
import com.shutdowntracker.projectimport.contract.ProjectParseEntitiesResponse;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryRequest;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;

/**
 * Stands in for {@code services/project-worker} during the journey test.
 *
 * <p>The worker is a separate deployable that owns MPXJ parsing and MSPDI/XML generation. The
 * journey this class supports is the one through the <em>API's</em> controllers, so the worker is
 * the one thing replaced — at the two declared client interfaces the API already uses to reach it,
 * not by reaching around them. Everything between those two seams is the real application.
 *
 * <p>What it returns is deliberately not arbitrary. The parse response is a schedule shaped like
 * the smallest one that can be walked: a summary task that must never be exported, two leaf tasks
 * that can be, one resource, and an assignment for each leaf, so a field user linked to that
 * resource has real work and a summary task is present to be excluded. The generation response
 * echoes the ids and the reserved storage URI the API sends, because
 * {@code ExportArtifactHandoffService.verifyWorkerResponse} rejects a worker that answers about a
 * different batch — a check worth exercising rather than bypassing.
 */
final class StubProjectWorker {

    static final String SUMMARY_TASK_UID = "1";
    static final String FIRST_LEAF_TASK_UID = "2";
    static final String SECOND_LEAF_TASK_UID = "3";
    static final String CREW_RESOURCE_UID = "R1";

    static final String PROJECT_NAME = "Synthetic Shutdown";
    static final String EXTERNAL_PROJECT_UID = "synthetic-journey-project";

    private StubProjectWorker() {
    }

    /** A parsed schedule for one import batch, echoing the batch the API asked about. */
    static ProjectParseEntitiesResponse parse(ProjectParseSummaryRequest request) {
        return new ProjectParseEntitiesResponse(
                summary(request),
                EXTERNAL_PROJECT_UID,
                OffsetDateTime.of(2026, 3, 1, 6, 0, 0, 0, ZoneOffset.UTC),
                tasks(),
                List.of(new ParsedResource(CREW_RESOURCE_UID, "Mechanical Crew", "work", null)),
                List.of(
                        new ParsedAssignment("A1", FIRST_LEAF_TASK_UID, CREW_RESOURCE_UID, null),
                        new ParsedAssignment("A2", SECOND_LEAF_TASK_UID, CREW_RESOURCE_UID, null)),
                List.of());
    }

    private static ProjectParseSummaryResponse summary(ProjectParseSummaryRequest request) {
        return new ProjectParseSummaryResponse(
                request.importBatchId(),
                "stub-project-worker",
                "journey-test",
                request.originalFilename(),
                "mspdi_xml",
                PROJECT_NAME,
                3,
                1,
                2,
                1,
                2,
                1,
                0,
                0,
                0,
                List.of());
    }

    private static List<ParsedTask> tasks() {
        return List.of(
                new ParsedTask(
                        SUMMARY_TASK_UID, "1", "Kiln shutdown", "1", "1", 1,
                        true, null, null, null, null, null, null, null, null, null),
                new ParsedTask(
                        FIRST_LEAF_TASK_UID, "2", "Isolate feed pump", "1.1", "1.1", 2,
                        false, SUMMARY_TASK_UID, null, null, null, null,
                        BigDecimal.ZERO, null, null, null),
                new ParsedTask(
                        SECOND_LEAF_TASK_UID, "3", "Strip discharge valve", "1.2", "1.2", 2,
                        false, SUMMARY_TASK_UID, null, null, null, null,
                        BigDecimal.ZERO, null, null, null));
    }

    /**
     * Writes the artifact the API reserved a path for, and reports it back.
     *
     * <p>The bytes are written rather than only described, because the next steps of the journey
     * download this file and hand it back as a returned candidate. A stub that reported a file it
     * had not written would pass generation and fail the download, which is exactly the kind of
     * break between two working steps this test exists to catch.
     */
    static ProjectExportArtifactGenerationResponse generate(ProjectExportArtifactGenerationRequest request) {
        Path outputPath = Path.of(request.outputPath());
        byte[] content = artifactBytes(request);
        try {
            Files.write(outputPath, content);
        } catch (IOException exception) {
            throw new UncheckedIOException("Stub worker could not write the export artifact.", exception);
        }

        String sha256 = sha256(content);
        return new ProjectExportArtifactGenerationResponse(
                request.exportBatchId(),
                request.projectId(),
                outputPath.toUri().toString(),
                sha256,
                new ProjectExportArtifactSummary(
                        outputPath.getFileName().toString(),
                        "mspdi_xml",
                        request.artifactRequest().tasks().size(),
                        3,
                        request.artifactRequest().tasks().stream()
                                .mapToInt(task -> task.fieldValues().size())
                                .sum(),
                        content.length,
                        sha256,
                        List.of()),
                "Stub worker generated a synthetic MSPDI/XML candidate. No Microsoft Project ran.");
    }

    /**
     * A recognisable stand-in for a generated schedule, carrying the batch it belongs to so a file
     * downloaded in one step can be identified when it is handed back in another.
     */
    private static byte[] artifactBytes(ProjectExportArtifactGenerationRequest request) {
        StringBuilder xml = new StringBuilder()
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<Project><Name>")
                .append(request.artifactRequest().projectName())
                .append("</Name><Tasks>");
        for (var task : request.artifactRequest().tasks()) {
            xml.append("<Task><UID>").append(task.microsoftProjectTaskUid()).append("</UID>");
            for (var value : task.fieldValues()) {
                xml.append("<Field name=\"")
                        .append(value.field().fieldName())
                        .append("\">")
                        .append(value.newValue())
                        .append("</Field>");
            }
            xml.append("</Task>");
        }
        return xml.append("</Tasks></Project>").toString().getBytes(StandardCharsets.UTF_8);
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required.", exception);
        }
    }
}
