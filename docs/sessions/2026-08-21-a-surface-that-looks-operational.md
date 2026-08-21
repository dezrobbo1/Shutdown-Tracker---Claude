# 2026-08-21 — A surface that looks operational

## Scope

Phase 3 slice 9. The palette, shape, focus rules and status classes, applied from the prototypes
restored in [the previous entry](2026-08-21-the-design-that-was-there-all-along.md).

Navigation is untouched. Design C's zone names are not the product's information architecture and
nothing here implies them.

## What was found

Beyond the palette gap the goal already named, the review that prompted this found three things
that were not in its description:

**The console had no button styling at all.** The field app declares thirteen button selectors; the
console declared five, and all five were for the refresh icon. Every action in the export chain —
create the preview, parse the file, download the candidate — rendered as a browser default control.
The console also reused the class name `.mobile-action-row` copied from the field app *without the
treatment that name implies*, which is why it looked like an oversight rather than a decision.

**Neither stylesheet had a single `:focus` or `:focus-visible` rule.** A keyboard user could tab
through the entire export chain without ever seeing where they were. The accessibility principle in
`docs/product/design-language-and-status-semantics.md` requires focus rules; this was not a
preference that had been weighed and declined.

**Four colour names stood in for six semantic classes.** `toneForState` returned
`green`/`amber`/`red`/`blue`. The design language defines Neutral, Info, Warning, Critical, Success
and Restricted. **Restricted had no representation at all**, and it is precisely the class the
export boundary needs: a summary task that can never be export eligible is not a failure, and a red
stamp on it says it is.

## Decisions

**A shared token layer, not two stylesheets that agree.** `@shutdown-tracker/design-tokens` holds
the palette, the shape and the six status classes; both applications map their own names onto it.
The design language requires that "the same state must look and read the same across console and
mobile", and two files kept in step by hand cannot promise that. Each app keeps its own token names
and maps them, so the change is a mapping to review rather than a rename across two stylesheets.

**Classes named for meaning, not colour.** The prototypes' stamp variants are `red`, `amber`,
`green`, `blue`, `review`. The product's are the six from its own design language. A class called
`red` cannot be re-themed, and it tells a reader nothing about why it is red.

**A parity test between the CSS and the TypeScript.** `statusClasses` in the package and the
`.status-chip.*` rules in `tokens.css` are two declarations of one truth, and two declarations drift
the moment somebody adds to one. Asserted against each other, on the same argument the capability
map is asserted against the server's enum.

**`:focus-visible` rather than `:focus`,** so a pointer click does not draw a ring on every button
that is pressed.

**The first action in a panel carries the shell colour.** One obvious next step per panel, and
everything else quiet — rather than a row of equally weighted buttons, which is the same failure as
badge soup in a different place.

**Disabled controls now look disabled.** The anti-slop rules forbid "disabled buttons that look
live". A control the server would refuse should say so before it is pressed, which is the argument
`CapabilityGate` already makes in the markup and the stylesheet was not making.

**Rejected: building the compact top status strip.** The design language asks for project, shift,
import, export and sync context along the top, and what is there is a page title and a strip reading
the API base URL. Building it needs the project's name, which the console does not currently fetch —
it holds an id. That is a data change wearing a styling change's clothes, and it is left open rather
than smuggled in here.

**Rejected: restyling every panel and table to match the prototypes.** The tokens now carry the
direction, so panels, tables and cards inherit most of it. Rewriting each treatment by hand in the
same change would have made the token layer impossible to review on its own.

## Verified

| Check | Result |
| --- | --- |
| `mvn test` | **523 tests, 0 failures, 0 errors, 0 skipped** |
| `npm test` | **144 tests** across four workspaces, up from 134 |
| `npm run build` | both applications built |
| `git diff --check` | clean |

Checked in the built output rather than only in source: both bundles carry `--dc-radius:2px`, both
carry a `:focus-visible` rule, and **neither contains `999px`** — the pill radius is gone from the
shipped CSS, not just from the stylesheet.

The six classes were rendered and read back: `Not started` → neutral, `In progress` → info,
`Needs planner review` → warning, `Rejected` → critical, `Verified` → success, `NOT_ELIGIBLE` →
restricted.

`scripts/db/validate-migrations.sh` was not run: no migration changed, and this host has no Docker.

## Left open

- **The compact top status strip**, per the decision above. It needs the project name on the client.
- **Panel, table and card treatments** are inherited from the tokens rather than matched to the
  prototypes deliberately. The prototypes use a mono uppercase panel header and a sticky table head
  that the built surfaces do not.
- **`toneForState` and `mobileChipTone` still match on display text.** They now return the right
  classes, but they get there by substring-matching a label. A state should carry its class rather
  than have it guessed from how it was spelled.
