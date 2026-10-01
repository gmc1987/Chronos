#!/usr/bin/env bash
set -euo pipefail

# Reproducible non-production acceptance runner. No account is created and no
# token is printed. HTTP checks are opt-in because a real deployment fixture is
# required for role, campus, publication, and guardian-scope assertions.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UI_ROOT="$(cd "$ROOT/../Chronos-UI" && pwd)"
mode="${1:-local}"

run_local() {
  "$ROOT/scripts/scan-education-migration-conflicts.sh"
  "$ROOT/scripts/check-education-flyway.sh" both
  (
    cd "$ROOT"
    ./mvnw -q -pl education-class-scheduling -am \
      -Dtest=\
EducationDataScopeServiceTest,\
CourseAdjustmentRecoveryControllerTest,\
CourseAdjustmentApplicationServiceTest,\
SupervisionCenterServiceScopeTest,\
HomeSchoolServiceTest,\
GradeControllerHttpTest,\
DomainEventOutboxOperationsHttpTest,\
DomainEventOutboxServiceTest \
      -Dsurefire.failIfNoSpecifiedTests=false test
  )
  (
    cd "$UI_ROOT"
    npm run test:teaching-center-routes
    npm run test:teaching-center-api
    npm run build
  )
  echo "PASS: local role acceptance contracts (teacher, academic affairs, supervision, student, parent)"
  echo "NOTE: browser, database, and deployed HTTP checks were not run; use '$0 http' with a non-production fixture."
}

require_http_environment() {
  command -v curl >/dev/null || { echo "BLOCKED: curl is required for HTTP acceptance" >&2; exit 3; }
  command -v python3 >/dev/null || { echo "BLOCKED: python3 is required for response assertions" >&2; exit 3; }
  : "${CHRONOS_BASE_URL:?BLOCKED: set CHRONOS_BASE_URL to a non-production deployment}"
  : "${CHRONOS_TEACHER_TOKEN:?BLOCKED: set a real non-production ordinary-teacher token}"
  : "${CHRONOS_ACADEMIC_TOKEN:?BLOCKED: set a real non-production academic-affairs token}"
  : "${CHRONOS_SUPERVISOR_TOKEN:?BLOCKED: set a real non-production supervisor token}"
  : "${CHRONOS_STUDENT_TOKEN:?BLOCKED: set a real non-production student token}"
  : "${CHRONOS_PARENT_TOKEN:?BLOCKED: set a real non-production parent token}"
  : "${CHRONOS_OFFERING_ID:?BLOCKED: set an in-scope offering id}"
  : "${CHRONOS_OUT_OF_SCOPE_OFFERING_ID:?BLOCKED: set a different-campus offering id}"
  : "${CHRONOS_HOMEWORK_ID:?BLOCKED: set a published homework id}"
  : "${CHRONOS_PUBLISHED_GRADEBOOK_ID:?BLOCKED: set a published gradebook id}"
  : "${CHRONOS_PARENT_CHILD_IDS:?BLOCKED: set comma-separated child ids authorized for the parent}"
}

http_request() {
  local expected="$1" token="$2" method="$3" path="$4" body="${5:-}"
  local response status
  if [[ -n "$body" ]]; then
    response="$(curl --silent --show-error --fail-with-body -X "$method" \
      -H "Authorization: Bearer $token" -H "Content-Type: application/json" \
      -d "$body" -w $'\n%{http_code}' "${CHRONOS_BASE_URL%/}$path" || true)"
  else
    response="$(curl --silent --show-error --fail-with-body -X "$method" \
      -H "Authorization: Bearer $token" -w $'\n%{http_code}' \
      "${CHRONOS_BASE_URL%/}$path" || true)"
  fi
  status="${response##*$'\n'}"
  [[ "$status" == "$expected" ]] || {
    echo "FAIL: expected HTTP $expected, got $status for $method $path" >&2
    return 1
  }
  echo "OK: HTTP $status $method $path"
  printf '%s' "${response%$'\n'*}"
}

run_http() {
  require_http_environment

  # Each request uses a real role token supplied by the fixture; no admin token
  # is accepted here, so administrator access cannot be reported as role access.
  http_request 200 "$CHRONOS_TEACHER_TOKEN" GET \
    "/education/teaching-center/api/plans?offeringId=$CHRONOS_OFFERING_ID&page=0&size=1" >/dev/null
  http_request 200 "$CHRONOS_ACADEMIC_TOKEN" GET \
    "/admin/education/term-progress" >/dev/null
  http_request 200 "$CHRONOS_SUPERVISOR_TOKEN" GET \
    "/portal/education/supervision/tasks" >/dev/null
  http_request 200 "$CHRONOS_STUDENT_TOKEN" GET \
    "/education/teaching-center/api/homeworks?page=0&size=20" >/dev/null
  parent_response="$(http_request 200 "$CHRONOS_PARENT_TOKEN" GET \
    "/portal/education/family/children")"
  python3 - "$CHRONOS_PARENT_CHILD_IDS" "$parent_response" <<'PY'
import json
import sys

allowed = set(filter(None, sys.argv[1].split(",")))
payload = json.loads(sys.argv[2])
children = payload.get("data", [])
if not isinstance(children, list):
    raise SystemExit("FAIL: parent children response is not a list")
returned = {str(child.get("id")) for child in children}
if not returned <= allowed:
    raise SystemExit(f"FAIL: parent scope leaked child ids: {sorted(returned - allowed)}")
print(f"OK: parent guardian scope ({len(returned)} authorized children)")
PY

  # Cross-campus isolation must fail closed for ordinary student/teacher access.
  http_request 403 "$CHRONOS_TEACHER_TOKEN" GET \
    "/education/teaching-center/api/homeworks?offeringId=$CHRONOS_OUT_OF_SCOPE_OFFERING_ID&page=0&size=20" >/dev/null
  http_request 403 "$CHRONOS_STUDENT_TOKEN" GET \
    "/education/teaching-center/api/homeworks?offeringId=$CHRONOS_OUT_OF_SCOPE_OFFERING_ID&page=0&size=20" >/dev/null

  # A published gradebook remains readable but cannot be edited through the
  # ordinary teacher path. The fixture supplies a harmless empty item payload.
  http_request 200 "$CHRONOS_TEACHER_TOKEN" GET \
    "/admin/education/grades/gradebooks/$CHRONOS_PUBLISHED_GRADEBOOK_ID" >/dev/null
  http_request 403 "$CHRONOS_TEACHER_TOKEN" PUT \
    "/admin/education/grades/gradebooks/$CHRONOS_PUBLISHED_GRADEBOOK_ID/items" \
    '{"items":[],"rowVersion":1}' >/dev/null

  echo "PASS: deployed non-production role acceptance HTTP matrix"
  echo "NOTE: retry/replay is verified by local tests only; operator credentials are intentionally not used."
}

case "$mode" in
  local) run_local ;;
  http) run_http ;;
  *)
    echo "usage: $0 [local|http]" >&2
    exit 2
    ;;
esac
