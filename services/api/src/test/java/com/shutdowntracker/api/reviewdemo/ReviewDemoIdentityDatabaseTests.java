package com.shutdowntracker.api.reviewdemo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.identity.Capability;
import com.shutdowntracker.api.identity.JdbcUserRepository;
import com.shutdowntracker.api.identity.ProjectAuthorizationService;
import com.shutdowntracker.api.identity.ProjectRole;
import com.shutdowntracker.api.identity.UserCreateRequest;
import com.shutdowntracker.api.identity.UserRecord;
import com.shutdowntracker.api.identity.UserStatus;
import com.shutdowntracker.api.project.JdbcProjectRepository;
import com.shutdowntracker.api.project.ReviewProjectBootstrapProperties;
import com.shutdowntracker.api.project.ReviewProjectBootstrapService;
import com.shutdowntracker.api.support.AbstractDatabaseTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * The seeder against a real database, asserted on what it makes possible rather than on how.
 *
 * <p>The outcome that matters is not that four rows appeared. It is that each seeded person is
 * allowed to take exactly their own step of the journey and refused the others — which is what a
 * person walking the chain will actually run into.
 */
class ReviewDemoIdentityDatabaseTests extends AbstractDatabaseTest {

    private ReviewDemoIdentityService service;
    private JdbcUserRepository userRepository;
    private JdbcReviewDemoIdentityRepository identityRepository;
    private ProjectAuthorizationService authorization;

    @BeforeEach
    void setUp() {
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(dataSource());
        userRepository = new JdbcUserRepository(named, new ObjectMapper());
        identityRepository = new JdbcReviewDemoIdentityRepository(named);
        authorization = new ProjectAuthorizationService(userRepository);
        service = new ReviewDemoIdentityService(
                new ReviewProjectBootstrapService(
                        new JdbcProjectRepository(named),
                        new ReviewProjectBootstrapProperties(false, null, null, null)),
                userRepository,
                new ReviewDemoIdentityProperties(true, null, null));
    }

    @Test
    void seedingTwiceLeavesOneUserAndOneMembershipPerRole() {
        List<ReviewDemoIdentity> first = service.ensureReviewIdentities();

        // A second grant for the same (project, user) would violate the partial unique index on
        // active memberships, so this failing loudly is itself the assertion.
        List<ReviewDemoIdentity> second = service.ensureReviewIdentities();

        assertThat(second).containsExactlyInAnyOrderElementsOf(first);
        assertThat(identityRepository.findSeeded("synthetic-review-identities"))
                .hasSize(first.size());
    }

    @Test
    void seedsEveryIdentityActiveSoAuthorizationDoesNotRefuseThem() {
        service.ensureReviewIdentities();

        assertThat(identityRepository.findSeeded("synthetic-review-identities"))
                .allSatisfy(identity -> assertThat(userRepository.findById(identity.id()).orElseThrow())
                        .extracting(UserRecord::status)
                        .isEqualTo(UserStatus.ACTIVE));
    }

    @Test
    void letsEachSeededPersonTakeTheirOwnStepOfTheJourney() {
        List<ReviewDemoIdentity> identities = service.ensureReviewIdentities();
        UUID projectId = identities.get(0).projectId();

        assertThat(authorization.requireCapability(
                projectId, actorFor(identities, ProjectRole.FIELD_USER), Capability.SUBMIT_TASK_PROGRESS))
                .isEqualTo(ProjectRole.FIELD_USER);
        assertThat(authorization.requireCapability(
                projectId, actorFor(identities, ProjectRole.SUPERVISOR), Capability.REVIEW_TASK_PROGRESS))
                .isEqualTo(ProjectRole.SUPERVISOR);
        assertThat(authorization.requireCapability(
                projectId, actorFor(identities, ProjectRole.PLANNER), Capability.PLANNER_REVIEW_TASK_PROGRESS))
                .isEqualTo(ProjectRole.PLANNER);
        assertThat(authorization.requireCapability(
                projectId, actorFor(identities, ProjectRole.PLANNER), Capability.APPROVE_EXPORT_BATCH))
                .isEqualTo(ProjectRole.PLANNER);
        assertThat(authorization.requireCapability(
                projectId, actorFor(identities, ProjectRole.PLANNER), Capability.RETURN_CANDIDATE_SCHEDULE))
                .isEqualTo(ProjectRole.PLANNER);
    }

    @Test
    void keepsTheTwoHalvesOfTheReviewOnTwoDifferentPeople() {
        List<ReviewDemoIdentity> identities = service.ensureReviewIdentities();
        UUID projectId = identities.get(0).projectId();
        Actor planner = actorFor(identities, ProjectRole.PLANNER);
        Actor supervisor = actorFor(identities, ProjectRole.SUPERVISOR);

        assertThatThrownBy(() ->
                authorization.requireCapability(projectId, planner, Capability.SUBMIT_TASK_PROGRESS))
                .describedAs("a planner who could submit could originate the work they later approve")
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() ->
                authorization.requireCapability(projectId, supervisor, Capability.APPROVE_EXPORT_BATCH))
                .describedAs("supervisor review confirms operational validity, not export")
                .isInstanceOf(ResponseStatusException.class);
        assertThat(planner.userId())
                .describedAs("Phase 2's four-eyes rule needs these to be two people, not two roles")
                .isNotEqualTo(supervisor.userId());
    }

    @Test
    void refusesTheSeededViewerEveryWriteInTheChain() {
        List<ReviewDemoIdentity> identities = service.ensureReviewIdentities();
        UUID projectId = identities.get(0).projectId();
        Actor viewer = actorFor(identities, ProjectRole.VIEWER);

        assertThat(authorization.requireCapability(projectId, viewer, Capability.VIEW_PROJECT))
                .isEqualTo(ProjectRole.VIEWER);
        assertThatThrownBy(() ->
                authorization.requireCapability(projectId, viewer, Capability.SUBMIT_TASK_PROGRESS))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() ->
                authorization.requireCapability(projectId, viewer, Capability.CREATE_EXPORT_PREVIEW))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void doesNotListAUserThatCarriesNoSyntheticMarker() {
        service.ensureReviewIdentities();
        UUID projectId = service.ensureReviewIdentities().get(0).projectId();
        UserRecord real = userRepository.create(new UserCreateRequest(
                "real.planner@example.com", "Real Planner", UserStatus.ACTIVE, null));
        userRepository.grantMembership(projectId, real.id(), ProjectRole.PLANNER, null);

        assertThat(identityRepository.findSeeded("synthetic-review-identities"))
                .describedAs("a real user must not appear here even when the flag is on by mistake")
                .extracting(ReviewDemoIdentity::id)
                .doesNotContain(real.id());
    }

    @Test
    void doesNotListIdentitiesBelongingToAnotherDataset() {
        service.ensureReviewIdentities();

        assertThat(identityRepository.findSeeded("synthetic-review-basic")).isEmpty();
    }

    private static Actor actorFor(List<ReviewDemoIdentity> identities, ProjectRole role) {
        ReviewDemoIdentity identity = identities.stream()
                .filter(candidate -> candidate.role() == role)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no seeded identity for " + role));
        return new Actor(identity.id(), role.databaseValue(), identity.displayName());
    }
}
