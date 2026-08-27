package com.shutdowntracker.api.identity;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Capabilities for the operations this service currently exposes, with the roles allowed
 * to perform them by default.
 *
 * <p>Taken from {@code docs/product/permission-matrix.md}. This covers the implemented
 * endpoints only; capabilities for execution, problems, evidence, and handover follow
 * when those endpoints exist, rather than being declared here before they mean anything.
 *
 * <p>One rule from the product docs is deliberately visible in the grants below: no capability
 * is granted by Operational Category membership, because classifying work never confers
 * authority over it.
 *
 * <p><strong>A second rule is suspended for the console round-trip trial.</strong> The trial is
 * driven by one person, so {@code admin} is a <em>super user</em>: it holds every capability
 * declared here, and {@link #SUPER_USER} rather than the individual grant lists is what says so.
 * Separation of duty is what this suspends — export approval is planner-owned precisely because
 * administering access is not the same as deciding what returns to Microsoft Project, and the
 * three review stages exist so that more than one person walks them. Those stages still exist and
 * are still walked in order; one person now walks all of them, so the four-eyes property does not
 * hold while the trial runs.
 *
 * <p>The per-role grants below are therefore still the real permission model, and are deliberately
 * left as they were: restoring separation of duty means removing the {@link #SUPER_USER} rule and
 * giving the trial a second actor, not reconstructing grants that were edited away.
 */
public enum Capability {

    UPLOAD_SOURCE_FILE(ProjectRole.PLANNER, ProjectRole.ADMIN),
    REQUEST_PROJECT_PARSE(ProjectRole.PLANNER, ProjectRole.ADMIN),
    ACCEPT_IMPORT_SNAPSHOT(ProjectRole.PLANNER, ProjectRole.ADMIN),
    REJECT_IMPORT_SNAPSHOT(ProjectRole.PLANNER, ProjectRole.ADMIN),
    RECONCILE_TASK_LINEAGE(ProjectRole.PLANNER),
    // Mapping configuration is planner-owned. An admin may administer who can configure
    // mappings but does not own the planner's interpretation of Project fields.
    MANAGE_IMPORT_PROFILE(ProjectRole.PLANNER),
    // Deciding which Microsoft Project resource is which person. Planner-owned for the same
    // reason as the import profile — it is an interpretation of the Project source — and shared
    // with an admin, who is the role that maintains who the users are in the first place.
    //
    // This capability governs curating the links. It is never consulted when deciding what
    // somebody may do: a link narrows a work list and confers no authority, so the capabilities
    // below are unchanged by whether the actor holds one.
    MANAGE_RESOURCE_LINK(ProjectRole.PLANNER, ProjectRole.ADMIN),
    RECORD_APPROVAL(ProjectRole.PLANNER),
    CREATE_EXPORT_PREVIEW(ProjectRole.PLANNER),
    APPROVE_EXPORT_BATCH(ProjectRole.PLANNER),
    GENERATE_EXPORT_ARTIFACT(ProjectRole.PLANNER),
    RECORD_EXPORT_VERIFICATION(ProjectRole.PLANNER),
    // Bringing back the schedule Microsoft Project calculated, and reading one back. Planner-only,
    // like every other capability in the export handoff: the planner is who runs Project against
    // the candidate and who reviews what it produced. Reading the returned file is gated on the
    // same capability rather than on VIEW_PROJECT, because a full recalculated schedule is not the
    // same kind of thing as a task list, and only somebody reviewing a candidate needs the bytes.
    RETURN_CANDIDATE_SCHEDULE(ProjectRole.PLANNER),

    // Execution. Field users and contractors submit for their own assigned work. The grant
    // stays by role even though project_resource_links now models who holds which resource,
    // because that link is relevance and this is permission. Narrowing the grant to it would
    // stop a supervisor reporting on behalf of a crew that has none, and would quietly turn
    // Project resource data into an authorization source, which AGENTS.md forbids.
    SUBMIT_TASK_PROGRESS(
            ProjectRole.FIELD_USER,
            ProjectRole.CONTRACTOR,
            ProjectRole.SUPERVISOR,
            ProjectRole.COORDINATOR),
    // Supervisor review confirms operational validity. It is not export approval, which
    // is why it does not include the planner-only export capabilities above.
    REVIEW_TASK_PROGRESS(ProjectRole.SUPERVISOR, ProjectRole.COORDINATOR, ProjectRole.SHUTDOWN_CONTROL),
    PLANNER_REVIEW_TASK_PROGRESS(ProjectRole.PLANNER),

    // Operational records. Anyone doing the work can raise a problem or capture evidence;
    // deciding what happens to a problem is a coordination responsibility.
    RAISE_PROBLEM(
            ProjectRole.FIELD_USER,
            ProjectRole.CONTRACTOR,
            ProjectRole.SUPERVISOR,
            ProjectRole.COORDINATOR,
            ProjectRole.SHUTDOWN_CONTROL,
            ProjectRole.INSPECTOR,
            ProjectRole.PLANNER),
    MANAGE_PROBLEM(ProjectRole.SUPERVISOR, ProjectRole.COORDINATOR, ProjectRole.SHUTDOWN_CONTROL),
    MANAGE_ACTION(ProjectRole.SUPERVISOR, ProjectRole.COORDINATOR, ProjectRole.SHUTDOWN_CONTROL),
    CAPTURE_EVIDENCE(
            ProjectRole.FIELD_USER,
            ProjectRole.CONTRACTOR,
            ProjectRole.SUPERVISOR,
            ProjectRole.INSPECTOR),
    RECORD_HANDOVER(
            ProjectRole.FIELD_USER,
            ProjectRole.SUPERVISOR,
            ProjectRole.COORDINATOR,
            ProjectRole.SHUTDOWN_CONTROL),

    // Critical Watch, from docs/product/critical-watchlist-permissions.md. Composing a
    // watchlist is a planning act; reporting against one is an execution act, which is why
    // these are two capabilities and not one.
    MANAGE_CRITICAL_WATCHLIST(ProjectRole.PLANNER, ProjectRole.SHUTDOWN_CONTROL),
    // The matrix says "assigned Field User, assigned Contractor". The grant is by role, for
    // the reason SUBMIT_TASK_PROGRESS gives above: the resource link decides relevance, not
    // authority. A planner is deliberately absent: planners compose Critical Work Packages,
    // they do not report on them.
    SUBMIT_CRITICAL_UPDATE(
            ProjectRole.SHUTDOWN_CONTROL,
            ProjectRole.COORDINATOR,
            ProjectRole.SUPERVISOR,
            ProjectRole.FIELD_USER,
            ProjectRole.CONTRACTOR,
            ProjectRole.INSPECTOR),

    /**
     * Clearing a synthetic review project back to nothing.
     *
     * <p>Trial scaffolding, and admin-only so that the refusal is the default for everybody else.
     * It is not in {@code docs/product/permission-matrix.md} because it is not a product capability:
     * it exists alongside the review seeder and leaves with it.
     */
    RESET_REVIEW_DATA(ProjectRole.ADMIN),

    VIEW_PROJECT(
            ProjectRole.ADMIN,
            ProjectRole.PLANNER,
            ProjectRole.SHUTDOWN_CONTROL,
            ProjectRole.COORDINATOR,
            ProjectRole.SUPERVISOR,
            ProjectRole.FIELD_USER,
            ProjectRole.CONTRACTOR,
            ProjectRole.INSPECTOR,
            ProjectRole.VIEWER);

    /**
     * The role that holds every capability, whatever the grants say.
     *
     * <p>One rule in one place, rather than {@code admin} appended to twenty-four grant lists.
     * Written that way it would be indistinguishable from a considered decision per capability,
     * a new capability would silently omit the super user, and restoring separation of duty
     * would be twenty-four edits that a reviewer has to check are all of them.
     *
     * <p>Mirrored in {@code packages/api-client/src/identity.ts}; {@code CapabilityClientParityTests}
     * fails if the two disagree.
     */
    public static final ProjectRole SUPER_USER = ProjectRole.ADMIN;

    private final Set<ProjectRole> declaredRoles;

    Capability(ProjectRole... declaredRoles) {
        this.declaredRoles = Set.of(declaredRoles);
    }

    public boolean allows(ProjectRole role) {
        return role == SUPER_USER || declaredRoles.contains(role);
    }

    /**
     * Every role that may perform this capability, the super user included.
     *
     * <p>This answers the same question {@link #allows(ProjectRole)} does and must not answer it
     * differently: a caller listing who may act and a caller asking about one role would otherwise
     * disagree about the super user.
     */
    public Set<ProjectRole> allowedRoles() {
        Set<ProjectRole> roles = new LinkedHashSet<>(declaredRoles);
        roles.add(SUPER_USER);
        return Set.copyOf(roles);
    }

    /**
     * The roles this capability is granted to by the permission matrix, before the super user rule.
     *
     * <p>Kept separate so the trial relaxation stays visible and reversible: this is the model
     * {@code docs/product/permission-matrix.md} describes and the one that returns when the trial
     * ends.
     */
    public Set<ProjectRole> declaredRoles() {
        return declaredRoles;
    }
}
