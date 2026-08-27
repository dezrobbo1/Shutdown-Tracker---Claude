# 2026-08-27 — The half that was still two people

## Scope

Finish what [One person, and one rule](2026-08-26-one-person-and-one-rule.md) scoped out. That
session made the console one person and left the field app alone, which was defensible while the
field app was somebody else. It stopped being defensible the moment `redeploy.sh` started building
both entrypoints as the same super user.

## What was found

**The field app still had a picker, and the picker now offered a corpse.** `buildFieldSession` read
a remembered identity and let it replace the build-time actor wholesale — the same defect the
console had just lost, in a file nobody had reopened. Combined with the seeder retiring the field
user, supervisor, planner and viewer, a handset that had ever chosen one of them was now sending a
deactivated user id on every request and getting 403 on all of it. Nothing in the interface would
say why: the control that caused it had been used weeks earlier.

That is the shape worth remembering. Removing the read path in one client and not the other did not
leave the second client merely stale; the other half of the change — retiring the accounts — turned
the leftover into a guaranteed failure. Neither edit was wrong alone.

**Three documents still described the deployment as it was.** `authorization-model.md` asserted
Planner-only export approval "including for Admin", which the super user rule had just suspended.
`product-walkthrough.md` told the reader to find a picker in the sidebar and walk as four people.
The README said both applications pick a person. A walkthrough that cannot be followed is worse than
one that does not exist, because the reader assumes the product is broken rather than the page.

## What changed

The field app lost its picker and its stored-identity read path, matching the console.
`canSwitchIdentity` went with it — it guarded switching while reports were queued, and there is
nothing left to switch. Both clients now clear the old storage key on boot. Nothing reads it, so a
leftover was already inert; it is removed so that reintroducing a read path years from now cannot
resurrect a retired account.

Two pieces of stale text that the capability change had falsified: the export gate telling the
reader "an administrator is not a routine approver" when the administrator had just become one, and
`canReconcileLineage`, computed on every render and consumed by nothing.

The three documents now say what is true, including the part that is least comfortable: **four-eyes
is not enforced anywhere in this codebase.** No code compares a reviewer to the person who
submitted the work. A supervisor already held both `SUBMIT_TASK_PROGRESS` and
`REVIEW_TASK_PROGRESS` before the trial, so one account could always have walked both halves.
Separation of duty here is role-shaped and always has been. The trial did not break four-eyes; it
removed the last reason to keep believing it was there.

## Still not done

Clearing the trial to a blank slate, and letting the super user define roles. Both are planned and
neither is written. The walkthrough's step 4 — Exports › Mapping — has still never been completed
on any recorded walk; it was greyed out for an admin until the super user rule landed, and it is the
step most worth watching on the next one.
