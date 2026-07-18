# Project Export Contract

Shared Java request and response records for API-to-worker MSPDI/XML export artifact handoff.

This package contains contract types only. It does not generate files, call MPXJ, persist metadata, calculate schedules, or write back to Microsoft Project.

The request contract allows only the MVP leaf-task export fields `percent_complete`, `actual_start`, and `actual_finish`, rejects summary-task candidates, and rejects duplicate imported-task/field candidates. The worker additionally validates field values and requires `percent_complete` to be a whole number from 0 through 100.
