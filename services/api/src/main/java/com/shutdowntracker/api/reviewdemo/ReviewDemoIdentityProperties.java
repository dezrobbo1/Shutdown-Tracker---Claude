package com.shutdowntracker.api.reviewdemo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the guarded review-identity seeder.
 *
 * <p>Disabled by default, and deliberately separate from the review project bootstrap. Identities
 * are the one class of seeded row that grants something — authorization resolves a membership —
 * so having the synthetic project must not imply having people who can act on it.
 */
@ConfigurationProperties(prefix = "shutdown-tracker.review-demo-identities")
public record ReviewDemoIdentityProperties(
        boolean enabled,
        String datasetId,
        String emailDomain
) {

    public ReviewDemoIdentityProperties {
        if (datasetId == null || datasetId.isBlank()) {
            datasetId = "synthetic-review-identities";
        }
        if (emailDomain == null || emailDomain.isBlank()) {
            // RFC 2606 reserves .invalid and guarantees it will never resolve, so a seeded
            // address cannot become somewhere a message is actually delivered.
            emailDomain = "review.invalid";
        }
    }
}
