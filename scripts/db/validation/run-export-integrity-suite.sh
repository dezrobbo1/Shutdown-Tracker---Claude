#!/usr/bin/env sh
set -eu

DB_USER=${POSTGRES_USER:-shutdown_tracker}
RUN_ID=$$
UPGRADE_DB="shutdown_tracker_upgrade_$RUN_ID"
CURRENT_DB="shutdown_tracker_current_$RUN_ID"
ATOMIC_DB="shutdown_tracker_atomic_v007_$RUN_ID"
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
  drop_database "$ATOMIC_DB"
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
  if [ "$#" -ne 1 ] || [ ! -f "$1" ]; then
    echo "Expected exactly one V007 migration file." >&2
    exit 1
  fi
  apply_migration "$database" "$1"
}

apply_all_migrations() {
  database=$1
  count=0
  last_name=
  for migration in /migrations/V*.sql; do
    count=$((count + 1))
    last_name=$(basename "$migration")
    apply_migration "$database" "$migration"
  done
  if [ "$count" -ne 7 ] || [ "$last_name" != "V007__enforce_export_candidate_integrity.sql" ]; then
    echo "Expected exactly V001-V007 for current-policy validation." >&2
    exit 1
  fi
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
      FROM pg_locks locks
      JOIN pg_stat_activity activity ON activity.pid = locks.pid
      WHERE activity.application_name = '$gate_app'
        AND locks.locktype = 'advisory'
        AND locks.granted
    );
  " "$gate_app to own its advisory gate"
}

release_gate() {
  database=$1
  gate_app=$2
  terminated=$(db_psql "$database" -qAtc "
    SELECT count(*)
    FROM (
      SELECT pg_terminate_backend(pid)
      FROM pg_stat_activity
      WHERE application_name = '$gate_app'
    ) terminated;
  " | tr -d '\r')
  if [ "$terminated" != "1" ]; then
    echo "Expected one synchronization backend for $gate_app; found $terminated." >&2
    return 1
  fi
  if wait "$GATE_OS_PID"; then :; else :; fi
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
  " "$holder_app to reach its synchronization point"
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

expect_sql_failure() {
  database=$1
  sql=$2
  expected_text=$3
  label=$4
  log_file="$LOG_DIR/expected-failure.log"
  if db_psql "$database" -c "$sql" >"$log_file" 2>&1; then
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

start_holder_file() {
  database=$1
  gate_id=$2
  holder_app=$3
  holder_file=$4
  holder_log=$5
  (
    export PGAPPNAME="$holder_app"
    db_psql "$database" -v gate_id="$gate_id" -f "$holder_file"
  ) >"$holder_log" 2>&1 &
  HOLDER_PID=$!
  wait_for_holder_gate "$database" "$holder_app"
}

start_approval_waiter() {
  database=$1
  waiter_app=$2
  waiter_log=$3
  approval_id=$4
  candidate_id=$5
  approval_state=$6
  reason=$7
  (
    export PGAPPNAME="$waiter_app"
    db_psql "$database" \
      -v approval_id="$approval_id" \
      -v candidate_id="$candidate_id" \
      -v approval_state="$approval_state" \
      -v reason="$reason" \
      -f /validation/concurrency/approval-waiter.sql
  ) >"$waiter_log" 2>&1 &
  WAITER_PID=$!
}

run_line_seal_concurrency() {
  echo "  Checking line insertion versus preview sealing..."
  gate_app="st_v_${RUN_ID}_line_gate"
  holder_app="st_v_${RUN_ID}_line_holder"
  waiter_app="st_v_${RUN_ID}_seal_waiter"
  gate_log="$LOG_DIR/line-gate.log"
  holder_log="$LOG_DIR/line-holder.log"
  waiter_log="$LOG_DIR/seal-waiter.log"

  start_gate "$CURRENT_DB" 1 "$gate_app" "$gate_log"
  start_holder_file "$CURRENT_DB" 1 "$holder_app" /validation/concurrency/line-insert-holder.sql "$holder_log"
  holder_pid=$HOLDER_PID
  (
    export PGAPPNAME="$waiter_app"
    db_psql "$CURRENT_DB" -c "UPDATE export_batches SET line_set_sealed = true WHERE id = '20000000-0000-0000-0000-000000000540';"
  ) >"$waiter_log" 2>&1 &
  waiter_pid=$!
  wait_for_blocker "$CURRENT_DB" "$waiter_app" "$holder_app"
  release_gate "$CURRENT_DB" "$gate_app"
  wait_success "$holder_pid" "$holder_log" "Line-insert holder"
  wait_success "$waiter_pid" "$waiter_log" "Seal waiter"
  assert_scalar "$CURRENT_DB" "SELECT line_set_sealed::TEXT FROM export_batches WHERE id = '20000000-0000-0000-0000-000000000540';" "true" "Concurrent seal state"
  assert_scalar "$CURRENT_DB" "SELECT count(*) FROM export_batch_lines WHERE export_batch_id = '20000000-0000-0000-0000-000000000540';" "1" "Concurrent line count"
}

run_duplicate_concurrency() {
  echo "  Checking concurrent duplicate task/field rejection..."
  gate_app="st_v_${RUN_ID}_duplicate_gate"
  holder_app="st_v_${RUN_ID}_duplicate_holder"
  waiter_app="st_v_${RUN_ID}_duplicate_waiter"
  gate_log="$LOG_DIR/duplicate-gate.log"
  holder_log="$LOG_DIR/duplicate-holder.log"
  waiter_log="$LOG_DIR/duplicate-waiter.log"

  start_gate "$CURRENT_DB" 2 "$gate_app" "$gate_log"
  start_holder_file "$CURRENT_DB" 2 "$holder_app" /validation/concurrency/duplicate-line-holder.sql "$holder_log"
  holder_pid=$HOLDER_PID
  (
    export PGAPPNAME="$waiter_app"
    db_psql "$CURRENT_DB" -f /validation/concurrency/duplicate-line-waiter.sql
  ) >"$waiter_log" 2>&1 &
  waiter_pid=$!
  wait_for_blocker "$CURRENT_DB" "$waiter_app" "$holder_app"
  release_gate "$CURRENT_DB" "$gate_app"
  wait_success "$holder_pid" "$holder_log" "Duplicate-line holder"
  wait_failure "$waiter_pid" "$waiter_log" "duplicate key value violates unique constraint" "Duplicate-line waiter"
  assert_scalar "$CURRENT_DB" "SELECT count(*) FROM export_batch_lines WHERE export_batch_id = '20000000-0000-0000-0000-000000000541' AND imported_task_id = '20000000-0000-0000-0000-000000000102' AND field_name = 'percent_complete';" "1" "Concurrent duplicate line count"
}

run_approval_preview_concurrency() {
  echo "  Checking approval change versus preview-line creation..."
  gate_app="st_v_${RUN_ID}_preview_gate"
  holder_app="st_v_${RUN_ID}_preview_holder"
  waiter_app="st_v_${RUN_ID}_preview_approval"
  gate_log="$LOG_DIR/preview-gate.log"
  holder_log="$LOG_DIR/preview-holder.log"
  waiter_log="$LOG_DIR/preview-approval.log"

  start_gate "$CURRENT_DB" 3 "$gate_app" "$gate_log"
  start_holder_file "$CURRENT_DB" 3 "$holder_app" /validation/concurrency/preview-line-holder.sql "$holder_log"
  holder_pid=$HOLDER_PID
  start_approval_waiter "$CURRENT_DB" "$waiter_app" "$waiter_log" \
    20000000-0000-0000-0000-000000003431 \
    20000000-0000-0000-0000-000000000243 rejected \
    "Concurrent rejection after preview line"
  waiter_pid=$WAITER_PID
  wait_for_blocker "$CURRENT_DB" "$waiter_app" "$holder_app"
  release_gate "$CURRENT_DB" "$gate_app"
  wait_success "$holder_pid" "$holder_log" "Preview-line holder"
  wait_success "$waiter_pid" "$waiter_log" "Preview approval waiter"
  expect_sql_failure "$CURRENT_DB" "UPDATE export_batches SET line_set_sealed = true WHERE id = '20000000-0000-0000-0000-000000000543';" "fresh export preview" "Stale concurrent preview seal"
}

run_approval_batch_concurrency() {
  echo "  Checking approval change versus batch approval..."
  gate_app="st_v_${RUN_ID}_batch_gate"
  holder_app="st_v_${RUN_ID}_batch_holder"
  waiter_app="st_v_${RUN_ID}_batch_approval"
  gate_log="$LOG_DIR/batch-gate.log"
  holder_log="$LOG_DIR/batch-holder.log"
  waiter_log="$LOG_DIR/batch-approval.log"

  start_gate "$CURRENT_DB" 4 "$gate_app" "$gate_log"
  start_holder_file "$CURRENT_DB" 4 "$holder_app" /validation/concurrency/batch-approval-holder.sql "$holder_log"
  holder_pid=$HOLDER_PID
  start_approval_waiter "$CURRENT_DB" "$waiter_app" "$waiter_log" \
    20000000-0000-0000-0000-000000003441 \
    20000000-0000-0000-0000-000000000244 rejected \
    "Concurrent rejection after batch approval"
  waiter_pid=$WAITER_PID
  wait_for_blocker "$CURRENT_DB" "$waiter_app" "$holder_app"
  release_gate "$CURRENT_DB" "$gate_app"
  wait_success "$holder_pid" "$holder_log" "Batch-approval holder"
  wait_success "$waiter_pid" "$waiter_log" "Batch approval-event waiter"
  assert_scalar "$CURRENT_DB" "SELECT status::TEXT FROM export_batches WHERE id = '20000000-0000-0000-0000-000000000544';" "approved" "Concurrent batch approval state"
  expect_sql_failure "$CURRENT_DB" "UPDATE export_batches SET status = 'generated', generated_at = now(), export_file_uri = 'validation://stale-batch.xml', export_file_hash = repeat('f', 64) WHERE id = '20000000-0000-0000-0000-000000000544';" "fresh export preview" "Stale approved batch generation"
}

run_approval_generation_concurrency() {
  echo "  Checking approval change versus generation..."
  gate_app="st_v_${RUN_ID}_generation_gate"
  holder_app="st_v_${RUN_ID}_generation_holder"
  waiter_app="st_v_${RUN_ID}_generation_approval"
  gate_log="$LOG_DIR/generation-gate.log"
  holder_log="$LOG_DIR/generation-holder.log"
  waiter_log="$LOG_DIR/generation-approval.log"

  start_gate "$CURRENT_DB" 5 "$gate_app" "$gate_log"
  (
    export PGAPPNAME="$holder_app"
    db_psql "$CURRENT_DB" -v gate_id=5 -v batch_id=20000000-0000-0000-0000-000000000545 -f /validation/concurrency/generation-holder.sql
  ) >"$holder_log" 2>&1 &
  holder_pid=$!
  wait_for_holder_gate "$CURRENT_DB" "$holder_app"
  start_approval_waiter "$CURRENT_DB" "$waiter_app" "$waiter_log" \
    20000000-0000-0000-0000-000000003451 \
    20000000-0000-0000-0000-000000000245 rejected \
    "Concurrent rejection after generation"
  waiter_pid=$WAITER_PID
  wait_for_blocker "$CURRENT_DB" "$waiter_app" "$holder_app"
  release_gate "$CURRENT_DB" "$gate_app"
  wait_success "$holder_pid" "$holder_log" "Generation holder"
  wait_success "$waiter_pid" "$waiter_log" "Generation approval-event waiter"
  assert_scalar "$CURRENT_DB" "SELECT status::TEXT FROM export_batches WHERE id = '20000000-0000-0000-0000-000000000545';" "generated" "Concurrent generation state"
}

run_snapshot_generation_concurrency() {
  echo "  Checking accepted-snapshot change versus generation..."
  gate_app="st_v_${RUN_ID}_snapshot_gate"
  holder_app="st_v_${RUN_ID}_snapshot_holder"
  waiter_app="st_v_${RUN_ID}_snapshot_waiter"
  gate_log="$LOG_DIR/snapshot-gate.log"
  holder_log="$LOG_DIR/snapshot-holder.log"
  waiter_log="$LOG_DIR/snapshot-waiter.log"

  start_gate "$CURRENT_DB" 6 "$gate_app" "$gate_log"
  (
    export PGAPPNAME="$holder_app"
    db_psql "$CURRENT_DB" -v gate_id=6 -v batch_id=20000000-0000-0000-0000-000000000546 -f /validation/concurrency/generation-holder.sql
  ) >"$holder_log" 2>&1 &
  holder_pid=$!
  wait_for_holder_gate "$CURRENT_DB" "$holder_app"
  (
    export PGAPPNAME="$waiter_app"
    db_psql "$CURRENT_DB" -f /validation/concurrency/snapshot-update-waiter.sql
  ) >"$waiter_log" 2>&1 &
  waiter_pid=$!
  wait_for_blocker "$CURRENT_DB" "$waiter_app" "$holder_app"
  release_gate "$CURRENT_DB" "$gate_app"
  wait_success "$holder_pid" "$holder_log" "Snapshot generation holder"
  wait_success "$waiter_pid" "$waiter_log" "Snapshot update waiter"
  assert_scalar "$CURRENT_DB" "SELECT status::TEXT FROM export_batches WHERE id = '20000000-0000-0000-0000-000000000546';" "generated" "Snapshot-concurrent generation state"
  assert_scalar "$CURRENT_DB" "SELECT status::TEXT FROM project_snapshots WHERE id = '20000000-0000-0000-0000-000000000004';" "superseded" "Post-generation snapshot state"
  db_psql "$CURRENT_DB" -c "UPDATE project_snapshots SET status = 'accepted' WHERE id = '20000000-0000-0000-0000-000000000004';" >/dev/null
}

run_task_generation_concurrency() {
  echo "  Checking imported-task change versus generation..."
  gate_app="st_v_${RUN_ID}_task_gate"
  holder_app="st_v_${RUN_ID}_task_holder"
  waiter_app="st_v_${RUN_ID}_task_waiter"
  gate_log="$LOG_DIR/task-gate.log"
  holder_log="$LOG_DIR/task-holder.log"
  waiter_log="$LOG_DIR/task-waiter.log"

  start_gate "$CURRENT_DB" 7 "$gate_app" "$gate_log"
  (
    export PGAPPNAME="$holder_app"
    db_psql "$CURRENT_DB" -v gate_id=7 -v batch_id=20000000-0000-0000-0000-000000000547 -f /validation/concurrency/generation-holder.sql
  ) >"$holder_log" 2>&1 &
  holder_pid=$!
  wait_for_holder_gate "$CURRENT_DB" "$holder_app"
  (
    export PGAPPNAME="$waiter_app"
    db_psql "$CURRENT_DB" -f /validation/concurrency/task-update-waiter.sql
  ) >"$waiter_log" 2>&1 &
  waiter_pid=$!
  wait_for_blocker "$CURRENT_DB" "$waiter_app" "$holder_app"
  release_gate "$CURRENT_DB" "$gate_app"
  wait_success "$holder_pid" "$holder_log" "Task generation holder"
  wait_success "$waiter_pid" "$waiter_log" "Task update waiter"
  assert_scalar "$CURRENT_DB" "SELECT status::TEXT FROM export_batches WHERE id = '20000000-0000-0000-0000-000000000547';" "generated" "Task-concurrent generation state"
}

run_generation_rollback_concurrency() {
  echo "  Checking failed generation rollback versus approval insertion..."
  gate_app="st_v_${RUN_ID}_rollback_gate"
  holder_app="st_v_${RUN_ID}_rollback_holder"
  waiter_app="st_v_${RUN_ID}_rollback_approval"
  gate_log="$LOG_DIR/rollback-gate.log"
  holder_log="$LOG_DIR/rollback-holder.log"
  waiter_log="$LOG_DIR/rollback-approval.log"

  start_gate "$CURRENT_DB" 8 "$gate_app" "$gate_log"
  start_holder_file "$CURRENT_DB" 8 "$holder_app" /validation/concurrency/generation-rollback-holder.sql "$holder_log"
  holder_pid=$HOLDER_PID
  start_approval_waiter "$CURRENT_DB" "$waiter_app" "$waiter_log" \
    20000000-0000-0000-0000-000000003481 \
    20000000-0000-0000-0000-000000000248 superseded \
    "Concurrent supersession after rollback"
  waiter_pid=$WAITER_PID
  wait_for_blocker "$CURRENT_DB" "$waiter_app" "$holder_app"
  release_gate "$CURRENT_DB" "$gate_app"
  wait_failure "$holder_pid" "$holder_log" "division by zero" "Generation rollback holder"
  wait_success "$waiter_pid" "$waiter_log" "Rollback approval-event waiter"
  assert_scalar "$CURRENT_DB" "SELECT status::TEXT FROM export_batches WHERE id = '20000000-0000-0000-0000-000000000548';" "approved" "Rolled-back generation state"
  assert_scalar "$CURRENT_DB" "SELECT (generated_at IS NULL AND export_file_uri IS NULL AND export_file_hash IS NULL)::TEXT FROM export_batches WHERE id = '20000000-0000-0000-0000-000000000548';" "true" "Rolled-back generation metadata"
}

run_reversed_candidate_concurrency() {
  echo "  Checking reversed multi-candidate approval ordering..."
  gate_app="st_v_${RUN_ID}_reverse_gate"
  holder_app="st_v_${RUN_ID}_reverse_holder"
  waiter_app="st_v_${RUN_ID}_reverse_waiter"
  gate_log="$LOG_DIR/reverse-gate.log"
  holder_log="$LOG_DIR/reverse-holder.log"
  waiter_log="$LOG_DIR/reverse-waiter.log"

  start_gate "$CURRENT_DB" 9 "$gate_app" "$gate_log"
  start_holder_file "$CURRENT_DB" 9 "$holder_app" /validation/concurrency/reversed-approval-holder.sql "$holder_log"
  holder_pid=$HOLDER_PID
  (
    export PGAPPNAME="$waiter_app"
    db_psql "$CURRENT_DB" -f /validation/concurrency/reversed-approval-waiter.sql
  ) >"$waiter_log" 2>&1 &
  waiter_pid=$!
  wait_for_blocker "$CURRENT_DB" "$waiter_app" "$holder_app"
  release_gate "$CURRENT_DB" "$gate_app"
  wait_success "$holder_pid" "$holder_log" "Reversed approval holder"
  wait_success "$waiter_pid" "$waiter_log" "Reversed approval waiter"
  assert_scalar "$CURRENT_DB" "SELECT (min(approval_event_order) FILTER (WHERE id IN ('20000000-0000-0000-0000-000000000353','20000000-0000-0000-0000-000000000354')) > max(approval_event_order) FILTER (WHERE id IN ('20000000-0000-0000-0000-000000000351','20000000-0000-0000-0000-000000000352')))::TEXT FROM approval_records;" "true" "Serialized reversed approval ordering"
}

echo "Preparing populated V006-to-V007 upgrade validation..."
create_database "$UPGRADE_DB"
apply_v001_through_v006 "$UPGRADE_DB"
db_psql "$UPGRADE_DB" -f /validation/fixtures/v006-export-integrity-seed.sql >/dev/null
apply_v007 "$UPGRADE_DB"
db_psql "$UPGRADE_DB" -f /validation/assertions/export-integrity-upgrade.sql

echo "Preparing clean current-policy database validation..."
create_database "$CURRENT_DB"
apply_all_migrations "$CURRENT_DB"
db_psql "$CURRENT_DB" -f /validation/assertions/export-integrity-clean.sql
db_psql "$CURRENT_DB" -f /validation/assertions/export-integrity-current-policy.sql

echo "Running deterministic PostgreSQL concurrency validation..."
run_line_seal_concurrency
run_duplicate_concurrency
run_approval_preview_concurrency
run_approval_batch_concurrency
run_approval_generation_concurrency
run_snapshot_generation_concurrency
run_task_generation_concurrency
run_generation_rollback_concurrency
run_reversed_candidate_concurrency

echo "Validating late V007 rollback under one PostgreSQL transaction..."
create_database "$ATOMIC_DB"
apply_v001_through_v006 "$ATOMIC_DB"
db_psql "$ATOMIC_DB" -f /validation/fixtures/v006-export-integrity-seed.sql >/dev/null
cp /migrations/V007__enforce_export_candidate_integrity.sql "$LOG_DIR/failing-v007.sql"
printf '\nSELECT 1 / 0;\n' >>"$LOG_DIR/failing-v007.sql"
if db_psql "$ATOMIC_DB" --single-transaction -f "$LOG_DIR/failing-v007.sql" >"$LOG_DIR/atomic-v007.log" 2>&1; then
  echo "The intentionally failing V007 unexpectedly succeeded." >&2
  exit 1
fi
if ! grep -F "division by zero" "$LOG_DIR/atomic-v007.log" >/dev/null; then
  echo "The intentionally failing V007 failed before its late failure sentinel." >&2
  cat "$LOG_DIR/atomic-v007.log" >&2
  exit 1
fi
db_psql "$ATOMIC_DB" -f /validation/assertions/export-integrity-atomicity-v007.sql

echo "PostgreSQL V001-V007 export-integrity validation passed."
