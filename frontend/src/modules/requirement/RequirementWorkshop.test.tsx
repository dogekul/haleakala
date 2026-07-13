import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useNavigate } from 'react-router-dom'
import { afterEach } from 'vitest'
import { AuthContext, type AuthState } from '../../app/AuthProvider'
import { RequirementWorkshop } from './RequirementWorkshop'

const auth: AuthState = {
  loading: false,
  me: {
    id: 1, organizationId: 1, username: 'engineer', displayName: '交付工程师', roles: ['DELIVERY_ENGINEER'],
    permissions: ['requirement:read', 'requirement:write', 'product:read'],
  },
  login: async () => undefined, logout: async () => undefined, refresh: async () => undefined,
}

const json = (value: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(value), {
  status, headers: { 'Content-Type': 'application/json' },
}))

function providers(children: React.ReactNode, permissions = auth.me!.permissions,
  client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })) {
  return <QueryClientProvider client={client}>
    <AuthContext.Provider value={{ ...auth, me: { ...auth.me!, permissions } }}>
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>{children}</MemoryRouter>
    </AuthContext.Provider>
  </QueryClientProvider>
}

afterEach(() => vi.unstubAllGlobals())

it('展示仅由人工确认决策驱动的三层漏斗并保留看板', async () => {
  vi.stubGlobal('fetch', vi.fn((input: string) => {
    const body = input.includes('/funnel') ? { L0: 8, L1: 5, L2: 2 }
      : input.includes('/api/v1/requirements') ? [{ id: 1, projectId: 1, projectCode: 'PRJ-001', projectName: '银行交付', code: 'REQ-001', title: '对账差异自动定位', description: '自动定位批次对账差异', priority: 'P1', status: 'CONFIRMED', confirmedLevel: 'L1', suggestedLevel: 'L0', confidence: 0.82 }]
        : []
    return Promise.resolve({ ok: true, json: async () => body })
  }))
  render(providers(<RequirementWorkshop />))

  await waitFor(() => expect(screen.getByText('对账差异自动定位')).toBeVisible())
  expect(screen.getByText('标品满足 L0')).toBeVisible()
  expect(screen.getByText('8 条')).toBeVisible()
  await userEvent.click(screen.getByText('看板'))
  expect(screen.getByTestId('requirement-board')).toBeVisible()
})

it('requirementId 每次只自动定位一次且参数变化时定位新需求', async () => {
  let requirementGets = 0
  vi.stubGlobal('fetch', vi.fn((input: string) => {
    if (input.includes('/funnel')) return Promise.resolve({ ok: true, json: async () => ({ L0: 0, L1: 0, L2: 0 }) })
    if (input.includes('/api/v1/requirements')) {
      requirementGets += 1
      const suffix = requirementGets > 1 ? '（刷新）' : ''
      return Promise.resolve({ ok: true, json: async () => [
        { id: 1, projectId: 1, projectCode: 'PRJ-001', code: 'REQ-001', title: `第一条需求${suffix}`, description: '描述一', priority: 'P1', status: 'CONFIRMED' },
        { id: 2, projectId: 1, projectCode: 'PRJ-001', code: 'REQ-002', title: '第二条需求', description: '描述二', priority: 'P2', status: 'SUBMITTED' },
      ] })
    }
    return Promise.resolve({ ok: true, json: async () => [] })
  }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  function NavigateRequirement() {
    const navigate = useNavigate()
    return <button onClick={() => navigate('/requirements?requirementId=2')}>定位第二条需求</button>
  }
  render(<QueryClientProvider client={client}><AuthContext.Provider value={auth}>
    <MemoryRouter initialEntries={['/requirements?requirementId=1']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <NavigateRequirement />
      <RequirementWorkshop />
    </MemoryRouter>
  </AuthContext.Provider></QueryClientProvider>)

  expect(await screen.findByRole('dialog', { name: 'AI 分类决策树' })).toBeVisible()
  await userEvent.click(screen.getByRole('button', { name: 'Close' }))
  await waitFor(() => expect(screen.queryByRole('dialog', { name: 'AI 分类决策树' })).not.toBeInTheDocument())

  await client.invalidateQueries({ queryKey: ['requirements'] })
  expect(await screen.findByText('第一条需求（刷新）')).toBeVisible()
  expect(screen.queryByRole('dialog', { name: 'AI 分类决策树' })).not.toBeInTheDocument()

  await userEvent.click(screen.getByRole('button', { name: '定位第二条需求' }))
  expect(await screen.findByRole('dialog', { name: 'AI 分类决策树' })).toBeVisible()
  expect(screen.getByRole('heading', { name: '第二条需求' })).toBeVisible()
})

function coverageFetch(entries: Array<{ featureId: number; coverageType: 'FULL' | 'PARTIAL' }> = []) {
  const requests: Array<{ path: string; init?: RequestInit }> = []
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    requests.push({ path, init })
    if (path === '/api/v1/requirements/funnel') return json({ L0: 0, L1: 1, L2: 0 })
    if (path === '/api/v1/requirements/1/product-features' && init?.method === 'PUT') {
      const body = JSON.parse(String(init.body)) as { entries: typeof entries }
      entries = body.entries
      return json({ requirementId: 1, fullyCovered: entries.some(item => item.coverageType === 'FULL'), entries })
    }
    if (path === '/api/v1/requirements/1/product-features') return json({
      requirementId: 1, fullyCovered: entries.some(item => item.coverageType === 'FULL'),
      entries: entries.map(item => ({ ...item, featureCode: `F-${item.featureId}`, featureName: item.featureId === 11 ? '自动对账' : '差异定位', moduleName: '对账中心' })),
    })
    if (path === '/api/v1/products/8/features') return json([
      { id: 11, productId: 8, moduleId: 81, code: 'F-11', name: '自动对账', status: 'ACTIVE', version: 0 },
      { id: 12, productId: 8, moduleId: 81, code: 'F-12', name: '差异定位', status: 'ACTIVE', version: 0 },
    ])
    if (path === '/api/v1/standardization/debts/from-requirement' && init?.method === 'POST') {
      return json({ id: 91, patternKey: 'REQUIREMENT:1', title: '对账要求', status: 'CANDIDATE', version: 0 }, 201)
    }
    if (path === '/api/v1/requirements') return json([{
      id: 1, organizationId: 1, projectId: 2, productId: 8, projectCode: 'PRJ-1', projectName: '银行交付',
      code: 'REQ-1', title: '对账要求', description: '对账要求说明', priority: 'P1', status: 'CONFIRMED', version: 0,
    }])
    throw new Error(`unexpected request: ${path}`)
  })
  return { fetch, requests }
}

it('在功能覆盖抽屉中保存多功能的 FULL 和 PARTIAL 关系', async () => {
  const { fetch, requests } = coverageFetch()
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  render(providers(<RequirementWorkshop />))

  await user.click(await screen.findByRole('button', { name: /功能覆盖/ }))
  const drawer = screen.getByRole('dialog', { name: '功能覆盖' })
  await user.click(within(drawer).getByRole('button', { name: /添加功能/ }))
  await user.click(within(drawer).getAllByRole('combobox', { name: '产品功能' })[0])
  await user.click(await screen.findByRole('option', { name: 'F-11 · 自动对账' }))
  await user.click(within(drawer).getAllByRole('combobox', { name: '覆盖方式' })[0])
  await user.click(await screen.findByRole('option', { name: '部分覆盖' }))

  await user.click(within(drawer).getByRole('button', { name: /添加功能/ }))
  await user.click(within(drawer).getAllByRole('combobox', { name: '产品功能' })[1])
  await user.click(await screen.findByRole('option', { name: 'F-12 · 差异定位' }))
  await user.click(within(drawer).getAllByRole('combobox', { name: '覆盖方式' })[1])
  await user.click(await screen.findByRole('option', { name: '完全覆盖' }))
  await user.click(within(drawer).getByRole('button', { name: '保存覆盖' }))

  await waitFor(() => expect(requests.find(request => request.path === '/api/v1/requirements/1/product-features'
    && request.init?.method === 'PUT')?.init?.body).toBe(JSON.stringify({ entries: [
    { featureId: 11, coverageType: 'PARTIAL' }, { featureId: 12, coverageType: 'FULL' },
  ] })))
})

it('仅部分覆盖时可加入标准化候选，成功后禁止重复提交', async () => {
  const { fetch, requests } = coverageFetch([{ featureId: 11, coverageType: 'PARTIAL' }])
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  render(providers(<RequirementWorkshop />))

  await user.click(await screen.findByRole('button', { name: /功能覆盖/ }))
  const candidate = await screen.findByRole('button', { name: '加入标准化候选' })
  await user.click(candidate)

  await waitFor(() => expect(requests.find(request => request.path === '/api/v1/standardization/debts/from-requirement'
    && request.init?.method === 'POST')?.init?.body).toBe(JSON.stringify({ requirementId: 1 })))
  expect(candidate).toBeDisabled()
})

it('候选创建发生其他冲突时不会被误判为已存在候选', async () => {
  const base = coverageFetch([{ featureId: 11, coverageType: 'PARTIAL' }])
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => String(input) === '/api/v1/standardization/debts/from-requirement'
    ? json({ code: 'CONFLICT', message: '需求已被产品功能完全覆盖' }, 409)
    : base.fetch(input, init))
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  render(providers(<RequirementWorkshop />))

  await user.click(await screen.findByRole('button', { name: /功能覆盖/ }))
  const candidate = await screen.findByRole('button', { name: '加入标准化候选' })
  await user.click(candidate)

  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/standardization/debts/from-requirement', expect.objectContaining({ method: 'POST' })))
  await waitFor(() => expect(candidate).toBeEnabled())
})

it('不允许在功能覆盖列表中选择重复功能', async () => {
  const { fetch, requests } = coverageFetch()
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  render(providers(<RequirementWorkshop />))

  await user.click(await screen.findByRole('button', { name: /功能覆盖/ }))
  const drawer = screen.getByRole('dialog', { name: '功能覆盖' })
  for (let index = 0; index < 2; index += 1) {
    await user.click(within(drawer).getByRole('button', { name: /添加功能/ }))
    await user.click(within(drawer).getAllByRole('combobox', { name: '产品功能' })[index])
    await user.click(await screen.findByRole('option', { name: 'F-11 · 自动对账' }))
  }
  await user.click(within(drawer).getByRole('button', { name: '保存覆盖' }))

  expect(await within(drawer).findByText('不能重复选择产品功能')).toBeVisible()
  expect(requests.some(request => request.init?.method === 'PUT')).toBe(false)
})

it('只读用户可查看覆盖但不能修改或加入候选', async () => {
  const { fetch } = coverageFetch([{ featureId: 11, coverageType: 'PARTIAL' }])
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  render(providers(<RequirementWorkshop />, ['requirement:read', 'product:read']))

  await user.click(await screen.findByRole('button', { name: /功能覆盖/ }))
  const drawer = screen.getByRole('dialog', { name: '功能覆盖' })
  expect(within(drawer).queryByRole('button', { name: '保存覆盖' })).not.toBeInTheDocument()
  expect(within(drawer).queryByRole('button', { name: '添加功能' })).not.toBeInTheDocument()
  expect(within(drawer).queryByRole('button', { name: '加入标准化候选' })).not.toBeInTheDocument()
})
