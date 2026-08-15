package com.shutdowntracker.projectworker.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers shared-secret authentication for worker handoff endpoints. */
@Configuration
public class WorkerSecurityConfiguration {

    private static final String WORKER_PATH_PATTERN = "/worker/*";

    @Bean
    public FilterRegistrationBean<WorkerAuthFilter> workerAuthFilterRegistration(WorkerAuthProperties properties) {
        FilterRegistrationBean<WorkerAuthFilter> registration = new FilterRegistrationBean<>();
        if (!properties.isEnabled()) {
            registration.setEnabled(false);
            return registration;
        }
        if (properties.sharedSecret() == null) {
            throw new IllegalStateException(
                    "shutdown-tracker.worker-auth.shared-secret must be set when worker authentication is enabled. "
                            + "Set the secret, or disable authentication only for isolated local development."
            );
        }
        registration.setFilter(new WorkerAuthFilter(properties.sharedSecret()));
        registration.addUrlPatterns(WORKER_PATH_PATTERN);
        return registration;
    }
}
