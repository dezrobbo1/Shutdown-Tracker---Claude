package com.shutdowntracker.api.exportpreview.handoff;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ProjectExportWorkerClientPropertiesTests {

    @Test
    void appliesBoundedTimeoutDefaultsWhenUnset() {
        ProjectExportWorkerClientProperties properties =
                new ProjectExportWorkerClientProperties(null, null, null, null, null);

        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void keepsConfiguredTimeouts() {
        ProjectExportWorkerClientProperties properties = new ProjectExportWorkerClientProperties(
                null,
                null,
                null,
                Duration.ofSeconds(3),
                Duration.ofSeconds(90)
        );

        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(90));
    }

    @Test
    void rejectsUnboundedTimeoutsByFallingBackToDefaults() {
        ProjectExportWorkerClientProperties properties = new ProjectExportWorkerClientProperties(
                null,
                null,
                null,
                Duration.ZERO,
                Duration.ofSeconds(-1)
        );

        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void treatsBlankSharedSecretAsMissing() {
        assertThat(new ProjectExportWorkerClientProperties(null, null, "  ", null, null).sharedSecret()).isNull();
    }
}
