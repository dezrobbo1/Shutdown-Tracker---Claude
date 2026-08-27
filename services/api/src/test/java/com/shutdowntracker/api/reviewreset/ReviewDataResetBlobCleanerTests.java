package com.shutdowntracker.api.reviewreset;

import static org.assertj.core.api.Assertions.assertThat;

import com.shutdowntracker.api.candidate.storage.CandidateScheduleStorageProperties;
import com.shutdowntracker.api.exportpreview.storage.ExportArtifactStorageProperties;
import com.shutdowntracker.api.operations.storage.EvidenceStorageProperties;
import com.shutdowntracker.api.sourcefile.storage.SourceFileStorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * That clearing stored files empties the directories and keeps the directories.
 */
class ReviewDataResetBlobCleanerTests {

    private ReviewDataResetBlobCleaner cleanerFor(Path root) {
        return new ReviewDataResetBlobCleaner(
                new SourceFileStorageProperties(root),
                new ExportArtifactStorageProperties(root.resolve("artifacts")),
                new EvidenceStorageProperties(root.resolve("evidence"), 1024L),
                new CandidateScheduleStorageProperties(root.resolve("candidates"), 1024L));
    }

    @Test
    @DisplayName("removes what is inside a root but not the root itself")
    void clearsChildrenAndKeepsTheDirectory(@TempDir Path temp) throws IOException {
        Path root = Files.createDirectories(temp.resolve("source-files"));
        Files.createDirectories(root.resolve("abc")).resolve("x").toFile().createNewFile();
        Files.writeString(root.resolve("abc").resolve("upload.xml"), "hello");

        List<ReviewDataResetResult.BlobReset> results = cleanerFor(root).clear();

        assertThat(root)
                .describedAs("the deployment owns this directory; recreating it would change its owner")
                .exists();
        assertThat(Files.list(root)).isEmpty();
        assertThat(results)
                .filteredOn(result -> result.root().equals(root.toAbsolutePath().normalize().toString()))
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.error()).isNull();
                    assertThat(result.filesDeleted()).isEqualTo(2);
                    assertThat(result.bytesFreed()).isEqualTo(5);
                });
    }

    @Test
    @DisplayName("a root that was never written to is not an error")
    void treatsAMissingRootAsNothingToDo(@TempDir Path temp) {
        List<ReviewDataResetResult.BlobReset> results = cleanerFor(temp.resolve("never-used")).clear();

        assertThat(results).allSatisfy(result -> assertThat(result.error()).isNull());
        assertThat(results).allSatisfy(result -> assertThat(result.filesDeleted()).isZero());
    }

    @Test
    @DisplayName("refuses a root close enough to the filesystem root to be a misconfiguration")
    void refusesAShallowRoot() {
        // The realistic accident is an environment variable set to something odd, not malice. None
        // of these should ever fire; each one firing is cheaper than the alternative.
        ReviewDataResetBlobCleaner cleaner = cleanerFor(Path.of("/"));

        assertThat(cleaner.clear())
                .filteredOn(result -> result.root().equals("/"))
                .singleElement()
                .satisfies(result -> assertThat(result.error()).contains("Refusing to clear"));
    }

    @Test
    @DisplayName("refuses the home directory")
    void refusesTheHomeDirectory() {
        Path home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        ReviewDataResetBlobCleaner cleaner = cleanerFor(home);

        assertThat(cleaner.clear())
                .filteredOn(result -> result.root().equals(home.toString()))
                .singleElement()
                .satisfies(result -> assertThat(result.error()).contains("Refusing to clear"));
    }
}
