package com.shutdowntracker.api.reviewreset;

import java.util.List;

/**
 * Exactly which tables a review reset empties, and which it leaves alone.
 *
 * <p>This is the file to read to know the blast radius. The lists are written out rather than
 * derived, because a reset that discovers its own scope at runtime widens silently when a migration
 * lands. {@code ReviewDataResetScopeTests} asserts that the two lists together are every table in
 * the database, so a new migration fails a test until somebody decides which side it belongs on.
 *
 * <p><strong>Why {@code TRUNCATE} and not {@code DELETE}.</strong> Five tables in the export and
 * candidate chain carry {@code BEFORE DELETE} triggers that raise an exception — export candidate
 * records, approval records, export batches, export batch lines and candidate schedule runs are
 * append-only by design. Those triggers are row-level, and {@code TRUNCATE} fires only
 * statement-level triggers, so truncate is not a shortcut here: it is the only mechanism that works.
 *
 * <p><strong>Why there is no {@code CASCADE}.</strong> Cascade truncates every table that
 * <em>references</em> one being truncated, so the day a migration adds a foreign key from a kept
 * table into this list, cascade would quietly take the kept table with it. Without cascade,
 * PostgreSQL refuses the statement instead and names the table it will not truncate alone. The
 * reset fails closed and somebody makes a decision. The test helper in {@code src/test} does use
 * cascade, and should not be copied: its job is to wipe everything, which is the opposite of this.
 */
public final class ReviewDataResetScope {

    /**
     * Everything a trial produces: what was imported, what was tracked against it, and what went
     * back out. Ordering is irrelevant — the whole list is truncated in one statement, so there are
     * no foreign keys to satisfy part-way through and no self-references to unpick.
     */
    public static final List<String> WIPE = List.of(
            // The imported schedule, and everything derived from parsing it.
            "source_files",
            "import_batches",
            "project_snapshots",
            "imported_tasks",
            "imported_resources",
            "imported_assignments",
            "imported_extended_attributes",
            "task_lineage_links",
            "task_category_values",

            // Execution recorded against that schedule.
            "task_execution_states",
            "task_progress_updates",
            "problems",
            "actions",
            "evidence",
            "handover_notes",

            // The export handoff and what came back from Microsoft Project.
            "export_candidate_records",
            "approval_records",
            "export_batches",
            "export_batch_lines",
            "candidate_schedule_runs",

            // Critical Watch reporting.
            "critical_watchlists",
            "critical_work_packages",
            "critical_work_package_sources",
            "reporting_periods",
            "critical_updates",
            "critical_update_lines",
            // Not project shell despite looking like configuration: it carries a foreign key to
            // critical_work_packages, which is wiped. Keeping it makes the un-cascaded truncate
            // refuse, which is the check working rather than a problem to route around.
            "reporting_policy_versions",

            // Who holds which Microsoft Project resource. Wiped because the resource ids it names
            // come from a schedule that is about to stop existing; re-link after the next import.
            "project_resource_links",

            // The trail of all of the above. See ReviewDataResetService for why this is defensible
            // here and nowhere else.
            "audit_events");

    /**
     * The project, the people, and the planner's own configuration.
     *
     * <p>{@code users} and {@code project_memberships} are not sentiment: {@code redeploy.sh}
     * resolves the seeded super user out of these tables and refuses to build without it, so wiping
     * them would leave the deployment unable to deploy. {@code schema_migration_log} is not created
     * by any migration — it is {@code check-schema-drift.sh}'s own bookkeeping — and losing it makes
     * the drift check fail and the deploy refuse for a different reason.
     */
    public static final List<String> KEEP = List.of(
            "users",
            "project_memberships",
            "projects",
            "import_profiles",
            "operational_categories",
            "operational_category_aliases",
            "schema_migration_log");

    private ReviewDataResetScope() {
    }

    /**
     * One statement, so PostgreSQL takes every lock at once and no deadlock ordering exists.
     *
     * <p>The names are compile-time constants from {@link #WIPE}, never anything a caller supplies.
     */
    public static String truncateStatement() {
        return "TRUNCATE TABLE " + String.join(", ", WIPE) + " RESTART IDENTITY";
    }
}
