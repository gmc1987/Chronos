#!/usr/bin/env bash
set -euo pipefail

# Read-only post-deployment acceptance gate for an explicitly supplied
# PostgreSQL target. It never invokes Flyway and never runs DDL or DML.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MIGRATIONS="$ROOT/education-app/src/main/resources/db/migration"
mode="${1:-}"

case "$mode" in
  empty|existing) ;;
  *)
    echo "usage: $0 <empty|existing>" >&2
    exit 2
    ;;
esac

for required in CHRONOS_DB_URL CHRONOS_DB_USERNAME CHRONOS_DB_PASSWORD CHRONOS_DB_ACCEPT_TARGET; do
  if [[ -z "${!required:-}" ]]; then
    echo "FAIL: $required is required; no database was contacted" >&2
    exit 2
  fi
done
if [[ "$CHRONOS_DB_ACCEPT_TARGET" != "1" ]]; then
  echo "FAIL: set CHRONOS_DB_ACCEPT_TARGET=1 to acknowledge the explicit target; no database was contacted" >&2
  exit 2
fi
if [[ "$CHRONOS_DB_URL" =~ ^jdbc:postgresql://([^/:?#]+)(:([0-9]+))?/([^/?#]+)(\?.*)?$ ]]; then
  db_host="${BASH_REMATCH[1]}"
  db_port="${BASH_REMATCH[3]:-5432}"
  db_name="${BASH_REMATCH[4]}"
else
  echo "FAIL: CHRONOS_DB_URL must be a JDBC PostgreSQL URL such as jdbc:postgresql://host:5432/database; no database was contacted" >&2
  exit 2
fi
if [[ "$db_name" == *%* || "$db_name" == .* || "$db_name" == *.* ]]; then
  echo "FAIL: encoded or qualified database names are not accepted; no database was contacted" >&2
  exit 2
fi
if [[ ! "$db_name" =~ ^[A-Za-z0-9_-]+$ ]]; then
  echo "FAIL: CHRONOS_DB_URL contains an unsafe database name; no database was contacted" >&2
  exit 2
fi

command -v psql >/dev/null || {
  echo "FAIL: psql is required; no database was contacted" >&2
  exit 127
}

schema="${CHRONOS_DB_SCHEMA:-public}"
if [[ ! "$schema" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
  echo "FAIL: CHRONOS_DB_SCHEMA must be a simple PostgreSQL identifier; no database was contacted" >&2
  exit 2
fi

psql_args=(-X -v ON_ERROR_STOP=1 --host="$db_host" --port="$db_port"
  --username="$CHRONOS_DB_USERNAME" --dbname="$db_name")
export PGPASSWORD="$CHRONOS_DB_PASSWORD"
readonly_sql() {
  psql "${psql_args[@]}" -Atqc "SET default_transaction_read_only = on; SET search_path TO \"$schema\"; $1"
}

connection="$(
  readonly_sql "SELECT current_database() || '|' || current_schema()"
)" || {
  echo "FAIL: target connection or read-only precheck failed; no schema/history checks ran" >&2
  exit 1
}
IFS='|' read -r actual_db actual_schema <<< "$connection"
if [[ "$actual_db" != "$db_name" || "$actual_schema" != "$schema" ]]; then
  echo "FAIL: connected target is database=$actual_db schema=$actual_schema, expected database=$db_name schema=$schema" >&2
  exit 1
fi
printf 'target database=%s schema=%s mode=%s read_only=true\n' "$actual_db" "$actual_schema" "$mode"

versions="$(
  find "$MIGRATIONS" -maxdepth 1 -type f -name 'V*.sql' -print |
    sed 's#^.*/V##; s/__.*//' | sort -V
)"
expected_latest="$(printf '%s\n' "$versions" | tail -1)"
expected_count="$(printf '%s\n' "$versions" | sed '/^$/d' | wc -l | tr -d ' ')"
key_versions="$(printf '%s\n' "$versions" | awk '
  { versions[NR] = $0 }
  END { for (i = 1; i <= NR; i++) if (i <= 2 || i > NR - 3) print versions[i] }
' | sort -Vu)"
# 老库采用 Flyway 默认 BASELINE=1，V0 结构已由原有数据库提供；
# 新库必须存在 V0 SQL。只允许明确的 0/1 基线，不能跳过任意迁移。
if [[ "$mode" == existing ]]; then
  key_versions="$(printf '%s\n' "$key_versions" | awk '$0 != "0"')"
fi

history_table="${schema}.flyway_schema_history"
if [[ "$(readonly_sql "SELECT to_regclass('$schema.flyway_schema_history') IS NOT NULL")" != "t" ]]; then
  echo "FAIL: $history_table is missing; target is not accepted" >&2
  exit 1
fi
if [[ "$mode" == existing && "$(readonly_sql "SELECT count(*) FROM \"$schema\".flyway_schema_history
  WHERE success = true AND ((version = '0' AND type = 'SQL')
    OR (version IN ('0','1') AND type = 'BASELINE'))")" == "0" ]]; then
  echo "FAIL: existing schema has neither V0 SQL nor a supported 0/1 Flyway baseline" >&2
  exit 1
fi
history_rows="$(
  readonly_sql "SELECT version || '|' || COALESCE(checksum::text, '') || '|' || success
    FROM \"$schema\".flyway_schema_history
    WHERE version IN ($(printf "'%s'," $key_versions | sed 's/,$//'))
    ORDER BY installed_rank"
)"
if [[ -z "$history_rows" ]]; then
  echo "FAIL: $history_table has no key migration rows; target is not accepted" >&2
  exit 1
fi
printf 'key flyway history (version|checksum|success):\n%s\n' "$history_rows"
if [[ "$(printf '%s\n' "$history_rows" | wc -l | tr -d ' ')" != \
      "$(printf '%s\n' "$key_versions" | wc -l | tr -d ' ')" ]]; then
  echo "FAIL: one or more key Flyway migrations are missing" >&2
  exit 1
fi
if printf '%s\n' "$history_rows" | awk -F'|' '$2 == "" || $3 != "true" { bad = 1 } END { exit bad }'; then
  :
else
  echo "FAIL: key Flyway history contains a missing checksum or unsuccessful migration" >&2
  exit 1
fi
failed_count="$(readonly_sql "SELECT count(*) FROM \"$schema\".flyway_schema_history WHERE success = false")"
actual_latest="$(readonly_sql "SELECT version FROM \"$schema\".flyway_schema_history
  WHERE success = true AND version IS NOT NULL
  ORDER BY string_to_array(replace(version, '_', '.'), '.')::bigint[] DESC LIMIT 1")"
if [[ "$failed_count" != "0" || "$actual_latest" != "$expected_latest" ]]; then
  echo "FAIL: Flyway history failed_rows=$failed_count latest=$actual_latest expected_latest=$expected_latest" >&2
  exit 1
fi
if [[ "$mode" == existing ]]; then
  baseline_count="$(readonly_sql "SELECT count(*) FROM \"$schema\".flyway_schema_history WHERE version IN ('0','1') AND type = 'BASELINE' AND success = true")"
  echo "existing-db: baseline_rows=$baseline_count (baseline is valid only for a complete pre-existing schema)"
else
  echo "empty-db: expected V0 plus $((expected_count - 1)) ordered SQL migrations"
fi

key_tables=(edu_academic_term edu_course_offering edu_schedule_entry
  edu_teaching_plan edu_exam_plan edu_gradebook edu_domain_event_outbox int_connector)
missing_tables="$(
  for table in "${key_tables[@]}"; do
    [[ "$(readonly_sql "SELECT to_regclass('$schema.$table') IS NOT NULL")" == "t" ]] || printf '%s\n' "$table"
  done
)"
if [[ -n "$missing_tables" ]]; then
  echo "FAIL: missing key tables in schema $schema:" >&2
  printf '%s\n' "$missing_tables" >&2
  exit 1
fi
printf 'key tables present: %s\n' "${key_tables[*]}"
echo "PASS: education deployment acceptance passed (read-only; no migration was modified or executed)"
