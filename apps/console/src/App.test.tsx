import { renderToString } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { capabilityAllows } from "@shutdown-tracker/api-client";
import type { TaskProgressUpdateRecord } from "@shutdown-tracker/api-client";
import { App } from "./App";
import { buildConsoleSession, describeSession, resolveRole, sessionAllows } from "./session";
import { consoleZones, parseZoneId, zoneById, zoneHref } from "./router";
import { buildZoneSession } from "./zones/ZoneProps";
import { toOffsetDateTime, validateProgressInput } from "./zones/ExecutionZone";
import { queueLabel, supervisorOutcomeMessage } from "./zones/ReviewQueueZone";
import { canApprove, canGenerate, canMarkOpened, canVerify, candidateRequestsFor } from "./zones/ExportZone";
import { validateCategoryInput } from "./zones/MappingZone";
import { ImportReviewZone, newestSnapshotId } from "./zones/ImportReviewZone";
import { ExecutionZone } from "./zones/ExecutionZone";
import { ReviewQueueZone } from "./zones/ReviewQueueZone";
import { ProblemsZone } from "./zones/ProblemsZone";
import { HandoverZone } from "./zones/HandoverZone";
import { MappingZone } from "./zones/MappingZone";
import { ExportZone } from "./zones/ExportZone";
import { createConsoleApiClient } from "./consoleApi";
import { formatPercent, toneForState } from "./formatting";

describe("console shell", () => {
  it("offers every operational zone as a linkable route", () => {
    const html = renderToString(<App />);

    expect(consoleZones.map((zone) => zone.label)).toEqual([
      "Import review",
      "Execution",
      "Progress review",
      "Problems",
      "Handover",
      "Mapping",
      "Export"
    ]);

    for (const zone of consoleZones) {
      expect(html).toContain(zone.label);
      expect(html).toContain(zoneHref(zone.id));
    }
  });

  it("opens on import review, because a shutdown starts with a schedule", () => {
    expect(parseZoneId("")).toBe("import-review");
    expect(parseZoneId("#/nonsense")).toBe("import-review");
    expect(parseZoneId("#/export")).toBe("export");
    expect(parseZoneId("export")).toBe("export");
    expect(zoneById("mapping").title).toBe("Operational categories");
  });

  it("says plainly when it is not configured rather than showing empty panels", () => {
    const html = renderToString(<App />);

    expect(html).toContain("No actor configured");
    expect(html).toContain("Not configured");
  });

  it("states that the server, not the role selector, decides what is permitted", () => {
    const html = renderToString(<App />);

    expect(html).toContain("The server checks your real membership on this project.");
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

describe("every zone renders", () => {
  // The router picks a component per route; a zone that throws on first render would be
  // invisible until someone navigated to it. Each is rendered here with an unconfigured
  // session, which is the state a zone must survive before any data arrives.
  const session = buildZoneSession(buildConsoleSession({}));
  const client = createConsoleApiClient(session, {
    fetchImpl: () => Promise.reject(new Error("no network in tests"))
  });

  const zones = [
    ["import-review", ImportReviewZone],
    ["execution", ExecutionZone],
    ["review-queue", ReviewQueueZone],
    ["problems", ProblemsZone],
    ["handover", HandoverZone],
    ["mapping", MappingZone],
    ["export", ExportZone]
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
});
