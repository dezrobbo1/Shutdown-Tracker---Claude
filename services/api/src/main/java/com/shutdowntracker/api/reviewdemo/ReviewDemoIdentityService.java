package com.shutdowntracker.api.reviewdemo;

import com.shutdowntracker.api.identity.ProjectMembershipRecord;
import com.shutdowntracker.api.identity.ProjectRole;
import com.shutdowntracker.api.identity.UserCreateRequest;
import com.shutdowntracker.api.identity.UserRecord;
import com.shutdowntracker.api.identity.UserRepository;
import com.shutdowntracker.api.identity.UserStatus;
import com.shutdowntracker.api.project.ReviewProjectBootstrapService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Seeds the one identity the console round-trip trial is driven by, and retires the rest.
 *
 * <p>Until this exists a membership can only be created by raw SQL, which means the product cannot
 * be operated at all without a database client. It is guarded, disabled by default, and creates
 * people only — never work. Seeding progress, snapshots or previews would put facts into the audit
 * trail and the export chain that nobody performed, and performing them is the entire point.
 *
 * <p><strong>One person, not five.</strong> This seeder used to create a field user, a supervisor, a
 * planner and a viewer as well, so the four-eyes journey could be walked by four people. The trial
 * is deliberately the opposite: one super user walks the whole round trip, and a console offering a
 * choice of who to be is a console that can be left acting as somebody who cannot finish the
 * journey. The two lists below are the whole difference: restoring the four-eyes journey means
 * moving those roles out of {@link #RETIRED_ROLES} and seeding them again.
 *
 * <p><strong>Retired, not deleted.</strong> Every {@code *_by_user_id} column across the schema
 * references {@code users}, so deleting a seeded identity either cascades through the audit trail or
 * fails against a foreign key. Retiring is what a real deployment does with a person who has left:
 * the membership is revoked and the account deactivated, both of which fail authorisation closed,
 * and what they did stays attributable. Only the identities this seeder itself created are touched,
 * matched on the address it gave them.
 */
@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class ReviewDemoIdentityService {

    /**
     * The one person the trial is walked as.
     *
     * <p>An administrator because {@code Capability.SUPER_USER} is {@code admin}: the trial actor
     * has to hold every step from uploading a schedule to returning a candidate, and that is the
     * role the super user rule names.
     */
    private static final SeededRole SUPER_USER = new SeededRole(ProjectRole.ADMIN, "Review Administrator");

    /**
     * Identities this seeder created before the trial narrowed to one person.
     *
     * <p>Listed so that a deployment seeded by an earlier build converges on the same state as a
     * fresh one. Without this the old accounts stay active and selectable, which is exactly the
     * confusion the trial removes — and the console kept acting as one of them across redeploys.
     */
    private static final List<ProjectRole> RETIRED_ROLES = List.of(
            ProjectRole.FIELD_USER,
            ProjectRole.SUPERVISOR,
            ProjectRole.PLANNER,
            ProjectRole.VIEWER);

    private final ReviewProjectBootstrapService reviewProjectBootstrapService;
    private final UserRepository userRepository;
    private final ReviewDemoIdentityProperties properties;

    public ReviewDemoIdentityService(
            ReviewProjectBootstrapService reviewProjectBootstrapService,
            UserRepository userRepository,
            ReviewDemoIdentityProperties properties
    ) {
        this.reviewProjectBootstrapService = reviewProjectBootstrapService;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    /**
     * Creates the super user if it is missing, retires any other identity this seeder created, and
     * returns what is left to act as.
     *
     * <p>Calls the project bootstrap directly rather than depending on runner ordering. It is
     * already idempotent, and asking for the project is more honest than assuming another runner
     * has been here first.
     */
    public List<ReviewDemoIdentity> ensureReviewIdentities() {
        UUID projectId = reviewProjectBootstrapService.ensureReviewProject().id();

        UserRecord user = ensureUser(SUPER_USER);
        ProjectMembershipRecord membership = ensureMembership(projectId, user.id(), SUPER_USER.role());
        retirePreviouslySeededIdentities(projectId);

        // The membership's role, not the one asked for. They are the same on a fresh seed, and
        // if somebody has since changed a membership by hand then what this reports had better
        // be what the server will actually enforce.
        return List.of(new ReviewDemoIdentity(user.id(), user.displayName(), membership.role(), projectId));
    }

    private UserRecord ensureUser(SeededRole seededRole) {
        String email = emailFor(seededRole.role());
        return userRepository.findByEmail(email)
                .map(this::reactivate)
                .orElseGet(() -> userRepository.create(new UserCreateRequest(
                        email,
                        seededRole.displayName(),
                        // Explicit, and load-bearing. users.status defaults to 'invited', and
                        // authorization refuses a user who cannot act — with a message that reads
                        // like a bug rather than like a seed that forgot to activate anyone.
                        UserStatus.ACTIVE,
                        null,
                        marker())));
    }

    /**
     * Undoes a retirement of the super user's own account.
     *
     * <p>Only reachable if the roles above are edited and edited back, but a seeder whose answer to
     * "the trial actor is deactivated" is to hand it back anyway would be the worst of both: the
     * console would be built against an identity every write refuses.
     */
    private UserRecord reactivate(UserRecord user) {
        if (user.status().canAct()) {
            return user;
        }
        userRepository.updateStatus(user.id(), UserStatus.ACTIVE);
        return new UserRecord(user.id(), user.email(), user.displayName(), UserStatus.ACTIVE, user.externalSubject());
    }

    private ProjectMembershipRecord ensureMembership(UUID projectId, UUID userId, ProjectRole role) {
        return userRepository.findActiveMembership(projectId, userId)
                .orElseGet(() -> userRepository.grantMembership(
                        projectId,
                        userId,
                        role,
                        // Nobody granted it. A null with a marker saying where it came from is
                        // truer than attributing it to whichever user happened to exist first.
                        null,
                        marker()));
    }

    private void retirePreviouslySeededIdentities(UUID projectId) {
        for (ProjectRole role : RETIRED_ROLES) {
            Optional<UserRecord> seeded = userRepository.findByEmail(emailFor(role));
            if (seeded.isEmpty()) {
                continue;
            }
            UserRecord user = seeded.get();
            // Both, and in this order. The membership is what authorisation reads first, and a
            // deactivated account with a live membership still shows up wherever memberships are
            // listed — as somebody who looks entitled and is refused.
            userRepository.findActiveMembership(projectId, user.id())
                    // Nobody revoked it, for the reason the grant above gives.
                    .ifPresent(membership -> userRepository.revokeMembership(membership.id(), null));
            if (user.status().canAct()) {
                userRepository.updateStatus(user.id(), UserStatus.DEACTIVATED);
            }
        }
    }

    private String emailFor(ProjectRole role) {
        return role.databaseValue() + "@" + properties.emailDomain();
    }

    private Map<String, Object> marker() {
        return Map.of(
                "synthetic", true,
                "allowed_use", "review_demo_only",
                "demo_dataset_id", properties.datasetId(),
                "source", "review_demo_identity_seeder");
    }

    private record SeededRole(ProjectRole role, String displayName) {
    }
}
