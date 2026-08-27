package com.shutdowntracker.api.reviewreset;

import static org.assertj.core.api.Assertions.assertThat;

import com.shutdowntracker.api.identity.ProjectAuthorizationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * That a deployment which did not ask for a reset button cannot be reset.
 *
 * <p>The claim is "in a real deployment this URL is a 404". Testing only the happy path would leave
 * that resting on somebody reading the annotation correctly, which is the kind of thing that quietly
 * stops being true during a refactor. For a route that empties a database, that is not good enough.
 */
class ReviewDataResetWiringTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void registersNothingByDefault() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(ReviewDataResetController.class));
    }

    @Test
    void registersNothingWhenOnlyTheResetFlagIsSet() {
        contextRunner
                .withPropertyValues("shutdown-tracker.review-data-reset.enabled=true")
                .run(context -> assertThat(context)
                        .describedAs("without persistence there is nothing to reset")
                        .doesNotHaveBean(ReviewDataResetController.class));
    }

    @Test
    void registersNothingWhenOnlyPersistenceIsEnabled() {
        contextRunner
                .withPropertyValues("shutdown-tracker.persistence.enabled=true")
                .run(context -> assertThat(context)
                        .describedAs("a real deployment enables persistence and must still 404 here")
                        .doesNotHaveBean(ReviewDataResetController.class));
    }

    @Test
    void registersTheControllerOnlyWhenBothAreSet() {
        contextRunner
                .withPropertyValues(
                        "shutdown-tracker.review-data-reset.enabled=true",
                        "shutdown-tracker.persistence.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(ReviewDataResetController.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ReviewDataResetProperties.class)
    @Import(ReviewDataResetController.class)
    static class TestConfiguration {

        @Bean
        ProjectAuthorizationService projectAuthorizationService() {
            return Mockito.mock(ProjectAuthorizationService.class);
        }

        @Bean
        ReviewResetProjectGuard reviewResetProjectGuard() {
            return Mockito.mock(ReviewResetProjectGuard.class);
        }

        @Bean
        ReviewDataResetService reviewDataResetService() {
            return Mockito.mock(ReviewDataResetService.class);
        }

        @Bean
        ReviewDataResetBlobCleaner reviewDataResetBlobCleaner() {
            return Mockito.mock(ReviewDataResetBlobCleaner.class);
        }
    }
}
