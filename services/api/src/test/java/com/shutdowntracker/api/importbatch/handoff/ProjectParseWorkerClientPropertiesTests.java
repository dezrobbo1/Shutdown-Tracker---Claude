package com.shutdowntracker.api.importbatch.handoff;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ProjectParseWorkerClientPropertiesTests {

    @Test
    void appliesBoundedDefaultsAndNormalizesBlankSecret() {
        ProjectParseWorkerClientProperties properties =
                new ProjectParseWorkerClientProperties(null, null, null, null, null, null);

        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofMinutes(2));
        assertThat(properties.sharedSecret()).isNull();
    }

    @Test
    void keepsPositiveConfiguredTimeouts() {
        ProjectParseWorkerClientProperties properties = new ProjectParseWorkerClientProperties(
                null,
                null,
                null,
                null,
                Duration.ofSeconds(3),
                Duration.ofSeconds(45)
        );
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(45));
    }

    @Test
    void rejectsUnboundedTimeoutsByFallingBackToDefaults() {
        ProjectParseWorkerClientProperties properties = new ProjectParseWorkerClientProperties(
                null,
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
        assertThat(new ProjectParseWorkerClientProperties(null, null, null, "  ", null, null).sharedSecret()).isNull();
    }
}
