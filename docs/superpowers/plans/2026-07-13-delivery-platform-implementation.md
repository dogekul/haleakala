# 智鹿交付项目管理平台实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在空 Git 仓库中交付 React + Java 8 的内部生产级交付项目管理平台，覆盖 6 模块、28 功能、真实持久化、权限、文件、AI/Agent 契约和 Docker Compose。

**Architecture:** 前端为 React 单页应用，后端为 Spring Boot 2.7 模块化单体。MySQL 是唯一事实源，Redis 只做会话/缓存，MinIO 保存文件；外部 Agent 和 AI 通过受控 HTTP 契约接入，开发环境使用 Mock Agent。

**Tech Stack:** Java 8、Spring Boot 2.7、Spring Security、Spring Data JPA、Flyway、MySQL 8、Redis、MinIO、React 18、TypeScript、Vite、Ant Design、TanStack Query、ECharts、Vitest、Playwright、Docker Compose。

## Global Constraints

- Java 运行时必须为 1.8；不得使用 Java 9+ API、record、var、文本块或虚拟线程。
- 单一公司、多个团队/产品线；不实现 SaaS 多租户。
- 一个 Spring Boot 部署单元；不引入微服务、BPMN 引擎或消息队列。
- 全部业务事实写入 MySQL；Redis 数据必须可重建；文件正文写入 MinIO。
- 前端沿用现有原型的信息架构和字段，以浅色双层导航、高密度列表为默认并保留卡片视图。
- 交付全部 6 模块、28 功能；平台底座不扩张为额外业务模块。
- 外部 Agent 由其他团队建设；本工程必须提供稳定契约、Mock、回调幂等、超时和补偿。
- AI 首期只实现一个 OpenAI-compatible 客户端；未配置时明确返回服务未配置。
- 采用 TDD：每个任务先留下会失败的最小测试，再写实现。
- 每个任务完成后单独提交，提交不得包含 `.env`、密钥、构建输出或 `.superpowers/`。

---

## 文件结构

```text
backend/
  pom.xml
  src/main/java/com/zhilu/delivery/
    DeliveryApplication.java
    common/          # API 响应、异常、分页、时间、幂等
    config/          # Security、Redis、MinIO、OpenAPI
    iam/             # 用户、团队、角色、权限、登录、SSO
    catalog/         # 产品、版本、字典、标品能力卡基础目录
    project/         # 项目、阶段、成员、风险、里程碑、模板、产出物
    automation/      # Agent Job、AI Client、回调与补偿
    requirement/     # 需求、分类、漏斗、去重、二开
    standardization/ # 成熟度、偏离度、债务、成本、飞轮
    knowledge/       # 范例、代码片段、培训材料
    resource/        # 人员画像、配置、冲突、负载
    dashboard/       # 驾驶舱聚合查询
    storage/         # MinIO 文件与版本
    audit/           # 审计与活动日志
  src/main/resources/
    application.yml
    db/migration/    # Flyway SQL
  src/test/java/com/zhilu/delivery/
frontend/
  package.json
  vite.config.ts
  src/
    app/             # 路由、QueryClient、鉴权、主题
    components/      # 通用状态、表格、卡片、文件、Agent 面板
    modules/         # iam、dashboard、project、requirement、standardization、knowledge、resource
    services/        # 类型化 API 客户端
    test/            # Vitest 工具
mock-agent/
  server.mjs         # Node 标准库实现 Agent 契约
infra/
  docker-compose.yml
  nginx/default.conf
  mysql/init/
.env.example
README.md
docs/api/agent-contract.md
docs/deployment.md
```

---

### Task 1: 工程骨架、统一 API 与数据库基线

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/zhilu/delivery/DeliveryApplication.java`
- Create: `backend/src/main/java/com/zhilu/delivery/common/api/ApiError.java`
- Create: `backend/src/main/java/com/zhilu/delivery/common/error/GlobalExceptionHandler.java`
- Create: `backend/src/main/java/com/zhilu/delivery/common/model/BaseEntity.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/db/migration/V1__platform_schema.sql`
- Test: `backend/src/test/java/com/zhilu/delivery/DeliveryApplicationTest.java`
- Test: `backend/src/test/java/com/zhilu/delivery/common/error/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Produces: `BaseEntity{id, createdAt, updatedAt, version}` and JSON error `{code,message,traceId,fieldErrors}`.
- Produces: health endpoint `/actuator/health` used by Compose and later tasks.

- [ ] **Step 1: Write the failing boot and error-contract tests**

```java
@SpringBootTest
public class DeliveryApplicationTest {
  @Test public void contextLoads() {}
}

@WebMvcTest(TestValidationController.class)
public class GlobalExceptionHandlerTest {
  @Autowired private MockMvc mvc;
  @Test public void returnsStableValidationShape() throws Exception {
    mvc.perform(post("/test-validation").contentType(APPLICATION_JSON).content("{}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
      .andExpect(jsonPath("$.traceId").isNotEmpty());
  }
}
```

- [ ] **Step 2: Run tests and verify they fail before the application exists**

Run: `cd backend && mvn -q -Dtest=DeliveryApplicationTest,GlobalExceptionHandlerTest test`

Expected: FAIL because the application and API error types do not exist.

- [ ] **Step 3: Add the minimal Spring Boot application, managed dependencies, base entity, error handler, configuration and V1 schema**

`pom.xml` must use Spring Boot `2.7.18`, Java `1.8`, starters for web, validation, security, JPA, Redis, OAuth2 client, actuator and test; add MySQL runtime, Flyway, MinIO and springdoc-openapi-ui `1.7.0`.

```java
@SpringBootApplication
@EnableJpaAuditing
public class DeliveryApplication {
  public static void main(String[] args) {
    SpringApplication.run(DeliveryApplication.class, args);
  }
}
```

V1 creates `organization`, `team`, `app_user`, `role`, `permission`, mapping tables, `product`, `product_version`, `file_object`, `audit_log` and `system_setting` with UTF-8, foreign keys, indexes and optimistic-lock `version` columns.

- [ ] **Step 4: Run tests and schema syntax check**

Run: `cd backend && mvn -q test`

Expected: PASS with two test classes and no Java version errors.

- [ ] **Step 5: Commit**

```bash
git add backend
git commit -m "chore: bootstrap Java 8 backend"
```

---

### Task 2: 内置账号、团队、角色权限与 OIDC 登录

**Files:**
- Create: `backend/src/main/java/com/zhilu/delivery/iam/domain/User.java`
- Create: `backend/src/main/java/com/zhilu/delivery/iam/domain/Team.java`
- Create: `backend/src/main/java/com/zhilu/delivery/iam/domain/Role.java`
- Create: `backend/src/main/java/com/zhilu/delivery/iam/repo/UserRepository.java`
- Create: `backend/src/main/java/com/zhilu/delivery/iam/service/IamService.java`
- Create: `backend/src/main/java/com/zhilu/delivery/iam/api/AuthController.java`
- Create: `backend/src/main/java/com/zhilu/delivery/iam/api/AdminIamController.java`
- Create: `backend/src/main/java/com/zhilu/delivery/config/SecurityConfig.java`
- Create: `backend/src/main/resources/db/migration/V2__seed_roles.sql`
- Test: `backend/src/test/java/com/zhilu/delivery/iam/IamServiceTest.java`
- Test: `backend/src/test/java/com/zhilu/delivery/iam/AuthControllerIT.java`

**Interfaces:**
- Produces: `GET /api/v1/auth/me`, `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`.
- Produces: admin CRUD under `/api/v1/admin/users|teams|roles`.
- Produces: `CurrentUser{id,teamIds,roleCodes,permissions}` used by every protected module.

- [ ] **Step 1: Write failing tests for password login and permission enforcement**

```java
@Test public void localLoginCreatesAuthenticatedSession() {
  User user = service.createLocalUser("admin", "secret123", "系统管理员");
  assertTrue(service.verifyPassword(user, "secret123"));
  assertTrue(service.permissions(user.getId()).contains("system:manage"));
}
```

Integration test must assert unauthenticated `/api/v1/admin/users` returns 401 and a non-admin session returns 403.

- [ ] **Step 2: Run focused tests**

Run: `cd backend && mvn -q -Dtest=IamServiceTest,AuthControllerIT test`

Expected: FAIL because IAM services and endpoints are absent.

- [ ] **Step 3: Implement local BCrypt login, Redis-backed session, six seeded roles, editable role-permission mappings and optional OIDC user mapping**

Use Spring Security session cookies; do not add a JWT library. OIDC endpoints are enabled only when issuer/client settings exist. On first OIDC login, map by `sub` then verified email; never merge identities by display name.

```java
@Bean
SecurityFilterChain security(HttpSecurity http) throws Exception {
  http.authorizeRequests()
      .antMatchers("/actuator/health", "/api/v1/auth/login").permitAll()
      .anyRequest().authenticated()
    .and().formLogin().disable()
    .oauth2Login(Customizer.withDefaults())
    .logout().logoutUrl("/api/v1/auth/logout");
  return http.build();
}
```

- [ ] **Step 4: Verify security and IAM tests**

Run: `cd backend && mvn -q -Dtest='*Iam*','*Auth*' test`

Expected: PASS; 401 and 403 remain distinguishable.

- [ ] **Step 5: Commit**

```bash
git add backend/src backend/pom.xml
git commit -m "feat: add identity and role permissions"
```

---

### Task 3: 产品目录、文件服务与审计

**Files:**
- Create: `backend/src/main/java/com/zhilu/delivery/catalog/`
- Create: `backend/src/main/java/com/zhilu/delivery/storage/`
- Create: `backend/src/main/java/com/zhilu/delivery/audit/`
- Create: `backend/src/main/resources/db/migration/V3__catalog_storage_audit.sql`
- Test: `backend/src/test/java/com/zhilu/delivery/catalog/ProductCatalogIT.java`
- Test: `backend/src/test/java/com/zhilu/delivery/storage/FileServiceTest.java`

**Interfaces:**
- Produces: product/version CRUD at `/api/v1/products`.
- Produces: `FileService.store(InputStream,String,String,long): FileObject` and signed download endpoint `/api/v1/files/{id}/download`.
- Produces: `AuditService.record(actor,action,resourceType,resourceId,details)`.

- [ ] **Step 1: Write failing product-version and MinIO metadata tests**

Test that a product version cannot belong to another product and that storing a file persists checksum, object key, size, MIME and version without storing bytes in MySQL.

- [ ] **Step 2: Run focused tests**

Run: `cd backend && mvn -q -Dtest=ProductCatalogIT,FileServiceTest test`

Expected: FAIL because catalog and storage modules are absent.

- [ ] **Step 3: Implement catalog CRUD, MinIO adapter, file versioning, MIME/size validation and audit aspect/service**

Object keys use `organization/{yyyy}/{MM}/{uuid}`. Maximum file size comes from configuration; default 100 MB. Signed URLs expire after 10 minutes.

```java
public interface FileService {
  FileObjectView store(InputStream content, String fileName, String mimeType, long size);
  URI signedDownload(long fileId, Duration ttl);
  FileObjectView addVersion(long fileId, InputStream content, long size, String checksum);
}
```

- [ ] **Step 4: Run focused and regression tests**

Run: `cd backend && mvn -q test`

Expected: PASS; no file bytes appear in database entities.

- [ ] **Step 5: Commit**

```bash
git add backend/src
git commit -m "feat: add catalog storage and audit"
```

---

### Task 4: React 工程、飞书式应用壳与鉴权

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/app/App.tsx`
- Create: `frontend/src/app/router.tsx`
- Create: `frontend/src/app/theme.ts`
- Create: `frontend/src/app/AuthProvider.tsx`
- Create: `frontend/src/components/AppShell.tsx`
- Create: `frontend/src/components/PageState.tsx`
- Create: `frontend/src/styles/global.css`
- Test: `frontend/src/components/AppShell.test.tsx`
- Test: `frontend/src/app/AuthProvider.test.tsx`

**Interfaces:**
- Consumes: `/api/v1/auth/me`, login and logout APIs from Task 2.
- Produces: route guard `RequirePermission`, shared `AppShell`, `PageState`, API client and module route entries.

- [ ] **Step 1: Write failing app-shell and route-guard tests**

```tsx
it('shows only permitted module entries', async () => {
  renderWithAuth(<AppShell />, { permissions: ['project:read'] })
  expect(screen.getByText('项目空间')).toBeVisible()
  expect(screen.queryByText('资源中心')).not.toBeInTheDocument()
})
```

- [ ] **Step 2: Run tests before implementation**

Run: `cd frontend && pnpm test --run`

Expected: FAIL because the React application does not exist.

- [ ] **Step 3: Implement Vite React app, Ant Design theme, QueryClient, typed fetch client, local login screen, permission routes and shallow double-sidebar shell**

Theme primary is `#3370ff`; page background `#f5f6f7`; no dark main sidebar. Module routes are `/dashboard`, `/projects`, `/requirements`, `/standardization`, `/knowledge`, `/resources`, `/admin`.

```tsx
export const theme: ThemeConfig = {
  token: { colorPrimary: '#3370ff', colorBgLayout: '#f5f6f7', borderRadius: 6 },
}

export function RequirePermission({ code, children }: Props) {
  const { me } = useAuth()
  return me.permissions.includes(code) ? children : <Navigate to="/403" replace />
}
```

- [ ] **Step 4: Run tests and production build**

Run: `cd frontend && pnpm test --run && pnpm build`

Expected: PASS and `dist/` generated without TypeScript errors.

- [ ] **Step 5: Commit**

```bash
git add frontend
git commit -m "feat: add React application shell"
```

---

### Task 5: M2 项目空间六功能

**Files:**
- Create: `backend/src/main/java/com/zhilu/delivery/project/`
- Create: `backend/src/main/resources/db/migration/V4__project_space.sql`
- Create: `frontend/src/modules/project/`
- Test: `backend/src/test/java/com/zhilu/delivery/project/ProjectLifecycleTest.java`
- Test: `backend/src/test/java/com/zhilu/delivery/project/ProjectApiIT.java`
- Test: `frontend/src/modules/project/ProjectWorkspace.test.tsx`

**Interfaces:**
- Produces: project CRUD, members, stages, risks, milestones, templates and artifacts under `/api/v1/projects`.
- Produces: `ProjectService.create(CreateProjectCommand): ProjectView` and `advanceStage(projectId,targetStage,mode)`.
- Produces: React routes `/projects`, `/projects/:id`, `/projects/:id/templates`, `/projects/:id/settings`.

- [ ] **Step 1: Write failing lifecycle tests**

```java
@Test public void projectStartsWithSevenOrderedStages() {
  ProjectView p = service.create(command());
  assertEquals(Arrays.asList("启动","需求采集","二开实施","上线切换","试运行与移交","标准化评估","项目收尾"),
      p.getStages().stream().map(StageView::getName).collect(toList()));
}
```

Also test blocking gate rejects advance with 409 and warning gate records an activity while advancing.

- [ ] **Step 2: Run focused tests**

Run: `cd backend && mvn -q -Dtest='*Project*' test`

Expected: FAIL because project module does not exist.

- [ ] **Step 3: Implement project aggregate, state transitions, member data scope, risk register, milestones, Markdown template instances, settings and activity records**

All project edits require `project:write` plus membership or cross-project scope. Template saves use optimistic locking.

```java
public enum DeliveryStage {
  START, REQUIREMENT, CUSTOM_DEV, GO_LIVE, TRIAL_HANDOVER, STANDARDIZATION, CLOSE
}

@Transactional
public ProjectView advanceStage(long projectId, DeliveryStage target, GateMode mode) {
  DeliveryProject project = projects.getForUpdate(projectId);
  gates.assertAdvanceAllowed(project, target, mode);
  project.advanceTo(target);
  activities.stageAdvanced(projectId, target);
  return mapper.view(project);
}
```

- [ ] **Step 4: Add project list/detail/lifecycle/risk/milestone/template/settings React pages and tests**

Run: `cd frontend && pnpm test --run src/modules/project`

Expected: PASS; loading, empty, error, forbidden and optimistic-lock conflict states render.

- [ ] **Step 5: Run backend and frontend regressions, then commit**

```bash
cd backend && mvn -q test
cd ../frontend && pnpm test --run && pnpm build
git add backend frontend
git commit -m "feat: deliver project workspace"
```

---

### Task 6: Agent、AI 与 M2 Skill 执行面板

**Files:**
- Create: `backend/src/main/java/com/zhilu/delivery/automation/`
- Create: `backend/src/main/resources/db/migration/V5__automation.sql`
- Create: `mock-agent/server.mjs`
- Create: `frontend/src/components/AgentExecutionPanel.tsx`
- Create: `docs/api/agent-contract.md`
- Test: `backend/src/test/java/com/zhilu/delivery/automation/AgentJobServiceTest.java`
- Test: `backend/src/test/java/com/zhilu/delivery/automation/AgentCallbackIT.java`
- Test: `frontend/src/components/AgentExecutionPanel.test.tsx`

**Interfaces:**
- Produces: `POST /api/v1/projects/{id}/agent-jobs`, `GET /api/v1/agent-jobs/{id}`, cancel and callback endpoints.
- Produces: `AiClient.completeJson(systemPrompt,userPrompt,schema): JsonNode`.
- Produces: six skills `deliver-init`, `deliver-require`, `deliver-dev`, `deliver-transition`, `deliver-standardize`, `deliver-close`.

- [ ] **Step 1: Write failing job state, callback idempotency and HMAC tests**

```java
@Test public void duplicateCallbackIsAppliedOnce() {
  service.accept(event("evt-1", "SUCCEEDED"));
  service.accept(event("evt-1", "SUCCEEDED"));
  assertEquals(1, artifactRepository.countByJobId(jobId));
}
```

- [ ] **Step 2: Run focused automation tests**

Run: `cd backend && mvn -q -Dtest='*Agent*' test`

Expected: FAIL because automation module is absent.

- [ ] **Step 3: Implement durable Job records, Agent HTTP client, HMAC validation, poll/callback merge, finite retry, timeout scanner, MinIO artifact binding and OpenAI-compatible AI client**

Network/5xx retries use 1s/3s/9s and stop after three attempts. Business 4xx are terminal. AI missing configuration raises `AI_NOT_CONFIGURED`.

```java
public interface AgentGateway {
  AgentSubmission submit(AgentRequest request);
  AgentStatus status(String externalJobId);
  void cancel(String externalJobId);
}

public interface AiClient {
  JsonNode completeJson(String systemPrompt, String userPrompt, JsonNode schema);
}
```

- [ ] **Step 4: Implement zero-dependency Mock Agent and React execution panel**

Mock accepts jobs, reports deterministic progress, emits success/failure/timeout based on request `scenario`, and returns Markdown artifacts. React polls every two seconds while non-terminal and stops immediately at terminal state.

- [ ] **Step 5: Run automation tests, Node syntax check and frontend tests, then commit**

```bash
cd backend && mvn -q -Dtest='*Agent*','*Automation*' test
node --check ../mock-agent/server.mjs
cd ../frontend && pnpm test --run AgentExecutionPanel
git add backend frontend mock-agent docs/api
git commit -m "feat: add agent and AI integrations"
```

---

### Task 7: M1 驾驶舱四功能

**Files:**
- Create: `backend/src/main/java/com/zhilu/delivery/dashboard/`
- Create: `frontend/src/modules/dashboard/`
- Test: `backend/src/test/java/com/zhilu/delivery/dashboard/DashboardQueryIT.java`
- Test: `frontend/src/modules/dashboard/DashboardPage.test.tsx`

**Interfaces:**
- Consumes: project, risk, requirement summary, product and resource data.
- Produces: `/api/v1/dashboard/summary`, `/projects`, `/risk-heatmap`, `/matrix`.
- Produces: list/card toggle persisted in browser local storage.

- [ ] **Step 1: Write failing aggregate query tests**

Seed red/yellow/green projects and assert risk order, health score, product grouping and permission-filtered counts.

- [ ] **Step 2: Run dashboard tests and verify failure**

Run: `cd backend && mvn -q -Dtest=DashboardQueryIT test`

Expected: FAIL because dashboard projections are absent.

- [ ] **Step 3: Implement read-only dashboard queries and create-project wizard calling Task 5 then optional `deliver-init`**

Use SQL projections; do not duplicate project rows into dashboard tables.

```java
public interface DashboardQueryService {
  DashboardSummary summary(CurrentUser user, DashboardFilter filter);
  List<ProjectRow> projects(CurrentUser user, DashboardFilter filter);
  List<RiskHeatmapRow> riskHeatmap(CurrentUser user);
  List<ProductMatrixRow> matrix(CurrentUser user);
}
```

- [ ] **Step 4: Implement KPI bar, filters, dense table, optional cards, heatmap and product×project matrix using real API data**

Run: `cd frontend && pnpm test --run src/modules/dashboard`

Expected: PASS with list as default and cards retained.

- [ ] **Step 5: Commit after full regression**

```bash
git add backend frontend
git commit -m "feat: deliver project dashboard"
```

---

### Task 8: M3 需求工坊五功能

**Files:**
- Create: `backend/src/main/java/com/zhilu/delivery/requirement/`
- Create: `backend/src/main/resources/db/migration/V6__requirements.sql`
- Create: `frontend/src/modules/requirement/`
- Test: `backend/src/test/java/com/zhilu/delivery/requirement/ClassificationServiceTest.java`
- Test: `backend/src/test/java/com/zhilu/delivery/requirement/RequirementApiIT.java`
- Test: `frontend/src/modules/requirement/RequirementWorkshop.test.tsx`

**Interfaces:**
- Produces: requirement CRUD, classify, confirm/override, merge, list/board and funnel APIs.
- Produces: `ClassificationDecision` as the only source used by statistics.
- Produces: confirmed L1 records and `CustomDevTask` queries consumed by later standardization evaluation.

- [ ] **Step 1: Write failing classification and funnel tests**

Test AI recommendation alone does not alter the funnel; confirmation does; override requires a reason; merge preserves source traceability.

- [ ] **Step 2: Run focused tests**

Run: `cd backend && mvn -q -Dtest='*Requirement*','*Classification*' test`

Expected: FAIL because requirement module is absent.

- [ ] **Step 3: Implement requirement aggregate, AI JSON schema validation, manual fallback, confirmed decision, duplicate scoring, merge transaction and L0/L1/L2 aggregation**

If AI is not configured, users can still manually classify. Requirements shorter than 10 Chinese characters return a validation warning but remain savable as drafts.

```java
@Transactional
public ClassificationDecisionView confirm(long requirementId, Level level, String overrideReason) {
  Requirement requirement = requirements.getForUpdate(requirementId);
  requirement.confirm(level, overrideReason);
  if (level == Level.L1) customDevTasks.createIfAbsent(requirement);
  return mapper.decision(requirement);
}
```

- [ ] **Step 4: Implement collection form, AI decision drawer, funnel charts, duplicate merge UI and list/board**

Run: `cd frontend && pnpm test --run src/modules/requirement`

Expected: PASS for AI configured, AI unavailable, confirm and override paths.

- [ ] **Step 5: Commit**

```bash
git add backend frontend
git commit -m "feat: deliver requirement workshop"
```

---

### Task 9: M4 标准化中心六功能

**Files:**
- Create: `backend/src/main/java/com/zhilu/delivery/standardization/`
- Create: `backend/src/main/resources/db/migration/V7__standardization.sql`
- Create: `frontend/src/modules/standardization/`
- Test: `backend/src/test/java/com/zhilu/delivery/standardization/StandardizationServiceTest.java`
- Test: `frontend/src/modules/standardization/StandardizationPage.test.tsx`

**Interfaces:**
- Consumes: confirmed requirement decisions, custom-dev effort, product/version catalog.
- Produces: baseline card, maturity, deviation, debt lifecycle, cost attribution and flywheel APIs.

- [ ] **Step 1: Write failing maturity, debt and flywheel tests**

Test same extension point used by at least five distinct projects creates one candidate; repeated evaluation is idempotent; debt cannot close before verification.

- [ ] **Step 2: Run focused tests**

Run: `cd backend && mvn -q -Dtest='*Standardization*' test`

Expected: FAIL because standardization module is absent.

- [ ] **Step 3: Implement three-dimensional baseline card, deterministic maturity scoring, deviation ratios, debt state machine, effort/cost aggregation and flywheel period metrics**

All scoring formulas live in named domain services with unit tests; controllers do not calculate metrics.

```java
public DebtCandidate evaluate(DebtKey key, Collection<ConfirmedL1> usages) {
  long projects = usages.stream().map(ConfirmedL1::getProjectId).distinct().count();
  return projects >= 5 ? DebtCandidate.create(key, projects) : DebtCandidate.none(key);
}
```

- [ ] **Step 4: Implement capability editor, assessment, deviation, debt, attribution and flywheel pages**

Run: `cd frontend && pnpm test --run src/modules/standardization`

Expected: PASS; charts have accompanying numeric tables for accessibility.

- [ ] **Step 5: Commit**

```bash
git add backend frontend
git commit -m "feat: deliver standardization center"
```

---

### Task 10: M5 知识库三功能

**Files:**
- Create: `backend/src/main/java/com/zhilu/delivery/knowledge/`
- Create: `backend/src/main/resources/db/migration/V8__knowledge.sql`
- Create: `frontend/src/modules/knowledge/`
- Test: `backend/src/test/java/com/zhilu/delivery/knowledge/KnowledgeSearchIT.java`
- Test: `frontend/src/modules/knowledge/KnowledgePage.test.tsx`

**Interfaces:**
- Produces: unified search/filter API and CRUD for examples, code snippets and training materials.
- Consumes: Task 3 file service and product/version tags.

- [ ] **Step 1: Write failing type/tag/keyword search tests**

Seed Chinese titles and tags, then assert keyword, product, industry, stage and lifecycle filters compose correctly and respect permissions.

- [ ] **Step 2: Run focused tests**

Run: `cd backend && mvn -q -Dtest='*Knowledge*' test`

Expected: FAIL because knowledge module is absent.

- [ ] **Step 3: Implement unified knowledge model, type-specific detail tables, MySQL indexed search, lifecycle status and MinIO attachments**

Do not add Elasticsearch at this scale. Matching for reuse uses normalized keyword/tag overlap plus optional AI reranking.

```java
public Page<KnowledgeSummary> search(KnowledgeQuery query, Pageable pageable) {
  Specification<KnowledgeItem> spec = KnowledgeSpecs.keyword(query.getKeyword())
      .and(KnowledgeSpecs.type(query.getType()))
      .and(KnowledgeSpecs.tags(query.getTags()))
      .and(KnowledgeSpecs.lifecycle(query.getLifecycle()));
  return repository.findAll(spec, pageable).map(mapper::summary);
}
```

- [ ] **Step 4: Implement search, filters, detail drawer, upload/edit and three content tabs**

Run: `cd frontend && pnpm test --run src/modules/knowledge`

Expected: PASS for empty, result, expired item and upload-error states.

- [ ] **Step 5: Commit**

```bash
git add backend frontend
git commit -m "feat: deliver knowledge center"
```

---

### Task 11: M6 资源中心四功能

**Files:**
- Create: `backend/src/main/java/com/zhilu/delivery/resource/`
- Create: `backend/src/main/resources/db/migration/V9__resources.sql`
- Create: `frontend/src/modules/resource/`
- Test: `backend/src/test/java/com/zhilu/delivery/resource/ResourceConflictServiceTest.java`
- Test: `frontend/src/modules/resource/ResourceCenter.test.tsx`

**Interfaces:**
- Produces: engineer profiles, assignments, conflict detection and load APIs.
- Consumes: IAM users/teams, product catalog and project dates.
- Emits: project-member changes back to project activity log through an application service.

- [ ] **Step 1: Write failing load and recommendation tests**

Test sum above 100% is blocking, 90–100% is warning, date ranges outside the project do not count, and replacement order is 60% product familiarity + 30% availability + 10% level.

- [ ] **Step 2: Run focused tests**

Run: `cd backend && mvn -q -Dtest='*Resource*' test`

Expected: FAIL because resource module is absent.

- [ ] **Step 3: Implement profile, product familiarity, dated assignment, load aggregation, conflict snapshots and deterministic recommendation scoring**

Assignment writes and project membership updates share one MySQL transaction.

```java
public int recommendationScore(Familiarity familiarity, int freePercent, Seniority level) {
  return familiarity.score() * 60 / 100
      + freePercent * 30 / 100
      + level.score() * 10 / 100;
}
```

- [ ] **Step 4: Implement personnel wall, assignment editor, conflict view and load dashboard**

Run: `cd frontend && pnpm test --run src/modules/resource`

Expected: PASS for normal, warning, blocking and no-replacement states.

- [ ] **Step 5: Commit**

```bash
git add backend frontend
git commit -m "feat: deliver resource center"
```

---

### Task 12: 演示数据、Docker Compose 与运维文档

**Files:**
- Create: `backend/src/main/resources/demo-data.sql`
- Create: `infra/docker-compose.yml`
- Create: `infra/nginx/default.conf`
- Create: `backend/Dockerfile`
- Create: `frontend/Dockerfile`
- Create: `mock-agent/Dockerfile`
- Create: `.env.example`
- Create: `README.md`
- Create: `docs/deployment.md`
- Test: `scripts/smoke.sh`

**Interfaces:**
- Consumes: all application services and health endpoints.
- Produces: one-command local deployment and deterministic demo users/projects mirroring the original prototype.

- [ ] **Step 1: Write a failing smoke script**

```sh
#!/bin/sh
set -eu
curl -fsS http://localhost:8080/actuator/health | grep '"status":"UP"'
curl -fsS http://localhost:8090/health | grep '"status":"UP"'
curl -fsS http://localhost/ | grep '智鹿交付'
```

- [ ] **Step 2: Run smoke before Compose exists**

Run: `sh scripts/smoke.sh`

Expected: FAIL because services are not running.

- [ ] **Step 3: Add multi-stage images, Compose health checks, persistent volumes, `.env.example`, demo profile and seed data**

Demo data includes five products, seven projects, eight members, six requirements, ten templates and all six roles. Default admin credentials are required via environment; no fixed production password is embedded.

```yaml
services:
  api:
    build: ../backend
    depends_on:
      mysql: { condition: service_healthy }
      redis: { condition: service_healthy }
      minio: { condition: service_healthy }
  web:
    build: ../frontend
    ports: ["80:80"]
  mock-agent:
    build: ../mock-agent
```

- [ ] **Step 4: Build and smoke test the stack**

Run: `docker compose -f infra/docker-compose.yml up -d --build && sh scripts/smoke.sh`

Expected: all three health checks PASS.

- [ ] **Step 5: Commit**

```bash
git add infra backend/Dockerfile frontend/Dockerfile mock-agent .env.example README.md docs/deployment.md scripts
git commit -m "chore: add deployable demo stack"
```

---

### Task 13: 核心端到端验收与最终回归

**Files:**
- Create: `frontend/playwright.config.ts`
- Create: `frontend/e2e/core-flow.spec.ts`
- Create: `frontend/e2e/permissions.spec.ts`
- Create: `frontend/e2e/agent-failure.spec.ts`
- Modify: `README.md`

**Interfaces:**
- Consumes: complete deployed stack.
- Produces: repeatable evidence for core closed loop, role permissions and Agent failure recovery.

- [ ] **Step 1: Write end-to-end tests before running them**

`core-flow.spec.ts` logs in, creates a project, verifies seven stages, adds and manually confirms a requirement, runs Mock `deliver-require`, sees the artifact, assigns a member and verifies the dashboard.

`permissions.spec.ts` verifies a delivery engineer cannot open admin or cross-project data.

`agent-failure.spec.ts` triggers Mock failure, verifies retry, then succeeds without duplicate artifacts.

- [ ] **Step 2: Run E2E acceptance tests**

Run: `cd frontend && pnpm exec playwright test`

Expected: all core, permissions and Agent failure-recovery cases PASS. Any failure blocks completion and is fixed in the owning module before this task continues.

- [ ] **Step 3: Run complete verification**

```bash
cd backend && mvn -q test
cd ../frontend && pnpm test --run && pnpm build && pnpm exec playwright test
cd .. && docker compose -f infra/docker-compose.yml config && sh scripts/smoke.sh
```

Expected: every command exits 0; Playwright reports all core, permissions and failure-recovery cases passed.

- [ ] **Step 4: Update README verification evidence and commit**

```bash
git add frontend README.md
git commit -m "test: verify complete delivery platform"
```
