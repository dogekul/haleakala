# Project Task Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a lightweight Microsoft To Do-style task manager inside each project, with optional hour-level deadlines, project-member assignment, two-state completion, checklists, contextual links, and in-app reminders.

**Architecture:** Add a dedicated JDBC-backed project-task domain instead of reusing custom development tasks. Keep the backend in one focused service and one controller, expose nested project task endpoints plus current-user reminder endpoints, and add a self-contained React task workspace that plugs into the existing project detail and application shell.

**Tech Stack:** Java 1.8, Spring Boot, JdbcTemplate, Flyway, MySQL/H2, React, TypeScript, Ant Design, TanStack Query, Vitest, Testing Library.

## Global Constraints

- Do not add dependencies.
- Task states are only `TODO` and `DONE`.
- Title is the only required quick-create field; assignee defaults to the current user.
- Deadline is optional, stored and edited at hour precision, and may be added after completion.
- Tasks may optionally reference the current project's stage or milestone but never affect stage gates.
- All user-facing buttons, states, empty text, validation, and errors are Chinese.
- Task lists grow with the page and must not introduce a fixed-height inner scrolling region.
- Current project members can read and create; creator, assignee, project manager, ADMIN, and PMO can edit/complete/reopen; creator, project manager, ADMIN, and PMO can soft-delete.
- Existing unrelated workspace changes must remain untouched.

---

## File Structure

### Backend

- Create `backend/src/main/resources/db/migration/V24__project_tasks.sql`: task, checklist, and reminder tables plus indexes.
- Create `backend/src/main/java/com/zhilu/delivery/task/ProjectTaskService.java`: project access, task CRUD, filtering, checklist replacement, optimistic locking, and reminder rules.
- Create `backend/src/main/java/com/zhilu/delivery/task/ProjectTaskController.java`: request validation and project-task/current-user reminder HTTP endpoints.
- Create `backend/src/test/java/com/zhilu/delivery/task/ProjectTaskApiIT.java`: database behavior, API contract, permissions, completion, optional deadlines, and reminders.

### Frontend

- Create `frontend/src/modules/project/projectTaskTypes.ts`: task, checklist, filter, request, and reminder types.
- Create `frontend/src/modules/project/projectTaskApi.ts`: task and reminder HTTP calls.
- Create `frontend/src/modules/project/ProjectTasks.tsx`: filters, quick creation, date grouping, detail editor, completion, reopen, and soft deletion.
- Create `frontend/src/modules/project/ProjectTasks.test.tsx`: task workspace interaction tests.
- Create `frontend/src/components/TaskReminderBell.tsx`: polling badge and reminder dropdown.
- Create `frontend/src/components/TaskReminderBell.test.tsx`: current-user reminder interaction tests.
- Modify `frontend/src/modules/project/ProjectDetail.tsx`: add URL-addressable “项目任务” tab.
- Modify `frontend/src/components/AppShell.tsx`: place the reminder bell in the top bar.
- Modify `frontend/src/styles/global.css`: consistent three-column task layout, task rows, detail panel, and reminder styles without inner scrolling.

---

### Task 1: Persist Tasks and Enforce Core Domain Rules

**Files:**
- Create: `backend/src/main/resources/db/migration/V24__project_tasks.sql`
- Create: `backend/src/main/java/com/zhilu/delivery/task/ProjectTaskService.java`
- Create: `backend/src/test/java/com/zhilu/delivery/task/ProjectTaskApiIT.java`

**Interfaces:**
- Produces: `ProjectTaskService.list(long, String, CurrentUser)`, `create(long, CreateCommand, CurrentUser)`, `get(long, long, CurrentUser)`, `update(long, long, UpdateCommand, CurrentUser)`, `complete(long, long, CurrentUser)`, `reopen(long, long, CurrentUser)`, and `delete(long, long, CurrentUser)`.
- Produces: task maps with camel-case keys consumed by the controller and frontend.

- [ ] **Step 1: Write the failing persistence tests**

Add tests that create a project and call the wished-for service API:

```java
@Test
void createsTaskWithCurrentUserAndNoDeadline() {
  Map<String, Object> task = tasks.create(projectId,
      new ProjectTaskService.CreateCommand("联系客户确认范围", null, null), member);

  assertThat(task.get("title")).isEqualTo("联系客户确认范围");
  assertThat(task.get("assigneeUserId")).isEqualTo(member.getId());
  assertThat(task.get("dueAt")).isNull();
  assertThat(jdbc.queryForObject(
      "select count(*) from project_task_reminder where task_id=?",
      Integer.class, task.get("id"))).isZero();
}

@Test
void truncatesDeadlineToHourAndCreatesDefaultReminder() {
  Map<String, Object> task = tasks.create(projectId,
      new ProjectTaskService.CreateCommand("准备周报", assigneeId,
          LocalDateTime.of(2026, 7, 25, 18, 47)), member);

  assertThat(task.get("dueAt")).isEqualTo(LocalDateTime.of(2026, 7, 25, 18, 0));
  assertThat(task.get("reminderAt")).isEqualTo(LocalDateTime.of(2026, 7, 25, 17, 30));
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
cd backend
./mvnw -Dtest=ProjectTaskApiIT test
```

Expected: compilation fails because `ProjectTaskService` and its commands do not exist.

- [ ] **Step 3: Add the minimal schema**

Create three tables:

```sql
CREATE TABLE project_task (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  title VARCHAR(240) NOT NULL,
  description TEXT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'TODO',
  priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
  creator_user_id BIGINT NOT NULL,
  assignee_user_id BIGINT NOT NULL,
  due_at TIMESTAMP(6) NULL,
  stage_code VARCHAR(32) NULL,
  milestone_id BIGINT NULL,
  completed_by_user_id BIGINT NULL,
  completed_at TIMESTAMP(6) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  deleted_by_user_id BIGINT NULL,
  deleted_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT fk_task_org FOREIGN KEY (organization_id) REFERENCES organization(id),
  CONSTRAINT fk_task_project FOREIGN KEY (project_id) REFERENCES delivery_project(id),
  CONSTRAINT fk_task_creator FOREIGN KEY (creator_user_id) REFERENCES app_user(id),
  CONSTRAINT fk_task_assignee FOREIGN KEY (assignee_user_id) REFERENCES app_user(id),
  CONSTRAINT fk_task_milestone FOREIGN KEY (milestone_id) REFERENCES milestone(id)
);
```

Add `project_task_check_item` with `task_id`, `content`, `completed`, and `sort_order`; add `project_task_reminder` with `task_id`, `recipient_user_id`, `channel`, `remind_at`, and `read_at`. Add the three task-list indexes and foreign keys described in the design.

- [ ] **Step 4: Implement minimal service behavior**

Implement task creation and mapping using `JdbcTemplate` and `SimpleJdbcInsert`. Normalize the title and hour:

```java
private LocalDateTime hour(LocalDateTime value) {
  return value == null ? null : value.withMinute(0).withSecond(0).withNano(0);
}

private String title(String value) {
  String normalized = value == null ? "" : value.trim();
  if (normalized.isEmpty()) throw new IllegalArgumentException("任务标题不能为空");
  if (normalized.length() > 240) throw new IllegalArgumentException("任务标题不能超过240个字符");
  return normalized;
}
```

When `dueAt` is present for a new `TODO` task, insert one `IN_APP` reminder at `dueAt.minusMinutes(30)`. Validate that project, assignee, stage, and milestone all belong to the same organization/project.

- [ ] **Step 5: Verify GREEN**

Run:

```bash
cd backend
./mvnw -Dtest=ProjectTaskApiIT test
```

Expected: the creation, hour truncation, and default reminder tests pass.

- [ ] **Step 6: Add update, completion, reopen, deletion, and optimistic-lock tests**

Add tests proving:

```java
assertThat(tasks.complete(projectId, taskId, assignee).get("status")).isEqualTo("DONE");
assertThat(tasks.reopen(projectId, taskId, creator).get("status")).isEqualTo("TODO");
assertThatThrownBy(() -> tasks.update(projectId, taskId, staleCommand, creator))
    .isInstanceOf(ConflictException.class)
    .hasMessageContaining("任务已被其他成员更新");
```

Also assert that adding a deadline to an already completed task stores the hour but does not automatically create a reminder.

- [ ] **Step 7: Run tests and verify RED**

Run the same focused Maven test. Expected: failures because update, lifecycle, delete, and version behavior are missing.

- [ ] **Step 8: Implement the minimal lifecycle behavior**

Use guarded updates with `where id=? and project_id=? and version=? and deleted=false`. Replace checklist rows inside the same transaction as the task update. Record completion fields on `DONE`, clear them on reopen, and set soft-delete audit fields on delete.

- [ ] **Step 9: Run tests and commit**

Run:

```bash
cd backend
./mvnw -Dtest=ProjectTaskApiIT test
git add backend/src/main/resources/db/migration/V24__project_tasks.sql \
  backend/src/main/java/com/zhilu/delivery/task/ProjectTaskService.java \
  backend/src/test/java/com/zhilu/delivery/task/ProjectTaskApiIT.java
git commit -m "feat: add project task domain"
```

Expected: all `ProjectTaskApiIT` service tests pass and only the three task-domain files are committed.

---

### Task 2: Expose Task and Reminder APIs with Authorization

**Files:**
- Create: `backend/src/main/java/com/zhilu/delivery/task/ProjectTaskController.java`
- Modify: `backend/src/main/java/com/zhilu/delivery/task/ProjectTaskService.java`
- Modify: `backend/src/test/java/com/zhilu/delivery/task/ProjectTaskApiIT.java`

**Interfaces:**
- Consumes: `ProjectTaskService` methods from Task 1.
- Produces: `/api/v1/projects/{projectId}/tasks/**` and `/api/v1/task-reminders/**`.

- [ ] **Step 1: Write failing API and authorization tests**

Use authenticated MockMvc requests:

```java
mvc.perform(post("/api/v1/projects/{id}/tasks", projectId)
        .with(actor(member)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"title\":\"确认验收环境\"}"))
    .andExpect(status().isCreated())
    .andExpect(jsonPath("$.assigneeUserId").value(member.getId()))
    .andExpect(jsonPath("$.dueAt").doesNotExist());

mvc.perform(put("/api/v1/projects/{id}/tasks/{taskId}", projectId, taskId)
        .with(actor(unrelatedMember)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(updateJson))
    .andExpect(status().isForbidden());
```

Cover creator, assignee, project manager, PMO, unrelated project member, non-member, soft delete, unread reminder ownership, and mark-read ownership.

- [ ] **Step 2: Run test and verify RED**

Run:

```bash
cd backend
./mvnw -Dtest=ProjectTaskApiIT test
```

Expected: 404 because the controller endpoints do not exist.

- [ ] **Step 3: Implement request DTOs and endpoints**

Create controller request types:

```java
public static final class CreateRequest {
  @NotBlank public String title;
  public Long assigneeUserId;
  public LocalDateTime dueAt;
}

public static final class UpdateRequest {
  @NotBlank public String title;
  public String description;
  @NotBlank public String priority;
  @NotNull public Long assigneeUserId;
  public LocalDateTime dueAt;
  public String stageCode;
  public Long milestoneId;
  @NotNull public Boolean reminderEnabled;
  public LocalDateTime reminderAt;
  @NotNull public Long version;
  @Valid public List<CheckItemRequest> checklist = Collections.emptyList();
}
```

Map the exact endpoints from the design. Return `201` for create, `204` for delete/read, and current task JSON for mutations.

- [ ] **Step 4: Add service authorization and reminder queries**

Implement:

```java
public List<Map<String, Object>> unreadReminders(CurrentUser user)
public void markReminderRead(long reminderId, CurrentUser user)
```

The unread query returns only `recipient_user_id = current user`, `channel='IN_APP'`, `read_at is null`, `remind_at <= current_timestamp`, and non-deleted tasks. Editing requires `project:write` plus creator/assignee/manager/ADMIN/PMO authorization; delete excludes assignee-only authority.

- [ ] **Step 5: Run focused and project authorization tests**

Run:

```bash
cd backend
./mvnw -Dtest=ProjectTaskApiIT,ProjectAuthorizationIT test
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/zhilu/delivery/task/ProjectTaskController.java \
  backend/src/main/java/com/zhilu/delivery/task/ProjectTaskService.java \
  backend/src/test/java/com/zhilu/delivery/task/ProjectTaskApiIT.java
git commit -m "feat: expose project task APIs"
```

---

### Task 3: Build the Project Task Workspace

**Files:**
- Create: `frontend/src/modules/project/projectTaskTypes.ts`
- Create: `frontend/src/modules/project/projectTaskApi.ts`
- Create: `frontend/src/modules/project/ProjectTasks.tsx`
- Create: `frontend/src/modules/project/ProjectTasks.test.tsx`

**Interfaces:**
- Consumes: project task HTTP endpoints from Task 2, `Project`, `useAuth`, Ant Design, and TanStack Query.
- Produces: `<ProjectTasks project={project} selectedTaskId={number | undefined} />`.

- [ ] **Step 1: Write the failing quick-create and grouping tests**

Render the component with mocked fetch responses and assert:

```tsx
expect(screen.getByRole('textbox', { name: '任务标题' })).toBeVisible()
expect(screen.getByLabelText('负责人')).toHaveTextContent('交付工程师')
expect(screen.getByLabelText('截止时间')).not.toBeRequired()

await user.type(screen.getByRole('textbox', { name: '任务标题' }), '  确认上线窗口  {enter}')
expect(fetch).toHaveBeenCalledWith(expect.stringContaining('/tasks'), expect.objectContaining({
  method: 'POST',
  body: JSON.stringify({ title: '确认上线窗口', assigneeUserId: 7, dueAt: null }),
}))
```

Add assertions for “无截止时间”, “今天”, “已逾期”, “已完成”, and the absence of fixed-height/virtualized list behavior.

- [ ] **Step 2: Run test and verify RED**

Run:

```bash
cd frontend
npm run test:run -- src/modules/project/ProjectTasks.test.tsx
```

Expected: module-not-found failure for `ProjectTasks`.

- [ ] **Step 3: Add types and API calls**

Define:

```ts
export type ProjectTaskStatus = 'TODO' | 'DONE'
export type ProjectTaskPriority = 'LOW' | 'NORMAL' | 'HIGH'
export type ProjectTaskFilter = 'mine' | 'all' | 'today' | 'overdue' | 'completed'

export interface ProjectTask {
  id: number
  projectId: number
  title: string
  description?: string
  status: ProjectTaskStatus
  priority: ProjectTaskPriority
  creatorUserId: number
  assigneeUserId: number
  assigneeName: string
  dueAt?: string
  stageCode?: string
  milestoneId?: number
  completedAt?: string
  reminderAt?: string
  reminderEnabled: boolean
  checklist: ProjectTaskCheckItem[]
  checklistCompleted: number
  checklistTotal: number
  version: number
  canEdit: boolean
  canDelete: boolean
}
```

Expose list, create, load, update, complete, reopen, and remove API functions.

- [ ] **Step 4: Implement the minimal three-column workspace**

Use an Ant Design `Menu` or plain button list for filters, a natural-flow task list in the middle, and a `Card` detail editor on the right. Use `DatePicker` with `showTime={{ format: 'HH:00' }}` and minute/second step restrictions so values are sent as `YYYY-MM-DDTHH:00:00`.

On quick create, send `dueAt: null` when empty and default `assigneeUserId` to `me.id`. Group tasks with pure functions so tests cover hour-level today/overdue behavior.

- [ ] **Step 5: Run test and verify GREEN**

Run the focused Vitest command. Expected: quick-create and grouping tests pass.

- [ ] **Step 6: Add failing detail lifecycle tests**

Test editing checklist/title/assignee/deadline, completing, reopening, and deletion confirmation:

```tsx
await user.click(screen.getByRole('checkbox', { name: '完成：确认上线窗口' }))
expect(fetch).toHaveBeenCalledWith(expect.stringContaining('/complete'), expect.objectContaining({
  method: 'POST',
}))

await user.click(screen.getByRole('button', { name: '删除任务' }))
expect(await screen.findByText('确认删除该任务？')).toBeVisible()
```

- [ ] **Step 7: Run RED, implement details, then run GREEN**

Use one controlled Ant Design form for the selected task. Invalidate only `['project-tasks', project.id]`, the selected task query, and `['task-reminders']`. Preserve form values on error and show `ApiError.message`.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/modules/project/projectTaskTypes.ts \
  frontend/src/modules/project/projectTaskApi.ts \
  frontend/src/modules/project/ProjectTasks.tsx \
  frontend/src/modules/project/ProjectTasks.test.tsx
git commit -m "feat: add project task workspace"
```

---

### Task 4: Add the Global In-App Reminder Entry

**Files:**
- Create: `frontend/src/components/TaskReminderBell.tsx`
- Create: `frontend/src/components/TaskReminderBell.test.tsx`
- Modify: `frontend/src/components/AppShell.tsx`

**Interfaces:**
- Consumes: `projectTaskApi.unreadReminders()` and `projectTaskApi.readReminder(id)`.
- Produces: top-bar bell linking to `/projects/{projectId}?tab=tasks&taskId={taskId}`.

- [ ] **Step 1: Write the failing reminder test**

```tsx
expect(await screen.findByRole('button', { name: '任务提醒，1条未读' })).toBeVisible()
await user.click(screen.getByRole('button', { name: '任务提醒，1条未读' }))
expect(screen.getByText('准备项目周报')).toBeVisible()
expect(screen.getByRole('link', { name: '查看任务' }))
  .toHaveAttribute('href', '/projects/12?tab=tasks&taskId=88')
```

Also test “标记已读” sends the reminder read request and removes the item.

- [ ] **Step 2: Run test and verify RED**

```bash
cd frontend
npm run test:run -- src/components/TaskReminderBell.test.tsx
```

Expected: module-not-found failure.

- [ ] **Step 3: Implement minimal polling and dropdown**

Use TanStack Query with:

```ts
useQuery({
  queryKey: ['task-reminders'],
  queryFn: projectTaskApi.unreadReminders,
  refetchInterval: 60_000,
})
```

Render an Ant Design `Badge` around a Chinese aria-labeled bell button and a `Dropdown` containing project name, task title, deadline, “查看任务”, and “标记已读”. Do not create a separate notification page.

- [ ] **Step 4: Add the bell to AppShell and verify**

Place `<TaskReminderBell />` before the environment label. Run:

```bash
cd frontend
npm run test:run -- src/components/TaskReminderBell.test.tsx src/components/AppShell.test.tsx
```

Expected: both suites pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/TaskReminderBell.tsx \
  frontend/src/components/TaskReminderBell.test.tsx \
  frontend/src/components/AppShell.tsx
git commit -m "feat: add task reminder entry"
```

---

### Task 5: Integrate the Task Tab, Styling, and Full Regression

**Files:**
- Modify: `frontend/src/modules/project/ProjectDetail.tsx`
- Modify: `frontend/src/modules/project/ProjectTasks.test.tsx`
- Modify: `frontend/src/styles/global.css`
- Modify: `backend/src/test/java/com/zhilu/delivery/SchemaBaselineTest.java`

**Interfaces:**
- Consumes: `<ProjectTasks>` and reminder link query parameters.
- Produces: URL-addressable project task tab and production-ready responsive styling.

- [ ] **Step 1: Write the failing project-tab navigation test**

Render `/projects/9?tab=tasks&taskId=33`, mock the project and task endpoints, then assert:

```tsx
expect(await screen.findByRole('tab', { name: /项目任务/ })).toHaveAttribute('aria-selected', 'true')
expect(await screen.findByDisplayValue('来自提醒的任务')).toBeVisible()
```

- [ ] **Step 2: Run test and verify RED**

```bash
cd frontend
npm run test:run -- src/modules/project/ProjectTasks.test.tsx
```

Expected: the project detail still defaults to the lifecycle tab.

- [ ] **Step 3: Integrate URL-driven tab state**

Read `tab` and `taskId` with `useSearchParams`, initialize the active tab from the URL, and add:

```tsx
{
  key: 'tasks',
  label: <span><CheckSquareOutlined /> 项目任务</span>,
  children: <ProjectTasks project={project} selectedTaskId={selectedTaskId} />,
}
```

When the user switches tabs, update only the `tab` query parameter and retain `taskId` only for the task tab.

- [ ] **Step 4: Add responsive, natural-flow styles**

Append task-specific classes to `global.css`:

```css
.project-task-layout {
  display: grid;
  grid-template-columns: 180px minmax(360px, 1fr) minmax(320px, 420px);
  gap: 16px;
  align-items: start;
}

.project-task-detail {
  position: sticky;
  top: 72px;
}

@media (max-width: 1180px) {
  .project-task-layout { grid-template-columns: 160px 1fr; }
  .project-task-detail { grid-column: 1 / -1; position: static; }
}
```

Do not add `height`, `max-height`, or `overflow-y` to task list/detail containers.

- [ ] **Step 5: Extend schema baseline coverage**

Add `project_task`, `project_task_check_item`, and `project_task_reminder` to the expected tables and foreign-key checks in `SchemaBaselineTest`.

- [ ] **Step 6: Run focused tests**

```bash
cd backend
./mvnw -Dtest=ProjectTaskApiIT,ProjectAuthorizationIT,SchemaBaselineTest test
cd ../frontend
npm run test:run -- src/modules/project/ProjectTasks.test.tsx \
  src/components/TaskReminderBell.test.tsx src/components/AppShell.test.tsx
```

Expected: all focused tests pass.

- [ ] **Step 7: Run full verification**

```bash
cd backend
./mvnw test
cd ../frontend
npm run test:run
npm run build
```

Expected: Maven, Vitest, TypeScript, and Vite all complete with zero failures.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/modules/project/ProjectDetail.tsx \
  frontend/src/modules/project/ProjectTasks.test.tsx \
  frontend/src/styles/global.css \
  backend/src/test/java/com/zhilu/delivery/SchemaBaselineTest.java
git commit -m "feat: integrate project task management"
```

---

## Final Verification

- Confirm quick creation works with title only.
- Confirm assignee defaults to the current user and can be changed to another project member.
- Confirm deadline remains optional, stores hour precision, and can be added after completion.
- Confirm no-deadline tasks do not generate reminders.
- Confirm task editor and deletion permissions match the approved matrix.
- Confirm reminder links open the correct task tab and task.
- Confirm all visible copy is Chinese.
- Confirm task list and detail do not use inner scrolling.
- Confirm no dependency files changed.
- Confirm unrelated pre-existing workspace changes were not committed.
