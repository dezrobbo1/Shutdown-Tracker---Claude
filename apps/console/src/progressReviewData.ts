export type ExecutionState =
  | "Not started"
  | "Ready"
  | "In progress"
  | "Paused"
  | "Blocked"
  | "Completed";

export type ProgressReviewState =
  | "Draft"
  | "Submitted"
  | "Needs supervisor review"
  | "Supervisor accepted"
  | "Correction requested"
  | "Rejected"
  | "Superseded"
  | "Needs planner review"
  | "Planner approved"
  | "Planner rejected";

export type ExportState =
  | "Not eligible"
  | "Eligible"
  | "Export blocked"
  | "Approved for export"
  | "In export preview"
  | "Artifact generated"
  | "Opened in Microsoft Project"
  | "Verified"
  | "Rejected / superseded";

export type SyncState =
  | "Local draft"
  | "Queued on device"
  | "Sending"
  | "Server received"
  | "Failed"
  | "Conflict";

export type ProjectValues = {
  percentComplete: string;
  actualStart: string;
  actualFinish: string;
  plannedStart: string;
  plannedFinish: string;
};

export type ProposedProgressValues = {
  percentComplete: string;
  actualStart: string;
  actualFinish: string;
  source: string;
  submittedBy: string;
  submittedAt: string;
  supervisorReviewedBy: string;
  plannerDecision: string;
};

export type ProgressReviewTask = {
  id: string;
  taskCode: string;
  taskName: string;
  workPackage: string;
  area: string;
  importedProjectIdentity: string;
  taskKind: "Leaf task" | "Summary task";
  executionState: ExecutionState;
  reviewState: ProgressReviewState;
  supervisorReviewState: ProgressReviewState;
  plannerReviewState: ProgressReviewState;
  exportState: ExportState;
  syncState: SyncState;
  evidenceStatus: string;
  blockerStatus: string;
  handoverNote: string;
  imported: ProjectValues;
  proposed: ProposedProgressValues;
  linkedBlockers: string[];
  evidenceGaps: string[];
  auditTrail: string[];
  warning: string;
};

export type TodayProgressReviewItem = {
  label: string;
  count: string;
  detail: string;
  chip: string;
  tone: "green" | "blue" | "amber" | "red";
};

export type SupervisorReviewItem = {
  task: string;
  submittedProgress: string;
  submittedBy: string;
  submittedTime: string;
  executionState: ExecutionState;
  evidenceStatus: string;
  blockerStatus: string;
  comment: string;
  reviewConfidence: string;
  state: string;
};

export type PlannerReviewItem = {
  task: string;
  taskKind: "Leaf task" | "Summary task";
  currentProjectPercent: string;
  proposedPercent: string;
  currentActualStart: string;
  proposedActualStart: string;
  currentActualFinish: string;
  proposedActualFinish: string;
  sourceUpdate: string;
  supervisorState: string;
  evidenceStatus: string;
  blockerActionStatus: string;
  exportEligibility: string;
  reimportConflictState: string;
};

export type ProgressCandidate = {
  task: string;
  field: string;
  oldValue: string;
  newValue: string;
  source: string;
  approvalState: string;
  generatedArtifactState: string;
  projectVerificationState: string;
  inclusion: "Approved progress candidate" | "Excluded candidate";
  exclusionReason: string;
};

export type StructuredProblem = {
  title: string;
  type: string;
  status: string;
  owner: string;
  linkedTask: string;
  handoverImpact: string;
};

export const progressReviewStateGroups = [
  {
    label: "Execution state",
    states: ["Not started", "Ready", "In progress", "Paused", "Blocked", "Completed"] satisfies ExecutionState[]
  },
  {
    label: "Progress review state",
    states: [
      "Draft",
      "Submitted",
      "Needs supervisor review",
      "Supervisor accepted",
      "Correction requested",
      "Rejected",
      "Superseded",
      "Needs planner review",
      "Planner approved",
      "Planner rejected"
    ] satisfies ProgressReviewState[]
  },
  {
    label: "Export state",
    states: [
      "Not eligible",
      "Eligible",
      "Export blocked",
      "Approved for export",
      "In export preview",
      "Artifact generated",
      "Opened in Microsoft Project",
      "Verified",
      "Rejected / superseded"
    ] satisfies ExportState[]
  },
  {
    label: "Sync state",
    states: ["Local draft", "Queued on device", "Sending", "Server received", "Failed", "Conflict"] satisfies SyncState[]
  }
];

export const todayProgressReviewItems: TodayProgressReviewItem[] = [
  {
    label: "Awaiting supervisor review",
    count: "4",
    detail: "Field updates waiting for operational check",
    chip: "Needs supervisor review",
    tone: "amber"
  },
  {
    label: "Awaiting planner review",
    count: "3",
    detail: "Supervisor-accepted updates needing export decision",
    chip: "Needs planner review",
    tone: "blue"
  },
  {
    label: "Ready for export",
    count: "2",
    detail: "Planner-approved leaf progress candidates",
    chip: "Ready for export",
    tone: "green"
  },
  {
    label: "Export preview exceptions",
    count: "5",
    detail: "Blocked by summary task, evidence, lineage, or blocker rules",
    chip: "Export blocked",
    tone: "red"
  },
  {
    label: "Evidence gaps",
    count: "2",
    detail: "Completion evidence required before review can advance",
    chip: "Evidence missing",
    tone: "amber"
  },
  {
    label: "Re-import conflicts",
    count: "1",
    detail: "Planner lineage review required before export",
    chip: "Re-import conflict",
    tone: "red"
  },
  {
    label: "Mobile queued / failed progress updates",
    count: "2",
    detail: "One queued locally, one failed but retained on device",
    chip: "Queued on device",
    tone: "amber"
  },
  {
    label: "Server received updates",
    count: "6",
    detail: "Received for review without export approval",
    chip: "Server received",
    tone: "green"
  },
  {
    label: "Superseded progress",
    count: "1",
    detail: "Older update replaced by supervisor correction",
    chip: "Superseded",
    tone: "blue"
  }
];

export const progressReviewTasks: ProgressReviewTask[] = [
  {
    id: "progress-a1",
    taskCode: "ST-A1",
    taskName: "C2 cyclone — remove access cover",
    workPackage: "Calciner C2 refractory outage",
    area: "Calciner C2",
    importedProjectIdentity: "Snapshot v3 / UID 1001 / WBS 1.1",
    taskKind: "Leaf task",
    executionState: "In progress",
    reviewState: "Needs planner review",
    supervisorReviewState: "Supervisor accepted",
    plannerReviewState: "Needs planner review",
    exportState: "Eligible",
    syncState: "Server received",
    evidenceStatus: "Evidence complete",
    blockerStatus: "No open blocker",
    handoverNote: "Ready for planner export review.",
    imported: {
      percentComplete: "40%",
      actualStart: "2026-06-20",
      actualFinish: "-",
      plannedStart: "2026-06-18",
      plannedFinish: "2026-06-25"
    },
    proposed: {
      percentComplete: "75%",
      actualStart: "2026-06-20",
      actualFinish: "-",
      source: "Mobile progress update",
      submittedBy: "Field supervisor",
      submittedAt: "2026-06-26 08:30",
      supervisorReviewedBy: "Supervisor / C2",
      plannerDecision: "Pending"
    },
    linkedBlockers: [],
    evidenceGaps: [],
    auditTrail: ["Field update submitted", "Supervisor accepted", "Awaiting planner review"],
    warning: "Leaf task. Eligible fields may be reviewed for export."
  },
  {
    id: "progress-a2",
    taskCode: "ST-A2",
    taskName: "C2 cyclone — inspect refractory lining",
    workPackage: "Calciner C2 refractory outage",
    area: "Calciner C2",
    importedProjectIdentity: "Snapshot v3 / UID 1002 / WBS 1.2",
    taskKind: "Leaf task",
    executionState: "Completed",
    reviewState: "Needs supervisor review",
    supervisorReviewState: "Needs supervisor review",
    plannerReviewState: "Draft",
    exportState: "Export blocked",
    syncState: "Server received",
    evidenceStatus: "Evidence missing.",
    blockerStatus: "Supervisor review blocked",
    handoverNote: "Evidence gap blocks planner review.",
    imported: {
      percentComplete: "80%",
      actualStart: "2026-06-19",
      actualFinish: "-",
      plannedStart: "2026-06-18",
      plannedFinish: "2026-06-24"
    },
    proposed: {
      percentComplete: "100%",
      actualStart: "2026-06-19",
      actualFinish: "2026-06-26",
      source: "Mobile completion update",
      submittedBy: "Shift supervisor",
      submittedAt: "2026-06-26 09:05",
      supervisorReviewedBy: "Not reviewed",
      plannerDecision: "Not ready"
    },
    linkedBlockers: [],
    evidenceGaps: ["Completion evidence required"],
    auditTrail: ["Completion submitted", "Evidence gap detected"],
    warning: "Leaf task. Eligible fields may be reviewed for export."
  },
  {
    id: "progress-summary-a",
    taskCode: "ST-SUM-A",
    taskName: "Calciner C2 refractory outage",
    workPackage: "Calciner C2 outage plan",
    area: "Calciner C2",
    importedProjectIdentity: "Snapshot v3 / UID 1000 / WBS 1",
    taskKind: "Summary task",
    executionState: "In progress",
    reviewState: "Submitted",
    supervisorReviewState: "Correction requested",
    plannerReviewState: "Planner rejected",
    exportState: "Not eligible",
    syncState: "Server received",
    evidenceStatus: "Context only",
    blockerStatus: "Summary task export blocked",
    handoverNote: "Direct summary task progress export is blocked.",
    imported: {
      percentComplete: "52%",
      actualStart: "2026-06-18",
      actualFinish: "-",
      plannedStart: "2026-06-18",
      plannedFinish: "2026-06-28"
    },
    proposed: {
      percentComplete: "70%",
      actualStart: "2026-06-18",
      actualFinish: "-",
      source: "Review comment",
      submittedBy: "Control room coordinator",
      submittedAt: "2026-06-26 09:20",
      supervisorReviewedBy: "Supervisor / access workfront",
      plannerDecision: "Summary task blocked"
    },
    linkedBlockers: [],
    evidenceGaps: [],
    auditTrail: ["Summary progress proposed", "Summary-task rule applied"],
    warning: "Summary task. Not eligible for direct progress export."
  },
  {
    id: "progress-b1",
    taskCode: "ST-B1",
    taskName: "Kiln feed chute — awaiting scaffold",
    workPackage: "Calciner C2 access workfront",
    area: "Access workfront",
    importedProjectIdentity: "Snapshot v3 / UID 2001 / WBS 2.1",
    taskKind: "Leaf task",
    executionState: "Blocked",
    reviewState: "Submitted",
    supervisorReviewState: "Needs supervisor review",
    plannerReviewState: "Draft",
    exportState: "Export blocked",
    syncState: "Server received",
    evidenceStatus: "Evidence not required yet",
    blockerStatus: "Scaffold not available",
    handoverNote: "Open blocker affects progress review.",
    imported: {
      percentComplete: "10%",
      actualStart: "2026-06-22",
      actualFinish: "-",
      plannedStart: "2026-06-22",
      plannedFinish: "2026-06-27"
    },
    proposed: {
      percentComplete: "25%",
      actualStart: "2026-06-22",
      actualFinish: "-",
      source: "Supervisor field check",
      submittedBy: "Package supervisor",
      submittedAt: "2026-06-26 10:10",
      supervisorReviewedBy: "Not reviewed",
      plannerDecision: "Blocked"
    },
    linkedBlockers: ["Scaffold not available"],
    evidenceGaps: [],
    auditTrail: ["Progress submitted", "Blocker linked"],
    warning: "Leaf task. Eligible fields may be reviewed for export."
  },
  {
    id: "progress-c1",
    taskCode: "ST-C1",
    taskName: "Permit isolation — confirm revised line",
    workPackage: "Calciner C2 permit workfront",
    area: "Permit / isolation",
    importedProjectIdentity: "Snapshot v2 / UID 3001 / WBS changed in v3",
    taskKind: "Leaf task",
    executionState: "In progress",
    reviewState: "Needs planner review",
    supervisorReviewState: "Supervisor accepted",
    plannerReviewState: "Needs planner review",
    exportState: "Export blocked",
    syncState: "Conflict",
    evidenceStatus: "Evidence complete",
    blockerStatus: "Lineage conflict",
    handoverNote: "Re-import conflict. Planner lineage review required.",
    imported: {
      percentComplete: "35%",
      actualStart: "2026-06-21",
      actualFinish: "-",
      plannedStart: "2026-06-20",
      plannedFinish: "2026-06-29"
    },
    proposed: {
      percentComplete: "60%",
      actualStart: "2026-06-21",
      actualFinish: "-",
      source: "Previous snapshot progress update",
      submittedBy: "Area coordinator",
      submittedAt: "2026-06-26 10:45",
      supervisorReviewedBy: "Supervisor / permit workfront",
      plannerDecision: "Lineage review required"
    },
    linkedBlockers: [],
    evidenceGaps: [],
    auditTrail: ["Progress submitted against previous snapshot", "Re-import conflict detected"],
    warning: "Leaf task. Eligible fields may be reviewed for export."
  },
  {
    id: "progress-d1",
    taskCode: "ST-D1",
    taskName: "Furnace bottom — install blanking plate",
    workPackage: "Furnace bottom workfront",
    area: "Furnace bottom",
    importedProjectIdentity: "Snapshot v3 / UID 4001 / WBS 4.1",
    taskKind: "Leaf task",
    executionState: "In progress",
    reviewState: "Planner approved",
    supervisorReviewState: "Supervisor accepted",
    plannerReviewState: "Planner approved",
    exportState: "In export preview",
    syncState: "Server received",
    evidenceStatus: "Evidence complete",
    blockerStatus: "No open blocker",
    handoverNote: "Included in draft export preview.",
    imported: {
      percentComplete: "20%",
      actualStart: "-",
      actualFinish: "-",
      plannedStart: "2026-06-23",
      plannedFinish: "2026-06-30"
    },
    proposed: {
      percentComplete: "50%",
      actualStart: "2026-06-24",
      actualFinish: "-",
      source: "Planner reviewed field update",
      submittedBy: "Workfront supervisor",
      submittedAt: "2026-06-26 11:15",
      supervisorReviewedBy: "Supervisor / furnace workfront",
      plannerDecision: "Approved for export"
    },
    linkedBlockers: [],
    evidenceGaps: [],
    auditTrail: ["Supervisor accepted", "Planner approved", "Added to draft export preview"],
    warning: "Leaf task. Eligible fields may be reviewed for export."
  },
  {
    id: "progress-e1-superseded",
    taskCode: "ST-E1",
    taskName: "Heat exchanger — inspect tube bundle",
    workPackage: "Cooling water workfront",
    area: "Cooling water",
    importedProjectIdentity: "Snapshot v3 / UID 5001 / WBS 5.1",
    taskKind: "Leaf task",
    executionState: "Paused",
    reviewState: "Superseded",
    supervisorReviewState: "Superseded",
    plannerReviewState: "Draft",
    exportState: "Rejected / superseded",
    syncState: "Server received",
    evidenceStatus: "Older evidence superseded",
    blockerStatus: "No open blocker",
    handoverNote: "Older update replaced by supervisor correction.",
    imported: {
      percentComplete: "55%",
      actualStart: "2026-06-21",
      actualFinish: "-",
      plannedStart: "2026-06-21",
      plannedFinish: "2026-06-27"
    },
    proposed: {
      percentComplete: "65%",
      actualStart: "2026-06-21",
      actualFinish: "-",
      source: "Older mobile update",
      submittedBy: "Night shift supervisor",
      submittedAt: "2026-06-26 07:30",
      supervisorReviewedBy: "Supervisor / cooling-water workfront",
      plannerDecision: "Superseded"
    },
    linkedBlockers: [],
    evidenceGaps: [],
    auditTrail: ["Older field update submitted", "Supervisor correction superseded update"],
    warning: "Leaf task. Eligible fields may be reviewed for export."
  },
  {
    id: "progress-mobile-queued",
    taskCode: "ST-M1",
    taskName: "Valve isolation — field update draft",
    workPackage: "Calciner C2 field updates",
    area: "Field updates",
    importedProjectIdentity: "Snapshot v3 / UID 6001 / WBS 6.1",
    taskKind: "Leaf task",
    executionState: "In progress",
    reviewState: "Draft",
    supervisorReviewState: "Draft",
    plannerReviewState: "Draft",
    exportState: "Not eligible",
    syncState: "Queued on device",
    evidenceStatus: "Local evidence pending",
    blockerStatus: "No open blocker",
    handoverNote: "Queued on this device. Not yet sent.",
    imported: {
      percentComplete: "0%",
      actualStart: "-",
      actualFinish: "-",
      plannedStart: "2026-06-26",
      plannedFinish: "2026-07-01"
    },
    proposed: {
      percentComplete: "15%",
      actualStart: "2026-06-26",
      actualFinish: "-",
      source: "Local mobile draft",
      submittedBy: "Field supervisor",
      submittedAt: "Saved locally",
      supervisorReviewedBy: "Not sent",
      plannerDecision: "Not available"
    },
    linkedBlockers: [],
    evidenceGaps: [],
    auditTrail: ["Saved locally", "Queued on device"],
    warning: "Leaf task. Eligible fields may be reviewed for export."
  },
  {
    id: "progress-mobile-failed",
    taskCode: "ST-M2",
    taskName: "Crane lift — evidence upload retry",
    workPackage: "Calciner C2 field updates",
    area: "Field updates",
    importedProjectIdentity: "Snapshot v3 / UID 6002 / WBS 6.2",
    taskKind: "Leaf task",
    executionState: "Blocked",
    reviewState: "Draft",
    supervisorReviewState: "Draft",
    plannerReviewState: "Draft",
    exportState: "Not eligible",
    syncState: "Failed",
    evidenceStatus: "Evidence saved locally",
    blockerStatus: "Material missing",
    handoverNote: "Could not send. Still saved on this device.",
    imported: {
      percentComplete: "25%",
      actualStart: "2026-06-25",
      actualFinish: "-",
      plannedStart: "2026-06-24",
      plannedFinish: "2026-06-30"
    },
    proposed: {
      percentComplete: "30%",
      actualStart: "2026-06-25",
      actualFinish: "-",
      source: "Failed mobile send",
      submittedBy: "Package owner",
      submittedAt: "Saved locally",
      supervisorReviewedBy: "Not sent",
      plannerDecision: "Not available"
    },
    linkedBlockers: ["Material missing"],
    evidenceGaps: [],
    auditTrail: ["Saved locally", "Could not send"],
    warning: "Leaf task. Eligible fields may be reviewed for export."
  }
];

export const taskDetailReview = progressReviewTasks[0];
export const summaryTaskDetailReview = progressReviewTasks[2];

export const supervisorReviewQueue: SupervisorReviewItem[] = [
  {
    task: "ST-A1 C2 cyclone — remove access cover",
    submittedProgress: "40% -> 75%",
    submittedBy: "Field supervisor",
    submittedTime: "2026-06-26 08:30",
    executionState: "In progress",
    evidenceStatus: "Evidence complete",
    blockerStatus: "No open blocker",
    comment: "Normal progress update for supervisor check.",
    reviewConfidence: "High",
    state: "New submission"
  },
  {
    task: "ST-A2 C2 cyclone — inspect refractory lining",
    submittedProgress: "80% -> 100%",
    submittedBy: "Shift supervisor",
    submittedTime: "2026-06-26 09:05",
    executionState: "Completed",
    evidenceStatus: "Evidence missing.",
    blockerStatus: "No open blocker",
    comment: "Completion claimed; evidence required.",
    reviewConfidence: "Low until evidence arrives",
    state: "Evidence missing"
  },
  {
    task: "ST-B1 Kiln feed chute — awaiting scaffold",
    submittedProgress: "10% -> 25%",
    submittedBy: "Package supervisor",
    submittedTime: "2026-06-26 10:10",
    executionState: "Blocked",
    evidenceStatus: "Evidence not required yet",
    blockerStatus: "Scaffold not available",
    comment: "Progress blocked by structured problem record.",
    reviewConfidence: "Medium",
    state: "Blocker open"
  },
  {
    task: "ST-SUM-A Calciner C2 refractory outage",
    submittedProgress: "52% -> 70%",
    submittedBy: "Control room coordinator",
    submittedTime: "2026-06-26 09:20",
    executionState: "In progress",
    evidenceStatus: "Context only",
    blockerStatus: "Summary task export blocked",
    comment: "Summary task update requires correction.",
    reviewConfidence: "Rule blocked",
    state: "Correction requested"
  },
  {
    task: "ST-D1 Furnace bottom — install blanking plate",
    submittedProgress: "20% -> 50%",
    submittedBy: "Workfront supervisor",
    submittedTime: "2026-06-26 11:15",
    executionState: "In progress",
    evidenceStatus: "Evidence complete",
    blockerStatus: "No open blocker",
    comment: "Ready for planner review.",
    reviewConfidence: "High",
    state: "Supervisor accepted"
  },
  {
    task: "ST-X1 Quality hold — resolve inspection finding",
    submittedProgress: "30% -> 90%",
    submittedBy: "Inspection lead",
    submittedTime: "2026-06-26 11:40",
    executionState: "In progress",
    evidenceStatus: "Evidence mismatch",
    blockerStatus: "Quality hold",
    comment: "Rejected until quality hold is cleared.",
    reviewConfidence: "Low",
    state: "Rejected"
  },
  {
    task: "ST-E1 Heat exchanger — inspect tube bundle",
    submittedProgress: "55% -> 65%",
    submittedBy: "Night shift supervisor",
    submittedTime: "2026-06-26 07:30",
    executionState: "Paused",
    evidenceStatus: "Older evidence superseded",
    blockerStatus: "No open blocker",
    comment: "Older update replaced by supervisor correction.",
    reviewConfidence: "Superseded",
    state: "Superseded"
  }
];

export const plannerReviewQueue: PlannerReviewItem[] = [
  {
    task: "ST-A1 C2 cyclone — remove access cover",
    taskKind: "Leaf task",
    currentProjectPercent: "40%",
    proposedPercent: "75%",
    currentActualStart: "2026-06-20",
    proposedActualStart: "2026-06-20",
    currentActualFinish: "-",
    proposedActualFinish: "-",
    sourceUpdate: "Mobile progress update",
    supervisorState: "Supervisor accepted",
    evidenceStatus: "Evidence complete",
    blockerActionStatus: "No open blocker",
    exportEligibility: "Eligible",
    reimportConflictState: "No conflict"
  },
  {
    task: "ST-D1 Furnace bottom — install blanking plate",
    taskKind: "Leaf task",
    currentProjectPercent: "20%",
    proposedPercent: "50%",
    currentActualStart: "-",
    proposedActualStart: "2026-06-24",
    currentActualFinish: "-",
    proposedActualFinish: "-",
    sourceUpdate: "Planner reviewed field update",
    supervisorState: "Supervisor accepted",
    evidenceStatus: "Evidence complete",
    blockerActionStatus: "No open blocker",
    exportEligibility: "Approved for export",
    reimportConflictState: "No conflict"
  },
  {
    task: "ST-A2 C2 cyclone — inspect refractory lining",
    taskKind: "Leaf task",
    currentProjectPercent: "80%",
    proposedPercent: "100%",
    currentActualStart: "2026-06-19",
    proposedActualStart: "2026-06-19",
    currentActualFinish: "-",
    proposedActualFinish: "2026-06-26",
    sourceUpdate: "Mobile completion update",
    supervisorState: "Needs supervisor review",
    evidenceStatus: "Evidence missing.",
    blockerActionStatus: "Evidence request open",
    exportEligibility: "Export blocked",
    reimportConflictState: "No conflict"
  },
  {
    task: "ST-SUM-A Calciner C2 refractory outage",
    taskKind: "Summary task",
    currentProjectPercent: "52%",
    proposedPercent: "70%",
    currentActualStart: "2026-06-18",
    proposedActualStart: "2026-06-18",
    currentActualFinish: "-",
    proposedActualFinish: "-",
    sourceUpdate: "Review comment",
    supervisorState: "Correction requested",
    evidenceStatus: "Context only",
    blockerActionStatus: "Summary task rule",
    exportEligibility: "Not eligible",
    reimportConflictState: "No conflict"
  },
  {
    task: "ST-C1 Permit isolation — confirm revised line",
    taskKind: "Leaf task",
    currentProjectPercent: "35%",
    proposedPercent: "60%",
    currentActualStart: "2026-06-21",
    proposedActualStart: "2026-06-21",
    currentActualFinish: "-",
    proposedActualFinish: "-",
    sourceUpdate: "Previous snapshot progress update",
    supervisorState: "Supervisor accepted",
    evidenceStatus: "Evidence complete",
    blockerActionStatus: "Lineage action required",
    exportEligibility: "Export blocked",
    reimportConflictState: "Re-import conflict. Planner lineage review required."
  }
];

export const progressCandidates: ProgressCandidate[] = [
  {
    task: "ST-D1 Furnace bottom — install blanking plate",
    field: "percent_complete",
    oldValue: "20%",
    newValue: "50%",
    source: "Planner approved field progress",
    approvalState: "Approved for export",
    generatedArtifactState: "In export preview",
    projectVerificationState: "Awaiting artifact generation",
    inclusion: "Approved progress candidate",
    exclusionReason: "None"
  },
  {
    task: "ST-D1 Furnace bottom — install blanking plate",
    field: "actual_start",
    oldValue: "-",
    newValue: "2026-06-24",
    source: "Planner approved field progress",
    approvalState: "Approved for export",
    generatedArtifactState: "In export preview",
    projectVerificationState: "Awaiting artifact generation",
    inclusion: "Approved progress candidate",
    exclusionReason: "None"
  },
  {
    task: "ST-SUM-A Calciner C2 refractory outage",
    field: "percent_complete",
    oldValue: "52%",
    newValue: "70%",
    source: "Summary task progress proposal",
    approvalState: "Rejected",
    generatedArtifactState: "Excluded",
    projectVerificationState: "Not sent to artifact",
    inclusion: "Excluded candidate",
    exclusionReason: "Leaf-task-only rule; summary-task direct progress export blocked"
  },
  {
    task: "ST-A2 C2 cyclone — inspect refractory lining",
    field: "actual_finish",
    oldValue: "-",
    newValue: "2026-06-26",
    source: "Mobile completion update",
    approvalState: "Needs supervisor review",
    generatedArtifactState: "Excluded",
    projectVerificationState: "Not sent to artifact",
    inclusion: "Excluded candidate",
    exclusionReason: "Evidence missing."
  },
  {
    task: "ST-C1 Permit isolation — confirm revised line",
    field: "percent_complete",
    oldValue: "35%",
    newValue: "60%",
    source: "Previous snapshot progress update",
    approvalState: "Needs planner review",
    generatedArtifactState: "Excluded",
    projectVerificationState: "Not sent to artifact",
    inclusion: "Excluded candidate",
    exclusionReason: "Re-import conflict. Planner lineage review required."
  }
];

export const exportPreviewSequence = [
  "Draft export preview — master .mpp not updated.",
  "Export batch approved — master .mpp not updated.",
  "MSPDI/XML artifact generated — master .mpp not updated.",
  "Planner must manually open/check the artifact in Microsoft Project.",
  "Verified in Microsoft Project — master .mpp update remains planner-controlled."
];

export const projectVerification = {
  artifactId: "artifact-progress-preview-001",
  generatedAt: "2026-06-26 12:10",
  generatedBy: "Shutdown planner",
  openedState: "Opened in Microsoft Project",
  openedBy: "Shutdown planner",
  openedAt: "2026-06-26 12:25",
  verifiedBy: "Shutdown planner",
  verifiedAt: "2026-06-26 12:40",
  verificationOutcome: "Verified",
  rejectedSupersededState: "No rejection or supersession recorded",
  notes: "Progress fields verified against synthetic task identity. Master .mpp update remains manual."
};

export const structuredProblems: StructuredProblem[] = [
  {
    title: "Scaffold not available",
    type: "Blocker",
    status: "Open",
    owner: "Supervisor",
    linkedTask: "ST-B1 Kiln feed chute — awaiting scaffold",
    handoverImpact: "Progress blocked from export review"
  },
  {
    title: "Permit not issued",
    type: "Problem",
    status: "Open",
    owner: "Planner",
    linkedTask: "ST-P1 Permit release — confirm isolations",
    handoverImpact: "Ready state cannot be confirmed"
  },
  {
    title: "Isolation not complete",
    type: "Blocker",
    status: "Action required",
    owner: "Field supervisor",
    linkedTask: "ST-I1 Isolation — complete verification",
    handoverImpact: "Supervisor review held"
  },
  {
    title: "Material missing",
    type: "Blocker",
    status: "Open",
    owner: "Package owner",
    linkedTask: "ST-M2 Crane lift — evidence upload retry",
    handoverImpact: "Mobile failed update retained"
  },
  {
    title: "Quality hold",
    type: "Hold point",
    status: "Rejected progress",
    owner: "Inspector",
    linkedTask: "ST-X1 Quality hold — resolve inspection finding",
    handoverImpact: "Progress rejected until hold clears"
  },
  {
    title: "Evidence missing",
    type: "Evidence gap",
    status: "Request open",
    owner: "Field supervisor",
    linkedTask: "ST-A2 C2 cyclone — inspect refractory lining",
    handoverImpact: "Planner review not ready"
  }
];

export const handoverSummaryItems = [
  {
    label: "Tasks completed but awaiting review",
    value: "ST-A2",
    detail: "Completion progress held for evidence."
  },
  {
    label: "Tasks accepted by supervisor but awaiting planner review",
    value: "ST-A1, ST-C1",
    detail: "Planner needs export and lineage decisions."
  },
  {
    label: "Open blockers affecting progress",
    value: "Scaffold not available, Material missing",
    detail: "Structured blocker records linked to progress."
  },
  {
    label: "Evidence gaps",
    value: "Completion evidence required",
    detail: "Request evidence before supervisor acceptance."
  },
  {
    label: "Rejected or superseded progress updates",
    value: "ST-X1, ST-E1",
    detail: "Older or rejected field progress stays out of export."
  },
  {
    label: "Export candidates not yet approved",
    value: "ST-A1",
    detail: "Eligible leaf progress awaiting planner approval."
  },
  {
    label: "Notes for incoming supervisor",
    value: "Review open blockers first",
    detail: "Only flagged items are carried forward."
  }
];
