package com.shutdowntracker.api.reviewdemo;

import java.util.List;

public interface ReviewDemoIdentityRepository {

    /**
     * The active seeded identities for one dataset.
     *
     * <p>Deliberately not on {@code UserRepository}: that interface is the production identity
     * contract, and a query that only makes sense for review data does not belong on it.
     */
    List<ReviewDemoIdentity> findSeeded(String datasetId);
}
