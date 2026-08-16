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
    await client.exportPreview.markOpenedInMicrosoftProject("project-a", "batch-a", {
      openedByUserId: "user-a",
      reason: "Synthetic Microsoft Project reopen"
    });
    await client.exportPreview.verify("project-a", "batch-a", {
      verifiedByUserId: "user-b",
      reason: "Synthetic manual verification complete"
    });
    await client.exportPreview.generateArtifact("project-a", "batch-a", {
      reason: "Synthetic worker generation"
    });

    expect(calls.map((call) => call.input)).toEqual([
      "/api/projects/project-a/export-preview/batch-a/approve",
      "/api/projects/project-a/export-preview/batch-a/mark-opened-in-microsoft-project",
      "/api/projects/project-a/export-preview/batch-a/verify",
      "/api/projects/project-a/export-preview/batch-a/generate-artifact"
    ]);
    expect(calls[0].body).toBe(JSON.stringify({ reason: "Synthetic approval" }));
    expect(calls[1].body).toBe(
      JSON.stringify({
        openedByUserId: "user-a",
        reason: "Synthetic Microsoft Project reopen"
      })
    );
    expect(calls[2].body).toBe(
      JSON.stringify({
        verifiedByUserId: "user-b",
        reason: "Synthetic manual verification complete"
      })
    );
    expect(calls[3].body).toBe(JSON.stringify({ reason: "Synthetic worker generation" }));
  });

  it("creates an authoritative candidate without caller-authored baseline, task identity, or fingerprint", async () => {
    const calls: CapturedRequest[] = [];
    const client = createShutdownTrackerApiClient({
      fetchImpl: captureFetch(calls, { id: "candidate-a", bindingPolicyVersion: 1 })
    });

    await client.exportCandidates.create("project a", {
      projectSnapshotId: "snapshot-a",
      importedTaskId: "task-a",
      fieldName: "actual_start",
      proposedValue: "2026-07-19T16:00:00+08:00",
      sourceEntityType: "task_update",
      sourceEntityId: "source-a",
      sourceVersion: "source-version-7",
      sourceActorUserId: "user-a",
      sourceTimestamp: "2026-07-19T15:55:00+08:00",
      reason: "Synthetic reviewed start",
      metadata: { fixture: "synthetic" }
    });

    expect(calls).toEqual([
      {
        input: "/api/projects/project%20a/export-candidates",
        method: "POST",
        body: JSON.stringify({
          projectSnapshotId: "snapshot-a",
          importedTaskId: "task-a",
          fieldName: "actual_start",
          proposedValue: "2026-07-19T16:00:00+08:00",
          sourceEntityType: "task_update",
          sourceEntityId: "source-a",
          sourceVersion: "source-version-7",
          sourceActorUserId: "user-a",
          sourceTimestamp: "2026-07-19T15:55:00+08:00",
          reason: "Synthetic reviewed start",
          metadata: { fixture: "synthetic" }
        })
      }
    ]);
    expect(calls[0].body).not.toContain("sourceEventOrPayloadHash");
    expect(calls[0].body).not.toContain("normalizedOldValue");
    expect(calls[0].body).not.toContain("capturedTaskExternalUid");
    expect(calls[0].body).not.toContain("approvalState");
  });

  it("records candidate approval as a separate event", async () => {
    const calls: CapturedRequest[] = [];
    const client = createShutdownTrackerApiClient({
      fetchImpl: captureFetch(calls, { id: "approval-a", approvalState: "APPROVED_FOR_EXPORT" })
    });

    await client.exportCandidates.createApprovalEvent("project-a", "candidate a", {
      approvalState: "APPROVED_FOR_EXPORT",
      reviewedByUserId: "planner-a",
      reviewedAt: "2026-07-19T16:05:00+08:00",
      reason: "Synthetic planner approval"
    });

    expect(calls).toEqual([
      {
        input: "/api/projects/project-a/export-candidates/candidate%20a/approval-events",
        method: "POST",
        body: JSON.stringify({
          approvalState: "APPROVED_FOR_EXPORT",
          reviewedByUserId: "planner-a",
          reviewedAt: "2026-07-19T16:05:00+08:00",
          reason: "Synthetic planner approval"
        })
      }
    ]);
  });

  it("creates an export preview from authoritative candidate ids only", async () => {
    const calls: CapturedRequest[] = [];
    const client = createShutdownTrackerApiClient({
      fetchImpl: captureFetch(calls, { batch: { status: "DRAFT_PREVIEW" }, lines: [], message: "ok" })
    });

    await client.exportPreview.create("project-a", {
      projectSnapshotId: "snapshot-a",
      candidateIds: ["candidate-a"],
      metadata: { source: "synthetic-test" }
    });

    expect(calls).toEqual([
      {
        input: "/api/projects/project-a/export-preview",
        method: "POST",
        body: JSON.stringify({
          projectSnapshotId: "snapshot-a",
          candidateIds: ["candidate-a"],
          metadata: { source: "synthetic-test" }
        })
      }
    ]);
  });

  it("rejects runtime caller-authored export authority before making a request", () => {
    const calls: CapturedRequest[] = [];
    const client = createShutdownTrackerApiClient({
      fetchImpl: captureFetch(calls, {})
    });

    const candidateRequest = {
      projectSnapshotId: "snapshot-a",
      importedTaskId: "task-a",
      fieldName: "actual_start",
      proposedValue: "2026-07-19T16:00:00+08:00",
      sourceEntityType: "task_update",
      sourceEntityId: "source-a",
      sourceVersion: "source-version-7",
      normalizedOldValue: "caller-baseline",
      sourceEventOrPayloadHash: "caller-fingerprint",
      approvalState: "APPROVED_FOR_EXPORT"
    } as Parameters<typeof client.exportCandidates.create>[1];
    const approvalRequest = {
      approvalState: "APPROVED_FOR_EXPORT",
      authoritativeExportCandidateId: "different-candidate",
      candidateBindingPolicyVersion: 999
    } as Parameters<typeof client.exportCandidates.createApprovalEvent>[2];
    const previewRequest = {
      projectSnapshotId: "snapshot-a",
      candidateIds: ["candidate-a"],
      lines: [{ importedTaskId: "task-a", fieldName: "actual_start", newValue: "caller-value" }]
    } as Parameters<typeof client.exportPreview.create>[1];

    expect(() => client.exportCandidates.create("project-a", candidateRequest)).toThrow(
      "Export candidate request contains unsupported field(s): approvalState, normalizedOldValue, sourceEventOrPayloadHash."
    );
    expect(() => client.exportCandidates.createApprovalEvent("project-a", "candidate-a", approvalRequest)).toThrow(
      "Export candidate approval request contains unsupported field(s): authoritativeExportCandidateId, candidateBindingPolicyVersion."
    );
    expect(() => client.exportPreview.create("project-a", previewRequest)).toThrow(
      "Export preview request contains unsupported field(s): lines."
    );
    expect(calls).toEqual([]);
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
        "Create export candidate",
        "Record export candidate approval event",
        "Create export preview",
        "Approve export batch",
        "Record Project reopen",
        "Verify export artifact",
        "Generate export artifact"
      ])
    );
    expect(shutdownTrackerReviewApiSurfaces.map((surface) => surface.path)).not.toContain(
      "/api/projects/{projectId}/export-preview/{exportBatchId}/mark-generated"
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
