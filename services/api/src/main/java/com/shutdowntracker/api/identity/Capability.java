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
    RECORD_APPROVAL(ProjectRole.PLANNER),
    CREATE_EXPORT_PREVIEW(ProjectRole.PLANNER),
    APPROVE_EXPORT_BATCH(ProjectRole.PLANNER),
    GENERATE_EXPORT_ARTIFACT(ProjectRole.PLANNER),
    RECORD_EXPORT_VERIFICATION(ProjectRole.PLANNER),
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
