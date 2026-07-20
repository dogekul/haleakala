# Customer Center Board Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将客户中心的售前推进和客户运营页面改为紧凑型高密度看板，统一卡片尺寸与操作区，并将用户可见的操作按钮全部改为中文。

**Architecture:** 保留现有页面组件、查询、Mutation 和抽屉逻辑，仅调整两个 React 页面中的展示结构和 `global.css` 中对应的看板样式。通过共享的 `crm-board-*` 展示 class 统一卡片结构，再分别使用 `presale-*`、`operation-*` class 控制五列横向看板和三列响应式看板。

**Tech Stack:** React 18、TypeScript、Ant Design、TanStack Query、Vitest、Testing Library、CSS Grid/Flexbox

## Global Constraints

- `PASS` 显示为“通过”，`REJECT` 显示为“拒绝”。
- 两个页面均不使用看板内部纵向滚动；数据增加时自然拉长页面。
- 售前推进宽度不足时只让看板整体横向滚动；客户运营在窄屏下降为两列、单列。
- 所有卡片固定同高，标题单行省略，辅助信息单行展示，操作区固定贴底。
- 不修改后端接口、数据库结构、业务状态机、权限和事件处理逻辑。
- 不增加新的前端依赖，不重构客户中心其他页面。

---

## File Map

- `frontend/src/modules/customer-center/PresaleBoardPage.tsx`：售前五列看板结构、卡片展示 class、按钮中文文案和标题提示。
- `frontend/src/modules/customer-center/OpportunityPages.test.tsx`：售前卡片结构与中文文案的回归测试。
- `frontend/src/modules/customer-center/OperationBoardPage.tsx`：运营筛选工具栏、三列卡片结构和中文操作文案。
- `frontend/src/modules/customer-center/DeliveryAndOperationPages.test.tsx`：运营卡片结构、中文操作和原有推进请求的回归测试。
- `frontend/src/styles/global.css`：共享卡片尺寸、列头数量徽标、紧凑间距、自然页面增长和响应式规则。

### Task 1: 售前推进紧凑卡片与中文操作

**Files:**
- Modify: `frontend/src/modules/customer-center/OpportunityPages.test.tsx:85`
- Modify: `frontend/src/modules/customer-center/PresaleBoardPage.tsx:74-86`
- Modify: `frontend/src/modules/customer-center/PresaleBoardPage.tsx:207-213`
- Modify: `frontend/src/styles/global.css:532-539`

**Interfaces:**
- Consumes: 现有 `Opportunity`、`opportunityStages`、`stageDocuments`、`artifactTypes` 和 `requestAdvance(item, decision)`。
- Produces: `presale-board-scroll`、`crm-board-count`、`crm-board-card`、`crm-board-card-meta`、`crm-board-card-actions` 展示 class；按钮仍通过原有 aria-label 和 handler 对外工作。

- [ ] **Step 1: 写入失败测试，约束中文文案与统一卡片结构**

在 `OpportunityPages.test.tsx` 的售前页面测试前加入：

```tsx
it('售前看板使用紧凑等高卡片和中文操作文案', async () => {
  vi.stubGlobal('fetch', vi.fn(() => json(opportunities)))
  show(<PresaleBoardPage />)

  expect(await screen.findByTestId('presale-board-scroll')).toBeVisible()

  const biddingTitle = '零售数据平台超长名称用于验证卡片省略展示'
  const biddingCard = screen.getByText(biddingTitle).closest<HTMLElement>('.presale-card')!
  expect(biddingCard).toHaveClass('crm-board-card')
  expect(within(biddingCard).getByRole('link', { name: biddingTitle })).toHaveAttribute('title', biddingTitle)
  expect(biddingCard.querySelector('.crm-board-card-meta')).toHaveTextContent('西部零售')
  expect(biddingCard.querySelector('.crm-board-card-actions')).toBeInTheDocument()
  expect(within(biddingCard).getByRole('button', { name: '产出物' })).toHaveTextContent('查看产出物')
  expect(within(biddingCard).getByRole('button', { name: `推进${biddingTitle}` })).toHaveTextContent('通过')
  expect(within(biddingCard).getByRole('button', { name: `丢单${biddingTitle}` })).toHaveTextContent('拒绝')

  const pocCard = screen.getByText('制造执行平台').closest<HTMLElement>('.presale-card')!
  expect(within(pocCard).getByRole('button', { name: '阶段文档' })).toHaveTextContent('查看文档')
  expect(within(pocCard).getByRole('button', { name: '产出物' })).toHaveTextContent('查看产出物')
  expect(within(pocCard).getByRole('button', { name: '推进制造执行平台' })).toHaveTextContent('推进阶段')
})
```

- [ ] **Step 2: 运行测试并确认按预期失败**

Run:

```bash
cd frontend
npm run test:run -- src/modules/customer-center/OpportunityPages.test.tsx -t '售前看板使用紧凑等高卡片和中文操作文案'
```

Expected: FAIL；找不到 `presale-board-scroll`，且现有按钮仍显示 `产出物`、`PASS`、`REJECT`。

- [ ] **Step 3: 为售前看板加入最小展示结构与中文文案**

将售前看板主体调整为以下结构，保留原有抽屉与事件处理：

```tsx
<PageState loading={query.isLoading} error={query.error} empty={!query.isLoading && !query.data?.length} onRetry={() => void query.refetch()}>
  <div className="presale-board-scroll" data-testid="presale-board-scroll">
    <div className="presale-board">{opportunityStages.map(stage => <section key={stage.value} data-testid={`presale-column-${stage.value}`} className="presale-column">
      <header><strong>{stage.label}</strong><span className="crm-board-count">{(query.data ?? []).filter(item => item.stage === stage.value).length}</span></header>
      {(query.data ?? []).filter(item => item.stage === stage.value).map(item => <Card key={item.id} size="small" className="presale-card crm-board-card">
        <Link title={item.title} to={`/customers/opportunities/${item.id}`}>{item.title}</Link>
        <p className="crm-board-card-meta" title={item.customerName}>{item.customerName}</p>
        <Space className="crm-board-card-actions" wrap size={[4, 4]}>
          {canWrite && stageDocuments[item.stage].length > 0 && <Button size="small" aria-label="阶段文档" icon={<FileTextOutlined />} onClick={() => { setDocumentFor(item); setArtifactError('') }}>查看文档</Button>}
          {canWrite && artifactTypes[item.stage].length > 0 && <Button size="small" aria-label="产出物" icon={<FileAddOutlined />} onClick={() => { setArtifactFor(item); setArtifactError('') }}>查看产出物</Button>}
          {canWrite && <AdvanceButtons item={item} loading={advance.isPending} onAdvance={requestAdvance} />}
        </Space>
      </Card>)}
    </section>)}</div>
  </div>
</PageState>
```

将 `AdvanceButtons` 的显示文本调整为：

```tsx
function AdvanceButtons({ item, loading, onAdvance }: { item: Opportunity; loading: boolean; onAdvance: (item: Opportunity, decision?: 'PASS' | 'REJECT') => void }) {
  if (item.stage === 'CONTRACT') return <><Button size="small" type="primary" aria-label={`转交${item.title}`} onClick={() => onAdvance(item, 'PASS')}>转交实施</Button>
    <Button size="small" danger aria-label={`丢单${item.title}`} onClick={() => onAdvance(item, 'REJECT')}>丢单</Button></>
  if (item.stage === 'OPPORTUNITY' || item.stage === 'BIDDING') return <><Button size="small" type="primary" loading={loading} aria-label={`推进${item.title}`} onClick={() => onAdvance(item, 'PASS')}>通过</Button>
    <Button size="small" danger aria-label={`丢单${item.title}`} onClick={() => onAdvance(item, 'REJECT')}>拒绝</Button></>
  return <Button size="small" type="primary" loading={loading} icon={<RightOutlined />} aria-label={`推进${item.title}`} onClick={() => onAdvance(item)}>推进阶段</Button>
}
```

用以下样式替换现有售前看板规则，并建立共享卡片规范：

```css
.presale-board-scroll { overflow-x: auto; padding-bottom: 4px; }
.presale-board { display: grid; min-width: 1120px; grid-template-columns: repeat(5,minmax(210px,1fr)); gap: 8px; }
.presale-page { overflow-x: visible; }
.presale-column { min-width: 0; padding: 7px; border: 1px solid #e5e6eb; border-radius: 8px; background: #f7f8fa; }
.presale-column > header, .operation-column > header { display: flex; align-items: center; justify-content: space-between; min-height: 28px; padding: 0 2px 7px; color: #4e5969; }
.crm-board-count { display: inline-flex; min-width: 20px; height: 20px; align-items: center; justify-content: center; padding: 0 6px; border-radius: 10px; color: #646a73; background: #e8eaed; font-size: 11px; font-weight: 500; }
.crm-board-card { min-width: 0; height: 126px; margin-bottom: 6px; overflow: hidden; border-color: #e5e6eb; box-shadow: 0 1px 2px rgba(31,35,41,.04); }
.crm-board-card:last-child { margin-bottom: 0; }
.crm-board-card > .ant-card-body { display: flex; min-width: 0; height: 100%; flex-direction: column; padding: 9px 10px; }
.crm-board-card a { display: block; min-width: 0; overflow: hidden; color: #245bdb; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.crm-board-card-meta { overflow: hidden; margin: 5px 0 0; color: #8f959e; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.crm-board-card-actions { min-height: 52px; margin-top: auto; align-content: flex-end; }
.crm-board-card-actions .ant-btn { height: 24px; padding-inline: 7px; font-size: 12px; }
```

- [ ] **Step 4: 运行售前测试并确认通过**

Run:

```bash
cd frontend
npm run test:run -- src/modules/customer-center/OpportunityPages.test.tsx
```

Expected: PASS；售前页面相关测试全部通过，控制台无未处理异常。

- [ ] **Step 5: 提交售前改版**

```bash
git add frontend/src/modules/customer-center/PresaleBoardPage.tsx frontend/src/modules/customer-center/OpportunityPages.test.tsx frontend/src/styles/global.css
git commit -m "style: refine presale board cards"
```

### Task 2: 客户运营工具栏、等高卡片与响应式布局

**Files:**
- Modify: `frontend/src/modules/customer-center/DeliveryAndOperationPages.test.tsx:70-87`
- Modify: `frontend/src/modules/customer-center/OperationBoardPage.tsx:1-66`
- Modify: `frontend/src/styles/global.css:520-524`
- Modify: `frontend/src/styles/global.css:557-573`

**Interfaces:**
- Consumes: Task 1 产生的 `crm-board-count`、`crm-board-card`、`crm-board-card-meta`、`crm-board-card-actions` 样式 class。
- Produces: `operation-toolbar`、`operation-filter-fields`、`operation-summary` class；保留 `crmApi.operations(queryString)` 和 `crmApi.advanceOperation(id, version)` 的调用契约。

- [ ] **Step 1: 扩展运营测试，先约束卡片结构和中文文案**

在现有“客户运营显示三列看板、关闭记录并携带版本推进”测试中，点击推进按钮前加入：

```tsx
const maintenanceCard = screen.getByText('华东银行持续运营').closest<HTMLElement>('.operation-card')!
expect(maintenanceCard).toHaveClass('crm-board-card')
expect(maintenanceCard.querySelector('.crm-board-card-meta')).toHaveTextContent('华东银行 · 周运营')
expect(maintenanceCard.querySelector('.crm-board-card-actions')).toBeInTheDocument()
expect(within(maintenanceCard).getByRole('button', { name: '推进华东银行持续运营' })).toHaveTextContent('推进阶段')

const repurchaseCard = screen.getByText('北方能源复购').closest<HTMLElement>('.operation-card')!
expect(within(repurchaseCard).getByRole('button', { name: '推进北方能源复购' })).toHaveTextContent('关闭运营')
expect(screen.getByTestId('operation-summary')).toHaveTextContent('开放 3 · 已关闭 1')
```

并把测试文件导入改为：

```tsx
import { render, screen, waitFor, within } from '@testing-library/react'
```

- [ ] **Step 2: 运行运营测试并确认按预期失败**

Run:

```bash
cd frontend
npm run test:run -- src/modules/customer-center/DeliveryAndOperationPages.test.tsx -t '客户运营显示三列看板、关闭记录并携带版本推进'
```

Expected: FAIL；现有卡片没有共享结构 class，推进按钮仍显示“推进”或“关闭”。

- [ ] **Step 3: 调整运营工具栏和卡片展示结构**

从 `OperationBoardPage.tsx` 的 Ant Design 导入中移除 `Tag`，将筛选卡片改为：

```tsx
<Card className="crm-filter operation-filter"><div className="crm-toolbar operation-toolbar">
  <Space className="operation-filter-fields" wrap size={8}>
    <Input allowClear prefix={<SearchOutlined />} placeholder="搜索运营或客户" value={keyword} onChange={event => setKeyword(event.target.value)} />
    <Select aria-label="运营负责人筛选" allowClear placeholder="全部负责人" virtual={false} value={owner} onChange={setOwner} options={owners} />
    <Select aria-label="运营客户筛选" allowClear placeholder="全部客户" virtual={false} value={customer} onChange={setCustomer} options={customers} />
    <Select aria-label="运营阶段筛选" allowClear placeholder="全部阶段" virtual={false} value={stage} onChange={setStage} options={[...operationStages, { value: 'CLOSED' as OperationStage, label: '已关闭' }]} />
    <Select aria-label="运营状态筛选" allowClear placeholder="全部状态" virtual={false} value={status} onChange={setStatus} options={[{ value: 'OPEN', label: '进行中' }, { value: 'CLOSED', label: '已关闭' }]} />
  </Space>
  <span className="operation-summary" data-testid="operation-summary">开放 {data.filter(item => item.status === 'OPEN').length} · 已关闭 {closed.length}</span>
</div></Card>
```

将运营列和卡片改为：

```tsx
<div className="operation-board">{operationStages.map(stage => <section key={stage.value} data-testid={`operation-column-${stage.value}`} className="operation-column">
  <header><strong>{stage.label}</strong><span className="crm-board-count">{data.filter(item => item.status === 'OPEN' && item.stage === stage.value).length}</span></header>
  {data.filter(item => item.status === 'OPEN' && item.stage === stage.value).map(item => <Card size="small" key={item.id} className="operation-card crm-board-card">
    <Link title={item.title} to={`/customers/operations/${item.id}`}>{item.title}</Link>
    <p className="crm-board-card-meta" title={`${item.customerName} · ${item.ownerName ?? '未分配负责人'}`}>{item.customerName} · {item.ownerName ?? '未分配负责人'}</p>
    {canWrite && <Space className="crm-board-card-actions" wrap size={[4, 4]}>
      <Button size="small" icon={<EditOutlined />} aria-label={`编辑${item.title}`} onClick={() => setEditing(item)}>编辑</Button>
      <Button size="small" type="primary" icon={<RightOutlined />} aria-label={`推进${item.title}`} onClick={() => advance.mutate(item)}>{item.stage === 'REPURCHASE' ? '关闭运营' : '推进阶段'}</Button>
    </Space>}
  </Card>)}
</section>)}</div>
```

用以下规则替换现有运营看板样式和单断点规则：

```css
.operation-filter .ant-card-body { padding: 9px 11px; }
.operation-toolbar { min-width: 0; }
.operation-filter-fields { min-width: 0; flex: 1; }
.operation-filter-fields .ant-input-affix-wrapper { width: 210px; }
.operation-filter-fields .ant-select { min-width: 112px; }
.operation-summary { flex: 0 0 auto; color: #646a73; font-size: 12px; white-space: nowrap; }
.operation-board { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 8px; }
.operation-column { min-width: 0; padding: 7px; border: 1px solid #e5e6eb; border-radius: 8px; background: #f7f8fa; }
.operation-card { border-color: #e5e6eb; }
.closed-operations { margin-top: 14px; border-color: #e5e6eb; }

@media (max-width: 1100px) {
  .operation-toolbar { align-items: flex-start; }
  .operation-filter-fields { row-gap: 6px; }
  .operation-board { grid-template-columns: repeat(2,minmax(0,1fr)); }
}

@media (max-width: 720px) {
  .operation-toolbar { flex-direction: column; }
  .operation-board { grid-template-columns: 1fr; }
}
```

保留现有通用 `.crm-toolbar`、`.full-link-track` 响应式规则，不让客户运营的改动影响其他客户中心页面。

- [ ] **Step 4: 运行运营测试并确认通过**

Run:

```bash
cd frontend
npm run test:run -- src/modules/customer-center/DeliveryAndOperationPages.test.tsx
```

Expected: PASS；原有推进请求仍携带 `{"version":0}`，只读权限测试仍通过。

- [ ] **Step 5: 提交客户运营改版**

```bash
git add frontend/src/modules/customer-center/OperationBoardPage.tsx frontend/src/modules/customer-center/DeliveryAndOperationPages.test.tsx frontend/src/styles/global.css
git commit -m "style: refine customer operation board"
```

### Task 3: 前端回归、构建与视觉验收

**Files:**
- Verify: `frontend/src/modules/customer-center/PresaleBoardPage.tsx`
- Verify: `frontend/src/modules/customer-center/OperationBoardPage.tsx`
- Verify: `frontend/src/styles/global.css`

**Interfaces:**
- Consumes: Task 1、Task 2 完成的两个页面及共享样式。
- Produces: 可交付的前端构建产物和人工视觉验收记录；不新增生产代码接口。

- [ ] **Step 1: 运行两组相关测试**

Run:

```bash
cd frontend
npm run test:run -- src/modules/customer-center/OpportunityPages.test.tsx src/modules/customer-center/DeliveryAndOperationPages.test.tsx
```

Expected: 两个测试文件全部 PASS，无未处理 Promise、React act 或重复 key 警告。

- [ ] **Step 2: 运行完整前端构建**

Run:

```bash
cd frontend
npm run build
```

Expected: `tsc -b` 和 `vite build` 均以退出码 0 完成。

- [ ] **Step 3: 检查补丁格式和改动范围**

Run:

```bash
git diff --check HEAD~2 -- frontend/src/modules/customer-center/PresaleBoardPage.tsx frontend/src/modules/customer-center/OperationBoardPage.tsx frontend/src/modules/customer-center/OpportunityPages.test.tsx frontend/src/modules/customer-center/DeliveryAndOperationPages.test.tsx frontend/src/styles/global.css
git status --short
```

Expected: `git diff --check` 无输出；`git status` 只保留任务开始前已经存在的后端修改和两个未跟踪 Markdown 文件。

- [ ] **Step 4: 启动页面并人工检查桌面布局**

Run:

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

在浏览器分别打开 `/customers/presale` 和 `/customers/operations`，确认：

- 售前五列紧凑排列，卡片同高，最多三项按钮可换行但操作区高度一致。
- 售前按钮显示“查看文档”“查看产出物”“通过”“拒绝”“推进阶段”。
- 客户运营筛选项保持单行优先，三列卡片同高，按钮显示“推进阶段”或“关闭运营”。
- 增加卡片时页面自然变长，没有看板内部纵向滚动条。
- 将视口缩窄到 1100px 以下时运营看板为两列，缩窄到 720px 以下时为单列；售前看板保持五列并出现整体横向滚动。

- [ ] **Step 5: 若视觉检查无需修正，记录最终提交状态**

Run:

```bash
git log -2 --oneline
```

Expected: 最新两条提交分别为 `style: refine customer operation board` 和 `style: refine presale board cards`。
