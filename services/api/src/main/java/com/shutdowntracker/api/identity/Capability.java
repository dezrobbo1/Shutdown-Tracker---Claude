package com.shutdowntracker.api.identity;

import java.util.Set;

/**
 * Capabilities for the operations this service currently exposes, with the roles allowed
 * to perform them by default.
 *
 * <p>Taken from {@code docs/product/permission-matrix.md}. This covers the implemented
 * endpoints only; capabilities for execution, problems, evidence, and handover follow
 * when those endpoints exist, rather than being declared here before they mean anything.
 *
 * <p>Two rules from the product docs are deliberately visible in the grants below. Export
 * approval is planner-owned — an admin can administer access but is not a routine export
 * approver. And no capability is granted by Operational Category membership: classifying
 * work never confers authority over it.
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

    private final Set<ProjectRole> allowedRoles;

    Capability(ProjectRole... allowedRoles) {
        this.allowedRoles = Set.of(allowedRoles);
    }

    public boolean allows(ProjectRole role) {
        return allowedRoles.contains(role);
    }

    public Set<ProjectRole> allowedRoles() {
        return allowedRoles;
    }
}
