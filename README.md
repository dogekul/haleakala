# 智鹿交付项目管理平台

面向企业软件交付的一体化工作台，界面参考飞书项目的高密度工作台风格。平台将项目执行、需求决策、Agent 自动化、标准化沉淀、知识复用和资源调度放在同一条可审计链路中。

## 直接运行

```bash
cp .env.example .env
docker compose up -d --build
./scripts/smoke-test.sh
```

打开 [http://localhost:53990](http://localhost:53990)，使用 `admin / Admin@123` 登录。演示环境已预置 6 个不同阶段项目、三层需求、标准化债务、知识条目、技能矩阵和资源冲突。

## 功能范围

- 总控看板：KPI、项目列表/卡片、风险热力、产品项目矩阵和快速启动。
- 项目空间：启动、需求、二开、上线、试运行移交、标准化、收尾七阶段门禁。
- 需求工坊：采集、AI 建议、人工 L0/L1/L2 确认、有理由改判、查重合并与二开任务。
- Agent 与 AI：六个交付 Skill、HMAC 回调、幂等、重试、超时、稳定产出物与 OpenAI-compatible 接入。
- 标准化中心：能力基线、成熟度、偏离度、高频二开债务、成本归集和产品飞轮。
- 知识库：最佳实践、代码片段、培训材料的草稿/发布和全文检索。
- 资源中心：人员档案、技能矩阵、项目排期、负载分析和重叠冲突预警。
- 系统管理：用户与团队、角色权限、产品版本、审计日志和平台运行设置。
- 平台基础：本地/OIDC 登录、RBAC、CSRF、Redis Session、MySQL/Flyway、MinIO 版本文件和审计日志。

## 技术架构

```text
React 18 + Ant Design + TanStack Query
                 │ same-origin /api
              Nginx
                 │
Spring Boot 2.7 / Java 8 ── MySQL 8 (business truth)
        │             ├─ Redis 7 (session)
        │             └─ MinIO (versioned artifacts)
        └─ HMAC HTTP ── External Agent / bundled Mock Agent
```

## 本地开发

```bash
# 基础设施（也可使用本机 MySQL/Redis/MinIO）
docker compose up -d mysql redis minio mock-agent

# 后端
cd backend
DB_URL='jdbc:mysql://localhost:3309/delivery?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true' \
REDIS_PORT=6381 \
MINIO_ENDPOINT=http://localhost:9002 \
AGENT_BASE_URL=http://localhost:8091 \
AGENT_CALLBACK_URL=http://host.docker.internal:8080/api/v1/integrations/agent/events \
SPRING_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/demo \
mvn spring-boot:run

# 前端
cd frontend
# pnpm 11 需要 Node.js 22+
corepack enable
pnpm install
pnpm dev
```

前端开发服务默认为 `53990`，会代理到本机 `8080`。上面的命令适配 Compose 暴露的开发端口并加载演示数据；独立后端默认只运行正式迁移。Linux 上从 Mock Agent 容器回调宿主机时，请将 `host.docker.internal` 替换为 Docker 网桥网关。

## 验证

```bash
cd backend && mvn test
cd frontend && pnpm test -- --run && pnpm build
node --check mock-agent/server.mjs
docker compose config --quiet
```

## 配置

常用环境变量见 [.env.example](./.env.example)。AI 为可选增强：`AI_BASE_URL` / `AI_MODEL` / `AI_API_KEY` 为空时，核心业务流程仍可人工完成。未来多供应商接入可在已抽象的 `AiClient` 上扩展。

## 文档

- [系统设计](./docs/superpowers/specs/2026-07-13-delivery-project-management-platform-design.md)
- [实施计划](./docs/superpowers/plans/2026-07-13-delivery-platform-implementation.md)
- [Agent HTTP 契约](./docs/api/agent-contract.md)
- [部署与运维](./docs/operations/deployment.md)
