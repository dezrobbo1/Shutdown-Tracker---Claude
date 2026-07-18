#!/usr/bin/env sh
set -eu

DB_NAME="shutdown_tracker"
DB_USER="shutdown_tracker"

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
COMPOSE_FILE="$REPO_ROOT/infra/docker/docker-compose.postgres.yml"
MIGRATIONS_DIR="$REPO_ROOT/infra/migrations"

case "$(uname -s 2>/dev/null || true)" in
  MINGW*|MSYS*|CYGWIN*)
    if [ -n "${MSYS2_ARG_CONV_EXCL:-}" ]; then
      MSYS2_ARG_CONV_EXCL="${MSYS2_ARG_CONV_EXCL};/migrations/"
    else
      MSYS2_ARG_CONV_EXCL="/migrations/"
    fi
    export MSYS2_ARG_CONV_EXCL
    ;;
esac

EXPECTED_TABLES="
projects
source_files
import_batches
project_snapshots
imported_tasks
imported_resources
imported_assignments
imported_extended_attributes
task_lineage_links
audit_events
approval_records
export_batches
export_batch_lines
critical_watchlists
critical_work_packages
critical_work_package_sources
reporting_policy_versions
reporting_periods
critical_updates
critical_update_lines
"

compose() {
  docker compose -f "$COMPOSE_FILE" "$@"
}

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required for migration validation." >&2
  exit 1
fi

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

echo "Applying migrations..."
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
  echo "Verified table: $table"
done

echo "Migration validation passed."
