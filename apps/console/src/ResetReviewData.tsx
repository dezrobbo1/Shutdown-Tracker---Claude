import { useState } from "react";
import type { ReviewDataResetResult } from "@shutdown-tracker/api-client";
import type { ConsoleApiClient } from "./consoleApi";
import { CapabilityGate, WriteFeedback, useWriteAction } from "./components";

/**
 * Clears a synthetic review project so the round-trip trial can be walked again from nothing.
 *
 * <p>Its own file rather than a corner of `components.tsx`: it holds real state and real prose, and
 * it is the only control in the console whose failure mode is losing work rather than not saving it.
 *
 * The safeguard is the confirmation, not the absence of the button. This deployment has no login and
 * is reachable from the internet, so hiding the control would protect nobody who had found the URL —
 * what protects the trial is that the server refuses any project without the synthetic marker, and
 * that clearing it requires typing its name.
 */
export function ResetReviewData({
  client,
  projectId,
  allowed,
  onReset
}: {
  client: ConsoleApiClient;
  projectId: string;
  allowed: boolean;
  onReset: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [typed, setTyped] = useState("");
  const [result, setResult] = useState<ReviewDataResetResult | null>(null);
  const reset = useWriteAction();

  const submit = async () => {
    const ok = await reset.run("Review data cleared.", async () => {
      const cleared = await client.reviewReset.run(projectId, typed.trim());
      setResult(cleared);
      return cleared;
    });
    if (ok) {
      setTyped("");
      setOpen(false);
      onReset();
    }
  };

  if (result) {
    const emptied = result.tables.filter((table) => table.rowsDeleted > 0);
    return (
      <div className="reset-panel">
        <p className="eyebrow">Cleared</p>
        {emptied.length === 0 ? (
          <p className="session-note">There was nothing to clear.</p>
        ) : (
          <ul className="reset-summary">
            {emptied.map((table) => (
              <li key={table.name}>
                <span>{table.name}</span>
                <strong>{table.rowsDeleted}</strong>
              </li>
            ))}
          </ul>
        )}
        {result.warnings.map((warning) => (
          <p className="session-note" key={warning}>
            {warning}
          </p>
        ))}
        <button type="button" className="link-button" onClick={() => setResult(null)}>
          Done
        </button>
      </div>
    );
  }

  return (
    <CapabilityGate
      allowed={allowed}
      reason="Clearing review data is limited to the trial's super user."
    >
      <div className="reset-panel">
        {open ? (
          <>
            <p className="eyebrow">Clear review data</p>
            <p className="session-note">
              This deletes the imported schedule and every task in it, all progress, problems,
              evidence, handovers, approvals, export batches, returned candidates, the resource
              links, and the whole audit trail. Uploaded and generated files are deleted too.
            </p>
            <p className="session-note">
              The project and the person signed in here are kept, so the console still works
              afterwards. There is no automatic backup.
            </p>
            <label className="reset-confirm">
              <span>Type the project's name to confirm</span>
              <input
                value={typed}
                onChange={(event) => setTyped(event.target.value)}
                aria-label="Project name confirmation"
                autoComplete="off"
              />
            </label>
            <div className="reset-actions">
              <button
                type="button"
                className="danger-button"
                disabled={typed.trim().length === 0 || reset.busy}
                onClick={() => void submit()}
              >
                Clear it
              </button>
              <button
                type="button"
                className="link-button"
                onClick={() => {
                  setOpen(false);
                  setTyped("");
                }}
              >
                Cancel
              </button>
            </div>
            <WriteFeedback state={reset.state} />
          </>
        ) : (
          <button type="button" className="danger-button" onClick={() => setOpen(true)}>
            Clear review data
          </button>
        )}
      </div>
    </CapabilityGate>
  );
}
