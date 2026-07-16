# 智鹿交付项目管理平台

面向企业软件交付的一体化工作台，界面参考飞书项目的高密度工作台风格。平台将项目执行、需求决策、Agent 自动化、标准化沉淀、知识复用和资源调度放在同一条可审计链路中。

## 直接运行

```bash
cp .env.example .env
docker compose up -d --build
./scripts/smoke-test.sh
```

打开 [http://localhost:53990](http://localhost:53990)，使用 `admin / Admin@123` 登录。演示环境已预置客户中心五阶段商机、实施项目、三阶段客户运营、6 个不同阶段项目、三层需求、标准化债务、知识条目、技能矩阵和资源冲突。

## 功能范围

- 总控看板：KPI、项目列表/卡片、风险热力、产品项目矩阵和快速启动。
- 客户中心：客户主数据、五阶段商机与售前关口、实施协同与驾驶舱、回款维保、持续运营和复购闭环。
- 项目空间：启动、需求、二开、上线、试运行移交、标准化、收尾七阶段门禁与阶段文档。
- 需求工坊：采集、AI 建议、人工 L0/L1/L2 确认、有理由改判、查重合并与二开任务。
- Agent 与 AI：六个交付 Skill、HMAC 回调、幂等、重试、超时、稳定产出物与 OpenAI-compatible 接入。
- 标准化中心：能力基线、成熟度、偏离度、高频二开债务、成本归集和产品飞轮。
- 知识库：最佳实践、代码片段、培训材料、文档模版的草稿/发布和全文检索。
- Outline 文档中心：知识正文和项目文档统一存储在私有化 Outline，支持预览、编辑、版本确认、失败重试及 Markdown/HTML/PDF/Word 导出。
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
        │             ├─ MinIO (versioned artifacts)
        │             └─ Outline (document content and revisions)
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
OUTLINE_BASE_URL=http://localhost:3000 \
OUTLINE_API_TOKEN='ol_api_xxx' \
OUTLINE_COLLECTION_ID='集合 UUID' \
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
node --check mock-outline/server.mjs
docker compose config --quiet
```

## 配置

常用环境变量见 [.env.example](./.env.example)。AI 为可选增强：`AI_BASE_URL` / `AI_MODEL` / `AI_API_KEY` 为空时，核心业务流程仍可人工完成。未来多供应商接入可在已抽象的 `AiClient` 上扩展。

### 私有化 Outline

Outline 是知识库与项目文档的可编辑正文、目录和修订中心；MySQL 保存业务关联、同步状态、确认版本，以及模版发布/项目创建时仅供异步复制使用的不可变快照。配置步骤：

1. 在 Outline 中为专用服务账号创建 API Key。
2. 配置 `OUTLINE_BASE_URL`、`OUTLINE_API_TOKEN` 和 `OUTLINE_COLLECTION_ID`。
3. 登录系统后调用系统管理接口初始化根目录，再按需迁移历史正文：

```bash
curl -fsS -X POST http://localhost:8082/api/v1/admin/document-center/initialize
curl -fsS -X POST http://localhost:8082/api/v1/admin/document-center/migrate-knowledge
curl -fsS -X POST http://localhost:8082/api/v1/admin/document-center/migrate-projects
curl -fsS http://localhost:8082/api/v1/admin/document-center/status
```

管理接口需要已登录的系统管理员会话与 CSRF Token，推荐直接从系统管理页操作。`OUTLINE_COLLECTION_ID` 必须是 Outline API 返回的集合 UUID；例如浏览器地址 `/collection/5pm66bm5lqk5luy-D4rIACBrmU/` 末段是 URL 标识，不能直接替代集合 UUID。现有 API Key 无法从哈希反推，缺失时请在 Outline 重新创建。密钥只由后端环境变量读取，不会返回前端或写入数据库。

未配置或暂时无法连接 Outline 时，项目仍可创建，文档空间会显示明确的待初始化/失败状态；修复配置后可直接重试。

从早期 V15 数据升级时，系统会在旧模版或待初始化项目首次使用时校验 Outline 当前修订：修订仍与原发布修订一致则自动补齐不可变快照；修订已变化则明确阻止复制，并提示重新发布模版或重建项目，避免静默漏建必需文档。

当前兼容边界：Markdown 正文、目录和修订以 Outline 为准；培训课件等二进制附件仍由现有 MinIO 文件中心保存和下载，不会自动迁移为 Outline Attachment。服务端导出不会抓取远程图片，避免 SSRF；需要携带 Outline 私有图片的完整离线包时，请使用 Outline 原生集合导出。

## 文档

- [系统设计](./docs/superpowers/specs/2026-07-13-delivery-project-management-platform-design.md)
- [实施计划](./docs/superpowers/plans/2026-07-13-delivery-platform-implementation.md)
- [Agent HTTP 契约](./docs/api/agent-contract.md)
- [部署与运维](./docs/operations/deployment.md)
