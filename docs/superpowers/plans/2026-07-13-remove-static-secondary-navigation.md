# Remove Static Secondary Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the non-functional secondary navigation column and let the workspace use the released width while preserving all working primary navigation.

**Architecture:** Simplify `AppShell` from a three-column shell to a two-column shell. Delete secondary-navigation-only data and markup at the component boundary, then remove its orphaned CSS rather than hiding it.

**Tech Stack:** React 18, TypeScript, React Router, Ant Design, Vitest, Testing Library, CSS Grid

## Global Constraints

- Keep the permission-filtered primary module navigation unchanged.
- Do not add secondary routes or change backend APIs.
- Do not modify unrelated pages or existing uncommitted deployment work.
- Add no dependencies.

---

### Task 1: Remove the static secondary navigation

**Files:**
- Modify: `frontend/src/components/AppShell.test.tsx:21-35`
- Modify: `frontend/src/components/AppShell.tsx:1-119`
- Modify: `frontend/src/styles/global.css:14-133,291-295`

**Interfaces:**
- Consumes: `AuthState.me.permissions`, `useLocation().pathname`, and the existing `modules` primary-navigation metadata.
- Produces: the existing `AppShell({ children }: { children?: ReactNode })` component with only the primary rail and workspace columns.

- [ ] **Step 1: Write the failing component assertion**

Add these assertions to the existing `只显示当前用户有权访问的模块入口` test after the primary-navigation assertions:

```tsx
expect(screen.queryByRole('button', { name: '项目列表' })).not.toBeInTheDocument()
expect(document.querySelector('.section-sidebar')).not.toBeInTheDocument()
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
cd frontend && pnpm test:run src/components/AppShell.test.tsx
```

Expected: FAIL because the current shell still renders the `项目列表` button and `.section-sidebar`.

- [ ] **Step 3: Delete secondary-navigation-only component code**

In `AppShell.tsx`, remove `AppstoreOutlined` and `ControlOutlined`, remove `short` and `menus` from `ModuleNav`, and reduce every module entry to the primary navigation fields:

```tsx
interface ModuleNav {
  path: string
  label: string
  permission: string
  icon: ReactNode
}

const modules: ModuleNav[] = [
  { path: '/dashboard', label: '驾驶舱', permission: 'dashboard:read', icon: <DashboardOutlined /> },
  { path: '/projects', label: '项目空间', permission: 'project:read', icon: <FolderOpenOutlined /> },
  { path: '/requirements', label: '需求工坊', permission: 'requirement:read', icon: <ToolOutlined /> },
  { path: '/standardization', label: '标准化中心', permission: 'standardization:read', icon: <ProductOutlined /> },
  { path: '/knowledge', label: '知识库', permission: 'knowledge:read', icon: <BookOutlined /> },
  { path: '/resources', label: '资源中心', permission: 'resource:read', icon: <TeamOutlined /> },
  { path: '/admin', label: '系统管理', permission: 'system:manage', icon: <SettingOutlined /> },
]
```

Delete the complete `<aside className="section-sidebar">...</aside>` block. Replace the topbar title block with only the current primary module title:

```tsx
<div>
  <strong>{active?.label ?? '工作台'}</strong>
</div>
```

- [ ] **Step 4: Delete orphaned sidebar CSS and switch to two columns**

Change the main grid and narrow-screen grid to:

```css
.app-shell {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  min-height: 100vh;
  background: #f5f6f7;
}

@media (max-width: 1280px) {
  .app-shell { grid-template-columns: 80px minmax(0, 1fr); }
  .page-content { padding-inline: 22px; }
}
```

Delete all `.section-sidebar`, `.section-brand`, `.section-title`, `.section-nav`, `.section-footer`, and `.topbar-path` rules.

- [ ] **Step 5: Run focused and full frontend verification**

Run:

```bash
cd frontend && pnpm test:run src/components/AppShell.test.tsx
cd frontend && pnpm test:run
cd frontend && pnpm build
```

Expected: the focused test passes, the complete frontend suite passes, and TypeScript/Vite build exits with status 0.

- [ ] **Step 6: Verify the live preview**

Refresh `http://127.0.0.1:53991/dashboard` and confirm that the white secondary sidebar is absent, the workspace fills the remaining width, and the primary module links remain usable.

- [ ] **Step 7: Commit the implementation**

```bash
git add frontend/src/components/AppShell.test.tsx frontend/src/components/AppShell.tsx frontend/src/styles/global.css
git commit -m "fix: remove static secondary navigation"
```
