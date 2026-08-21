package com.shutdowntracker.api.reviewdemo;

import com.shutdowntracker.api.identity.ProjectMembershipRecord;
import com.shutdowntracker.api.identity.ProjectRole;
import com.shutdowntracker.api.identity.UserCreateRequest;
import com.shutdowntracker.api.identity.UserRecord;
import com.shutdowntracker.api.identity.UserRepository;
import com.shutdowntracker.api.identity.UserStatus;
import com.shutdowntracker.api.project.ReviewProjectBootstrapService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Seeds one synthetic identity per journey role, so the review chain can be walked by a person.
 *
 * <p>Until this exists a membership can only be created by raw SQL, which means the product cannot
 * be operated at all without a database client. It is guarded, disabled by default, and creates
 * people only — never work. Seeding progress, snapshots or previews would put facts into the audit
 * trail and the export chain that nobody performed, and performing them is the entire point.
 *
 * <p><strong>Three roles are a complete cover.</strong> Every capability in {@code Capability} is
 * held by at least one of planner, supervisor and field user; none is exclusive to admin,
 * coordinator, shutdown control, contractor or inspector. A viewer is seeded as well, because it is
 * the only way to see what a role that can do almost nothing is shown.
 *
 * <p><strong>Field user and supervisor are separate people on purpose.</strong> A supervisor already
 * holds {@code SUBMIT_TASK_PROGRESS}, so one account could both submit and review. The two-step
 * review exists so that two people make the two decisions, and a shared account would quietly defeat
 * the rule this seeder is meant to let somebody exercise.
 *
 * <p><strong>There is no reset.</strong> Every {@code *_by_user_id} column across the schema
 * references {@code users}, so deleting a seeded identity either cascades through the audit trail or
 * fails against a foreign key. Reruns reuse what is already there instead.
 */
@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class ReviewDemoIdentityService {

    /**
     * The people needed to walk from a field update to a returned candidate schedule, in the order
     * the journey uses them.
     */
    private static final List<SeededRole> SEEDED_ROLES = List.of(
            new SeededRole(ProjectRole.FIELD_USER, "Review Field User"),
            new SeededRole(ProjectRole.SUPERVISOR, "Review Supervisor"),
            new SeededRole(ProjectRole.PLANNER, "Review Planner"),
            new SeededRole(ProjectRole.VIEWER, "Review Viewer"));

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
     * Creates any missing review identities and returns all of them.
     *
     * <p>Calls the project bootstrap directly rather than depending on runner ordering. It is
     * already idempotent, and asking for the project is more honest than assuming another runner
     * has been here first.
     */
    public List<ReviewDemoIdentity> ensureReviewIdentities() {
        UUID projectId = reviewProjectBootstrapService.ensureReviewProject().id();

        List<ReviewDemoIdentity> identities = new ArrayList<>();
        for (SeededRole seededRole : SEEDED_ROLES) {
            UserRecord user = ensureUser(seededRole);
            ProjectMembershipRecord membership = ensureMembership(projectId, user.id(), seededRole.role());
            // The membership's role, not the one asked for. They are the same on a fresh seed, and
            // if somebody has since changed a membership by hand then what this reports had better
            // be what the server will actually enforce.
            identities.add(new ReviewDemoIdentity(user.id(), user.displayName(), membership.role(), projectId));
        }
        return List.copyOf(identities);
    }

    private UserRecord ensureUser(SeededRole seededRole) {
        String email = seededRole.role().databaseValue() + "@" + properties.emailDomain();
        return userRepository.findByEmail(email)
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
