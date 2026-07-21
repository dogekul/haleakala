import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { ProjectWorkspace } from './ProjectWorkspace'

const projects = [{
  id: 1,
  code: 'PRJ-001',
  name: '华东银行核心系统交付',
  customerName: '华东银行',
  productName: '智鹿 ERP',
  productVersionName: 'V5.2',
  managerName: '张宁',
  status: 'ACTIVE',
  currentStage: 'REQUIREMENT',
  riskLevel: 'YELLOW',
  startDate: '2026-07-01',
  plannedEndDate: '2026-12-31',
  stages: [], members: [], risks: [], milestones: [], templates: [], artifacts: [], activities: [],
}]

it('默认使用高密度列表并可切换为卡片视图', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => projects }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}>
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <ProjectWorkspace />
    </MemoryRouter>
  </QueryClientProvider>)

  await waitFor(() => expect(screen.getByRole('table')).toBeVisible())
  expect(screen.getByText('华东银行核心系统交付')).toBeVisible()
  await userEvent.click(screen.getByText('卡片视图'))
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
  expect(screen.getByTestId('project-card-1')).toBeVisible()
  vi.unstubAllGlobals()
})

it('创建项目使用独立的可绑定产品版本查询并在切换产品时清空版本', async () => {
  const requests: string[] = []
  let projectBody: Record<string, unknown> | undefined
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    requests.push(path)
    if (path === '/api/v1/projects' && init?.method === 'POST') {
      projectBody = JSON.parse(String(init.body))
      return Promise.resolve(new Response(JSON.stringify({ id: 99, name: '新项目' }), { status: 201, headers: { 'Content-Type': 'application/json' } }))
    }
    if (path === '/api/v1/projects') return Promise.resolve(new Response('[]', { status: 200 }))
    if (path === '/api/v1/customers?status=ACTIVE') return Promise.resolve(new Response(JSON.stringify([
      { id: 81, name: '华东银行', shortName: '华东行', contactName: '王经理', status: 'ACTIVE' },
    ]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
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
  render(<QueryClientProvider client={client}>
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <ProjectWorkspace />
    </MemoryRouter>
  </QueryClientProvider>)
  const user = userEvent.setup()

  await user.click(screen.getByRole('button', { name: /创建项目$/ }))
  const drawer = screen.getByRole('dialog', { name: '创建交付项目' })
  await user.click(within(drawer).getByRole('combobox', { name: '客户' }))
  expect(await screen.findByRole('option', { name: /华东银行.*华东行/ })).toBeInTheDocument()
  await user.click(screen.getByText(/华东银行.*华东行/))
  expect(requests).toContain('/api/v1/customers?status=ACTIVE')
  expect(client.getQueryData(['active-customers'])).toBeDefined()
  await user.click(within(drawer).getByRole('combobox', { name: '产品' }))
  expect(await screen.findByRole('option', { name: '生效产品' })).toBeVisible()
  expect(screen.queryByRole('option', { name: '规划产品' })).not.toBeInTheDocument()
  await user.click(screen.getByText('生效产品'))
  const version = within(drawer).getByRole('combobox', { name: '标品版本' })
  await waitFor(() => expect(version).toBeEnabled())
  await user.click(version)
  expect(await screen.findByRole('option', { name: 'V1 已发布' })).toBeVisible()
  expect(screen.queryByRole('option', { name: 'V2 规划中' })).not.toBeInTheDocument()
  await user.click(screen.getByText('V1 已发布'))
  const versionSelect = version.closest('.ant-select') as HTMLElement
  expect(versionSelect.querySelector('.ant-select-selection-item')).toHaveTextContent('V1 已发布')

  await user.click(within(drawer).getByRole('combobox', { name: '产品' }))
  await user.click(screen.getByText('另一产品'))
  expect(versionSelect.querySelector('.ant-select-selection-item')).not.toBeInTheDocument()
  await waitFor(() => expect(requests).toContain('/api/v1/products/2/versions?bindable=true'))
  expect(client.getQueryData(['bindable-products'])).toBeDefined()
  expect(client.getQueryData(['bindable-product-versions', 1])).toBeDefined()
  expect(client.getQueryData(['products'])).toBeUndefined()
  expect(client.getQueryData(['product-versions', 1])).toBeUndefined()

  await user.click(version)
  await user.click(await screen.findByText('V3 已发布'))
  expect(within(drawer).queryByRole('textbox', { name: '项目编号' })).not.toBeInTheDocument()
  await user.type(within(drawer).getByRole('textbox', { name: '项目名称' }), '新项目')
  await user.click(within(drawer).getByRole('button', { name: '创建项目' }))
  await waitFor(() => expect(projectBody).toEqual(expect.objectContaining({ customerId: 81 })))
  expect(projectBody).not.toHaveProperty('customerName')
  expect(projectBody).not.toHaveProperty('code')
})

it('没有启用客户时提示先维护客户主数据', async () => {
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
    const path = String(input)
    if (path === '/api/v1/projects' || path === '/api/v1/customers?status=ACTIVE' || path === '/api/v1/products?bindable=true') {
      return Promise.resolve(new Response('[]', { status: 200 }))
    }
    throw new Error(`unexpected request: ${path}`)
  }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
    <ProjectWorkspace />
  </MemoryRouter></QueryClientProvider>)
  const user = userEvent.setup()
  await user.click(screen.getByRole('button', { name: /创建项目$/ }))
  const drawer = screen.getByRole('dialog', { name: '创建交付项目' })
  await user.click(within(drawer).getByRole('combobox', { name: '客户' }))
  const customerLink = await screen.findByRole('link', { name: '前往客户管理' })
  expect(customerLink).toBeVisible()
  expect(customerLink.parentElement).toHaveTextContent('请先创建启用客户')
  expect(customerLink).toHaveAttribute('href', '/customers')
})
