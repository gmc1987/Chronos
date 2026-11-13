#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ ! -f .env ]]; then
  echo "FAIL: 部署目录缺少 .env" >&2
  exit 2
fi
if [[ ! -f deploy/frontend/dist/index.html ]]; then
  echo "FAIL: 前端补丁缺少 deploy/frontend/dist/index.html" >&2
  exit 2
fi

docker compose --env-file .env config --quiet
docker compose --env-file .env build nginx
docker compose --env-file .env up -d --no-deps --force-recreate nginx

echo "等待前端容器就绪……"
for _ in $(seq 1 30); do
  if curl --fail --silent --max-time 5 http://127.0.0.1/admin/login/ >/dev/null; then
    echo "PASS: 前端补丁已部署，请在浏览器执行强制刷新。"
    docker compose --env-file .env ps nginx
    exit 0
  fi
  sleep 2
done

echo "FAIL: 前端容器未在 60 秒内就绪" >&2
docker compose --env-file .env logs --tail=100 nginx >&2
exit 1
