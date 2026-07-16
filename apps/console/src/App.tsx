import {
  CheckCircle2,
  ChevronDown,
  CircleDashed,
  ClipboardCheck,
  FileSearch,
  FileWarning,
  Link2,
  PencilLine,
  RefreshCw,
  XCircle
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  formatConsoleReviewError,
  initialConsoleReviewLoadState,
  loadConsoleReviewData,
  reviewApiConnection,
  reviewApiRuntimeConfig
} from "./apiReviewClient";
import type { ConsoleReviewData, ConsoleReviewLoadState } from "./apiReviewClient";
import {
  buildConsoleMetrics,
  buildExportPreviewRows,
  buildExportPreviewSignals,
  buildReviewRows,
  consoleNavItems,
} from "./consoleData";
import {
  exportPreviewSequence,
  handoverSummaryItems,
  plannerReviewQueue,
  progressCandidates,
  progressReviewStateGroups,
  progressReviewTasks,
  projectVerification,
  structuredProblems,
  summaryTaskDetailReview,
  supervisorReviewQueue,
  taskDetailReview,
  todayProgressReviewItems
} from "./progressReviewData";
import type { ProgressReviewTask } from "./progressReviewData";

export function App() {
  const [reviewData, setReviewData] = useState<ConsoleReviewData | null>(null);
  const [loadState, setLoadState] = useState<ConsoleReviewLoadState>(() =>
    initialConsoleReviewLoadState(reviewApiRuntimeConfig)
  );

  const refreshReviewData = useCallback(async () => {
    if (reviewApiRuntimeConfig.liveEnabled) {
      setLoadState({
        status: "loading",
        message: "Fetching import/export review data."
      });
    }

    try {
      const nextReviewData = await loadConsoleReviewData();
      setReviewData(nextReviewData);
      setLoadState({
        status: nextReviewData.mode === "live" ? "loaded" : "synthetic",
        message: nextReviewData.message
      });
    } catch (error) {
      setLoadState({
        status: "error",
        message: formatConsoleReviewError(error)
      });
    }
  }, []);

  useEffect(() => {
    void refreshReviewData();
  }, [refreshReviewData]);

  const consoleMetrics = useMemo(() => buildConsoleMetrics(reviewData, loadState), [reviewData, loadState]);
  const reviewRows = useMemo(() => buildReviewRows(reviewData), [reviewData]);
  const exportPreviewRows = useMemo(() => buildExportPreviewRows(reviewData), [reviewData]);
  const exportPreviewSignals = useMemo(() => buildExportPreviewSignals(reviewData), [reviewData]);
  const reviewStatusLabel = statusLabel(loadState, reviewData);

  return (
    <div className="console-shell">
      <aside className="sidebar" aria-label="Console navigation">
        <div className="brand">
          <span className="brand-mark">ST</span>
          <div>
            <strong>Shutdown Tracker</strong>
            <span>Master Console</span>
          </div>
        </div>
        <nav className="nav-list">
          {consoleNavItems.map((item) => (
            <button
              className={item.active ? "nav-item active" : "nav-item"}
              type="button"
              key={item.label}
              aria-current={item.active ? "page" : undefined}
            >
              <item.icon size={18} aria-hidden="true" />
              <span>{item.label}</span>
            </button>
          ))}
        </nav>
      </aside>

      <main className="workspace">
        <header className="workspace-header">
          <div>
            <p className="eyebrow">Review scaffold</p>
            <h1>Execution review console</h1>
          </div>
          <div className="header-actions" aria-label="Console actions">
            <button
              type="button"
              className="icon-button"
              aria-label="Refresh review data"
              disabled={loadState.status === "loading"}
              onClick={() => void refreshReviewData()}
            >
              <RefreshCw size={18} aria-hidden="true" />
            </button>
            <button type="button" className="filter-button">
              <FileSearch size={18} aria-hidden="true" />
              <span>Review view</span>
              <ChevronDown size={16} aria-hidden="true" />
            </button>
          </div>
        </header>

        <section className="metric-grid" aria-label="Review status">
          {consoleMetrics.map((metric) => (
            <article className={`metric-card ${metric.tone}`} key={metric.label}>
              <div className="metric-icon" aria-hidden="true">
                <metric.icon size={20} />
              </div>
              <div>
                <p>{metric.label}</p>
                <strong>{metric.value}</strong>
                <span>{metric.detail}</span>
              </div>
            </article>
          ))}
        </section>

        <section className="work-panel" aria-label="Today progress review widget">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Today</p>
              <h2>Progress review</h2>
            </div>
            <span className="status-chip amber">Review visual shell</span>
          </div>
          <div className="review-question-grid">
            {todayProgressReviewItems.map((item) => (
              <article className={`review-question ${item.tone}`} key={item.label}>
                <div>
                  <span>{item.label}</span>
                  <strong>{item.count}</strong>
                </div>
                <StatusChip label={item.chip} />
                <p>{item.detail}</p>
              </article>
            ))}
          </div>
        </section>

        <section className="api-surface" aria-label="Review API client wiring">
          <div>
            <p className="eyebrow">API client</p>
            <h2>Import/export review operations</h2>
            <span>
              {reviewApiConnection.baseUrlLabel} / {reviewApiConnection.projectIdLabel} / {loadState.message}
            </span>
          </div>
          <div className="api-surface-list">
            {reviewApiConnection.highlightedSurfaces.map((surface) => (
              <span key={`${surface.method}-${surface.path}`}>
                <strong>{surface.method}</strong>
                {surface.label}
              </span>
            ))}
            <span>
              <strong>{reviewApiConnection.operationCount}</strong>
              Wired operations
            </span>
          </div>
        </section>

        <section className="content-grid">
          <article className="work-panel">
            <div className="panel-heading">
              <div>
                <p className="eyebrow">Import review</p>
                <h2>Snapshot workbench</h2>
              </div>
              <span className={`status-chip ${statusTone(loadState, reviewData)}`}>{reviewStatusLabel}</span>
            </div>
            <div className="review-table" role="table" aria-label="Snapshot review rows">
              <div className="review-row review-head" role="row">
                <span role="columnheader">Item</span>
                <span role="columnheader">Source</span>
                <span role="columnheader">State</span>
                <span role="columnheader">Context</span>
              </div>
              {reviewRows.map((row) => (
                <div className="review-row" role="row" key={row.item}>
                  <span role="cell">{row.item}</span>
                  <span role="cell">{row.source}</span>
                  <span role="cell">{row.state}</span>
                  <span role="cell">{row.owner}</span>
                </div>
              ))}
            </div>
          </article>

          <article className="work-panel">
            <div className="panel-heading">
              <div>
                <p className="eyebrow">Export preview</p>
                <h2>Eligible update candidates</h2>
              </div>
              <span className={`status-chip ${reviewData?.exportPreview === null ? "amber" : ""}`}>
                {exportPreviewLabel(reviewData)}
              </span>
            </div>
            <div className="preview-list">
              {exportPreviewRows.map((row) => (
                <div className="preview-line" key={`${row.field}-${row.candidate}`}>
                  <span>{row.field}</span>
                  <strong>{row.candidate}</strong>
                  <em>{row.eligibility}</em>
                </div>
              ))}
            </div>
            <div className="preview-signals" aria-label="Export preview status">
              {exportPreviewSignals.map((signal) => (
                <div key={signal.label}>
                  <span>{signal.label}</span>
                  <strong>{signal.value}</strong>
                </div>
              ))}
            </div>
          </article>
        </section>

        <section className="state-key" aria-label="Progress review state model">
          {progressReviewStateGroups.map((group) => (
            <article className="work-panel compact-panel" key={group.label}>
              <p className="eyebrow">{group.label}</p>
              <div className="chip-list">
                {group.states.map((state) => (
                  <StatusChip label={state} key={state} />
                ))}
              </div>
            </article>
          ))}
        </section>

        <section className="content-grid task-detail-grid" aria-label="Task detail progress panels">
          <TaskProgressPanel task={taskDetailReview} />
          <TaskProgressPanel task={summaryTaskDetailReview} />
        </section>

        <section
          className="work-panel route-panel"
          aria-label="Today supervisor review queue"
          data-zone="Today"
          data-route="/progress-review/supervisor"
        >
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Today / review queue</p>
              <h2>Supervisor Review Queue</h2>
            </div>
            <span className="status-chip blue">Visual-only controls</span>
          </div>
          <p className="boundary-copy">
            Supervisor review confirms operational validity. It does not approve Microsoft Project export.
          </p>
          <div className="supervisor-grid">
            {supervisorReviewQueue.map((item) => (
              <article className="queue-card" key={`${item.task}-${item.state}`}>
                <div className="queue-card-heading">
                  <div>
                    <strong>{item.task}</strong>
                    <span>{item.submittedProgress}</span>
                  </div>
                  <StatusChip label={item.state} />
                </div>
                <dl className="detail-list two-column">
                  <div>
                    <dt>Submitted by</dt>
                    <dd>{item.submittedBy}</dd>
                  </div>
                  <div>
                    <dt>Submitted time</dt>
                    <dd>{item.submittedTime}</dd>
                  </div>
                  <div>
                    <dt>Execution state</dt>
                    <dd>{item.executionState}</dd>
                  </div>
                  <div>
                    <dt>Evidence status</dt>
                    <dd>{item.evidenceStatus}</dd>
                  </div>
                  <div>
                    <dt>Blocker status</dt>
                    <dd>{item.blockerStatus}</dd>
                  </div>
                  <div>
                    <dt>Review confidence</dt>
                    <dd>{item.reviewConfidence}</dd>
                  </div>
                </dl>
                <p>{item.comment}</p>
                <VisualOnlyControls labels={["Accept", "Correct", "Reject"]} />
              </article>
            ))}
          </div>
        </section>

        <section
          className="work-panel route-panel priority-panel"
          aria-label="Exports planner progress review queue"
          data-zone="Exports"
          data-route="/progress-review/planner"
        >
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Exports / planner review</p>
              <h2>Planner Progress Review Queue</h2>
            </div>
            <span className="status-chip amber">Planner export decision</span>
          </div>
          <p className="boundary-copy">
            Planner approval marks this progress as eligible for export preview. The master .mpp is not updated.
          </p>
          <div className="planner-table" role="table" aria-label="Planner progress comparison table">
            <div className="planner-row planner-head" role="row">
              <span role="columnheader">Task</span>
              <span role="columnheader">Leaf / summary</span>
              <span role="columnheader">Project value</span>
              <span role="columnheader">Proposed value</span>
              <span role="columnheader">Review state</span>
              <span role="columnheader">Export check</span>
              <span role="columnheader">Visual decision</span>
            </div>
            {plannerReviewQueue.map((item) => (
              <div className="planner-row" role="row" key={item.task}>
                <span role="cell">
                  <strong>{item.task}</strong>
                  <em>{item.sourceUpdate}</em>
                </span>
                <span role="cell">
                  <StatusChip label={item.taskKind} />
                </span>
                <span role="cell">
                  <ProgressComparisonTable
                    values={[
                      ["Percent", item.currentProjectPercent],
                      ["Actual start", item.currentActualStart],
                      ["Actual finish", item.currentActualFinish]
                    ]}
                  />
                </span>
                <span role="cell">
                  <ProgressComparisonTable
                    values={[
                      ["Percent", item.proposedPercent],
                      ["Actual start", item.proposedActualStart],
                      ["Actual finish", item.proposedActualFinish]
                    ]}
                  />
                </span>
                <span role="cell">
                  <StatusChip label={item.supervisorState} />
                  <small>{item.evidenceStatus}</small>
                  <small>{item.blockerActionStatus}</small>
                </span>
                <span role="cell">
                  <StatusChip label={item.exportEligibility} />
                  <small>{item.reimportConflictState}</small>
                </span>
                <span role="cell">
                  <VisualOnlyControls labels={["Approve for export", "Reject", "Request clarification"]} compact />
                </span>
              </div>
            ))}
          </div>
        </section>

        <section className="work-panel" aria-label="Exports progress candidates in export preview" data-zone="Exports">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Exports / preview</p>
              <h2>Progress candidates in this batch</h2>
            </div>
            <span className="status-chip red">Leaf-task-only rule</span>
          </div>
          <div className="sequence-strip" aria-label="Controlled MSPDI XML handoff sequence">
            {exportPreviewSequence.map((step) => (
              <article className="sequence-step" key={step}>
                <CircleDashed size={17} aria-hidden="true" />
                <span>{step}</span>
              </article>
            ))}
          </div>
          <div className="candidate-grid">
            {progressCandidates.map((candidate) => (
              <article className="candidate-card" key={`${candidate.task}-${candidate.field}`}>
                <div className="queue-card-heading">
                  <div>
                    <strong>{candidate.task}</strong>
                    <span>{candidate.field}</span>
                  </div>
                  <StatusChip label={candidate.inclusion} />
                </div>
                <div className="old-new-grid" aria-label={`${candidate.task} ${candidate.field} values`}>
                  <div>
                    <span>Old value</span>
                    <strong>{candidate.oldValue}</strong>
                  </div>
                  <div>
                    <span>New value</span>
                    <strong>{candidate.newValue}</strong>
                  </div>
                  <div>
                    <span>Source</span>
                    <strong>{candidate.source}</strong>
                  </div>
                </div>
                <dl className="detail-list">
                  <div>
                    <dt>Approval state</dt>
                    <dd>{candidate.approvalState}</dd>
                  </div>
                  <div>
                    <dt>Generated artifact state</dt>
                    <dd>{candidate.generatedArtifactState}</dd>
                  </div>
                  <div>
                    <dt>Project verification state</dt>
                    <dd>{candidate.projectVerificationState}</dd>
                  </div>
                  <div>
                    <dt>Exclusion reason</dt>
                    <dd>{candidate.exclusionReason}</dd>
                  </div>
                </dl>
              </article>
            ))}
          </div>
        </section>

        <section className="content-grid" aria-label="Project verification and blocker examples">
          <article className="work-panel" data-zone="Exports">
            <div className="panel-heading">
              <div>
                <p className="eyebrow">Exports / Project verification</p>
                <h2>Manual Microsoft Project check</h2>
              </div>
              <StatusChip label={projectVerification.openedState} />
            </div>
            <p className="boundary-copy">
              Shutdown Tracker records verification metadata only. Saving or updating the master .mpp is a manual
              planner-controlled step outside Shutdown Tracker automation.
            </p>
            <dl className="detail-list two-column">
              <div>
                <dt>Generated artifact ID</dt>
                <dd>{projectVerification.artifactId}</dd>
              </div>
              <div>
                <dt>Generated at</dt>
                <dd>{projectVerification.generatedAt}</dd>
              </div>
              <div>
                <dt>Generated by</dt>
                <dd>{projectVerification.generatedBy}</dd>
              </div>
              <div>
                <dt>Opened by</dt>
                <dd>{projectVerification.openedBy}</dd>
              </div>
              <div>
                <dt>Opened at</dt>
                <dd>{projectVerification.openedAt}</dd>
              </div>
              <div>
                <dt>Verified by</dt>
                <dd>{projectVerification.verifiedBy}</dd>
              </div>
              <div>
                <dt>Verified at</dt>
                <dd>{projectVerification.verifiedAt}</dd>
              </div>
              <div>
                <dt>Verification outcome</dt>
                <dd>{projectVerification.verificationOutcome}</dd>
              </div>
              <div>
                <dt>Rejected / superseded state</dt>
                <dd>{projectVerification.rejectedSupersededState}</dd>
              </div>
              <div>
                <dt>Notes</dt>
                <dd>{projectVerification.notes}</dd>
              </div>
            </dl>
          </article>

          <article className="work-panel">
            <div className="panel-heading">
              <div>
                <p className="eyebrow">Problems / blockers</p>
                <h2>Progress links</h2>
              </div>
              <span className="status-chip amber">Structured records</span>
            </div>
            <div className="problem-list">
              {structuredProblems.map((problem) => (
                <article className="problem-card" key={problem.title}>
                  <div>
                    <strong>{problem.title}</strong>
                    <span>{problem.type} / {problem.status}</span>
                  </div>
                  <p>{problem.linkedTask}</p>
                  <small>{problem.owner} / {problem.handoverImpact}</small>
                </article>
              ))}
            </div>
            <VisualOnlyControls
              labels={["Create blocker", "Link existing blocker", "Create action", "Request evidence", "Include in handover"]}
            />
          </article>
        </section>

        <section className="work-panel" aria-label="Handover Summary">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Handover Summary</p>
              <h2>Flagged progress review items</h2>
            </div>
            <span className="status-chip blue">Incoming supervisor</span>
          </div>
          <p className="boundary-copy">Only flagged or unresolved progress items appear in handover. Routine comments are excluded.</p>
          <div className="handover-grid">
            {handoverSummaryItems.map((item) => (
              <article className="handover-card" key={item.label}>
                <span>{item.label}</span>
                <strong>{item.value}</strong>
                <p>{item.detail}</p>
              </article>
            ))}
          </div>
        </section>
      </main>
    </div>
  );
}

function TaskProgressPanel({ task }: { task: ProgressReviewTask }) {
  return (
    <article className="work-panel task-progress-panel">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Task detail</p>
          <h2>{task.taskName}</h2>
        </div>
        <StatusChip label={task.taskKind} />
      </div>
      <p className="boundary-copy">Discussion and progress review only. This does not update Microsoft Project.</p>
      <p className={task.taskKind === "Summary task" ? "task-warning red" : "task-warning"}>
        {task.warning}
      </p>
      <dl className="detail-list two-column">
        <div>
          <dt>Imported Project task identity</dt>
          <dd>{task.importedProjectIdentity}</dd>
        </div>
        <div>
          <dt>Task code</dt>
          <dd>{task.taskCode}</dd>
        </div>
        <div>
          <dt>Work package / area</dt>
          <dd>{task.workPackage} / {task.area}</dd>
        </div>
        <div>
          <dt>Execution state</dt>
          <dd>{task.executionState}</dd>
        </div>
      </dl>
      <div className="comparison-columns">
        <article>
          <h3>Current imported Project values</h3>
          <ProgressComparisonTable
            values={[
              ["Percent complete", task.imported.percentComplete],
              ["Actual start", task.imported.actualStart],
              ["Actual finish", task.imported.actualFinish],
              ["Planned start", task.imported.plannedStart],
              ["Planned finish", task.imported.plannedFinish]
            ]}
          />
        </article>
        <article>
          <h3>Latest field progress update</h3>
          <ProgressComparisonTable
            values={[
              ["Proposed percent complete", task.proposed.percentComplete],
              ["Proposed actual start", task.proposed.actualStart],
              ["Proposed actual finish", task.proposed.actualFinish],
              ["Source of update", task.proposed.source],
              ["Submitted by", task.proposed.submittedBy],
              ["Submitted at", task.proposed.submittedAt],
              ["Supervisor reviewed by", task.proposed.supervisorReviewedBy],
              ["Planner decision", task.proposed.plannerDecision]
            ]}
          />
        </article>
      </div>
      <div className="review-state-strip" aria-label={`${task.taskName} review states`}>
        <StatusChip label={task.supervisorReviewState} />
        <StatusChip label={task.plannerReviewState} />
        <StatusChip label={task.exportState} />
        <StatusChip label={task.syncState} />
      </div>
      <dl className="detail-list two-column">
        <div>
          <dt>Linked blockers</dt>
          <dd>{task.linkedBlockers.length === 0 ? "None" : task.linkedBlockers.join(", ")}</dd>
        </div>
        <div>
          <dt>Linked evidence gaps</dt>
          <dd>{task.evidenceGaps.length === 0 ? "None" : task.evidenceGaps.join(", ")}</dd>
        </div>
        <div>
          <dt>Linked handover notes</dt>
          <dd>{task.handoverNote}</dd>
        </div>
        <div>
          <dt>Audit trail summary</dt>
          <dd>{task.auditTrail.join(" / ")}</dd>
        </div>
      </dl>
    </article>
  );
}

function ProgressComparisonTable({ values }: { values: Array<[string, string]> }) {
  return (
    <dl className="comparison-list">
      {values.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function VisualOnlyControls({ labels, compact = false }: { labels: string[]; compact?: boolean }) {
  const icons = [CheckCircle2, PencilLine, XCircle, Link2, FileWarning];

  return (
    <div className={compact ? "visual-controls compact" : "visual-controls"} aria-label="Visual-only review controls">
      {labels.map((label, index) => {
        const ControlIcon = icons[index] ?? ClipboardCheck;

        return (
          <button type="button" disabled key={label} title="Visual-only control">
            <ControlIcon size={15} aria-hidden="true" />
            <span>{label}</span>
          </button>
        );
      })}
    </div>
  );
}

function StatusChip({ label }: { label: string }) {
  return <span className={`status-chip ${chipTone(label)}`}>{label}</span>;
}

function chipTone(label: string) {
  const value = label.toLowerCase();

  if (
    value.includes("blocked") ||
    value.includes("missing") ||
    value.includes("conflict") ||
    value.includes("rejected") ||
    value.includes("failed") ||
    value.includes("not eligible")
  ) {
    return "red";
  }

  if (
    value.includes("review") ||
    value.includes("queued") ||
    value.includes("correction") ||
    value.includes("draft") ||
    value.includes("paused")
  ) {
    return "amber";
  }

  if (
    value.includes("ready") ||
    value.includes("accepted") ||
    value.includes("approved") ||
    value.includes("eligible") ||
    value.includes("server received") ||
    value.includes("verified") ||
    value.includes("leaf")
  ) {
    return "green";
  }

  return "blue";
}

function statusLabel(loadState: ConsoleReviewLoadState, reviewData: ConsoleReviewData | null) {
  if (loadState.status === "loading") {
    return "Loading";
  }

  if (loadState.status === "error") {
    return "Review API error";
  }

  return reviewData?.mode === "live" ? "Live API" : "Synthetic review";
}

function statusTone(loadState: ConsoleReviewLoadState, reviewData: ConsoleReviewData | null) {
  if (loadState.status === "error") {
    return "red";
  }

  return reviewData?.mode === "live" ? "blue" : "";
}

function exportPreviewLabel(reviewData: ConsoleReviewData | null) {
  if (reviewData?.mode === "live") {
    return reviewData.exportPreview === null ? "No batch id" : reviewData.exportPreview.batch.status;
  }

  return "Draft";
}
