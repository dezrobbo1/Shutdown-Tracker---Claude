import {
  createShutdownTrackerApiClient,
  shutdownTrackerReviewApiSurfaces
} from "@shutdown-tracker/api-client";
import type {
  ExportPreviewDetail,
  ImportReviewSnapshotDetail,
  ImportReviewSnapshotSummary
} from "@shutdown-tracker/api-client";

type ConsoleReviewEnv = Record<string, unknown>;

export type ConsoleReviewRuntimeConfig = {
  baseUrl: string;
  projectId: string;
  importSnapshotId: string;
  exportBatchId: string;
  liveEnabled: boolean;
};

export type ConsoleReviewData = {
  mode: "synthetic" | "live";
  projectId: string | null;
  snapshots: ImportReviewSnapshotSummary[];
  selectedSnapshotId: string | null;
  snapshotDetail: ImportReviewSnapshotDetail | null;
  exportBatchId: string | null;
  exportPreview: ExportPreviewDetail | null;
  message: string;
};

export type ConsoleReviewLoadState = {
  status: "synthetic" | "loading" | "loaded" | "error";
  message: string;
};

export type ShutdownTrackerReviewApiClient = ReturnType<typeof createShutdownTrackerApiClient>;

export const reviewApiRuntimeConfig = buildConsoleReviewConfig(import.meta.env);

export const reviewApiClient = createShutdownTrackerApiClient({
  baseUrl: reviewApiRuntimeConfig.baseUrl
});

export const reviewApiConnection = {
  baseUrlLabel: reviewApiRuntimeConfig.baseUrl || "Relative API",
  projectIdLabel: reviewApiRuntimeConfig.projectId || "No project configured",
  modeLabel: reviewApiRuntimeConfig.liveEnabled ? "Live review data" : "Synthetic review data",
  operationCount: shutdownTrackerReviewApiSurfaces.length,
  highlightedSurfaces: shutdownTrackerReviewApiSurfaces.filter((surface) =>
    [
      "List import snapshots",
      "Create lineage link",
      "Create export preview",
      "Approve export batch",
      "Generate export artifact"
    ].includes(surface.label)
  ),
  surfaces: shutdownTrackerReviewApiSurfaces
};

export function buildConsoleReviewConfig(env: ConsoleReviewEnv): ConsoleReviewRuntimeConfig {
  const baseUrl = cleanEnvValue(env.VITE_SHUTDOWN_TRACKER_API_BASE_URL);
  const projectId = cleanEnvValue(env.VITE_SHUTDOWN_TRACKER_PROJECT_ID);
  const importSnapshotId = cleanEnvValue(env.VITE_SHUTDOWN_TRACKER_IMPORT_SNAPSHOT_ID);
  const exportBatchId = cleanEnvValue(env.VITE_SHUTDOWN_TRACKER_EXPORT_BATCH_ID);

  return {
    baseUrl,
    projectId,
    importSnapshotId,
    exportBatchId,
    liveEnabled: projectId.length > 0
  };
}

export function initialConsoleReviewLoadState(config = reviewApiRuntimeConfig): ConsoleReviewLoadState {
  if (config.liveEnabled) {
    return {
      status: "loading",
      message: "Fetching import/export review data."
    };
  }

  return {
    status: "synthetic",
    message: "Synthetic review data. Set a project id to fetch live review data."
  };
}

export async function loadConsoleReviewData(
  config = reviewApiRuntimeConfig,
  client: ShutdownTrackerReviewApiClient = reviewApiClient
): Promise<ConsoleReviewData> {
  if (!config.liveEnabled) {
    return {
      mode: "synthetic",
      projectId: null,
      snapshots: [],
      selectedSnapshotId: null,
      snapshotDetail: null,
      exportBatchId: null,
      exportPreview: null,
      message: "Synthetic review data. Set VITE_SHUTDOWN_TRACKER_PROJECT_ID to fetch live review data."
    };
  }

  const snapshots = await client.importReview.listSnapshots(config.projectId);
  const selectedSnapshotId = selectSnapshotId(snapshots, config.importSnapshotId);

  const [snapshotDetail, exportPreview] = await Promise.all([
    selectedSnapshotId === null
      ? Promise.resolve(null)
      : client.importReview.getSnapshot(config.projectId, selectedSnapshotId),
    config.exportBatchId.length === 0
      ? Promise.resolve(null)
      : client.exportPreview.get(config.projectId, config.exportBatchId)
  ]);

  return {
    mode: "live",
    projectId: config.projectId,
    snapshots,
    selectedSnapshotId,
    snapshotDetail,
    exportBatchId: config.exportBatchId || null,
    exportPreview,
    message: buildLoadedMessage(snapshots, exportPreview)
  };
}

export function formatConsoleReviewError(error: unknown) {
  if (error instanceof Error) {
    return `Review data could not be loaded: ${error.message}`;
  }

  return "Review data could not be loaded.";
}

function selectSnapshotId(snapshots: ImportReviewSnapshotSummary[], configuredSnapshotId: string) {
  if (configuredSnapshotId.length > 0) {
    return configuredSnapshotId;
  }

  return [...snapshots].sort((left, right) => right.snapshotVersion - left.snapshotVersion)[0]?.id ?? null;
}

function buildLoadedMessage(snapshots: ImportReviewSnapshotSummary[], exportPreview: ExportPreviewDetail | null) {
  const snapshotLabel = snapshots.length === 1 ? "snapshot" : "snapshots";
  const exportLabel = exportPreview === null ? "no export batch loaded" : "export batch loaded";

  return `${snapshots.length} import ${snapshotLabel}; ${exportLabel}.`;
}

function cleanEnvValue(value: unknown) {
  return typeof value === "string" ? value.trim() : "";
}
