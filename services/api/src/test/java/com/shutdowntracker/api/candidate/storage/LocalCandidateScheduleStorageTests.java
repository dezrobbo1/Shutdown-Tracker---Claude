package com.shutdowntracker.api.candidate.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The candidate store keeps its own root, and keeps to it.
 *
 * <p>It shares its byte handling with the evidence store, so these are not a second copy of those
 * assertions: they prove the sharing did not lose the confinement, and that a candidate cannot be
 * written into or read out of somebody else's directory.
 */
class LocalCandidateScheduleStorageTests {

    @TempDir
    private Path tempDir;

    @TempDir
    private Path outsideRoot;

    @Test
    void storesACandidateInsideTheConfiguredRootAndReadsItBack() throws Exception {
        byte[] content = "<Project><Name>Recalculated</Name></Project>".getBytes(StandardCharsets.UTF_8);
        CandidateScheduleStorage storage = storage();

        StoredCandidateSchedule stored = storage.store(new CandidateScheduleStorageRequest(
                "kiln-shutdown-candidate.xml", new ByteArrayInputStream(content), content.length));

        assertThat(Path.of(URI.create(stored.storageUri()))).startsWith(tempDir.toAbsolutePath());
        assertThat(stored.storedFilename()).isEqualTo("kiln-shutdown-candidate.xml");
        assertThat(stored.sizeBytes()).isEqualTo(content.length);
        // The hash is a fact about what reached the disk, and is what a planner decision is
        // later bound to.
        assertThat(stored.contentHashSha256()).hasSize(64);

        try (InputStream read = storage.read(stored.storageUri())) {
            assertThat(read.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void sanitizesAPathLikeFilenameRatherThanFollowingIt() throws Exception {
        byte[] content = "<Project/>".getBytes(StandardCharsets.UTF_8);

        StoredCandidateSchedule stored = storage().store(new CandidateScheduleStorageRequest(
                "..\\real-site\\master plan.xml", new ByteArrayInputStream(content), content.length));

        assertThat(Path.of(URI.create(stored.storageUri()))).startsWith(tempDir.toAbsolutePath());
        assertThat(stored.storedFilename()).isEqualTo("master_plan.xml");
    }

    /**
     * The URI handed to {@code read} comes from a database column. A store that fetches whatever
     * it is given turns a candidate run row into a way to read any file the process can reach.
     */
    @Test
    void refusesToReadAFileOutsideTheConfiguredRoot() throws Exception {
        Path outside = outsideRoot.resolve("accepted-master.xml");
        Files.writeString(outside, "the master schedule");

        assertThatThrownBy(() -> storage().read(outside.toUri().toString()))
                .hasMessageContaining("Candidate schedule storage location is outside the configured local root");
    }

    @Test
    void leavesNoPartialFileWhenTheUploadWasShorterThanItClaimed() {
        assertThatThrownBy(() -> storage().store(new CandidateScheduleStorageRequest(
                "truncated.xml", new ByteArrayInputStream("<Proj".getBytes(StandardCharsets.UTF_8)), 4096)))
                .hasMessage("Stored byte count did not match the returned candidate schedule size.");

        assertThat(tempDir).isEmptyDirectory();
    }

    private CandidateScheduleStorage storage() {
        return new LocalCandidateScheduleStorage(
                new CandidateScheduleStorageProperties(tempDir, 1_048_576L));
    }
}
