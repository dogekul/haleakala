# Customer Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build organization-scoped customer master data with database-generated IDs and require active customer selection when creating projects.

**Architecture:** Add a V12 migration, a focused Spring `customer` service/controller, and a React customer module that reuses the existing list/card and drawer patterns. Keep `delivery_project.customer_name` as a compatibility snapshot while adding `customer_id`; all new project creation validates and persists the selected customer server-side.

**Tech Stack:** Java 8, Spring Boot 2.7, JdbcTemplate, Flyway, MySQL/H2, React 18, TypeScript, Ant Design, TanStack Query, Vitest, Playwright.

## Global Constraints

- Customer identity is the database-generated `BIGINT AUTO_INCREMENT` primary key; no customer code field exists in the database, API, or UI.
- Customer fields are name, short name, contact name, phone, email, address, status, and remark; industry is excluded.
- Data is isolated by `organization_id` and duplicate names in one organization are rejected.
- Customer deletion is not supported; inactive customers remain readable but cannot be selected for new projects.
- Both project creation entry points submit `customerId`, never free-text `customerName`.
- Existing `delivery_project.customer_name` remains as a fallback snapshot for migrated data.
- No new frontend or backend dependencies.

---

### Task 1: Schema, migration, and permissions

**Files:**
- Create: `backend/src/main/resources/db/migration/V12__customer_management.sql`
- Modify: `backend/src/test/java/com/zhilu/delivery/SchemaBaselineTest.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/iam/SecurityAccessTest.java`

**Interfaces:**
- Consumes: existing `organization`, `delivery_project`, `permission`, `role`, and `role_permission` tables.
- Produces: `customer`, nullable `delivery_project.customer_id`, `customer:read`, and `customer:write`.

- [ ] **Step 1: Write failing migration and permission tests**

Add assertions that the migrated schema contains `customer`, that `delivery_project.customer_id` exists, that no `customer.code` column exists, and that distinct legacy `(organization_id, customer_name)` values are backfilled to one customer and linked. Add security assertions for the two customer permissions and role grants.

```java
assertEquals(1, jdbc.queryForObject(
    "select count(*) from information_schema.tables where table_name='customer'", Integer.class));
assertEquals(0, jdbc.queryForObject(
    "select count(*) from information_schema.columns where table_name='customer' and column_name='code'", Integer.class));
assertEquals(2, jdbc.queryForObject(
    "select count(*) from permission where code in ('customer:read','customer:write')", Integer.class));
```

- [ ] **Step 2: Run the tests and verify RED**

Run: `mvn -q -Dtest=SchemaBaselineTest,SecurityAccessTest test`

Expected: failures because the customer table, project foreign key, and permissions do not exist.

- [ ] **Step 3: Implement V12**

Create `customer` with an auto-increment ID and `(organization_id,name)` unique key, migrate distinct nonblank legacy names, add/backfill `delivery_project.customer_id`, then add the foreign key and indexes. Insert permissions idempotently and grant read to all built-in roles; grant write to ADMIN, PMO, and DELIVERY_MANAGER.

```sql
CREATE TABLE customer (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  name VARCHAR(180) NOT NULL,
  short_name VARCHAR(100) NULL,
  contact_name VARCHAR(100) NULL,
  phone VARCHAR(40) NULL,
  email VARCHAR(160) NULL,
  address VARCHAR(500) NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  remark TEXT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT uk_customer_org_name UNIQUE (organization_id,name),
  CONSTRAINT fk_customer_org FOREIGN KEY (organization_id) REFERENCES organization(id)
);
```

- [ ] **Step 4: Run the tests and verify GREEN**

Run: `mvn -q -Dtest=SchemaBaselineTest,SecurityAccessTest test`

Expected: all selected tests pass with zero failures and errors.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V12__customer_management.sql backend/src/test/java/com/zhilu/delivery/SchemaBaselineTest.java backend/src/test/java/com/zhilu/delivery/iam/SecurityAccessTest.java
git commit -m "feat: add customer master data schema"
```

### Task 2: Customer API and organization isolation

**Files:**
- Create: `backend/src/main/java/com/zhilu/delivery/customer/CustomerService.java`
- Create: `backend/src/main/java/com/zhilu/delivery/customer/CustomerController.java`
- Create: `backend/src/test/java/com/zhilu/delivery/customer/CustomerApiIT.java`

**Interfaces:**
- Consumes: `customer` table, `CurrentUser`, `AuditService`, `ConflictException`, and `NotFoundException`.
- Produces: `GET /api/v1/customers`, `POST /api/v1/customers`, and `PUT /api/v1/customers/{id}` returning maps with `id`, customer fields, `projectCount`, `updatedAt`, and `version`.

- [ ] **Step 1: Write failing API tests**

Cover database-generated ID, list filters, update, duplicate name conflict, optimistic lock conflict, and cross-organization invisibility. Requests contain no `code` property.

```java
mvc.perform(post("/api/v1/customers").with(actor(100, 100, "customer:write")).with(csrf())
    .contentType(MediaType.APPLICATION_JSON)
    .content("{\"name\":\"华东银行\",\"shortName\":\"华东行\",\"status\":\"ACTIVE\",\"version\":0}"))
    .andExpect(status().isCreated())
    .andExpect(jsonPath("$.id").isNumber())
    .andExpect(jsonPath("$.code").doesNotExist());
```

- [ ] **Step 2: Run the tests and verify RED**

Run: `mvn -q -Dtest=CustomerApiIT test`

Expected: compilation or 404 failures because the customer API is absent.

- [ ] **Step 3: Implement the minimal service and controller**

`CustomerService` queries only `organization_id=?`, trims required names, validates status against `ACTIVE`/`INACTIVE`, catches `DuplicateKeyException` as `ConflictException("客户名称已存在")`, and uses `where id=? and organization_id=? and version=?` for updates. `CustomerController` validates name/email, obtains organization from `CurrentUser`, and records create/update audit events.

```java
@GetMapping
public List<Map<String,Object>> customers(@RequestParam(required=false) String keyword,
    @RequestParam(required=false) String status, @AuthenticationPrincipal CurrentUser user) {
  return customers.list(user.getOrganizationId(), keyword, status);
}
```

- [ ] **Step 4: Run the tests and verify GREEN**

Run: `mvn -q -Dtest=CustomerApiIT test`

Expected: all customer API tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/zhilu/delivery/customer backend/src/test/java/com/zhilu/delivery/customer
git commit -m "feat: add customer management api"
```

### Task 3: Require customers when creating projects

**Files:**
- Modify: `backend/src/main/java/com/zhilu/delivery/project/CreateProjectCommand.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/project/ProjectController.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/project/ProjectService.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/project/ProjectView.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/project/ProjectApiIT.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/project/ProjectAuthorizationIT.java`
- Modify: every backend test constructor call found by `rg 'new CreateProjectCommand' backend/src/test`

**Interfaces:**
- Consumes: `customer.id`, organization, and status.
- Produces: `CreateProjectCommand.getCustomerId()`, request property `customerId`, response property `customerId`, and current customer name with snapshot fallback.

- [ ] **Step 1: Write failing project tests**

Add tests proving a project is created from an active local customer, inactive customers return 400, foreign customers return 404, and API requests without `customerId` fail validation. Assert saved `customer_id` and automatic `customer_name`; never submit a customer name.

```java
.content("{\"code\":\"PRJ-CUSTOMER\",\"name\":\"客户项目\",\"customerId\":610,"
    + "\"productId\":610,\"productVersionId\":610,\"gateMode\":\"BLOCK\"}")
.andExpect(status().isCreated())
.andExpect(jsonPath("$.customerId").value(610))
.andExpect(jsonPath("$.customerName").value("北方银行"));
```

- [ ] **Step 2: Run the tests and verify RED**

Run: `mvn -q -Dtest=ProjectApiIT,ProjectAuthorizationIT,ProjectLifecycleTest test`

Expected: failures because project creation still expects free-text `customerName`.

- [ ] **Step 3: Implement project linkage**

Replace `customerName` in `CreateProjectCommand` with `long customerId`. In `ProjectService.create`, select the customer with `id=? and organization_id=?`; throw `NotFoundException` if absent and `IllegalArgumentException("停用客户不能创建项目")` if inactive. Insert both `customer_id` and the selected `name`. Update project select clauses to left join customer and map `customerId` plus `coalesce(c.name,p.customer_name)`.

```java
Map<String,Object> customer = customerForProject(command.getOrganizationId(), command.getCustomerId());
values.put("customer_id", command.getCustomerId());
values.put("customer_name", customer.get("name"));
```

- [ ] **Step 4: Update existing test fixtures and verify GREEN**

Seed a customer before each direct project creation and pass its ID. Run:

`mvn -q -Dtest=ProjectApiIT,ProjectAuthorizationIT,ProjectLifecycleTest,DashboardQueryIT,RequirementApiIT,AgentJobServiceTest,ResourceServiceTest,StandardizationServiceTest,FileServiceTest test`

Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/zhilu/delivery/project backend/src/test/java/com/zhilu/delivery
git commit -m "feat: link projects to active customers"
```

### Task 4: Customer management page and permissions

**Files:**
- Create: `frontend/src/modules/customer/types.ts`
- Create: `frontend/src/modules/customer/customerApi.ts`
- Create: `frontend/src/modules/customer/CustomerPage.tsx`
- Create: `frontend/src/modules/customer/CustomerPage.test.tsx`
- Modify: `frontend/src/app/App.tsx`
- Modify: `frontend/src/app/homeRoute.ts`
- Modify: `frontend/src/components/AppShell.tsx`
- Modify: `frontend/src/components/AppShell.test.tsx`
- Modify: `frontend/src/styles/global.css`

**Interfaces:**
- Consumes: customer REST API and `customer:read`/`customer:write` from `useAuth()`.
- Produces: `/customers` route, customer list/card UI, filters, and create/edit drawer.

- [ ] **Step 1: Write failing page and navigation tests**

Test default list view, card switch, keyword/status filtering, statistics, long-text truncation class, no code/industry field, save payload, and read-only controls. Extend `AppShell.test.tsx` to require `customer:read` for the navigation item.

```tsx
expect(await screen.findByText('华东银行')).toBeVisible()
expect(screen.getByRole('button', { name: '新建客户' })).toBeVisible()
expect(screen.queryByLabelText('客户编码')).not.toBeInTheDocument()
expect(screen.queryByLabelText('行业')).not.toBeInTheDocument()
```

- [ ] **Step 2: Run tests and verify RED**

Run: `pnpm vitest run src/modules/customer/CustomerPage.test.tsx src/components/AppShell.test.tsx`

Expected: missing module/route/navigation failures.

- [ ] **Step 3: Implement API, types, route, and navigation**

Define `Customer` with `id`, name/contact fields, status, `projectCount`, `updatedAt`, and `version`. Define `customerApi.list(filters)`, `create`, and `update`. Lazy-load `CustomerPage` at `/customers` behind `customer:read`; insert the rail item between dashboard and projects.

- [ ] **Step 4: Implement the styled page**

Reuse `page-heading compact`, `filter-surface`, Ant Design `Table`, `Row/Col/Card`, and a `Radio.Group` list/card toggle. Use a 600px drawer, ellipsis CSS for contact/address text, `Tag` for status, and conditionally render write controls from `customer:write`.

```tsx
<Input allowClear prefix={<SearchOutlined />} placeholder="搜索客户、简称或联系人" />
<Radio.Group value={view} buttonStyle="solid">
  <Radio.Button value="list" aria-label="列表视图"><BarsOutlined /></Radio.Button>
  <Radio.Button value="card" aria-label="卡片视图"><AppstoreOutlined /></Radio.Button>
</Radio.Group>
```

- [ ] **Step 5: Run tests and verify GREEN**

Run: `pnpm vitest run src/modules/customer/CustomerPage.test.tsx src/components/AppShell.test.tsx`

Expected: all selected frontend tests pass.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/modules/customer frontend/src/app/App.tsx frontend/src/app/homeRoute.ts frontend/src/components/AppShell.tsx frontend/src/components/AppShell.test.tsx frontend/src/styles/global.css
git commit -m "feat: add customer management workspace"
```

### Task 5: Customer selectors in both project creation flows

**Files:**
- Modify: `frontend/src/modules/project/types.ts`
- Modify: `frontend/src/modules/project/ProjectWorkspace.tsx`
- Modify: `frontend/src/modules/project/ProjectWorkspace.test.tsx`
- Modify: `frontend/src/modules/dashboard/DashboardPage.tsx`
- Modify: `frontend/src/modules/dashboard/DashboardPage.test.tsx`
- Reuse: `frontend/src/modules/customer/customerApi.ts`

**Interfaces:**
- Consumes: `customerApi.list({status:'ACTIVE'})` and `Customer`.
- Produces: required `customerId` form fields and project create payloads without `customerName`.

- [ ] **Step 1: Write failing selector tests**

For each drawer, mock active customers, open the drawer, select a customer, submit, and assert `customerId` is present while `customerName` is absent. Assert inactive customers are not options and empty data renders “请先创建启用客户”.

```tsx
expect(JSON.parse(String(createCall?.[1]?.body))).toMatchObject({ customerId: 81 })
expect(JSON.parse(String(createCall?.[1]?.body))).not.toHaveProperty('customerName')
```

- [ ] **Step 2: Run tests and verify RED**

Run: `pnpm vitest run src/modules/project/ProjectWorkspace.test.tsx src/modules/dashboard/DashboardPage.test.tsx`

Expected: current drawers expose a text input and send `customerName`.

- [ ] **Step 3: Implement both selectors**

Load active customers only while each drawer is open. Render searchable selects with `optionFilterProp="label"`, labels `${name}${shortName ? ` · ${shortName}` : ''}`, and a required `customerId`. Add `customerId` to `Project` and remove no display fields.

- [ ] **Step 4: Run tests and verify GREEN**

Run: `pnpm vitest run src/modules/project/ProjectWorkspace.test.tsx src/modules/dashboard/DashboardPage.test.tsx`

Expected: both suites pass and verify customer IDs.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/modules/project frontend/src/modules/dashboard
git commit -m "feat: select customers when creating projects"
```

### Task 6: Full verification and browser acceptance

**Files:**
- Modify: `frontend/e2e/platform-acceptance.e2e.ts`

**Interfaces:**
- Consumes: full Docker-backed application.
- Produces: browser proof of customer create/edit/project selection/inactivation.

- [ ] **Step 1: Extend the E2E flow before production changes if it was not already covered by Tasks 4-5**

Add a test flow that creates a customer without a code, edits its contact information, creates a project selecting that customer, verifies the project shows the customer, then inactivates the customer and verifies it disappears from the create-project selector.

- [ ] **Step 2: Run focused and full backend verification**

Run: `mvn -q test` from `backend/`.

Expected: all backend tests pass with zero failures/errors.

- [ ] **Step 3: Run full frontend verification**

Run: `pnpm test -- --run` and `pnpm build` from `frontend/`.

Expected: all frontend tests pass and Vite production build completes.

- [ ] **Step 4: Run browser acceptance**

Run: `pnpm e2e` from `frontend/`.

Expected: every Playwright test passes and disposable Docker resources are removed.

- [ ] **Step 5: Commit verification coverage**

```bash
git add frontend/e2e/platform-acceptance.e2e.ts
git commit -m "test: cover customer project workflow"
```
