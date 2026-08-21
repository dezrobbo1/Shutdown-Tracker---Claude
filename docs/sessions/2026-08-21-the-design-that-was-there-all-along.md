# 2026-08-21 — The design that was there all along

## Scope

Phase 3 slice 8: restore the Design C prototypes and correct the claim that they were unrecoverable.
Prompted by a plain observation — the interface does not look like the design — which turned out to
be both true and partly obscured by this repository's own documentation.

## What was found

**The prototypes were never lost.** `docs/design/prototypes/design-c/README.md` stated they were
"not present in this repository and do not appear in any branch or in its history". They were added
in `792be38` and are reachable through the `legacy` remote.

The reason the claim survived is worth keeping: **the README named the files differently from the
files**. It called them `shutdown-tracker-console-v4.html`; on disk they are
`shutdown_tracker_design_c_v4_console.html` — different separators, different word order. A search
for the documented name finds nothing, twice, and the second search confirms the first.

**The README also claimed the visual direction "has been acted on".** Measured against the restored
files, it has not:

| | Prototype | Built |
| --- | --- | --- |
| `--radius` | `2px` | `8px` |
| small radius | `2px` | `6px` |
| status chip | rectangular stamp | `--radius-chip: 999px`, a pill |
| `--shadow` | `none` | box shadows present |
| accent | `#c97a2b` | `#1d473d` / `#2d876d` |
| canvas | `#f3f4f1` | `#ffffff` |
| operational state colours | six | four |

Both statements were load-bearing. Together they said: the reference is gone, and you have already
done the thing it described. Neither was true, and the combination is what let the gap sit.

**What is not a gap:** the information architecture. Design C's own nav reads Control / Work /
Resolve / Evidence / Project bridge, and the built console reads Today / Tasks / Problems / Evidence
/ Exports. That difference is deliberate and documented in three places — Design C is adopted as
visual guidance only, and its zone names are explicitly not the product's IA.

## Decisions

**Restored with two identifiers neutralised.** A supplier name in a problem row became `Crane crew`,
and the project code became `SYN-001`. This repository must not carry vendor, contractor or project
identifiers, and there is no way to confirm from here that these were invented. Neither substitution
touches layout, density, tokens or component treatment, which is what the files are for. Recorded in
the README rather than left for a reader to notice a difference from the original.

**Rejected: restoring verbatim and flagging the identifiers instead.** The repository is served
publicly with no access control. A note saying "this file contains a name we could not verify" does
not stop it being served.

**Rejected: fixing the styling in the same change.** Slice 8 is the reference and the correction.
The palette, focus rules, status classes and the console's unstyled controls are slice 9, and
putting them here would mean the correction could not be reviewed on its own.

## Verified

Documentation and design references only; no application code changed. `mvn test` **523 tests, 0
failures, 0 skipped**; `npm test` 134 across three workspaces; `npm run build` built both
applications; `git diff --check` clean.

The recovered files were diffed against the versions in `792be38` to confirm the only differences
are the two substitutions above.

## Left open

Slice 9, and it is larger than its description. Beyond the palette, the six status classes and
visible focus that the goal names, the review that prompted this found:

- **The console's primary action buttons have no styling at all.** The field app has thirteen button
  rules; the console has five, all for the refresh icon. Every action in the journey renders as a
  browser default control. The console also reuses the class name `.mobile-action-row` from the
  field app without the styling that name implies.
- **Neither stylesheet has a single `:focus` or `:focus-visible` rule**, against the accessibility
  principle in `docs/product/design-language-and-status-semantics.md`.
- **Four colour-named tones stand in for six semantic classes.** `toneForState` returns
  `green`/`amber`/`red`/`blue`; the design language defines Neutral, Info, Warning, Critical,
  Success and Restricted. Restricted has no representation, and it is the class the export boundary
  needs. The tones are also chosen by substring-matching display labels.
- **The documented compact top status strip is not built.** The rule asks for project, shift,
  import, export and sync context; what is there is a page title and a pill reading the API base
  URL.
