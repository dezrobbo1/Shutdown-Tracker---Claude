package com.shutdowntracker.api.candidate.storage;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shutdown-tracker.candidate-schedule-storage")
public record CandidateScheduleStorageProperties(Path localRoot, long maxSizeBytes) {

    /**
     * A Project schedule saved as MSPDI/XML is large and verbose: the same plan is several times
     * the size of the {@code .mpp} it came from. This is generous on purpose, and is still a limit
     * rather than an invitation.
     */
    private static final long DEFAULT_MAX_SIZE_BYTES = 209_715_200L;

    public CandidateScheduleStorageProperties {
        if (localRoot == null) {
            localRoot = Path.of(".shutdown-tracker", "candidate-schedules");
        }
        if (maxSizeBytes <= 0) {
            maxSizeBytes = DEFAULT_MAX_SIZE_BYTES;
        }
    }
}
