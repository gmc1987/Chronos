#!/usr/bin/env bash
set -euo pipefail

# Produces an immutable gzip JSONL archive of audit rows older than an explicit
# UTC cutoff. Optional SIEM delivery is HTTPS-only. This script never purges the
# source table; retention deletion must follow the organization's legal policy.

for required in CHRONOS_DB_URL CHRONOS_DB_USERNAME CHRONOS_DB_PASSWORD CHRONOS_AUDIT_BEFORE; do
  if [[ -z "${!required:-}" ]]; then echo "FAIL: $required is required" >&2; exit 2; fi
done
if [[ "$CHRONOS_DB_URL" =~ ^jdbc:postgresql://([^/:?#]+)(:([0-9]+))?/([^/?#]+)(\?.*)?$ ]]; then
  db_host="${BASH_REMATCH[1]}"; db_port="${BASH_REMATCH[3]:-5432}"; db_name="${BASH_REMATCH[4]}"
else
  echo "FAIL: CHRONOS_DB_URL must be a JDBC PostgreSQL URL" >&2; exit 2
fi
if [[ ! "$CHRONOS_AUDIT_BEFORE" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]]; then
  echo "FAIL: CHRONOS_AUDIT_BEFORE must use UTC format YYYY-MM-DDTHH:MM:SSZ" >&2; exit 2
fi
for command in psql gzip sha256sum; do command -v "$command" >/dev/null || exit 127; done
output_dir="${CHRONOS_AUDIT_ARCHIVE_DIR:-$PWD/audit-archives}"
mkdir -p "$output_dir"
safe_cutoff="${CHRONOS_AUDIT_BEFORE//[:T-]/}"
safe_cutoff="${safe_cutoff%Z}Z"
archive="$output_dir/chronos-audit-before-$safe_cutoff.jsonl.gz"
temporary="$archive.partial"
trap 'rm -f "$temporary"' EXIT
export PGPASSWORD="$CHRONOS_DB_PASSWORD"
psql -X -v ON_ERROR_STOP=1 --host="$db_host" --port="$db_port" \
  --username="$CHRONOS_DB_USERNAME" --dbname="$db_name" -At \
  -c \
  "SET default_transaction_read_only=on; COPY (
     SELECT json_build_object(
       'id', id, 'username', username, 'action', action, 'detail', detail,
       'createTime', create_time, 'lastUpdateTime', last_update_time)::text
     FROM t_audit_log WHERE create_time < '$CHRONOS_AUDIT_BEFORE'::timestamptz
     ORDER BY create_time, id
   ) TO STDOUT" | gzip -9 >"$temporary"
gzip -t "$temporary"
mv "$temporary" "$archive"
sha256sum "$archive" >"$archive.sha256"
rows="$(gzip -cd "$archive" | wc -l | tr -d ' ')"

if [[ -n "${CHRONOS_SIEM_URL:-}" ]]; then
  if [[ "$CHRONOS_SIEM_URL" != https://* && "${CHRONOS_ALLOW_INSECURE_TEST_ENDPOINT:-0}" != "1" ]]; then
    echo "FAIL: CHRONOS_SIEM_URL must use HTTPS" >&2; exit 2
  fi
  command -v curl >/dev/null || exit 127
  curl_args=(--fail-with-body --silent --show-error --retry 3
    -H 'Content-Type: application/x-ndjson' -H 'Content-Encoding: gzip')
  if [[ -n "${CHRONOS_SIEM_BEARER_TOKEN:-}" ]]; then
    curl_args+=(-H "Authorization: Bearer $CHRONOS_SIEM_BEARER_TOKEN")
  fi
  curl "${curl_args[@]}" --data-binary "@$archive" "$CHRONOS_SIEM_URL" >/dev/null
fi
printf 'PASS: audit archive=%s rows=%s sha256=%s siem=%s\n' \
  "$archive" "$rows" "$(cut -d' ' -f1 "$archive.sha256")" "${CHRONOS_SIEM_URL:+delivered}"
