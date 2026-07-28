# 全局可搜索下拉框一致性 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 统一动态实体下拉框的搜索和选择保持行为，并消除筛选请求导致的页面闪烁。

**Architecture:** 使用一个保持 Ant Design `Select` 表单协议的薄封装统一搜索行为，只缓存当前已选选项；服务端筛选页面使用 React Query 上一帧数据和独立筛选维度数据源。固定枚举仍使用普通 `Select`。

**Tech Stack:** React 18、TypeScript、Ant Design 5、TanStack Query 5、Vitest、Testing Library

## Global Constraints

- 不新增第三方依赖。
- 表单和 API 继续提交原始标量 ID，不使用 `labelInValue`。
- 只在上游实体变化时清空其直接依赖项。
- 固定状态、阶段和布尔枚举继续使用普通 `Select`。

---

### Task 1: 共享可搜索选择器

**Files:**
- Create: `frontend/src/components/SearchSelect.tsx`
- Create: `frontend/src/components/SearchSelect.test.tsx`

**Interfaces:**
- Consumes: Ant Design `SelectProps<ValueType, DefaultOptionType>`.
- Produces: `SearchSelect<ValueType>`，可直接作为 `Form.Item` 的子控件使用。

- [ ] **Step 1: 写入失败测试**

测试真实表单行为：输入 `cp 001` 能匹配 `CP-001 · 消保合规`，选择后提交得到数字 ID；重新渲染为 `loading` 且 `options=[]` 时仍显示已选标签；清空表单值后旧选项不再出现。

- [ ] **Step 2: 运行测试确认红灯**

Run: `pnpm exec vitest run src/components/SearchSelect.test.tsx --pool=threads --maxWorkers=1`

Expected: FAIL，因为 `SearchSelect` 尚不存在。

- [ ] **Step 3: 最小实现**

实现以下公开行为：

```tsx
export function SearchSelect<ValueType = unknown>(
  props: SelectProps<ValueType, DefaultOptionType>,
) {
  return <Select
    showSearch
    allowClear
    virtual={false}
    optionFilterProp="label"
    filterOption={matchSearchOption}
    {...props}
    options={optionsWithSelectedFallback}
  />
}
```

`matchSearchOption` 对输入和标签执行小写化并移除空白及 `·_-`。缓存只补充 `props.value` 当前引用的选项。

- [ ] **Step 4: 运行组件测试确认绿灯**

Run: `pnpm exec vitest run src/components/SearchSelect.test.tsx --pool=threads --maxWorkers=1`

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/components/SearchSelect.tsx frontend/src/components/SearchSelect.test.tsx
git commit -m "feat: add stable searchable select"
```

### Task 2: 服务端筛选页面防闪烁

**Files:**
- Modify: `frontend/src/modules/customer-center/OpportunityOverviewPage.tsx`
- Modify: `frontend/src/modules/customer-center/OperationBoardPage.tsx`
- Modify: `frontend/src/modules/customer-center/OpportunityPages.test.tsx`
- Modify: `frontend/src/modules/customer-center/DeliveryAndOperationPages.test.tsx`

**Interfaces:**
- Consumes: `SearchSelect`、TanStack Query `keepPreviousData`.
- Produces: 稳定的商机和运营筛选界面；筛选维度不受当前结果集裁剪。

- [ ] **Step 1: 写入失败页面测试**

在延迟返回第二次筛选请求时断言：

```tsx
expect(screen.getByText('商机一')).toBeInTheDocument()
expect(screen.getByRole('combobox', { name: '客户筛选' })).toHaveValue()
```

并验证选中客户后其他完整维度选项仍可搜索。

- [ ] **Step 2: 运行测试确认红灯**

Run: `pnpm exec vitest run frontend/src/modules/customer-center/OpportunityPages.test.tsx frontend/src/modules/customer-center/DeliveryAndOperationPages.test.tsx --pool=threads --maxWorkers=1`

Expected: FAIL，筛选查询期间旧主体被卸载或筛选维度被裁剪。

- [ ] **Step 3: 最小实现**

主查询增加：

```ts
placeholderData: keepPreviousData
```

新增未筛选维度查询，所有客户、产品、负责人候选从该查询构建；动态筛选下拉框改用 `SearchSelect`。

- [ ] **Step 4: 运行页面测试确认绿灯**

Run: `pnpm exec vitest run src/modules/customer-center/OpportunityPages.test.tsx src/modules/customer-center/DeliveryAndOperationPages.test.tsx --pool=threads --maxWorkers=1`

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/modules/customer-center
git commit -m "fix: keep CRM filters stable while loading"
```

### Task 3: 迁移动态实体选择器

**Files:**
- Modify: `frontend/src/modules/dashboard/DashboardPage.tsx`
- Modify: `frontend/src/modules/project/ProjectWorkspace.tsx`
- Modify: `frontend/src/modules/customer-center/OpportunityEditor.tsx`
- Modify: `frontend/src/modules/customer-center/OperationEditor.tsx`
- Modify: `frontend/src/modules/customer-center/PresaleBoardPage.tsx`
- Modify: `frontend/src/modules/customer-center/ImplementationCockpitPage.tsx`
- Modify: `frontend/src/modules/product/ProductListPage.tsx`
- Modify: `frontend/src/modules/product/ProductStructureTab.tsx`
- Modify: `frontend/src/modules/requirement/RequirementWorkshop.tsx`
- Modify: `frontend/src/modules/requirement/FeatureCoverageDrawer.tsx`
- Modify: `frontend/src/modules/resource/ResourcePage.tsx`
- Modify: `frontend/src/modules/standardization/StandardizationPage.tsx`
- Modify: `frontend/src/modules/standardization/ConvertToFeatureDrawer.tsx`
- Modify: `frontend/src/modules/admin/AuditLogsPage.tsx`
- Modify: `frontend/src/modules/admin/UsersTeamsPage.tsx`
- Test: existing colocated `*.test.tsx` files.

**Interfaces:**
- Consumes: `SearchSelect`.
- Produces: 客户、项目、产品、版本、人员、模块、功能、需求和接口维度的一致搜索体验。

- [ ] **Step 1: 扩充现有页面测试**

在项目创建、商机编辑、资源分配和标准化转换测试中，使用可见标签搜索并选择实体，断言最终请求体保存对应数字 ID；产品变化后仅版本字段被清空。

- [ ] **Step 2: 运行相关测试确认红灯**

Run: `pnpm exec vitest run src/modules/dashboard/DashboardPage.test.tsx src/modules/project/ProjectWorkspace.test.tsx src/modules/customer-center/OpportunityPages.test.tsx src/modules/resource/ResourcePage.test.tsx src/modules/standardization/StandardizationPage.test.tsx --pool=threads --maxWorkers=1`

Expected: 至少一个新增搜索断言 FAIL。

- [ ] **Step 3: 最小迁移**

将 API 或数据集生成的动态实体选项改用 `SearchSelect`。保留所有普通枚举 `Select`，保留现有 `onChange` 清理直接下游字段的逻辑。

- [ ] **Step 4: 运行相关测试确认绿灯**

Run: 与 Step 2 相同。

Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add frontend/src
git commit -m "fix: standardize entity selectors"
```

### Task 4: 全量验证

**Files:**
- Verify only.

**Interfaces:**
- Consumes: Tasks 1-3 的最终代码。
- Produces: 可提交的前端改动。

- [ ] **Step 1: 全量前端测试**

Run: `pnpm exec vitest run --pool=threads --maxWorkers=1`

Expected: 31 个测试文件、全部测试通过。

- [ ] **Step 2: 生产构建**

Run: `pnpm build`

Expected: TypeScript 与 Vite 构建成功。

- [ ] **Step 3: 检查改动和工作区**

Run: `git diff --check && git status --short && git log -5 --oneline`

Expected: 无空白错误，只包含本次预期文件。

- [ ] **Step 4: 最终提交**

若验证过程产生必要的测试调整：

```bash
git add frontend/src
git commit -m "test: cover searchable select stability"
```
