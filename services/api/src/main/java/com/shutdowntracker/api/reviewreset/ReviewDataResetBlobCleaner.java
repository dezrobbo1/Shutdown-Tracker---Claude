package com.shutdowntracker.api.reviewreset;

import com.shutdowntracker.api.candidate.storage.CandidateScheduleStorageProperties;
import com.shutdowntracker.api.exportpreview.storage.ExportArtifactStorageProperties;
import com.shutdowntracker.api.operations.storage.EvidenceStorageProperties;
import com.shutdowntracker.api.sourcefile.storage.SourceFileStorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Clears the uploaded and generated files that the wiped rows pointed at.
 *
 * <p><strong>Runs after the database commits, deliberately.</strong> Files first would mean a failed
 * transaction leaves rows referring to bytes that are gone — an actively broken state that reads as
 * corruption. Database first leaves files nothing refers to, which is inert: every stored path lives
 * in {@code source_files.storage_uri}, {@code evidence.storage_uri} or
 * {@code candidate_schedule_runs.candidate_storage_uri}, and all three tables have just been
 * emptied. Orphaned bytes waste disk and nothing else.
 *
 * <p><strong>The project worker writes to two of these roots too</strong> — it shares the source
 * file and export artifact directories with the API through the same environment variables. The
 * database lock timeout does not reach the filesystem, so a parse or an artifact generation running
 * at this moment can still be writing into a directory being cleared. That is reported rather than
 * prevented: the failure is visible in the result, and the fix is not to press this mid-import.
 *
 * <p>Roots come from the configured properties beans rather than literals, so a deployment that
 * moves its storage does not quietly keep clearing the old location.
 */
@Component
public class ReviewDataResetBlobCleaner {

    private final Map<String, Path> roots;

    public ReviewDataResetBlobCleaner(
            SourceFileStorageProperties sourceFiles,
            ExportArtifactStorageProperties exportArtifacts,
            EvidenceStorageProperties evidence,
            CandidateScheduleStorageProperties candidateSchedules
    ) {
        Map<String, Path> configured = new LinkedHashMap<>();
        configured.put("source-files", sourceFiles.localRoot());
        configured.put("export-artifacts", exportArtifacts.localRoot());
        configured.put("evidence", evidence.localRoot());
        configured.put("candidate-schedules", candidateSchedules.localRoot());
        this.roots = Map.copyOf(configured);
    }

    public List<ReviewDataResetResult.BlobReset> clear() {
        List<ReviewDataResetResult.BlobReset> results = new ArrayList<>();
        for (Map.Entry<String, Path> entry : roots.entrySet()) {
            results.add(clearRoot(entry.getValue()));
        }
        return List.copyOf(results);
    }

    private ReviewDataResetResult.BlobReset clearRoot(Path configuredRoot) {
        Path root = configuredRoot.toAbsolutePath().normalize();
        String label = root.toString();

        String refusal = refuseUnsafe(root);
        if (refusal != null) {
            return new ReviewDataResetResult.BlobReset(label, 0, 0, refusal);
        }
        if (!Files.isDirectory(root)) {
            // Nothing has been stored yet. Not a failure, and not something to create here.
            return new ReviewDataResetResult.BlobReset(label, 0, 0, null);
        }

        long files = 0;
        long bytes = 0;
        try (Stream<Path> children = Files.list(root)) {
            for (Path child : children.toList()) {
                Deleted deleted = deleteRecursively(child);
                files += deleted.files();
                bytes += deleted.bytes();
            }
        } catch (IOException e) {
            return new ReviewDataResetResult.BlobReset(label, files, bytes, e.getMessage());
        }
        return new ReviewDataResetResult.BlobReset(label, files, bytes, null);
    }

    /**
     * Rails against a misconfigured root taking the machine with it.
     *
     * <p>A blank or shallow {@code local-root} is the realistic accident — the default is a relative
     * path, so a deployment that sets the environment variable to something odd could resolve to
     * {@code /} or a home directory. None of these should ever fire; each one firing is cheaper than
     * the alternative.
     */
    private static String refuseUnsafe(Path root) {
        if (root.getNameCount() < 2) {
            return "Refusing to clear " + root + ": too close to the filesystem root.";
        }
        if (root.getParent() == null) {
            return "Refusing to clear the filesystem root.";
        }
        Path home = Path.of(System.getProperty("user.home", "/nonexistent")).toAbsolutePath().normalize();
        if (root.equals(home)) {
            return "Refusing to clear the home directory.";
        }
        return null;
    }

    /** Deletes a directory tree depth-first, keeping a count of what went. */
    private static Deleted deleteRecursively(Path path) throws IOException {
        long files = 0;
        long bytes = 0;
        try (Stream<Path> walk = Files.walk(path)) {
            List<Path> deepestFirst = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path target : deepestFirst) {
                if (Files.isRegularFile(target)) {
                    files += 1;
                    bytes += Files.size(target);
                }
                Files.deleteIfExists(target);
            }
        }
        return new Deleted(files, bytes);
    }

    private record Deleted(long files, long bytes) {
    }
}
