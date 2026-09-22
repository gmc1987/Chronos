#!/usr/bin/env bash
set -euo pipefail

# Local, non-production evidence runner. It uses repository tests and static
# checks only; deployment modes are deliberately opt-in and read-only.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mode="${1:-local}"

case "$mode" in
  local)
    "$ROOT/scripts/scan-education-migration-conflicts.sh"
    "$ROOT/scripts/check-education-flyway.sh" both
    (
      cd "$ROOT"
      ./mvnw -q -pl education-class-scheduling -am \
        -Dtest=EducationDataScopeServiceTest,CourseAdjustmentRecoveryControllerTest,CourseAdjustmentApplicationServiceTest \
        -Dsurefire.failIfNoSpecifiedTests=false test
    )
    echo "PASS: local education acceptance evidence (scope, cross-campus denial, retry/replay)"
    ;;
  deployment-flyway)
    exec "$ROOT/scripts/verify-education-flyway-isolated.sh"
    ;;
  *)
    echo "usage: $0 [local|deployment-flyway]" >&2
    exit 2
    ;;
esac
