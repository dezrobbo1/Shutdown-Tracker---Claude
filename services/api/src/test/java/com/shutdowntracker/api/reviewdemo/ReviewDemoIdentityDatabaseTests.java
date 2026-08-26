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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * The seeder against a real database, asserted on what it makes possible rather than on how.
 *
 * <p>The outcome that matters is not that a row appeared. It is that the one person left can take
 * every step of the round trip, that the people an earlier build seeded are refused by the server
 * and not merely hidden by the console, and that nobody else gained anything — which is what
 * somebody walking the trial will actually run into.
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
    void seedingTwiceLeavesOneUserAndOneMembership() {
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
    void letsTheSuperUserTakeEveryStepOfTheRoundTrip() {
        List<ReviewDemoIdentity> identities = service.ensureReviewIdentities();
        UUID projectId = identities.get(0).projectId();
        Actor superUser = actorFor(identities, ProjectRole.ADMIN);

        // The whole point of the trial: one person, every step, refused nowhere. A gap here is not
        // a permissions detail — it is a round trip that stops halfway with a 403.
        for (Capability capability : Capability.values()) {
            assertThat(authorization.requireCapability(projectId, superUser, capability))
                    .describedAs("the super user may %s", capability)
                    .isEqualTo(ProjectRole.ADMIN);
        }
    }

    /**
     * The identities an earlier build seeded are retired, and retired by the server rather than by
     * the interface hiding them.
     *
     * <p>This is the half that matters. A console that stops offering somebody is a console; a
     * membership that no longer authorises anything is the product actually refusing them.
     */
    @Test
    void refusesEveryIdentityAnEarlierBuildSeeded() {
        UUID projectId = service.ensureReviewIdentities().get(0).projectId();
        List<Actor> retired = seedAsAnEarlierBuild(projectId);

        service.ensureReviewIdentities();

        for (Actor actor : retired) {
            assertThatThrownBy(() ->
                    authorization.requireCapability(projectId, actor, Capability.VIEW_PROJECT))
                    .describedAs("%s was retired and must be refused even a read", actor.displayName())
                    .isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() ->
                    authorization.requireCapability(projectId, actor, Capability.SUBMIT_TASK_PROGRESS))
                    .isInstanceOf(ResponseStatusException.class);
        }

        assertThat(identityRepository.findSeeded("synthetic-review-identities"))
                .describedAs("and there is one person left to act as")
                .hasSize(1);
    }

    /**
     * The super user rule grants everything to one role, not to everybody.
     *
     * <p>Worth asserting against a real membership: {@code Capability.allows} is a pure function
     * anybody can read, but the thing that would actually be dangerous is a widening that only
     * shows up once a role is resolved from the database.
     */
    @Test
    void widensNothingForAnyRoleButTheSuperUser() {
        UUID projectId = service.ensureReviewIdentities().get(0).projectId();
        UserRecord viewer = userRepository.create(new UserCreateRequest(
                "real.viewer@example.com", "Real Viewer", UserStatus.ACTIVE, null));
        userRepository.grantMembership(projectId, viewer.id(), ProjectRole.VIEWER, null);
        Actor actor = new Actor(viewer.id(), ProjectRole.VIEWER.databaseValue(), viewer.displayName());

        assertThat(authorization.requireCapability(projectId, actor, Capability.VIEW_PROJECT))
                .isEqualTo(ProjectRole.VIEWER);
        assertThatThrownBy(() ->
                authorization.requireCapability(projectId, actor, Capability.SUBMIT_TASK_PROGRESS))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() ->
                authorization.requireCapability(projectId, actor, Capability.CREATE_EXPORT_PREVIEW))
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

    /**
     * The four identities the seeder created before the trial narrowed to one person, written
     * straight to the database the way an earlier build left them: active, and holding a
     * membership.
     */
    private List<Actor> seedAsAnEarlierBuild(UUID projectId) {
        List<Actor> actors = new ArrayList<>();
        for (ProjectRole role : List.of(
                ProjectRole.FIELD_USER, ProjectRole.SUPERVISOR, ProjectRole.PLANNER, ProjectRole.VIEWER)) {
            UserRecord user = userRepository.create(new UserCreateRequest(
                    role.databaseValue() + "@review.invalid",
                    "Review " + role.databaseValue(),
                    UserStatus.ACTIVE,
                    null,
                    Map.of("synthetic", true, "demo_dataset_id", "synthetic-review-identities")));
            userRepository.grantMembership(projectId, user.id(), role, null, Map.of("synthetic", true));
            actors.add(new Actor(user.id(), role.databaseValue(), user.displayName()));
        }
        return List.copyOf(actors);
    }

    private static Actor actorFor(List<ReviewDemoIdentity> identities, ProjectRole role) {
        ReviewDemoIdentity identity = identities.stream()
                .filter(candidate -> candidate.role() == role)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no seeded identity for " + role));
        return new Actor(identity.id(), role.databaseValue(), identity.displayName());
    }
}
