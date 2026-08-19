# Field Identity and Assigned Work

## The question this answers

A field user opens the mobile app and expects to see their work. Microsoft Project already knows
which work is theirs — it is booked against a resource — but Project's resources and Shutdown
Tracker's users are two different populations of names, and nothing connected them. Until this
existed the field app listed every leaf task in the accepted snapshot and told the reader so.

## The decision

**A Microsoft Project resource is linked to a Shutdown Tracker user by an explicit, project-scoped
link that a planner or administrator creates. It is never inferred, and it grants relevance only.**

Three rules already in this repository decide that shape, and each rules out an alternative.

### It is explicit, never inferred

Project resources are named however the planner typed them: `J. Okafor`, `Fitter 2`,
`MECH-CREW-A`. Matching those to user accounts by name, or by an email in a custom field, is a
guess about identity. [Project Operational Mapping](project-operational-mapping.md) already
governs uncertain source identity: Shutdown Tracker "may present evidence and a proposed remap for
planner review. It must not silently activate that remap when identity is uncertain."

Automatic matching is therefore not available as a default. A link exists because somebody with
the capability made it, and the audit event names them. Proposing candidate matches for a human to
confirm remains open and would obey the same rule.

### It grants relevance, not permission

`AGENTS.md` keeps visibility/relevance, responsibility, update permission, review permission and
export authority separate, and states that Project-derived membership is not application
authorization. A link decides what a work list shows. What a person may then do is resolved from
`project_memberships` exactly as before.

Two consequences follow, and both are deliberate:

- Linking somebody to a resource **cannot widen** what they may do. No authorization check reads
  the link.
- Not being linked **cannot narrow** it. A supervisor reporting on behalf of a crew, or a field
  user sent to cover a job that is not booked to them, is unaffected. Their work list is empty;
  their permissions are not.

This is why `SUBMIT_TASK_PROGRESS` and `SUBMIT_CRITICAL_UPDATE` are still granted by role even
though the link now exists. Narrowing them to it would turn Project resource data into an
authorization source, and would lock somebody out of work they have been told to do because a
planner had not got to the link yet.

### It survives re-import

The link is keyed on the project and the resource's Project UID, not on the `imported_resources`
row, because that row belongs to one snapshot and a new import replaces it.

A snapshot that no longer carries the resource does **not** delete the link. Operational Mapping
requires that configuration for values absent from the current snapshot "remain available for
historical records and future reappearance rather than being automatically deleted." The link is
kept, and reported as unmatched.

## What each surface must show

An empty work list is not one fact. Four situations produce one, and only the last means the
reader is finished:

| Situation | What the field app says |
| --- | --- |
| No accepted snapshot | No schedule has been accepted, so no work is assigned to anyone |
| The reader holds no link | No Project resource is linked to your account; a planner links you |
| A link's resource is absent from the accepted snapshot | The schedule does not carry the resource you are linked to; tell a planner |
| Linked, resource present, nothing booked | None of the accepted schedule's work is assigned to you |

Collapsing these into "no work" tells somebody standing on site with a radio that they are done
when they are not. Keeping them apart is the same rule as
[ux-anti-slop-rules.md](ux-anti-slop-rules.md): a screen must not imply a state that is not real.

The console shows the same distinction from the other side. A link whose resource the newest
accepted schedule has lost is marked, because otherwise it silently empties somebody's work list
and nothing says why.

## Model

One active link per `(project, resource)`. A resource is one person, so it cannot be linked to two
users at once; a person may hold several resources, because a named resource and a trade resource
can both be theirs.

Revoking sets a state on the row rather than deleting it, so the record keeps who linked whom and
who undid it. A revoked link frees its resource to be linked to somebody else.

## Not decided here

- **Automatic match proposals.** Presenting evidence for a probable match, for a planner to
  confirm, is permitted by the mapping rules and is not built.
- **Crew and contractor resources standing for several people.** A resource linked to one user
  today. Whether a crew resource should resolve to a group is a product question that a group
  model would have to come first for.
- **Narrowing responsibility.** The permission matrix's `assigned only` level describes a future
  responsibility scope. This link is the data such a scope would need, but it is not that scope,
  and turning it into one is a separate decision with its own review.
