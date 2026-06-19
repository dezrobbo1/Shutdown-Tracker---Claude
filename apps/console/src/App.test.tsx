import { renderToString } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { createShutdownTrackerApiClient } from "@shutdown-tracker/api-client";
import { App } from "./App";
import {
  buildConsoleReviewConfig,
  loadConsoleReviewData,
  reviewApiConnection
} from "./apiReviewClient";
import {
  buildExportPreviewRows,
  buildExportPreviewSignals,
  buildReviewRows,
  consoleNavItems,
  exportPreviewSignals
} from "./consoleData";

describe("console scaffold", () => {
  it("renders the planned console navigation", () => {
    const html = renderToString(<App />);

    for (const item of consoleNavItems) {
      expect(html).toContain(item.label);
    }
  });

  it("renders export preview status signals", () => {
    const html = renderToString(<App />);

    for (const signal of exportPreviewSignals) {
      expect(html).toContain(signal.label);
      expect(html).toContain(signal.value);
    }
  });

  it("renders the wired import and export review API client operations", () => {
    const html = renderToString(<App />);

    expect(reviewApiConnection.operationCount).toBeGreaterThanOrEqual(10);
    expect(html).toContain("Import/export review operations");
    expect(html).toContain("List import snapshots");
    expect(html).toContain("Create export preview");
    expect(html).toContain("Wired operations");
  });

  it("does not surface schedule-authoring language", () => {
    const html = renderToString(<App />);
    const forbidden = [
      /critical path/i,
      /\bfloat\b/i,
      /resource levell?ing/i,
      /recovery scheduling/i,
      /automatic date movement/i,
      /schedule optimization/i
    ];

    for (const pattern of forbidden) {
      expect(html).not.toMatch(pattern);
    }
  });
});

describe("console review data fetching", () => {
  it("stays synthetic until a project id is configured", async () => {
    const config = buildConsoleReviewConfig({
      VITE_SHUTDOWN_TRACKER_API_BASE_URL: " http://localhost:8080 ",
      VITE_SHUTDOWN_TRACKER_PROJECT_ID: " "
    });

    const data = await loadConsoleReviewData(config);

    expect(config.liveEnabled).toBe(false);
    expect(data.mode).toBe("synthetic");
    expect(data.snapshots).toEqual([]);
  });

  it("loads import snapshots, selected snapshot detail, and optional export preview", async () => {
    const calls: string[] = [];
    const client = createShutdownTrackerApiClient({
      fetchImpl: async (input) => {
        calls.push(input);

        if (input.endsWith("/import-review/snapshots")) {
          return jsonResponse([
            {
              id: "snapshot-a",
              projectId: "project-a",
              importBatchId: "batch-a",
              status: "PARSED",
              externalProjectUid: null,
              externalProjectName: "Synthetic Basic WBS",
              projectStatusDate: null,
              snapshotVersion: 1,
              parserName: "mpxj",
              parserVersion: "13",
              warningCount: 0,
              errorCount: 0,
              taskCount: 6,
              summaryTaskCount: 2,
              leafTaskCount: 4,
              resourceCount: 0,
              assignmentCount: 0,
              extendedAttributeCount: 0
            }
          ]);
        }

        if (input.endsWith("/import-review/snapshots/snapshot-a")) {
          return jsonResponse({
            snapshot: {
              id: "snapshot-a",
              projectId: "project-a",
              importBatchId: "batch-a",
              status: "PARSED",
              externalProjectUid: null,
              externalProjectName: "Synthetic Basic WBS",
              projectStatusDate: null,
              snapshotVersion: 1,
              parserName: "mpxj",
              parserVersion: "13",
              warningCount: 0,
              errorCount: 0,
              taskCount: 6,
              summaryTaskCount: 2,
              leafTaskCount: 4,
              resourceCount: 0,
              assignmentCount: 0,
              extendedAttributeCount: 0
            },
            tasks: [
              {
                id: "task-a1",
                externalUid: "1",
                externalId: "1",
                name: "Synthetic Task A1",
                wbs: "1.1",
                outlineNumber: "1.1",
                outlineLevel: 2,
                summary: false,
                parentExternalUid: null,
                parentImportedTaskId: null,
                plannedStart: null,
                plannedFinish: null,
                actualStart: null,
                actualFinish: null,
                percentComplete: null,
                physicalPercentComplete: null,
                notes: null
              }
            ],
            resources: [],
            assignments: [],
            extendedAttributes: []
          });
        }

        return jsonResponse({
          batch: {
            id: "export-batch-a",
            projectId: "project-a",
            projectSnapshotId: "snapshot-a",
            status: "DRAFT_PREVIEW",
            previewCreatedAt: null,
            approvedAt: null,
            approvedByUserId: null,
            generatedAt: null,
            generatedByUserId: null,
            verifiedAt: null,
            verifiedByUserId: null,
            exportFileUri: null,
            exportFileHash: null,
            failureReason: null,
            lineCount: 1,
            eligibleLineCount: 1,
            ineligibleLineCount: 0
          },
          lines: [
            {
              id: "line-a",
              exportBatchId: "export-batch-a",
              projectId: "project-a",
              projectSnapshotId: "snapshot-a",
              importedTaskId: "task-a1",
              importedTaskExternalUid: "1",
              importedTaskExternalId: "1",
              importedTaskName: "Synthetic Task A1",
              sourceEntityType: "TASK_PROGRESS",
              sourceEntityId: "progress-a",
              approvalState: "APPROVED_FOR_EXPORT",
              fieldName: "percent_complete",
              oldValue: "0",
              newValue: "50",
              sourceActorUserId: null,
              sourceTimestamp: null,
              reason: null,
              leafTask: true,
              exportEligible: true
            }
          ],
          message: "Synthetic preview"
        });
      }
    });

    const data = await loadConsoleReviewData(
      {
        baseUrl: "",
        projectId: "project-a",
        importSnapshotId: "",
        exportBatchId: "export-batch-a",
        liveEnabled: true
      },
      client
    );

    expect(calls).toEqual([
      "/api/projects/project-a/import-review/snapshots",
      "/api/projects/project-a/import-review/snapshots/snapshot-a",
      "/api/projects/project-a/export-preview/export-batch-a"
    ]);
    expect(data.mode).toBe("live");
    expect(data.selectedSnapshotId).toBe("snapshot-a");
    expect(buildReviewRows(data).map((row) => row.item)).toContain("Synthetic Task A1");
    expect(buildExportPreviewRows(data)[0]).toMatchObject({
      field: "percent_complete",
      candidate: "Synthetic Task A1",
      eligibility: "Eligible leaf update"
    });
    expect(buildExportPreviewSignals(data)).toContainEqual({
      label: "Lines",
      value: "1/1 eligible"
    });
  });

  it("does not create write endpoints for live console data loading", async () => {
    const methods: string[] = [];
    const client = createShutdownTrackerApiClient({
      fetchImpl: async (_input, init) => {
        methods.push(init?.method ?? "GET");
        return jsonResponse([]);
      }
    });

    await loadConsoleReviewData(
      {
        baseUrl: "",
        projectId: "project-a",
        importSnapshotId: "",
        exportBatchId: "",
        liveEnabled: true
      },
      client
    );

    expect(methods).toEqual(["GET"]);
  });
});

function jsonResponse(payload: unknown) {
  return new Response(JSON.stringify(payload), {
    status: 200,
    headers: {
      "Content-Type": "application/json"
    }
  });
}
