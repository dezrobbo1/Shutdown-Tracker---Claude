package com.shutdowntracker.api.project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "shutdown-tracker.review-project-bootstrap", name = "enabled", havingValue = "true")
public class ReviewProjectBootstrapRunner implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReviewProjectBootstrapRunner.class);

    private final ObjectProvider<ReviewProjectBootstrapService> bootstrapServiceProvider;

    public ReviewProjectBootstrapRunner(ObjectProvider<ReviewProjectBootstrapService> bootstrapServiceProvider) {
        this.bootstrapServiceProvider = bootstrapServiceProvider;
    }

    @Override
    public void run(String... args) {
        ReviewProjectBootstrapService bootstrapService = bootstrapServiceProvider.getIfAvailable();
        if (bootstrapService == null) {
            LOGGER.warn("Review project bootstrap requested, but persistence services are not enabled.");
            return;
        }
        ProjectRecord project = bootstrapService.ensureReviewProject();
        LOGGER.info("Review project bootstrap ready: id={}, name={}", project.id(), project.name());
    }
}
