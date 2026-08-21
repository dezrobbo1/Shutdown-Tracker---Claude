import { readFileSync } from "node:fs";
import { renderToString } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { capabilityAllows } from "@shutdown-tracker/api-client";
import type {
  ImportReviewSnapshotSummary,
  ProjectSnapshotStatus,
  TaskProgressUpdateRecord
} from "@shutdown-tracker/api-client";
import { App } from "./App";
import { buildConsoleSession, describeSession, resolveRole, sessionAllows } from "./session";
import { consoleZones, parseRoute, parseZoneId, sectionById, zoneById, zoneHref } from "./router";
import { buildZoneSession } from "./zones/ZoneProps";
import { newestAcceptedSnapshot } from "./useSnapshotTasks";
import { toOffsetDateTime, validateProgressInput } from "./zones/ExecutionZone";
import { queueLabel, supervisorOutcomeMessage } from "./zones/ReviewQueueZone";
import {
  canApprove,
  canGenerate,
  canMarkOpened,
  canReturnCandidate,
  canVerify,
  candidateRequestsFor
} from "./zones/ExportZone";
import { validateCategoryInput } from "./zones/MappingZone";
import { ImportReviewZone, newestSnapshotId } from "./zones/ImportReviewZone";
import { ExecutionZone } from "./zones/ExecutionZone";
import { PlannerReviewZone, SupervisorReviewZone } from "./zones/ReviewQueueZone";
import { ProblemsZone } from "./zones/ProblemsZone";
import { HandoverZone } from "./zones/HandoverZone";
import { MappingZone } from "./zones/MappingZone";
import { PeopleZone, resourceLabel } from "./zones/PeopleZone";
import { ExportZone } from "./zones/ExportZone";
import { TodayZone } from "./zones/TodayZone";
import { EvidenceZone, attachEvidenceFile, fileSizeLabel } from "./zones/EvidenceZone";
import { CriticalWatchZone, reportCountLabel, updateStatusLabel } from "./zones/CriticalWatchZone";
import { createConsoleApiClient } from "./consoleApi";
import { candidateRunStateLabels, formatPercent, toneForState } from "./formatting";

describe("console shell", () => {
  it("offers every operational zone as a linkable route", () => {
    const html = renderToString(<App />);

    // The baseline information architecture from the concept pack. Adding a top-level zone
    // is a product decision, so this list is deliberately pinned.
    expect(consoleZones.map((zone) => zone.label)).toEqual([
      "Today",
      "Tasks",
      "Problems",
      "Evidence",
      "Exports"
    ]);

    for (const zone of consoleZones) {
      expect(html).toContain(zone.label);
      expect(html).toContain(zoneHref(zone.id));
    }
  });

  it("opens on Today, because the first question is what needs attention now", () => {
    expect(parseZoneId("")).toBe("today");
    expect(parseZoneId("#/nonsense")).toBe("today");
    expect(parseZoneId("#/exports")).toBe("exports");
    expect(parseZoneId("exports")).toBe("exports");
    expect(zoneById("exports").sections[0].title).toBe("Imported snapshots");
  });

  it("addresses a section inside a zone so a link can point at the exact surface", () => {
    expect(parseRoute("#/exports/mapping")).toEqual({ zoneId: "exports", sectionId: "mapping" });
    expect(zoneHref("exports", "mapping")).toBe("#/exports/mapping");

    // An unknown section falls back to the zone's first, rather than rendering nothing.
    expect(parseRoute("#/exports/nonsense").sectionId).toBe("import-review");
    expect(sectionById("tasks", "supervisor-review").title).toBe("Supervisor review queue");
  });

  it("keeps the surfaces that lost a sidebar entry reachable as sections", () => {
    const sections = consoleZones.flatMap((zone) =>
      zone.sections.map((section) => `${zone.id}/${section.id}`)
    );

    expect(sections).toContain("exports/import-review");
    expect(sections).toContain("exports/mapping");
    expect(sections).toContain("exports/planner-review");
    // Who each Project resource is: the same kind of act as Mapping, so a section beside it.
    expect(sections).toContain("exports/people");
    expect(sections).toContain("problems/handover");
    expect(sections).toContain("tasks/supervisor-review");
    // Critical Watch reports on scheduled work, so it is a section under Tasks rather than a
    // sixth zone competing with the concept pack's five.
    expect(sections).toContain("tasks/critical-watch");
    expect(consoleZones.map((zone) => zone.id)).toHaveLength(5);
  });

  it("says plainly when it is not configured rather than showing empty panels", () => {
    const html = renderToString(<App />);

    expect(html).toContain("No actor configured");
    expect(html).toContain("Not configured");
  });

  it("offers only the configured identity when seeded review identities are unavailable", () => {
    const html = renderToString(<App />);

    // The old fallback was the nine-role selector, which changed what the interface offered and
    // never what the server answered. Falling back to it would restore exactly that.
    expect(html).toContain("Seeded review identities are not enabled on this server.");
    expect(html).not.toContain("Acting role");
  });
});

describe("session", () => {
  const env = {
    VITE_SHUTDOWN_TRACKER_PROJECT_ID: "project-1",
    VITE_SHUTDOWN_TRACKER_ACTOR_ID: "user-1",
    VITE_SHUTDOWN_TRACKER_ACTOR_NAME: "Sam Planner",
    VITE_SHUTDOWN_TRACKER_ACTOR_ROLE: "planner"
  };

  it("is live only when both a project and an actor are configured", () => {
    expect(buildConsoleSession(env).live).toBe(true);
    expect(buildConsoleSession({ ...env, VITE_SHUTDOWN_TRACKER_PROJECT_ID: "" }).live).toBe(false);
    expect(buildConsoleSession({ ...env, VITE_SHUTDOWN_TRACKER_ACTOR_ID: "" }).live).toBe(false);
  });

  it("prefers the in-session role over the build-time default", () => {
    expect(resolveRole("supervisor", "planner")).toBe("supervisor");
    expect(resolveRole(null, "planner")).toBe("planner");
  });

  it("discards an unrecognised role instead of sending it to the API", () => {
    expect(resolveRole("wizard", "planner")).toBe("planner");
    expect(resolveRole("wizard", "sorcerer")).toBeNull();
    expect(buildConsoleSession({ ...env, VITE_SHUTDOWN_TRACKER_ACTOR_ROLE: "wizard" }).actor).toBeNull();
  });

  it("permits nothing when no actor is configured", () => {
    const session = buildConsoleSession({});

    expect(sessionAllows(session, "VIEW_PROJECT")).toBe(false);
    expect(sessionAllows(session, "APPROVE_EXPORT_BATCH")).toBe(false);
    expect(describeSession(session)).toContain("No actor configured");
  });

  it("takes the whole identity from storage rather than mixing it with the build-time actor", () => {
    const session = buildConsoleSession(env, {
      userId: "user-2",
      role: "supervisor",
      displayName: "Rae Supervisor",
      projectId: "project-2"
    });

    // All four together. Taking the id from one source and the role from another is how a session
    // ends up claiming a role its membership does not have.
    expect(session.actor?.userId).toBe("user-2");
    expect(session.actor?.role).toBe("supervisor");
    expect(session.actor?.displayName).toBe("Rae Supervisor");
    expect(session.projectId).toBe("project-2");
  });

  it("falls back to the build-time actor when the stored identity is unusable", () => {
    const withoutUser = buildConsoleSession(env, {
      userId: "",
      role: "supervisor",
      displayName: "Rae Supervisor"
    });
    const withUnknownRole = buildConsoleSession(env, {
      userId: "user-2",
      role: "wizard",
      displayName: "Rae Supervisor"
    });

    // Discarded whole, not partially applied: a half-valid identity produces a confusing refusal
    // at the first write rather than an obvious problem now.
    expect(withoutUser.actor?.userId).toBe("user-1");
    expect(withUnknownRole.actor?.userId).toBe("user-1");
    expect(withUnknownRole.actor?.role).toBe("planner");
  });

  it("keeps the build-time project when the stored identity does not name one", () => {
    const session = buildConsoleSession(env, {
      userId: "user-2",
      role: "supervisor",
      displayName: "Rae Supervisor"
    });

    expect(session.projectId).toBe("project-1");
  });
});

describe("capability gating mirrors the product rules", () => {
  it("keeps export approval with the planner and away from the administrator", () => {
    expect(capabilityAllows("APPROVE_EXPORT_BATCH", "planner")).toBe(true);
    expect(capabilityAllows("APPROVE_EXPORT_BATCH", "admin")).toBe(false);
    expect(capabilityAllows("APPROVE_EXPORT_BATCH", "supervisor")).toBe(false);
  });

  it("does not let supervisor acceptance become export approval", () => {
    const supervisor = buildZoneSession(
      buildConsoleSession({
        VITE_SHUTDOWN_TRACKER_PROJECT_ID: "p",
        VITE_SHUTDOWN_TRACKER_ACTOR_ID: "u",
        VITE_SHUTDOWN_TRACKER_ACTOR_ROLE: "supervisor"
      })
    );

    expect(supervisor.canReviewProgress).toBe(true);
    expect(supervisor.canPlannerReview).toBe(false);
    expect(supervisor.canApproveExport).toBe(false);
    expect(supervisor.canGenerateArtifact).toBe(false);
  });

  it("lets a field user report progress but not review it", () => {
    const field = buildZoneSession(
      buildConsoleSession({
        VITE_SHUTDOWN_TRACKER_PROJECT_ID: "p",
        VITE_SHUTDOWN_TRACKER_ACTOR_ID: "u",
        VITE_SHUTDOWN_TRACKER_ACTOR_ROLE: "field_user"
      })
    );

    expect(field.canSubmitProgress).toBe(true);
    expect(field.canRaiseProblem).toBe(true);
    expect(field.canReviewProgress).toBe(false);
    expect(field.canManageProblem).toBe(false);
  });

  it("keeps mapping configuration with the planner, who owns the interpretation", () => {
    const admin = buildZoneSession(
      buildConsoleSession({
        VITE_SHUTDOWN_TRACKER_PROJECT_ID: "p",
        VITE_SHUTDOWN_TRACKER_ACTOR_ID: "u",
        VITE_SHUTDOWN_TRACKER_ACTOR_ROLE: "admin"
      })
    );

    expect(admin.canAcceptSnapshot).toBe(true);
    expect(admin.canManageMapping).toBe(false);

    // Linking a Project resource to a person is shared with the admin, who maintains who the
    // users are. It is the one mapping-shaped act that is not planner-only.
    expect(admin.canManageResourceLink).toBe(true);
  });

  it("does not let a resource link become a permission", () => {
    const field = buildZoneSession(
      buildConsoleSession({
        VITE_SHUTDOWN_TRACKER_PROJECT_ID: "p",
        VITE_SHUTDOWN_TRACKER_ACTOR_ID: "u",
        VITE_SHUTDOWN_TRACKER_ACTOR_ROLE: "field_user"
      })
    );

    // A field user is the person a link is about, and still cannot curate one. The link decides
    // what their work list shows; it is not a grant, and it is not theirs to make.
    expect(field.canManageResourceLink).toBe(false);
    expect(capabilityAllows("MANAGE_RESOURCE_LINK", "planner")).toBe(true);
    expect(capabilityAllows("MANAGE_RESOURCE_LINK", "admin")).toBe(true);
    expect(capabilityAllows("MANAGE_RESOURCE_LINK", "supervisor")).toBe(false);
  });
});

describe("linking a person to their Project resource", () => {
  const session = buildZoneSession(buildConsoleSession({}));
  const client = createConsoleApiClient(session, {
    fetchImpl: () => Promise.reject(new Error("no network in tests"))
  });

  it("says a link decides visibility rather than access", () => {
    const html = renderToString(<PeopleZone session={session} client={client} />);

    // The sentence a planner needs before they start linking people: this is not access control.
    expect(html).toContain("grants no permission and takes");
  });

  it("does not offer the link form to somebody who may not link", () => {
    const html = renderToString(<PeopleZone session={session} client={client} />);

    // An unconfigured session holds no capability, so the form is not rendered as a control
    // that will fail on submit — the panel says whose job it is instead.
    expect(html).toContain("Linking a resource to a person is planner-owned.");
    expect(html).not.toContain("Choose a resource from the accepted schedule");
  });

  it("shows how much work a resource carries, so a crew is not lost among materials", () => {
    expect(
      resourceLabel({
        resourceExternalUid: "R-MECH",
        name: "Mechanical crew",
        assignedLeafTaskCount: 12,
        linkedUserDisplayName: null
      })
    ).toBe("Mechanical crew — 12 tasks");

    // Already linked resources say who holds them rather than vanishing from the list, so a
    // planner can see why one is unavailable instead of wondering where it went.
    expect(
      resourceLabel({
        resourceExternalUid: "R-ELEC",
        name: "Electrical crew",
        assignedLeafTaskCount: 1,
        linkedUserDisplayName: "Sam Okafor"
      })
    ).toBe("Electrical crew — 1 task — already linked to Sam Okafor");
  });
});

describe("progress reporting", () => {
  it("rejects a percentage outside the reportable range", () => {
    expect(validateProgressInput("50", "", "")).toBeNull();
    expect(validateProgressInput("", "", "")).toBeNull();
    expect(validateProgressInput("101", "", "")).toContain("between 0 and 100");
    expect(validateProgressInput("-1", "", "")).toContain("between 0 and 100");
    expect(validateProgressInput("half", "", "")).toContain("between 0 and 100");
  });

  it("refuses a finish before its start, and a finish with no start", () => {
    expect(validateProgressInput("", "2026-08-14T08:00", "2026-08-14T06:00")).toContain(
      "cannot be before actual start"
    );
    expect(validateProgressInput("", "", "2026-08-14T06:00")).toContain("actual start before");
    expect(validateProgressInput("", "2026-08-14T06:00", "2026-08-14T08:00")).toBeNull();
  });

  it("sends an offset-carrying instant rather than a zoneless local string", () => {
    const sent = toOffsetDateTime("2026-08-14T08:00");

    expect(sent).not.toBeNull();
    expect(new Date(sent as string).toISOString()).toBe(sent);
    expect(toOffsetDateTime("")).toBeNull();
  });
});

describe("review queues", () => {
  it("names who is waiting rather than showing a state constant", () => {
    expect(queueLabel(0)).toBe("0 awaiting");
    expect(queueLabel(1)).toBe("1 awaiting");
    expect(queueLabel(null)).toBe("Loading");
  });

  it("tells the supervisor that acceptance is not export approval", () => {
    expect(supervisorOutcomeMessage("SUPERVISOR_ACCEPTED")).toContain("awaits planner review");
    expect(supervisorOutcomeMessage("CORRECTION_REQUESTED")).toContain("not export eligible");
    expect(supervisorOutcomeMessage("REJECTED")).toContain("will not reach an export batch");
  });
});

describe("controlled export", () => {
  const update = (overrides: Partial<TaskProgressUpdateRecord> = {}): TaskProgressUpdateRecord => ({
    id: "update-1",
    projectId: "project-1",
    projectSnapshotId: "snapshot-1",
    importedTaskId: "task-1",
    executionState: "IN_PROGRESS",
    percentComplete: 40,
    actualStart: "2026-08-14T06:00:00Z",
    actualFinish: null,
    physicalPercentComplete: null,
    comment: null,
    submittedByUserId: "user-1",
    progressReviewState: "SUPERVISOR_ACCEPTED",
    plannerReviewState: "PLANNER_APPROVED",
    exportState: "ELIGIBLE",
    supersedesProgressUpdateId: null,
    ...overrides
  });

  it("emits a candidate only for the fields an update actually carries", () => {
    const requests = candidateRequestsFor(update(), "snapshot-1");

    expect(requests.map((request) => request.fieldName)).toEqual(["percent_complete", "actual_start"]);
    expect(requests.every((request) => request.sourceEntityType === "task_progress_update")).toBe(true);
    expect(requests.every((request) => request.sourceEntityId === "update-1")).toBe(true);
    expect(requests.every((request) => request.projectSnapshotId === "snapshot-1")).toBe(true);
    expect(requests.every((request) => request.sourceVersion === "update-1")).toBe(true);
  });

  it("emits nothing for an update that changes no schedule field", () => {
    expect(candidateRequestsFor(update({ percentComplete: null, actualStart: null }), "snapshot-1")).toEqual([]);
  });

  /**
   * Physical percent complete is reviewable and readable, but it is not on the MVP export
   * whitelist. Emitting it built previews carrying a line the server could only ever mark
   * ineligible, which made the preview misdescribe what the chain had actually approved.
   */
  it("does not emit a field the export whitelist will refuse", () => {
    const requests = candidateRequestsFor(
      update({ percentComplete: null, actualStart: null, physicalPercentComplete: 30 }),
      "snapshot-1"
    );

    expect(requests).toEqual([]);
    expect(candidateRequestsFor(update({ physicalPercentComplete: 30 }), "snapshot-1").map((r) => r.fieldName))
      .toEqual(["percent_complete", "actual_start"]);
  });

  /**
   * "Newest" and "newest accepted" differ exactly when a re-import is sitting in review — and the
   * export policy refuses candidates against anything but the accepted snapshot, so the console
   * pointing at an unaccepted one sent the whole zone at a schedule the server would reject.
   */
  it("works from the newest accepted schedule, not merely the newest one", () => {
    const snapshot = (snapshotVersion: number, status: ProjectSnapshotStatus) =>
      ({ id: `s${snapshotVersion}`, snapshotVersion, status }) as ImportReviewSnapshotSummary;

    expect(newestAcceptedSnapshot([snapshot(1, "ACCEPTED"), snapshot(2, "PARSED")])?.id).toBe("s1");
    expect(newestAcceptedSnapshot([snapshot(1, "ACCEPTED"), snapshot(2, "ACCEPTED")])?.id).toBe("s2");
    expect(newestAcceptedSnapshot([snapshot(1, "REJECTED"), snapshot(2, "PARSED")])).toBeUndefined();
  });

  it("enforces the export sequence so no step can be skipped", () => {
    expect(canApprove("DRAFT_PREVIEW")).toBe(true);
    expect(canApprove("APPROVED")).toBe(false);

    expect(canGenerate("DRAFT_PREVIEW")).toBe(false);
    expect(canGenerate("APPROVED")).toBe(true);

    expect(canMarkOpened("APPROVED")).toBe(false);
    expect(canMarkOpened("GENERATED")).toBe(true);

    expect(canVerify("GENERATED")).toBe(false);
    expect(canVerify("OPENED_IN_MICROSOFT_PROJECT")).toBe(true);
  });

  /**
   * A candidate can only be returned against a batch Microsoft Project was actually handed.
   * Offering the control earlier would invite a planner to upload a file that came from
   * somewhere else entirely, and the record would say it came from this batch.
   */
  it("accepts a returned candidate only after an artifact exists", () => {
    expect(canReturnCandidate("DRAFT_PREVIEW")).toBe(false);
    expect(canReturnCandidate("AWAITING_APPROVAL")).toBe(false);
    expect(canReturnCandidate("APPROVED")).toBe(false);

    expect(canReturnCandidate("GENERATED")).toBe(true);
    expect(canReturnCandidate("OPENED_IN_MICROSOFT_PROJECT")).toBe(true);
    // Verifying that the artifact opened and returning what Project made of it are separate
    // acts, and a planner may do them in that order.
    expect(canReturnCandidate("VERIFIED")).toBe(true);

    expect(canReturnCandidate("REJECTED")).toBe(false);
    expect(canReturnCandidate("FAILED")).toBe(false);
    expect(canReturnCandidate("SUPERSEDED")).toBe(false);
  });

  it("keeps returning a candidate with the planner, and says it is not adoption", () => {
    const asRole = (role: string) =>
      buildZoneSession(
        buildConsoleSession({
          VITE_SHUTDOWN_TRACKER_PROJECT_ID: "p",
          VITE_SHUTDOWN_TRACKER_ACTOR_ID: "u",
          VITE_SHUTDOWN_TRACKER_ACTOR_ROLE: role
        })
      );

    expect(asRole("planner").canReturnCandidate).toBe(true);
    expect(asRole("supervisor").canReturnCandidate).toBe(false);
    // An administrator administers access; they do not run Microsoft Project against a candidate.
    expect(asRole("admin").canReturnCandidate).toBe(false);

    expect(candidateRunStateLabels.ACCEPTED).toBe("Accepted by planner");
    expect(candidateRunStateLabels.ACCEPTED).not.toContain("adopt");
    expect(candidateRunStateLabels.RETURNED).toContain("not yet compared");
  });
});

describe("operational mapping", () => {
  it("will not accept a source mode without the configuration it needs", () => {
    expect(validateCategoryInput("TASK_FIELD", "", "1")).toContain("field name or alias");
    expect(validateCategoryInput("TASK_FIELD", "Text3", "1")).toBeNull();
    expect(validateCategoryInput("HIERARCHY_ANCESTOR", "", "not a level")).toContain("outline level");
    expect(validateCategoryInput("HIERARCHY_ANCESTOR", "", "2")).toBeNull();
    expect(validateCategoryInput("RESOURCE_GROUP", "", "")).toBeNull();
  });
});

describe("display", () => {
  it("selects the newest snapshot, not the first the API returned", () => {
    const snapshots = [
      { id: "a", snapshotVersion: 1 },
      { id: "c", snapshotVersion: 3 },
      { id: "b", snapshotVersion: 2 }
    ] as Parameters<typeof newestSnapshotId>[0];

    expect(newestSnapshotId(snapshots)).toBe("c");
    expect(newestSnapshotId([])).toBeNull();
  });

  it("distinguishes an absent value from zero", () => {
    expect(formatPercent(0)).toBe("0%");
    expect(formatPercent(null)).toBe("—");
  });

  it("tones a waiting state differently from a settled one", () => {
    expect(toneForState("Awaiting supervisor review")).toBe("amber");
    expect(toneForState("Planner approved")).toBe("green");
    expect(toneForState("Export blocked")).toBe("red");
  });
});

describe("evidence attachment", () => {
  const file = new File(["blanking plate fitted"], "blanking-plate.jpg", { type: "image/jpeg" });

  /**
   * Registering second would leave a stored file with nothing referencing it, and there is no
   * record to upload against until the first call returns. The order is the contract.
   */
  it("registers the record before uploading the file, and uploads against that record", async () => {
    const calls: string[] = [];
    const client = {
      evidence: {
        register: async (projectId: string, request: { originalFilename: string; sizeBytes?: number | null }) => {
          calls.push(`register ${projectId} ${request.originalFilename} ${request.sizeBytes}`);
          return { id: "evidence-1" };
        },
        uploadContent: async (projectId: string, evidenceId: string) => {
          calls.push(`upload ${projectId} ${evidenceId}`);
          return { id: evidenceId };
        }
      }
    };

    await attachEvidenceFile(client as never, "p1", "task-1", file, "  Guard removed.  ");

    expect(calls).toEqual([
      `register p1 blanking-plate.jpg ${file.size}`,
      "upload p1 evidence-1"
    ]);
  });

  it("does not upload when the record could not be registered", async () => {
    let uploaded = false;
    const client = {
      evidence: {
        register: async () => {
          throw new Error("registration refused");
        },
        uploadContent: async () => {
          uploaded = true;
          return { id: "evidence-1" };
        }
      }
    };

    await expect(attachEvidenceFile(client as never, "p1", "task-1", file, "")).rejects.toThrow(
      "registration refused"
    );
    expect(uploaded).toBe(false);
  });

  it("says how big an uploaded file is, and says when there is no file", () => {
    expect(fileSizeLabel(512)).toBe("512 bytes");
    expect(fileSizeLabel(2048)).toBe("2 KB");
    expect(fileSizeLabel(3 * 1024 * 1024)).toBe("3.0 MB");
    // A record uploaded before size was recorded still reads as uploaded rather than as zero.
    expect(fileSizeLabel(null)).toBe("Uploaded");
  });
});

describe("every zone renders", () => {
  // The router picks a component per route; a zone that throws on first render would be
  // invisible until someone navigated to it. Each is rendered here with an unconfigured
  // session, which is the state a zone must survive before any data arrives.
  const session = buildZoneSession(buildConsoleSession({}));
  const client = createConsoleApiClient(session, {
    fetchImpl: () => Promise.reject(new Error("no network in tests"))
  });

  const zones = [
    ["today", TodayZone],
    ["import-review", ImportReviewZone],
    ["execution", ExecutionZone],
    ["supervisor-review", SupervisorReviewZone],
    ["planner-review", PlannerReviewZone],
    ["problems", ProblemsZone],
    ["handover", HandoverZone],
    ["evidence", EvidenceZone],
    ["mapping", MappingZone],
    ["people", PeopleZone],
    ["export", ExportZone],
    ["critical-watch", CriticalWatchZone]
  ] as const;

  for (const [id, Zone] of zones) {
    it(`renders ${id} without a configured project`, () => {
      const html = renderToString(<Zone session={session} client={client} />);

      expect(html.length).toBeGreaterThan(0);
      expect(html).toContain("Configure a project and actor");
    });
  }

  it("shows the product boundary where someone might expect a recalculation", () => {
    const html = renderToString(<ExportZone session={session} client={client} />);

    expect(html).toContain("does not write the master");
    expect(html).toContain("recalculates nothing");
  });

  it("says a Critical Work Package is grouped, not calculated", () => {
    const html = renderToString(<CriticalWatchZone session={session} client={client} />);

    // The name invites the assumption that the product works out what is critical. It does
    // not: membership is chosen by a person from summary tasks.
    expect(html).toContain("summary tasks");
    expect(html).toContain("critical path");
    expect(html).toContain("does not update Microsoft Project");
  });

  it("reports Critical Watch coverage without claiming anything is overdue", () => {
    const html = renderToString(<TodayZone session={session} client={client} />);

    // Reporting policies are not built, so there is no schedule to be late against.
    expect(html).toContain("Coverage, not lateness");
    expect(html).not.toContain("overdue report");
  });
});

describe("critical watch", () => {
  it("keeps a superseded report visible and labelled rather than hiding it", () => {
    expect(updateStatusLabel({ status: "superseded", supersedesCriticalUpdateId: null })).toBe(
      "Superseded"
    );
    expect(updateStatusLabel({ status: "submitted", supersedesCriticalUpdateId: "earlier" })).toBe(
      "Correction"
    );
    expect(updateStatusLabel({ status: "submitted", supersedesCriticalUpdateId: null })).toBe(
      "Reported"
    );
  });

  it("distinguishes no reports from not yet knowing", () => {
    expect(reportCountLabel(null)).toBe("Loading");
    expect(reportCountLabel(0)).toBe("0 reports");
    expect(reportCountLabel(1)).toBe("1 report");
  });

  it("separates composing a package from reporting on one", () => {
    const planner = buildZoneSession(
      buildConsoleSession({
        VITE_SHUTDOWN_TRACKER_PROJECT_ID: "p",
        VITE_SHUTDOWN_TRACKER_ACTOR_ID: "u",
        VITE_SHUTDOWN_TRACKER_ACTOR_ROLE: "planner"
      })
    );
    const supervisor = buildZoneSession(
      buildConsoleSession({
        VITE_SHUTDOWN_TRACKER_PROJECT_ID: "p",
        VITE_SHUTDOWN_TRACKER_ACTOR_ID: "u",
        VITE_SHUTDOWN_TRACKER_ACTOR_ROLE: "supervisor"
      })
    );

    expect(planner.canManageCriticalWatchlist).toBe(true);
    // A planner decides what a package covers; the people on the work report against it.
    expect(planner.canSubmitCriticalUpdate).toBe(false);
    expect(supervisor.canSubmitCriticalUpdate).toBe(true);
    expect(supervisor.canManageCriticalWatchlist).toBe(false);
  });
});

describe("design tokens", () => {
  // The stylesheets carried ~65 literal colours between them, so a restyle meant a
  // find-and-replace rather than a change of palette. Every colour is now declared once, and
  // this keeps it that way.
  const consoleCss = readFileSync(new URL("./styles.css", import.meta.url), "utf8");
  const stylesheets = [
    ["console", consoleCss],
    ["field app", readFileSync(new URL("../../mobile-pwa/src/styles.css", import.meta.url), "utf8")]
  ] as const;

  for (const [name, css] of stylesheets) {
    it(`declares every ${name} colour once, in :root`, () => {
      const root = css.slice(0, css.indexOf("\n}"));
      const rest = css.slice(css.indexOf("\n}"));

      expect(root).toContain("--brand:");
      expect(root).toContain("--radius:");
      expect(rest.match(/#[0-9a-fA-F]{6}/g)).toBeNull();
    });
  }

  it("keeps the page header compact rather than a marketing hero", () => {
    const header = consoleCss.slice(consoleCss.indexOf(".workspace-header h1 {"));

    expect(header).not.toContain("clamp(2rem");
    expect(header.slice(0, 80)).toContain("1.35rem");
  });
});
