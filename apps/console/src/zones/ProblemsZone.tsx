import { useCallback, useState } from "react";
import type { ActionRecord, ProblemRecord, ProblemSeverity } from "@shutdown-tracker/api-client";
import {
  BoundaryNote,
  CapabilityGate,
  PanelHeading,
  ResourceView,
  StatusChip,
  WriteFeedback,
  useWriteAction
} from "../components";
import { formatDateTime, shortId } from "../formatting";
import { useAsyncResource } from "../useAsyncResource";
import { leafTasks, taskLabel, useSnapshotTasks } from "../useSnapshotTasks";
import type { ZoneProps } from "./ZoneProps";

const severities: ProblemSeverity[] = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];

/**
 * Problems and the actions taken about them.
 *
 * A problem is a structured record, not a message: it says what is wrong, whether work can
 * continue, and who owns it. That is what makes it answerable later, and what keeps a blocker
 * from living only in a supervisor's memory between shifts.
 */
export function ProblemsZone({ session, client }: ZoneProps) {
  const projectId = session.projectId;
  const tasks = useSnapshotTasks(client, projectId, session.live);
  const snapshotTasks = tasks.state.status === "loaded" ? tasks.state.value : null;

  const problems = useAsyncResource<ProblemRecord[]>(
    useCallback(() => client.problems.listOpen(projectId), [client, projectId]),
    { enabled: session.live, idleMessage: "Configure a project and actor to load problems." }
  );

  const actions = useAsyncResource<ActionRecord[]>(
    useCallback(() => client.actions.listOpen(projectId), [client, projectId]),
    { enabled: session.live, idleMessage: "Configure a project and actor to load actions." }
  );

  const raise = useWriteAction();
  const manage = useWriteAction();

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [severity, setSeverity] = useState<ProblemSeverity>("MEDIUM");
  const [blocksExecution, setBlocksExecution] = useState(false);
  const [importedTaskId, setImportedTaskId] = useState("");
  const [actionTitles, setActionTitles] = useState<Record<string, string>>({});

  const raiseProblem = async () => {
    if (title.trim().length === 0) {
      return;
    }
    const ok = await raise.run("Problem raised and attributed to you.", () =>
      client.problems.raise(projectId, {
        title: title.trim(),
        description: description.trim() || null,
        severity,
        blocksExecution,
        importedTaskId: importedTaskId || null
      })
    );
    if (ok) {
      setTitle("");
      setDescription("");
      setBlocksExecution(false);
      await problems.reload();
    }
  };

  const closeProblem = async (problem: ProblemRecord) => {
    const ok = await manage.run("Problem closed. The record and its history remain readable.", () =>
      client.problems.close(projectId, problem.id)
    );
    if (ok) {
      await problems.reload();
    }
  };

  const createAction = async (problem: ProblemRecord) => {
    const actionTitle = (actionTitles[problem.id] ?? "").trim();
    if (actionTitle.length === 0) {
      return;
    }
    const ok = await manage.run("Action created against the problem.", () =>
      client.actions.create(projectId, {
        problemId: problem.id,
        importedTaskId: problem.importedTaskId,
        title: actionTitle
      })
    );
    if (ok) {
      setActionTitles((current) => ({ ...current, [problem.id]: "" }));
      await actions.reload();
    }
  };

  const completeAction = async (action: ActionRecord) => {
    const ok = await manage.run("Action marked complete.", () =>
      client.actions.complete(projectId, action.id)
    );
    if (ok) {
      await actions.reload();
    }
  };

  return (
    <div className="zone-grid">
      <article className="work-panel">
        <PanelHeading eyebrow="Operational records" title="Open problems">
          <StatusChip
            label={problems.state.status === "loaded" ? `${problems.state.value.length} open` : "Loading"}
            tone={problems.state.status === "loaded" && problems.state.value.length === 0 ? "green" : "amber"}
          />
        </PanelHeading>
        <BoundaryNote>
          Marking a problem as blocking records that work cannot proceed. It does not move any
          date — the schedule consequence is a planner decision made from this evidence.
        </BoundaryNote>

        <ResourceView
          resource={problems}
          emptyWhen={(value) => value.length === 0}
          emptyMessage="No open problems on this project."
        >
          {(value) => (
            <div className="problem-list">
              {value.map((problem) => (
                <article className="problem-card" key={problem.id}>
                  <div className="queue-card-heading">
                    <div>
                      <p className="eyebrow">
                        {snapshotTasks === null
                          ? problem.importedTaskId
                            ? `Task ${shortId(problem.importedTaskId)}`
                            : "Project level"
                          : problem.importedTaskId
                            ? taskLabel(snapshotTasks, problem.importedTaskId)
                            : "Project level"}
                      </p>
                      <h3>{problem.title}</h3>
                    </div>
                    <StatusChip label={problem.severity} tone={severityTone(problem.severity)} />
                  </div>
                  {problem.description ? <p className="queue-comment">{problem.description}</p> : null}
                  <div className="chip-list">
                    <StatusChip label={problem.status} />
                    {problem.blocksExecution ? <StatusChip label="Blocks execution" tone="red" /> : null}
                  </div>

                  <CapabilityGate
                    allowed={session.canManageAction}
                    reason="Deciding what happens about a problem is a supervisor, coordinator, or shutdown control responsibility."
                  >
                    <label className="wide-field">
                      <span>New action</span>
                      <input
                        value={actionTitles[problem.id] ?? ""}
                        onChange={(event) =>
                          setActionTitles((current) => ({ ...current, [problem.id]: event.target.value }))
                        }
                        placeholder="What will be done about it"
                      />
                    </label>
                    <div className="mobile-action-row">
                      <button type="button" disabled={manage.busy} onClick={() => void createAction(problem)}>
                        Add action
                      </button>
                      <button type="button" disabled={manage.busy} onClick={() => void closeProblem(problem)}>
                        Close problem
                      </button>
                    </div>
                  </CapabilityGate>
                </article>
              ))}
            </div>
          )}
        </ResourceView>
        <WriteFeedback state={manage.state} />
      </article>

      <div className="zone-column">
        <article className="work-panel">
          <PanelHeading eyebrow="Raise" title="New problem" />
          <CapabilityGate
            allowed={session.canRaiseProblem}
            reason="Your role on this project cannot raise problems."
          >
            <form
              className="progress-form"
              onSubmit={(event) => {
                event.preventDefault();
                void raiseProblem();
              }}
            >
              <label className="wide-field">
                <span>Title</span>
                <input value={title} onChange={(event) => setTitle(event.target.value)} required />
              </label>
              <label className="wide-field">
                <span>Description</span>
                <textarea rows={3} value={description} onChange={(event) => setDescription(event.target.value)} />
              </label>
              <label>
                <span>Severity</span>
                <select
                  value={severity}
                  onChange={(event) => setSeverity(event.target.value as ProblemSeverity)}
                >
                  {severities.map((value) => (
                    <option value={value} key={value}>
                      {value}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span>Task</span>
                <select value={importedTaskId} onChange={(event) => setImportedTaskId(event.target.value)}>
                  <option value="">Project level</option>
                  {snapshotTasks === null
                    ? null
                    : leafTasks(snapshotTasks)
                        .slice(0, 300)
                        .map((task) => (
                          <option value={task.id} key={task.id}>
                            {task.name ?? task.externalId ?? task.id}
                          </option>
                        ))}
                </select>
              </label>
              <label className="wide-field checkbox-field">
                <input
                  type="checkbox"
                  checked={blocksExecution}
                  onChange={(event) => setBlocksExecution(event.target.checked)}
                />
                <span>Work cannot proceed until this is resolved</span>
              </label>
              <div className="mobile-action-row">
                <button type="submit" disabled={raise.busy || title.trim().length === 0}>
                  Raise problem
                </button>
              </div>
            </form>
          </CapabilityGate>
          <WriteFeedback state={raise.state} />
        </article>

        <article className="work-panel">
          <PanelHeading eyebrow="Follow-up" title="Open actions" />
          <ResourceView
            resource={actions}
            emptyWhen={(value) => value.length === 0}
            emptyMessage="No open actions."
          >
            {(value) => (
              <div className="problem-list">
                {value.map((action) => (
                  <article className="handover-card" key={action.id}>
                    <div>
                      <strong>{action.title}</strong>
                      <p>
                        {action.dueAt ? `Due ${formatDateTime(action.dueAt)}` : "No due date"} ·{" "}
                        {action.problemId ? `Problem ${shortId(action.problemId)}` : "Standalone"}
                      </p>
                    </div>
                    <div className="chip-list">
                      <StatusChip label={action.status} />
                    </div>
                    <CapabilityGate
                      allowed={session.canManageAction}
                      reason="Completing an action is a supervisor, coordinator, or shutdown control responsibility."
                    >
                      <button type="button" disabled={manage.busy} onClick={() => void completeAction(action)}>
                        Mark complete
                      </button>
                    </CapabilityGate>
                  </article>
                ))}
              </div>
            )}
          </ResourceView>
        </article>
      </div>
    </div>
  );
}

export function severityTone(severity: ProblemSeverity) {
  if (severity === "CRITICAL" || severity === "HIGH") {
    return "red";
  }
  return severity === "MEDIUM" ? "amber" : "blue";
}
