package com.shutdowntracker.api.exportpreview.handoff;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ProjectExportWorkerClientPropertiesTests {

    @Test
    void appliesBoundedDefaultsAndNormalizesBlankSecret() {
        ProjectExportWorkerClientProperties properties =
                new ProjectExportWorkerClientProperties(null, null, "  ", null, null);
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofMinutes(2));
        assertThat(properties.sharedSecret()).isNull();
    }

    @Test
    void keepsPositiveConfiguredTimeouts() {
        ProjectExportWorkerClientProperties properties = new ProjectExportWorkerClientProperties(
                null, null, "secret", Duration.ofSeconds(3), Duration.ofSeconds(90)
        );
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(90));
        assertThat(properties.sharedSecret()).isEqualTo("secret");
    }
}
