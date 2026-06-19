import {
  createShutdownTrackerApiClient,
  shutdownTrackerReviewApiSurfaces
} from "@shutdown-tracker/api-client";

const configuredBaseUrl = import.meta.env.VITE_SHUTDOWN_TRACKER_API_BASE_URL ?? "";

export const reviewApiClient = createShutdownTrackerApiClient({
  baseUrl: configuredBaseUrl
});

export const reviewApiConnection = {
  baseUrlLabel: configuredBaseUrl || "Relative API",
  operationCount: shutdownTrackerReviewApiSurfaces.length,
  highlightedSurfaces: shutdownTrackerReviewApiSurfaces.filter((surface) =>
    [
      "List import snapshots",
      "Create lineage link",
      "Create export preview",
      "Approve export batch",
      "Record generated artifact"
    ].includes(surface.label)
  ),
  surfaces: shutdownTrackerReviewApiSurfaces
};
