#!/usr/bin/env bash
set -euo pipefail

# Read-only diagnostic for an explicitly isolated PostgreSQL database.
# This script never runs Flyway repair/baseline, DDL, or data cleanup.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MIGRATIONS="$ROOT/education-app/src/main/resources/db/migration"

: "${CHRONOS_FLYWAY_VERIFY_DB:?set CHRONOS_FLYWAY_VERIFY_DB to an isolated database name}"
: "${CHRONOS_FLYWAY_VERIFY_ALLOW_ISOLATED:?set CHRONOS_FLYWAY_VERIFY_ALLOW_ISOLATED=1 to acknowledge read-only verification}"

if [[ "$CHRONOS_FLYWAY_VERIFY_ALLOW_ISOLATED" != "1" ]]; then
  echo "FAIL: CHRONOS_FLYWAY_VERIFY_ALLOW_ISOLATED must be 1" >&2
  exit 2
fi
if [[ ! "$CHRONOS_FLYWAY_VERIFY_DB" =~ (^|[-_])(test|verify)([-_]|$) ]]; then
  echo "FAIL: database name must contain test or verify; refusing non-isolated database" >&2
  exit 2
fi
command -v psql >/dev/null || {
  echo "FAIL: psql is required; no database was contacted" >&2
  exit 127
}

"$ROOT/scripts/check-education-flyway.sh" both

versions="$(
  find "$MIGRATIONS" -maxdepth 1 -type f -name 'V*.sql' -print |
    sed 's#^.*/##; s/__.*//' |
    sort -V
)"
expected_latest="$(printf '%s\n' "$versions" | tail -1)"
expected_count="$(printf '%s\n' "$versions" | sed '/^$/d' | wc -l | tr -d ' ')"

psql_args=(-X -v ON_ERROR_STOP=1 --dbname="$CHRONOS_FLYWAY_VERIFY_DB")
if [[ -n "${CHRONOS_FLYWAY_VERIFY_PSQL_OPTIONS:-}" ]]; then
  # Deliberately opt-in for local socket/port/SSL options; do not parse URLs.
  read -r -a extra_psql_args <<< "$CHRONOS_FLYWAY_VERIFY_PSQL_OPTIONS"
  psql_args+=("${extra_psql_args[@]}")
fi

printf 'database=%s mode=read-only expected_latest=%s expected_migrations=%s\n' \
  "$CHRONOS_FLYWAY_VERIFY_DB" "$expected_latest" "$expected_count"
psql "${psql_args[@]}" <<'SQL'
\pset pager off
\echo 'flyway_schema_history diagnostics:'
SELECT installed_rank, version, description, type, checksum, success
FROM flyway_schema_history
ORDER BY installed_rank;
\echo 'failed migrations (must be empty):'
SELECT installed_rank, version, description, checksum
FROM flyway_schema_history
WHERE success = false;
\echo 'duplicate successful versions (must be empty):'
SELECT version, count(*) AS rows
FROM flyway_schema_history
WHERE success = true AND version IS NOT NULL
GROUP BY version
HAVING count(*) > 1;
\echo 'successful migrations without checksum (must be empty for SQL):'
SELECT version, description
FROM flyway_schema_history
WHERE success = true AND type = 'SQL' AND checksum IS NULL;
SQL

failed_count="$(
  psql "${psql_args[@]}" -Atqc \
    "SELECT count(*) FROM flyway_schema_history WHERE success = false"
)"
duplicate_count="$(
  psql "${psql_args[@]}" -Atqc \
    "SELECT count(*) FROM (SELECT version FROM flyway_schema_history WHERE success = true AND version IS NOT NULL GROUP BY version HAVING count(*) > 1) duplicates"
)"
null_checksum_count="$(
  psql "${psql_args[@]}" -Atqc \
    "SELECT count(*) FROM flyway_schema_history WHERE success = true AND type = 'SQL' AND checksum IS NULL"
)"
if [[ "$failed_count" != "0" || "$duplicate_count" != "0" || "$null_checksum_count" != "0" ]]; then
  echo "FAIL: isolated Flyway history contains failed, duplicate, or checksum-less rows" >&2
  exit 1
fi

actual_latest="$(
  psql "${psql_args[@]}" -Atqc \
    "SELECT COALESCE(max(version), '') FROM flyway_schema_history WHERE success = true"
)"
if [[ "$actual_latest" != "$expected_latest" ]]; then
  echo "FAIL: isolated database latest successful version '$actual_latest' != repository '$expected_latest'" >&2
  exit 1
fi

echo "PASS: isolated Flyway history is readable and reaches $expected_latest"
