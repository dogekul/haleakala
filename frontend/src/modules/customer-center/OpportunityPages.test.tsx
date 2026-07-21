import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, vi } from 'vitest'
import { AuthContext, type AuthState } from '../../app/AuthProvider'
import { OpportunityDetailPage } from './OpportunityDetailPage'
import { OpportunityOverviewPage } from './OpportunityOverviewPage'
import { PresaleBoardPage } from './PresaleBoardPage'

const now = Date.now()
const opportunities = [
  { id: 1, organizationId: 1, customerId: 10, customerName: '华东银行', title: '财务中台升级', amount: 100,
    stage: 'LEAD', status: 'OPEN', commercialOwnerUserId: 101, commercialOwnerName: '王商务',
    solutionOwnerUserId: 102, solutionOwnerName: '李方案', projectManagerUserId: 103, projectManagerName: '周项目',
    operationOwnerUserId: 104, operationOwnerName: '孙运营', stageEnteredAt: new Date(now - 20 * 86400000).toISOString(),
    createdAt: '2026-06-01T08:00:00', updatedAt: '2026-07-01T08:00:00', version: 0 },
  { id: 2, organizationId: 1, customerId: 11, customerName: '华南制造', title: '制造执行平台', amount: 200,
    stage: 'POC', status: 'OPEN', productName: '智鹿 CRM', solutionOwnerName: '李方案', stageEnteredAt: new Date(now - 2 * 86400000).toISOString(),
    createdAt: '2026-06-02T08:00:00', updatedAt: '2026-07-02T08:00:00', version: 1 },
  { id: 3, organizationId: 1, customerId: 12, customerName: '北方能源', title: '能源运营平台', amount: 300,
    stage: 'CONTRACT', status: 'WON', stageEnteredAt: '2026-07-01T08:00:00', createdAt: '2026-06-03T08:00:00', updatedAt: '2026-07-03T08:00:00', version: 4 },
  { id: 4, organizationId: 1, customerId: 13, customerName: '西部零售', title: '零售数据平台超长名称用于验证卡片省略展示', amount: 400,
    stage: 'BIDDING', status: 'LOST', stageEnteredAt: '2026-07-01T08:00:00', createdAt: '2026-06-04T08:00:00', updatedAt: '2026-07-04T08:00:00', version: 3 },
]

const auth: AuthState = {
  loading: false,
  me: { id: 1, organizationId: 1, username: 'crm-owner', displayName: '客户负责人', roles: ['PMO'], permissions: ['crm:read', 'crm:write'] },
  login: async () => undefined, logout: async () => undefined, refresh: async () => undefined,
}

const json = (value: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(value), {
  status, headers: { 'Content-Type': 'application/json' },
}))

function show(node: React.ReactNode, permissions = ['crm:read', 'crm:write'], path = '/') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return Object.assign(render(<QueryClientProvider client={client}>
    <AuthContext.Provider value={{ ...auth, me: { ...auth.me!, permissions } }}>
      <MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>{node}</MemoryRouter>
    </AuthContext.Provider>
  </QueryClientProvider>), { client })
}

afterEach(() => vi.unstubAllGlobals())

it('商机总览展示指标漏斗阶段超时并切换卡片视图', async () => {
  vi.stubGlobal('fetch', vi.fn(() => json(opportunities)))
  const user = userEvent.setup()
  show(<OpportunityOverviewPage />)

  expect(await screen.findByText('财务中台升级')).toBeVisible()
  expect(screen.getByTestId('opportunity-total')).toHaveTextContent('4')
  expect(screen.getByTestId('open-amount')).toHaveTextContent('300')
  expect(screen.getByTestId('won-count')).toHaveTextContent('1')
  expect(screen.getByTestId('lost-count')).toHaveTextContent('1')
  expect(screen.getByTestId('win-rate')).toHaveTextContent('50%')
  expect(screen.getByText('阶段停留 20 天')).toBeVisible()
  expect(screen.getAllByTestId(/funnel-/)).toHaveLength(5)
  await user.click(screen.getByText('卡片'))
  expect(screen.getByTestId('opportunity-card-4')).toHaveClass('crm-ellipsis-card')
  expect(screen.getByRole('button', { name: '新建商机' })).toBeVisible()
})

it('商机筛选发送服务端查询且只读用户没有编辑入口', async () => {
  const fetch = vi.fn((_path: RequestInfo | URL) => json(opportunities))
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  show(<OpportunityOverviewPage />, ['crm:read'])
  await screen.findByText('财务中台升级')
  await user.type(screen.getByPlaceholderText('搜索商机或客户'), '财务')
  await waitFor(() => expect(fetch.mock.calls.some(call => String(call[0]).includes('keyword=%E8%B4%A2%E5%8A%A1'))).toBe(true))
  expect(screen.getByRole('combobox', { name: '商务负责人筛选' })).toBeInTheDocument()
  expect(screen.getByRole('combobox', { name: '方案负责人筛选' })).toBeInTheDocument()
  expect(screen.getByRole('combobox', { name: '项目经理筛选' })).toBeInTheDocument()
  expect(screen.getByRole('combobox', { name: '运营负责人筛选' })).toBeInTheDocument()
  await user.click(screen.getByRole('combobox', { name: '项目经理筛选' }))
  await user.click(await screen.findByRole('option', { name: '周项目' }))
  await waitFor(() => expect(fetch.mock.calls.some(call => String(call[0]).includes('projectManagerUserId=103'))).toBe(true))
  expect(screen.queryByRole('button', { name: '新建商机' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /编辑/ })).not.toBeInTheDocument()
})

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

it('推进线索时从知识库模版填写并提交需求调研报告', async () => {
  const requests: Array<{ path: string; body?: string }> = []
  const fetch = vi.fn((path: RequestInfo | URL, init?: RequestInit) => {
    const value = String(path)
    if (value.endsWith('/research-report/prepare')) {
      return json({
        linkId: 91,
        title: '银行需求调研报告',
        markdown: '# {{调研结论}}',
        renderedHtml: '<h1>需求调研报告</h1><p>请填写调研结论</p>',
        revision: 4,
        syncStatus: 'READY',
        sourceTemplateId: 20,
        sourceTemplateRevision: 3,
      })
    }
    if (value.endsWith('/research-report/submit')) {
      requests.push({ path: value, body: String(init?.body) })
      const body = JSON.parse(String(init?.body))
      return json({
        linkId: 91,
        title: body.title,
        markdown: body.markdown,
        renderedHtml: '<h1>已完成调研</h1>',
        revision: 5,
        syncStatus: 'READY',
        opportunity: { ...opportunities[0], stage: 'OPPORTUNITY', version: 1 },
      })
    }
    return json(opportunities)
  })
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  show(<PresaleBoardPage />)

  await screen.findByText('财务中台升级')
  expect(screen.getAllByTestId(/presale-column-/)).toHaveLength(5)
  const leadCard = screen.getByText('财务中台升级').closest<HTMLElement>('.presale-card')!
  expect(within(leadCard).queryByRole('button', { name: '产出物' })).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: '推进财务中台升级' }))
  const drawer = await screen.findByRole('dialog', { name: '填写需求调研报告' })
  expect(await within(drawer).findByText('请填写调研结论')).toBeVisible()
  expect(within(drawer).queryByLabelText('报告正文')).not.toBeInTheDocument()
  await user.click(within(drawer).getByRole('button', { name: '编辑' }))
  const title = within(drawer).getByRole('textbox', { name: '文档标题' })
  const editor = within(drawer).getByRole('textbox', { name: 'Markdown 正文' })
  await user.clear(title)
  await user.type(title, '银行需求调研报告')
  await user.clear(editor)
  await user.type(editor, '# 已完成调研')
  await user.click(within(drawer).getByRole('button', { name: '提交报告并推进' }))
  await waitFor(() => expect(requests).toContainEqual({
    path: '/api/v1/opportunities/1/research-report/submit',
    body: JSON.stringify({
      title: '银行需求调研报告',
      markdown: '# 已完成调研',
      revision: 4,
      opportunityVersion: 0,
    }),
  }))
})

it('商机评审 PASS 改为从知识库模版填写决策纪要后提交推进', async () => {
  const decision = { ...opportunities[0], id: 6, stage: 'OPPORTUNITY', title: '评审中的财务平台', version: 2 }
  const requests: string[] = []
  vi.stubGlobal('fetch', vi.fn((path: RequestInfo | URL, init?: RequestInit) => {
    const value = String(path)
    if (value.endsWith('/documents/DECISION_MINUTES/prepare')) return json({
      linkId: 96, title: '决策评审纪要', markdown: '# {{评审结论}}',
      renderedHtml: '<h1>决策评审纪要</h1><p>请填写评审结论</p>', revision: 2,
      syncStatus: 'READY', sourceTemplateId: 30, sourceTemplateRevision: 4,
      generationStatus: 'MANUAL', warnings: [],
    })
    if (value.endsWith('/documents/DECISION_MINUTES/submit')) {
      requests.push(String(init?.body))
      const body = JSON.parse(String(init?.body))
      return json({ ...body, linkId: 96, renderedHtml: '<h1>通过</h1>', revision: 3,
        syncStatus: 'READY', opportunity: { ...decision, stage: 'POC', version: 3 } })
    }
    return json([decision])
  }))
  const user = userEvent.setup()
  show(<PresaleBoardPage />)

  await screen.findByText('评审中的财务平台')
  await user.click(screen.getByRole('button', { name: '推进评审中的财务平台' }))
  const drawer = await screen.findByRole('dialog', { name: '商机推进文档' })
  expect(await within(drawer).findByText('请填写评审结论')).toBeVisible()
  await user.click(within(drawer).getByRole('button', { name: '编辑' }))
  const editor = within(drawer).getByRole('textbox', { name: 'Markdown 正文' })
  await user.clear(editor)
  await user.type(editor, '# 评审通过')
  await user.click(within(drawer).getByRole('button', { name: '提交纪要并通过' }))
  await waitFor(() => expect(requests).toContain(JSON.stringify({
    title: '决策评审纪要', markdown: '# 评审通过', revision: 2, opportunityVersion: 2,
  })))
})

it('POC 阶段分别展示 AI 生成的甲方诉求与差距分析且不再手填', async () => {
  const poc = { ...opportunities[1], title: 'POC 中的制造平台' }
  vi.stubGlobal('fetch', vi.fn((path: RequestInfo | URL) => {
    const value = String(path)
    if (value.endsWith('/documents/CLIENT_REQUESTS/prepare')) return json({
      linkId: 97, title: '甲方诉求清单', markdown: '# 统一账户体系',
      renderedHtml: '<h1>甲方诉求清单</h1><p>统一账户体系</p>', revision: 2,
      syncStatus: 'READY', sourceTemplateId: 31, sourceTemplateRevision: 4,
      generationStatus: 'AI', warnings: ['功能“移动审批”的设计 Spec 尚未初始化'],
    })
    if (value.endsWith('/documents/GAP_ANALYSIS/prepare')) return json({
      linkId: 98, title: '差距分析报告', markdown: '# 差距分析',
      renderedHtml: '<h1>差距分析报告</h1><p>存在移动审批差距</p>', revision: 2,
      syncStatus: 'READY', sourceTemplateId: 32, sourceTemplateRevision: 5,
      generationStatus: 'AI', warnings: [],
    })
    return json([poc])
  }))
  const user = userEvent.setup()
  show(<PresaleBoardPage />)

  await screen.findByText('POC 中的制造平台')
  await user.click(screen.getByRole('button', { name: '阶段文档' }))
  const drawer = await screen.findByRole('dialog', { name: '商机推进文档' })
  expect(within(drawer).getByRole('tab', { name: '甲方诉求清单' })).toBeVisible()
  expect(within(drawer).getByRole('tab', { name: '差距分析报告' })).toBeVisible()
  expect(await within(drawer).findByText('统一账户体系')).toBeVisible()
  expect(within(drawer).getByText(/移动审批.*设计 Spec 尚未初始化/)).toBeVisible()
  expect(within(drawer).getByRole('button', { name: 'AI 重新生成' })).toBeVisible()
  await user.click(within(drawer).getByRole('tab', { name: '差距分析报告' }))
  expect(await within(drawer).findByText('存在移动审批差距')).toBeVisible()

  await user.click(within(drawer).getByRole('button', { name: '补充其他产出物' }))
  const artifactDrawer = await screen.findByRole('dialog', { name: '补充产出物' })
  await user.click(within(artifactDrawer).getByRole('combobox', { name: '产出物类型' }))
  expect(screen.queryByRole('option', { name: '甲方诉求清单' })).not.toBeInTheDocument()
  expect(screen.queryByRole('option', { name: '差距分析报告' })).not.toBeInTheDocument()
})

it('文件产出物直接上传且交接使用产品版本和负责人选择器', async () => {
  const contract = { ...opportunities[2], customerName: '华东银行', status: 'OPEN', productId: 20, productVersionId: 21,
    projectManagerUserId: 30, title: '待交接合同' }
  const fetch = vi.fn((path: RequestInfo | URL, init?: RequestInit) => {
    const value = String(path)
    if (value.endsWith('/handoff') && init?.method === 'POST') {
      return json({ ...contract, status: 'WON', projectId: 99, projectName: '财务中台实施' })
    }
    if (value.includes('/api/v1/files') && init?.method === 'POST') {
      return json({ id: 77, originalName: '合同材料.pdf', fileVersion: 1, sizeBytes: 1024 })
    }
    if (value.includes('/artifacts') && init?.method === 'POST') {
      return json({ id: 88, opportunityId: contract.id, stageFrom: 'CONTRACT', artifactType: 'CONTRACT', title: '合同', fileId: 77 })
    }
    if (value.includes('/api/v1/products/20/versions')) return json([{ id: 21, versionName: 'V5.0', status: 'RELEASED' }])
    if (value.includes('/api/v1/products')) return json([{ id: 20, name: '企业财务云', status: 'ACTIVE' }])
    if (value.includes('/api/v1/crm/owner-options')) return json([{ id: 30, displayName: '周项目' }])
    if (value.includes('/api/v1/opportunities')) return json([contract])
    return json([])
  })
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  const view = show(<PresaleBoardPage />)

  await screen.findByText('待交接合同')
  await user.click(screen.getByRole('button', { name: '产出物' }))
  const artifactDrawer = await screen.findByRole('dialog', { name: '补充产出物' })
  await user.click(within(artifactDrawer).getByRole('combobox', { name: '产出物类型' }))
  await user.click(await screen.findByText('合同', { selector: '.ant-select-item-option-content' }))
  expect(within(artifactDrawer).queryByLabelText('文件 ID')).not.toBeInTheDocument()
  const fileInput = artifactDrawer.querySelector<HTMLInputElement>('input[type="file"]')
  expect(fileInput).not.toBeNull()
  await user.upload(fileInput!, new File(['contract'], '合同材料.pdf', { type: 'application/pdf' }))
  expect(await within(artifactDrawer).findByText(/合同材料\.pdf.*已上传/)).toBeVisible()

  await user.click(within(artifactDrawer).getByRole('button', { name: 'Close' }))
  await waitFor(() => expect(screen.queryByRole('dialog', { name: '补充产出物' })).not.toBeInTheDocument())
  view.unmount()
  const handoffView = show(<PresaleBoardPage />)
  await screen.findByText('待交接合同')
  await user.click(screen.getByRole('button', { name: '转交待交接合同' }))
  const handoff = await screen.findByRole('dialog', { name: '转交实施' })
  expect(within(handoff).getByLabelText('客户')).toBeDisabled()
  expect(within(handoff).getByLabelText('客户')).toHaveValue('华东银行')
  expect([...handoff.querySelectorAll('.ant-form-item-label > label')]
    .map(label => label.textContent)
    .filter(label => ['客户', '产品', '产品版本', '项目名称'].includes(label ?? '')))
    .toEqual(['客户', '产品', '产品版本', '项目名称'])
  expect(within(handoff).getByRole('combobox', { name: /^产品$/ })).toBeVisible()
  expect(within(handoff).getByRole('combobox', { name: /^产品版本$/ })).toBeEnabled()
  expect(within(handoff).getByLabelText('项目经理')).toBeVisible()
  expect(within(handoff).queryByLabelText('产品 ID')).not.toBeInTheDocument()
  expect(within(handoff).queryByLabelText('项目编码')).not.toBeInTheDocument()
  const projectName = within(handoff).getByLabelText('项目名称')
  await waitFor(() => expect(projectName).toHaveValue('华东银行 - 企业财务云 V5.0 实施项目'))
  await user.clear(projectName)
  await user.type(projectName, '财务中台实施')
  handoffView.client.setQueryData(['products', 'bindable'], [
    { id: 20, name: '企业财务云', status: 'ACTIVE' },
    { id: 22, name: '无关产品', status: 'ACTIVE' },
  ])
  await waitFor(() => expect(projectName).toHaveValue('财务中台实施'))
  await user.type(within(handoff).getByLabelText('开始日期'), '2026-07-21')
  await user.type(within(handoff).getByLabelText('计划结束'), '2026-10-31')
  await user.click(within(handoff).getByRole('button', { name: '确认转交' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith(
    expect.stringMatching(/\/handoff$/), expect.objectContaining({ method: 'POST' }),
  ))
  const handoffBody = JSON.parse(String(fetch.mock.calls.find(([url, init]) =>
    String(url).endsWith('/handoff') && init?.method === 'POST')?.[1]?.body))
  expect(handoffBody.project.name).toBe('财务中台实施')
  expect(handoffBody.project).not.toHaveProperty('code')
})

it('CRM只读用户不能补产出物或转交', async () => {
  vi.stubGlobal('fetch', vi.fn(() => json(opportunities.filter(item => item.status === 'OPEN'))))
  show(<PresaleBoardPage />, ['crm:read'])
  await screen.findByText('财务中台升级')
  expect(screen.queryByRole('button', { name: '产出物' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /推进|转交|丢单/ })).not.toBeInTheDocument()
})

it('商机详情展示活动产出物和客户到运营的全链深链', async () => {
  vi.stubGlobal('fetch', vi.fn((path: RequestInfo | URL) => {
    const value = String(path)
    if (value.endsWith('/research-report')) return json({
      linkId: 93, title: '需求调研报告', markdown: '# Outline 报告正文',
      renderedHtml: '<h1>Outline 报告正文</h1>', revision: 5, syncStatus: 'READY',
    })
    if (value.endsWith('/activities')) return json([{ id: 91, opportunityId: 1, stageCode: 'LEAD', title: '确认关键联系人', status: 'TODO', sortOrder: 0, createdAt: '2026-07-01', version: 0 }])
    if (value.endsWith('/artifacts')) return json([
      { id: 92, opportunityId: 1, stageFrom: 'LEAD', artifactType: 'RESEARCH_REPORT', title: '历史调研报告', contentMarkdown: '调研结论', createdAt: '2026-07-01' },
      { id: 93, opportunityId: 1, stageFrom: 'LEAD', artifactType: 'RESEARCH_REPORT', title: '需求调研报告', outlineLinkId: 93, sourceTemplateRevision: 3, createdAt: '2026-07-02' },
    ])
    if (value.endsWith('/full-link')) return json({ customer: { id: 10, name: '华东银行' }, opportunity: { id: 1, title: '财务中台升级', stage: 'LEAD', status: 'OPEN' }, project: { id: 88, name: '财务中台项目', stage: 'REQUIREMENT', status: 'ACTIVE' }, operation: { id: 89, title: '华东银行运营', stage: 'MAINTENANCE', status: 'OPEN' } })
    return json(opportunities[0])
  }))
  show(<Routes><Route path="/customers/opportunities/:id" element={<OpportunityDetailPage />} /></Routes>,
    ['crm:read', 'crm:write'], '/customers/opportunities/1')

  expect(await screen.findByRole('heading', { name: '财务中台升级' })).toBeVisible()
  expect(screen.getByRole('link', { name: /返回商机总览/ })).toHaveClass('detail-back-link')
  expect(await screen.findByText('确认关键联系人')).toBeVisible()
  expect(screen.getByRole('button', { name: '完成确认关键联系人' })).toBeVisible()
  expect(await screen.findByText('调研结论')).toBeVisible()
  await userEvent.click(screen.getByRole('button', { name: '预览报告' }))
  expect(await screen.findByText('Outline 报告正文')).toBeVisible()
  expect(await screen.findByRole('link', { name: '进入项目' })).toHaveAttribute('href', '/projects/88')
  expect(screen.getByRole('link', { name: '进入运营' })).toHaveAttribute('href', '/customers/operations/89')
})
