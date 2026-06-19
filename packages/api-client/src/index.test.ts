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
      JSON.stringify({
        openedByUserId: "user-a",
        reason: "Synthetic Microsoft Project reopen"
      })
    );
    expect(calls[3].body).toBe(
      JSON.stringify({
        verifiedByUserId: "user-b",
        reason: "Synthetic manual verification complete"
      })
    );
    expect(calls[4].body).toBe(JSON.stringify({ reason: "Synthetic worker generation" }));
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
