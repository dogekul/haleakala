import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { DashboardPage } from './DashboardPage'

it('默认展示高密度项目列表并保留卡片视图', async () => {
  window.localStorage.clear()
  vi.stubGlobal('fetch', vi.fn((input: string) => {
    const body = input.includes('/summary') ? { activeProjects: 3, totalProjects: 3, redProjects: 1, yellowProjects: 1, healthScore: 68, openRisks: 4, overdueMilestones: 1, stageDistribution: {}, productDistribution: {} }
      : input.includes('/projects') ? [{ id: 1, code: 'PRJ-001', name: '华东银行核心系统交付', customerName: '华东银行', productName: '智鹿 ERP', productVersionName: 'V5', managerName: '张宁', status: 'ACTIVE', currentStage: 'REQUIREMENT', riskLevel: 'RED', progress: 17, openRiskCount: 2, overdueMilestoneCount: 1 }]
        : []
    return Promise.resolve({ ok: true, json: async () => body })
  }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}><DashboardPage /></MemoryRouter></QueryClientProvider>)

  await waitFor(() => expect(screen.getAllByRole('table')[0]).toBeVisible())
  expect(screen.getByText('项目健康度')).toBeVisible()
  expect(screen.getByText('华东银行核心系统交付')).toBeVisible()
  await userEvent.click(screen.getByText('卡片'))
  expect(screen.getByTestId('dashboard-project-card-1')).toBeVisible()
  expect(window.localStorage.getItem('dashboard-project-view')).toBe('card')
  vi.unstubAllGlobals()
})

it('快速创建使用可绑定产品版本且切换产品会清空已选版本', async () => {
  window.localStorage.clear()
  const requests: string[] = []
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
    const path = String(input)
    requests.push(path)
    if (path === '/api/v1/dashboard/summary') return Promise.resolve(new Response(JSON.stringify({
      activeProjects: 0, healthScore: 100, redProjects: 0, openRisks: 0, overdueMilestones: 0,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    if (path === '/api/v1/dashboard/projects' || path === '/api/v1/dashboard/risk-heatmap' || path === '/api/v1/dashboard/matrix') {
      return Promise.resolve(new Response('[]', { status: 200 }))
    }
    if (path === '/api/v1/products?bindable=true') return Promise.resolve(new Response(JSON.stringify([
      { id: 1, code: 'ACTIVE', name: '生效产品', status: 'ACTIVE' },
      { id: 9, code: 'DRAFT', name: '规划产品', status: 'PLANNING' },
      { id: 2, code: 'NEXT', name: '另一产品', status: 'ACTIVE' },
    ]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    if (path === '/api/v1/products/1/versions?bindable=true') return Promise.resolve(new Response(JSON.stringify([
      { id: 11, productId: 1, versionName: 'V1 已发布', status: 'RELEASED' },
      { id: 19, productId: 1, versionName: 'V2 规划中', status: 'PLANNING' },
    ]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    if (path === '/api/v1/products/2/versions?bindable=true') return Promise.resolve(new Response(JSON.stringify([
      { id: 21, productId: 2, versionName: 'V3 已发布', status: 'RELEASED' },
    ]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    throw new Error(`unexpected request: ${path}`)
  }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}><DashboardPage /></MemoryRouter></QueryClientProvider>)
  const user = userEvent.setup()

  await user.click(await screen.findByRole('button', { name: /快速创建项目$/ }))
  const drawer = screen.getByRole('dialog', { name: '快速创建交付项目' })
  await user.click(within(drawer).getByRole('combobox', { name: '产品' }))
  expect(await screen.findByRole('option', { name: 'ACTIVE · 生效产品' })).toBeVisible()
  expect(screen.queryByRole('option', { name: 'DRAFT · 规划产品' })).not.toBeInTheDocument()
  await user.click(screen.getByText('ACTIVE · 生效产品'))
  const version = within(drawer).getByRole('combobox', { name: '版本' })
  await waitFor(() => expect(version).toBeEnabled())
  await user.click(version)
  expect(await screen.findByRole('option', { name: 'V1 已发布' })).toBeVisible()
  expect(screen.queryByRole('option', { name: 'V2 规划中' })).not.toBeInTheDocument()
  await user.click(screen.getByText('V1 已发布'))
  const versionSelect = version.closest('.ant-select') as HTMLElement
  expect(versionSelect.querySelector('.ant-select-selection-item')).toHaveTextContent('V1 已发布')

  await user.click(within(drawer).getByRole('combobox', { name: '产品' }))
  await user.click(screen.getByText('NEXT · 另一产品'))
  expect(versionSelect.querySelector('.ant-select-selection-item')).not.toBeInTheDocument()
  await waitFor(() => expect(requests).toContain('/api/v1/products/2/versions?bindable=true'))
  expect(client.getQueryData(['bindable-products'])).toBeDefined()
  expect(client.getQueryData(['bindable-product-versions', 1])).toBeDefined()
  expect(client.getQueryData(['products'])).toBeUndefined()
  expect(client.getQueryData(['product-versions', 1])).toBeUndefined()
})
