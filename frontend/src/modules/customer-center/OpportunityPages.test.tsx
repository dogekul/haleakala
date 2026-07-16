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
    stage: 'LEAD', status: 'OPEN', commercialOwnerName: '王商务', stageEnteredAt: new Date(now - 20 * 86400000).toISOString(),
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
  return render(<QueryClientProvider client={client}>
    <AuthContext.Provider value={{ ...auth, me: { ...auth.me!, permissions } }}>
      <MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>{node}</MemoryRouter>
    </AuthContext.Provider>
  </QueryClientProvider>)
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
  expect(screen.queryByRole('button', { name: '新建商机' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /编辑/ })).not.toBeInTheDocument()
})

it('售前看板按五阶段分列且缺少产出物时打开补充抽屉', async () => {
  const fetch = vi.fn((path: RequestInfo | URL, init?: RequestInit) => {
    if (String(path).includes('/advance') && init?.method === 'POST') {
      return json({ code: 'INVALID_ARGUMENT', message: '缺少必需产出物：商机调研报告' }, 400)
    }
    return json(opportunities)
  })
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  show(<PresaleBoardPage />)

  await screen.findByText('财务中台升级')
  expect(screen.getAllByTestId(/presale-column-/)).toHaveLength(5)
  await user.click(screen.getByRole('button', { name: '推进财务中台升级' }))
  const drawer = await screen.findByRole('dialog', { name: '补充产出物' })
  expect(within(drawer).getByText('缺少必需产出物：商机调研报告')).toBeVisible()
  expect(within(drawer).getByLabelText('报告正文')).toBeVisible()
})

it('商机详情展示活动产出物和客户到运营的全链深链', async () => {
  vi.stubGlobal('fetch', vi.fn((path: RequestInfo | URL) => {
    const value = String(path)
    if (value.endsWith('/activities')) return json([{ id: 91, opportunityId: 1, stageCode: 'LEAD', title: '确认关键联系人', status: 'TODO', sortOrder: 0, createdAt: '2026-07-01', version: 0 }])
    if (value.endsWith('/artifacts')) return json([{ id: 92, opportunityId: 1, stageFrom: 'LEAD', artifactType: 'RESEARCH_REPORT', title: '商机调研报告', contentMarkdown: '调研结论', createdAt: '2026-07-01' }])
    if (value.endsWith('/full-link')) return json({ customer: { id: 10, name: '华东银行' }, opportunity: { id: 1, title: '财务中台升级', stage: 'LEAD', status: 'OPEN' }, project: { id: 88, name: '财务中台项目', stage: 'REQUIREMENT', status: 'ACTIVE' }, operation: { id: 89, title: '华东银行运营', stage: 'MAINTENANCE', status: 'OPEN' } })
    return json(opportunities[0])
  }))
  show(<Routes><Route path="/customers/opportunities/:id" element={<OpportunityDetailPage />} /></Routes>,
    ['crm:read', 'crm:write'], '/customers/opportunities/1')

  expect(await screen.findByRole('heading', { name: '财务中台升级' })).toBeVisible()
  expect(await screen.findByText('确认关键联系人')).toBeVisible()
  expect(screen.getByRole('button', { name: '完成确认关键联系人' })).toBeVisible()
  expect(await screen.findByText('调研结论')).toBeVisible()
  expect(await screen.findByRole('link', { name: '进入项目' })).toHaveAttribute('href', '/projects/88')
  expect(screen.getByRole('link', { name: '进入运营' })).toHaveAttribute('href', '/customers/operations/89')
})
