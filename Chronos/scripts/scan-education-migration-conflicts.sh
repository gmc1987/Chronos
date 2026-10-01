#!/usr/bin/env bash
set -euo pipefail

# Read-only repository check. It never connects to or changes a database.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MIGRATIONS="$ROOT/education-app/src/main/resources/db/migration"

test -d "$MIGRATIONS" || {
  echo "FAIL: education migration directory is missing: $MIGRATIONS" >&2
  exit 1
}

count=0
previous_version=""
previous_file=""
latest=""
while IFS= read -r migration; do
  file="$(basename "$migration")"
  if [[ ! "$file" =~ ^V[0-9]+__[a-z0-9][a-z0-9_-]*\.sql$ ]]; then
    echo "FAIL: invalid education migration filename: $file" >&2
    exit 1
  fi
  version="${file%%__*}"
  if [[ "$version" == "$previous_version" ]]; then
    echo "FAIL: duplicate education migration version $version:" >&2
    printf '  %s\n  %s\n' "$previous_file" "$file" >&2
    exit 1
  fi
  previous_version="$version"
  previous_file="$file"
  latest="$version"
  count=$((count + 1))
done < <(find "$MIGRATIONS" -maxdepth 1 -type f -name 'V*.sql' -print | sort -V)

(( count > 0 )) || {
  echo "FAIL: no education migrations found" >&2
  exit 1
}

test -f "$MIGRATIONS/V0__chronos_education_baseline.sql" || {
  echo "FAIL: V0 education baseline is missing" >&2
  exit 1
}

printf 'PASS: %d education migration versions; latest=%s; duplicates=0; filenames=valid\n' \
  "$count" "$latest"
