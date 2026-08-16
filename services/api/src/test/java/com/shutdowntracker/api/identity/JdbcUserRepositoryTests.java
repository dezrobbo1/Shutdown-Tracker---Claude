package com.shutdowntracker.api.identity;

import java.util.UUID;
import com.shutdowntracker.api.support.AbstractDatabaseTest;
import com.shutdowntracker.api.support.DatabaseFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcUserRepositoryTests extends AbstractDatabaseTest {

    private JdbcUserRepository repository;
    private DatabaseFixtures fixtures;

    @BeforeEach
    void setUp() {
        repository = new JdbcUserRepository(new NamedParameterJdbcTemplate(dataSource()));
        fixtures = new DatabaseFixtures(jdbcTemplate());
    }

    @Test
    void createsAUserAsInvitedByDefault() {
        UserRecord created = repository.create(
                new UserCreateRequest("sam.okafor@example.com", "Sam Okafor", null, null));

        assertThat(created.id()).isNotNull();
        assertThat(created.status())
                .describedAs("a new account should not be able to act until activated")
                .isEqualTo(UserStatus.INVITED);
    }

    @Test
    void findsAUserByEmailRegardlessOfCase() {
        repository.create(new UserCreateRequest("Dana.Reyes@Example.com", "Dana Reyes", UserStatus.ACTIVE, null));

        assertThat(repository.findByEmail("dana.reyes@example.com"))
                .describedAs("lookup must match the case-insensitive unique index")
                .isPresent();
    }

    @Test
    void refusesADuplicateEmailInAnyCase() {
        repository.create(new UserCreateRequest("dana@example.com", "Dana", UserStatus.ACTIVE, null));

        assertThatThrownBy(() -> repository.create(
                new UserCreateRequest("DANA@EXAMPLE.COM", "Impostor", UserStatus.ACTIVE, null)))
                .describedAs("two accounts must not exist for the same address")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesTwoAccountsClaimingTheSameExternalIdentity() {
        repository.create(new UserCreateRequest("a@example.com", "A", UserStatus.ACTIVE, "idp-subject-1"));

        assertThatThrownBy(() -> repository.create(
                new UserCreateRequest("b@example.com", "B", UserStatus.ACTIVE, "idp-subject-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsManyUsersWithoutAnExternalSubject() {
        repository.create(new UserCreateRequest("a@example.com", "A", UserStatus.ACTIVE, null));
        repository.create(new UserCreateRequest("b@example.com", "B", UserStatus.ACTIVE, null));

        assertThat(jdbcTemplate().queryForObject("SELECT count(*) FROM users", Integer.class))
                .describedAs("the partial unique index must not treat nulls as duplicates")
                .isEqualTo(2);
    }

    @Test
    void grantsAndFindsProjectMembership() {
        UUID projectId = fixtures.createProject("Kiln Shutdown");
        UserRecord user = repository.create(
                new UserCreateRequest("planner@example.com", "Planner", UserStatus.ACTIVE, null));

        repository.grantMembership(projectId, user.id(), ProjectRole.PLANNER, null);

        assertThat(repository.findActiveMembership(projectId, user.id()))
                .map(ProjectMembershipRecord::role)
                .contains(ProjectRole.PLANNER);
    }

    @Test
    void rolesAreScopedPerProject() {
        UUID kiln = fixtures.createProject("Kiln");
        UUID boiler = fixtures.createProject("Boiler");
        UserRecord user = repository.create(
                new UserCreateRequest("person@example.com", "Person", UserStatus.ACTIVE, null));

        repository.grantMembership(kiln, user.id(), ProjectRole.PLANNER, null);
        repository.grantMembership(boiler, user.id(), ProjectRole.VIEWER, null);

        assertThat(repository.findActiveMembership(kiln, user.id()).orElseThrow().role())
                .isEqualTo(ProjectRole.PLANNER);
        assertThat(repository.findActiveMembership(boiler, user.id()).orElseThrow().role())
                .describedAs("being a planner on one project must not confer it on another")
                .isEqualTo(ProjectRole.VIEWER);
    }

    @Test
    void refusesTwoActiveRolesOnTheSameProject() {
        UUID projectId = fixtures.createProject("Kiln");
        UserRecord user = repository.create(
                new UserCreateRequest("person@example.com", "Person", UserStatus.ACTIVE, null));
        repository.grantMembership(projectId, user.id(), ProjectRole.SUPERVISOR, null);

        assertThatThrownBy(() -> repository.grantMembership(projectId, user.id(), ProjectRole.PLANNER, null))
                .describedAs("a second active role must be a deliberate policy decision")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesMembershipForAUserThatDoesNotExist() {
        UUID projectId = fixtures.createProject("Kiln");

        assertThatThrownBy(() ->
                repository.grantMembership(projectId, UUID.randomUUID(), ProjectRole.PLANNER, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void storesEveryRoleInTheProductRoleSet() {
        UUID projectId = fixtures.createProject("All Roles");
        for (ProjectRole role : ProjectRole.values()) {
            UserRecord user = repository.create(new UserCreateRequest(
                    role.databaseValue() + "@example.com", role.name(), UserStatus.ACTIVE, null));
            repository.grantMembership(projectId, user.id(), role, null);
        }

        assertThat(jdbcTemplate().queryForObject(
                "SELECT count(*) FROM project_memberships WHERE active", Integer.class))
                .describedAs("every documented role must be a valid project_role value")
                .isEqualTo(ProjectRole.values().length);
    }
}
