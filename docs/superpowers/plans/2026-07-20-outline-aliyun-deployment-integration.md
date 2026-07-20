# Outline Alibaba Cloud Deployment and Zhilu Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deploy a pinned, TLS-protected Outline stack on Alibaba ECS `8.166.121.138`, bootstrap Dex authentication, and connect the deployed Zhilu organization to a real Outline collection.

**Architecture:** A standalone Docker Compose stack runs Outline, PostgreSQL, Redis, Dex, and Caddy under `/opt/outline-stack`. Caddy exposes only public port 443, while the Zhilu backend reaches Outline through the existing `zhilu-delivery_default` Docker network and stores the scoped API token in its existing encrypted organization configuration.

**Tech Stack:** Docker Compose, Outline 1.7.1, PostgreSQL 18.4, Redis 7.4.9, Dex 2.45.1, Caddy 2.11.4, Bash, curl, rsync, OpenSSL.

## Global Constraints

- Preserve the existing dirty files `backend/src/main/java/com/zhilu/delivery/automation/OpenAiCompatibleClient.java`, `backend/src/test/java/com/zhilu/delivery/automation/OpenAiCompatibleClientTest.java`, `合规审查系统需求调研报告.md`, and `需求调研报告模板.md`.
- Perform repository edits in an isolated `codex/outline-aliyun-deploy` worktree, then integrate only the committed deployment files into `main`.
- Use `docker.getoutline.com/outlinewiki/outline:1.7.1`, `postgres:18.4-alpine3.24`, `redis:7.4.9-alpine3.21`, `ghcr.io/dexidp/dex:v2.45.1-alpine`, and `caddy:2.11.4-alpine`; never use `latest`.
- Keep Rainier on port 80 and expose the new stack only on `443/tcp` and `443/udp`.
- Do not modify or restart Rainier or the six existing Zhilu containers.
- Do not commit `.env`, passwords, bcrypt hashes, API tokens, database dumps, or encrypted disaster artifacts.
- Use `WEB_CONCURRENCY=1` and memory limits of 768 MiB for Outline, 384 MiB for PostgreSQL, and 128 MiB each for Redis, Dex, and Caddy.
- Never run `docker compose down --volumes`, delete `/opt/outline-stack`, or delete `/opt/outline-backups` during rollback.
- Use the existing SSH key `/Users/dogekul/Downloads/codex.pem` and target `root@8.166.121.138`.

---

## File Map

- Create `deploy/outline/docker-compose.ecs.yml`: the isolated production stack and network contract.
- Create `deploy/outline/Caddyfile`: TLS and path routing for Outline and Dex.
- Create `deploy/outline/dex-config.yaml.tpl`: secret-free Dex OIDC and local-user template.
- Create `scripts/test-outline-deployment.sh`: static tests for versions, ports, secrets, shell syntax, and Compose rendering.
- Create `scripts/deploy-outline-aliyun.sh`: idempotent preflight, backup, Swap, secret generation, config rendering, pull, and startup.
- Create `scripts/backup-outline-aliyun.sh`: root-only daily PostgreSQL, volume, configuration, and checksum backup.
- Create `scripts/verify-outline-aliyun.sh`: infrastructure and regression checks that do not print secrets.
- Modify `docs/operations/deployment.md`: operator-facing URLs, commands, backup, restore, and upgrade procedure.

### Task 1: Create and validate the secret-free Outline stack bundle

**Files:**
- Create: `scripts/test-outline-deployment.sh`
- Create: `deploy/outline/docker-compose.ecs.yml`
- Create: `deploy/outline/Caddyfile`
- Create: `deploy/outline/dex-config.yaml.tpl`

**Interfaces:**
- Consumes: environment variables in server-only `/opt/outline-stack/.env` and rendered `/opt/outline-stack/dex/config.yaml`.
- Produces: Compose services `outline`, `postgres`, `redis`, `dex`, and `caddy`; Docker alias `outline` on external network `zhilu-delivery_default`.

- [ ] **Step 1: Write the failing static deployment test**

Create `scripts/test-outline-deployment.sh` with checks for exact image tags, absence of `latest`, absence of port 80, required memory limits, secret placeholders, and a renderable Compose model:

```bash
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
echo "Outline deployment static tests passed"
```

- [ ] **Step 2: Run the test and verify it fails before the bundle exists**

Run:

```bash
chmod +x scripts/test-outline-deployment.sh
./scripts/test-outline-deployment.sh
```

Expected: non-zero exit with `Missing .../deploy/outline/docker-compose.ecs.yml`.

- [ ] **Step 3: Create the production Compose file**

Create `deploy/outline/docker-compose.ecs.yml` with:

```yaml
name: outline-stack

services:
  postgres:
    image: postgres:18.4-alpine3.24
    restart: unless-stopped
    mem_limit: 384m
    environment:
      POSTGRES_USER: outline
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}
      POSTGRES_DB: outline
      PGDATA: /var/lib/postgresql/data/pgdata
      TZ: Asia/Shanghai
    volumes:
      - postgres-data:/var/lib/postgresql/data
    networks: [private]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U outline -d outline"]
      interval: 10s
      timeout: 5s
      retries: 30

  redis:
    image: redis:7.4.9-alpine3.21
    restart: unless-stopped
    mem_limit: 128m
    environment:
      REDIS_PASSWORD: ${REDIS_PASSWORD:?REDIS_PASSWORD is required}
      TZ: Asia/Shanghai
    command: ["sh", "-c", "exec redis-server --appendonly yes --requirepass \"$$REDIS_PASSWORD\""]
    volumes:
      - redis-data:/data
    networks: [private]
    healthcheck:
      test: ["CMD-SHELL", "redis-cli -a \"$$REDIS_PASSWORD\" ping | grep -q PONG"]
      interval: 10s
      timeout: 5s
      retries: 30

  dex:
    image: ghcr.io/dexidp/dex:v2.45.1-alpine
    restart: unless-stopped
    mem_limit: 128m
    command: ["dex", "serve", "/etc/dex/config.yaml"]
    volumes:
      - ./dex/config.yaml:/etc/dex/config.yaml:ro
      - dex-data:/var/dex
    networks: [private]
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://127.0.0.1:5558/healthz/live"]
      interval: 10s
      timeout: 5s
      retries: 30

  outline:
    image: docker.getoutline.com/outlinewiki/outline:1.7.1
    restart: unless-stopped
    mem_limit: 768m
    environment:
      NODE_ENV: production
      URL: https://outline.8.166.121.138.sslip.io
      PORT: 3000
      SECRET_KEY: ${OUTLINE_SECRET_KEY:?OUTLINE_SECRET_KEY is required}
      UTILS_SECRET: ${OUTLINE_UTILS_SECRET:?OUTLINE_UTILS_SECRET is required}
      DATABASE_URL: postgres://outline:${POSTGRES_PASSWORD}@postgres:5432/outline
      PGSSLMODE: disable
      REDIS_URL: redis://:${REDIS_PASSWORD}@redis:6379
      FORCE_HTTPS: "true"
      WEB_CONCURRENCY: "1"
      FILE_STORAGE: local
      FILE_STORAGE_LOCAL_ROOT_DIR: /var/lib/outline/data
      FILE_STORAGE_UPLOAD_MAX_SIZE: "26214400"
      OIDC_ISSUER_URL: https://outline.8.166.121.138.sslip.io/dex
      OIDC_CLIENT_ID: outline
      OIDC_CLIENT_SECRET: ${DEX_OIDC_CLIENT_SECRET:?DEX_OIDC_CLIENT_SECRET is required}
      OIDC_DISPLAY_NAME: 智鹿账号
      OIDC_SCOPES: openid profile email
      OIDC_USERNAME_CLAIM: preferred_username
      OIDC_DISABLE_REDIRECT: "true"
      LOG_LEVEL: info
      TZ: Asia/Shanghai
    volumes:
      - outline-data:/var/lib/outline/data
    depends_on:
      postgres: { condition: service_healthy }
      redis: { condition: service_healthy }
      dex: { condition: service_healthy }
    networks:
      private:
      zhilu:
        aliases: [outline]
    healthcheck:
      test: ["CMD", "node", "-e", "const r=require('http').get('http://127.0.0.1:3000/',x=>process.exit(x.statusCode<500?0:1));r.on('error',()=>process.exit(1))"]
      interval: 10s
      timeout: 5s
      retries: 60
      start_period: 30s

  caddy:
    image: caddy:2.11.4-alpine
    restart: unless-stopped
    mem_limit: 128m
    ports:
      - "443:443"
      - "443:443/udp"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy-data:/data
      - caddy-config:/config
    depends_on:
      dex: { condition: service_healthy }
      outline: { condition: service_healthy }
    networks: [private]
    healthcheck:
      test: ["CMD", "wget", "-q", "-O", "/dev/null", "http://127.0.0.1:2019/config/"]
      interval: 10s
      timeout: 5s
      retries: 30

networks:
  private:
  zhilu:
    external: true
    name: zhilu-delivery_default

volumes:
  postgres-data:
  redis-data:
  dex-data:
  outline-data:
  caddy-data:
  caddy-config:
```

- [ ] **Step 4: Create the Caddy and Dex templates**

Create `deploy/outline/Caddyfile`:

```caddyfile
{
  auto_https disable_redirects
}

outline.8.166.121.138.sslip.io {
  encode zstd gzip

  @dex path /dex /dex/*
  handle @dex {
    reverse_proxy dex:5556
  }

  handle {
    reverse_proxy outline:3000
  }
}
```

Create `deploy/outline/dex-config.yaml.tpl`:

```yaml
issuer: https://outline.8.166.121.138.sslip.io/dex
storage:
  type: sqlite3
  config:
    file: /var/dex/dex.db
web:
  http: 0.0.0.0:5556
telemetry:
  http: 0.0.0.0:5558
oauth2:
  skipApprovalScreen: true
enablePasswordDB: true
staticClients:
  - id: outline
    name: Outline
    secret: __DEX_OIDC_CLIENT_SECRET__
    redirectURIs:
      - https://outline.8.166.121.138.sslip.io/auth/oidc.callback
staticPasswords:
  - email: outline-admin@zhilu.local
    hash: "__DEX_ADMIN_PASSWORD_HASH__"
    username: outline-admin
    userID: __DEX_ADMIN_USER_ID__
frontend:
  issuer: 智鹿 Outline
```

- [ ] **Step 5: Run the static test and verify it passes**

Run: `./scripts/test-outline-deployment.sh`

Expected: `Outline deployment static tests passed`.

- [ ] **Step 6: Commit the stack bundle**

```bash
git add deploy/outline scripts/test-outline-deployment.sh
git commit -m "ops: add pinned Outline deployment stack"
```

Expected: one commit containing only the four new files.

### Task 2: Add idempotent deployment, backup, and verification automation

**Files:**
- Create: `scripts/deploy-outline-aliyun.sh`
- Create: `scripts/backup-outline-aliyun.sh`
- Create: `scripts/verify-outline-aliyun.sh`
- Modify: `scripts/test-outline-deployment.sh`

**Interfaces:**
- Consumes: Task 1 bundle, `/Users/dogekul/Downloads/codex.pem`, `/opt/zhilu-delivery/.env`, Docker network `zhilu-delivery_default`.
- Produces: `/opt/outline-stack`, `/opt/outline-backups`, `/etc/cron.d/outline-backup`, and local root-only `~/Downloads/outline-admin-credentials.txt`.

- [ ] **Step 1: Extend the static test before adding the scripts**

Append before the final success message in `scripts/test-outline-deployment.sh`:

```bash
for script in deploy-outline-aliyun.sh backup-outline-aliyun.sh verify-outline-aliyun.sh; do
  [[ -x "$ROOT_DIR/scripts/$script" ]] || { echo "Missing executable scripts/$script" >&2; exit 1; }
  bash -n "$ROOT_DIR/scripts/$script"
done
grep -q 'swapon' "$ROOT_DIR/scripts/deploy-outline-aliyun.sh"
grep -q 'mysqldump' "$ROOT_DIR/scripts/deploy-outline-aliyun.sh"
grep -q 'pg_dump' "$ROOT_DIR/scripts/backup-outline-aliyun.sh"
grep -q 'sha256sum' "$ROOT_DIR/scripts/backup-outline-aliyun.sh"
! grep -R -E 'Admin@123|ol_api_[A-Za-z0-9]+' "$ROOT_DIR/deploy/outline" "$ROOT_DIR/scripts/"*outline*.sh
```

- [ ] **Step 2: Run the test and verify it fails for the missing automation**

Run: `./scripts/test-outline-deployment.sh`

Expected: non-zero exit with `Missing executable scripts/deploy-outline-aliyun.sh`.

- [ ] **Step 3: Create the deployment script**

Create `scripts/deploy-outline-aliyun.sh` implementing these exact operations in this order:

```bash
#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SSH_KEY="${SSH_KEY:-/Users/dogekul/Downloads/codex.pem}"
TARGET="${TARGET:-root@8.166.121.138}"
REMOTE_DIR=/opt/outline-stack
BACKUP_DIR=/opt/outline-backups
SSH=(ssh -i "$SSH_KEY" -o IdentitiesOnly=yes -o BatchMode=yes -o ConnectTimeout=10)

[[ -f "$SSH_KEY" ]] || { echo "SSH key not found" >&2; exit 1; }
[[ "$(stat -f '%Lp' "$SSH_KEY" 2>/dev/null || stat -c '%a' "$SSH_KEY")" =~ ^(400|600)$ ]] || {
  echo "SSH key must use mode 400 or 600" >&2; exit 1;
}
for command in ssh rsync scp curl; do command -v "$command" >/dev/null; done

"${SSH[@]}" "$TARGET" 'test -d /opt/zhilu-delivery && docker network inspect zhilu-delivery_default >/dev/null && ! ss -lnt | grep -q ":443 "'
"${SSH[@]}" "$TARGET" "install -d -m 700 '$REMOTE_DIR' '$REMOTE_DIR/dex' '$BACKUP_DIR/preflight'"
rsync -a -e "ssh -i '$SSH_KEY' -o IdentitiesOnly=yes -o BatchMode=yes" \
  "$ROOT_DIR/deploy/outline/" "$TARGET:$REMOTE_DIR/"
rsync -a -e "ssh -i '$SSH_KEY' -o IdentitiesOnly=yes -o BatchMode=yes" \
  "$ROOT_DIR/scripts/backup-outline-aliyun.sh" "$ROOT_DIR/scripts/verify-outline-aliyun.sh" \
  "$TARGET:$REMOTE_DIR/"

"${SSH[@]}" "$TARGET" bash -s <<'REMOTE'
set -Eeuo pipefail
cd /opt/outline-stack
stamp="$(date +%Y%m%d-%H%M%S)"
docker ps --format '{{.Names}}\t{{.Status}}\t{{.Ports}}' > "/opt/outline-backups/preflight/docker-$stamp.txt"
free -h > "/opt/outline-backups/preflight/memory-$stamp.txt"
df -h > "/opt/outline-backups/preflight/disk-$stamp.txt"

cd /opt/zhilu-delivery
set -a; . ./.env; set +a
docker compose --env-file .env -f deploy/aliyun/docker-compose.ecs.yml exec -T mysql \
  mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers delivery \
  | gzip -9 > "/opt/outline-backups/preflight/zhilu-$stamp.sql.gz"
gzip -t "/opt/outline-backups/preflight/zhilu-$stamp.sql.gz"

if ! swapon --show=NAME --noheadings | grep -q .; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null
  swapon /swapfile
  grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

cd /opt/outline-stack
docker pull caddy:2.11.4-alpine
if [[ ! -f .env ]]; then
  umask 077
  admin_password="$(openssl rand -hex 16)"
  admin_hash="$(docker run --rm caddy:2.11.4-alpine caddy hash-password --plaintext "$admin_password")"
  printf '%s\n' "$admin_hash" > dex/password.hash
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
chmod 600 .env dex/password.hash admin-credentials.txt
set -a; . ./.env; set +a
admin_hash="$(cat dex/password.hash)"
sed \
  -e "s|__DEX_OIDC_CLIENT_SECRET__|$DEX_OIDC_CLIENT_SECRET|g" \
  -e "s|__DEX_ADMIN_PASSWORD_HASH__|$admin_hash|g" \
  -e "s|__DEX_ADMIN_USER_ID__|$DEX_ADMIN_USER_ID|g" \
  dex-config.yaml.tpl > dex/config.yaml
chmod 600 dex/config.yaml
chmod 700 backup-outline-aliyun.sh verify-outline-aliyun.sh

compose=(docker compose --env-file .env -f docker-compose.ecs.yml)
"${compose[@]}" config --quiet
for image in postgres:18.4-alpine3.24 redis:7.4.9-alpine3.21 ghcr.io/dexidp/dex:v2.45.1-alpine docker.getoutline.com/outlinewiki/outline:1.7.1; do
  docker pull "$image"
done
"${compose[@]}" up -d postgres redis dex outline caddy
"${compose[@]}" ps
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
REMOTE

scp -i "$SSH_KEY" -o IdentitiesOnly=yes -o BatchMode=yes \
  "$TARGET:$REMOTE_DIR/admin-credentials.txt" /Users/dogekul/Downloads/outline-admin-credentials.txt
chmod 600 /Users/dogekul/Downloads/outline-admin-credentials.txt
echo "Outline stack started; credentials saved to /Users/dogekul/Downloads/outline-admin-credentials.txt"
```

The script must not echo any value from `.env` or `admin-credentials.txt`.

- [ ] **Step 4: Create the backup script**

Create `scripts/backup-outline-aliyun.sh` with this complete content. It creates a UTC timestamp directory, runs `pg_dump`, archives the `outline-stack_outline-data`, `outline-stack_dex-data`, and `outline-stack_caddy-data` volumes with pinned `alpine:3.23`, archives `.env`, rendered configs and Compose files, validates readability, writes `SHA256SUMS`, and keeps the newest seven backups:

```bash
#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
STACK_DIR=/opt/outline-stack
BACKUP_ROOT=/opt/outline-backups
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
target="$BACKUP_ROOT/$stamp"
mkdir -p "$target"
cd "$STACK_DIR"
compose=(docker compose --env-file .env -f docker-compose.ecs.yml)
"${compose[@]}" exec -T postgres pg_dump -U outline -d outline --format=custom > "$target/outline.pgdump"
"${compose[@]}" exec -T postgres pg_restore -l < "$target/outline.pgdump" >/dev/null
for volume in outline-data dex-data caddy-data; do
  docker run --rm \
    -v "outline-stack_${volume}:/source:ro" \
    -v "$target:/backup" \
    alpine:3.23 sh -c "tar -C /source -czf /backup/${volume}.tar.gz ."
  tar -tzf "$target/${volume}.tar.gz" >/dev/null
done
tar -czf "$target/config-and-secrets.tar.gz" .env docker-compose.ecs.yml Caddyfile dex/config.yaml dex/password.hash
tar -tzf "$target/config-and-secrets.tar.gz" >/dev/null
(cd "$target" && sha256sum outline.pgdump *.tar.gz > SHA256SUMS && sha256sum -c SHA256SUMS)
mapfile -t backups < <(find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -name '20*T*Z' | sort)
if (( ${#backups[@]} > 7 )); then
  printf '%s\0' "${backups[@]:0:${#backups[@]}-7}" | xargs -0 rm -rf --
fi
echo "Outline backup completed: $target"
```

- [ ] **Step 5: Create the verification script**

Create `scripts/verify-outline-aliyun.sh` to run from the ECS and assert:

```bash
#!/usr/bin/env bash
set -Eeuo pipefail
cd /opt/outline-stack
compose=(docker compose --env-file .env -f docker-compose.ecs.yml)
"${compose[@]}" ps --format json | grep -q 'outline-stack-outline'
curl -fsS https://outline.8.166.121.138.sslip.io/dex/.well-known/openid-configuration | grep -q '"issuer":"https://outline.8.166.121.138.sslip.io/dex"'
curl -fsS -o /dev/null https://outline.8.166.121.138.sslip.io/
curl -fsS http://127.0.0.1:53990/actuator/health | grep -q '"status":"UP"'
curl -fsS http://127.0.0.1/api/health >/dev/null
[[ "$(docker ps --filter name=rainier --format '{{.Names}}' | wc -l | tr -d ' ')" -eq 3 ]]
[[ "$(docker ps --filter name=zhilu-delivery --format '{{.Names}}' | wc -l | tr -d ' ')" -eq 6 ]]
! dmesg --since '15 minutes ago' 2>/dev/null | grep -qi 'out of memory'
echo "Outline infrastructure verification passed"
```

- [ ] **Step 6: Make scripts executable, run static tests, and commit**

```bash
chmod +x scripts/deploy-outline-aliyun.sh scripts/backup-outline-aliyun.sh scripts/verify-outline-aliyun.sh
./scripts/test-outline-deployment.sh
git add scripts/deploy-outline-aliyun.sh scripts/backup-outline-aliyun.sh scripts/verify-outline-aliyun.sh scripts/test-outline-deployment.sh
git commit -m "ops: automate Outline ECS deployment and backup"
```

Expected: tests pass and the commit contains only the four scripts.

### Task 3: Run protected remote preflight and deploy the stack

**Files:**
- No repository changes.
- Create remotely: `/opt/outline-stack/*`, `/opt/outline-backups/preflight/*`, `/etc/cron.d/outline-backup`.

**Interfaces:**
- Consumes: Task 1 and Task 2 commits.
- Produces: healthy private services and HTTPS public endpoint.

- [ ] **Step 1: Verify the existing production baseline**

Run read-only SSH checks for `docker ps`, `free -h`, `df -h`, `swapon --show`, `ss -lntup`, `docker network inspect zhilu-delivery_default`, Rainier `/api/health`, and Zhilu `/zhilu/actuator/health`.

Expected: 3 Rainier and 6 Zhilu containers running, 443 unused, both health endpoints successful, at least 5 GiB free disk.

- [ ] **Step 2: Run the deployment script**

Run:

```bash
./scripts/deploy-outline-aliyun.sh
```

Expected: preflight database backup validates, 2 GiB Swap exists, all pinned images pull, and five `outline-stack-*` containers start. If 443 is blocked or a container fails health checks, stop only the Outline stack without volumes and inspect the new stack logs.

- [ ] **Step 3: Poll health without hiding transient failures**

Every 20 seconds for up to 10 minutes, run:

```bash
ssh -i /Users/dogekul/Downloads/codex.pem root@8.166.121.138 \
  'cd /opt/outline-stack && docker compose --env-file .env -f docker-compose.ecs.yml ps && free -m && swapon --show'
```

Expected: all five services become healthy, restart count remains zero, host has no OOM, and Swap stays below 1536 MiB.

- [ ] **Step 4: Verify public TLS and OIDC discovery**

Run:

```bash
curl -fsS https://outline.8.166.121.138.sslip.io/dex/.well-known/openid-configuration
curl -fsSI https://outline.8.166.121.138.sslip.io/
```

Expected: Dex discovery contains the exact issuer and Outline returns a successful or redirect response over a publicly trusted certificate.

### Task 4: Bootstrap Outline through Dex and create the scoped integration token

**Files:**
- No repository changes.
- Read locally: `/Users/dogekul/Downloads/outline-admin-credentials.txt`.

**Interfaces:**
- Consumes: healthy public Outline and Dex services.
- Produces: Outline workspace, Collection UUID, and a scoped API token used in Task 5.

- [ ] **Step 1: Open Outline and complete OIDC login**

Use the in-app browser at `https://outline.8.166.121.138.sslip.io`, select “智鹿账号”, and enter `outline-admin@zhilu.local` plus the generated password from the root-only local credentials file.

Expected: Dex authenticates and returns to Outline without an issuer or callback error.

- [ ] **Step 2: Complete first-run workspace setup**

Set the workspace name to `智鹿交付文档中心`. If Outline creates an initial sample collection, leave it intact until the target collection is created and verified.

Expected: the user reaches the Outline workspace and has administrator settings access.

- [ ] **Step 3: Create the target collection**

Create a private collection named `智鹿交付文档中心` and record the final identifier segment from its Collection URL. The canonical UUID is resolved in Step 5 after the scoped token exists.

Expected: `collections.info` for the recorded identifier returns name `智鹿交付文档中心`.

- [ ] **Step 4: Create the least-privilege API token**

In Outline Settings → API Keys, create a long-lived key named `智鹿系统集成` with exactly these scopes:

```text
collections.info documents.create documents.info documents.list documents.update documents.move documents.export
```

Copy it once into a protected temporary value for Task 5; do not save it in the repository, shell history, commentary, or screenshots.

- [ ] **Step 5: Validate every required scope**

From the ECS, call `collections.info` with the Collection URL identifier recorded in Step 3 and record the canonical UUID returned in `data.id`. Then call the six document endpoints with the same Bearer token: create two disposable documents, read and update the first, list the collection, move the first under the second, and export the first. Keep both disposable documents for the later Zhilu smoke test rather than deleting them, because delete permission is intentionally absent.

Expected: all required calls return success and the token does not require a broader wildcard scope.

### Task 5: Save the organization configuration and initialize Zhilu document roots

**Files:**
- No repository changes.

**Interfaces:**
- Consumes: internal URL `http://outline:3000`, public URL `https://outline.8.166.121.138.sslip.io`, scoped API token, and target Collection UUID.
- Produces: organization-level encrypted Outline configuration and initialized knowledge/project/product roots.

- [ ] **Step 1: Log into Zhilu as the existing administrator**

Open `http://8.166.121.138/zhilu/`, log in as `admin / Admin@123`, and navigate to System Management → Document Center.

Expected: the document-center configuration page loads for the demo organization.

- [ ] **Step 2: Test the draft configuration before saving**

Enter the internal URL `http://outline:3000` and public URL `https://outline.8.166.121.138.sslip.io`. Paste the exact Collection UUID recorded in Task 4 Step 3 into the Collection field, and paste the scoped token created in Task 4 Step 4 into the API Token field.

```text
Backend URL: http://outline:3000
Public URL: https://outline.8.166.121.138.sslip.io
```

Use the page’s connection test action.

Expected: status `READY`, normalized Collection UUID, and Collection name `智鹿交付文档中心`. A failure must not be followed by Save.

- [ ] **Step 3: Save and verify the encrypted organization configuration**

Save from the same page, then reload the configuration.

Expected: source `ORGANIZATION`, correct two URLs and Collection UUID, `apiTokenConfigured=true`, and no API token value in the response or UI.

- [ ] **Step 4: Initialize root structures**

Run the existing “initialize document center” and “initialize product documents” actions exactly once each, then use the status refresh action.

Expected: knowledge, project, and product roots report ready. If either operation partially fails, use the same idempotent retry action and do not delete created Outline documents.

- [ ] **Step 5: Run a real Zhilu document workflow**

Create or open one knowledge/product specification document in Zhilu, edit its Markdown, save, reload, export it, and click “Open in Outline”.

Expected: content round-trips through Outline, export is non-empty, and the public deep link opens the exact document.

### Task 6: Install backups, create disaster artifacts, document operations, and complete verification

**Files:**
- Modify: `docs/operations/deployment.md`

**Interfaces:**
- Consumes: fully initialized stack and Task 2 backup script.
- Produces: verified backup, encrypted off-host disaster package, operations handoff, and final deployment commit on `main`.

- [ ] **Step 1: Run and validate the first complete backup**

Run remotely:

```bash
/opt/outline-stack/backup-outline-aliyun.sh
latest="$(find /opt/outline-backups -mindepth 1 -maxdepth 1 -type d -name '20*T*Z' | sort | tail -1)"
cd "$latest" && sha256sum -c SHA256SUMS
```

Expected: PostgreSQL custom dump, three volume archives, root-only config archive, and all checksums pass.

- [ ] **Step 2: Create the encrypted off-host secret archive**

On the ECS, generate a 32-byte random passphrase, encrypt a tar archive containing `.env`, Dex config/hash, and Compose/Caddy files using `openssl enc -aes-256-cbc -pbkdf2 -salt`, then copy the `.enc` artifact and a separate root-only passphrase file to `/Users/dogekul/Downloads/`. Remove the plaintext staging archive and the server-side plaintext `admin-credentials.txt` after confirming the local copies exist.

Expected: `openssl enc -d ... | tar -tzf -` lists the expected files locally without exposing their contents.

- [ ] **Step 3: Document operations**

Append an “Alibaba Outline” section to `docs/operations/deployment.md` containing the public URL, stack and backup paths, exact `docker compose` status/log/restart commands, daily 03:30 Asia/Shanghai backup schedule, retention of seven backups, restore order, version upgrade procedure, and the rule never to rotate `SECRET_KEY` during restart or upgrade.

- [ ] **Step 4: Run final infrastructure and regression verification**

Run remotely:

```bash
/opt/outline-stack/verify-outline-aliyun.sh
cd /opt/zhilu-delivery && BASE_URL=http://127.0.0.1:53990 ./scripts/smoke-test.sh
```

Run publicly from the local machine:

```bash
BASE_URL=http://8.166.121.138/zhilu ./scripts/smoke-test.sh
curl -fsS -o /dev/null https://outline.8.166.121.138.sslip.io/
```

Expected: both Zhilu smoke tests and Outline verification pass; Rainier remains healthy; five new and nine existing containers are running without restart loops.

- [ ] **Step 5: Commit operations documentation and verify the branch**

```bash
git add docs/operations/deployment.md docs/superpowers/specs/2026-07-20-outline-aliyun-deployment-integration-design.md
git commit -m "docs: document Outline ECS operations"
./scripts/test-outline-deployment.sh
git status --short
```

Expected: tests pass and the worktree is clean.

- [ ] **Step 6: Integrate the deployment commits into `main` without touching user changes**

From the original workspace, confirm the four user-owned dirty paths are unchanged, then fast-forward or merge `codex/outline-aliyun-deploy` into `main`. Stage or commit no pre-existing dirty file.

Expected: `main` contains only the deployment bundle, automation, spec variant clarification, plan, and operations documentation in the new commits; the four original dirty paths remain dirty/untracked exactly as before.

- [ ] **Step 7: Final handoff**

Report the public Outline URL, administrator email, local credential file path, encrypted disaster archive path, backup path/schedule, integration status, exact verification results, and commit IDs. Do not print the API token, `.env` secrets, administrator password, or disaster passphrase in the response.
