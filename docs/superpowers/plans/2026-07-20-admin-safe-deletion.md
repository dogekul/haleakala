# Admin Safe Deletion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为系统管理中的用户、团队和角色补齐可见、可确认、可审计的安全删除能力。

**Architecture:** 在现有 `AdminIamController` 与 `IamAdminService` 中增加三个 DELETE 端点，由服务层先验证组织边界和保护规则，再执行最小范围物理删除。前端沿用 React Query 和 Ant Design，在列表或卡片中提供中文危险操作和二次确认；所有成功删除都刷新对应查询，后端冲突原因原样展示。

**Tech Stack:** Java 1.8、Spring Boot、JdbcTemplate、MockMvc、React、TypeScript、TanStack Query、Ant Design、Vitest、Testing Library

## Global Constraints

- 不新增数据库迁移或第三方依赖。
- 不级联删除项目、商机、文档、审计等业务历史。
- 当前登录用户不可删除；已有业务引用的用户只能停用。
- 有直属用户、成员关系或下级团队的团队不可删除。
- 内置角色和已分配给用户的角色不可删除。
- 删除成功返回 HTTP 204，并写入对应审计事件。
- 前端按钮与反馈全部使用中文，并在删除前二次确认。

---

### Task 1: 后端安全删除端点

**Files:**
- Modify: `backend/src/test/java/com/zhilu/delivery/iam/AdminIamControllerTest.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/iam/api/AdminIamController.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/iam/service/IamAdminService.java`

**Interfaces:**
- Consumes: `CurrentUser#getId()`、`CurrentUser#getOrganizationId()`、现有 `AuditService#record(...)` 和全局异常映射。
- Produces: `void deleteUser(CurrentUser actor, long userId)`、`void deleteTeam(long organizationId, long teamId)`、`void deleteRole(long roleId)`；DELETE `/api/v1/admin/users/{id}`、`/teams/{id}`、`/roles/{id}`。

- [ ] **Step 1: 写成功删除和保护规则的失败测试**

在 `AdminIamControllerTest` 中引入 `MockMvcRequestBuilders.delete`，插入独立用户、团队和非内置角色，断言三个 DELETE 端点返回 204、目标记录归零、审计 action 分别为 `USER_DELETED`、`TEAM_DELETED`、`ROLE_DELETED`。再增加以下冲突断言：删除当前用户返回 409；删除有直属用户或子团队的团队返回 409；删除内置角色或已分配角色返回 409；删除被业务表引用的用户返回 409 且身份关系没有被部分删除。

```java
mockMvc.perform(delete("/api/v1/admin/users/{id}", deletableUserId)
        .with(user("admin").roles("ADMIN")))
    .andExpect(status().isNoContent());
mockMvc.perform(delete("/api/v1/admin/teams/{id}", deletableTeamId)
        .with(user("admin").roles("ADMIN")))
    .andExpect(status().isNoContent());
mockMvc.perform(delete("/api/v1/admin/roles/{id}", customRoleId)
        .with(user("admin").roles("ADMIN")))
    .andExpect(status().isNoContent());
mockMvc.perform(delete("/api/v1/admin/users/{id}", 300L)
        .with(user("admin").roles("ADMIN")))
    .andExpect(status().isConflict());
```

- [ ] **Step 2: 运行后端测试并确认红灯**

Run: `cd backend && ./mvnw -Dtest=AdminIamControllerTest test`

Expected: FAIL，三个 DELETE 路径返回 405 或缺少对应服务方法。

- [ ] **Step 3: 实现服务层保护和最小删除**

在 `IamAdminService` 中实现三个事务方法。用户删除先验证当前用户和组织，再清理 `user_role`、`user_team`、`sso_identity`；删除 `app_user` 的 `DataIntegrityViolationException` 转换为 `ConflictException("用户已有业务记录，不能删除；请停用用户")`，事务回滚已清理关系。团队删除前分别查询同组织下的 `app_user.primary_team_id`、`user_team` 和 `team.parent_id`；角色删除前查询 `built_in` 与 `user_role`，通过后删除 `role_permission` 和 `role`。

```java
@Transactional
public void deleteRole(long roleId) {
  List<Boolean> builtIn = jdbc.queryForList(
      "select built_in from role where id=?", Boolean.class, roleId);
  if (builtIn.isEmpty()) throw new NotFoundException("角色不存在");
  if (Boolean.TRUE.equals(builtIn.get(0))) throw new ConflictException("内置角色不能删除");
  Integer assigned = jdbc.queryForObject(
      "select count(*) from user_role where role_id=?", Integer.class, roleId);
  if (assigned != null && assigned > 0) throw new ConflictException("角色已分配给用户，不能删除");
  jdbc.update("delete from role_permission where role_id=?", roleId);
  jdbc.update("delete from role where id=?", roleId);
}
```

- [ ] **Step 4: 暴露 DELETE 端点并记录审计**

在 `AdminIamController` 引入 `DeleteMapping`，三个方法均加 `@ResponseStatus(HttpStatus.NO_CONTENT)` 和 `@Transactional`；调用服务后分别记录 `USER_DELETED`、`TEAM_DELETED`、`ROLE_DELETED`。

```java
@DeleteMapping("/users/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)
@Transactional
public void deleteUser(@PathVariable long id,
    @AuthenticationPrincipal CurrentUser user) {
  admin.deleteUser(user, id);
  record(user, "USER_DELETED", "USER", id, "删除用户");
}
```

- [ ] **Step 5: 运行后端测试并确认绿灯**

Run: `cd backend && ./mvnw -Dtest=AdminIamControllerTest test`

Expected: PASS，且删除失败用例中的关系数据保持不变。

- [ ] **Step 6: 提交后端变更**

```bash
git add backend/src/main/java/com/zhilu/delivery/iam/api/AdminIamController.java backend/src/main/java/com/zhilu/delivery/iam/service/IamAdminService.java backend/src/test/java/com/zhilu/delivery/iam/AdminIamControllerTest.java
git commit -m "feat: add safe admin deletion endpoints"
```

### Task 2: 前端删除 API 与交互

**Files:**
- Modify: `frontend/src/modules/admin/adminApi.test.ts`
- Modify: `frontend/src/modules/admin/UsersTeamsPage.test.tsx`
- Modify: `frontend/src/modules/admin/AdminFlows.test.tsx`
- Modify: `frontend/src/modules/admin/adminApi.ts`
- Modify: `frontend/src/modules/admin/UsersTeamsPage.tsx`
- Modify: `frontend/src/modules/admin/RolesPage.tsx`

**Interfaces:**
- Consumes: Task 1 的三个 HTTP 204 DELETE 端点，以及现有 `api<void>`、React Query 查询键 `admin-users`、`admin-teams`、`admin-roles`。
- Produces: `adminApi.deleteUser(id: number)`、`deleteTeam(id: number)`、`deleteRole(id: number)`，以及用户、团队、角色界面的中文删除操作。

- [ ] **Step 1: 写 API 合约和页面交互失败测试**

在 `adminApi.test.ts` 断言三个新方法使用 DELETE。用户/团队页面测试提供可删除数据，点击带实体名的删除按钮并确认，断言请求路径及列表刷新。角色流程测试同时提供 `builtIn: false` 与 `builtIn: true` 角色，断言非内置角色可确认删除、内置角色的删除按钮禁用且提示保护原因。

```ts
await adminApi.deleteUser(11)
expect(fetch).toHaveBeenCalledWith('/api/v1/admin/users/11', expect.objectContaining({ method: 'DELETE' }))
await adminApi.deleteTeam(22)
expect(fetch).toHaveBeenCalledWith('/api/v1/admin/teams/22', expect.objectContaining({ method: 'DELETE' }))
await adminApi.deleteRole(33)
expect(fetch).toHaveBeenCalledWith('/api/v1/admin/roles/33', expect.objectContaining({ method: 'DELETE' }))
```

- [ ] **Step 2: 运行前端目标测试并确认红灯**

Run: `cd frontend && npm test -- --run src/modules/admin/adminApi.test.ts src/modules/admin/UsersTeamsPage.test.tsx src/modules/admin/AdminFlows.test.tsx`

Expected: FAIL，`deleteUser`、`deleteTeam`、`deleteRole` 不存在，页面找不到删除按钮。

- [ ] **Step 3: 添加前端 DELETE API**

在 `adminApi.ts` 增加以下方法，不发送 body：

```ts
deleteUser: (id: number) => api<void>(`/api/v1/admin/users/${id}`, { method: 'DELETE' }),
deleteTeam: (id: number) => api<void>(`/api/v1/admin/teams/${id}`, { method: 'DELETE' }),
deleteRole: (id: number) => api<void>(`/api/v1/admin/roles/${id}`, { method: 'DELETE' }),
```

- [ ] **Step 4: 实现用户与团队删除交互**

在 `UsersTeamsPage` 中增加两个 mutation；成功后分别刷新 `admin-users` 或 `admin-teams` 并提示“用户已删除”或“团队已删除”，失败时展示后端中文原因。操作列增加带 `DeleteOutlined` 的 danger link，通过 `Popconfirm` 询问“确认删除用户…？”或“确认删除团队…？”，按钮 aria-label 包含实体名称以便无歧义操作。

```tsx
<Popconfirm title={`确认删除用户“${item.displayName}”？`} description="删除后无法恢复。已有业务记录的用户请改为停用。" onConfirm={() => deleteUser.mutate(item.id)}>
  <Button type="link" danger size="small" icon={<DeleteOutlined />} aria-label={`删除用户${item.displayName}`}>删除</Button>
</Popconfirm>
```

- [ ] **Step 5: 实现角色删除与内置保护提示**

在 `RolesPage` 增加角色删除 mutation。非内置角色显示 danger 按钮和确认框；内置角色显示禁用的“内置角色不可删除”按钮。删除成功刷新 `admin-roles` 并关闭可能打开的该角色权限抽屉。

```tsx
{role.builtIn ? (
  <Button block danger disabled icon={<DeleteOutlined />}>内置角色不可删除</Button>
) : (
  <Popconfirm title={`确认删除角色“${role.name}”？`} description="已分配给用户的角色不能删除。" onConfirm={() => remove.mutate(role.id)}>
    <Button block danger icon={<DeleteOutlined />} aria-label={`删除角色${role.name}`}>删除角色</Button>
  </Popconfirm>
)}
```

- [ ] **Step 6: 运行前端目标测试和构建**

Run: `cd frontend && npm test -- --run src/modules/admin/adminApi.test.ts src/modules/admin/UsersTeamsPage.test.tsx src/modules/admin/AdminFlows.test.tsx`

Expected: PASS。

Run: `cd frontend && npm run build`

Expected: PASS，无 TypeScript 错误。

- [ ] **Step 7: 提交前端变更**

```bash
git add frontend/src/modules/admin/adminApi.ts frontend/src/modules/admin/adminApi.test.ts frontend/src/modules/admin/UsersTeamsPage.tsx frontend/src/modules/admin/UsersTeamsPage.test.tsx frontend/src/modules/admin/RolesPage.tsx frontend/src/modules/admin/AdminFlows.test.tsx
git commit -m "feat: add admin deletion controls"
```

### Task 3: 集成、部署与页面验收

**Files:**
- Verify only: `compose.yaml`
- Verify only: `frontend/src/modules/admin/UsersTeamsPage.tsx`
- Verify only: `frontend/src/modules/admin/RolesPage.tsx`

**Interfaces:**
- Consumes: Tasks 1–2 已通过测试的 API 与页面。
- Produces: 本地 main 分支中的完整功能，以及运行中的最新前后端容器。

- [ ] **Step 1: 运行后端完整测试和前端完整测试**

Run: `cd backend && ./mvnw test`

Expected: PASS。

Run: `cd frontend && npm test -- --run`

Expected: PASS；若仓库已有并行测试波动，则逐文件复跑失败文件并记录结果，不掩盖本次回归。

- [ ] **Step 2: 检查变更范围和保护语义**

Run: `git diff main...HEAD --check && git diff --stat main...HEAD`

Expected: 无空白错误；仅包含本计划列出的后端、前端和测试文件。

- [ ] **Step 3: 合并到本地 main 并重建服务**

```bash
git switch main
git merge --ff-only codex/admin-safe-deletion
docker compose -p zhilu-delivery-main -f compose.yaml build backend frontend
docker compose -p zhilu-delivery-main -f compose.yaml up -d --no-deps backend frontend
docker compose -p zhilu-delivery-main -f compose.yaml ps
```

Expected: backend、frontend 均为 Up/healthy，且用户原有未提交文件保持不变。

- [ ] **Step 4: 浏览器验收**

打开 `/admin/users`，确认用户和团队操作列均有中文“删除”按钮、确认文案完整、受引用数据失败时显示中文冲突原因；打开 `/admin/roles`，确认非内置角色可删除，内置角色显示不可删除状态，卡片布局没有溢出或错位。

- [ ] **Step 5: 最终提交检查**

Run: `git status --short && git log -3 --oneline`

Expected: 仅保留用户原有的 `OpenAiCompatibleClient` 相关修改和两个未跟踪 Markdown 文件；main 包含本功能提交。
