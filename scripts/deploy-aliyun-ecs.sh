#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SSH_KEY="${SSH_KEY:-$HOME/Downloads/codex.pem}"
TARGET_HOST="${TARGET_HOST:-root@8.166.121.138}"
PUBLIC_HOST="${PUBLIC_HOST:-8.166.121.138}"
PUBLIC_DEMO_HOST="${PUBLIC_DEMO_HOST:-zhilu.${PUBLIC_HOST}.nip.io}"
REMOTE_DIR="${REMOTE_DIR:-/opt/zhilu-delivery}"
COMPOSE_FILE="deploy/aliyun/docker-compose.ecs.yml"
SSH_ARGS=(-i "$SSH_KEY" -o IdentitiesOnly=yes -o BatchMode=yes -o ConnectTimeout=10)

for command in ssh rsync curl; do
  command -v "$command" >/dev/null || { echo "Missing required command: $command" >&2; exit 1; }
done

[[ -f "$SSH_KEY" ]] || { echo "SSH key not found: $SSH_KEY" >&2; exit 1; }
key_mode="$(stat -f '%Lp' "$SSH_KEY" 2>/dev/null || stat -c '%a' "$SSH_KEY")"
[[ "$key_mode" == 600 || "$key_mode" == 400 ]] || { echo "SSH key mode must be 600 or 400 (current: $key_mode)" >&2; exit 1; }
[[ -f "$ROOT_DIR/$COMPOSE_FILE" ]] || { echo "Missing $COMPOSE_FILE" >&2; exit 1; }
[[ -f "$ROOT_DIR/deploy/aliyun/rainier-nginx.conf" ]] || { echo "Missing Rainier ingress config" >&2; exit 1; }
[[ -x "$ROOT_DIR/scripts/smoke-test.sh" ]] || { echo "scripts/smoke-test.sh must be executable" >&2; exit 1; }
[[ "$PUBLIC_DEMO_HOST" =~ ^[A-Za-z0-9.-]+$ ]] || { echo "Invalid PUBLIC_DEMO_HOST" >&2; exit 1; }

check_rainier() {
  curl -fsS --max-time 10 "http://$PUBLIC_HOST/" >/dev/null
  curl -fsS --max-time 10 "http://$PUBLIC_HOST/api/health" >/dev/null
}

echo "Checking Rainier before deployment..."
check_rainier

ssh "${SSH_ARGS[@]}" "$TARGET_HOST" "mkdir -p '$REMOTE_DIR'"

echo "Syncing current workspace snapshot..."
rsync --archive --compress --delete \
  --exclude='.git/' \
  --exclude='.env' \
  --exclude='.DS_Store' \
  --exclude='.idea/' \
  --exclude='*.log' \
  --exclude='backend/target/' \
  --exclude='frontend/dist/' \
  --exclude='frontend/node_modules/' \
  --exclude='node_modules/' \
  -e "ssh -i '$SSH_KEY' -o IdentitiesOnly=yes -o BatchMode=yes -o ConnectTimeout=10" \
  "$ROOT_DIR/" "$TARGET_HOST:$REMOTE_DIR/"

echo "Building and starting Zhilu Delivery on ECS..."
ssh "${SSH_ARGS[@]}" "$TARGET_HOST" bash -s -- "$REMOTE_DIR" "$COMPOSE_FILE" "$PUBLIC_DEMO_HOST" <<'REMOTE_SCRIPT'
set -Eeuo pipefail

REMOTE_DIR="$1"
COMPOSE_FILE="$2"
PUBLIC_DEMO_HOST="$3"
cd "$REMOTE_DIR"

if [[ ! -f .env ]]; then
  command -v openssl >/dev/null || { echo "openssl is required on the ECS" >&2; exit 1; }
  umask 077
  {
    printf 'DB_PASSWORD=%s\n' "$(openssl rand -hex 24)"
    printf 'MYSQL_ROOT_PASSWORD=%s\n' "$(openssl rand -hex 24)"
    printf 'MINIO_ACCESS_KEY=zhilu%s\n' "$(openssl rand -hex 8)"
    printf 'MINIO_SECRET_KEY=%s\n' "$(openssl rand -hex 24)"
    printf 'AGENT_SHARED_SECRET=%s\n' "$(openssl rand -hex 32)"
    printf 'AI_BASE_URL=\nAI_MODEL=\nAI_API_KEY=\n'
  } > .env
fi
chmod 600 .env

compose=(docker compose --env-file .env -f "$COMPOSE_FILE")
"${compose[@]}" config --quiet
for service in backend frontend mock-agent; do
  "${compose[@]}" build "$service"
done
"${compose[@]}" up -d --remove-orphans

healthy=false
for _ in $(seq 1 90); do
  if curl -fsS http://127.0.0.1:53990/actuator/health | grep -q '"status":"UP"'; then
    healthy=true
    break
  fi
  sleep 3
done

if [[ "$healthy" != true ]]; then
  "${compose[@]}" ps >&2
  "${compose[@]}" logs --tail=120 backend frontend mysql redis minio mock-agent >&2
  exit 1
fi

BASE_URL=http://127.0.0.1:53990 ./scripts/smoke-test.sh
"${compose[@]}" ps

rainier_nginx=/opt/rainier/frontend/nginx.conf
rainier_backup=/opt/rainier/frontend/nginx.conf.pre-zhilu
rainier_candidate="$(mktemp)"
trap 'rm -f "$rainier_candidate"' EXIT
[[ -f "$rainier_nginx" ]] || { echo "Rainier Nginx config not found" >&2; exit 1; }
docker inspect rainier-frontend >/dev/null
[[ -f "$rainier_backup" ]] || cp -p "$rainier_nginx" "$rainier_backup"
sed "s/__PUBLIC_DEMO_HOST__/$PUBLIC_DEMO_HOST/g" deploy/aliyun/rainier-nginx.conf > "$rainier_candidate"
install -m 644 "$rainier_candidate" "$rainier_nginx"
docker cp "$rainier_nginx" rainier-frontend:/etc/nginx/conf.d/default.conf
if ! docker exec rainier-frontend nginx -t; then
  cp -p "$rainier_backup" "$rainier_nginx"
  docker cp "$rainier_nginx" rainier-frontend:/etc/nginx/conf.d/default.conf
  exit 1
fi
docker exec rainier-frontend nginx -s reload
REMOTE_SCRIPT

echo "Checking the public demo..."
BASE_URL="http://$PUBLIC_DEMO_HOST" "$ROOT_DIR/scripts/smoke-test.sh"

echo "Checking Rainier after deployment..."
check_rainier
echo "Deployment complete: http://$PUBLIC_DEMO_HOST"
