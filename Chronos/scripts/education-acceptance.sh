#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

# Unit/MockMvc/contract checks are always safe. The application context test is
# enabled only when CHRONOS_DB_URL names an isolated test/verify database.
./mvnw -pl education-class-scheduling,integration-center -am \
  -Dtest='*ControllerHttpTest,*ServiceTest,*BoundaryTest,*ConsumerTest,*OutboxServiceTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

if [[ "${CHRONOS_DB_URL:-}" =~ jdbc:postgresql:.*/[^/?]*(test|verify)[^/?]*(\?.*)? ]]; then
  ./mvnw -pl education-app -am -Dtest=ChronosEducationApplicationTests test
else
  echo "BLOCKED: set CHRONOS_DB_URL to an isolated *test* or *verify* PostgreSQL database for context/E2E startup."
fi
