#!/usr/bin/env bash
set -euo pipefail

# Creates a portable PostgreSQL custom-format backup, verifies its catalog and
# writes checksum/manifest files. The target must be explicitly acknowledged.

for required in CHRONOS_DB_URL CHRONOS_DB_USERNAME CHRONOS_DB_PASSWORD CHRONOS_DB_ACCEPT_TARGET; do
  if [[ -z "${!required:-}" ]]; then
    echo "FAIL: $required is required; no backup was created" >&2
    exit 2
  fi
done
if [[ "$CHRONOS_DB_ACCEPT_TARGET" != "1" ]]; then
  echo "FAIL: set CHRONOS_DB_ACCEPT_TARGET=1 to acknowledge the source database" >&2
  exit 2
fi
if [[ "$CHRONOS_DB_URL" =~ ^jdbc:postgresql://([^/:?#]+)(:([0-9]+))?/([^/?#]+)(\?.*)?$ ]]; then
  db_host="${BASH_REMATCH[1]}"
  db_port="${BASH_REMATCH[3]:-5432}"
  db_name="${BASH_REMATCH[4]}"
else
  echo "FAIL: CHRONOS_DB_URL must be a JDBC PostgreSQL URL" >&2
  exit 2
fi
if [[ ! "$db_name" =~ ^[A-Za-z0-9_-]+$ ]]; then
  echo "FAIL: unsafe database name" >&2
  exit 2
fi
for command in pg_dump pg_restore sha256sum; do
  command -v "$command" >/dev/null || { echo "FAIL: $command is required" >&2; exit 127; }
done

output_dir="${CHRONOS_BACKUP_DIR:-$PWD/backups}"
retention_days="${CHRONOS_BACKUP_RETENTION_DAYS:-30}"
if [[ ! "$retention_days" =~ ^[0-9]+$ ]]; then
  echo "FAIL: CHRONOS_BACKUP_RETENTION_DAYS must be a non-negative integer" >&2
  exit 2
fi
mkdir -p "$output_dir"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
prefix="chronos-${db_name}-${timestamp}"
dump="$output_dir/$prefix.dump"
manifest="$output_dir/$prefix.manifest.json"
checksum="$dump.sha256"
temporary="$dump.partial"
trap 'rm -f "$temporary"' EXIT

export PGPASSWORD="$CHRONOS_DB_PASSWORD"
pg_dump -Fc --no-owner --no-privileges \
  --host="$db_host" --port="$db_port" --username="$CHRONOS_DB_USERNAME" \
  --dbname="$db_name" --file="$temporary"
pg_restore --list "$temporary" >/dev/null
mv "$temporary" "$dump"
sha256sum "$dump" >"$checksum"
bytes="$(wc -c <"$dump" | tr -d ' ')"
sha="$(cut -d' ' -f1 "$checksum")"
cat >"$manifest" <<EOF
{"database":"$db_name","createdAt":"$timestamp","format":"postgres-custom","bytes":$bytes,"sha256":"$sha","ownerIncluded":false,"privilegesIncluded":false}
EOF

if (( retention_days > 0 )); then
  find "$output_dir" -maxdepth 1 -type f \
    \( -name 'chronos-*.dump' -o -name 'chronos-*.dump.sha256' -o -name 'chronos-*.manifest.json' \) \
    -mtime "+$retention_days" -delete
fi
printf 'PASS: backup=%s bytes=%s sha256=%s manifest=%s\n' "$dump" "$bytes" "$sha" "$manifest"
