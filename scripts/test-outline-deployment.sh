#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE="$ROOT_DIR/deploy/outline/docker-compose.ecs.yml"
CADDY="$ROOT_DIR/deploy/outline/Caddyfile"
DEX="$ROOT_DIR/deploy/outline/dex-config.yaml.tpl"

for file in "$COMPOSE" "$CADDY" "$DEX"; do
  [[ -f "$file" ]] || { echo "Missing $file" >&2; exit 1; }
done

grep -q 'outline:1.7.1' "$COMPOSE"
grep -q 'postgres:18.4-alpine3.24' "$COMPOSE"
grep -q 'redis:7.4.9-alpine3.21' "$COMPOSE"
grep -q 'dex:v2.45.1-alpine' "$COMPOSE"
grep -q 'caddy:2.11.4-alpine' "$COMPOSE"
! grep -q ':latest' "$COMPOSE"
! grep -Eq '(^|[[:space:]-])80:80([/"[:space:]]|$)' "$COMPOSE"
grep -q '443:443' "$COMPOSE"
grep -q 'mem_limit: 768m' "$COMPOSE"
grep -q 'external: true' "$COMPOSE"
grep -q 'outline.8.166.121.138.sslip.io' "$CADDY"
grep -q '__DEX_OIDC_CLIENT_SECRET__' "$DEX"
grep -q '__DEX_ADMIN_PASSWORD_HASH__' "$DEX"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
cp "$COMPOSE" "$CADDY" "$tmp/"
mkdir -p "$tmp/dex"
sed \
  -e 's/__DEX_OIDC_CLIENT_SECRET__/0123456789abcdef0123456789abcdef/g' \
  -e 's|__DEX_ADMIN_PASSWORD_HASH__|$2a$12$123456789012345678901u12345678901234567890123456789012|g' \
  -e 's/__DEX_ADMIN_USER_ID__/11111111-1111-4111-8111-111111111111/g' \
  "$DEX" > "$tmp/dex/config.yaml"
cat > "$tmp/.env" <<'ENV'
POSTGRES_PASSWORD=11111111111111111111111111111111
REDIS_PASSWORD=22222222222222222222222222222222
OUTLINE_SECRET_KEY=3333333333333333333333333333333333333333333333333333333333333333
OUTLINE_UTILS_SECRET=4444444444444444444444444444444444444444444444444444444444444444
DEX_OIDC_CLIENT_SECRET=0123456789abcdef0123456789abcdef
ENV
(cd "$tmp" && docker compose --env-file .env -f docker-compose.ecs.yml config --quiet)

for script in deploy-outline-aliyun.sh backup-outline-aliyun.sh verify-outline-aliyun.sh; do
  [[ -x "$ROOT_DIR/scripts/$script" ]] || { echo "Missing executable scripts/$script" >&2; exit 1; }
  bash -n "$ROOT_DIR/scripts/$script"
done
grep -q 'swapon' "$ROOT_DIR/scripts/deploy-outline-aliyun.sh"
grep -q 'mysqldump' "$ROOT_DIR/scripts/deploy-outline-aliyun.sh"
grep -q '< /dev/null' "$ROOT_DIR/scripts/deploy-outline-aliyun.sh"
grep -q 'pg_dump' "$ROOT_DIR/scripts/backup-outline-aliyun.sh"
grep -q 'sha256sum' "$ROOT_DIR/scripts/backup-outline-aliyun.sh"
! grep -R -E 'Admin@123|ol_api_[A-Za-z0-9]+' \
  "$ROOT_DIR/deploy/outline" \
  "$ROOT_DIR/scripts/deploy-outline-aliyun.sh" \
  "$ROOT_DIR/scripts/backup-outline-aliyun.sh" \
  "$ROOT_DIR/scripts/verify-outline-aliyun.sh"

echo "Outline deployment static tests passed"
