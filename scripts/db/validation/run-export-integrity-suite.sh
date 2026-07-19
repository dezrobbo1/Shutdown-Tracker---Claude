#!/usr/bin/env sh
set -eu

DB_USER=${POSTGRES_USER:-shutdown_tracker}
RUN_ID=$$
UPGRADE_DB="shutdown_tracker_upgrade_$RUN_ID"
CURRENT_DB="shutdown_tracker_current_$RUN_ID"
ATOMIC_V007_DB="shutdown_tracker_atomic_v007_$RUN_ID"
ATOMIC_V008_DB="shutdown_tracker_atomic_v008_$RUN_ID"
LOG_DIR="/tmp/shutdown-tracker-export-integrity-$RUN_ID"

mkdir -p "$LOG_DIR"

admin_psql() {
  psql -X -v ON_ERROR_STOP=1 -U "$DB_USER" -d postgres "$@"
}

db_psql() {
  database=$1
  shift
  psql -X -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$database" "$@"
}

drop_database() {
  database=$1
  dropdb --if-exists --force -U "$DB_USER" "$database" >/dev/null 2>&1 || true
}

cleanup() {
  admin_psql -qAtc "
    SELECT pg_terminate_backend(pid)
    FROM pg_stat_activity
    WHERE application_name LIKE 'st_v_${RUN_ID}_%'
      AND pid <> pg_backend_pid();
  " >/dev/null 2>&1 || true

  drop_database "$UPGRADE_DB"
  drop_database "$CURRENT_DB"
  drop_database "$ATOMIC_V007_DB"
  drop_database "$ATOMIC_V008_DB"
  rm -f "$LOG_DIR"/* 2>/dev/null || true
  rmdir "$LOG_DIR" 2>/dev/null || true
}

trap cleanup EXIT HUP INT TERM

create_database() {
  database=$1
  drop_database "$database"
  createdb -U "$DB_USER" "$database"
}

apply_migration() {
  database=$1
  migration=$2
  echo "  Applying $(basename "$migration") to $database"
  db_psql "$database" --single-transaction -f "$migration" >/dev/null
}

apply_v001_through_v006() {
  database=$1
  for migration in /migrations/V00[1-6]__*.sql; do
    apply_migration "$database" "$migration"
  done
}

apply_v007() {
  database=$1
  set -- /migrations/V007__*.sql
  if [ ! -f "$1" ]; then
    echo "V007 migration file is missing." >&2
    exit 1
  fi
  apply_migration "$database" "$1"
}

apply_v008() {
  database=$1
  set -- /migrations/V008__*.sql
  if [ ! -f "$1" ]; then
    echo "V008 migration file is missing." >&2
    exit 1
  fi
  apply_migration "$database" "$1"
}

apply_all_migrations() {
  database=$1
  for migration in /migrations/V*.sql; do
    apply_migration "$database" "$migration"
  done
}

wait_until_true() {
  database=$1
  sql=$2
  label=$3
  attempt=0
  while [ "$attempt" -lt 30 ]; do
    result=$(db_psql "$database" -qAtc "$sql" | tr -d '\r')
    if [ "$result" = "t" ]; then
      return 0
    fi
    attempt=$((attempt + 1))
    sleep 1
  done
  echo "Timed out waiting for $label." >&2
  db_psql "$database" -c "
    SELECT pid, application_name, state, wait_event_type, wait_event, query
    FROM pg_stat_activity
    WHERE datname = current_database()
    ORDER BY pid;
  " >&2 || true
  return 1
}

start_gate() {
  database=$1
  gate_id=$2
  gate_app=$3
  gate_log=$4

  (
    export PGAPPNAME="$gate_app"
    db_psql "$database" -c "
      SELECT pg_advisory_lock(807, $gate_id);
      SELECT pg_sleep(45);
    "
  ) >"$gate_log" 2>&1 &
  GATE_OS_PID=$!

  wait_until_true "$database" "
    SELECT EXISTS (
      SELECT 1
      FROM pg_locks l
      JOIN pg_stat_activity a ON a.pid = l.pid
      WHERE a.application_name = '$gate_app'
        AND l.locktype = 'advisory'
        AND l.granted
    );
  " "$gate_app to own its advisory gate"
}

release_gate() {
  database=$1
  gate_app=$2
  admin_result=$(db_psql "$database" -qAtc "
    SELECT count(*)
    FROM (
      SELECT pg_terminate_backend(pid)
      FROM pg_stat_activity
      WHERE application_name = '$gate_app'
    ) terminated;
  " | tr -d '\r')
  if [ "$admin_result" != "1" ]; then
    echo "Expected one synthetic gate backend for $gate_app; found $admin_result." >&2
    return 1
  fi
  if wait "$GATE_OS_PID"; then
    :
  else
    :
  fi
}

wait_for_holder_gate() {
  database=$1
  holder_app=$2
  wait_until_true "$database" "
    SELECT EXISTS (
      SELECT 1
      FROM pg_stat_activity
      WHERE application_name = '$holder_app'
        AND wait_event_type = 'Lock'
        AND wait_event = 'advisory'
    );
  " "$holder_app to reach its advisory synchronization point"
}

wait_for_blocker() {
  database=$1
  waiter_app=$2
  holder_app=$3
  wait_until_true "$database" "
    SELECT EXISTS (
      SELECT 1
      FROM pg_stat_activity waiter
      JOIN pg_stat_activity holder
        ON holder.application_name = '$holder_app'
      WHERE waiter.application_name = '$waiter_app'
        AND holder.pid = ANY(pg_blocking_pids(waiter.pid))
    );
  " "$waiter_app to be blocked by $holder_app"
}

wait_success() {
  process_id=$1
  log_file=$2
  label=$3
  if ! wait "$process_id"; then
    echo "$label failed:" >&2
    cat "$log_file" >&2
    return 1
  fi
}

wait_failure() {
  process_id=$1
  log_file=$2
  expected_text=$3
  label=$4
  if wait "$process_id"; then
    echo "$label unexpectedly succeeded." >&2
    cat "$log_file" >&2
    return 1
  fi
  if ! grep -F "$expected_text" "$log_file" >/dev/null; then
    echo "$label failed for an unexpected reason:" >&2
    cat "$log_file" >&2
    return 1
  fi
}

assert_scalar() {
  database=$1
  sql=$2
  expected=$3
  label=$4
  actual=$(db_psql "$database" -qAtc "$sql" | tr -d '\r')
  if [ "$actual" != "$expected" ]; then
    echo "$label: expected '$expected', found '$actual'." >&2
    return 1
  fi
}

run_line_seal_concurrency() {
  echo "  Checking line insertion versus sealing..."
  gate_app="st_v_${RUN_ID}_line_gate"
  holder_app="st_v_${RUN_ID}_line_holder"
  waiter_app="st_v_${RUN_ID}_seal_waiter"
  gate_log="$LOG_DIR/line-gate.log"
  holder_log="$LOG_DIR/line-holder.log"
  waiter_log="$LOG_DIR/seal-waiter.log"

  start_gate "$CURRENT_DB" 1 "$gate_app" "$gate_log"
  (
    export PGAPPNAME="$holder_app"
    db_psql "$CURRENT_DB" -v gate_id=1 -f /validation/concurrency/line-insert-holder.sql
  ) >"$holder_log" 2>&1 &
  holder_pid=$!
  wait_for_holder_gate "$CURRENT_DB" "$holder_app"

  (
    export PGAPPNAME="$waiter_app"
    db_psql "$CURRENT_DB" -c "
      UPDATE export_batches
      SET line_set_sealed = true
      WHERE id = '20000000-0000-0000-0000-000000000510';
    "
  ) >"$waiter_log" 2>&1 &
  waiter_pid=$!
  wait_for_blocker "$CURRENT_DB" "$waiter_app" "$holder_app"
  release_gate "$CURRENT_DB" "$gate_app"
  wait_success "$holder_pid" "$holder_log" "Line-insert holder"
  wait_success "$waiter_pid" "$waiter_log" "Seal waiter"

  assert_scalar "$CURRENT_DB" "SELECT line_set_sealed::TEXT FROM export_batches WHERE id = '20000000-0000-0000-0000-000000000510';" "true" "Concurrent seal state"
  assert_scalar "$CURRENT_DB" "SELECT count(*) FROM export_batch_lines WHERE export_batch_id = '20000000-0000-0000-0000-000000000510';" "1" "Concurrent line count"
  echo "  Line insertion versus sealing passed."
}

run_duplicate_concurrency() {
  echo "  Checking concurrent duplicate rejection..."
  gate_app="st_v_${RUN_ID}_duplicate_gate"
  holder_app="st_v_${RUN_ID}_duplicate_holder"
  waiter_app="st_v_${RUN_ID}_duplicate_waiter"
  gate_log="$LOG_DIR/duplicate-gate.log"
  holder_log="$LOG_DIR/duplicate-holder.log"
  waiter_log="$LOG_DIR/duplicate-waiter.log"

  start_gate "$CURRENT_DB" 2 "$gate_app" "$gate_log"
  (
    export PGAPPNAME="$holder_app"
    db_psql "$CURRENT_DB" -v gate_id=2 -f /validation/concurrency/duplicate-line-holder.sql
  ) >"$holder_log" 2>&1 &
  holder_pid=$!
  wait_for_holder_gate "$CURRENT_DB" "$holder_app"

  (
    export PGAPPNAME="$waiter_app"
    db_psql "$CURRENT_DB" -f /validation/concurrency/duplicate-line-waiter.sql
  ) >"$waiter_log" 2>&1 &
  waiter_pid=$!
  wait_for_blocker "$CURRENT_DB" "$waiter_app" "$holder_app"
  release_gate "$CURRENT_DB" "$gate_app"
  wait_success "$holder_pid" "$holder_log" "Duplicate-line holder"
  wait_failure "$waiter_pid" "$waiter_log" "export_batch_lines_policy2_task_field_unique" "Duplicate-line waiter"

  assert_scalar "$CURRENT_DB" "
    SELECT count(*)
    FROM export_batch_lines
    WHERE export_batch_id = '20000000-0000-0000-0000-000000000511'
      AND imported_task_id = '20000000-0000-0000-0000-000000000101'
      AND field_name = 'percent_complete';
  " "1" "Concurrent duplicate line count"
  echo "  Concurrent duplicate rejection passed."
}

run_generation_approval_concurrency() {
  echo "  Checking generation versus approval insertion..."
  gate_app="st_v_${RUN_ID}_generation_gate"
  holder_app="st_v_${RUN_ID}_generation_holder"
  waiter_app="st_v_${RUN_ID}_generation_approval"
  gate_log="$LOG_DIR/generation-gate.log"
  holder_log="$LOG_DIR/generation-holder.log"
  waiter_log="$LOG_DIR/generation-approval.log"

  start_gate "$CURRENT_DB" 3 "$gate_app" "$gate_log"
  (
    export PGAPPNAME="$holder_app"
    db_psql "$CURRENT_DB" -v gate_id=3 -f /validation/concurrency/generation-holder.sql
  ) >"$holder_log" 2>&1 &
  holder_pid=$!
  wait_for_holder_gate "$CURRENT_DB" "$holder_app"

  (
    export PGAPPNAME="$waiter_app"
    db_psql "$CURRENT_DB" \
      -v approval_id=20000000-0000-0000-0000-000000000712 \
      -v source_id=20000000-0000-0000-0000-000000000412 \
      -v approval_state=rejected \
      -v reason="Concurrent rejection after generation" \
      -v created_at=2026-07-18T09:01:00Z \
      -f /validation/concurrency/approval-waiter.sql
  ) >"$waiter_log" 2>&1 &
  waiter_pid=$!
  wait_for_blocker "$CURRENT_DB" "$waiter_app" "$holder_app"
  release_gate "$CURRENT_DB" "$gate_app"
  wait_success "$holder_pid" "$holder_log" "Generation holder"
  wait_success "$waiter_pid" "$waiter_log" "Post-generation approval waiter"

  assert_scalar "$CURRENT_DB" "SELECT status::TEXT FROM export_batches WHERE id = '20000000-0000-0000-0000-000000000512';" "generated" "Concurrent generation state"
  assert_scalar "$CURRENT_DB" "SELECT count(*) FROM approval_records WHERE id = '20000000-0000-0000-0000-000000000712' AND approval_state = 'rejected';" "1" "Post-generation approval count"
  echo "  Generation versus approval insertion passed."
}

run_generation_rollback_concurrency() {
  echo "  Checking generation rollback versus approval insertion..."
  gate_app="st_v_${RUN_ID}_rollback_gate"
  holder_app="st_v_${RUN_ID}_rollback_holder"
  waiter_app="st_v_${RUN_ID}_rollback_approval"
  gate_log="$LOG_DIR/rollback-gate.log"
  holder_log="$LOG_DIR/rollback-holder.log"
  waiter_log="$LOG_DIR/rollback-approval.log"

  start_gate "$CURRENT_DB" 4 "$gate_app" "$gate_log"
  (
    export PGAPPNAME="$holder_app"
    db_psql "$CURRENT_DB" -v gate_id=4 -f /validation/concurrency/generation-rollback-holder.sql
  ) >"$holder_log" 2>&1 &
  holder_pid=$!
  wait_for_holder_gate "$CURRENT_DB" "$holder_app"

  (
    export PGAPPNAME="$waiter_app"
    db_psql "$CURRENT_DB" \
      -v approval_id=20000000-0000-0000-0000-000000000713 \
      -v source_id=20000000-0000-0000-0000-000000000413 \
      -v approval_state=superseded \
      -v reason="Concurrent supersession after rollback" \
      -v created_at=2026-07-18T09:02:00Z \
      -f /validation/concurrency/approval-waiter.sql
  ) >"$waiter_log" 2>&1 &
  waiter_pid=$!
  wait_for_blocker "$CURRENT_DB" "$waiter_app" "$holder_app"
  release_gate "$CURRENT_DB" "$gate_app"
  wait_failure "$holder_pid" "$holder_log" "division by zero" "Generation rollback holder"
  wait_success "$waiter_pid" "$waiter_log" "Post-rollback approval waiter"

  assert_scalar "$CURRENT_DB" "SELECT status::TEXT FROM export_batches WHERE id = '20000000-0000-0000-0000-000000000513';" "approved" "Rolled-back generation state"
  assert_scalar "$CURRENT_DB" "SELECT count(*) FROM approval_records WHERE id = '20000000-0000-0000-0000-000000000713' AND approval_state = 'superseded';" "1" "Post-rollback approval count"
  echo "  Generation rollback versus approval insertion passed."
}

echo "Preparing populated V006-to-current upgrade validation..."
create_database "$UPGRADE_DB"
apply_v001_through_v006 "$UPGRADE_DB"
db_psql "$UPGRADE_DB" --single-transaction -f /validation/fixtures/v006-export-integrity-seed.sql >/dev/null
apply_v007 "$UPGRADE_DB"
db_psql "$UPGRADE_DB" --single-transaction -f /validation/fixtures/v007-policy1-export-integrity-seed.sql >/dev/null
apply_v008 "$UPGRADE_DB"
db_psql "$UPGRADE_DB" --single-transaction -f /validation/assertions/export-integrity-upgrade.sql

echo "Preparing current-policy database validation..."
create_database "$CURRENT_DB"
apply_all_migrations "$CURRENT_DB"
db_psql "$CURRENT_DB" --single-transaction -f /validation/assertions/export-integrity-current-policy.sql

echo "Running deterministic PostgreSQL concurrency validation..."
# The scenario functions below are added after the current-policy fixture so all
# holder and waiter sessions use committed policy-2 candidate rows.
run_line_seal_concurrency
run_duplicate_concurrency
run_generation_approval_concurrency
run_generation_rollback_concurrency

echo "Validating late V007 rollback..."
create_database "$ATOMIC_V007_DB"
apply_v001_through_v006 "$ATOMIC_V007_DB"
set -- /migrations/V007__*.sql
if db_psql "$ATOMIC_V007_DB" --single-transaction -f "$1" -c "SELECT intentionally_missing_v007_tail();" >"$LOG_DIR/atomic-v007.log" 2>&1; then
  echo "The intentionally failing V007 transaction unexpectedly succeeded." >&2
  exit 1
fi
db_psql "$ATOMIC_V007_DB" --single-transaction -f /validation/assertions/export-integrity-atomicity-v007.sql

echo "Validating late V008 rollback..."
create_database "$ATOMIC_V008_DB"
apply_v001_through_v006 "$ATOMIC_V008_DB"
apply_v007 "$ATOMIC_V008_DB"
set -- /migrations/V008__*.sql
if db_psql "$ATOMIC_V008_DB" --single-transaction -f "$1" -c "SELECT intentionally_missing_v008_tail();" >"$LOG_DIR/atomic-v008.log" 2>&1; then
  echo "The intentionally failing V008 transaction unexpectedly succeeded." >&2
  exit 1
fi
db_psql "$ATOMIC_V008_DB" --single-transaction -f /validation/assertions/export-integrity-atomicity-v008.sql

echo "PostgreSQL export-integrity validation passed."
