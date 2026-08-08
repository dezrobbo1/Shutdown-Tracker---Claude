export type JsonObject = Record<string, unknown>;

export type ProjectSnapshotStatus = "PARSED" | "ACCEPTED" | "REJECTED" | "SUPERSEDED" | "FAILED";
export type TaskLineageReviewState = "SUGGESTED" | "ACCEPTED" | "REJECTED" | "SUPERSEDED";
export type ApprovalState =
  | "DRAFT"
  | "SUBMITTED"
  | "AWAITING_REVIEW"
  | "CORRECTION_REQUESTED"
  | "APPROVED_FOR_EXPORT"
  | "REJECTED"
  | "SUPERSEDED"
  | "EXPORTED";
export type ExportBatchState =
  | "DRAFT_PREVIEW"
  | "AWAITING_APPROVAL"
  | "APPROVED"
  | "REJECTED"
  | "GENERATED"
  | "OPENED_IN_MICROSOFT_PROJECT"
  | "VERIFIED"
  | "SUPERSEDED"
  | "FAILED";
export type SourceFileKind = "MPP" | "MSPDI_XML" | "XML" | "OTHER";
export type ImportBatchStatus = "PENDING" | "PARSING" | "PARSED" | "ACCEPTED" | "FAILED" | "SUPERSEDED";

export type SourceFileMetadataRecord = {
  id: string;
  projectId: string;
  originalFilename: string;
  fileKind: SourceFileKind;
  storageUri: string;
  contentHash: string;
  sizeBytes: number;
};

export type ImportBatchRecord = {
  id: string;
  projectId: string;
  sourceFileId: string;
  status: ImportBatchStatus;
  parserName: string | null;
  parserVersion: string | null;
  warningCount: number;
  errorCount: number;
};

export type ProjectParseSummaryResponse = {
  importBatchId: string;
  parserName: string;
  parserVersion: string;
  sourceFilename: string;
  detectedFormat: string;
  projectName: string;
  taskCount: number;
  summaryTaskCount: number;
  leafTaskCount: number;
  resourceCount: number;
  assignmentCount: number;
  calendarCount: number;
  customFieldCount: number;
  warningCount: number;
  errorCount: number;
  notes: string[];
};

export type ImportBatchParseHandoffResponse = {
  importBatch: ImportBatchRecord;
  parseSummary: ProjectParseSummaryResponse;
  message: string;
};

export type SourceFileUploadResponse = {
  originalFilename: string | null;
  sizeBytes: number;
  detectedExtension: string;
  accepted: boolean;
  rejectionReason: string | null;
  sourceFile: SourceFileMetadataRecord | null;
  importBatch: ImportBatchRecord | null;
  message: string;
};

export type ImportReviewSnapshotSummary = {
  id: string;
  projectId: string;
  importBatchId: string;
  status: ProjectSnapshotStatus;
  externalProjectUid: string | null;
  externalProjectName: string | null;
  projectStatusDate: string | null;
  snapshotVersion: number;
  parserName: string | null;
  parserVersion: string | null;
  warningCount: number;
  errorCount: number;
  taskCount: number;
  summaryTaskCount: number;
  leafTaskCount: number;
  resourceCount: number;
  assignmentCount: number;
  extendedAttributeCount: number;
};

export type ImportReviewTaskRow = {
  id: string;
  externalUid: string | null;
  externalId: string | null;
  name: string | null;
  wbs: string | null;
  outlineNumber: string | null;
  outlineLevel: number | null;
  summary: boolean;
  parentExternalUid: string | null;
  parentImportedTaskId: string | null;
  plannedStart: string | null;
  plannedFinish: string | null;
  actualStart: string | null;
  actualFinish: string | null;
  percentComplete: number | null;
  physicalPercentComplete: number | null;
  notes: string | null;
};

export type ImportReviewResourceRow = {
  id: string;
  externalUid: string | null;
  name: string | null;
  resourceType: string | null;
};

export type ImportReviewAssignmentRow = {
  id: string;
  externalUid: string | null;
  taskExternalUid: string | null;
  resourceExternalUid: string | null;
  importedTaskId: string | null;
  importedResourceId: string | null;
};

export type ImportReviewExtendedAttributeRow = {
  id: string;
  entityType: string;
  entityExternalUid: string | null;
  fieldId: string | null;
  fieldName: string | null;
  alias: string | null;
  value: string | null;
};

export type ImportReviewSnapshotDetail = {
  snapshot: ImportReviewSnapshotSummary;
  tasks: ImportReviewTaskRow[];
  resources: ImportReviewResourceRow[];
  assignments: ImportReviewAssignmentRow[];
  extendedAttributes: ImportReviewExtendedAttributeRow[];
};

export type ImportReviewDecisionResponse = {
  snapshot: ImportReviewSnapshotSummary;
  message: string;
};

export type ExportCandidateFieldName =
  | "percent_complete"
  | "physical_percent_complete"
  | "actual_start"
  | "actual_finish";

export type ExportCandidateCreateRequest = {
  projectSnapshotId: string;
  importedTaskId: string;
  fieldName: ExportCandidateFieldName;
  proposedValue: string;
  sourceEntityType: string;
  sourceEntityId: string;
  sourceVersion: string;
  sourceActorUserId?: string | null;
  sourceTimestamp?: string | null;
  reason?: string | null;
  metadata?: JsonObject | null;
};

export type ExportCandidateRecord = {
  id: string;
  bindingPolicyVersion: number;
  projectId: string;
  projectSnapshotId: string;
  importedTaskId: string;
  sourceEntityType: string;
  sourceEntityId: string;
  sourceVersion: string;
  fieldName: ExportCandidateFieldName;
  normalizedOldValue: string | null;
  normalizedNewValue: string;
  sourceEventOrPayloadHash: string;
  capturedTaskExternalUid: string;
  capturedTaskExternalId: string;
  capturedTaskName: string;
  capturedLeafTask: boolean;
  sourceActorUserId: string | null;
  sourceTimestamp: string | null;
  reason: string | null;
  createdAt: string;
  metadata: JsonObject;
};

export type ExportCandidateApprovalEventCreateRequest = {
  approvalState: ApprovalState;
  requestedAt?: string | null;
  reviewedByUserId?: string | null;
  reviewedAt?: string | null;
  reason?: string | null;
  metadata?: JsonObject | null;
};

export type ExportCandidateApprovalEventRecord = {
  id: string;
  projectId: string;
  projectSnapshotId: string;
  authoritativeExportCandidateId: string;
  candidateBindingPolicyVersion: number;
  approvalState: ApprovalState;
  requestedByUserId: string | null;
  requestedAt: string | null;
  reviewedByUserId: string | null;
  reviewedAt: string | null;
  reason: string | null;
  createdAt: string;
  metadata: JsonObject;
};

const exportCandidateCreateRequestFields = [
  "projectSnapshotId",
  "importedTaskId",
  "fieldName",
  "proposedValue",
  "sourceEntityType",
  "sourceEntityId",
  "sourceVersion",
  "sourceActorUserId",
  "sourceTimestamp",
  "reason",
  "metadata"
] as const;

const exportCandidateApprovalEventCreateRequestFields = [
  "approvalState",
  "requestedAt",
  "reviewedByUserId",
  "reviewedAt",
  "reason",
  "metadata"
] as const;

const exportPreviewCreateRequestFields = ["projectSnapshotId", "candidateIds", "metadata"] as const;

export type TaskLineageRecord = {
  id: string;
  projectId: string;
  previousSnapshotId: string;
  currentSnapshotId: string;
  previousImportedTaskId: string;
  previousTaskExternalUid: string | null;
  previousTaskName: string | null;
  currentImportedTaskId: string;
  currentTaskExternalUid: string | null;
  currentTaskName: string | null;
  matchMethod: string;
  matchConfidence: number | null;
  reviewState: TaskLineageReviewState;
  reviewedByUserId: string | null;
  reviewedAt: string | null;
};

export type TaskLineageCreateRequest = {
  previousSnapshotId: string;
  currentSnapshotId: string;
  previousImportedTaskId: string;
  currentImportedTaskId: string;
  matchMethod: string;
  matchConfidence?: number | null;
  metadata?: JsonObject | null;
};

export type TaskLineageDecisionResponse = {
  lineageLink: TaskLineageRecord;
  message: string;
};

export type ExportPreviewCreateRequest = {
  projectSnapshotId: string;
  candidateIds: string[];
  metadata?: JsonObject | null;
};

export type ExportPreviewBatchRecord = {
  id: string;
  projectId: string;
  projectSnapshotId: string;
  status: ExportBatchState;
  previewCreatedAt: string | null;
  approvedAt: string | null;
  approvedByUserId: string | null;
  generatedAt: string | null;
  generatedByUserId: string | null;
  verifiedAt: string | null;
  verifiedByUserId: string | null;
  exportFileUri: string | null;
  exportFileHash: string | null;
  failureReason: string | null;
  lineCount: number;
  eligibleLineCount: number;
  ineligibleLineCount: number;
  integrityPolicyVersion: number | null;
  lineSetSealed: boolean | null;
};

export type ExportPreviewLineRecord = {
  id: string;
  exportBatchId: string;
  projectId: string;
  projectSnapshotId: string;
  importedTaskId: string;
  importedTaskExternalUid: string | null;
  importedTaskExternalId: string | null;
  importedTaskName: string | null;
  sourceEntityType: string;
  sourceEntityId: string;
  approvalState: ApprovalState | null;
  sourceApprovalRecordId: string | null;
  fieldName: string;
  oldValue: string | null;
  newValue: string;
  sourceActorUserId: string | null;
  sourceTimestamp: string | null;
  reason: string | null;
  leafTask: boolean;
  exportEligible: boolean;
  integrityPolicyVersion: number | null;
  authoritativeExportCandidateId: string | null;
  capturedSourceVersion: string | null;
  capturedSourceEventOrPayloadHash: string | null;
};

export type ExportPreviewDetail = {
  batch: ExportPreviewBatchRecord;
  lines: ExportPreviewLineRecord[];
  message: string;
};

export type ExportBatchDecisionRequest = {
  reviewedByUserId?: string | null;
  reason?: string | null;
  metadata?: JsonObject | null;
};

export type ExportBatchProjectOpenRequest = {
  openedByUserId: string;
  reason?: string | null;
  metadata?: JsonObject | null;
};

export type ExportBatchVerificationRequest = {
  verifiedByUserId: string;
  reason?: string | null;
  metadata?: JsonObject | null;
};

export type ProjectExportArtifactSummary = {
  outputFilename: string;
  artifactFormat: string;
  taskCount: number;
  exportedFieldCount: number;
  sizeBytes: number;
  sha256: string;
  notes: string[];
};

export type ProjectExportArtifactGenerationResponse = {
  exportBatchId: string;
  projectId: string;
  exportFileUri: string;
  exportFileHash: string;
  artifactSummary: ProjectExportArtifactSummary;
  message: string;
};

export type ExportArtifactGenerationRequest = {
  generatedByUserId?: string | null;
  reason?: string | null;
  metadata?: JsonObject | null;
};

export type ExportArtifactGenerationResponse = {
  exportPreview: ExportPreviewDetail;
  workerResponse: ProjectExportArtifactGenerationResponse;
  message: string;
};

export type ReviewApiSurface = {
  label: string;
  method: "GET" | "POST";
  path: string;
};

export const shutdownTrackerReviewApiSurfaces: ReviewApiSurface[] = [
  { label: "Upload source file", method: "POST", path: "/api/projects/{projectId}/source-files" },
  {
    label: "Request import batch parse summary",
    method: "POST",
    path: "/api/projects/{projectId}/import-batches/{importBatchId}/request-parse-summary"
  },
  { label: "List import snapshots", method: "GET", path: "/api/projects/{projectId}/import-review/snapshots" },
  { label: "Read import snapshot", method: "GET", path: "/api/projects/{projectId}/import-review/snapshots/{snapshotId}" },
  { label: "Accept import snapshot", method: "POST", path: "/api/projects/{projectId}/import-review/snapshots/{snapshotId}/accept" },
  { label: "Reject import snapshot", method: "POST", path: "/api/projects/{projectId}/import-review/snapshots/{snapshotId}/reject" },
  { label: "List lineage links", method: "GET", path: "/api/projects/{projectId}/import-review/lineage-links" },
  { label: "Create lineage link", method: "POST", path: "/api/projects/{projectId}/import-review/lineage-links" },
  { label: "Create export candidate", method: "POST", path: "/api/projects/{projectId}/export-candidates" },
  {
    label: "Record export candidate approval event",
    method: "POST",
    path: "/api/projects/{projectId}/export-candidates/{candidateId}/approval-events"
  },
  { label: "Create export preview", method: "POST", path: "/api/projects/{projectId}/export-preview" },
  { label: "Read export preview", method: "GET", path: "/api/projects/{projectId}/export-preview/{exportBatchId}" },
  { label: "Approve export batch", method: "POST", path: "/api/projects/{projectId}/export-preview/{exportBatchId}/approve" },
  { label: "Reject export batch", method: "POST", path: "/api/projects/{projectId}/export-preview/{exportBatchId}/reject" },
  {
    label: "Record Project reopen",
    method: "POST",
    path: "/api/projects/{projectId}/export-preview/{exportBatchId}/mark-opened-in-microsoft-project"
  },
  { label: "Verify export artifact", method: "POST", path: "/api/projects/{projectId}/export-preview/{exportBatchId}/verify" },
  { label: "Generate export artifact", method: "POST", path: "/api/projects/{projectId}/export-preview/{exportBatchId}/generate-artifact" }
];

export type FetchLike = (input: string, init?: RequestInit) => Promise<Response>;

export type ShutdownTrackerApiClientOptions = {
  baseUrl?: string;
  fetchImpl?: FetchLike;
};

export class ShutdownTrackerApiError extends Error {
  readonly status: number;
  readonly responseBody: string;

  constructor(message: string, status: number, responseBody: string) {
    super(message);
    this.name = "ShutdownTrackerApiError";
    this.status = status;
    this.responseBody = responseBody;
  }
}

export function createShutdownTrackerApiClient(options: ShutdownTrackerApiClientOptions = {}) {
  const transport = options.fetchImpl ?? defaultFetch();
  const baseUrl = normalizeBaseUrl(options.baseUrl ?? "");

  return {
    sourceFiles: {
      upload: (projectId: string, file: Blob, filename?: string) => {
        const formData = new FormData();
        if (filename) {
          formData.append("file", file, filename);
        } else {
          formData.append("file", file);
        }
        return requestJson<SourceFileUploadResponse>(transport, baseUrl, sourceFilesPath(projectId), {
          method: "POST",
          formData
        });
      }
    },
    importBatches: {
      requestParseSummary: (projectId: string, importBatchId: string) =>
        requestJson<ImportBatchParseHandoffResponse>(
          transport,
          baseUrl,
          importBatchPath(projectId, importBatchId, "request-parse-summary"),
          { method: "POST" }
        )
    },
    importReview: {
      listSnapshots: (projectId: string) =>
        requestJson<ImportReviewSnapshotSummary[]>(transport, baseUrl, importReviewPath(projectId, "snapshots")),
      getSnapshot: (projectId: string, snapshotId: string) =>
        requestJson<ImportReviewSnapshotDetail>(transport, baseUrl, importReviewPath(projectId, `snapshots/${snapshotId}`)),
      acceptSnapshot: (projectId: string, snapshotId: string) =>
        requestJson<ImportReviewDecisionResponse>(
          transport,
          baseUrl,
          importReviewPath(projectId, `snapshots/${snapshotId}/accept`),
          { method: "POST" }
        ),
      rejectSnapshot: (projectId: string, snapshotId: string) =>
        requestJson<ImportReviewDecisionResponse>(
          transport,
          baseUrl,
          importReviewPath(projectId, `snapshots/${snapshotId}/reject`),
          { method: "POST" }
        )
    },
    taskLineage: {
      listBySnapshotPair: (projectId: string, previousSnapshotId: string, currentSnapshotId: string) =>
        requestJson<TaskLineageRecord[]>(
          transport,
          baseUrl,
          importReviewPath(projectId, "lineage-links"),
          { query: { previousSnapshotId, currentSnapshotId } }
        ),
      createSuggested: (projectId: string, request: TaskLineageCreateRequest) =>
        requestJson<TaskLineageRecord>(transport, baseUrl, importReviewPath(projectId, "lineage-links"), {
          method: "POST",
          body: request
        }),
      accept: (projectId: string, lineageLinkId: string) =>
        requestJson<TaskLineageDecisionResponse>(
          transport,
          baseUrl,
          importReviewPath(projectId, `lineage-links/${lineageLinkId}/accept`),
          { method: "POST" }
        ),
      reject: (projectId: string, lineageLinkId: string) =>
        requestJson<TaskLineageDecisionResponse>(
          transport,
          baseUrl,
          importReviewPath(projectId, `lineage-links/${lineageLinkId}/reject`),
          { method: "POST" }
        )
    },
    exportCandidates: {
      create: (projectId: string, request: ExportCandidateCreateRequest) => {
        assertOnlySupportedRequestFields("Export candidate request", request, exportCandidateCreateRequestFields);
        return requestJson<ExportCandidateRecord>(transport, baseUrl, exportCandidatesPath(projectId), {
          method: "POST",
          body: {
            projectSnapshotId: request.projectSnapshotId,
            importedTaskId: request.importedTaskId,
            fieldName: request.fieldName,
            proposedValue: request.proposedValue,
            sourceEntityType: request.sourceEntityType,
            sourceEntityId: request.sourceEntityId,
            sourceVersion: request.sourceVersion,
            sourceActorUserId: request.sourceActorUserId,
            sourceTimestamp: request.sourceTimestamp,
            reason: request.reason,
            metadata: request.metadata
          }
        });
      },
      createApprovalEvent: (
        projectId: string,
        candidateId: string,
        request: ExportCandidateApprovalEventCreateRequest
      ) => {
        assertOnlySupportedRequestFields(
          "Export candidate approval request",
          request,
          exportCandidateApprovalEventCreateRequestFields
        );
        return requestJson<ExportCandidateApprovalEventRecord>(
          transport,
          baseUrl,
          exportCandidatesPath(projectId, `${candidateId}/approval-events`),
          {
            method: "POST",
            body: {
              approvalState: request.approvalState,
              requestedAt: request.requestedAt,
              reviewedByUserId: request.reviewedByUserId,
              reviewedAt: request.reviewedAt,
              reason: request.reason,
              metadata: request.metadata
            }
          }
        );
      }
    },
    exportPreview: {
      create: (projectId: string, request: ExportPreviewCreateRequest) => {
        assertOnlySupportedRequestFields("Export preview request", request, exportPreviewCreateRequestFields);
        return requestJson<ExportPreviewDetail>(transport, baseUrl, exportPreviewPath(projectId), {
          method: "POST",
          body: {
            projectSnapshotId: request.projectSnapshotId,
            candidateIds: request.candidateIds,
            metadata: request.metadata
          }
        });
      },
      get: (projectId: string, exportBatchId: string) =>
        requestJson<ExportPreviewDetail>(transport, baseUrl, exportPreviewPath(projectId, exportBatchId)),
      approve: (projectId: string, exportBatchId: string, request?: ExportBatchDecisionRequest) =>
        requestJson<ExportPreviewDetail>(transport, baseUrl, exportPreviewPath(projectId, `${exportBatchId}/approve`), {
          method: "POST",
          body: request
        }),
      reject: (projectId: string, exportBatchId: string, request?: ExportBatchDecisionRequest) =>
        requestJson<ExportPreviewDetail>(transport, baseUrl, exportPreviewPath(projectId, `${exportBatchId}/reject`), {
          method: "POST",
          body: request
        }),
      markOpenedInMicrosoftProject: (projectId: string, exportBatchId: string, request: ExportBatchProjectOpenRequest) =>
        requestJson<ExportPreviewDetail>(
          transport,
          baseUrl,
          exportPreviewPath(projectId, `${exportBatchId}/mark-opened-in-microsoft-project`),
          { method: "POST", body: request }
        ),
      verify: (projectId: string, exportBatchId: string, request: ExportBatchVerificationRequest) =>
        requestJson<ExportPreviewDetail>(
          transport,
          baseUrl,
          exportPreviewPath(projectId, `${exportBatchId}/verify`),
          { method: "POST", body: request }
        ),
      generateArtifact: (projectId: string, exportBatchId: string, request?: ExportArtifactGenerationRequest) =>
        requestJson<ExportArtifactGenerationResponse>(
          transport,
          baseUrl,
          exportPreviewPath(projectId, `${exportBatchId}/generate-artifact`),
          { method: "POST", body: request ?? {} }
        )
    }
  };
}

type RequestOptions = {
  method?: "GET" | "POST";
  body?: unknown;
  formData?: FormData;
  query?: Record<string, string>;
};

async function requestJson<T>(
  fetchImpl: FetchLike,
  baseUrl: string,
  path: string,
  options: RequestOptions = {}
): Promise<T> {
  const headers: Record<string, string> = {
    Accept: "application/json"
  };
  let body: BodyInit | undefined;

  if (options.formData !== undefined) {
    body = options.formData;
  } else if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(options.body);
  }

  const response = await fetchImpl(resolveUrl(baseUrl, path, options.query), {
    method: options.method ?? "GET",
    headers,
    body
  });

  if (!response.ok) {
    const responseBody = await response.text();
    throw new ShutdownTrackerApiError(`Shutdown Tracker API request failed with ${response.status}.`, response.status, responseBody);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

function sourceFilesPath(projectId: string) {
  return `/api/projects/${encodePathSegment(projectId)}/source-files`;
}

function importBatchPath(projectId: string, importBatchId: string, path: string) {
  return `/api/projects/${encodePathSegment(projectId)}/import-batches/${encodePathSegment(importBatchId)}/${encodePath(path)}`;
}

function importReviewPath(projectId: string, path: string) {
  return `/api/projects/${encodePathSegment(projectId)}/import-review/${encodePath(path)}`;
}

function exportPreviewPath(projectId: string, path = "") {
  const suffix = path ? `/${encodePath(path)}` : "";
  return `/api/projects/${encodePathSegment(projectId)}/export-preview${suffix}`;
}

function exportCandidatesPath(projectId: string, path = "") {
  const suffix = path ? `/${encodePath(path)}` : "";
  return `/api/projects/${encodePathSegment(projectId)}/export-candidates${suffix}`;
}

function assertOnlySupportedRequestFields(
  requestName: string,
  request: object,
  supportedFields: readonly string[]
) {
  const supported = new Set(supportedFields);
  const unsupported = Object.keys(request)
    .filter((field) => !supported.has(field))
    .sort();
  if (unsupported.length > 0) {
    throw new TypeError(`${requestName} contains unsupported field(s): ${unsupported.join(", ")}.`);
  }
}

function encodePath(value: string) {
  return value.split("/").map(encodePathSegment).join("/");
}

function encodePathSegment(value: string) {
  return encodeURIComponent(value);
}

function resolveUrl(baseUrl: string, path: string, query?: Record<string, string>) {
  const queryString = query ? `?${new URLSearchParams(query).toString()}` : "";
  return `${baseUrl}${path}${queryString}`;
}

function normalizeBaseUrl(baseUrl: string) {
  return baseUrl.replace(/\/+$/, "");
}

function defaultFetch(): FetchLike {
  if (typeof globalThis.fetch !== "function") {
    throw new Error("A fetch implementation is required in this runtime.");
  }
  return globalThis.fetch.bind(globalThis) as FetchLike;
}
