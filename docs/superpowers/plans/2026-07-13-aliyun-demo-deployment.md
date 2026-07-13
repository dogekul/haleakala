# Alibaba Cloud Demo Deployment Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Publish the current Zhilu Delivery workspace snapshot to the Rainier Alibaba Cloud ECS at `http://8.166.121.138/zhilu/` without disrupting Rainier.

**Architecture:** Add a deployment-specific Docker Compose stack under `deploy/aliyun` and a local orchestration script that validates the workspace, rsyncs the current snapshot to `/opt/zhilu-delivery`, creates a persistent remote secret file when absent, builds images sequentially, starts the stack, and runs both remote and public smoke tests. Rainier's Nginx keeps serving the IP root and its own `/api`, while routing only `/zhilu/` to port `53990`; the frontend is built with the matching route and asset base, the backend is loopback-bound, and MySQL, Redis, MinIO, and the mock agent remain on the Compose network.

**Tech Stack:** Bash, rsync, SSH, Docker Compose, Spring Boot, React/Vite, MySQL 8, Redis 7, MinIO, Nginx

---

### Task 1: Add an isolated ECS Compose stack

**Files:**
- Create: `deploy/aliyun/docker-compose.ecs.yml`

**Step 1: Prove the deployment configuration is absent**

Run:

```bash
docker compose -f deploy/aliyun/docker-compose.ecs.yml config --quiet
```

Expected: FAIL because the file does not exist.

**Step 2: Add the minimal ECS stack**

Create a Compose project named `zhilu-delivery` with:

- MySQL, Redis, MinIO, and mock-agent available only inside the Compose network.
- Backend published only as `127.0.0.1:8082:8080`.
- Frontend published as `53990:80`.
- Health checks and dependency ordering for every runtime service.
- Persistent volumes for MySQL, Redis, and MinIO.
- Backend configuration for Flyway migrations plus demo data and conservative JVM memory limits.

**Step 3: Validate interpolation and topology**

Run:

```bash
DB_PASSWORD=test MYSQL_ROOT_PASSWORD=test MINIO_ACCESS_KEY=test MINIO_SECRET_KEY=test AGENT_SHARED_SECRET=test \
  docker compose -f deploy/aliyun/docker-compose.ecs.yml config --quiet
```

Expected: PASS with no missing variable or schema errors.

### Task 2: Add a repeatable deployment orchestrator

**Files:**
- Create: `scripts/deploy-aliyun-ecs.sh`

**Step 1: Prove the script is absent**

Run:

```bash
bash -n scripts/deploy-aliyun-ecs.sh
```

Expected: FAIL because the file does not exist.

**Step 2: Implement local and remote safeguards**

The script must:

1. Use `SSH_KEY`, `TARGET_HOST`, `PUBLIC_HOST`, and `REMOTE_DIR` overrides with safe defaults.
2. Check the PEM mode, required local tools, source files, Rainier public UI, and Rainier health endpoint.
3. rsync the current workspace while excluding Git metadata, local `.env`, dependencies, build outputs, IDE files, and logs.
4. Preserve the remote `.env`; create it with random secrets and mode `600` only when absent.
5. Validate Compose, build backend/frontend/mock-agent sequentially, then start the stack.
6. Poll health and print diagnostics on failure without printing secrets.
7. Install the dedicated `/zhilu/` route in Rainier's Nginx without changing its root or API routes, then run the repository smoke test locally on the ECS and again through the public endpoint.
8. Re-check Rainier after deployment.

**Step 3: Make the script executable and validate its syntax**

Run:

```bash
chmod +x scripts/deploy-aliyun-ecs.sh
bash -n scripts/deploy-aliyun-ecs.sh
```

Expected: PASS.

### Task 3: Validate the workspace before deployment

**Files:**
- Verify only; do not modify current business files.

**Step 1: Run backend tests**

Run:

```bash
cd backend && mvn test
```

Expected: PASS.

**Step 2: Run frontend tests and production build**

Run:

```bash
cd frontend && pnpm test:run && pnpm build
```

Expected: PASS.

**Step 3: Validate the mock agent and Compose configuration**

Run:

```bash
node --check mock-agent/server.mjs
DB_PASSWORD=test MYSQL_ROOT_PASSWORD=test MINIO_ACCESS_KEY=test MINIO_SECRET_KEY=test AGENT_SHARED_SECRET=test \
  docker compose -f deploy/aliyun/docker-compose.ecs.yml config --quiet
```

Expected: PASS.

**Step 4: Commit deployment-only files**

Run:

```bash
git add deploy/aliyun/docker-compose.ecs.yml scripts/deploy-aliyun-ecs.sh docs/superpowers/plans/2026-07-13-aliyun-demo-deployment.md
git commit -m "feat: add aliyun ecs demo deployment"
```

Expected: deployment files are committed while unrelated working-tree changes remain untouched.

### Task 4: Deploy and verify the public demo

**Files:**
- Deploy snapshot to: `/opt/zhilu-delivery`

**Step 1: Execute the deployment**

Run:

```bash
SSH_KEY=/Users/dogekul/Downloads/codex.pem ./scripts/deploy-aliyun-ecs.sh
```

Expected: images build sequentially, all services become healthy, remote and public smoke tests pass.

**Step 2: Verify service exposure and Rainier isolation**

Run:

```bash
ssh -i /Users/dogekul/Downloads/codex.pem -o IdentitiesOnly=yes root@8.166.121.138 \
  'cd /opt/zhilu-delivery && docker compose --env-file .env -f deploy/aliyun/docker-compose.ecs.yml ps && ss -lnt'
curl -fsS http://8.166.121.138/zhilu/actuator/health
curl -fsS http://8.166.121.138/api/health
```

Expected: Zhilu is public through its dedicated `/zhilu/` path on port `80`, its frontend listens on host port `53990`, its backend is loopback-only on `8082`, dependencies are not host-published, and Rainier remains healthy on its unchanged IP-root route.

**Step 3: Verify login and a core UI route**

Open `http://8.166.121.138/zhilu/`, sign in with the demo account, confirm the dashboard renders, and leave the public dashboard open for review.

Expected: login succeeds and the dashboard is usable.
