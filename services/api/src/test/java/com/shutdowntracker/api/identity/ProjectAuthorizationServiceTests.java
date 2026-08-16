package com.shutdowntracker.api.identity;

import java.util.UUID;
import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.support.AbstractDatabaseTest;
import com.shutdowntracker.api.support.DatabaseFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectAuthorizationServiceTests extends AbstractDatabaseTest {

    private ProjectAuthorizationService service;
    private JdbcUserRepository repository;
    private DatabaseFixtures fixtures;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        repository = new JdbcUserRepository(new NamedParameterJdbcTemplate(dataSource()));
        service = new ProjectAuthorizationService(repository);
        fixtures = new DatabaseFixtures(jdbcTemplate());
        projectId = fixtures.createProject("Kiln Shutdown");
    }

    @Test
    void allowsAPlannerToApproveAnExportBatch() {
        Actor planner = actorWithRole("planner@example.com", ProjectRole.PLANNER);

        ProjectRole resolved = service.requireCapability(projectId, planner, Capability.APPROVE_EXPORT_BATCH);

        assertThat(resolved).isEqualTo(ProjectRole.PLANNER);
    }

    @Test
    void refusesASupervisorApprovingAnExportBatch() {
        Actor supervisor = actorWithRole("supervisor@example.com", ProjectRole.SUPERVISOR);

        assertThatThrownBy(() -> service.requireCapability(projectId, supervisor, Capability.APPROVE_EXPORT_BATCH))
                .describedAs("supervisor review confirms operational validity, not export")
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void refusesAnAdminApprovingAnExportBatch() {
        Actor admin = actorWithRole("admin@example.com", ProjectRole.ADMIN);

        assertThatThrownBy(() -> service.requireCapability(projectId, admin, Capability.APPROVE_EXPORT_BATCH))
                .describedAs("admin administers access; export approval stays planner-owned")
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void ignoresTheRoleClaimedInTheRequest() {
        UserRecord user = repository.create(
                new UserCreateRequest("viewer@example.com", "Viewer", UserStatus.ACTIVE, null));
        repository.grantMembership(projectId, user.id(), ProjectRole.VIEWER, null);

        // The header claims planner; stored membership says viewer.
        Actor liar = new Actor(user.id(), "planner", "Viewer");

        assertThatThrownBy(() -> service.requireCapability(projectId, liar, Capability.APPROVE_EXPORT_BATCH))
                .describedAs("authority must come from stored membership, never the request")
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void refusesAUserWithNoMembershipOnTheProject() {
        UserRecord outsider = repository.create(
                new UserCreateRequest("outsider@example.com", "Outsider", UserStatus.ACTIVE, null));

        assertThatThrownBy(() -> service.requireCapability(
                projectId, new Actor(outsider.id(), null, null), Capability.VIEW_PROJECT))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void refusesASuspendedUserWhoStillHoldsAPlannerRole() {
        UserRecord user = repository.create(
                new UserCreateRequest("suspended@example.com", "Suspended", UserStatus.ACTIVE, null));
        repository.grantMembership(projectId, user.id(), ProjectRole.PLANNER, null);
        jdbcTemplate().update(
                "UPDATE users SET status = CAST('suspended' AS user_status) WHERE id = ?", user.id());

        assertThatThrownBy(() -> service.requireCapability(
                projectId, new Actor(user.id(), null, null), Capability.APPROVE_EXPORT_BATCH))
                .describedAs("suspending an account must revoke authority without unpicking role grants")
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void refusesAnActorThatIsNotAKnownUser() {
        assertThatThrownBy(() -> service.requireCapability(
                projectId, new Actor(UUID.randomUUID(), "planner", "Ghost"), Capability.VIEW_PROJECT))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void membershipOnAnotherProjectGrantsNothingHere() {
        UUID otherProject = fixtures.createProject("Boiler");
        UserRecord user = repository.create(
                new UserCreateRequest("planner@example.com", "Planner", UserStatus.ACTIVE, null));
        repository.grantMembership(otherProject, user.id(), ProjectRole.PLANNER, null);

        assertThatThrownBy(() -> service.requireCapability(
                projectId, new Actor(user.id(), null, null), Capability.APPROVE_EXPORT_BATCH))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void everyRoleMayViewTheProjectItBelongsTo() {
        for (ProjectRole role : ProjectRole.values()) {
            UUID scopedProject = fixtures.createProject("Project " + role.name());
            UserRecord user = repository.create(new UserCreateRequest(
                    role.databaseValue() + "@example.com", role.name(), UserStatus.ACTIVE, null));
            repository.grantMembership(scopedProject, user.id(), role, null);

            assertThat(service.requireCapability(
                    scopedProject, new Actor(user.id(), null, null), Capability.VIEW_PROJECT))
                    .isEqualTo(role);
        }
    }

    private Actor actorWithRole(String email, ProjectRole role) {
        UserRecord user = repository.create(new UserCreateRequest(email, role.name(), UserStatus.ACTIVE, null));
        repository.grantMembership(projectId, user.id(), role, null);
        return new Actor(user.id(), role.databaseValue(), role.name());
    }
}
