import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach } from 'vitest'
import { AuthContext, type AuthState } from '../../app/AuthProvider'
import { StandardizationPage } from './StandardizationPage'

const auth: AuthState = {
  loading: false,
  me: {
    id: 1, organizationId: 1, username: 'product-owner', displayName: '产品负责人', roles: ['PRODUCT_OWNER'],
    permissions: ['standardization:read', 'standardization:write', 'product:read', 'product:write'],
  },
  login: async () => undefined, logout: async () => undefined, refresh: async () => undefined,
}

const json = (value: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(value), {
  status, headers: { 'Content-Type': 'application/json' },
}))

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(done => { resolve = done })
  return { promise, resolve }
}

function show(permissions = auth.me!.permissions,
  client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })) {
  render(<QueryClientProvider client={client}><AuthContext.Provider value={{ ...auth, me: { ...auth.me!, permissions } }}>
    <MemoryRouter><StandardizationPage /></MemoryRouter>
  </AuthContext.Provider></QueryClientProvider>)
  return client
}

afterEach(() => vi.unstubAllGlobals())

it('同时提供六个标准化视图和可核对的数字口径', async () => {
  vi.stubGlobal('fetch', vi.fn((input: string) => {
    let body: unknown = []
    if (input.endsWith('/api/v1/products')) body = [{ id: 1, code: 'ERP', name: '企业财务', status: 'ACTIVE' }]
    else if (input.includes('/versions')) body = [{ id: 11, productId: 1, versionName: 'V5.0', status: 'ACTIVE' }]
    else if (input.includes('/baselines')) body = [{ id: 1, productVersionId: 11, capabilityCode: 'AR-001', capabilityName: '应收对账', dimension: 'FUNCTION', scopeDescription: '标准应收对账与差异识别', status: 'ACTIVE', version: 0 }]
    else if (input.includes('/assessments')) body = { period: '2026-07', standardCoverage: 72, reuseRate: 64, documentationScore: 80, extensionReadiness: 56, deliveryStability: 90, maturityScore: 71 }
    else if (input.includes('/deviations')) body = [{ projectId: 1, projectCode: 'P-001', projectName: '银行项目', total: 20, l0: 12, l1: 6, l2: 2, deviationRate: 40 }]
    else if (input.includes('/debts')) body = [{ id: 1, patternKey: 'reconciliation.retry', title: '对账重跑', occurrenceCount: 8, distinctProjects: 5, status: 'CANDIDATE' }]
    else if (input.includes('/costs')) body = { estimatedPersonDays: 80, actualPersonDays: 92, estimatedCost: 160000, actualCost: 184000, byExtensionPoint: [] }
    else if (input.includes('/flywheel')) body = { period: '2026-07', confirmedRequirements: 50, l0Count: 36, l1Count: 12, reuseRate: 64, debtClosedCount: 3, customCost: 184000, standardCoverage: 72 }
    return Promise.resolve({ ok: true, json: async () => body })
  }))
  show()

  await waitFor(() => expect(screen.getByText('应收对账')).toBeVisible())
  for (const tab of ['能力基线', '成熟度', '偏离度', '标准化债务', '成本归集', '产品飞轮']) expect(screen.getByText(tab)).toBeVisible()
  await userEvent.click(screen.getByText('成熟度'))
  expect(await screen.findByText('71')).toBeVisible()
  expect(screen.getByRole('table')).toBeVisible()
})

function conversionFetch() {
  const requests: Array<{ path: string; init?: RequestInit }> = []
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    requests.push({ path, init })
    if (path === '/api/v1/products') return json([
      { id: 1, code: 'ERP', name: '企业财务', status: 'ACTIVE', moduleCount: 1, featureCount: 0, updatedAt: '', version: 0 },
      { id: 2, code: 'CRM', name: '客户经营', status: 'ACTIVE', moduleCount: 1, featureCount: 0, updatedAt: '', version: 0 },
    ])
    if (path === '/api/v1/products/1/versions') return json([
      { id: 11, productId: 1, versionName: 'V5.0', status: 'PLANNING', version: 0 },
      { id: 12, productId: 1, versionName: 'V4.9', status: 'RELEASED', version: 1 },
    ])
    if (path === '/api/v1/products/2/versions') return json([{ id: 21, productId: 2, versionName: 'V2.0', status: 'PLANNING', version: 0 }])
    if (path === '/api/v1/products/1/modules') return json([{ id: 101, productId: 1, code: 'AR', name: '应收管理', status: 'ACTIVE', sortOrder: 1, version: 0 }])
    if (path === '/api/v1/products/2/modules') return json([{ id: 201, productId: 2, code: 'CRM', name: '客户管理', status: 'ACTIVE', sortOrder: 1, version: 0 }])
    if (path === '/api/v1/standardization/baselines?productVersionId=11') return json([])
    if (path === '/api/v1/standardization/assessments?productVersionId=11') return json({ period: '2026-07', standardCoverage: 0, reuseRate: 0, documentationScore: 0, extensionReadiness: 0, deliveryStability: 0, maturityScore: 0 })
    if (path === '/api/v1/standardization/deviations?productVersionId=11') return json([])
    if (path === '/api/v1/standardization/debts?productVersionId=11') return json([{
      id: 91, patternKey: 'REQUIREMENT:1', title: '对账差异自动定位', occurrenceCount: 1, distinctProjects: 1,
      status: 'CANDIDATE', version: 3,
    }])
    if (path === '/api/v1/standardization/costs?productVersionId=11') return json({ estimatedPersonDays: 0, actualPersonDays: 0, estimatedCost: 0, actualCost: 0, byExtensionPoint: [] })
    if (path === '/api/v1/standardization/flywheel?productVersionId=11') return json({ period: '2026-07', confirmedRequirements: 0, l0Count: 0, l1Count: 0, reuseRate: 0, debtClosedCount: 0, customCost: 0, standardCoverage: 0 })
    if (path === '/api/v1/standardization/debts/91/convert-to-feature' && init?.method === 'POST') return json({
      id: 91, patternKey: 'REQUIREMENT:1', title: '对账差异自动定位', occurrenceCount: 1, distinctProjects: 1,
      status: 'INCLUDED', convertedFeatureId: 301, targetVersion: 'V5.0', version: 4,
    })
    if (path === '/api/v1/products/1/features') return json([])
    if (path === '/api/v1/products/1/coverage') return json({ productId: 1, features: [], uncoveredRequirements: [] })
    if (path === '/api/v1/products/1/versions/11/features') return json({ versionId: 11, version: 1, entries: [] })
    throw new Error(`unexpected request: ${path}`)
  })
  return { fetch, requests }
}

it('将标准化候选转为产品功能并可选加入规划版本', async () => {
  const { fetch, requests } = conversionFetch()
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  client.setQueryData(['requirement-coverage', 1], { requirementId: 1, fullyCovered: false, entries: [] })
  show(auth.me!.permissions, client)

  await user.click(await screen.findByRole('tab', { name: /标准化债务/ }))
  await user.click(await screen.findByRole('button', { name: '转为产品功能' }))
  const drawer = screen.getByRole('dialog', { name: '转为产品功能' })
  expect(within(drawer).getByLabelText('功能名称')).toHaveValue('对账差异自动定位')
  await user.type(within(drawer).getByLabelText('功能编码'), 'AR-DIFF')
  await user.click(within(drawer).getByRole('combobox', { name: '目标模块' }))
  await user.click(await screen.findByRole('option', { name: 'AR · 应收管理' }))
  await user.click(within(drawer).getByRole('combobox', { name: '加入规划版本' }))
  await user.click(await screen.findByRole('option', { name: 'V5.0' }))
  await user.type(within(drawer).getByLabelText('负责人 ID（可选）'), '7')
  await user.click(within(drawer).getByRole('button', { name: '创建功能' }))

  await waitFor(() => {
    const request = requests.find(item => item.path === '/api/v1/standardization/debts/91/convert-to-feature'
      && item.init?.method === 'POST')
    expect(JSON.parse(String(request?.init?.body))).toEqual({
      productId: 1, moduleId: 101, productVersionId: 11, code: 'AR-DIFF', name: '对账差异自动定位',
      ownerUserId: 7, version: 3,
    })
  })
  await waitFor(() => expect(client.getQueryState(['requirement-coverage', 1])?.isInvalidated).toBe(true))
})

it('转换抽屉切换产品时清空已选模块和版本且只显示新产品选项', async () => {
  const { fetch } = conversionFetch()
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  show()

  await user.click(await screen.findByRole('tab', { name: /标准化债务/ }))
  await user.click(await screen.findByRole('button', { name: '转为产品功能' }))
  const drawer = screen.getByRole('dialog', { name: '转为产品功能' })
  await user.click(within(drawer).getByRole('combobox', { name: '目标模块' }))
  await user.click(await screen.findByRole('option', { name: 'AR · 应收管理' }))
  await user.click(within(drawer).getByRole('combobox', { name: '加入规划版本' }))
  await user.click(await screen.findByRole('option', { name: 'V5.0' }))
  await user.click(within(drawer).getByRole('combobox', { name: '目标产品' }))
  await user.click(await screen.findByRole('option', { name: '客户经营' }))

  expect(within(drawer).getByRole('combobox', { name: '目标模块' })).toHaveValue('')
  expect(within(drawer).getByRole('combobox', { name: '加入规划版本' })).toHaveValue('')
  await user.click(within(drawer).getByRole('combobox', { name: '目标模块' }))
  await waitFor(() => expect(screen.getByRole('option', { name: 'CRM · 客户管理' })).toBeVisible())
  expect(screen.queryByRole('option', { name: 'AR · 应收管理' })).not.toBeInTheDocument()
})

it('快速切换产品时延迟返回的旧模块请求不会覆盖新产品选项', async () => {
  const base = conversionFetch()
  const oldModules = deferred<Response>()
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => String(input) === '/api/v1/products/1/modules'
    ? oldModules.promise : base.fetch(input, init))
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  show()

  await user.click(await screen.findByRole('tab', { name: /标准化债务/ }))
  await user.click(await screen.findByRole('button', { name: '转为产品功能' }))
  const drawer = screen.getByRole('dialog', { name: '转为产品功能' })
  await user.click(within(drawer).getByRole('combobox', { name: '目标产品' }))
  await user.click(await screen.findByRole('option', { name: '客户经营' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/products/2/modules', expect.anything()))

  oldModules.resolve(new Response(JSON.stringify([
    { id: 101, productId: 1, code: 'AR', name: '应收管理', status: 'ACTIVE', sortOrder: 1, version: 0 },
  ]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
  await user.click(within(drawer).getByRole('combobox', { name: '目标模块' }))
  await waitFor(() => expect(screen.getByRole('option', { name: 'CRM · 客户管理' })).toBeVisible())
  expect(screen.queryByRole('option', { name: 'AR · 应收管理' })).not.toBeInTheDocument()
})

it('只读标准化用户不发起写请求也不显示转换和流转操作', async () => {
  const { fetch, requests } = conversionFetch()
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  show(['standardization:read', 'product:read'])

  await user.click(await screen.findByRole('tab', { name: /标准化债务/ }))
  await screen.findByText('对账差异自动定位')
  expect(screen.queryByRole('button', { name: '转为产品功能' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '评估高频二开' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '待评审' })).not.toBeInTheDocument()
  expect(requests.some(request => request.init?.method === 'POST')).toBe(false)
})
