package com.shutdowntracker.api.reviewdemo;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lists the identities the review seeder created, so a person can choose which one to be.
 *
 * <p><strong>Why this does not breach the standing non-goal.</strong> The active goal forbids user
 * and membership management over HTTP, because a membership endpoint would let anyone who can set
 * an actor header grant themselves any role. This endpoint is not that, on four counts:
 *
 * <ul>
 *   <li>It is GET only. No user, membership, role or status can be created, changed or revoked
 *       through it. Creation stays inside a flag-guarded runner on the server.</li>
 *   <li>It moves no authority. Anyone who can set the actor header can already claim any user id,
 *       including one they guessed. This only says which synthetic ids exist.</li>
 *   <li>It returns only rows the server itself seeded, matched on a marker the seeder wrote. A real
 *       user cannot appear in the response even if the flag were enabled by mistake, because a real
 *       user carries no marker.</li>
 *   <li>The memberships it lists exist only on the synthetic review project, so a seeded id is
 *       powerless everywhere else.</li>
 * </ul>
 *
 * <p>The residual exposure is real and worth naming: with the flag on, this publishes a list of ids
 * that are valid actor headers for one synthetic project. That is the same exposure the
 * trusted-header seam already has, confined to synthetic data, and off by default.
 *
 * <p><strong>It takes no {@code Actor}, deliberately.</strong> Declaring one is what makes an
 * endpoint require an actor header, and this answers the question asked <em>before</em> an identity
 * has been chosen. Requiring one would be a chicken and egg.
 *
 * <p><strong>It is conditioned on the bean, not on a runtime check.</strong> A flag tested inside
 * the method still leaves the route mapped and is one refactor from being tested in the wrong
 * branch. Conditioning the bean means that in a real deployment the controller does not exist and
 * the URL is an ordinary 404 — there is nothing left to get wrong.
 */
@RestController
@RequestMapping("/api/review-identities")
@ConditionalOnProperty(
        name = {
                "shutdown-tracker.review-demo-identities.enabled",
                "shutdown-tracker.persistence.enabled"
        },
        havingValue = "true")
public class ReviewIdentityController {

    private final ReviewDemoIdentityRepository identityRepository;
    private final ReviewDemoIdentityProperties properties;

    public ReviewIdentityController(
            ReviewDemoIdentityRepository identityRepository,
            ReviewDemoIdentityProperties properties
    ) {
        this.identityRepository = identityRepository;
        this.properties = properties;
    }

    @GetMapping
    public List<ReviewDemoIdentity> listSeededIdentities() {
        return identityRepository.findSeeded(properties.datasetId());
    }
}
