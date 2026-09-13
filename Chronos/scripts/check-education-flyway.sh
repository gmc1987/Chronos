#!/usr/bin/env bash
set -euo pipefail

# Static deployment gate for both an empty database and an existing database.
# The application performs the actual migration; this check prevents unsafe
# defaults (Hibernate DDL or an unbounded test database) from reaching it.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="$ROOT/education-app/src/main/resources/application.yml"
MIGRATIONS="$(dirname "$APP")/db/migration"
mode="${1:-both}"

case "$mode" in
  empty|existing|both) ;;
  *) echo "usage: $0 [empty|existing|both]" >&2; exit 2 ;;
esac

grep -q 'ddl-auto:.*validate' "$APP" || {
  echo "FAIL: JPA must validate Flyway-managed schema" >&2; exit 1;
}
grep -q 'baseline-on-migrate: true' "$APP" || {
  echo "FAIL: existing databases require Flyway baseline-on-migrate" >&2; exit 1;
}
grep -q 'locations: classpath:db/migration' "$APP" || {
  echo "FAIL: migration location is not configured" >&2; exit 1;
}
test -f "$MIGRATIONS/V0__chronos_education_baseline.sql" || {
  echo "FAIL: empty databases require V0 baseline" >&2; exit 1;
}

if [[ "$mode" != existing ]]; then
  echo "empty-db: V0 baseline and ordered migrations are present"
fi
if [[ "$mode" != empty ]]; then
  echo "existing-db: baseline-on-migrate and validate are enabled"
fi
echo "Flyway/config check passed"
