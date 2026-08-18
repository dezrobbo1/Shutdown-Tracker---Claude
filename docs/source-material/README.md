# Source Material Library

This directory is a **catalogue and provenance layer**, not a raw-source store.

Current product and architecture authority remains under `docs/product`, `docs/architecture`, `docs/adr`, and `docs/research`.

## Raw source policy

Real Microsoft Project schedules, customer/site files, uploaded research bundles, screenshots, and other raw source archives must not be committed to this public application repository.

Raw source material should live in an approved external location such as the project File Library or another controlled store. The repository may retain:

- source names;
- provenance notes;
- disposition/classification;
- sanitized summaries;
- hashes or manifest metadata when useful and non-sensitive.

It must not retain the raw real schedule files merely for convenience.

## Structure

- `inbox/` — instructions for classifying newly received material; no raw operational files should be committed here.
- `research/` — catalogue of supporting research sources.
- `reference/` — catalogue of historical/reference sources, including externally held Project schedule evidence.
- `archive/` — archive policy/manifest notes only; raw archives are external.
- `source-disposition.md` — disposition register for known sources.

## Cleanup note

A previously committed ZIP bundle containing source documents and three real Microsoft Project XML schedules has been removed from the current repository tree. The files remain described by the catalogues for provenance, but the raw bundle is not an application-repository artifact.

Deleting the current-tree file does not purge earlier Git history. History-retention/purge is a separate repository-administration decision and should be handled deliberately if the source material is considered sensitive.
