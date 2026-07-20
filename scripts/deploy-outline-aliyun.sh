#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SSH_KEY="${SSH_KEY:-/Users/dogekul/Downloads/codex.pem}"
TARGET="${TARGET:-root@8.166.121.138}"
REMOTE_DIR=/opt/outline-stack
BACKUP_DIR=/opt/outline-backups
SSH=(ssh -i "$SSH_KEY" -o IdentitiesOnly=yes -o BatchMode=yes -o ConnectTimeout=10)

[[ -f "$SSH_KEY" ]] || { echo "SSH key not found: $SSH_KEY" >&2; exit 1; }
[[ "$(stat -f '%Lp' "$SSH_KEY" 2>/dev/null || stat -c '%a' "$SSH_KEY")" =~ ^(400|600)$ ]] || {
  echo "SSH key must use mode 400 or 600" >&2
  exit 1
}
for command in ssh rsync scp curl; do
  command -v "$command" >/dev/null || { echo "Missing required command: $command" >&2; exit 1; }
done

"${SSH[@]}" "$TARGET" bash -s <<'PREFLIGHT'
set -Eeuo pipefail
test -d /opt/zhilu-delivery
docker network inspect zhilu-delivery_default >/dev/null
if ss -lnt | grep -q ':443 '; then
  docker ps --filter name=outline-stack-caddy --format '{{.Names}}' | grep -q '^outline-stack-caddy-1$'
fi
PREFLIGHT

"${SSH[@]}" "$TARGET" "install -d -m 700 '$REMOTE_DIR' '$REMOTE_DIR/dex' '$BACKUP_DIR/preflight'"
rsync -a -e "ssh -i '$SSH_KEY' -o IdentitiesOnly=yes -o BatchMode=yes" \
  "$ROOT_DIR/deploy/outline/" "$TARGET:$REMOTE_DIR/"
rsync -a -e "ssh -i '$SSH_KEY' -o IdentitiesOnly=yes -o BatchMode=yes" \
  "$ROOT_DIR/scripts/backup-outline-aliyun.sh" "$ROOT_DIR/scripts/verify-outline-aliyun.sh" \
  "$TARGET:$REMOTE_DIR/"

"${SSH[@]}" "$TARGET" bash -s <<'REMOTE'
set -Eeuo pipefail

stack_dir=/opt/outline-stack
backup_dir=/opt/outline-backups
stamp="$(date +%Y%m%d-%H%M%S)"
docker ps --format '{{.Names}}\t{{.Status}}\t{{.Ports}}' > "$backup_dir/preflight/docker-$stamp.txt"
free -h > "$backup_dir/preflight/memory-$stamp.txt"
df -h > "$backup_dir/preflight/disk-$stamp.txt"

cd /opt/zhilu-delivery
set -a
. ./.env
set +a
docker compose --env-file .env -f deploy/aliyun/docker-compose.ecs.yml exec -T mysql \
  mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers delivery < /dev/null \
  | gzip -9 > "$backup_dir/preflight/zhilu-$stamp.sql.gz"
gzip -t "$backup_dir/preflight/zhilu-$stamp.sql.gz"

if (( $(free -b | awk '/^Swap:/ {print $2}') < 2147483648 )); then
  if [[ ! -f /swapfile ]]; then
    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile >/dev/null
  fi
  swapon --show=NAME --noheadings | grep -qx /swapfile || swapon /swapfile
  grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

ensure_image() {
  local image="$1"
  docker image inspect "$image" >/dev/null 2>&1 && return 0
  for attempt in 1 2 3; do
    timeout 300 docker pull "$image" && return 0
    echo "Image pull failed ($attempt/3): $image" >&2
    sleep 5
  done
  return 1
}

cd "$stack_dir"
ensure_image caddy:2.11.4-alpine
if [[ ! -f .env ]]; then
  umask 077
  admin_password="$(openssl rand -hex 16)"
  docker run --rm caddy:2.11.4-alpine caddy hash-password --plaintext "$admin_password" > dex/password.hash
  {
    printf 'POSTGRES_PASSWORD=%s\n' "$(openssl rand -hex 24)"
    printf 'REDIS_PASSWORD=%s\n' "$(openssl rand -hex 24)"
    printf 'OUTLINE_SECRET_KEY=%s\n' "$(openssl rand -hex 32)"
    printf 'OUTLINE_UTILS_SECRET=%s\n' "$(openssl rand -hex 32)"
    printf 'DEX_OIDC_CLIENT_SECRET=%s\n' "$(openssl rand -hex 32)"
    printf 'DEX_ADMIN_USER_ID=%s\n' "$(cat /proc/sys/kernel/random/uuid)"
  } > .env
  {
    printf 'URL=https://outline.8.166.121.138.sslip.io\n'
    printf 'EMAIL=outline-admin@zhilu.local\n'
    printf 'PASSWORD=%s\n' "$admin_password"
  } > admin-credentials.txt
fi
[[ -s dex/password.hash ]] || { echo 'Missing Dex password hash' >&2; exit 1; }
chmod 600 .env dex/password.hash
[[ ! -f admin-credentials.txt ]] || chmod 600 admin-credentials.txt

set -a
. ./.env
set +a
admin_hash="$(cat dex/password.hash)"
sed \
  -e "s|__DEX_OIDC_CLIENT_SECRET__|$DEX_OIDC_CLIENT_SECRET|g" \
  -e "s|__DEX_ADMIN_PASSWORD_HASH__|$admin_hash|g" \
  -e "s|__DEX_ADMIN_USER_ID__|$DEX_ADMIN_USER_ID|g" \
  dex-config.yaml.tpl > dex/config.yaml
chmod 644 dex/config.yaml
chmod 700 backup-outline-aliyun.sh verify-outline-aliyun.sh

compose=(docker compose --env-file .env -f docker-compose.ecs.yml)
"${compose[@]}" config --quiet
for image in \
  postgres:18.4-alpine3.24 \
  redis:7.4.9-alpine3.21 \
  ghcr.io/dexidp/dex:v2.45.1-alpine \
  docker.getoutline.com/outlinewiki/outline:1.7.1; do
  ensure_image "$image"
done
docker volume create outline-stack_dex-data >/dev/null
docker run --rm --user root --entrypoint sh \
  -v outline-stack_dex-data:/var/dex \
  ghcr.io/dexidp/dex:v2.45.1-alpine \
  -c 'chown -R 1001:1001 /var/dex'

"${compose[@]}" stop outline >/dev/null 2>&1 || true
"${compose[@]}" up -d postgres redis dex caddy

foundation_ready=false
for _ in $(seq 1 60); do
  foundation_ready=true
  for service in postgres redis dex caddy; do
    cid="$("${compose[@]}" ps -q "$service")"
    [[ -n "$cid" ]] || { foundation_ready=false; break; }
    state="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid")"
    [[ "$state" == healthy ]] || { foundation_ready=false; break; }
  done
  [[ "$foundation_ready" == true ]] && \
    curl -fsS --max-time 5 https://outline.8.166.121.138.sslip.io/dex/.well-known/openid-configuration >/dev/null && break
  foundation_ready=false
  sleep 10
done
if [[ "$foundation_ready" != true ]]; then
  "${compose[@]}" ps >&2
  "${compose[@]}" logs --tail=160 postgres redis dex caddy >&2
  exit 1
fi

"${compose[@]}" exec -T redis sh -c 'redis-cli -a "$REDIS_PASSWORD" del migrations >/dev/null' < /dev/null
"${compose[@]}" run --rm --no-deps outline node_modules/.bin/sequelize db:migrate --env production-ssl-disabled < /dev/null
"${compose[@]}" up -d outline
"${compose[@]}" up -d --force-recreate caddy

ready=false
for _ in $(seq 1 60); do
  ready=true
  for service in postgres redis dex outline caddy; do
    cid="$("${compose[@]}" ps -q "$service")"
    [[ -n "$cid" ]] || { ready=false; break; }
    state="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid")"
    [[ "$state" == healthy ]] || { ready=false; break; }
  done
  [[ "$ready" == true ]] && break
  sleep 10
done
if [[ "$ready" != true ]]; then
  "${compose[@]}" ps >&2
  "${compose[@]}" logs --tail=160 postgres redis dex outline caddy >&2
  exit 1
fi

install -m 644 /dev/stdin /etc/cron.d/outline-backup <<'CRON'
CRON_TZ=Asia/Shanghai
30 3 * * * root /opt/outline-stack/backup-outline-aliyun.sh >> /var/log/outline-backup.log 2>&1
CRON
"${compose[@]}" ps
REMOTE

if "${SSH[@]}" "$TARGET" "test -f '$REMOTE_DIR/admin-credentials.txt'"; then
  scp -i "$SSH_KEY" -o IdentitiesOnly=yes -o BatchMode=yes \
    "$TARGET:$REMOTE_DIR/admin-credentials.txt" /Users/dogekul/Downloads/outline-admin-credentials.txt
  chmod 600 /Users/dogekul/Downloads/outline-admin-credentials.txt
fi
[[ -s /Users/dogekul/Downloads/outline-admin-credentials.txt ]] || {
  echo "Outline credentials are not available locally" >&2
  exit 1
}
echo "Outline stack started; credentials saved to /Users/dogekul/Downloads/outline-admin-credentials.txt"
