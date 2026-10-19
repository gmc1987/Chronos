#!/usr/bin/env bash
set -euo pipefail

# Restores a verified backup only into a new, explicitly named database and
# runs the read-only deployment gate. Existing databases are never overwritten.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
backup="${1:-}"
target_db="${2:-}"
mode="${3:-empty}"
if [[ -z "$backup" || -z "$target_db" || ! -f "$backup" ]]; then
  echo "usage: $0 <backup.dump> <new-database-name> [empty|existing]" >&2
  exit 2
fi
if [[ ! "$target_db" =~ ^[A-Za-z0-9_-]+$ ]]; then
  echo "FAIL: restore database name must be a simple identifier" >&2
  exit 2
fi
for required in CHRONOS_DB_URL CHRONOS_DB_USERNAME CHRONOS_DB_PASSWORD CHRONOS_DB_RESTORE_ACK; do
  if [[ -z "${!required:-}" ]]; then echo "FAIL: $required is required" >&2; exit 2; fi
done
if [[ "$CHRONOS_DB_RESTORE_ACK" != "$target_db" ]]; then
  echo "FAIL: CHRONOS_DB_RESTORE_ACK must exactly match $target_db" >&2
  exit 2
fi
if [[ "$CHRONOS_DB_URL" =~ ^jdbc:postgresql://([^/:?#]+)(:([0-9]+))?/([^/?#]+)(\?.*)?$ ]]; then
  db_host="${BASH_REMATCH[1]}"; db_port="${BASH_REMATCH[3]:-5432}"; source_db="${BASH_REMATCH[4]}"
else
  echo "FAIL: CHRONOS_DB_URL must be a JDBC PostgreSQL URL" >&2; exit 2
fi
if [[ "$source_db" == "$target_db" ]]; then
  echo "FAIL: restore target must differ from the source database" >&2; exit 2
fi
for command in psql createdb dropdb pg_restore sha256sum; do
  command -v "$command" >/dev/null || { echo "FAIL: $command is required" >&2; exit 127; }
done
checksum="$backup.sha256"
if [[ ! -f "$checksum" ]]; then echo "FAIL: checksum file $checksum is missing" >&2; exit 1; fi
(cd "$(dirname "$backup")" && sha256sum --check "$(basename "$checksum")")
pg_restore --list "$backup" >/dev/null

export PGPASSWORD="$CHRONOS_DB_PASSWORD"
common=(--host="$db_host" --port="$db_port" --username="$CHRONOS_DB_USERNAME")
if psql "${common[@]}" --dbname=postgres -Atqc \
  "SELECT 1 FROM pg_database WHERE datname = '$target_db'" | grep -q 1; then
  echo "FAIL: target database already exists; refusing to overwrite it" >&2
  exit 1
fi
createdb "${common[@]}" "$target_db"
cleanup_on_failure() {
  status=$?
  if (( status != 0 )); then dropdb "${common[@]}" --if-exists "$target_db" || true; fi
  exit "$status"
}
trap cleanup_on_failure EXIT
pg_restore "${common[@]}" --dbname="$target_db" --no-owner --no-privileges --exit-on-error "$backup"

export CHRONOS_DB_URL="jdbc:postgresql://$db_host:$db_port/$target_db"
export CHRONOS_DB_ACCEPT_TARGET=1
"$ROOT/scripts/education-deployment-acceptance.sh" "$mode"
trap - EXIT
printf 'PASS: restored and verified database=%s from=%s\n' "$target_db" "$backup"
if [[ "${CHRONOS_DROP_VERIFIED_RESTORE:-0}" == "1" ]]; then
  dropdb "${common[@]}" "$target_db"
  printf 'cleanup: dropped verified restore database=%s\n' "$target_db"
fi
