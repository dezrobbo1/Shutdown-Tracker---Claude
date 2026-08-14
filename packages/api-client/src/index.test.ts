import { describe, expect, it } from "vitest";
import {
  ShutdownTrackerApiError,
  createShutdownTrackerApiClient,
  shutdownTrackerReviewApiSurfaces
} from "./index";

describe("shutdown tracker api client", () => {
  it("requests import review snapshots with encoded project ids", async () => {
    const calls: CapturedRequest[] = [];
    const client = createShutdownTrackerApiClient({
      baseUrl: "https://api.example.test/",
      fetchImpl: captureFetch(calls, [])
    });

    await client.importReview.listSnapshots("project 1");

    expect(calls).toEqual([
      {
        input: "https://api.example.test/api/projects/project%201/import-review/snapshots",
        method: "GET",
        body: undefined
      }
    ]);
  });

  it("adds lineage query parameters", async () => {
    const calls: CapturedRequest[] = [];
    const client = createShutdownTrackerApiClient({
      fetchImpl: captureFetch(calls, [])
    });

    await client.taskLineage.listBySnapshotPair("project-a", "previous snap", "current snap");

    expect(calls[0].input).toBe(
      "/api/projects/project-a/import-review/lineage-links?previousSnapshotId=previous+snap&currentSnapshotId=current+snap"
    );
  });

  it("posts export preview lifecycle requests as json", async () => {
    const calls: CapturedRequest[] = [];
    const client = createShutdownTrackerApiClient({
      fetchImpl: captureFetch(calls, { batch: { status: "APPROVED" }, lines: [], message: "ok" })
    });

    await client.exportPreview.approve("project-a", "batch-a", {
      reason: "Synthetic approval"
    });
    await client.exportPreview.markGenerated("project-a", "batch-a", {
      exportFileUri: "object://synthetic/export.mspdi.xml",
      exportFileHash: "sha256:synthetic"
    });
    await client.exportPreview.markOpenedInMicrosoftProject("project-a", "batch-a", {
      reason: "Synthetic Microsoft Project reopen"
    });
    await client.exportPreview.verify("project-a", "batch-a", {
      reason: "Synthetic manual verification complete"
    });
    await client.exportPreview.generateArtifact("project-a", "batch-a", {
      reason: "Synthetic worker generation"
    });

    expect(calls.map((call) => call.input)).toEqual([
      "/api/projects/project-a/export-preview/batch-a/approve",
      "/api/projects/project-a/export-preview/batch-a/mark-generated",
      "/api/projects/project-a/export-preview/batch-a/mark-opened-in-microsoft-project",
      "/api/projects/project-a/export-preview/batch-a/verify",
      "/api/projects/project-a/export-preview/batch-a/generate-artifact"
    ]);
    expect(calls[0].body).toBe(JSON.stringify({ reason: "Synthetic approval" }));
    expect(calls[1].body).toBe(
      JSON.stringify({
        exportFileUri: "object://synthetic/export.mspdi.xml",
        exportFileHash: "sha256:synthetic"
      })
    );
    expect(calls[2].body).toBe(
      JSON.stringify({ reason: "Synthetic Microsoft Project reopen" })
    );
    expect(calls[3].body).toBe(
      JSON.stringify({ reason: "Synthetic manual verification complete" })
    );
    expect(calls[4].body).toBe(JSON.stringify({ reason: "Synthetic worker generation" }));
  });

  it("sends configured actor headers on every request", async () => {
    let sentHeaders: Record<string, string> = {};
    let sentBody: BodyInit | null | undefined;
    const client = createShutdownTrackerApiClient({
      headers: { "X-Shutdown-Tracker-Actor-Id": "00000000-0000-0000-0000-0000000000a1" },
      fetchImpl: async (_input: string, init?: RequestInit) => {
        sentHeaders = (init?.headers ?? {}) as Record<string, string>;
        sentBody = init?.body;
        return new Response(JSON.stringify({ batch: {}, lines: [], message: "ok" }), {
          status: 200,
          headers: { "Content-Type": "application/json" }
        });
      }
    });

    await client.exportPreview.approve("project-a", "batch-a", { reason: "Synthetic approval" });

    expect(sentHeaders["X-Shutdown-Tracker-Actor-Id"]).toBe("00000000-0000-0000-0000-0000000000a1");
    // Actor identity travels in headers only; it must never appear in the request body.
    expect(sentBody).toBe(JSON.stringify({ reason: "Synthetic approval" }));
  });

  it("uploads source files as multipart form data", async () => {
    const calls: CapturedRequest[] = [];
    const client = createShutdownTrackerApiClient({
      fetchImpl: captureFetch(calls, {
        accepted: true,
        sourceFile: { id: "source-file-a" },
        importBatch: { id: "import-batch-a", status: "PENDING" },
        message: "stored"
      })
    });

    await client.sourceFiles.upload(
      "project a",
      new Blob(["synthetic"], { type: "application/xml" }),
      "synthetic-basic-wbs.mspdi.xml"
    );

    expect(calls[0].input).toBe("/api/projects/project%20a/source-files");
    expect(calls[0].method).toBe("POST");
    expect(calls[0].body).toBeInstanceOf(FormData);
  });

  it("requests import batch parse handoff", async () => {
    const calls: CapturedRequest[] = [];
    const client = createShutdownTrackerApiClient({
      fetchImpl: captureFetch(calls, {
        importBatch: { id: "batch-a", status: "PARSED" },
        parseSummary: { parserName: "mpxj" },
        message: "recorded"
      })
    });

    await client.importBatches.requestParseSummary("project a", "batch a");

    expect(calls[0].input).toBe(
      "/api/projects/project%20a/import-batches/batch%20a/request-parse-summary"
    );
    expect(calls[0].method).toBe("POST");
    expect(calls[0].body).toBeUndefined();
  });

  it("throws a typed error for non-successful responses", async () => {
    const client = createShutdownTrackerApiClient({
      fetchImpl: async () => new Response("conflict", { status: 409 })
    });

    await expect(client.importReview.acceptSnapshot("project-a", "snapshot-a")).rejects.toMatchObject({
      name: "ShutdownTrackerApiError",
      status: 409,
      responseBody: "conflict"
    } satisfies Partial<ShutdownTrackerApiError>);
  });

  it("submits task progress without asserting who submitted it", async () => {
    const calls: CapturedRequest[] = [];
    const client = createShutdownTrackerApiClient({
      baseUrl: "https://example.test",
      fetchImpl: captureFetch(calls, {}),
      headers: { "X-Shutdown-Tracker-Actor-Id": "actor-1" }
    });

    await client.taskProgress.submit("project-1", {
      importedTaskId: "task-1",
      executionState: "IN_PROGRESS",
      percentComplete: 50,
      idempotencyKey: "device-key-1"
    });

    expect(calls[0].input).toBe("https://example.test/api/projects/project-1/task-progress");
    expect(calls[0].method).toBe("POST");
    // The server resolves the actor from the request; the body must not carry a user id.
    expect(String(calls[0].body)).not.toContain("UserId");
    expect(String(calls[0].body)).toContain("device-key-1");
  });

  it("routes supervisor and planner review to separate endpoints", async () => {
    const calls: CapturedRequest[] = [];
    const client = createShutdownTrackerApiClient({
      baseUrl: "https://example.test",
      fetchImpl: captureFetch(calls, {})
    });

    await client.taskProgress.supervisorReview("p1", "u1", { decision: "SUPERVISOR_ACCEPTED" });
    await client.taskProgress.plannerReview("p1", "u1", { approved: true });

    expect(calls[0].input).toBe("https://example.test/api/projects/p1/task-progress/u1/supervisor-review");
    expect(calls[1].input).toBe("https://example.test/api/projects/p1/task-progress/u1/planner-review");
  });

  it("exposes problems, actions, evidence, and handover", async () => {
    const calls: CapturedRequest[] = [];
    const client = createShutdownTrackerApiClient({
      baseUrl: "https://example.test",
      fetchImpl: captureFetch(calls, {})
    });

    await client.problems.raise("p1", { title: "Scaffold missing", blocksExecution: true });
    await client.actions.create("p1", { title: "Order scaffold" });
    await client.evidence.register("p1", { importedTaskId: "t1", originalFilename: "photo.jpg" });
    await client.handover.create("p1", {
      shiftLabel: "Night",
      note: "Valve left isolated.",
      requiresAcknowledgement: true
    });

    expect(calls.map((call) => call.input)).toEqual([
      "https://example.test/api/projects/p1/problems",
      "https://example.test/api/projects/p1/actions",
      "https://example.test/api/projects/p1/evidence",
      "https://example.test/api/projects/p1/handover-notes"
    ]);
  });

  it("configures operational categories and resolves a snapshot", async () => {
    const calls: CapturedRequest[] = [];
    const client = createShutdownTrackerApiClient({
      baseUrl: "https://example.test",
      fetchImpl: captureFetch(calls, [])
    });

    await client.importProfiles.addCategory("p1", "profile-1", {
      name: "Work Group",
      sourceMode: "TASK_FIELD",
      sourceField: "Work Group",
      multiValued: false,
      requiredForExecution: true
    });
    await client.importProfiles.resolveSnapshot("p1", "snapshot-1");

    expect(calls[0].input).toBe("https://example.test/api/projects/p1/import-profiles/profile-1/categories");
    expect(calls[1].input).toBe("https://example.test/api/projects/p1/import-profiles/resolve/snapshot-1");
    expect(calls[1].method).toBe("POST");
  });

  it("describes the import and export review API surface", () => {
    expect(shutdownTrackerReviewApiSurfaces.map((surface) => surface.label)).toEqual(
      expect.arrayContaining([
        "List import snapshots",
        "Upload source file",
        "Request import batch parse summary",
        "Create lineage link",
        "Create export preview",
        "Approve export batch",
        "Record generated artifact",
        "Record Project reopen",
        "Verify export artifact",
        "Generate export artifact"
      ])
    );
  });
});

type CapturedRequest = {
  input: string;
  method: string;
  body: BodyInit | null | undefined;
};

function captureFetch(calls: CapturedRequest[], payload: unknown) {
  return async (input: string, init?: RequestInit) => {
    calls.push({
      input,
      method: init?.method ?? "GET",
      body: init?.body
    });

    return new Response(JSON.stringify(payload), {
      status: 200,
      headers: {
        "Content-Type": "application/json"
      }
    });
  };
}
