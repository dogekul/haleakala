# Customer Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有客户管理之上交付商机、售前、实施协同、实施驾驶舱和客户运营五个工作区，并让赢单商机原子转交现有项目、项目收尾幂等生成运营单。

**Architecture:** `opportunity` 包唯一持有五阶段售前状态，`project` 包继续唯一持有七阶段实施状态，`operation` 包持有收尾后的客户运营状态。前端用一个客户中心路由壳复用统一页签，客户管理仍调用既有模块，其余页面通过 `crm:read` / `crm:write` 独立鉴权。实现坚持 JdbcTemplate、事务、组织隔离、乐观锁和唯一约束，不引入流程引擎或第二套实施数据。

**Tech Stack:** Java 8、Spring Boot 2.7、Spring Security、JdbcTemplate、Flyway、MySQL/H2、React 18、TypeScript、Ant Design、React Query、React Router、Vitest、Playwright。

## Global Constraints

- 不修改 `customer` 表及 `/api/v1/customers` 的业务契约；只复用 `CustomerService.get` 并校验 `status=ACTIVE`。
- 不在 CRM 保存实施阶段、风险、里程碑或交付产出物副本；实施页面只读现有项目表。
- 所有跨实体读取都显式校验 `organization_id`，跨组织资源统一按 404 处理。
- 状态、项目关联和运营来源关联只能通过专用动作改变，不能从通用更新接口写入。
- 每个行为先写失败测试，再写最小实现；每完成一个任务执行指定测试并单独提交。
- 不新增依赖。前端继续使用已安装的 Ant Design、React Query 和 React Router。

---

### Task 1: 建立 CRM 数据约束与权限基线

**Files:**

- Create: `backend/src/main/resources/db/migration/V13__customer_lifecycle.sql`
- Modify: `backend/src/test/java/com/zhilu/delivery/SchemaBaselineTest.java`

- [ ] **Step 1: 写失败的迁移契约测试**

在 `SchemaBaselineTest` 新增 `customerLifecycleSchemaAndPermissionsAreInstalled()`，断言四张表、关键唯一约束和角色权限：

```java
@Test
void customerLifecycleSchemaAndPermissionsAreInstalled() {
  assertEquals(Integer.valueOf(4), jdbc.queryForObject(
      "select count(*) from information_schema.tables where table_schema='public' "
          + "and table_name in ('sales_opportunity','opportunity_activity',"
          + "'opportunity_artifact','customer_operation')", Integer.class));
  assertEquals(Integer.valueOf(2), jdbc.queryForObject(
      "select count(*) from permission where code in ('crm:read','crm:write')", Integer.class));
  assertEquals(Integer.valueOf(4), jdbc.queryForObject(
      "select count(*) from role_permission rp join role r on r.id=rp.role_id "
          + "join permission p on p.id=rp.permission_id "
          + "where r.code in ('ADMIN','PMO') and p.code in ('crm:read','crm:write')",
      Integer.class));
  assertEquals(Integer.valueOf(1), jdbc.queryForObject(
      "select count(*) from information_schema.table_constraints "
          + "where table_schema='public' and table_name='sales_opportunity' "
          + "and constraint_name='uk_opportunity_project'", Integer.class));
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && mvn -q -Dtest=SchemaBaselineTest test`

Expected: FAIL，缺少 `V13` 表和权限。

- [ ] **Step 3: 写最小迁移**

`V13__customer_lifecycle.sql` 创建：

```sql
CREATE TABLE sales_opportunity (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  customer_name_snapshot VARCHAR(180) NOT NULL,
  title VARCHAR(180) NOT NULL,
  note TEXT NULL,
  amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  product_id BIGINT NULL,
  product_version_id BIGINT NULL,
  commercial_owner_user_id BIGINT NULL,
  solution_owner_user_id BIGINT NULL,
  project_manager_user_id BIGINT NULL,
  operation_owner_user_id BIGINT NULL,
  stage VARCHAR(24) NOT NULL DEFAULT 'LEAD',
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  project_id BIGINT NULL,
  stage_entered_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_opportunity_project UNIQUE (project_id),
  CONSTRAINT fk_opportunity_org FOREIGN KEY (organization_id) REFERENCES organization(id),
  CONSTRAINT fk_opportunity_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
  CONSTRAINT fk_opportunity_product FOREIGN KEY (product_id) REFERENCES product(id),
  CONSTRAINT fk_opportunity_version FOREIGN KEY (product_version_id) REFERENCES product_version(id),
  CONSTRAINT fk_opportunity_project FOREIGN KEY (project_id) REFERENCES delivery_project(id),
  CONSTRAINT fk_opportunity_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
);
```

同一迁移继续创建 `opportunity_activity`、`opportunity_artifact`、`customer_operation` 的设计字段和外键；为商机建立 `(organization_id,status,stage)`、`customer_id`、四类负责人索引，为活动/产出物建立 `(opportunity_id,stage_code/stage_from)` 索引，为运营建立 `(organization_id,status,stage)` 索引；增加 `uk_operation_opportunity(opportunity_id)`。插入 `crm:read`、`crm:write`，并授予 `ADMIN`、`PMO`。

- [ ] **Step 4: 运行迁移测试**

Run: `cd backend && mvn -q -Dtest=SchemaBaselineTest test`

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/resources/db/migration/V13__customer_lifecycle.sql backend/src/test/java/com/zhilu/delivery/SchemaBaselineTest.java
git commit -m "feat: add customer lifecycle schema"
```

### Task 2: 实现商机基础资料、查询与负责人选项

**Files:**

- Create: `backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityStage.java`
- Create: `backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityService.java`
- Create: `backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityController.java`
- Create: `backend/src/main/java/com/zhilu/delivery/opportunity/CrmQueryController.java`
- Create: `backend/src/test/java/com/zhilu/delivery/opportunity/OpportunityApiIT.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/config/SecurityConfig.java`

- [ ] **Step 1: 写创建、查询、编辑和组织隔离的失败集成测试**

使用 `@SpringBootTest`、`@AutoConfigureMockMvc`、`@WithMockUser` 与现有登录/session helper，覆盖：

```java
mockMvc.perform(post("/api/v1/opportunities").with(csrf()).session(session)
    .contentType(APPLICATION_JSON)
    .content("{\"customerId\":1,\"title\":\"财务中台升级\",\"amount\":800000," 
        + "\"commercialOwnerUserId\":1}"))
    .andExpect(status().isCreated())
    .andExpect(jsonPath("$.stage").value("LEAD"))
    .andExpect(jsonPath("$.status").value("OPEN"))
    .andExpect(jsonPath("$.customerName").value("华东银行"));
```

继续断言停用客户返回 400、跨组织客户/负责人/产品返回 404、产品版本不属于产品返回 400、旧 `version` 更新返回 409、通用更新不能修改 `stage/status/projectId`、筛选只返回当前组织数据、`GET /api/v1/crm/owner-options` 只返回本组织启用用户。

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd backend && mvn -q -Dtest=OpportunityApiIT test`

Expected: FAIL，接口不存在。

- [ ] **Step 3: 写售前阶段枚举**

```java
public enum OpportunityStage {
  LEAD, OPPORTUNITY, POC, BIDDING, CONTRACT;

  public OpportunityStage next() {
    if (this == CONTRACT) throw new ConflictException("合同阶段必须转交实施或丢单");
    return values()[ordinal() + 1];
  }
}
```

- [ ] **Step 4: 写最小 JdbcTemplate 服务和控制器**

`OpportunityService` 暴露：

```java
List<Map<String, Object>> list(long organizationId, OpportunityFilter filter);
Map<String, Object> get(long organizationId, long id);
Map<String, Object> create(long organizationId, long actorId, OpportunityInput input);
Map<String, Object> update(long organizationId, long id, long version, OpportunityInput input);
```

`create` 调用 `CustomerService.get`，要求 `ACTIVE`，所有负责人用 `app_user.organization_id/status` 校验；产品与版本用带组织条件的联表校验；查询使用 `coalesce(c.name,o.customer_name_snapshot)`、产品名和负责人名 enrich。`update` SQL 必须带 `where id=? and organization_id=? and version=?`，字段列表不含阶段、状态、项目。

`SecurityConfig` 在 customers 规则之前增加：

```java
.antMatchers(HttpMethod.GET, "/api/v1/opportunities/**", "/api/v1/operations/**",
    "/api/v1/crm/**").hasAuthority("crm:read")
.antMatchers("/api/v1/opportunities/**", "/api/v1/operations/**")
    .hasAuthority("crm:write")
```

每个写接口在同一事务调用 `AuditService.record`，资源类型分别为 `OPPORTUNITY` / `OPPORTUNITY_ACTIVITY` / `OPPORTUNITY_ARTIFACT`。

- [ ] **Step 5: 运行测试**

Run: `cd backend && mvn -q -Dtest=OpportunityApiIT,SecurityAccessTest test`

Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/zhilu/delivery/opportunity backend/src/main/java/com/zhilu/delivery/config/SecurityConfig.java backend/src/test/java/com/zhilu/delivery/opportunity
git commit -m "feat: add opportunity management api"
```

### Task 3: 实现活动、产出物与售前门禁状态机

**Files:**

- Modify: `backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityController.java`
- Create: `backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityGate.java`
- Create: `backend/src/test/java/com/zhilu/delivery/opportunity/OpportunityLifecycleIT.java`

- [ ] **Step 1: 写五阶段、门禁与终态的失败测试**

测试逐阶段添加材料并推进：

```java
assertAdvanceFails(id, "缺少商机调研报告");
addReport(id, "RESEARCH_REPORT", "调研报告", "## 结论\n需求明确");
assertStage(advance(id, null), "OPPORTUNITY");

addReport(id, "DECISION_MINUTES", "评审纪要", "同意进入 POC");
assertStage(advance(id, "PASS"), "POC");
```

继续覆盖：报告必须有 Markdown、文件材料必须引用同组织 `file_object`、产出物只追加；活动只能在当前商机阶段创建，`TODO ↔ DONE` 使用版本锁；`OPPORTUNITY/BIDDING/CONTRACT` 的 `REJECT` 变为 `LOST`；丢单或赢单后所有推进返回 409；不能跳级。

- [ ] **Step 2: 运行失败测试**

Run: `cd backend && mvn -q -Dtest=OpportunityLifecycleIT test`

Expected: FAIL，活动/产出物/推进接口不存在。

- [ ] **Step 3: 用常量映射实现固定门禁**

```java
private static final Map<OpportunityStage, List<String>> REQUIRED = gates();

private static Map<OpportunityStage, List<String>> gates() {
  Map<OpportunityStage, List<String>> values = new EnumMap<OpportunityStage, List<String>>(OpportunityStage.class);
  values.put(OpportunityStage.LEAD, Arrays.asList("RESEARCH_REPORT"));
  values.put(OpportunityStage.OPPORTUNITY, Arrays.asList("DECISION_MINUTES"));
  values.put(OpportunityStage.POC, Arrays.asList("PRESENTATION","CLIENT_REQUESTS","POC_SCORE","GAP_ANALYSIS"));
  values.put(OpportunityStage.BIDDING, Arrays.asList("BID_DOCUMENT"));
  values.put(OpportunityStage.CONTRACT, Arrays.asList("AWARD_NOTICE","CONTRACT","REVIEW_MINUTES","EMAIL_ARCHIVE","SEALED_CONTRACT"));
  return Collections.unmodifiableMap(values);
}
```

`OpportunityGate` 只负责 `missingArtifacts(opportunityId, stage, decision)` 与报告/文件类型校验。`advance` 加 `@Transactional`，按数据库当前 `stage/status/version` 重新读取，校验决策与门禁后单条更新 `stage/status/stage_entered_at/version` 并审计。`CONTRACT/PASS` 明确拒绝，提示使用 `/handoff`。

- [ ] **Step 4: 实现活动与产出物接口**

实现设计中的 `GET/POST activities`、`PUT activity`、`GET/POST artifacts`；每条查询同时限制 `organization_id` 和 `opportunity_id`。产出物响应 enrich 文件原名，活动响应返回 `stageCode/status/completedAt/version`。

- [ ] **Step 5: 运行测试**

Run: `cd backend && mvn -q -Dtest=OpportunityLifecycleIT,OpportunityApiIT test`

Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/zhilu/delivery/opportunity backend/src/test/java/com/zhilu/delivery/opportunity
git commit -m "feat: add presale lifecycle gates"
```

### Task 4: 实现合同赢单到现有项目的原子转交

**Files:**

- Modify: `backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityController.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/project/ProjectService.java`
- Create: `backend/src/test/java/com/zhilu/delivery/opportunity/OpportunityHandoffIT.java`

- [ ] **Step 1: 写创建项目和关联项目两条失败测试**

请求采用明确的判别字段：

```json
{
  "mode": "CREATE",
  "version": 4,
  "project": {
    "code": "PRJ-CRM-001",
    "name": "财务中台实施",
    "productId": 1,
    "productVersionId": 1,
    "managerUserId": 1,
    "startDate": "2026-07-16",
    "plannedEndDate": "2026-10-31",
    "gateMode": "BLOCK"
  }
}
```

以及：

```json
{ "mode": "LINK", "version": 4, "projectId": 88 }
```

断言成功后商机为 `CONTRACT/WON` 且只有一个 `projectId`；新项目客户来自商机；关联项目必须同组织、同客户且未被其他商机引用。缺合同材料、项目创建失败、旧版本、重复请求都不能留下 `WON` 无项目或重复项目。

- [ ] **Step 2: 运行失败测试**

Run: `cd backend && mvn -q -Dtest=OpportunityHandoffIT test`

Expected: FAIL，`/handoff` 不存在。

- [ ] **Step 3: 实现事务转交**

在 `OpportunityService.handoff(...)` 上加 `@Transactional`：锁定/重新读取商机，要求 `CONTRACT/OPEN` 与版本匹配；要求 `PASS` 门禁完整；`CREATE` 调用：

```java
ProjectView project = projects.create(new CreateProjectCommand(
    organizationId, request.project.code, request.project.name, customerId,
    request.project.productId, request.project.productVersionId,
    request.project.managerUserId, actorId, request.project.startDate,
    request.project.plannedEndDate, request.project.gateMode));
```

`LINK` 先通过新增的 `ProjectService.getForOrganization(projectId, organizationId)` 校验组织与客户，再查询 `sales_opportunity.project_id` 冲突。最后以 `where id=? and organization_id=? and version=? and status='OPEN' and project_id is null` 更新为 `WON`。捕获唯一键冲突转成 409。

- [ ] **Step 4: 运行测试**

Run: `cd backend && mvn -q -Dtest=OpportunityHandoffIT,ProjectApiIT,ProjectAuthorizationIT test`

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/zhilu/delivery/opportunity backend/src/main/java/com/zhilu/delivery/project/ProjectService.java backend/src/test/java/com/zhilu/delivery/opportunity
git commit -m "feat: hand off won opportunities to projects"
```

### Task 5: 实现实施协同、驾驶舱与全链只读模型

**Files:**

- Create: `backend/src/main/java/com/zhilu/delivery/opportunity/CrmImplementationQueryService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/opportunity/CrmQueryController.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityController.java`
- Create: `backend/src/test/java/com/zhilu/delivery/opportunity/CrmImplementationQueryIT.java`

- [ ] **Step 1: 写真实项目聚合的失败测试**

构造已转交商机、七阶段项目、开放风险和逾期里程碑，断言：

```java
mockMvc.perform(get("/api/v1/crm/implementation").session(session))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$[0].projectStage").value("CUSTOM_DEV"))
    .andExpect(jsonPath("$[0].riskLevel").value("RED"))
    .andExpect(jsonPath("$[0].openRiskCount").value(1));
mockMvc.perform(get("/api/v1/crm/implementation-cockpit").session(session))
    .andExpect(jsonPath("$.overdueMilestones").value(1))
    .andExpect(jsonPath("$.items[0].projectStage").value("CUSTOM_DEV"));
```

同时断言跨组织项目不返回，商机表没有实施阶段字段，`full-link` 按顺序返回 customer/opportunity/project/operation 节点且不存在的后续节点标为空。

- [ ] **Step 2: 运行失败测试**

Run: `cd backend && mvn -q -Dtest=CrmImplementationQueryIT test`

Expected: FAIL，聚合接口不存在。

- [ ] **Step 3: 写只读聚合 SQL**

`CrmImplementationQueryService` 以 `sales_opportunity o join delivery_project p on p.id=o.project_id` 为主查询，关联客户、用户、`project_risk` 和 `milestone` 聚合开放风险数、红色风险数、逾期里程碑数、最近里程碑；所有子查询都由主项目且主查询 `o.organization_id=? and p.organization_id=?` 约束。驾驶舱指标从同一 items 结果计算，避免第二套查询语义。

- [ ] **Step 4: 实现 full-link**

`GET /api/v1/opportunities/{id}/full-link` 先按组织读取商机，再按其外键聚合：

```json
{
  "customer": { "id": 1, "name": "华东银行" },
  "opportunity": { "id": 9, "title": "财务中台升级", "status": "WON" },
  "project": { "id": 88, "name": "财务中台实施", "stage": "CUSTOM_DEV" },
  "operation": null
}
```

- [ ] **Step 5: 运行测试并提交**

Run: `cd backend && mvn -q -Dtest=CrmImplementationQueryIT,OpportunityApiIT test`

Expected: PASS。

```bash
git add backend/src/main/java/com/zhilu/delivery/opportunity backend/src/test/java/com/zhilu/delivery/opportunity
git commit -m "feat: add crm implementation views"
```

### Task 6: 实现客户运营与项目收尾幂等钩子

**Files:**

- Create: `backend/src/main/java/com/zhilu/delivery/operation/CustomerOperationService.java`
- Create: `backend/src/main/java/com/zhilu/delivery/operation/CustomerOperationController.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/project/ProjectService.java`
- Create: `backend/src/test/java/com/zhilu/delivery/operation/CustomerOperationApiIT.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/project/ProjectLifecycleTest.java`

- [ ] **Step 1: 写运营 CRUD/推进与自动创建的失败测试**

覆盖启用客户手工创建、按组织筛选、基本资料乐观锁编辑、线性推进：

```text
MAINTENANCE/OPEN → OPERATING/OPEN → REPURCHASE/OPEN → CLOSED/CLOSED
```

项目从 `STANDARDIZATION` 推进 `CLOSE` 时，若有来源商机则自动创建一条 `MAINTENANCE/OPEN` 运营单；重复调用 `ensureForClosedProject` 或重试推进都只有一条。无来源商机的普通项目不自动创建。运营详情返回来源全链，关闭后编辑/推进返回 409。

- [ ] **Step 2: 运行失败测试**

Run: `cd backend && mvn -q -Dtest=CustomerOperationApiIT,ProjectLifecycleTest test`

Expected: FAIL，运营接口与收尾钩子不存在。

- [ ] **Step 3: 实现运营服务**

`CustomerOperationService` 暴露：

```java
List<Map<String, Object>> list(long organizationId, OperationFilter filter);
Map<String, Object> get(long organizationId, long id);
Map<String, Object> create(long organizationId, long actorId, OperationInput input);
Map<String, Object> update(long organizationId, long id, long version, OperationInput input);
Map<String, Object> advance(long organizationId, long id, long version, long actorId);
void ensureForClosedProject(long organizationId, long projectId, long actorId);
```

`ensureForClosedProject` 使用单条 `insert ... select` 从已赢单商机和项目复制客户/标题/负责人，并以 `not exists` 加 `uk_operation_opportunity` 双重幂等；必须加入项目推进事务。`ProjectService.advanceStage` 在成功更新到 `CLOSE` 后、返回前调用该方法。

- [ ] **Step 4: 实现控制器、审计和权限测试**

写接口资源类型 `CUSTOMER_OPERATION`；手工创建关联项目或商机时重新验证组织、客户一致性。GET 使用 `crm:read`，写使用 `crm:write`。

- [ ] **Step 5: 运行测试并提交**

Run: `cd backend && mvn -q -Dtest=CustomerOperationApiIT,ProjectLifecycleTest,ProjectApiIT test`

Expected: PASS。

```bash
git add backend/src/main/java/com/zhilu/delivery/operation backend/src/main/java/com/zhilu/delivery/project/ProjectService.java backend/src/test/java/com/zhilu/delivery/operation backend/src/test/java/com/zhilu/delivery/project/ProjectLifecycleTest.java
git commit -m "feat: add customer operations lifecycle"
```

### Task 7: 建立客户中心前端路由、类型和统一页签

**Files:**

- Create: `frontend/src/modules/customer-center/types.ts`
- Create: `frontend/src/modules/customer-center/crmApi.ts`
- Create: `frontend/src/modules/customer-center/CustomerCenterRoutes.tsx`
- Create: `frontend/src/modules/customer-center/CustomerCenterTabs.tsx`
- Create: `frontend/src/modules/customer-center/CustomerCenterRoutes.test.tsx`
- Modify: `frontend/src/app/App.tsx`
- Modify: `frontend/src/components/AppShell.tsx`
- Modify: `frontend/src/components/AppShell.test.tsx`
- Modify: `frontend/src/app/homeRoute.ts`
- Modify: `frontend/src/modules/customer/CustomerPage.tsx`

- [ ] **Step 1: 写路由、入口和权限的失败测试**

断言主导航入口改名“客户中心”，具备 `customer:read` 或 `crm:read` 任一权限均可见；客户管理页只要求 `customer:read`，其余页只要求 `crm:read`；没有 `crm:write` 时隐藏所有新增/推进按钮；页签顺序固定为客户管理、商机总览、售前推进、实施协同、实施驾驶舱、客户运营。

- [ ] **Step 2: 运行失败测试**

Run: `cd frontend && npm run test:run -- src/modules/customer-center/CustomerCenterRoutes.test.tsx src/components/AppShell.test.tsx`

Expected: FAIL，客户中心路由不存在且入口仍叫客户管理。

- [ ] **Step 3: 定义稳定的前端契约**

`types.ts` 定义 `OpportunityStage/Status`、`Opportunity`、`OpportunityActivity`、`OpportunityArtifact`、`ImplementationItem`、`ImplementationCockpit`、`CustomerOperation` 和 `FullLink`；字段必须与后端响应一一对应，禁止在组件中使用 `Record<string, unknown>` 代替核心模型。

`crmApi.ts` 封装所有设计接口，例如：

```ts
export const crmApi = {
  opportunities: (query = '') => api<Opportunity[]>(`/api/v1/opportunities${query}`),
  opportunity: (id: number) => api<Opportunity>(`/api/v1/opportunities/${id}`),
  createOpportunity: (input: OpportunityInput) => api<Opportunity>('/api/v1/opportunities', {
    method: 'POST', body: JSON.stringify(input),
  }),
  advanceOpportunity: (id: number, input: AdvanceInput) => api<Opportunity>(`/api/v1/opportunities/${id}/advance`, {
    method: 'POST', body: JSON.stringify(input),
  }),
}
```

- [ ] **Step 4: 实现路由壳与页签**

`App.tsx` 的 `/customers/*` 外层只要求登录；`CustomerCenterRoutes` 对 index 使用 `customer:read`，对 CRM 子路由使用 `crm:read`。`AppShell` 的模块配置允许 `permissions: ['customer:read','crm:read']` 任一命中，`homeRoute` 同步相同逻辑。`CustomerPage` 不复制页签，统一由 routes 壳渲染。

- [ ] **Step 5: 运行测试并提交**

Run: `cd frontend && npm run test:run -- src/modules/customer-center/CustomerCenterRoutes.test.tsx src/components/AppShell.test.tsx src/app/AuthProvider.test.tsx`

Expected: PASS。

```bash
git add frontend/src/modules/customer-center frontend/src/app/App.tsx frontend/src/components/AppShell.tsx frontend/src/components/AppShell.test.tsx frontend/src/app/homeRoute.ts frontend/src/modules/customer/CustomerPage.tsx
git commit -m "feat: add customer center navigation"
```

### Task 8: 实现商机总览、售前推进和商机详情

**Files:**

- Create: `frontend/src/modules/customer-center/OpportunityOverviewPage.tsx`
- Create: `frontend/src/modules/customer-center/PresaleBoardPage.tsx`
- Create: `frontend/src/modules/customer-center/OpportunityDetailPage.tsx`
- Create: `frontend/src/modules/customer-center/OpportunityEditor.tsx`
- Create: `frontend/src/modules/customer-center/OpportunityPages.test.tsx`
- Modify: `frontend/src/modules/customer-center/CustomerCenterRoutes.tsx`
- Modify: `frontend/src/styles/global.css`

- [ ] **Step 1: 写交互失败测试**

模拟 API 数据并验证：总数/开放金额/赢单/丢单/赢单率；五阶段漏斗；超过 14 天的阶段停留预警；客户/产品/负责人/阶段/状态/关键词筛选；列表/卡片切换与长文本省略；新建/编辑只在 `crm:write` 可见。

售前页验证五列商机、缺件时打开产出物抽屉、报告正文和文件 ID 表单、关口 PASS/REJECT 确认、丢单二次确认、合同 PASS 打开项目转交抽屉。详情页验证基本资料、活动 TODO/DONE、产出物、全链路以及项目深链。

- [ ] **Step 2: 运行失败测试**

Run: `cd frontend && npm run test:run -- src/modules/customer-center/OpportunityPages.test.tsx`

Expected: FAIL，页面不存在。

- [ ] **Step 3: 实现商机总览**

只在客户端计算当前已取回结果的 KPI 与漏斗；筛选变化构造 `URLSearchParams` 重新请求服务端。Ant Design `Statistic`、`Progress`、`Table/Card` 和 `PageState` 复用现有模式。阶段停留天数由 `stageEnteredAt` 计算，阈值固定 14 天并清楚标注。

- [ ] **Step 4: 实现售前推进与转交抽屉**

推进请求失败且错误为缺材料时保留服务端错误并打开产出物抽屉；补齐后由用户再次推进。合同转交的 CREATE 表单复用 `customerId`，只采集项目 code/name/product/version/manager/date/gateMode；LINK 只展示同客户现有项目。成功后失效 `opportunities/projects/implementation` 查询。

- [ ] **Step 5: 实现详情页**

活动、产出物、全链三个区块各自有 loading/error/empty；活动完成使用记录自身 version；文件类产出物只提交现有上传接口返回的 `fileId`；全链 project 点击 `/projects/:id`，operation 点击 `/customers/operations/:id`。

- [ ] **Step 6: 运行测试与构建并提交**

Run: `cd frontend && npm run test:run -- src/modules/customer-center/OpportunityPages.test.tsx && npm run build`

Expected: PASS 且 Vite production build 成功。

```bash
git add frontend/src/modules/customer-center frontend/src/styles/global.css
git commit -m "feat: add opportunity and presale workspace"
```

### Task 9: 实施协同、驾驶舱和客户运营页面

**Files:**

- Create: `frontend/src/modules/customer-center/ImplementationPage.tsx`
- Create: `frontend/src/modules/customer-center/ImplementationCockpitPage.tsx`
- Create: `frontend/src/modules/customer-center/OperationBoardPage.tsx`
- Create: `frontend/src/modules/customer-center/OperationDetailPage.tsx`
- Create: `frontend/src/modules/customer-center/DeliveryAndOperationPages.test.tsx`
- Modify: `frontend/src/modules/customer-center/CustomerCenterRoutes.tsx`
- Modify: `frontend/src/modules/customer-center/crmApi.ts`
- Modify: `frontend/src/styles/global.css`

- [ ] **Step 1: 写实施与运营失败测试**

实施协同断言客户、商机、项目、现有七阶段、负责人、风险、最近里程碑和计划日期；唯一写入口为“进入项目”，不出现本地阶段推进。驾驶舱断言实施中、红色风险、逾期里程碑、收尾项目指标以及健康度/阶段/负责人/客户筛选。

运营页断言三列活动看板、手工新建、推进、关闭后不再显示推进按钮；详情断言客户/商机/项目来源、负责人、更新时间和全链深链；只读用户无新建/编辑/推进。

- [ ] **Step 2: 运行失败测试**

Run: `cd frontend && npm run test:run -- src/modules/customer-center/DeliveryAndOperationPages.test.tsx`

Expected: FAIL，页面不存在。

- [ ] **Step 3: 实施只读页面**

实施协同使用表格/卡片自适应展示，`projectStage` 通过现有七阶段中文映射；风险与逾期使用 Tag，不在前端推导项目阶段。驾驶舱使用后端返回 KPI，筛选只影响 items 展示并同步数量说明。

- [ ] **Step 4: 实现运营页面**

看板只显示 `MAINTENANCE/OPERATING/REPURCHASE` 的开放记录，关闭记录通过状态筛选在列表区可查；新建抽屉要求启用客户，可选来源项目/商机；推进携带 version，成功失效 `operations`、详情和 `full-link` 查询。

- [ ] **Step 5: 运行测试与构建并提交**

Run: `cd frontend && npm run test:run -- src/modules/customer-center/DeliveryAndOperationPages.test.tsx src/modules/customer-center/CustomerCenterRoutes.test.tsx && npm run build`

Expected: PASS。

```bash
git add frontend/src/modules/customer-center frontend/src/styles/global.css
git commit -m "feat: add implementation and operation workspaces"
```

### Task 10: 补齐演示数据、全链端到端验收和回归

**Files:**

- Modify: `backend/src/main/resources/db/demo/R__demo_data.sql`
- Modify: `frontend/e2e/platform-acceptance.e2e.ts`
- Create: `frontend/e2e/customer-lifecycle.e2e.ts`
- Modify: `README.md`（若当前 README 有模块/验收说明；没有则不创建）

- [ ] **Step 1: 添加可重复执行的演示数据**

为现有华东银行客户插入覆盖五个售前阶段、一个已转交实施项目和三列运营阶段的最少演示记录；使用稳定业务唯一键查询 ID 与 `where not exists`，确保 repeatable migration 可重复运行。不要写入与自动运营唯一约束冲突的数据。

- [ ] **Step 2: 更新平台冒烟端到端测试**

`platform-acceptance.e2e.ts` 将“客户管理”入口改为“客户中心”，进入后验证六个页签可见并能打开客户管理、商机总览、实施驾驶舱。

- [ ] **Step 3: 写完整客户生命周期 E2E**

`customer-lifecycle.e2e.ts` 用管理员登录，创建客户和商机，按阶段补材料并推进，合同阶段创建项目，进入项目验证七阶段状态；通过 API/页面推进到 CLOSE 后验证只出现一条运营单，再推进运营至关闭。关键断言：

```ts
await expect(page.getByText(opportunityTitle, { exact: true })).toBeVisible()
await expect(page.getByRole('link', { name: projectName })).toHaveAttribute('href', /\/projects\//)
await expect(page.getByText('回款/维保')).toBeVisible()
```

- [ ] **Step 4: 运行后端全量测试**

Run: `cd backend && mvn -q test`

Expected: exit 0，全部 `*Test` / `*IT` 通过。

- [ ] **Step 5: 运行前端全量测试和生产构建**

Run: `cd frontend && npm run test:run && npm run build`

Expected: 所有 Vitest 用例通过，TypeScript 与 Vite build 成功。

- [ ] **Step 6: 运行隔离 Docker E2E**

Run: `cd frontend && npm run e2e -- e2e/platform-acceptance.e2e.ts e2e/product-center.e2e.ts e2e/customer-lifecycle.e2e.ts`

Expected: 3 个 Playwright 场景全部通过，runner 自动销毁临时容器、网络、镜像和数据卷。

- [ ] **Step 7: 浏览器人工验收**

启动本地 compose，在应用内浏览器依次检查 1440px 与窄窗口：客户中心页签、商机长文本、漏斗/看板、缺件抽屉、项目深链、运营看板；确认无控制台错误、遮挡、水平溢出和不可点击控件。

- [ ] **Step 8: 最终提交**

```bash
git add backend/src/main/resources/db/demo/R__demo_data.sql frontend/e2e frontend/src backend/src README.md
git commit -m "test: cover customer lifecycle end to end"
```

### Task 11: 最终审查、合并与本地启动

**Files:**

- Review: all files changed since `main`

- [ ] **Step 1: 检查范围和工作区**

Run: `git status --short && git diff --stat main...HEAD && git log --oneline main..HEAD`

Expected: 仅客户生命周期相关改动，没有未跟踪构建产物或 Rainier 代码复制。

- [ ] **Step 2: 验证最新提交上的关键测试**

Run: `cd backend && mvn -q test && cd ../frontend && npm run test:run && npm run build`

Expected: 全绿；以本次新鲜输出作为完成依据。

- [ ] **Step 3: 快进或普通合并到本地 main**

在主工作区执行：

```bash
git checkout main
git merge --no-ff codex/customer-lifecycle -m "merge: add customer lifecycle module"
```

- [ ] **Step 4: 在合并后的 main 做烟测并启动项目**

Run: `docker compose up -d --build && docker compose ps`

Expected: mysql、redis、minio、mock-agent、backend、frontend 全部 running/healthy；浏览器可从本地登录页进入客户中心。

- [ ] **Step 5: 交付说明**

报告合并提交、测试数量/命令、访问地址、演示账号、已知限制；列出 `git status --short`，确认没有未提交改动。
