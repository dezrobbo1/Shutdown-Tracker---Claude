package com.shutdowntracker.api.config;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the profile configuration files against defects that only surface at startup.
 *
 * <p>A duplicate top-level key previously made the {@code local} profile unbootable:
 * Spring Boot loads YAML with duplicate keys rejected, so the whole application failed
 * with {@code DuplicateKeyException} before anything else ran. Nothing caught it, because
 * the test suite only ever activates the {@code review} and {@code test} profiles. These
 * tests load each file the same way Spring Boot does, so a repeat is caught in CI.
 */
class ApplicationYamlTests {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @ParameterizedTest
    @ValueSource(strings = {
            "application.yml",
            "application-local.yml",
            "application-review.yml"
    })
    void configurationFileLoadsWithoutDuplicateKeys(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource(filename);
        if (!resource.exists()) {
            return;
        }

        List<PropertySource<?>> sources = loader.load(filename, resource);

        assertThat(sources)
                .describedAs("%s must parse; duplicate top-level keys fail the whole application at startup", filename)
                .isNotEmpty();
    }

    @Test
    void localProfileEnablesPersistenceAndTheTrustedHeaderActor() throws IOException {
        List<PropertySource<?>> sources =
                loader.load("application-local.yml", new ClassPathResource("application-local.yml"));

        PropertySource<?> source = sources.get(0);

        // Both settings live under the same `shutdown-tracker` root. When they were split
        // across two top-level blocks the file did not merge them, it failed to load, so
        // asserting both are visible from one parse is the regression check.
        assertThat(source.getProperty("shutdown-tracker.persistence.enabled"))
                .describedAs("the local profile is the only one that turns persistence on")
                .isEqualTo(true);
        assertThat(source.getProperty("shutdown-tracker.actor.trusted-header.enabled"))
                .describedAs("both keys must survive the same parse")
                .isEqualTo(true);
    }
}
