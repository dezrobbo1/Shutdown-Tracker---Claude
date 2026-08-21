#!/usr/bin/env sh
set -eu

DB_NAME="shutdown_tracker"
DB_USER="shutdown_tracker"

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
COMPOSE_FILE="$REPO_ROOT/infra/docker/docker-compose.postgres.yml"
MIGRATIONS_DIR="$REPO_ROOT/infra/migrations"

# This script applies whatever migrations exist, in order. It only pins V007, because the
# export-integrity suite it calls builds a V006-to-V007 upgrade scenario by that filename.
# It deliberately does not pin the total count: doing so meant every migration added after
# V007 failed this script on its first line instead of being validated by it.
set -- "$MIGRATIONS_DIR"/V*.sql
if [ "$#" -lt 7 ]; then
  echo "Expected at least V001-V007 in $MIGRATIONS_DIR; found $#." >&2
  exit 1
fi
if [ "$(basename "$7")" != "V007__enforce_export_candidate_integrity.sql" ]; then
  echo "Expected V007__enforce_export_candidate_integrity.sql as the seventh migration." >&2
  exit 1
fi

case "$(uname -s 2>/dev/null || true)" in
  MINGW*|MSYS*|CYGWIN*)
    if [ -n "${MSYS2_ARG_CONV_EXCL:-}" ]; then
      MSYS2_ARG_CONV_EXCL="${MSYS2_ARG_CONV_EXCL};/migrations/;/validation/"
    else
      MSYS2_ARG_CONV_EXCL="/migrations/;/validation/"
    fi
    export MSYS2_ARG_CONV_EXCL
    ;;
esac

# The tables the migrations create, derived from the migrations rather than transcribed.
#
# This was a hand-written list of names, and it named 33 tables when the migrations created 35 --
# missing project_resource_links and candidate_schedule_runs. Because the check below asks
# "does each name I know about exist", a list that has not heard of a table cannot notice its
# absence: it passed against a database missing the two most recent migrations. Keeping such a
# list current is the same discipline that failed in the first place, so it is computed instead.
EXPECTED_TABLES=$(
  grep -hoiE 'CREATE TABLE (IF NOT EXISTS )?[a-z_.]+' "$MIGRATIONS_DIR"/V*.sql \
    | sed -E 's/.*[[:space:]]//; s/^public\.//' | sort -u
)

compose() {
  docker compose -f "$COMPOSE_FILE" "$@"
}

cleanup() {
  compose down -v >/dev/null 2>&1 || true
}

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required for migration validation." >&2
  exit 1
fi

trap cleanup EXIT
trap 'exit 130' HUP INT TERM

echo "Resetting local PostgreSQL validation database..."
compose down -v
compose up -d

echo "Waiting for PostgreSQL to become ready..."
attempt=0
stable=0
until [ "$stable" -ge 3 ]; do
  attempt=$((attempt + 1))
  if compose exec -T postgres psql -U "$DB_USER" -d "$DB_NAME" -tAc "SELECT 1;" >/dev/null 2>&1; then
    stable=$((stable + 1))
  else
    stable=0
  fi
  if [ "$attempt" -ge 90 ]; then
    echo "PostgreSQL did not become ready in time." >&2
    compose logs postgres >&2 || true
    exit 1
  fi
  if [ "$stable" -lt 3 ]; then
    sleep 1
  fi
done

echo "Applying every migration in order..."
for migration in "$MIGRATIONS_DIR"/V*.sql; do
  migration_name=$(basename "$migration")
  echo "Applying $migration_name"
  compose exec -T postgres psql --single-transaction -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" -f "/migrations/$migration_name"
done

echo "Verifying expected tables..."
for table in $EXPECTED_TABLES; do
  exists=$(compose exec -T postgres psql -U "$DB_USER" -d "$DB_NAME" -tAc "SELECT to_regclass('public.$table') IS NOT NULL;" | tr -d '\r')
  if [ "$exists" != "t" ]; then
    echo "Expected table missing: $table" >&2
    exit 1
  fi
done
echo "Verified $(echo "$EXPECTED_TABLES" | wc -l | tr -d ' ') tables named by the migrations."

# The other direction, which a per-name check cannot see: a table the database has and no
# migration creates means the two have diverged, whichever way round.
ACTUAL_TABLES=$(compose exec -T postgres psql -U "$DB_USER" -d "$DB_NAME" -tAc \
  "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY 1;" | tr -d '\r')
UNEXPECTED=$(printf '%s\n' "$ACTUAL_TABLES" | grep -vxF "$EXPECTED_TABLES" || true)
if [ -n "$UNEXPECTED" ]; then
  echo "Tables present that no migration creates:" >&2
  printf '  %s\n' $UNEXPECTED >&2
  exit 1
fi

echo "Running populated-upgrade and PostgreSQL export-integrity validation..."
compose exec -T postgres sh -c 'tr -d "\015" < "$1" | sh' _ /validation/validation/run-export-integrity-suite.sh

echo "Migration validation passed."
