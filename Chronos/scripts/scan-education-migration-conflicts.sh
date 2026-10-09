#!/usr/bin/env bash
set -euo pipefail

# Read-only repository check. It never connects to or changes a database.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MIGRATIONS="$ROOT/education-app/src/main/resources/db/migration"

test -d "$MIGRATIONS" || {
  echo "FAIL: education migration directory is missing: $MIGRATIONS" >&2
  exit 1
}

# Flyway accepts numeric version components separated by dots or underscores.
# Compare their normalized values: V1, V01 and V1_0 describe the same version.
python3 - "$MIGRATIONS" <<'PY'
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])
versions = {}
latest = None
files = sorted(root.glob("V*.sql"))
for path in files:
    match = re.fullmatch(r"V([0-9]+(?:[._][0-9]+)*)__[a-z0-9][a-z0-9_-]*\.sql", path.name)
    if not match:
        sys.exit(f"FAIL: invalid education migration filename: {path.name}")
    parts = [int(part) for part in re.split(r"[._]", match.group(1))]
    while len(parts) > 1 and parts[-1] == 0:
        parts.pop()
    version = tuple(parts)
    if version in versions:
        sys.exit(f"FAIL: duplicate education migration version:\n  {versions[version]}\n  {path.name}")
    versions[version] = path.name
    if latest is None or version > latest:
        latest = version
if not files:
    sys.exit("FAIL: no education migrations found")
if not (root / "V0__chronos_education_baseline.sql").is_file():
    sys.exit("FAIL: V0 education baseline is missing")
latest_name = versions[latest].split("__", 1)[0]
print(f"PASS: {len(files)} education migration versions; latest={latest_name}; duplicates=0; filenames=valid")
PY
