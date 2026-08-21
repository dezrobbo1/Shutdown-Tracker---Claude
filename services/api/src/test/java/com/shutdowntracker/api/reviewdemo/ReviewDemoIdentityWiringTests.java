package com.shutdowntracker.api.reviewdemo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * That the review-identity surface does not exist unless it is asked for.
 *
 * <p>The claim being defended is "a real deployment does not have this endpoint". A test that only
 * checked the happy path would leave that claim resting on reading the annotation correctly, which
 * is exactly the kind of thing that survives a refactor while quietly ceasing to be true.
 */
class ReviewDemoIdentityWiringTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void registersNothingByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(ReviewIdentityController.class);
            assertThat(context).doesNotHaveBean(ReviewDemoIdentityRunner.class);
        });
    }

    @Test
    void registersNoControllerWhenOnlyTheDemoFlagIsSet() {
        contextRunner
                .withPropertyValues("shutdown-tracker.review-demo-identities.enabled=true")
                .run(context -> assertThat(context)
                        .describedAs("without persistence there is no repository to answer from")
                        .doesNotHaveBean(ReviewIdentityController.class));
    }

    @Test
    void registersNoControllerWhenOnlyPersistenceIsEnabled() {
        contextRunner
                .withPropertyValues("shutdown-tracker.persistence.enabled=true")
                .run(context -> assertThat(context)
                        .describedAs("a real deployment enables persistence and must still 404 here")
                        .doesNotHaveBean(ReviewIdentityController.class));
    }

    @Test
    void registersTheControllerOnlyWhenBothAreSet() {
        contextRunner
                .withPropertyValues(
                        "shutdown-tracker.review-demo-identities.enabled=true",
                        "shutdown-tracker.persistence.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ReviewIdentityController.class);
                    assertThat(context).hasSingleBean(ReviewDemoIdentityRunner.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ReviewDemoIdentityProperties.class)
    @Import({ReviewIdentityController.class, ReviewDemoIdentityRunner.class})
    static class TestConfiguration {

        @Bean
        ReviewDemoIdentityRepository reviewDemoIdentityRepository() {
            return datasetId -> List.of();
        }
    }
}
