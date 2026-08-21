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
    void seedsOneActiveIdentityPerJourneyRole() {
        FakeUserRepository users = new FakeUserRepository();
        ReviewDemoIdentityService service = service(users);

        List<ReviewDemoIdentity> identities = service.ensureReviewIdentities();

        assertThat(identities).extracting(ReviewDemoIdentity::role)
                .describedAs("the journey needs a submitter, a reviewer and a planner as three people")
                .contains(ProjectRole.FIELD_USER, ProjectRole.SUPERVISOR, ProjectRole.PLANNER);
        assertThat(users.created).extracting(UserCreateRequest::status)
                .describedAs("users.status defaults to invited, and an invited user is refused by canAct()")
                .containsOnly(UserStatus.ACTIVE);
    }

    @Test
    void givesTheFieldUserAndTheSupervisorSeparateAccounts() {
        FakeUserRepository users = new FakeUserRepository();

        List<ReviewDemoIdentity> identities = service(users).ensureReviewIdentities();

        UUID fieldUserId = idOf(identities, ProjectRole.FIELD_USER);
        UUID supervisorId = idOf(identities, ProjectRole.SUPERVISOR);
        assertThat(fieldUserId)
                .describedAs("a supervisor can submit progress, so one shared account would defeat "
                        + "the two-person review the seeder exists to let somebody exercise")
                .isNotEqualTo(supervisorId);
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

    private static UUID idOf(List<ReviewDemoIdentity> identities, ProjectRole role) {
        return identities.stream()
                .filter(identity -> identity.role() == role)
                .findFirst()
                .orElseThrow()
                .id();
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
    }
}
