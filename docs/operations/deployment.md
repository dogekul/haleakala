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
