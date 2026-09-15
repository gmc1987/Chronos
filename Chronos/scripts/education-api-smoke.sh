#!/usr/bin/env bash
set -euo pipefail

: "${CHRONOS_BASE_URL:?set CHRONOS_BASE_URL, e.g. http://localhost:8080}"
: "${CHRONOS_TEACHER_TOKEN:?set CHRONOS_TEACHER_TOKEN}"
: "${CHRONOS_STUDENT_TOKEN:?set CHRONOS_STUDENT_TOKEN}"
: "${CHRONOS_OFFERING_ID:?set CHRONOS_OFFERING_ID}"
: "${CHRONOS_OUT_OF_SCOPE_OFFERING_ID:?set CHRONOS_OUT_OF_SCOPE_OFFERING_ID}"
: "${CHRONOS_HOMEWORK_ID:?set CHRONOS_HOMEWORK_ID}"

api="${CHRONOS_BASE_URL%/}"
teacher_auth=(-H "Authorization: Bearer ${CHRONOS_TEACHER_TOKEN}")
student_auth=(-H "Authorization: Bearer ${CHRONOS_STUDENT_TOKEN}")

request() {
  local expected="$1"
  shift
  local response status
  set +e
  response="$(curl --fail-with-body --silent --show-error -w $'\n%{http_code}' "$@")"
  local curl_status=$?
  set -e
  status="${response##*$'\n'}"
  if [[ "$status" != "$expected" || ( "$curl_status" -ne 0 && "$expected" == 2* ) ]]; then
    printf 'expected HTTP %s, got %s for %s\n%s\n' "$expected" "$status" "$*" "$response" >&2
    return 1
  fi
  printf 'OK %s %s\n' "$status" "$1"
}

request_page() {
  local url="$1"
  local response status body
  response="$(curl --fail-with-body --silent --show-error -w $'\n%{http_code}' "${@:2}" "$url")"
  status="${response##*$'\n'}"
  body="${response%$'\n'*}"
  [[ "$status" == "200" ]] || { printf 'expected HTTP 200, got %s for %s\n%s\n' "$status" "$url" "$body" >&2; return 1; }
  python3 -c 'import json,sys; d=json.load(sys.stdin).get("data"); assert isinstance(d, dict) and "content" in d and "totalElements" in d, "page response missing content/totalElements"' <<<"$body"
  printf 'OK 200 paged %s\n' "$url"
}

request_page "$api/education/teaching-center/api/plans?offeringId=${CHRONOS_OFFERING_ID}&page=0&size=1" "${teacher_auth[@]}"
request_page "$api/education/teaching-center/api/lesson-plans?offeringId=${CHRONOS_OFFERING_ID}&page=0&size=1" "${teacher_auth[@]}"
request_page "$api/education/teaching-center/api/preparations?offeringId=${CHRONOS_OFFERING_ID}&page=0&size=1" "${teacher_auth[@]}"
request_page "$api/education/teaching-center/api/coursewares?offeringId=${CHRONOS_OFFERING_ID}&page=0&size=1" "${teacher_auth[@]}"
request_page "$api/education/teaching-center/api/materials?offeringId=${CHRONOS_OFFERING_ID}&page=0&size=1" "${teacher_auth[@]}"
request_page "$api/education/teaching-center/api/question-banks?offeringId=${CHRONOS_OFFERING_ID}&page=0&size=1" "${teacher_auth[@]}"
request_page "$api/education/teaching-center/api/knowledge-points?page=0&size=1" "${teacher_auth[@]}"
request_page "$api/education/teaching-center/api/mistakes?page=0&size=1" "${teacher_auth[@]}"
request_page "$api/education/teaching-center/api/research?page=0&size=1" "${teacher_auth[@]}"

request 200 "${student_auth[@]}" "$api/education/teaching-center/api/homeworks?page=0&size=20"
request 200 "${student_auth[@]}" "$api/education/teaching-center/api/homeworks/${CHRONOS_HOMEWORK_ID}/my-submission"
request 403 "${student_auth[@]}" "$api/education/teaching-center/api/homeworks?offeringId=${CHRONOS_OUT_OF_SCOPE_OFFERING_ID}&page=0&size=20"
request 403 "${teacher_auth[@]}" "$api/education/teaching-center/api/homeworks/${CHRONOS_HOMEWORK_ID}/my-submission"

# Optional detail/version checks use IDs created by the environment fixture.
for spec in \
  "CHRONOS_PLAN_ID|/education/teaching/plans/%s|/education/teaching/plans/%s/versions" \
  "CHRONOS_LESSON_PLAN_ID|/education/teaching/lessons/%s|/education/teaching/lessons/%s/versions" \
  "CHRONOS_PREPARATION_ID|/education/teaching/preparations/%s|" \
  "CHRONOS_COURSEWARE_ID|/education/teaching-center/coursewares/%s|/education/teaching-center/coursewares/%s/versions" \
  "CHRONOS_MATERIAL_ID|/education/teaching-center/materials/%s|/education/teaching-center/materials/%s/versions" \
  "CHRONOS_QUESTION_ID|/education/teaching-center/questions/%s|/education/teaching-center/questions/%s/versions" \
  "CHRONOS_RESEARCH_ID|/education/teaching-center/api/research/%s|" \
  "CHRONOS_MISTAKE_ID|/education/teaching-center/api/mistakes/%s|"; do
  IFS='|' read -r variable detail versions <<<"$spec"
  value="${!variable:-}"
  [[ -n "$value" ]] || continue
  printf -v detail_url "$api$detail" "$value"
  request 200 "${teacher_auth[@]}" "$detail_url"
  if [[ -n "$versions" ]]; then
    printf -v versions_url "$api$versions" "$value"
    request 200 "${teacher_auth[@]}" "$versions_url"
  fi
done

# Optional write-path acceptance. Set RUN_TEACHING_RESOURCE_LIFECYCLE=1 and provide
# a file already uploaded through /files; the default smoke remains read-only.
if [[ "${RUN_TEACHING_RESOURCE_LIFECYCLE:-0}" == "1" ]]; then
  : "${CHRONOS_RESOURCE_FILE_ID:?set CHRONOS_RESOURCE_FILE_ID when lifecycle is enabled}"
  resource_title="${CHRONOS_RESOURCE_TITLE:-smoke-teaching-material-$(date +%s)}"
  resource_body="$(printf '{"offeringId":"%s","title":"%s","materialType":"TEXTBOOK","shareScope":"PRIVATE","description":"education api smoke"}' \
    "$CHRONOS_OFFERING_ID" "$resource_title")"

  create_response="$(curl --fail-with-body --silent --show-error \
    -H "Content-Type: application/json" "${teacher_auth[@]}" \
    -X POST "$api/education/teaching-center/materials" -d "$resource_body")"
  resource_id="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["id"])' <<<"$create_response")"
  [[ -n "$resource_id" ]] || { echo "resource creation returned no id" >&2; exit 1; }
  printf 'OK 200 created teaching material %s\n' "$resource_id"

  request 200 -H "Content-Type: application/json" "${teacher_auth[@]}" \
    -X PUT "$api/education/teaching-center/materials/$resource_id" \
    -d "{\"offeringId\":\"$CHRONOS_OFFERING_ID\",\"title\":\"$resource_title-edited\",\"materialType\":\"TEXTBOOK\",\"shareScope\":\"PRIVATE\",\"description\":\"edited by smoke\"}"

  version_response="$(curl --fail-with-body --silent --show-error \
    -H "Content-Type: application/json" "${teacher_auth[@]}" \
    -X POST "$api/education/teaching-center/materials/$resource_id/versions" \
    -d "{\"fileId\":\"$CHRONOS_RESOURCE_FILE_ID\",\"metadataJson\":\"{}\"}")"
  version_id="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["id"])' <<<"$version_response")"
  [[ -n "$version_id" ]] || { echo "version creation returned no id" >&2; exit 1; }
  printf 'OK 200 created material version %s\n' "$version_id"

  request 200 "${teacher_auth[@]}" -X POST "$api/education/teaching-center/materials/versions/$version_id/submit"
  request 200 "${teacher_auth[@]}" -X POST "$api/education/teaching-center/materials/versions/$version_id/submit-review"
  request 200 "${teacher_auth[@]}" -X POST "$api/education/teaching-center/materials/versions/$version_id/approve"
  request 200 "${teacher_auth[@]}" -X POST "$api/education/teaching-center/materials/versions/$version_id/publish"
  request 200 "${teacher_auth[@]}" -X POST "$api/education/teaching-center/materials/versions/$version_id/archive"
fi
