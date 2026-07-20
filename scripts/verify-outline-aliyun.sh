#!/usr/bin/env bash
set -Eeuo pipefail

cd /opt/outline-stack
compose=(docker compose --env-file .env -f docker-compose.ecs.yml)

for service in postgres redis dex outline caddy; do
  cid="$("${compose[@]}" ps -q "$service")"
  [[ -n "$cid" ]] || { echo "$service is not running" >&2; exit 1; }
  [[ "$(docker inspect -f '{{.State.Health.Status}}' "$cid")" == healthy ]] || {
    echo "$service is not healthy" >&2
    exit 1
  }
done

curl -fsS https://outline.8.166.121.138.sslip.io/dex/.well-known/openid-configuration \
  | grep -Eq '"issuer"[[:space:]]*:[[:space:]]*"https://outline.8.166.121.138.sslip.io/dex"'
curl -fsS -o /dev/null https://outline.8.166.121.138.sslip.io/
curl -fsS http://127.0.0.1:53990/actuator/health | grep -q '"status":"UP"'
curl -fsS http://127.0.0.1/api/health >/dev/null
[[ "$(docker ps --filter name=rainier --format '{{.Names}}' | wc -l | tr -d ' ')" -eq 3 ]]
[[ "$(docker ps --filter name=zhilu-delivery --format '{{.Names}}' | wc -l | tr -d ' ')" -eq 6 ]]
! dmesg --since '15 minutes ago' 2>/dev/null | grep -qi 'out of memory'
echo "Outline infrastructure verification passed"
