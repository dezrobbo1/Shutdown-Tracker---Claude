import { useCallback, useState } from "react";
import type { LinkCandidates, ProjectResourceLinkRecord } from "@shutdown-tracker/api-client";
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
import type { ZoneProps } from "./ZoneProps";

/**
 * Who each Microsoft Project resource is.
 *
 * The field app cannot show somebody their own work until something says which resource in the
 * schedule is them. This is that something, and it is a list a planner curates by hand.
 *
 * It is deliberately not a name-matching screen. Project resources are named however the planner
 * typed them — "J. Okafor", "Fitter 2", "MECH-CREW-A" — and guessing which user that is would be
 * an uncertain remap, which the mapping rules refuse to activate without review.
 *
 * A link decides what a work list shows and nothing else. It grants no permission, takes none
 * away, and no authorization check anywhere reads it. That is the point stated on the panel: a
 * planner setting these up is arranging visibility, not access, and should not think otherwise.
 */
export function PeopleZone({ session, client }: ZoneProps) {
  const projectId = session.projectId;

  const links = useAsyncResource<ProjectResourceLinkRecord[]>(
    useCallback(() => client.assignedWork.listLinks(projectId), [client, projectId]),
    { enabled: session.live, idleMessage: "Configure a project and actor to load resource links." }
  );

  // Only a planner or admin may read the candidate lists, so this stays idle for everyone else
  // rather than erroring in a panel they cannot act on anyway.
  const candidates = useAsyncResource<LinkCandidates>(
    useCallback(() => client.assignedWork.candidates(projectId), [client, projectId]),
    {
      enabled: session.live && session.canManageResourceLink,
      idleMessage: "Linking a resource to a person is planner-owned."
    }
  );

  const write = useWriteAction();
  const [userId, setUserId] = useState("");
  const [resourceExternalUid, setResourceExternalUid] = useState("");

  const candidateValue = candidates.state.status === "loaded" ? candidates.state.value : null;

  const createLink = async () => {
    if (userId === "" || resourceExternalUid === "") {
      return;
    }
    const ok = await write.run("Linked. That person's work list now narrows to this resource.", () =>
      client.assignedWork.link(projectId, { userId, resourceExternalUid })
    );
    if (ok) {
      setUserId("");
      setResourceExternalUid("");
      await links.reload();
      await candidates.reload();
    }
  };

  const revoke = async (link: ProjectResourceLinkRecord) => {
    const ok = await write.run("Link revoked. It stays on the record as revoked, not deleted.", () =>
      client.assignedWork.revokeLink(projectId, link.id)
    );
    if (ok) {
      await links.reload();
      await candidates.reload();
    }
  };

  return (
    <div className="zone-grid">
      <article className="work-panel">
        <PanelHeading eyebrow="Field identity" title="Who each Project resource is" />
        <BoundaryNote>
          A link decides which work the field app shows a person. It grants no permission and takes
          none away — what somebody may do is still their role on this project.
        </BoundaryNote>

        <ResourceView
          resource={links}
          emptyWhen={(value) => value.length === 0}
          emptyMessage="No resource is linked to anyone yet, so every field user sees an empty work list."
        >
          {(value) => (
            <div className="review-table" role="table" aria-label="Resource links">
              <div className="review-row review-head" role="row">
                <span role="columnheader">Project resource</span>
                <span role="columnheader">Person</span>
                <span role="columnheader">In current schedule</span>
                <span role="columnheader">State</span>
              </div>
              {value.map((link) => (
                <div className="review-row" role="row" key={link.id}>
                  <span role="cell">
                    {link.resourceNameInSnapshot ?? link.resourceNameAtLink ?? link.resourceExternalUid}
                    <small> {link.resourceExternalUid}</small>
                  </span>
                  <span role="cell">{link.userDisplayName}</span>
                  <span role="cell">
                    {/* The state that would otherwise be invisible: a link whose resource the newest
                        accepted schedule no longer carries silently empties somebody's work list. */}
                    {link.matchedInSnapshot ? (
                      <StatusChip label="Present" tone="green" />
                    ) : (
                      <StatusChip label="Not in this schedule" tone="amber" />
                    )}
                  </span>
                  <span role="cell">
                    {link.active ? (
                      <CapabilityGate
                        allowed={session.canManageResourceLink}
                        reason="Linking a resource to a person is planner-owned."
                      >
                        <button type="button" disabled={write.busy} onClick={() => void revoke(link)}>
                          Revoke
                        </button>
                      </CapabilityGate>
                    ) : (
                      <StatusChip label="Revoked" tone="grey" />
                    )}
                  </span>
                </div>
              ))}
            </div>
          )}
        </ResourceView>
        <WriteFeedback state={write.state} />
      </article>

      <article className="work-panel">
        <PanelHeading eyebrow="Link a resource" title="Link a person to their resource" />
        <ResourceView resource={candidates}>
          {(value) =>
            value.projectSnapshotId === null ? (
              <BoundaryNote>
                No schedule has been accepted for this project, so there are no resources to link
                yet.
              </BoundaryNote>
            ) : (
              <CapabilityGate
                allowed={session.canManageResourceLink}
                reason="Linking a resource to a person is planner-owned."
              >
                <form
                  className="progress-form"
                  onSubmit={(event) => {
                    event.preventDefault();
                    void createLink();
                  }}
                >
                  <label className="wide-field">
                    <span>Person</span>
                    <select value={userId} onChange={(event) => setUserId(event.target.value)} required>
                      <option value="">Choose a member of this project</option>
                      {value.users.map((user) => (
                        <option value={user.userId} key={user.userId}>
                          {`${user.displayName} — ${user.role}`}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="wide-field">
                    <span>Project resource</span>
                    <select
                      value={resourceExternalUid}
                      onChange={(event) => setResourceExternalUid(event.target.value)}
                      required
                    >
                      <option value="">Choose a resource from the accepted schedule</option>
                      {value.resources.map((resource) => (
                        <option
                          value={resource.resourceExternalUid}
                          key={resource.resourceExternalUid}
                          disabled={resource.linkedUserId !== null}
                        >
                          {resourceLabel(resource)}
                        </option>
                      ))}
                    </select>
                  </label>
                  <div className="mobile-action-row">
                    <button
                      type="submit"
                      disabled={write.busy || userId === "" || resourceExternalUid === ""}
                    >
                      Link
                    </button>
                  </div>
                </form>
              </CapabilityGate>
            )
          }
        </ResourceView>
      </article>
    </div>
  );
}

/**
 * How a resource reads in the picker.
 *
 * The task count is here because a shutdown schedule carries plant, materials and cost resources
 * beside people, and only the ones work is booked against are worth linking. An already-linked
 * resource says who holds it rather than being hidden, so a planner can see why it is unavailable.
 */
export function resourceLabel(resource: {
  resourceExternalUid: string;
  name: string | null;
  assignedLeafTaskCount: number;
  linkedUserDisplayName: string | null;
}) {
  const name = resource.name ?? resource.resourceExternalUid;
  const work =
    resource.assignedLeafTaskCount === 1 ? "1 task" : `${resource.assignedLeafTaskCount} tasks`;
  return resource.linkedUserDisplayName === null
    ? `${name} — ${work}`
    : `${name} — ${work} — already linked to ${resource.linkedUserDisplayName}`;
}
