# Detail Back Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将商机、项目、客户运营和产品详情页的返回入口统一为清晰、可聚焦的浅灰描边导航按钮。

**Architecture:** 保留四处现有 React Router `Link` 和目标路由，仅为链接统一增加 `detail-back-link` 类，并在全局样式中集中定义视觉状态。现有页面容器结构保持不变，不新增组件或依赖。

**Tech Stack:** React 18、React Router、TypeScript、Ant Design、CSS、Vitest、Testing Library

## Global Constraints

- 返回入口高度为 32px，采用浅灰描边、白色背景和圆角。
- 悬停时使用浅蓝背景、蓝色边框和蓝色文字。
- 键盘聚焦时显示清晰的蓝色焦点环。
- 保留现有文案、路由和 `Link` 语义。
- 仅修改四个详情页的返回入口，不调整业务操作按钮，不新增依赖。

---

### Task 1: 统一四个详情页返回导航入口

**Files:**
- Modify: `frontend/src/modules/customer-center/OpportunityPages.test.tsx`
- Modify: `frontend/src/modules/customer-center/DeliveryAndOperationPages.test.tsx`
- Modify: `frontend/src/modules/project/ProjectDocuments.test.tsx`
- Modify: `frontend/src/modules/product/ProductDetailPage.test.tsx`
- Modify: `frontend/src/modules/customer-center/OpportunityDetailPage.tsx`
- Modify: `frontend/src/modules/customer-center/OperationDetailPage.tsx`
- Modify: `frontend/src/modules/project/ProjectDetail.tsx`
- Modify: `frontend/src/modules/product/ProductDetailPage.tsx`
- Modify: `frontend/src/styles/global.css`

**Interfaces:**
- Consumes: React Router `Link` 的 `to`、`className` 与可访问名称。
- Produces: 四处返回链接共同使用的 CSS 类 `detail-back-link`。

- [ ] **Step 1: 为四个详情页增加失败的共享样式契约断言**

在各页面现有渲染测试中加入对应断言：

```tsx
expect(screen.getByRole('link', { name: '返回商机总览' }))
  .toHaveClass('detail-back-link')
expect(screen.getByRole('link', { name: '返回客户运营' }))
  .toHaveClass('detail-back-link')
expect(screen.getByRole('link', { name: '返回项目列表' }))
  .toHaveClass('detail-back-link')
expect(screen.getByRole('link', { name: '返回产品中心' }))
  .toHaveClass('detail-back-link')
```

- [ ] **Step 2: 运行定向测试并确认因缺少共享类而失败**

Run:

```bash
cd frontend && npm test -- --run src/modules/customer-center/OpportunityPages.test.tsx src/modules/customer-center/DeliveryAndOperationPages.test.tsx src/modules/project/ProjectDocuments.test.tsx src/modules/product/ProductDetailPage.test.tsx
```

Expected: FAIL，四处断言收到原有 `detail-back`、`product-detail-back` 或空类名，而不是 `detail-back-link`。

- [ ] **Step 3: 为四处链接增加共享类**

将四个链接统一为以下形式：

```tsx
<Link className="detail-back-link" to="/customers/opportunities"><ArrowLeftOutlined /> 返回商机总览</Link>
<Link className="detail-back-link" to="/customers/operations"><ArrowLeftOutlined /> 返回客户运营</Link>
<Link className="detail-back-link" to="/products"><ArrowLeftOutlined /> 返回产品中心</Link>
```

项目详情保留原有布局容器：

```tsx
<div className="detail-back">
  <Link className="detail-back-link" to="/projects">
    <ArrowLeftOutlined /> 返回项目列表
  </Link>
</div>
```

- [ ] **Step 4: 添加最小共享样式并移除重复链接样式**

在 `frontend/src/styles/global.css` 中定义：

```css
.detail-back-link {
  display: inline-flex;
  min-height: 32px;
  align-items: center;
  gap: 7px;
  padding: 5px 11px;
  border: 1px solid #dfe1e5;
  border-radius: 7px;
  color: #4e5969;
  background: #fff;
  font-size: 13px;
  line-height: 20px;
  transition: color .16s ease, border-color .16s ease, background-color .16s ease;
}
.detail-back-link:hover {
  border-color: #8fb1ff;
  color: #245bdb;
  background: #f2f6ff;
}
.detail-back-link:focus-visible {
  outline: 2px solid rgba(51,112,255,.28);
  outline-offset: 2px;
}
```

保留 `.detail-back { margin-bottom: 14px; }` 作为项目详情容器间距；为直接放置链接的页面使用：

```css
a.detail-back-link { margin-bottom: 14px; }
```

删除 `.product-detail-back` 原有的重复布局规则。

- [ ] **Step 5: 运行定向测试并确认通过**

Run:

```bash
cd frontend && npm test -- --run src/modules/customer-center/OpportunityPages.test.tsx src/modules/customer-center/DeliveryAndOperationPages.test.tsx src/modules/project/ProjectDocuments.test.tsx src/modules/product/ProductDetailPage.test.tsx
```

Expected: PASS，四个测试文件均无失败。

- [ ] **Step 6: 运行完整前端测试和生产构建**

Run:

```bash
cd frontend && npm test -- --run && npm run build
```

Expected: 所有测试 PASS，Vite 生产构建退出码为 0。

- [ ] **Step 7: 提交实现**

```bash
git add frontend/src/modules/customer-center/OpportunityPages.test.tsx \
  frontend/src/modules/customer-center/DeliveryAndOperationPages.test.tsx \
  frontend/src/modules/project/ProjectDocuments.test.tsx \
  frontend/src/modules/product/ProductDetailPage.test.tsx \
  frontend/src/modules/customer-center/OpportunityDetailPage.tsx \
  frontend/src/modules/customer-center/OperationDetailPage.tsx \
  frontend/src/modules/project/ProjectDetail.tsx \
  frontend/src/modules/product/ProductDetailPage.tsx \
  frontend/src/styles/global.css
git commit -m "style: unify detail back navigation"
```

### Task 2: 更新并核验本地页面

**Files:**
- Verify only: `compose.yaml`

**Interfaces:**
- Consumes: `zhilu-delivery-main` Compose 项目和前端镜像。
- Produces: `http://localhost:53990` 上加载最新构建的健康前端容器。

- [ ] **Step 1: 重建并替换前端容器**

Run:

```bash
docker compose -p zhilu-delivery-main -f compose.yaml build frontend
docker compose -p zhilu-delivery-main -f compose.yaml up -d --no-deps frontend
```

Expected: 前端镜像构建成功，容器完成 recreate 并进入 healthy 状态。

- [ ] **Step 2: 检查四处返回导航**

依次打开商机详情、项目详情、客户运营详情和产品详情，确认：

- 四处均为浅灰描边按钮形态。
- 悬停时背景、边框和文字变为蓝色反馈。
- Tab 聚焦时存在清晰焦点环。
- 文案和目标路由保持不变。

- [ ] **Step 3: 核对工作区边界**

Run:

```bash
git status --short
```

Expected: 本任务文件已提交；用户原有后端修改和 Markdown 文件仍保持原状。
