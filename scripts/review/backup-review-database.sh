#!/usr/bin/env sh
# Take a compressed dump of the review database before clearing it.
#
# Deliberately a script rather than a step inside the reset endpoint. Shelling out to pg_dump from
# the API would need the binary on PATH and credentials in the process, and would turn a sub-second
# operation into a multi-second one that can fail for reasons unrelated to the reset -- at which
# point somebody has to decide whether to press on anyway. Here the decision is already made.
#
# Usage: sh scripts/review/backup-review-database.sh [output-directory]
set -eu

PGHOST="${PGHOST:-127.0.0.1}"
PGPORT="${PGPORT:-5433}"
PGUSER="${PGUSER:-postgres}"
PGDATABASE="${PGDATABASE:-shutdown_tracker}"
OUT_DIR="${1:-$HOME/shutdown-tracker-deploy/backups}"

if ! command -v pg_dump >/dev/null 2>&1; then
	echo "pg_dump is not on PATH. Install postgresql-client, or dump from another machine." >&2
	exit 1
fi

mkdir -p "$OUT_DIR"
STAMP=$(date -u +%Y%m%d-%H%M%S)
TARGET="$OUT_DIR/${PGDATABASE}-${STAMP}.dump"

echo "==> dumping $PGDATABASE from $PGHOST:$PGPORT"
# -Fc is the custom format the one existing manual dump uses, so pg_restore works the same way.
pg_dump -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -Fc -f "$TARGET"

echo "==> wrote $TARGET ($(du -h "$TARGET" | cut -f1))"
echo "    restore with: pg_restore -h $PGHOST -p $PGPORT -U $PGUSER -d $PGDATABASE --clean $TARGET"
