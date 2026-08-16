import { useState } from "react";
import type { ReactNode } from "react";
import { toneForState } from "./formatting";
import { describeApiError } from "./useAsyncResource";
import type { Resource, ResourceState } from "./useAsyncResource";

/** Presentation pieces shared by the zones. */

export function StatusChip({ label, tone }: { label: string; tone?: string }) {
  return <span className={`status-chip ${tone ?? toneForState(label)}`}>{label}</span>;
}

export function PanelHeading({
  eyebrow,
  title,
  children
}: {
  eyebrow: string;
  title: string;
  children?: ReactNode;
}) {
  return (
    <div className="panel-heading">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h2>{title}</h2>
      </div>
      {children}
    </div>
  );
}

/**
 * Renders whichever of the four states a resource is in.
 *
 * Empty is deliberately separate from idle and error, and each carries its own wording: an
 * empty review queue means the work is done, an idle one means nothing was requested, and a
 * failed one means the answer is unknown. Those must not look alike.
 */
export function ResourceView<T>({
  resource,
  emptyWhen,
  emptyMessage,
  children
}: {
  resource: Resource<T>;
  emptyWhen?: (value: T) => boolean;
  emptyMessage?: string;
  children: (value: T) => ReactNode;
}) {
  const { state } = resource;

  if (state.status === "idle") {
    return <p className="boundary-copy">{state.message}</p>;
  }
  if (state.status === "loading") {
    return <p className="boundary-copy">Loading…</p>;
  }
  if (state.status === "error") {
    return (
      <p className="boundary-copy zone-error" role="alert">
        {state.message}
      </p>
    );
  }
  if (emptyWhen?.(state.value)) {
    return <p className="boundary-copy">{emptyMessage ?? "Nothing to show."}</p>;
  }
  return <>{children(state.value)}</>;
}

export function isLoaded<T>(state: ResourceState<T>): state is { status: "loaded"; value: T } {
  return state.status === "loaded";
}

export type WriteState =
  | { status: "idle" }
  | { status: "working" }
  | { status: "done"; message: string }
  | { status: "failed"; message: string };

/**
 * Runs a write and reports what happened.
 *
 * Every write in this product is attributable and most are irreversible — an accepted
 * snapshot, an approved export. Silence after a click is not acceptable feedback, so the
 * outcome is always stated, including the reason for a refusal.
 */
export function useWriteAction() {
  const [state, setState] = useState<WriteState>({ status: "idle" });

  const run = async (successMessage: string, operation: () => Promise<unknown>) => {
    setState({ status: "working" });
    try {
      await operation();
      setState({ status: "done", message: successMessage });
      return true;
    } catch (error) {
      setState({ status: "failed", message: describeApiError(error) });
      return false;
    }
  };

  return { state, run, busy: state.status === "working" };
}

export function WriteFeedback({ state }: { state: WriteState }) {
  if (state.status === "idle") {
    return null;
  }
  if (state.status === "working") {
    return <p className="write-feedback">Working…</p>;
  }
  return (
    <p
      className={state.status === "failed" ? "write-feedback failed" : "write-feedback done"}
      role={state.status === "failed" ? "alert" : "status"}
    >
      {state.message}
    </p>
  );
}

/**
 * A control the current role may not use.
 *
 * Disabled rather than hidden, with the reason on the element. Hiding it leaves someone
 * hunting for a control that is not there; showing why makes it obvious that the next step
 * belongs to a different role.
 */
export function CapabilityGate({
  allowed,
  reason,
  children
}: {
  allowed: boolean;
  reason: string;
  children: ReactNode;
}) {
  if (allowed) {
    return <>{children}</>;
  }
  return (
    <div className="capability-gate" title={reason}>
      <fieldset disabled>{children}</fieldset>
      <span className="capability-reason">{reason}</span>
    </div>
  );
}

/** A labelled read-only value. */
export function DetailValue({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="detail-value">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

/**
 * A statement of something the product will not do.
 *
 * Kept in the interface rather than only in documentation. The boundary is the reason a
 * planner can trust the tool with their schedule, and it should be visible at the point where
 * someone might otherwise expect the tool to recalculate something.
 */
export function BoundaryNote({ children }: { children: ReactNode }) {
  return <p className="boundary-copy">{children}</p>;
}
