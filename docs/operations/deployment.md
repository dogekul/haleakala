# 部署与运维手册

## 系统要求

- Docker Engine 24+ 与 Docker Compose v2
- 默认需要端口：`53990` Web、`8082` API、`3309` MySQL、`6381` Redis、`9002/9003` MinIO、`8091` Mock Agent
- 生产环境建议仅对外暴露 Web 端口，其余服务放在内网

## 首次部署

- `SETTINGS_ENCRYPTION_KEY` 用于加密管理页保存的 API Token。生产环境必须设置独立、稳定的高强度值；更换前需先完成密钥轮换，直接更换会导致已有密文无法解密。

```bash
cp .env.example .env
# 先修改 .env 中的数据库、MinIO 和 Agent 密钥
docker compose up -d --build
docker compose ps
./scripts/smoke-test.sh
```

Web 入口为 `http://localhost:53990`，演示账号 `admin / Admin@123`。演示数据只在 Compose 配置的 `db/demo` Flyway 路径中加载，常规测试和独立后端运行不会自动添加。

## 健康检查与日志

```bash
curl http://localhost:53990/actuator/health
docker compose logs -f backend
docker compose logs -f mysql redis minio mock-agent
```

Swagger UI 可直接通过 `http://localhost:8082/swagger-ui/index.html` 访问。默认 Nginx 只代理 `/api` 和 `/actuator`；如需经由 Web 域名对外提供 Swagger，在 `frontend/nginx.conf` 中增加 `/swagger-ui/` 与 `/v3/api-docs/` 代理。

## 升级

1. 备份 MySQL 和 MinIO。
2. 拉取新代码后执行 `docker compose build backend frontend mock-agent`。
3. 执行 `docker compose up -d`。Flyway 会在后端接受流量前完成数据库迁移。
4. 执行 `./scripts/smoke-test.sh`，并检查审计日志。

## 备份与恢复

```bash
# MySQL
docker compose exec -T mysql mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" delivery > delivery.sql
docker compose exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" delivery < delivery.sql

# MinIO 和 Redis 使用 Docker volume 快照或企业备份方案
docker volume ls | grep zhilu-delivery
```

MySQL 是业务事实的权威数据源；Redis 只保存会话，恢复失败时用户需重新登录。MinIO 中的对象必须与 MySQL `file_object/file_version` 同步备份。

## 阿里云 Outline

- 公网入口：`https://outline.8.166.121.138.sslip.io`
- ECS 目录：`/opt/outline-stack`
- 备份目录：`/opt/outline-backups`
- 智鹿文档中心的服务地址和浏览器地址均使用上述 HTTPS 入口。Outline 开启强制 HTTPS 时，直连 `http://outline:3000` 会拒绝 POST API 请求。

常用运维命令：

```bash
cd /opt/outline-stack
docker compose --env-file .env -f docker-compose.ecs.yml ps
docker compose --env-file .env -f docker-compose.ecs.yml logs --tail=200 outline dex caddy
docker compose --env-file .env -f docker-compose.ecs.yml restart outline
./verify-outline-aliyun.sh
```

`/etc/cron.d/outline-backup` 每天 03:30（Asia/Shanghai）运行 `/opt/outline-stack/backup-outline-aliyun.sh`，保留最近 7 份完整备份。备份先写入隐藏的 `.partial` 目录，数据库、卷归档和 SHA-256 校验全部通过后才发布为时间戳目录。

恢复顺序：

1. 停止 `outline` 和 `caddy`，保留 PostgreSQL、Redis 和所有 Docker volume。
2. 根据 `SHA256SUMS` 校验备份，用 `pg_restore --clean --if-exists` 恢复 `outline.pgdump`。
3. 依次恢复 `outline-data.tar.gz`、`dex-data.tar.gz` 和 `caddy-data.tar.gz`。
4. 仅在原服务器配置丢失时，从加密灾备包恢复 `.env`、Dex 和 Caddy 配置。
5. 先启动 PostgreSQL、Redis、Dex 和 Caddy，运行 Outline 数据库迁移，再启动 Outline 并执行验证脚本。

升级时先生成并验证备份，再修改锁定的镜像版本、拉取镜像、运行迁移和健康检查。重启或升级时绝对不能轮换 `SECRET_KEY`，否则已加密的 Outline 数据将无法解密。

## 安全基线

- 替换 `.env` 中所有默认密钥，并将 `.env` 保持在版本库外。
- 首次生产登录后新建管理员账号，删除或禁用演示账号。
- 对外使用 HTTPS，限制 MySQL、Redis、MinIO 和 Agent 端口的网络访问。
- SSO 生产启用时配置 OIDC Client，本地账号仅作应急通道。
- Agent 回调使用 5 分钟时间窗的 HMAC-SHA256，密钥需定期轮换。

## 停止与清理

```bash
docker compose down
# 仅在确认要删除全部业务数据时执行
docker compose down -v
```
