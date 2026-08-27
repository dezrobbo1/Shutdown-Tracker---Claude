package com.shutdowntracker.api.reviewreset;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Whether this deployment can have its review data cleared.
 *
 * <p>A flag of its own rather than reusing the review-identity flag. Listing which synthetic people
 * exist and destroying a trial in progress are not the same power, and one switch controlling both
 * would mean turning on the harmless one to get the diagnostic and silently arming the other.
 */
@ConfigurationProperties(prefix = "shutdown-tracker.review-data-reset")
public record ReviewDataResetProperties(boolean enabled) {
}
