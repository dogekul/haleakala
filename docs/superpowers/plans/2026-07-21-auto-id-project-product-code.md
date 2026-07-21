# 项目与产品自动编号 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建项目和产品时不填写编码，并让返回的 `code` 等于数据库自增主键 ID。

**Architecture:** 保留既有字符串 `code` 字段与所有读取方。创建事务用 UUID 临时值通过非空/唯一约束，取得自增 ID 后立即回写十进制 ID；现有数据不迁移。

**Tech Stack:** Java 8、Spring Boot 2.7、JdbcTemplate、MySQL/H2、React、TypeScript、Ant Design、Vitest。

## Global Constraints

- 不新增依赖或数据库迁移。
- 已有项目和产品编码保持不变。
- 所有新建入口不展示、不校验、不提交编码。
- 后端仍返回 `code` 兼容现有查询、文档和页面。

---

### Task 1: 产品自动编号

**Files:**
- Modify: `backend/src/test/java/com/zhilu/delivery/catalog/ProductCatalogIT.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/catalog/ProductCatalogController.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/catalog/ProductCatalogService.java`

**Interfaces:**
- Consumes: `SimpleJdbcInsert.executeAndReturnKey(Map<String,Object>)`
- Produces: `createProduct(long, Long, String, String, String)`，返回 `code == String.valueOf(id)`

- [ ] **Step 1: Write the failing test**

将产品测试的创建 JSON 改为不含 `code`，并断言：

```java
.content("{\"name\":\"" + name + "\",\"category\":\"企业应用\"}"))
.andExpect(jsonPath("$.code").value(String.valueOf(id)));
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ProductCatalogIT test`
Expected: FAIL，创建请求因 `code` 缺失返回 400。

- [ ] **Step 3: Write minimal implementation**

移除创建请求的编码校验和服务参数，插入后回写 ID：

```java
Map<String, Object> values = new HashMap<String, Object>();
values.put("organization_id", organizationId);
values.put("owner_user_id", ownerUserId);
values.put("code", "PENDING-" + UUID.randomUUID().toString());
values.put("name", name.trim());
values.put("category", category);
values.put("description", description);
values.put("status", "PLANNING");
long id = new SimpleJdbcInsert(jdbc).withTableName("product")
    .usingGeneratedKeyColumns("id").executeAndReturnKey(values).longValue();
jdbc.update("update product set code=? where id=?", String.valueOf(id), id);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=ProductCatalogIT test`
Expected: PASS。

### Task 2: 项目与商机交接自动编号

**Files:**
- Modify: `backend/src/test/java/com/zhilu/delivery/project/ProjectLifecycleTest.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/opportunity/OpportunityHandoffIT.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/project/CreateProjectCommand.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/project/ProjectController.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/project/ProjectService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/opportunity/OpportunityService.java`

**Interfaces:**
- Consumes: 创建项目所需名称、客户、产品版本、负责人和日期
- Produces: 无 `code` 的 `CreateProjectCommand` 与商机交接创建请求

- [ ] **Step 1: Write the failing tests**

```java
ProjectView project = projects.create(command("IGNORED"));
assertEquals(String.valueOf(project.getId()), project.getCode());
```

商机交接 JSON 删除 `project.code`，并断言 `project.code` 等于返回的 `projectId`。

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=ProjectLifecycleTest,OpportunityHandoffIT test`
Expected: FAIL，当前项目沿用人工编码且交接请求要求编码。

- [ ] **Step 3: Write minimal implementation**

删除 `CreateProjectCommand.code` 和公开创建 DTO 的 `code`，项目插入值改为：

```java
values.put("code", "PENDING-" + UUID.randomUUID().toString());
long projectId = insert("delivery_project", values);
jdbc.update("update delivery_project set code=? where id=?",
    String.valueOf(projectId), projectId);
```

商机 `ProjectInput` 删除 `code`，构造项目命令时不再传编码。

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -Dtest=ProjectLifecycleTest,OpportunityHandoffIT test`
Expected: PASS。

### Task 3: 移除所有创建表单的编码输入

**Files:**
- Modify: `frontend/src/modules/project/ProjectWorkspace.test.tsx`
- Modify: `frontend/src/modules/dashboard/DashboardPage.test.tsx`
- Modify: `frontend/src/modules/customer-center/OpportunityPages.test.tsx`
- Modify: `frontend/src/modules/product/ProductListPage.test.tsx`
- Modify: `frontend/src/modules/project/ProjectWorkspace.tsx`
- Modify: `frontend/src/modules/dashboard/DashboardPage.tsx`
- Modify: `frontend/src/modules/customer-center/PresaleBoardPage.tsx`
- Modify: `frontend/src/modules/product/ProductListPage.tsx`

**Interfaces:**
- Consumes: 现有 `projectApi.create`、`crmApi.handoff`、`productApi.saveProduct`
- Produces: 不含 `code` 的创建请求负载

- [ ] **Step 1: Write the failing tests**

在四组页面测试中断言：

```tsx
expect(within(drawer).queryByLabelText(/项目编号|项目编码|产品编码/)).not.toBeInTheDocument()
expect(requestBody).not.toHaveProperty('code')
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npm test -- --run src/modules/project/ProjectWorkspace.test.tsx src/modules/dashboard/DashboardPage.test.tsx src/modules/customer-center/OpportunityPages.test.tsx src/modules/product/ProductListPage.test.tsx`
Expected: FAIL，当前抽屉仍显示编码字段。

- [ ] **Step 3: Write minimal implementation**

删除四个创建入口的编码 `Form.Item` 与负载映射；产品编辑也只展示名称等可编辑字段。在名称字段下显示：

```tsx
<Typography.Text type="secondary">编号由系统自动生成</Typography.Text>
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `npm test -- --run src/modules/project/ProjectWorkspace.test.tsx src/modules/dashboard/DashboardPage.test.tsx src/modules/customer-center/OpportunityPages.test.tsx src/modules/product/ProductListPage.test.tsx`
Expected: PASS。

### Task 4: 全量验证与交付

**Files:**
- Verify: all modified files

**Interfaces:**
- Consumes: Tasks 1–3
- Produces: 可运行的本地完整系统

- [ ] **Step 1: Run backend tests**

Run: `mvn -q clean test`
Expected: 0 failures, 0 errors。

- [ ] **Step 2: Run frontend tests and build**

Run: `npm test -- --run --silent --testTimeout=10000 && npm run build`
Expected: 全部测试通过且 Vite 构建退出码为 0。

- [ ] **Step 3: Verify and commit**

Run: `git diff --check && git status --short`
Expected: 无空白错误，仅包含本功能文件。

- [ ] **Step 4: Rebuild local services**

Run: `docker compose -p zhilu-delivery-main up -d --build`
Expected: backend 与 frontend 均 healthy，`http://localhost:53990/projects` 返回 200。
