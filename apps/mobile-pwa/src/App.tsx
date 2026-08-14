import { useCallback, useEffect, useMemo, useState } from "react";
import { CheckCircle2, ClipboardList, RefreshCw, ShieldAlert, UploadCloud } from "lucide-react";
import type {
  ImportReviewTaskRow,
  ProblemRecord,
  TaskExecutionState
} from "@shutdown-tracker/api-client";
import {
  createFieldApiClient,
  describeFieldSession,
  fieldBaseUrl,
  fieldSessionAllows,
  initialFieldSession
} from "./fieldSession";
import { useFieldQueue } from "./useFieldQueue";
import { pendingCount, rejectedCount } from "./offlineQueue";
import type { QueuedProgressUpdate, SyncState } from "./offlineQueue";

/**
 * The Mobile Field App.
 *
 * Built for someone wearing gloves, standing in a plant, with no reliable signal. Every screen
 * works from what is already on the device: the assigned work is cached after the first load,
 * and a progress report is stored locally before anything is sent. Nothing here writes to
 * Microsoft Project — a report goes to a supervisor, who decides whether it is right.
 */

const reportableStates: TaskExecutionState[] = [
  "READY",
  "IN_PROGRESS",
  "PAUSED",
  "BLOCKED",
  "COMPLETED"
];

const executionStateLabels: Record<TaskExecutionState, string> = {
  NOT_STARTED: "Not started",
  READY: "Ready",
  IN_PROGRESS: "In progress",
  PAUSED: "Paused",
  BLOCKED: "Blocked",
  COMPLETED: "Complete"
};

const syncStateLabels: Record<SyncState, string> = {
  PENDING: "Waiting to send",
  SENDING: "Sending",
  SYNCED: "Server received",
  REJECTED: "Could not be sent"
};

type Screen = "work" | "progress" | "problem" | "sync";

export function App() {
  const session = initialFieldSession;
  const client = useMemo(() => createFieldApiClient(session, fieldBaseUrl), [session]);
  const queue = useFieldQueue(client, session.projectId);

  const [screen, setScreen] = useState<Screen>("work");
  const [tasks, setTasks] = useState<ImportReviewTaskRow[]>([]);
  const [problems, setProblems] = useState<ProblemRecord[]>([]);
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);
  const [loadMessage, setLoadMessage] = useState(
    session.live ? "Loading assigned work…" : "No project configured for this device."
  );

  const loadWork = useCallback(async () => {
    if (!session.live) {
      return;
    }
    setLoadMessage("Loading assigned work…");
    try {
      const snapshots = await client.importReview.listSnapshots(session.projectId);
      const newest = [...snapshots].sort((left, right) => right.snapshotVersion - left.snapshotVersion)[0];
      if (!newest) {
        setLoadMessage("No accepted schedule is available for this project yet.");
        return;
      }
      const detail = await client.importReview.getSnapshot(session.projectId, newest.id);
      setTasks(detail.tasks.filter((task) => !task.summary));
      const open = await client.problems.listOpen(session.projectId).catch(() => []);
      setProblems(open);
      setLoadMessage("");
    } catch (error) {
      // Work already on the device stays usable; only the refresh failed.
      setLoadMessage(
        tasks.length > 0
          ? "Could not refresh. Showing the work already on this device."
          : `Could not load work: ${error instanceof Error ? error.message : "unknown error"}`
      );
    }
  }, [client, session.live, session.projectId, tasks.length]);

  useEffect(() => {
    void loadWork();
    // Loading once on start; a refresh is an explicit action so the app does not fight a weak
    // connection while someone is trying to use it.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const blockingTaskIds = useMemo(
    () =>
      new Set(
        problems
          .filter((problem) => problem.blocksExecution && problem.importedTaskId !== null)
          .map((problem) => problem.importedTaskId as string)
      ),
    [problems]
  );

  const selectedTask = tasks.find((task) => task.id === selectedTaskId) ?? null;
  const waiting = pendingCount(queue.state.items);
  const rejected = rejectedCount(queue.state.items);

  return (
    <div className="mobile-frame">
      <header className="mobile-header">
        <div>
          <p className="eyebrow">{describeFieldSession(session)}</p>
          <h1>{screenTitle(screen, selectedTask)}</h1>
        </div>
        <span className={queue.state.online ? "connection-pill" : "connection-pill offline"}>
          {queue.state.online ? "Online" : "Offline"}
        </span>
      </header>

      <main className="mobile-content">
        <section className="sync-banner" aria-label="Sync status">
          <div className="sync-banner-main">
            <span className={`sync-status-dot ${waiting > 0 ? "waiting" : "clear"}`} aria-hidden="true" />
            <div>
              <span>{waiting === 0 ? "Everything sent" : `${waiting} waiting to send`}</span>
              <strong>
                {queue.state.durable
                  ? "Reports are saved on this device until the server has them."
                  : "This browser cannot store reports. Do not close the app before sending."}
              </strong>
            </div>
          </div>
          {rejected > 0 ? (
            <div className="sync-banner-details">
              <span>
                <strong>{rejected}</strong> could not be sent and need attention in Sync.
              </span>
            </div>
          ) : null}
        </section>

        {screen === "work" ? (
          <WorkList
            tasks={tasks}
            blockingTaskIds={blockingTaskIds}
            loadMessage={loadMessage}
            onSelect={(task) => {
              setSelectedTaskId(task.id);
              setScreen("progress");
            }}
          />
        ) : null}

        {screen === "progress" ? (
          selectedTask === null ? (
            <p className="boundary-copy">Choose a task from My Work first.</p>
          ) : (
            <ProgressCapture
              key={selectedTask.id}
              task={selectedTask}
              blocked={blockingTaskIds.has(selectedTask.id)}
              canSubmit={fieldSessionAllows(session, "SUBMIT_TASK_PROGRESS")}
              onCapture={async (request) => {
                await queue.capture(request, selectedTask.name ?? "Task");
                setScreen("sync");
              }}
            />
          )
        ) : null}

        {screen === "problem" ? (
          <ProblemCapture
            tasks={tasks}
            defaultTaskId={selectedTaskId}
            canRaise={fieldSessionAllows(session, "RAISE_PROBLEM")}
            onRaise={async (request) => {
              await client.problems.raise(session.projectId, request);
              await loadWork();
              setScreen("work");
            }}
          />
        ) : null}

        {screen === "sync" ? (
          <SyncQueue
            items={queue.state.items}
            flushing={queue.state.flushing}
            online={queue.state.online}
            onFlush={() => void queue.flush()}
            onRetry={(localId) => void queue.retry(localId)}
            onClear={() => void queue.clearSettled()}
          />
        ) : null}

        <section className="action-band" aria-label="Field actions">
          <button type="button" onClick={() => void loadWork()}>
            <RefreshCw size={18} aria-hidden="true" />
            <span>Refresh work</span>
          </button>
          <button type="button" onClick={() => setScreen("problem")}>
            <ShieldAlert size={18} aria-hidden="true" />
            <span>Raise problem</span>
          </button>
        </section>
      </main>

      <nav className="bottom-nav" aria-label="Field navigation">
        <NavButton icon={ClipboardList} label="My Work" active={screen === "work"} onClick={() => setScreen("work")} />
        <NavButton
          icon={CheckCircle2}
          label="Progress"
          active={screen === "progress"}
          onClick={() => setScreen("progress")}
        />
        <NavButton icon={ShieldAlert} label="Problem" active={screen === "problem"} onClick={() => setScreen("problem")} />
        <NavButton
          icon={UploadCloud}
          label={waiting > 0 ? `Sync (${waiting})` : "Sync"}
          active={screen === "sync"}
          onClick={() => setScreen("sync")}
        />
      </nav>
    </div>
  );
}

function WorkList({
  tasks,
  blockingTaskIds,
  loadMessage,
  onSelect
}: {
  tasks: ImportReviewTaskRow[];
  blockingTaskIds: Set<string>;
  loadMessage: string;
  onSelect: (task: ImportReviewTaskRow) => void;
}) {
  if (tasks.length === 0) {
    return <p className="boundary-copy">{loadMessage || "No work is assigned on this device."}</p>;
  }

  return (
    <section className="work-list" aria-label="Assigned work">
      {loadMessage ? <p className="boundary-copy">{loadMessage}</p> : null}
      {tasks.slice(0, 100).map((task) => (
        <article className="work-card" key={task.id}>
          <div>
            <p>{task.wbs ?? task.outlineNumber ?? task.externalId ?? "—"}</p>
            <h2>{task.name ?? "Unnamed task"}</h2>
            <span>{task.plannedStart ? `Planned ${formatShort(task.plannedStart)}` : "No planned start"}</span>
            <div className="mobile-chip-row">
              {blockingTaskIds.has(task.id) ? (
                <MobileChip label="Blocked" />
              ) : (
                <MobileChip label="No blocker" />
              )}
            </div>
          </div>
          <aside className="work-card-side">
            <span className="percent-pill">{task.percentComplete === null ? "—" : `${task.percentComplete}%`}</span>
            <button type="button" onClick={() => onSelect(task)}>
              Report
            </button>
          </aside>
        </article>
      ))}
    </section>
  );
}

/**
 * Capturing a progress report.
 *
 * The submit button says what actually happens. "Save" would suggest the report is finished;
 * it is not — it goes to a supervisor for review, and the reporter should expect that.
 */
function ProgressCapture({
  task,
  blocked,
  canSubmit,
  onCapture
}: {
  task: ImportReviewTaskRow;
  blocked: boolean;
  canSubmit: boolean;
  onCapture: (request: {
    importedTaskId: string;
    executionState: TaskExecutionState;
    percentComplete: number | null;
    actualStart: string | null;
    actualFinish: string | null;
    comment: string | null;
  }) => Promise<void>;
}) {
  const [executionState, setExecutionState] = useState<TaskExecutionState>("IN_PROGRESS");
  const [percentComplete, setPercentComplete] = useState("");
  const [actualStart, setActualStart] = useState("");
  const [actualFinish, setActualFinish] = useState("");
  const [comment, setComment] = useState("");
  const [busy, setBusy] = useState(false);

  const problem = validateFieldProgress(percentComplete, actualStart, actualFinish);

  return (
    <section className="progress-flow" aria-label="Task progress">
      <div className="section-heading">
        <p className="eyebrow">{task.wbs ?? task.externalId ?? "Task"}</p>
        <h2>{task.name ?? "Unnamed task"}</h2>
      </div>
      <p className="boundary-copy">
        Submitting sends this to your supervisor for review. It does not change the schedule.
      </p>

      {blocked ? (
        <p className="field-alert" role="status">
          An open blocker is recorded against this task.
        </p>
      ) : null}

      <form
        className="progress-form"
        onSubmit={(event) => {
          event.preventDefault();
          if (problem !== null || busy) {
            return;
          }
          setBusy(true);
          void onCapture({
            importedTaskId: task.id,
            executionState,
            percentComplete: percentComplete === "" ? null : Number(percentComplete),
            actualStart: toInstant(actualStart),
            actualFinish: toInstant(actualFinish),
            comment: comment || null
          }).finally(() => setBusy(false));
        }}
      >
        <label>
          <span>State</span>
          <select
            value={executionState}
            onChange={(event) => setExecutionState(event.target.value as TaskExecutionState)}
          >
            {reportableStates.map((state) => (
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
          <input type="datetime-local" value={actualStart} onChange={(event) => setActualStart(event.target.value)} />
        </label>
        <label>
          <span>Actual finish</span>
          <input type="datetime-local" value={actualFinish} onChange={(event) => setActualFinish(event.target.value)} />
        </label>
        <label className="wide-field">
          <span>Comment</span>
          <textarea rows={3} value={comment} onChange={(event) => setComment(event.target.value)} />
        </label>

        {problem !== null ? (
          <p className="field-alert" role="alert">
            {problem}
          </p>
        ) : null}

        <div className="mobile-action-row">
          <button type="submit" disabled={!canSubmit || busy || problem !== null}>
            Submit for review
          </button>
        </div>
        {canSubmit ? null : (
          <p className="boundary-copy">Your role on this project cannot report progress.</p>
        )}
      </form>
    </section>
  );
}

function ProblemCapture({
  tasks,
  defaultTaskId,
  canRaise,
  onRaise
}: {
  tasks: ImportReviewTaskRow[];
  defaultTaskId: string | null;
  canRaise: boolean;
  onRaise: (request: {
    importedTaskId: string | null;
    title: string;
    description: string | null;
    blocksExecution: boolean;
  }) => Promise<void>;
}) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [importedTaskId, setImportedTaskId] = useState(defaultTaskId ?? "");
  const [blocksExecution, setBlocksExecution] = useState(true);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  return (
    <section className="progress-flow" aria-label="Raise a problem">
      <div className="section-heading">
        <p className="eyebrow">Problem</p>
        <h2>What is wrong?</h2>
      </div>
      <p className="boundary-copy">
        A problem is a record someone has to answer, not a message. Marking it blocking says
        work cannot continue.
      </p>

      <form
        className="progress-form"
        onSubmit={(event) => {
          event.preventDefault();
          if (title.trim().length === 0 || busy) {
            return;
          }
          setBusy(true);
          setMessage(null);
          void onRaise({
            importedTaskId: importedTaskId || null,
            title: title.trim(),
            description: description.trim() || null,
            blocksExecution
          })
            .then(() => {
              setTitle("");
              setDescription("");
              setMessage("Problem raised.");
            })
            .catch((error: unknown) =>
              setMessage(error instanceof Error ? error.message : "The problem could not be raised.")
            )
            .finally(() => setBusy(false));
        }}
      >
        <label className="wide-field">
          <span>Title</span>
          <input value={title} onChange={(event) => setTitle(event.target.value)} required />
        </label>
        <label className="wide-field">
          <span>Detail</span>
          <textarea rows={3} value={description} onChange={(event) => setDescription(event.target.value)} />
        </label>
        <label className="wide-field">
          <span>Task</span>
          <select value={importedTaskId} onChange={(event) => setImportedTaskId(event.target.value)}>
            <option value="">Not task specific</option>
            {tasks.slice(0, 200).map((task) => (
              <option value={task.id} key={task.id}>
                {task.name ?? task.id}
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
          <span>Work cannot continue</span>
        </label>
        <div className="mobile-action-row">
          <button type="submit" disabled={!canRaise || busy || title.trim().length === 0}>
            Raise problem
          </button>
        </div>
        {message ? <p className="field-alert" role="status">{message}</p> : null}
        {canRaise ? null : <p className="boundary-copy">Your role cannot raise problems on this project.</p>}
      </form>
    </section>
  );
}

/**
 * The sync queue.
 *
 * Shown as its own screen rather than a background detail. "Did my report get through?" is a
 * question a field user genuinely needs answered, and the honest answer distinguishes saved on
 * the device from received by the server.
 */
function SyncQueue({
  items,
  flushing,
  online,
  onFlush,
  onRetry,
  onClear
}: {
  items: QueuedProgressUpdate[];
  flushing: boolean;
  online: boolean;
  onFlush: () => void;
  onRetry: (localId: string) => void;
  onClear: () => void;
}) {
  return (
    <section className="sync-queue" aria-label="Sync queue">
      <div className="section-heading">
        <p className="eyebrow">Sync queue</p>
        <h2>Reports on this device</h2>
      </div>

      {items.length === 0 ? (
        <p className="boundary-copy">Nothing has been captured on this device.</p>
      ) : (
        items.map((item) => (
          <article className="sync-queue-card" key={item.localId}>
            <div>
              <span>{formatShort(item.capturedAt)}</span>
              <strong>{item.taskName}</strong>
              <p>
                {executionStateLabels[item.request.executionState]}
                {item.request.percentComplete === null || item.request.percentComplete === undefined
                  ? ""
                  : ` · ${item.request.percentComplete}%`}
              </p>
              {item.lastError ? <p className="field-alert">{item.lastError}</p> : null}
            </div>
            <div className="sync-queue-side">
              <MobileChip label={syncStateLabels[item.syncState]} />
              {item.syncState === "REJECTED" ? (
                <button type="button" onClick={() => onRetry(item.localId)}>
                  Try again
                </button>
              ) : null}
            </div>
          </article>
        ))
      )}

      <div className="mobile-action-row">
        <button type="button" disabled={flushing || !online} onClick={onFlush}>
          {flushing ? "Sending…" : "Send now"}
        </button>
        <button type="button" onClick={onClear}>
          Clear sent
        </button>
      </div>
      {online ? null : <p className="boundary-copy">Offline. Reports will send when a connection returns.</p>}
    </section>
  );
}

function NavButton({
  icon: Icon,
  label,
  active,
  onClick
}: {
  icon: typeof ClipboardList;
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      className={active ? "active" : undefined}
      aria-current={active ? "page" : undefined}
      onClick={onClick}
    >
      <Icon size={19} aria-hidden="true" />
      <span>{label}</span>
    </button>
  );
}

function MobileChip({ label }: { label: string }) {
  return <span className={`mobile-chip ${mobileChipTone(label)}`}>{label}</span>;
}

export function mobileChipTone(label: string) {
  const value = label.toLowerCase();

  if (value.includes("blocked") || value.includes("could not") || value.includes("failed")) {
    return "red";
  }
  if (value.includes("waiting") || value.includes("sending")) {
    return "amber";
  }
  if (value.includes("server received") || value.includes("no blocker")) {
    return "green";
  }
  return "blue";
}

export function screenTitle(screen: Screen, task: ImportReviewTaskRow | null) {
  if (screen === "work") {
    return "My Work";
  }
  if (screen === "progress") {
    return task?.name ?? "Progress";
  }
  return screen === "problem" ? "Raise a problem" : "Sync";
}

/**
 * Checks a report on the device.
 *
 * Validated here because the device may be offline: a report captured with an impossible value
 * would sit in the queue and be rejected hours later, by which time the person who could
 * correct it has gone home.
 */
export function validateFieldProgress(
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
  if (actualStart === "" && actualFinish !== "") {
    return "Record an actual start before an actual finish.";
  }
  if (actualStart !== "" && actualFinish !== "" && new Date(actualFinish) < new Date(actualStart)) {
    return "Actual finish cannot be before actual start.";
  }
  return null;
}

export function toInstant(localValue: string): string | null {
  if (localValue === "") {
    return null;
  }
  const parsed = new Date(localValue);
  return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString();
}

function formatShort(value: string) {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return parsed.toLocaleString(undefined, {
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  });
}
