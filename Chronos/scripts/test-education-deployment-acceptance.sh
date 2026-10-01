#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="$ROOT/scripts/education-deployment-acceptance.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
cat >"$tmp/psql" <<'EOF'
#!/usr/bin/env bash
echo "ERROR: psql must not be called for missing or invalid input" >&2
exit 99
EOF
chmod +x "$tmp/psql"

if PATH="$tmp:$PATH" "$SCRIPT" empty >/dev/null 2>"$tmp/missing.err"; then
  echo "FAIL: missing credentials unexpectedly passed" >&2
  exit 1
fi
grep -q 'CHRONOS_DB_URL is required' "$tmp/missing.err"

if PATH="$tmp:$PATH" CHRONOS_DB_URL='jdbc:postgresql://host:5432/unknown' \
  CHRONOS_DB_USERNAME=user CHRONOS_DB_PASSWORD=secret \
  CHRONOS_DB_ACCEPT_TARGET=0 "$SCRIPT" existing >/dev/null 2>"$tmp/ack.err"; then
  echo "FAIL: unacknowledged target unexpectedly passed" >&2
  exit 1
fi
grep -q 'no database was contacted' "$tmp/ack.err"

bash -n "$SCRIPT"
echo "PASS: deployment acceptance refuses missing credentials and unacknowledged targets"
