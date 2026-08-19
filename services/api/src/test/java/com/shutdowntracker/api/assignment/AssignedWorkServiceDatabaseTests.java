package com.shutdowntracker.api.assignment;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.audit.JdbcAuditEventRecorder;
import com.shutdowntracker.api.identity.Capability;
import com.shutdowntracker.api.identity.ProjectRole;
import com.shutdowntracker.api.importedproject.ImportedAssignmentCreateRequest;
import com.shutdowntracker.api.importedproject.ImportedProjectEntities;
import com.shutdowntracker.api.importedproject.ImportedProjectPersistenceService;
import com.shutdowntracker.api.importedproject.ImportedProjectSnapshotCreateRequest;
import com.shutdowntracker.api.importedproject.ImportedResourceCreateRequest;
import com.shutdowntracker.api.importedproject.ImportedTaskCreateRequest;
import com.shutdowntracker.api.importedproject.JdbcImportedProjectRepository;
import com.shutdowntracker.api.support.AbstractDatabaseTest;
import com.shutdowntracker.api.support.DatabaseFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Assignment-scoped work lists.
 *
 * <p>The claim under test is narrow and easy to get wrong: a link decides which work a person is
 * shown, and decides nothing else. These cover both halves — that the filtering is real, and that
 * it never becomes an authorization source.
 */
class AssignedWorkServiceDatabaseTests extends AbstractDatabaseTest {

    private AssignedWorkService service;
    private JdbcProjectResourceLinkRepository repository;
    private DatabaseFixtures fixtures;
    private ImportedProjectPersistenceService persistence;
    private NamedParameterJdbcTemplate named;

    private UUID projectId;
    private UUID importBatchId;
    private Actor planner;
    private Actor fitter;
    private Actor electrician;

    @BeforeEach
    void setUp() {
        named = new NamedParameterJdbcTemplate(dataSource());
        repository = new JdbcProjectResourceLinkRepository(named);
        service = new AssignedWorkService(repository, new JdbcAuditEventRecorder(named, new ObjectMapper()));
        fixtures = new DatabaseFixtures(jdbcTemplate());
        persistence = new ImportedProjectPersistenceService(
                new JdbcImportedProjectRepository(named, new ObjectMapper()));

        DatabaseFixtures.ImportChain chain = fixtures.createImportChain("Kiln Shutdown");
        projectId = chain.projectId();
        importBatchId = chain.importBatchId();

        planner = actor("planner@example.com", "planner");
        fitter = actor("fitter@example.com", "field_user");
        electrician = actor("sparks@example.com", "field_user");
    }

    // ---------------------------------------------------------------- filtering

    @Test
    void showsOnlyTheWorkAssignedToTheReadersResource() {
        acceptSnapshot(snapshotWithTwoCrews());
        service.link(projectId, planner, new ProjectResourceLinkCreateRequest(fitter.userId(), "R-MECH"));

        AssignedWorkView work = service.assignedWork(projectId, fitter);

        assertThat(work.linked()).isTrue();
        assertThat(work.tasks()).extracting(task -> task.name())
                .containsExactly("Remove access cover", "Replace liner plate");
    }

    @Test
    void twoPeopleOnOneScheduleSeeDifferentWork() {
        acceptSnapshot(snapshotWithTwoCrews());
        service.link(projectId, planner, new ProjectResourceLinkCreateRequest(fitter.userId(), "R-MECH"));
        service.link(projectId, planner, new ProjectResourceLinkCreateRequest(electrician.userId(), "R-ELEC"));

        assertThat(service.assignedWork(projectId, fitter).tasks())
                .extracting(task -> task.externalUid()).containsExactly("T-1", "T-2");
        // T-2 is shared: both crews are assigned to it, so both see it. Only T-1 is the fitter's
        // alone and only T-3 is the electrician's alone.
        assertThat(service.assignedWork(projectId, electrician).tasks())
                .extracting(task -> task.externalUid()).containsExactly("T-2", "T-3");
    }

    @Test
    void aTaskAssignedToTwoOfTheReadersResourcesIsListedOnce() {
        acceptSnapshot(snapshotWithTwoCrews());
        service.link(projectId, planner, new ProjectResourceLinkCreateRequest(fitter.userId(), "R-MECH"));
        service.link(projectId, planner, new ProjectResourceLinkCreateRequest(fitter.userId(), "R-ELEC"));

        // T-2 carries an assignment for both crews. A join would return it twice.
        assertThat(service.assignedWork(projectId, fitter).tasks())
                .extracting(task -> task.externalUid()).containsExactly("T-1", "T-2", "T-3");
    }

    @Test
    void aSummaryTaskIsNeverListedAsWorkEvenWhenAssigned() {
        acceptSnapshot(snapshotWithTwoCrews());
        service.link(projectId, planner, new ProjectResourceLinkCreateRequest(fitter.userId(), "R-MECH"));

        // The fixture assigns R-MECH to the summary task S-1 as well. A roll-up is not a job.
        assertThat(service.assignedWork(projectId, fitter).tasks())
                .extracting(task -> task.externalUid()).doesNotContain("S-1");
    }

    // ------------------------------------------------------- the kinds of empty

    @Test
    void anUnlinkedUserGetsNoWorkRatherThanEveryTask() {
        acceptSnapshot(snapshotWithTwoCrews());

        AssignedWorkView work = service.assignedWork(projectId, fitter);

        // The regression this whole slice exists to prevent: falling back to the whole schedule.
        assertThat(work.linked()).isFalse();
        assertThat(work.tasks()).isEmpty();
        assertThat(work.projectSnapshotId()).isNotNull();
    }

    @Test
    void noAcceptedSnapshotIsReportedAsSuchRatherThanAsAnEmptyDay() {
        snapshotWithTwoCrews(); // parsed, never accepted
        service.link(projectId, planner, new ProjectResourceLinkCreateRequest(fitter.userId(), "R-MECH"));

        AssignedWorkView work = service.assignedWork(projectId, fitter);

        assertThat(work.projectSnapshotId()).isNull();
        assertThat(work.linked()).isTrue();
        assertThat(work.tasks()).isEmpty();
    }

    @Test
    void aLinkTheAcceptedScheduleNoLongerCarriesIsReportedAsUnmatched() {
        acceptSnapshot(snapshotWithTwoCrews());
        service.link(projectId, planner, new ProjectResourceLinkCreateRequest(fitter.userId(), "R-GONE"));

        AssignedWorkView work = service.assignedWork(projectId, fitter);

        assertThat(work.linked()).isTrue();
        assertThat(work.unmatchedResourceUids()).containsExactly("R-GONE");
        assertThat(work.tasks()).isEmpty();
    }

    // ------------------------------------------------------------- re-import

    @Test
    void aLinkSurvivesReImportAndKeepsResolvingWork() {
        acceptSnapshot(snapshotWithTwoCrews());
        service.link(projectId, planner, new ProjectResourceLinkCreateRequest(fitter.userId(), "R-MECH"));
        assertThat(service.assignedWork(projectId, fitter).tasks()).hasSize(2);

        // A new schedule arrives carrying the same resource UID. The link is keyed on the project
        // and the UID, not on the imported_resources row, so it must still resolve.
        UUID reimported = acceptSnapshot(snapshotWithTwoCrews());

        AssignedWorkView work = service.assignedWork(projectId, fitter);
        assertThat(work.projectSnapshotId()).isEqualTo(reimported);
        assertThat(work.tasks()).hasSize(2);
        assertThat(work.unmatchedResourceUids()).isEmpty();
    }

    @Test
    void aLinkIsKeptRatherThanDeletedWhenTheResourceDisappears() {
        acceptSnapshot(snapshotWithTwoCrews());
        service.link(projectId, planner, new ProjectResourceLinkCreateRequest(fitter.userId(), "R-MECH"));

        acceptSnapshot(snapshotWithoutTheMechanicalCrew());

        // Kept, per the mapping rule that configuration for an absent source value stays available
        // for history and for the value reappearing. Reported, so nobody reads it as a clear day.
        assertThat(service.links(projectId)).singleElement()
                .satisfies(link -> {
                    assertThat(link.active()).isTrue();
                    assertThat(link.matchedInSnapshot()).isFalse();
                    assertThat(link.resourceNameAtLink()).isEqualTo("Mechanical crew");
                });
        assertThat(service.assignedWork(projectId, fitter).unmatchedResourceUids()).containsExactly("R-MECH");
    }

    // --------------------------------------------------------- curating links

    @Test
    void oneResourceCannotBeLinkedToTwoPeopleAtOnce() {
        acceptSnapshot(snapshotWithTwoCrews());
        service.link(projectId, planner, new ProjectResourceLinkCreateRequest(fitter.userId(), "R-MECH"));

        assertThatThrownBy(() -> service.link(
                projectId, planner, new ProjectResourceLinkCreateRequest(electrician.userId(), "R-MECH")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already linked");
    }

    @Test
    void revokingStopsTheWorkShowingAndFreesTheResource() {
        acceptSnapshot(snapshotWithTwoCrews());
        ProjectResourceLinkRecord link = service.link(
                projectId, planner, new ProjectResourceLinkCreateRequest(fitter.userId(), "R-MECH"));

        ProjectResourceLinkRecord revoked = service.revoke(projectId, planner, link.id());

        assertThat(revoked.active()).isFalse();
        assertThat(revoked.revokedByUserId()).isEqualTo(planner.userId());
        assertThat(service.assignedWork(projectId, fitter).linked()).isFalse();

        // Revoked, not deleted: the row is still there, and the resource can be linked again.
        assertThat(service.links(projectId)).hasSize(1);
        service.link(projectId, planner, new ProjectResourceLinkCreateRequest(electrician.userId(), "R-MECH"));
        assertThat(service.assignedWork(projectId, electrician).tasks()).hasSize(2);
    }

    @Test
    void revokingTwiceReportsTheSecondAttemptRatherThanOverwritingTheFirstRevoker() {
        acceptSnapshot(snapshotWithTwoCrews());
        ProjectResourceLinkRecord link = service.link(
                projectId, planner, new ProjectResourceLinkCreateRequest(fitter.userId(), "R-MECH"));
        service.revoke(projectId, planner, link.id());

        assertThatThrownBy(() -> service.revoke(projectId, planner, link.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No active resource link");
    }

    @Test
    void linkingRecordsWhoDidItAsProjectConfigurationRatherThanAsAPermissionChange() {
        acceptSnapshot(snapshotWithTwoCrews());
        service.link(projectId, planner, new ProjectResourceLinkCreateRequest(fitter.userId(), "R-MECH"));

        Map<String, Object> event = jdbcTemplate().queryForMap(
                "SELECT event_category::text AS category, event_type, actor_user_id"
                        + " FROM audit_events WHERE event_type = 'project_resource_linked'");

        // `project`, not `permission`. Recording it as a permission event would claim the link
        // grants something, and the whole design rests on it granting nothing.
        assertThat(event.get("category")).isEqualTo("project");
        assertThat(event.get("actor_user_id")).isEqualTo(planner.userId());
    }

    // ------------------------------------------- relevance is not authorization

    @Test
    void aLinkGrantsNoCapabilityAndItsAbsenceTakesNoneAway() {
        acceptSnapshot(snapshotWithTwoCrews());
        service.link(projectId, planner, new ProjectResourceLinkCreateRequest(fitter.userId(), "R-MECH"));

        // Being linked does not widen what a field user may do...
        assertThat(Capability.APPROVE_EXPORT_BATCH.allows(ProjectRole.FIELD_USER)).isFalse();
        assertThat(Capability.MANAGE_RESOURCE_LINK.allows(ProjectRole.FIELD_USER)).isFalse();

        // ...and not being linked does not narrow it. The electrician holds no link at all and
        // still submits progress, which is what stops a missing link locking somebody out of work
        // they have been told to do.
        assertThat(service.assignedWork(projectId, electrician).linked()).isFalse();
        assertThat(Capability.SUBMIT_TASK_PROGRESS.allows(ProjectRole.FIELD_USER)).isTrue();
    }

    @Test
    void curatingLinksIsPlannerAndAdminOnly() {
        assertThat(Capability.MANAGE_RESOURCE_LINK.allowedRoles())
                .containsExactlyInAnyOrder(ProjectRole.PLANNER, ProjectRole.ADMIN);
    }

    // ------------------------------------------------------------- candidates

    @Test
    void theResourcePickerRanksResourcesByHowMuchWorkIsBookedAgainstThem() {
        acceptSnapshot(snapshotWithTwoCrews());

        List<ProjectResourceLinkRepository.LinkableResource> resources =
                service.candidates(projectId).resources();

        // R-MATL is a material resource with nothing booked against it — the kind a real schedule
        // is full of, and the kind a planner should not have to scroll past to find a crew.
        assertThat(resources).last()
                .satisfies(resource -> {
                    assertThat(resource.resourceExternalUid()).isEqualTo("R-MATL");
                    assertThat(resource.assignedLeafTaskCount()).isZero();
                });
        assertThat(resources).extracting(ProjectResourceLinkRepository.LinkableResource::resourceExternalUid)
                .containsExactlyInAnyOrder("R-MECH", "R-ELEC", "R-MATL");

        // The summary assignment A-5 is not counted: two leaf tasks each, not three for R-MECH.
        assertThat(resources)
                .filteredOn(resource -> !resource.resourceExternalUid().equals("R-MATL"))
                .allSatisfy(resource -> {
                    assertThat(resource.assignedLeafTaskCount()).isEqualTo(2);
                    assertThat(resource.linkedUserId()).isNull();
                });
    }

    @Test
    void theResourcePickerSaysWhoAlreadyHoldsALinkedResource() {
        acceptSnapshot(snapshotWithTwoCrews());
        service.link(projectId, planner, new ProjectResourceLinkCreateRequest(fitter.userId(), "R-MECH"));

        assertThat(service.candidates(projectId).resources())
                .filteredOn(resource -> resource.resourceExternalUid().equals("R-MECH"))
                .singleElement()
                .satisfies(resource -> {
                    assertThat(resource.linkedUserId()).isEqualTo(fitter.userId());
                    assertThat(resource.linkedUserDisplayName()).isEqualTo("field_user");
                });
    }

    @Test
    void theUserPickerOffersThisProjectsMembersRatherThanEveryUser() {
        acceptSnapshot(snapshotWithTwoCrews());
        UUID outsider = fixtures.createUser("outsider@example.com", "outsider");

        assertThat(service.candidates(projectId).users())
                .extracting(ProjectResourceLinkRepository.LinkableUser::userId)
                .containsExactlyInAnyOrder(planner.userId(), fitter.userId(), electrician.userId())
                .doesNotContain(outsider);
    }

    // ------------------------------------------------------------------ setup

    /**
     * Two crews on one schedule.
     *
     * <p>T-1 and T-2 are mechanical, T-3 is electrical, and T-2 carries a second assignment to the
     * electrical crew so the de-duplication case is real rather than hypothetical. S-1 is a summary
     * task with a mechanical assignment on it, which is what a roll-up looks like in a real file.
     */
    private UUID snapshotWithTwoCrews() {
        return persistSnapshot(List.of(
                        new ImportedResourceCreateRequest("R-MECH", "Mechanical crew", "work", Map.of()),
                        new ImportedResourceCreateRequest("R-ELEC", "Electrical crew", "work", Map.of()),
                        new ImportedResourceCreateRequest("R-MATL", "Blanking plates", "material", Map.of())),
                List.of(
                        assignment("A-1", "T-1", "R-MECH"),
                        assignment("A-2", "T-2", "R-MECH"),
                        assignment("A-3", "T-2", "R-ELEC"),
                        assignment("A-4", "T-3", "R-ELEC"),
                        assignment("A-5", "S-1", "R-MECH")));
    }

    private UUID snapshotWithoutTheMechanicalCrew() {
        return persistSnapshot(
                List.of(new ImportedResourceCreateRequest("R-ELEC", "Electrical crew", "work", Map.of())),
                List.of(assignment("A-4", "T-3", "R-ELEC")));
    }

    private UUID persistSnapshot(
            List<ImportedResourceCreateRequest> resources,
            List<ImportedAssignmentCreateRequest> assignments
    ) {
        UUID batchId = importBatchId == null
                ? fixtures.createImportBatch(projectId, fixtures.createSourceFile(projectId))
                : importBatchId;
        // Each snapshot needs its own batch: the chain is one file, one parse, one snapshot.
        importBatchId = null;

        persistence.persistParsedSnapshot(new ImportedProjectSnapshotCreateRequest(
                projectId, batchId, "PROJ-1", "Kiln", null, Map.of(),
                new ImportedProjectEntities(
                        List.of(
                                task("S-1", "Kiln overhaul", true),
                                task("T-1", "Remove access cover", false),
                                task("T-2", "Replace liner plate", false),
                                task("T-3", "Isolate feeder motor", false)),
                        resources,
                        assignments,
                        List.of())));

        return jdbcTemplate().queryForObject(
                "SELECT id FROM project_snapshots WHERE import_batch_id = ?", UUID.class, batchId);
    }

    private UUID acceptSnapshot(UUID snapshotId) {
        jdbcTemplate().update(
                "UPDATE project_snapshots SET status = 'accepted', accepted_at = now() WHERE id = ?", snapshotId);
        return snapshotId;
    }

    private static ImportedTaskCreateRequest task(String uid, String name, boolean summary) {
        return new ImportedTaskCreateRequest(
                uid, uid, name, uid, uid, 1, summary, null, null,
                null, null, null, null, null, null, null, Map.of());
    }

    private static ImportedAssignmentCreateRequest assignment(String uid, String taskUid, String resourceUid) {
        return new ImportedAssignmentCreateRequest(uid, taskUid, resourceUid, null, null, Map.of());
    }

    private Actor actor(String email, String role) {
        UUID userId = fixtures.createUser(email, role);
        fixtures.grantMembership(projectId, userId, role);
        return new Actor(userId, role, role);
    }
}
