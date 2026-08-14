import { useCallback, useState } from "react";
import type {
  CategoryResolutionSummary,
  CategorySourceMode,
  ImportProfileRecord,
  MappingHealth,
  OperationalCategoryRecord
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
import { useAsyncResource } from "../useAsyncResource";
import { useSnapshotTasks } from "../useSnapshotTasks";
import type { ZoneProps } from "./ZoneProps";

const sourceModes: { value: CategorySourceMode; label: string; help: string }[] = [
  {
    value: "TASK_FIELD",
    label: "Task field",
    help: "Read from a Project field or a planner's custom-field alias, such as Text3 aliased to Discipline."
  },
  {
    value: "HIERARCHY_ANCESTOR",
    label: "Summary ancestor",
    help: "Read from the name of an ancestor summary task at a chosen outline level."
  },
  {
    value: "RESOURCE_GROUP",
    label: "Resource group",
    help: "Read from the Group field of the resources assigned to the task."
  }
];

/**
 * Operational Categories, and the Import Profile that defines them.
 *
 * Every shutdown labels its work differently, and the planner already encoded those labels in
 * the Project file. Rather than imposing a fixed taxonomy, a profile says where to read each
 * category from. Classification is descriptive only: putting a task in a category never grants
 * anyone authority over it.
 */
export function MappingZone({ session, client }: ZoneProps) {
  const projectId = session.projectId;
  const tasks = useSnapshotTasks(client, projectId, session.live);
  const snapshotId = tasks.state.status === "loaded" ? tasks.state.value.snapshotId : null;

  const profile = useAsyncResource<ImportProfileRecord | null>(
    useCallback(
      () => client.importProfiles.getActive(projectId).catch(() => null),
      [client, projectId]
    ),
    { enabled: session.live, idleMessage: "Configure a project and actor to load the import profile." }
  );

  const activeProfile = profile.state.status === "loaded" ? profile.state.value : null;

  const categories = useAsyncResource<OperationalCategoryRecord[]>(
    useCallback(
      () =>
        activeProfile === null
          ? Promise.resolve([])
          : client.importProfiles.listCategories(projectId, activeProfile.id),
      [client, projectId, activeProfile]
    ),
    { enabled: session.live && activeProfile !== null, idleMessage: "Create an import profile to define categories." }
  );

  const [resolution, setResolution] = useState<CategoryResolutionSummary[] | null>(null);
  const [readiness, setReadiness] = useState<string[] | null>(null);

  const write = useWriteAction();
  const [profileName, setProfileName] = useState("");
  const [categoryName, setCategoryName] = useState("");
  const [sourceMode, setSourceMode] = useState<CategorySourceMode>("TASK_FIELD");
  const [sourceField, setSourceField] = useState("");
  const [sourceOutlineLevel, setSourceOutlineLevel] = useState("1");
  const [multiValued, setMultiValued] = useState(false);
  const [requiredForExecution, setRequiredForExecution] = useState(false);

  const createProfile = async () => {
    if (profileName.trim().length === 0) {
      return;
    }
    const ok = await write.run("Profile created and activated for this project.", async () => {
      const created = await client.importProfiles.create(projectId, profileName.trim());
      await client.importProfiles.activate(projectId, created.id);
    });
    if (ok) {
      setProfileName("");
      await profile.reload();
    }
  };

  const addCategory = async () => {
    if (activeProfile === null || categoryName.trim().length === 0) {
      return;
    }
    const validation = validateCategoryInput(sourceMode, sourceField, sourceOutlineLevel);
    if (validation !== null) {
      return;
    }
    const ok = await write.run("Category added to the profile.", () =>
      client.importProfiles.addCategory(projectId, activeProfile.id, {
        name: categoryName.trim(),
        sourceMode,
        sourceField: sourceMode === "TASK_FIELD" ? sourceField.trim() : null,
        sourceOutlineLevel: sourceMode === "HIERARCHY_ANCESTOR" ? Number(sourceOutlineLevel) : null,
        multiValued,
        requiredForExecution
      })
    );
    if (ok) {
      setCategoryName("");
      setSourceField("");
      await categories.reload();
    }
  };

  const resolveAgainstSnapshot = async () => {
    if (snapshotId === null) {
      return;
    }
    await write.run("Resolved against the current snapshot.", async () => {
      const summaries = await client.importProfiles.resolveSnapshot(projectId, snapshotId);
      setResolution(summaries);
      const missing = await client.importProfiles.executionReadiness(projectId, snapshotId);
      setReadiness(missing);
      await categories.reload();
    });
  };

  const categoryValidation = validateCategoryInput(sourceMode, sourceField, sourceOutlineLevel);

  return (
    <div className="zone-grid">
      <article className="work-panel">
        <PanelHeading eyebrow="Import profile" title={activeProfile?.name ?? "No active profile"}>
          {activeProfile ? <StatusChip label={`Version ${activeProfile.version}`} tone="blue" /> : null}
        </PanelHeading>
        <BoundaryNote>
          A profile is versioned. Re-importing a changed file reports each category's health
          rather than silently remapping work to a different category.
        </BoundaryNote>

        {activeProfile === null ? (
          <CapabilityGate
            allowed={session.canManageMapping}
            reason="Import profiles are planner-owned."
          >
            <form
              className="progress-form"
              onSubmit={(event) => {
                event.preventDefault();
                void createProfile();
              }}
            >
              <label className="wide-field">
                <span>Profile name</span>
                <input
                  value={profileName}
                  onChange={(event) => setProfileName(event.target.value)}
                  placeholder="Kiln shutdown 2026"
                  required
                />
              </label>
              <div className="mobile-action-row">
                <button type="submit" disabled={write.busy || profileName.trim().length === 0}>
                  Create and activate
                </button>
              </div>
            </form>
          </CapabilityGate>
        ) : (
          <>
            <ResourceView
              resource={categories}
              emptyWhen={(value) => value.length === 0}
              emptyMessage="This profile has no categories yet."
            >
              {(value) => (
                <div className="review-table" role="table" aria-label="Operational categories">
                  <div className="review-row review-head" role="row">
                    <span role="columnheader">Category</span>
                    <span role="columnheader">Read from</span>
                    <span role="columnheader">Required</span>
                    <span role="columnheader">Health</span>
                  </div>
                  {value.map((category) => (
                    <div className="review-row" role="row" key={category.id}>
                      <span role="cell">{category.name}</span>
                      <span role="cell">{describeSource(category)}</span>
                      <span role="cell">{category.requiredForExecution ? "For execution" : "Optional"}</span>
                      <span role="cell">
                        <StatusChip label={healthLabels[category.health]} tone={healthTone(category.health)} />
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </ResourceView>

            <div className="mobile-action-row">
              <button type="button" disabled={write.busy || snapshotId === null} onClick={() => void resolveAgainstSnapshot()}>
                Resolve against current snapshot
              </button>
            </div>

            {resolution !== null ? (
              <>
                <h3 className="subheading">Resolution</h3>
                <div className="review-table dense" role="table" aria-label="Category resolution">
                  <div className="review-row review-head" role="row">
                    <span role="columnheader">Category</span>
                    <span role="columnheader">Tasks classified</span>
                    <span role="columnheader">Distinct values</span>
                    <span role="columnheader">Health</span>
                  </div>
                  {resolution.map((summary) => (
                    <div className="review-row" role="row" key={summary.operationalCategoryId}>
                      <span role="cell">{summary.categoryName}</span>
                      <span role="cell">{summary.taskCount}</span>
                      <span role="cell">{summary.distinctValueCount}</span>
                      <span role="cell">
                        <StatusChip label={healthLabels[summary.health]} tone={healthTone(summary.health)} />
                      </span>
                    </div>
                  ))}
                </div>
              </>
            ) : null}

            {readiness !== null ? (
              <p className={readiness.length === 0 ? "write-feedback done" : "write-feedback failed"} role="status">
                {readiness.length === 0
                  ? "Every leaf task carries the classifications this project requires."
                  : `${readiness.length} leaf task${readiness.length === 1 ? "" : "s"} are missing a required classification and are not ready for execution.`}
              </p>
            ) : null}
          </>
        )}
        <WriteFeedback state={write.state} />
      </article>

      {activeProfile === null ? null : (
        <article className="work-panel">
          <PanelHeading eyebrow="Define" title="Add a category" />
          <CapabilityGate allowed={session.canManageMapping} reason="Import profiles are planner-owned.">
            <form
              className="progress-form"
              onSubmit={(event) => {
                event.preventDefault();
                void addCategory();
              }}
            >
              <label className="wide-field">
                <span>Name</span>
                <input
                  value={categoryName}
                  onChange={(event) => setCategoryName(event.target.value)}
                  placeholder="Discipline"
                  required
                />
              </label>
              <label className="wide-field">
                <span>Source</span>
                <select
                  value={sourceMode}
                  onChange={(event) => setSourceMode(event.target.value as CategorySourceMode)}
                >
                  {sourceModes.map((mode) => (
                    <option value={mode.value} key={mode.value}>
                      {mode.label}
                    </option>
                  ))}
                </select>
              </label>
              <BoundaryNote>{sourceModes.find((mode) => mode.value === sourceMode)?.help}</BoundaryNote>

              {sourceMode === "TASK_FIELD" ? (
                <label className="wide-field">
                  <span>Field or alias</span>
                  <input
                    value={sourceField}
                    onChange={(event) => setSourceField(event.target.value)}
                    placeholder="Text3, or the alias the planner gave it"
                  />
                </label>
              ) : null}

              {sourceMode === "HIERARCHY_ANCESTOR" ? (
                <label>
                  <span>Outline level</span>
                  <input
                    value={sourceOutlineLevel}
                    inputMode="numeric"
                    onChange={(event) => setSourceOutlineLevel(event.target.value)}
                  />
                </label>
              ) : null}

              <label className="wide-field checkbox-field">
                <input
                  type="checkbox"
                  checked={multiValued}
                  onChange={(event) => setMultiValued(event.target.checked)}
                />
                <span>A task may carry more than one value</span>
              </label>
              <label className="wide-field checkbox-field">
                <input
                  type="checkbox"
                  checked={requiredForExecution}
                  onChange={(event) => setRequiredForExecution(event.target.checked)}
                />
                <span>A leaf task without this value is not ready for execution</span>
              </label>

              <div className="mobile-action-row">
                <button
                  type="submit"
                  disabled={write.busy || categoryName.trim().length === 0 || categoryValidation !== null}
                >
                  Add category
                </button>
              </div>
            </form>
          </CapabilityGate>
          {categoryValidation !== null ? (
            <p className="write-feedback failed" role="alert">
              {categoryValidation}
            </p>
          ) : null}
        </article>
      )}
    </div>
  );
}

export const healthLabels: Record<MappingHealth, string> = {
  HEALTHY: "Healthy",
  HEALTHY_WITH_NEW_VALUES: "Healthy, new values seen",
  CONFIGURATION_CHANGED: "Configuration changed",
  CONFIRMATION_REQUIRED: "Needs confirmation",
  BROKEN: "Broken",
  PROFILE_MISMATCH: "Profile mismatch"
};

export function healthTone(health: MappingHealth) {
  if (health === "BROKEN" || health === "PROFILE_MISMATCH") {
    return "red";
  }
  if (health === "HEALTHY") {
    return "green";
  }
  return "amber";
}

export function describeSource(category: OperationalCategoryRecord) {
  if (category.sourceMode === "TASK_FIELD") {
    return `Task field ${category.sourceField ?? "—"}`;
  }
  if (category.sourceMode === "HIERARCHY_ANCESTOR") {
    return `Summary ancestor at outline level ${category.sourceOutlineLevel ?? "—"}`;
  }
  return "Assigned resource group";
}

/** A source mode without its configuration would resolve to nothing on every task. */
export function validateCategoryInput(
  sourceMode: CategorySourceMode,
  sourceField: string,
  sourceOutlineLevel: string
): string | null {
  if (sourceMode === "TASK_FIELD" && sourceField.trim().length === 0) {
    return "A task-field category needs the field name or alias to read.";
  }
  if (sourceMode === "HIERARCHY_ANCESTOR") {
    const level = Number(sourceOutlineLevel);
    if (!Number.isInteger(level) || level < 0) {
      return "An ancestor category needs a whole-number outline level.";
    }
  }
  return null;
}
