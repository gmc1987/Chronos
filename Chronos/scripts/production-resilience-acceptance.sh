#!/usr/bin/env bash
set -euo pipefail

# Executes a reversible dependency-failure drill against a non-production test
# deployment. Caller-provided commands isolate provider-specific operations.

for required in CHRONOS_BASE_URL CHRONOS_FAULT_START_COMMAND CHRONOS_FAULT_RECOVER_COMMAND CHRONOS_RESILIENCE_ACK; do
  if [[ -z "${!required:-}" ]]; then echo "FAIL: $required is required" >&2; exit 2; fi
done
if [[ "$CHRONOS_RESILIENCE_ACK" != "NON_PRODUCTION_DRILL" ]]; then
  echo "FAIL: CHRONOS_RESILIENCE_ACK must be NON_PRODUCTION_DRILL" >&2; exit 2
fi
command -v curl >/dev/null || exit 127
base="${CHRONOS_BASE_URL%/}"
timeout="${CHRONOS_RECOVERY_TIMEOUT_SECONDS:-90}"
recover() { bash -lc "$CHRONOS_FAULT_RECOVER_COMMAND" >/dev/null 2>&1 || true; }
trap recover EXIT INT TERM

curl --fail --silent "$base/actuator/health/readiness" >/dev/null
curl --fail --silent "$base/actuator/health/liveness" >/dev/null
bash -lc "$CHRONOS_FAULT_START_COMMAND"

deadline=$((SECONDS + 30)); readiness_failed=0
while (( SECONDS < deadline )); do
  if ! curl --fail --silent --max-time 3 "$base/actuator/health/readiness" >/dev/null; then readiness_failed=1; break; fi
  sleep 1
done
if (( readiness_failed == 0 )); then echo "FAIL: readiness stayed healthy during dependency fault" >&2; exit 1; fi
curl --fail --silent --max-time 3 "$base/actuator/health/liveness" >/dev/null

recover
trap - EXIT INT TERM
deadline=$((SECONDS + timeout))
while (( SECONDS < deadline )); do
  if curl --fail --silent --max-time 3 "$base/actuator/health/readiness" >/dev/null; then
    echo "PASS: readiness failed, liveness stayed healthy, dependency recovery completed"
    exit 0
  fi
  sleep 2
done
echo "FAIL: readiness did not recover within ${timeout}s" >&2
exit 1
