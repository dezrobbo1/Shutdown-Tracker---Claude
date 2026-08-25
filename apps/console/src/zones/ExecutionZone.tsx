import { useCallback, useMemo, useState } from "react";
import type {
  ImportReviewTaskRow,
  ProblemRecord,
  TaskExecutionState
} from "@shutdown-tracker/api-client";
import {
  BoundaryNote,
  CapabilityGate,
  PanelHeading,
  ResourceView,
  StatusChip,
  WriteFeedback,
  useWriteAction
} from "../components";
import { executionStateLabels, formatDateTime, formatPercent } from "../formatting";
import { useAsyncResource } from "../useAsyncResource";
import { allResourceGroups, leafTasks, useSnapshotTasks } from "../useSnapshotTasks";
import type { ZoneProps } from "./ZoneProps";

const submittableStates: TaskExecutionState[] = [
  "READY",
  "IN_PROGRESS",
  "PAUSED",
  "BLOCKED",
  "COMPLETED"
];

/**
 * Live execution against the imported schedule.
 *
 * Only leaf tasks can be reported on. A summary task's progress is the roll-up of the work
 * beneath it, and reporting against it directly would put a number into the schedule that no
 * one actually performed — which is also why the export path refuses summary rows.
 */
export function ExecutionZone({ session, client }: ZoneProps) {
  const projectId = session.projectId;
  const tasks = useSnapshotTasks(client, projectId, session.live);
  const [filter, setFilter] = useState("");
  const [groupFilter, setGroupFilter] = useState("");
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);

  const problems = useAsyncResource<ProblemRecord[]>(
    useCallback(() => client.problems.listOpen(projectId), [client, projectId]),
    { enabled: session.live, idleMessage: "Configure a project and actor to load open problems." }
  );

  const blockingTaskIds = useMemo(() => {
    if (problems.state.status !== "loaded") {
      return new Set<string>();
    }
    return new Set(
      problems.state.value
        .filter((problem) => problem.blocksExecution && problem.importedTaskId !== null)
        .map((problem) => problem.importedTaskId as string)
    );
  }, [problems.state]);

  const snapshot = tasks.state.status === "loaded" ? tasks.state.value : null;

  const groupOptions = useMemo(
    () => (snapshot === null ? [] : allResourceGroups(snapshot)),
    [snapshot]
  );

  const visibleTasks = useMemo(() => {
    if (snapshot === null) {
      return [];
    }
    const leaves = leafTasks(snapshot);
    const needle = filter.trim().toLowerCase();
    return leaves.filter((task) => {
      if (needle.length > 0 && !matchesFilter(task, needle)) {
        return false;
      }
      if (groupFilter.length > 0) {
        return (snapshot.groupsByTaskId.get(task.id) ?? []).includes(groupFilter);
      }
      return true;
    });
  }, [snapshot, filter, groupFilter]);

  const selectedTask =
    tasks.state.status === "loaded" && selectedTaskId !== null
      ? (tasks.state.value.byId.get(selectedTaskId) ?? null)
      : null;

  return (
    <div className="zone-grid">
      <article className="work-panel">
        <PanelHeading eyebrow="Live work" title="Leaf tasks">
          <StatusChip label={`${visibleTasks.length} shown`} tone="blue" />
        </PanelHeading>
        <BoundaryNote>
          Reporting is against leaf tasks only. Summary rows are shown for context in Import
          review and are never export candidates.
        </BoundaryNote>
        <BoundaryNote>
          Task, duration, resource group and the planned dates are what Microsoft Project says and
          are never written back. The shaded columns — actual start, actual finish and percent —
          are the execution input this product owns, and the only values an export carries.
        </BoundaryNote>
        <label className="wide-field">
          <span>Filter</span>
          <input
            value={filter}
            onChange={(event) => setFilter(event.target.value)}
            placeholder="Task name, WBS, or identifier"
          />
        </label>
        <label className="wide-field">
          <span>Resource group</span>
          <select
            value={groupFilter}
            onChange={(event) => setGroupFilter(event.target.value)}
            disabled={groupOptions.length === 0}
          >
            <option value="">
              {groupOptions.length === 0 ? "No groups in this schedule" : "Every group"}
            </option>
            {groupOptions.map((group) => (
              <option key={group} value={group}>
                {group}
              </option>
            ))}
          </select>
        </label>

        <ResourceView
          resource={tasks}
          emptyWhen={(value) => value.tasks.length === 0}
          emptyMessage="No imported schedule is available. Accept a snapshot in Import review first."
        >
          {() =>
            visibleTasks.length === 0 ? (
              <BoundaryNote>No leaf tasks match that filter.</BoundaryNote>
            ) : (
              <div className="review-table dense task-table" role="table" aria-label="Leaf tasks">
                <div className="review-row task-row review-head" role="row">
                  <span role="columnheader">WBS</span>
                  <span role="columnheader">Task</span>
                  <span role="columnheader">Duration</span>
                  <span role="columnheader">Resource group</span>
                  <span role="columnheader">Planned start</span>
                  <span role="columnheader">Planned finish</span>
                  <span role="columnheader" className="reported-cell">
                    Actual start
                  </span>
                  <span role="columnheader" className="reported-cell">
                    Actual finish
                  </span>
                  <span role="columnheader" className="reported-cell">
                    Percent
                  </span>
                  <span role="columnheader">Blocker</span>
                </div>
                {visibleTasks.slice(0, 200).map((task) => (
                  <button
                    type="button"
                    className={
                      task.id === selectedTaskId
                        ? "review-row task-row selectable selected"
                        : "review-row task-row selectable"
                    }
                    role="row"
                    key={task.id}
                    onClick={() => setSelectedTaskId(task.id)}
                    aria-pressed={task.id === selectedTaskId}
                  >
                    <span role="cell">{task.wbs ?? task.outlineNumber ?? task.externalId ?? "—"}</span>
                    <span role="cell">{task.name ?? "Unnamed task"}</span>
                    <span role="cell">{task.durationText ?? "—"}</span>
                    <span role="cell">
                      {(snapshot?.groupsByTaskId.get(task.id) ?? []).join(", ") || "—"}
                    </span>
                    <span role="cell">{formatDateTime(task.plannedStart)}</span>
                    <span role="cell">{formatDateTime(task.plannedFinish)}</span>
                    <span role="cell" className="reported-cell">
                      {formatDateTime(task.actualStart)}
                    </span>
                    <span role="cell" className="reported-cell">
                      {formatDateTime(task.actualFinish)}
                    </span>
                    <span role="cell" className="reported-cell">
                      {formatPercent(task.percentComplete)}
                    </span>
                    <span role="cell">
                      {blockingTaskIds.has(task.id) ? <StatusChip label="Blocked" tone="red" /> : "—"}
                    </span>
                  </button>
                ))}
              </div>
            )
          }
        </ResourceView>
      </article>

      <article className="work-panel">
        <PanelHeading eyebrow="Report progress" title={selectedTask?.name ?? "Select a task"} />
        {selectedTask === null ? (
          <BoundaryNote>Choose a leaf task to report progress against it.</BoundaryNote>
        ) : (
          <ProgressForm
            key={selectedTask.id}
            task={selectedTask}
            blocked={blockingTaskIds.has(selectedTask.id)}
            canSubmit={session.canSubmitProgress}
            onSubmit={(request) => client.taskProgress.submit(projectId, request)}
          />
        )}
      </article>
    </div>
  );
}

/**
 * The progress report itself.
 *
 * The imported values are shown alongside the inputs so the reporter can see what the
 * schedule currently says without that value being editable here. Only actuals and percent
 * complete are reportable: planned dates belong to Microsoft Project.
 */
function ProgressForm({
  task,
  blocked,
  canSubmit,
  onSubmit
}: {
  task: ImportReviewTaskRow;
  blocked: boolean;
  canSubmit: boolean;
  onSubmit: (request: {
    importedTaskId: string;
    executionState: TaskExecutionState;
    percentComplete?: number | null;
    actualStart?: string | null;
    actualFinish?: string | null;
    comment?: string | null;
    idempotencyKey?: string | null;
  }) => Promise<unknown>;
}) {
  const [executionState, setExecutionState] = useState<TaskExecutionState>("IN_PROGRESS");
  const [percentComplete, setPercentComplete] = useState("");
  const [actualStart, setActualStart] = useState("");
  const [actualFinish, setActualFinish] = useState("");
  const [comment, setComment] = useState("");
  const write = useWriteAction();

  const validation = validateProgressInput(percentComplete, actualStart, actualFinish);

  const submit = async () => {
    if (validation !== null) {
      return;
    }
    const ok = await write.run("Submitted for supervisor review.", () =>
      onSubmit({
        importedTaskId: task.id,
        executionState,
        percentComplete: percentComplete === "" ? null : Number(percentComplete),
        actualStart: toOffsetDateTime(actualStart),
        actualFinish: toOffsetDateTime(actualFinish),
        comment: comment || null,
        idempotencyKey: null
      })
    );
    if (ok) {
      setComment("");
    }
  };

  return (
    <>
      <div className="detail-grid">
        <div className="detail-value">
          <span>Planned start</span>
          <strong>{formatDateTime(task.plannedStart)}</strong>
        </div>
        <div className="detail-value">
          <span>Planned finish</span>
          <strong>{formatDateTime(task.plannedFinish)}</strong>
        </div>
        <div className="detail-value">
          <span>Imported percent</span>
          <strong>{formatPercent(task.percentComplete)}</strong>
        </div>
      </div>

      {blocked ? (
        <p className="write-feedback failed" role="status">
          An open problem on this task blocks execution. Report progress only if the blocker no
          longer applies.
        </p>
      ) : null}

      <CapabilityGate
        allowed={canSubmit}
        reason="Reporting progress is for field users, contractors, supervisors, and coordinators."
      >
        <form
          className="progress-form"
          onSubmit={(event) => {
            event.preventDefault();
            void submit();
          }}
        >
          <label>
            <span>Execution state</span>
            <select
              value={executionState}
              onChange={(event) => setExecutionState(event.target.value as TaskExecutionState)}
            >
              {submittableStates.map((state) => (
                <option value={state} key={state}>
                  {executionStateLabels[state]}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span>Percent complete</span>
            <input
              value={percentComplete}
              inputMode="numeric"
              onChange={(event) => setPercentComplete(event.target.value)}
              placeholder="0-100"
            />
          </label>
          <label>
            <span>Actual start</span>
            <input
              type="datetime-local"
              value={actualStart}
              onChange={(event) => setActualStart(event.target.value)}
            />
          </label>
          <label>
            <span>Actual finish</span>
            <input
              type="datetime-local"
              value={actualFinish}
              onChange={(event) => setActualFinish(event.target.value)}
            />
          </label>
          <label className="wide-field">
            <span>Comment</span>
            <textarea rows={3} value={comment} onChange={(event) => setComment(event.target.value)} />
          </label>
          <div className="mobile-action-row">
            <button type="submit" disabled={write.busy || validation !== null}>
              Submit for review
            </button>
          </div>
        </form>
      </CapabilityGate>

      {validation !== null ? (
        <p className="write-feedback failed" role="alert">
          {validation}
        </p>
      ) : null}
      <WriteFeedback state={write.state} />
    </>
  );
}

/**
 * Checks a report before it is sent.
 *
 * Caught here so the reporter can correct it immediately rather than discovering a rejection
 * after the fact. The server validates the same rules; this only saves a round trip.
 */
export function validateProgressInput(
  percentComplete: string,
  actualStart: string,
  actualFinish: string
): string | null {
  if (percentComplete !== "") {
    const value = Number(percentComplete);
    if (!Number.isFinite(value) || value < 0 || value > 100) {
      return "Percent complete must be between 0 and 100.";
    }
  }
  if (actualStart !== "" && actualFinish !== "" && new Date(actualFinish) < new Date(actualStart)) {
    return "Actual finish cannot be before actual start.";
  }
  if (actualStart === "" && actualFinish !== "") {
    return "Record an actual start before an actual finish.";
  }
  return null;
}

/** A `datetime-local` value carries no zone; it is read as local time and sent with an offset. */
export function toOffsetDateTime(localValue: string): string | null {
  if (localValue === "") {
    return null;
  }
  const parsed = new Date(localValue);
  return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString();
}

export function matchesFilter(task: ImportReviewTaskRow, needle: string) {
  return [task.name, task.wbs, task.outlineNumber, task.externalId, task.externalUid]
    .filter((value): value is string => typeof value === "string")
    .some((value) => value.toLowerCase().includes(needle));
}
