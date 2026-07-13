# System Administration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the inert system-administration placeholder with five working, permission-protected management pages backed by persisted APIs and audit records.

**Architecture:** Extend the existing `/api/v1/admin` boundary with organization-scoped IAM, audit, and settings operations, while retaining the existing product catalog endpoints with stricter write authorization. Add a routed React admin module whose focused pages share typed API functions and TanStack Query invalidation.

**Tech Stack:** Java 8, Spring Boot 2.7, Spring Security, JdbcTemplate, JUnit/MockMvc, React 18, TypeScript, React Router 6, Ant Design 5, TanStack Query 5, Vitest/Testing Library.

## Global Constraints

- Do not add dependencies.
- Use status changes instead of physical deletion.
- Scope user, team, audit, and setting data to the authenticated user's `organizationId`.
- Never expose password hashes or persist deployment secrets.
- Keep product reads available to authenticated business users; require `system:manage` for product writes.
- Every behavior change starts with a failing automated test.

---

### Task 1: Organization-scoped user and team administration

**Files:**
- Modify: `backend/src/test/java/com/zhilu/delivery/iam/AdminIamControllerTest.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/iam/api/AdminIamController.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/iam/service/IamAdminService.java`

**Interfaces:**
- Consumes: authenticated `CurrentUser` from Spring Security.
- Produces: organization-scoped `users(CurrentUser)`, `teams(CurrentUser)`, `updateUser(...)`, `updateTeam(...)`, and enriched user rows containing `primaryTeamId`, `primaryTeamName`, and `roles`.

- [ ] **Step 1: Write failing MockMvc tests for organization isolation and edits**

Add tests that authenticate with a `CurrentUser` principal and assert another organization's rows are absent, then exercise user/team updates:

```java
CurrentUser admin = new CurrentUser(300L, 300L, "admin", "系统管理员",
    Collections.singletonList("ADMIN"), Collections.singletonList("system:manage"));

mvc.perform(get("/api/v1/admin/users").with(user(admin)))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$[0].primaryTeamName").value("交付一组"))
    .andExpect(jsonPath("$[0].roles[0]").value("DELIVERY_ENGINEER"));

mvc.perform(put("/api/v1/admin/users/{id}", userId).with(user(admin)).with(csrf())
    .contentType(MediaType.APPLICATION_JSON)
    .content("{\"displayName\":\"王工\",\"email\":\"wang@zhilu.local\","
        + "\"primaryTeamId\":" + teamId + ",\"roleCodes\":[\"TECH_MANAGER\"],"
        + "\"status\":\"ACTIVE\"}"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.displayName").value("王工"));
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `cd backend && mvn -Dtest=AdminIamControllerTest test`

Expected: FAIL because existing list methods are global and the `PUT` endpoints do not exist.

- [ ] **Step 3: Implement the minimal scoped service and controller methods**

Use `@AuthenticationPrincipal CurrentUser user`, derive organization IDs only from it, replace user roles transactionally, validate `ACTIVE|DISABLED`, and reject disabling `user.getId()`. Map duplicate keys to `ConflictException` and absent rows to `NotFoundException`.

```java
@GetMapping("/users")
public List<Map<String, Object>> users(@AuthenticationPrincipal CurrentUser user) {
  return admin.users(user.getOrganizationId());
}

@PutMapping("/users/{id}")
public Map<String, Object> updateUser(@PathVariable long id,
    @Valid @RequestBody UpdateUserRequest request,
    @AuthenticationPrincipal CurrentUser user) {
  return admin.updateUser(user, id, request.displayName, request.email,
      request.primaryTeamId, request.roleCodes, request.status);
}
```

- [ ] **Step 4: Run IAM tests and verify GREEN**

Run: `cd backend && mvn -Dtest=AdminIamControllerTest,SecurityAccessTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/zhilu/delivery/iam backend/src/test/java/com/zhilu/delivery/iam
git commit -m "feat: complete user and team administration"
```

### Task 2: Permission catalog and role safety

**Files:**
- Modify: `backend/src/test/java/com/zhilu/delivery/iam/AdminIamControllerTest.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/iam/api/AdminIamController.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/iam/service/IamAdminService.java`

**Interfaces:**
- Consumes: role and permission tables.
- Produces: `GET /api/v1/admin/permissions` and safe full-replacement role permissions.

- [ ] **Step 1: Write failing tests for permission listing and ADMIN self-lock prevention**

```java
mvc.perform(get("/api/v1/admin/permissions").with(user(admin)))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$[0].code").isNotEmpty())
    .andExpect(jsonPath("$[0].module").isNotEmpty());

mvc.perform(put("/api/v1/admin/roles/{id}/permissions", adminRoleId)
    .with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
    .content("{\"permissionCodes\":[\"dashboard:read\"]}"))
    .andExpect(status().isConflict());
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `cd backend && mvn -Dtest=AdminIamControllerTest test`

Expected: FAIL with missing permissions endpoint and a 200 for unsafe ADMIN replacement.

- [ ] **Step 3: Implement the permission catalog and guard**

Return ordered maps with `code`, `name`, and `module`; before deleting role permissions, load the role code and throw `ConflictException` when `ADMIN` would lose `system:manage`.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `cd backend && mvn -Dtest=AdminIamControllerTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main backend/src/test/java/com/zhilu/delivery/iam/AdminIamControllerTest.java
git commit -m "feat: add safe role permission administration"
```

### Task 3: Audit query and white-listed settings

**Files:**
- Create: `backend/src/main/java/com/zhilu/delivery/admin/AdminAuditService.java`
- Create: `backend/src/main/java/com/zhilu/delivery/admin/SystemSettingService.java`
- Create: `backend/src/main/java/com/zhilu/delivery/admin/AdminSystemController.java`
- Create: `backend/src/test/java/com/zhilu/delivery/admin/AdminSystemControllerTest.java`

**Interfaces:**
- Produces: `GET /api/v1/admin/audit-logs`, `GET /api/v1/admin/settings`, `PUT /api/v1/admin/settings`.
- `SystemSettingService.agentTimeoutMinutes(long organizationId)` is consumed by `AgentJobService` in Task 4.

- [ ] **Step 1: Write failing tests for audit pagination/filtering and settings validation**

```java
mvc.perform(get("/api/v1/admin/audit-logs?page=1&pageSize=20&keyword=TRACE-300")
    .with(user(admin)))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.total").value(1))
    .andExpect(jsonPath("$.items[0].traceId").value("TRACE-300"));

mvc.perform(put("/api/v1/admin/settings").with(user(admin)).with(csrf())
    .contentType(MediaType.APPLICATION_JSON)
    .content("{\"platformName\":\"智鹿交付中心\",\"environmentLabel\":\"演示环境\","
        + "\"timezone\":\"Asia/Shanghai\",\"supportEmail\":\"support@zhilu.local\","
        + "\"agentTimeoutMinutes\":45}"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.agentTimeoutMinutes").value(45));
```

- [ ] **Step 2: Run the new test and verify RED**

Run: `cd backend && mvn -Dtest=AdminSystemControllerTest test`

Expected: FAIL because the classes and endpoints are absent.

- [ ] **Step 3: Implement query construction and setting upsert**

Build audit SQL from a fixed set of clauses and bound parameters; clamp `pageSize` to 1–100. Store only internal keys `platform.name`, `platform.environmentLabel`, `platform.timezone`, `platform.supportEmail`, and `agent.timeoutMinutes`. Validate lengths, `ZoneId.of(timezone)`, a conservative email pattern, and integer range 1–240.

```java
public long agentTimeoutMinutes(long organizationId) {
  String value = setting(organizationId, "agent.timeoutMinutes");
  return value == null ? defaultAgentTimeoutMinutes : Long.parseLong(value);
}
```

- [ ] **Step 4: Run the test and verify GREEN**

Run: `cd backend && mvn -Dtest=AdminSystemControllerTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/zhilu/delivery/admin backend/src/test/java/com/zhilu/delivery/admin
git commit -m "feat: add audit and system settings administration"
```

### Task 4: Product write authorization, write auditing, and dynamic Agent timeout

**Files:**
- Modify: `backend/src/test/java/com/zhilu/delivery/iam/SecurityAccessTest.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/catalog/ProductCatalogIT.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/automation/AgentJobServiceTest.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/config/SecurityConfig.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/catalog/ProductCatalogController.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/automation/AgentJobService.java`

**Interfaces:**
- Consumes: `SystemSettingService.agentTimeoutMinutes(organizationId)`.
- Produces: protected and audited product writes; new Agent jobs use the current organization setting.

- [ ] **Step 1: Write failing authorization, audit, and timeout tests**

Assert `project:read` can GET products but receives 403 on POST; assert an admin POST creates an `audit_log` row. Insert `agent.timeoutMinutes=45`, submit a job, and assert `timeout_at` is approximately 45 minutes after `created_at`.

- [ ] **Step 2: Run the focused tests and verify RED**

Run: `cd backend && mvn -Dtest=SecurityAccessTest,ProductCatalogIT,AgentJobServiceTest test`

Expected: FAIL because product writes currently require only authentication, product writes are not audited, and Agent timeout is constructor-fixed.

- [ ] **Step 3: Implement minimal authorization and runtime reads**

Add method-ordered security rules:

```java
.antMatchers(HttpMethod.GET, "/api/v1/products/**").authenticated()
.antMatchers("/api/v1/products/**").hasAuthority("system:manage")
```

Inject `AuditService` into `ProductCatalogController` and record successful writes with the authenticated `CurrentUser`. Inject `SystemSettingService` into `AgentJobService` and compute `timeoutAt` from the project's `organization_id` on each submission.

- [ ] **Step 4: Run focused and full backend tests**

Run: `cd backend && mvn -Dtest=SecurityAccessTest,ProductCatalogIT,AgentJobServiceTest test && mvn test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main backend/src/test
git commit -m "feat: secure and audit administration writes"
```

### Task 5: Admin routing and typed frontend API

**Files:**
- Create: `frontend/src/modules/admin/types.ts`
- Create: `frontend/src/modules/admin/adminApi.ts`
- Create: `frontend/src/modules/admin/AdminPage.tsx`
- Create: `frontend/src/modules/admin/AdminPage.test.tsx`
- Modify: `frontend/src/app/App.tsx`
- Modify: `frontend/src/components/AppShell.tsx`
- Modify: `frontend/src/components/AppShell.test.tsx`

**Interfaces:**
- Produces: route-aware admin navigation and typed API functions for all five pages.

- [ ] **Step 1: Write failing route/navigation tests**

Render an admin `AuthContext`, start at `/admin/roles`, and assert all five links are present, “角色权限” is active, and the admin page content renders. Click “审计日志” and assert the location becomes `/admin/audit-logs`.

```tsx
expect(screen.getByRole('link', { name: '用户与团队' })).toHaveAttribute('href', '/admin/users')
expect(screen.getByRole('link', { name: '角色权限' })).toHaveClass('active')
await user.click(screen.getByRole('link', { name: '审计日志' }))
expect(screen.getByTestId('location')).toHaveTextContent('/admin/audit-logs')
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `cd frontend && pnpm test -- --run src/components/AppShell.test.tsx src/modules/admin/AdminPage.test.tsx`

Expected: FAIL because submenu items are buttons and `AdminPage` does not exist.

- [ ] **Step 3: Implement routes, menu links, types, and API functions**

Change module menus to `{ label, path? }`, render admin items as React Router `Link`, and compute the active item from `location.pathname`. Lazy-load `AdminPage` in `App.tsx`. Define exact functions such as `getAdminUsers`, `saveAdminUser`, `getRoles`, `replaceRolePermissions`, `getAuditLogs`, `getSettings`, and `saveSettings`, all using the shared `api` helper.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run: `cd frontend && pnpm test -- --run src/components/AppShell.test.tsx src/modules/admin/AdminPage.test.tsx`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app frontend/src/components frontend/src/modules/admin
git commit -m "feat: add routed system administration shell"
```

### Task 6: User/team and role management pages

**Files:**
- Create: `frontend/src/modules/admin/UsersTeamsPage.tsx`
- Create: `frontend/src/modules/admin/RolesPage.tsx`
- Create: `frontend/src/modules/admin/UsersTeamsPage.test.tsx`
- Create: `frontend/src/modules/admin/RolesPage.test.tsx`
- Modify: `frontend/src/modules/admin/AdminPage.tsx`
- Modify: `frontend/src/styles/global.css`

**Interfaces:**
- Consumes: Task 5 admin API functions and types.
- Produces: working user/team drawers and role permission matrix.

- [ ] **Step 1: Write failing component tests**

Stub fetch responses, render each page under `QueryClientProvider`, and assert tables render server values. Submit an edited user and changed permission set, then assert the expected `PUT` request body and refreshed UI.

- [ ] **Step 2: Run the focused tests and verify RED**

Run: `cd frontend && pnpm test -- --run src/modules/admin/UsersTeamsPage.test.tsx src/modules/admin/RolesPage.test.tsx`

Expected: FAIL because the page components are absent.

- [ ] **Step 3: Implement the minimal pages**

Use Ant Design `Tabs`, `Table`, `Drawer`, `Form`, `Select`, `Switch`, and `Checkbox.Group`. Keep username and built-in role metadata read-only. On mutation success invalidate only `['admin','users']`, `['admin','teams']`, or `['admin','roles']`; render `ApiError.message` in an `Alert` on failure.

- [ ] **Step 4: Run tests and verify GREEN**

Run: `cd frontend && pnpm test -- --run src/modules/admin/UsersTeamsPage.test.tsx src/modules/admin/RolesPage.test.tsx`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/modules/admin frontend/src/styles/global.css
git commit -m "feat: add identity and role administration pages"
```

### Task 7: Product/version, audit, and settings pages

**Files:**
- Create: `frontend/src/modules/admin/ProductsPage.tsx`
- Create: `frontend/src/modules/admin/AuditLogsPage.tsx`
- Create: `frontend/src/modules/admin/SettingsPage.tsx`
- Create: `frontend/src/modules/admin/SystemPages.test.tsx`
- Modify: `frontend/src/modules/admin/AdminPage.tsx`
- Modify: `frontend/src/components/AppShell.tsx`
- Modify: `frontend/src/styles/global.css`

**Interfaces:**
- Consumes: Task 5 admin API functions and types.
- Produces: the remaining three working pages and live shell branding/settings.

- [ ] **Step 1: Write failing page tests**

Test product/version rows and create drawers, audit filters and pagination parameters, settings load/save, and updated platform/environment labels in `AppShell` after settings cache refresh.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `cd frontend && pnpm test -- --run src/modules/admin/SystemPages.test.tsx`

Expected: FAIL because all three pages are absent.

- [ ] **Step 3: Implement the minimal pages and shell settings query**

Use the same table/drawer pattern. Format audit timestamps with `Intl.DateTimeFormat('zh-CN', { timeZone })`. Keep audit details read-only. Use a single vertical settings form and invalidate `['admin','settings']` after save so `AppShell` rerenders the brand and environment label.

- [ ] **Step 4: Run all frontend tests and build**

Run: `cd frontend && pnpm test -- --run && pnpm build`

Expected: all tests PASS and Vite build completes without TypeScript errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src
git commit -m "feat: complete system administration pages"
```

### Task 8: Integrated verification

**Files:**
- Modify only files required by failures discovered during verification.

**Interfaces:**
- Produces: verified end-to-end system administration behavior.

- [ ] **Step 1: Run the complete automated suite**

```bash
cd backend && mvn test
cd ../frontend && pnpm test -- --run && pnpm build
```

Expected: all commands exit 0.

- [ ] **Step 2: Start or reuse the local backend/frontend and run browser acceptance**

Log in as the demo administrator, visit all five `/admin/*` routes, perform one successful save on each writable page, refresh, and confirm persistence. Verify filters on audit logs. Confirm a non-admin cannot see the module and receives 403 from an admin API.

- [ ] **Step 3: Inspect the final diff and repository status**

Run: `git diff --check && git status --short && git log --oneline -10`

Expected: no whitespace errors; only intended user pre-existing files remain uncommitted.

- [ ] **Step 4: Commit any verification-only fixes**

```bash
git add <only-files-fixed-during-verification>
git commit -m "fix: resolve system administration acceptance issues"
```

