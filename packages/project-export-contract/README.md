# Project Export Contract

Shared Java request and response records for API-to-worker MSPDI/XML export artifact handoff.

This package contains contract types only. It does not generate files, call MPXJ, persist metadata, calculate schedules, or write back to Microsoft Project.

The request contract allows only the MVP leaf-task export fields `percent_complete`, `actual_start`, and `actual_finish`. It rejects physical percent, unknown fields, summary-task candidates, duplicate imported-task/field candidates, inconsistent repeated imported-task identity, and duplicate Microsoft Project UID or ID mappings.

`ProjectExportValueNormalizer` is the canonical proposed-value boundary shared by the API and worker. Whole-number percent equivalents such as `75`, `75.0`, and `075` canonicalize to `75`. Proposed actual dates require ISO-8601 minute- or second-precision values with an explicit offset and canonicalize to whole seconds while preserving the reviewed Microsoft Project local wall-clock component; omitted seconds become `:00`, all-zero fractions canonicalize away, and non-zero fractions, offset-free values, and invalid values are rejected. The worker uses the normalized local date-time component without converting it to UTC and validates the normalized request again before applying values and before writing MSPDI/XML. Imported baseline timestamps do not cross this contract boundary; the API preserves their available microsecond precision separately for freshness comparison.
