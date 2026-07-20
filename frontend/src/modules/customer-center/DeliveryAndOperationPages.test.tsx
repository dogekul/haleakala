import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, vi } from 'vitest'
import { AuthContext, type AuthState } from '../../app/AuthProvider'
import { ImplementationCockpitPage } from './ImplementationCockpitPage'
import { ImplementationPage } from './ImplementationPage'
import { OperationBoardPage } from './OperationBoardPage'
import { OperationDetailPage } from './OperationDetailPage'

const implementation = [{
  opportunityId: 11, opportunityTitle: '财务中台升级', customerId: 21, customerName: '华东银行',
  projectId: 31, projectCode: 'PRJ-031', projectName: '财务中台项目', projectStage: 'REQUIREMENT',
  projectStatus: 'ACTIVE', managerUserId: 41, managerName: '周项目', riskLevel: 'RED', openRiskCount: 2,
  redRiskCount: 1, overdueMilestoneCount: 1, nextMilestoneName: '需求基线确认',
  nextMilestoneDueDate: '2026-07-20', plannedEndDate: '2026-12-31', updatedAt: '2026-07-16T08:00:00', health: 'RED',
}]
const operations = [
  { id: 51, organizationId: 1, customerId: 21, customerName: '华东银行', title: '华东银行持续运营', stage: 'MAINTENANCE', status: 'OPEN', ownerUserId: 41, ownerName: '周运营', projectId: 31, project: { id: 31, name: '财务中台项目' }, opportunityId: 11, opportunity: { id: 11, title: '财务中台升级' }, createdAt: '2026-07-15T08:00:00', updatedAt: '2026-07-16T08:00:00', version: 0 },
  { id: 52, organizationId: 1, customerId: 22, customerName: '华南制造', title: '华南制造经营', stage: 'OPERATING', status: 'OPEN', ownerName: '孙运营', createdAt: '2026-07-15T08:00:00', updatedAt: '2026-07-16T08:00:00', version: 2 },
  { id: 53, organizationId: 1, customerId: 23, customerName: '北方能源', title: '北方能源复购', stage: 'REPURCHASE', status: 'OPEN', ownerName: '孙运营', createdAt: '2026-07-15T08:00:00', updatedAt: '2026-07-16T08:00:00', version: 3 },
  { id: 54, organizationId: 1, customerId: 24, customerName: '西部零售', title: '西部零售已关闭', stage: 'CLOSED', status: 'CLOSED', ownerName: '孙运营', createdAt: '2026-06-15T08:00:00', updatedAt: '2026-07-10T08:00:00', version: 5 },
]
const auth: AuthState = {
  loading: false, me: { id: 1, organizationId: 1, username: 'crm-owner', displayName: '客户负责人', roles: ['PMO'], permissions: ['crm:read', 'crm:write'] },
  login: async () => undefined, logout: async () => undefined, refresh: async () => undefined,
}
const json = (value: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } }))

function show(node: React.ReactNode, permissions = ['crm:read', 'crm:write'], path = '/') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}><AuthContext.Provider value={{ ...auth, me: { ...auth.me!, permissions } }}>
    <MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>{node}</MemoryRouter>
  </AuthContext.Provider></QueryClientProvider>)
}

afterEach(() => vi.unstubAllGlobals())

it('实施协同只读呈现项目真实七阶段、风险和里程碑', async () => {
  vi.stubGlobal('fetch', vi.fn(() => json(implementation)))
  show(<ImplementationPage />)

  expect(await screen.findByText('华东银行')).toBeVisible()
  expect(screen.getByText('财务中台升级')).toBeVisible()
  expect(screen.getByText('财务中台项目')).toBeVisible()
  expect(screen.getByText('需求采集')).toBeVisible()
  expect(screen.getByText('周项目')).toBeVisible()
  expect(screen.getByText('1 个红色风险')).toBeVisible()
  expect(screen.getByText(/需求基线确认/)).toBeVisible()
  expect(screen.getByText(/2026-12-31/)).toBeVisible()
  expect(screen.getByRole('link', { name: '进入项目' })).toHaveAttribute('href', '/projects/31')
  expect(screen.queryByRole('button', { name: /推进阶段/ })).not.toBeInTheDocument()
})

it('实施驾驶舱使用后端指标并按健康度筛选项目', async () => {
  vi.stubGlobal('fetch', vi.fn(() => json({ implementationProjects: 8, redRiskProjects: 2, overdueMilestones: 3, closingProjects: 1, items: implementation })))
  const user = userEvent.setup()
  show(<ImplementationCockpitPage />)

  await waitFor(() => expect(screen.getByTestId('implementation-count')).toHaveTextContent('8'))
  expect(screen.getByTestId('red-risk-count')).toHaveTextContent('2')
  expect(screen.getByTestId('overdue-count')).toHaveTextContent('3')
  expect(screen.getByTestId('closing-count')).toHaveTextContent('1')
  await user.click(screen.getByRole('combobox', { name: '健康度筛选' }))
  await user.click(await screen.findByRole('option', { name: '红色' }))
  expect(screen.getByText('当前筛选 1 个项目')).toBeVisible()
})

it('客户运营显示三列看板、关闭记录并携带版本推进', async () => {
  const fetch = vi.fn((path: RequestInfo | URL, init?: RequestInit) => {
    if (String(path).endsWith('/51/advance') && init?.method === 'POST') return json({ ...operations[0], stage: 'OPERATING', version: 1 })
    return json(operations)
  })
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  show(<OperationBoardPage />)

  expect(await screen.findByText('华东银行持续运营')).toBeVisible()
  expect(screen.getAllByTestId(/operation-column-/)).toHaveLength(3)
  expect(screen.getByText('西部零售已关闭')).toBeVisible()
  expect(screen.getByRole('button', { name: '新建运营' })).toBeVisible()
  const maintenanceCard = screen.getByText('华东银行持续运营').closest<HTMLElement>('.operation-card')!
  expect(maintenanceCard).toHaveClass('crm-board-card')
  expect(maintenanceCard.querySelector('.crm-board-card-meta')).toHaveTextContent('华东银行 · 周运营')
  expect(maintenanceCard.querySelector('.crm-board-card-actions')).toBeInTheDocument()
  expect(within(maintenanceCard).getByRole('button', { name: '推进华东银行持续运营' })).toHaveTextContent('推进阶段')

  const repurchaseCard = screen.getByText('北方能源复购').closest<HTMLElement>('.operation-card')!
  expect(within(repurchaseCard).getByRole('button', { name: '推进北方能源复购' })).toHaveTextContent('关闭运营')
  expect(screen.getByTestId('operation-summary')).toHaveTextContent('开放 3 · 已关闭 1')
  await user.click(screen.getByRole('button', { name: '推进华东银行持续运营' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/operations/51/advance', expect.objectContaining({
    method: 'POST', body: '{"version":0}',
  })))
})

it('客户运营筛选由服务端查询执行', async () => {
  const fetch = vi.fn((_path: RequestInfo | URL) => json(operations))
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  show(<OperationBoardPage />)

  await screen.findByText('华东银行持续运营')
  await user.type(screen.getByPlaceholderText('搜索运营或客户'), '银行')
  await user.click(screen.getByRole('combobox', { name: '运营阶段筛选' }))
  await user.click(await screen.findByRole('option', { name: '持续运营' }))
  await waitFor(() => expect(fetch.mock.calls.some(call => {
    const path = String(call[0])
    return path.includes('keyword=%E9%93%B6%E8%A1%8C') && path.includes('stage=OPERATING')
  })).toBe(true))
})

it('运营详情展示客户商机项目来源和可跳转全链', async () => {
  vi.stubGlobal('fetch', vi.fn(() => json(operations[0])))
  show(<Routes><Route path="/customers/operations/:id" element={<OperationDetailPage />} /></Routes>,
    ['crm:read'], '/customers/operations/51')

  expect(await screen.findByRole('heading', { name: '华东银行持续运营' })).toBeVisible()
  expect(screen.getByText('周运营')).toBeVisible()
  expect(screen.getByRole('link', { name: '财务中台升级' })).toHaveAttribute('href', '/customers/opportunities/11')
  expect(screen.getByRole('link', { name: '财务中台项目' })).toHaveAttribute('href', '/projects/31')
  expect(screen.getByText(/2026-07-16/)).toBeVisible()
  expect(screen.queryByRole('button', { name: /编辑|推进/ })).not.toBeInTheDocument()
})

it('运营只读用户没有新增编辑推进操作', async () => {
  vi.stubGlobal('fetch', vi.fn(() => json(operations)))
  show(<OperationBoardPage />, ['crm:read'])
  await screen.findByText('华东银行持续运营')
  expect(screen.queryByRole('button', { name: /新建运营|编辑|推进/ })).not.toBeInTheDocument()
})
