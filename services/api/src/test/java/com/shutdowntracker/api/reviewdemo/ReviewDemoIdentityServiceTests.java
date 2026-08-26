package com.shutdowntracker.api.reviewdemo;

import static org.assertj.core.api.Assertions.assertThat;

import com.shutdowntracker.api.identity.ProjectMembershipRecord;
import com.shutdowntracker.api.identity.ProjectRole;
import com.shutdowntracker.api.identity.UserCreateRequest;
import com.shutdowntracker.api.identity.UserRecord;
import com.shutdowntracker.api.identity.UserRepository;
import com.shutdowntracker.api.identity.UserStatus;
import com.shutdowntracker.api.project.ProjectRecord;
import com.shutdowntracker.api.project.ProjectRepository;
import com.shutdowntracker.api.project.ReviewProjectBootstrapProperties;
import com.shutdowntracker.api.project.ReviewProjectBootstrapService;
import com.shutdowntracker.api.project.ReviewProjectCreateRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewDemoIdentityServiceTests {

    @Test
    void seedsTheSuperUserAndNobodyElse() {
        FakeUserRepository users = new FakeUserRepository();
        ReviewDemoIdentityService service = service(users);

        List<ReviewDemoIdentity> identities = service.ensureReviewIdentities();

        assertThat(identities).extracting(ReviewDemoIdentity::role)
                .describedAs("the trial is walked by one person, so there is one person to be")
                .containsExactly(ProjectRole.ADMIN);
        assertThat(users.created).extracting(UserCreateRequest::status)
                .describedAs("users.status defaults to invited, and an invited user is refused by canAct()")
                .containsOnly(UserStatus.ACTIVE);
    }

    /**
     * A deployment seeded by an earlier build already has a field user, a supervisor, a planner and
     * a viewer, all active. Leaving them is what let the console go on acting as a planner across
     * redeploys, and a console offering four people is the confusion the trial exists to remove.
     */
    @Test
    void retiresTheIdentitiesAnEarlierBuildSeeded() {
        FakeUserRepository users = new FakeUserRepository();
        ReviewDemoIdentityService service = service(users);
        UUID projectId = service.ensureReviewIdentities().get(0).projectId();
        seedAsAnEarlierBuild(users, projectId);

        service.ensureReviewIdentities();

        for (ProjectRole role : RETIRED) {
            UserRecord retired = users.findByEmail(role.databaseValue() + "@review.invalid").orElseThrow();
            assertThat(retired.status().canAct())
                    .describedAs("%s must not be able to act", role)
                    .isFalse();
            assertThat(users.findActiveMembership(projectId, retired.id()))
                    .describedAs("%s must not still look entitled on the project", role)
                    .isEmpty();
        }
    }

    @Test
    void leavesTheSuperUserActiveWhileRetiringTheRest() {
        FakeUserRepository users = new FakeUserRepository();
        ReviewDemoIdentityService service = service(users);
        UUID projectId = service.ensureReviewIdentities().get(0).projectId();
        seedAsAnEarlierBuild(users, projectId);

        List<ReviewDemoIdentity> identities = service.ensureReviewIdentities();

        assertThat(identities).singleElement()
                .extracting(ReviewDemoIdentity::role)
                .isEqualTo(ProjectRole.ADMIN);
        UUID superUserId = identities.get(0).id();
        assertThat(users.findById(superUserId).orElseThrow().status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(users.findActiveMembership(projectId, superUserId)).isPresent();
    }

    /**
     * Retirement is reversible in the schema and irreversible in fact: the audit trail references
     * these people, so deleting them would either cascade through it or fail on a foreign key.
     */
    @Test
    void retiresRatherThanDeletesSoTheAuditTrailStillNamesSomebody() {
        FakeUserRepository users = new FakeUserRepository();
        ReviewDemoIdentityService service = service(users);
        seedAsAnEarlierBuild(users, service.ensureReviewIdentities().get(0).projectId());

        service.ensureReviewIdentities();

        assertThat(users.findByEmail("planner@review.invalid"))
                .describedAs("the account is still there to attribute past work to")
                .isPresent();
    }

    @Test
    void touchesOnlyTheAccountsThisSeederCreated() {
        FakeUserRepository users = new FakeUserRepository();
        ReviewDemoIdentityService service = service(users);
        UUID projectId = service.ensureReviewIdentities().get(0).projectId();
        UserRecord realPlanner = users.create(new UserCreateRequest(
                "someone@example.com", "A Real Planner", UserStatus.ACTIVE, null, Map.of()));
        users.grantMembership(projectId, realPlanner.id(), ProjectRole.PLANNER, null, Map.of());

        service.ensureReviewIdentities();

        assertThat(users.findById(realPlanner.id()).orElseThrow().status())
                .describedAs("a real user who happens to hold a retired role is not this seeder's business")
                .isEqualTo(UserStatus.ACTIVE);
        assertThat(users.findActiveMembership(projectId, realPlanner.id())).isPresent();
    }

    @Test
    void createsNothingOnASecondRun() {
        FakeUserRepository users = new FakeUserRepository();
        ReviewDemoIdentityService service = service(users);

        List<ReviewDemoIdentity> first = service.ensureReviewIdentities();
        int createdAfterFirstRun = users.created.size();
        int grantedAfterFirstRun = users.granted.size();
        List<ReviewDemoIdentity> second = service.ensureReviewIdentities();

        assertThat(users.created).hasSize(createdAfterFirstRun);
        assertThat(users.granted).hasSize(grantedAfterFirstRun);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void marksEverySeededRowAsSyntheticAndScopedToTheDataset() {
        FakeUserRepository users = new FakeUserRepository();

        service(users).ensureReviewIdentities();

        assertThat(users.created).allSatisfy(request -> assertThat(request.metadata())
                .containsEntry("synthetic", true)
                .containsEntry("allowed_use", "review_demo_only")
                .containsEntry("demo_dataset_id", "synthetic-review-identities"));
        assertThat(users.grantedMetadata).allSatisfy(metadata -> assertThat(metadata)
                .describedAs("a reset must be able to tell seeded memberships from real ones")
                .containsEntry("synthetic", true));
    }

    @Test
    void grantsEveryMembershipOnTheReviewProjectAndNoOther() {
        FakeUserRepository users = new FakeUserRepository();

        List<ReviewDemoIdentity> identities = service(users).ensureReviewIdentities();

        assertThat(identities).extracting(ReviewDemoIdentity::projectId)
                .describedAs("a seeded identity is powerless anywhere but the synthetic project")
                .containsOnly(identities.get(0).projectId());
        assertThat(users.granted).extracting(Grant::projectId)
                .containsOnly(identities.get(0).projectId());
    }

    @Test
    void addressesSeededIdentitiesInAReservedDomain() {
        FakeUserRepository users = new FakeUserRepository();

        service(users).ensureReviewIdentities();

        assertThat(users.created).extracting(UserCreateRequest::email)
                .describedAs(".invalid is reserved by RFC 2606 and can never be delivered to")
                .allSatisfy(email -> assertThat(email).endsWith("@review.invalid"));
    }

    private static final List<ProjectRole> RETIRED = List.of(
            ProjectRole.FIELD_USER, ProjectRole.SUPERVISOR, ProjectRole.PLANNER, ProjectRole.VIEWER);

    /** The identities an earlier build seeded alongside the administrator, so retirement has work. */
    private void seedAsAnEarlierBuild(FakeUserRepository users, UUID projectId) {
        for (ProjectRole role : RETIRED) {
            UserRecord user = users.create(new UserCreateRequest(
                    role.databaseValue() + "@review.invalid",
                    "Review " + role.databaseValue(),
                    UserStatus.ACTIVE,
                    null,
                    Map.of("synthetic", true)));
            users.grantMembership(projectId, user.id(), role, null, Map.of("synthetic", true));
        }
    }

    private ReviewDemoIdentityService service(FakeUserRepository users) {
        ReviewProjectBootstrapService bootstrap = new ReviewProjectBootstrapService(
                new FakeProjectRepository(),
                new ReviewProjectBootstrapProperties(false, null, null, null));
        return new ReviewDemoIdentityService(
                bootstrap,
                users,
                new ReviewDemoIdentityProperties(true, null, null));
    }

    private record Grant(UUID projectId, UUID userId, ProjectRole role) {
    }

    private static final class FakeProjectRepository implements ProjectRepository {

        private ProjectRecord project;

        @Override
        public Optional<ProjectRecord> findReviewBootstrapProject(String projectName) {
            return Optional.ofNullable(project).filter(existing -> existing.name().equals(projectName));
        }

        @Override
        public ProjectRecord createReviewBootstrapProject(ReviewProjectCreateRequest request) {
            project = new ProjectRecord(UUID.randomUUID(), request.name(), "active", request.timezone());
            return project;
        }
    }

    private static final class FakeUserRepository implements UserRepository {

        private final Map<String, UserRecord> usersByEmail = new HashMap<>();
        private final Map<String, ProjectMembershipRecord> membershipsByKey = new HashMap<>();
        private final List<UserCreateRequest> created = new ArrayList<>();
        private final List<Grant> granted = new ArrayList<>();
        private final List<Map<String, Object>> grantedMetadata = new ArrayList<>();


        @Override
        public UserRecord create(UserCreateRequest request) {
            created.add(request);
            UserRecord record = new UserRecord(
                    UUID.randomUUID(),
                    request.email(),
                    request.displayName(),
                    request.status(),
                    request.externalSubject());
            usersByEmail.put(request.email().toLowerCase(), record);
            return record;
        }

        @Override
        public Optional<UserRecord> findById(UUID userId) {
            return usersByEmail.values().stream().filter(user -> user.id().equals(userId)).findFirst();
        }

        @Override
        public Optional<UserRecord> findByEmail(String email) {
            return Optional.ofNullable(usersByEmail.get(email.toLowerCase()));
        }

        @Override
        public ProjectMembershipRecord grantMembership(
                UUID projectId,
                UUID userId,
                ProjectRole role,
                UUID grantedByUserId,
                Map<String, Object> metadata
        ) {
            granted.add(new Grant(projectId, userId, role));
            grantedMetadata.add(metadata);
            ProjectMembershipRecord record =
                    new ProjectMembershipRecord(UUID.randomUUID(), projectId, userId, role, true);
            membershipsByKey.put(projectId + ":" + userId, record);
            return record;
        }

        @Override
        public Optional<ProjectMembershipRecord> findActiveMembership(UUID projectId, UUID userId) {
            return Optional.ofNullable(membershipsByKey.get(projectId + ":" + userId));
        }

        @Override
        public void revokeMembership(UUID membershipId, UUID revokedByUserId) {
            membershipsByKey.values().removeIf(membership -> membership.id().equals(membershipId));
        }

        @Override
        public void updateStatus(UUID userId, UserStatus status) {
            usersByEmail.replaceAll((email, user) -> user.id().equals(userId)
                    ? new UserRecord(user.id(), user.email(), user.displayName(), status, user.externalSubject())
                    : user);
        }
    }
}
