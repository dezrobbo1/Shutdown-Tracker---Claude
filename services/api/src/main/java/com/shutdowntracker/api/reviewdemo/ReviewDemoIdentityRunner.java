package com.shutdowntracker.api.reviewdemo;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "shutdown-tracker.review-demo-identities", name = "enabled", havingValue = "true")
public class ReviewDemoIdentityRunner implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReviewDemoIdentityRunner.class);

    private final ObjectProvider<ReviewDemoIdentityService> identityServiceProvider;

    public ReviewDemoIdentityRunner(ObjectProvider<ReviewDemoIdentityService> identityServiceProvider) {
        this.identityServiceProvider = identityServiceProvider;
    }

    @Override
    public void run(String... args) {
        ReviewDemoIdentityService identityService = identityServiceProvider.getIfAvailable();
        if (identityService == null) {
            LOGGER.warn("Review demo identities requested, but persistence services are not enabled.");
            return;
        }
        List<ReviewDemoIdentity> identities = identityService.ensureReviewIdentities();
        // Logged individually and with the ids: an operator who needs to act as one of these
        // people over curl, or bake one into a build, should not need a database client to
        // find out who exists.
        identities.forEach(identity -> LOGGER.info(
                "Review demo identity ready: id={}, name={}, role={}, project={}",
                identity.id(),
                identity.displayName(),
                identity.role().databaseValue(),
                identity.projectId()));
    }
}
