import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useNavigate } from 'react-router-dom'
import { afterEach } from 'vitest'
import { Modal } from 'antd'
import { AuthContext, type AuthState } from '../../app/AuthProvider'
import { RequirementWorkshop } from './RequirementWorkshop'
import type { Requirement } from './types'

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

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(done => { resolve = done })
  return { promise, resolve }
}

function providers(children: React.ReactNode, permissions = auth.me!.permissions,
  client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })) {
  return <QueryClientProvider client={client}>
    <AuthContext.Provider value={{ ...auth, me: { ...auth.me!, permissions } }}>
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>{children}</MemoryRouter>
    </AuthContext.Provider>
  </QueryClientProvider>
}

afterEach(() => { Modal.destroyAll(); vi.unstubAllGlobals() })

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

it('分类决策展示证据、资料提醒、建设内容表和投产计划表', async () => {
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
    const path = String(input)
    if (path === '/api/v1/requirements/funnel') return json({ L0: 0, L1: 0, L2: 0 })
    if (path === '/api/v1/requirements') return json([{
      id: 71, organizationId: 1, projectId: 41, productId: 8, projectCode: 'PRJ-041',
      projectName: '消保合规交付', code: 'REQ-071', title: '客户证件有效期校验',
      description: '增加证件有效期与黑名单联动校验', priority: 'P1', status: 'SUBMITTED',
      version: 2, suggestedLevel: 'L1', confidence: 0.93,
      suggestionReason: '现有功能仅部分覆盖，需要增强开发',
      classificationEvidence: ['需求调研报告/详细需求', '客户校验 Spec/当前能力'],
      classificationWarnings: ['生产窗口待确认'],
      constructionContents: [{
        moduleName: '客户管理', featureCode: 'VALIDATION', featureName: '客户校验',
        versionAvailability: 'INCLUDED', currentCapability: '校验证件格式',
        gap: '缺少有效期和黑名单联动', changeType: 'ENHANCEMENT',
        constructionContent: '增强开发', acceptanceCriteria: '无效证件和黑名单客户被准确拦截并留痕',
        priority: 'P1', evidence: '客户校验 Spec/当前能力',
      }],
      productionPlan: [{
        phase: '开发与验证', workItem: '完成校验增强和回归测试', ownerRole: '研发、测试、实施',
        plannedStart: '2026-08-01', plannedEnd: '2026-10-31', deliverable: '发布包和测试报告',
        entryCriteria: '方案评审通过', exitCriteria: '验收标准全部通过',
        riskAndRollback: '灰度发布并验证回退',
      }],
    }])
    throw new Error(`unexpected request: ${path}`)
  }))
  const user = userEvent.setup()
  render(providers(<RequirementWorkshop />))

  await user.click(await screen.findByRole('button', { name: /分类决策/ }))
  const drawer = screen.getByRole('dialog', { name: 'AI 分类决策树' })
  expect(within(drawer).getByText('需求调研报告/详细需求')).toBeVisible()
  expect(within(drawer).getByText('生产窗口待确认')).toBeVisible()
  expect(within(drawer).getByRole('tab', { name: '建设内容表' })).toBeVisible()
  expect(within(drawer).getAllByText('增强开发').length).toBeGreaterThan(0)
  await user.click(within(drawer).getByRole('tab', { name: '投产计划表' }))
  expect(within(drawer).getByText('灰度发布并验证回退')).toBeVisible()
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

  await act(() => client.invalidateQueries({ queryKey: ['requirements'] }))
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
  const { fetch, requests } = coverageFetch([
    { featureId: 11, coverageType: 'PARTIAL' }, { featureId: 12, coverageType: 'FULL' },
  ])
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  render(providers(<RequirementWorkshop />))

  await user.click(await screen.findByRole('button', { name: /功能覆盖/ }))
  const drawer = screen.getByRole('dialog', { name: '功能覆盖' })
  expect(await within(drawer).findAllByRole('combobox', { name: '产品功能' })).toHaveLength(2)
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

it('覆盖和功能列表尚未加载完成时禁止保存，不会用空列表覆盖已有数据', async () => {
  const base = coverageFetch()
  const coveragePending = deferred<Response>()
  const featuresPending = deferred<Response>()
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    if (path === '/api/v1/requirements/1/product-features' && !init?.method) return coveragePending.promise
    if (path === '/api/v1/products/8/features' && !init?.method) return featuresPending.promise
    return base.fetch(input, init)
  })
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  render(providers(<RequirementWorkshop />))

  await user.click(await screen.findByRole('button', { name: /功能覆盖/ }))
  const drawer = screen.getByRole('dialog', { name: '功能覆盖' })
  const save = within(drawer).getByRole('button', { name: '保存覆盖' })
  expect(save).toBeDisabled()
  expect(within(drawer).getByRole('button', { name: /添加功能/ })).toBeDisabled()
  await user.click(save)
  expect(fetch.mock.calls.some(([, init]) => (init as RequestInit | undefined)?.method === 'PUT')).toBe(false)

  await act(async () => {
    coveragePending.resolve(new Response(JSON.stringify({
      requirementId: 1, fullyCovered: false,
      entries: [{ featureId: 11, featureCode: 'F-11', featureName: '自动对账', moduleName: '对账中心', coverageType: 'PARTIAL' }],
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    featuresPending.resolve(new Response(JSON.stringify([
      { id: 11, productId: 8, moduleId: 81, code: 'F-11', name: '自动对账', status: 'ACTIVE', version: 0 },
    ]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
  })
  await waitFor(() => expect(save).toBeEnabled())
})

it('功能列表加载失败时保持禁用并可重试，不提交空覆盖', async () => {
  const base = coverageFetch([{ featureId: 11, coverageType: 'PARTIAL' }])
  let featureAttempts = 0
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    if (String(input) === '/api/v1/products/8/features' && !init?.method) {
      featureAttempts += 1
      return featureAttempts === 1
        ? json({ code: 'REQUEST_FAILED', message: '功能列表加载失败' }, 500)
        : json([{ id: 11, productId: 8, moduleId: 81, code: 'F-11', name: '自动对账', status: 'ACTIVE', version: 0 }])
    }
    return base.fetch(input, init)
  })
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  render(providers(<RequirementWorkshop />))

  await user.click(await screen.findByRole('button', { name: /功能覆盖/ }))
  const drawer = screen.getByRole('dialog', { name: '功能覆盖' })
  expect(await within(drawer).findByText('功能列表加载失败')).toBeVisible()
  const save = within(drawer).getByRole('button', { name: '保存覆盖' })
  expect(save).toBeDisabled()
  expect(within(drawer).getByRole('button', { name: '加入标准化候选' })).toBeDisabled()
  await user.click(save)
  expect(fetch.mock.calls.some(([, init]) => (init as RequestInit | undefined)?.method === 'PUT')).toBe(false)

  await user.click(within(drawer).getByRole('button', { name: '重新加载' }))
  await waitFor(() => expect(save).toBeEnabled())
  expect(featureAttempts).toBe(2)
})

it('完成需求采集时提交表单并提示调研文档已保存到 Outline', async () => {
  const requests: Array<{ path: string; init?: RequestInit }> = []
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    requests.push({ path, init })
    if (path === '/api/v1/requirements/funnel') return json({ L0: 0, L1: 0, L2: 0 })
    if (path === '/api/v1/projects') return json([{
      id: 41, code: 'PRJ-041', name: '消保合规交付', customerName: '示例银行', status: 'ACTIVE',
    }])
    if (path === '/api/v1/requirements' && init?.method === 'POST') return json({
      id: 51, organizationId: 1, projectId: 41, productId: 8, projectCode: 'PRJ-041',
      projectName: '消保合规交付', code: 'REQ-051', title: '交易限额校验',
      description: '付款前校验客户交易限额并保留结果', source: '客户访谈', priority: 'P2',
      status: 'DRAFT', version: 1, outlineLinkId: 61, sourceTemplateId: 71,
      sourceTemplateRevision: 5,
    }, 201)
    if (path === '/api/v1/requirements') return json([])
    throw new Error(`unexpected request: ${path}`)
  }))
  const user = userEvent.setup()
  render(providers(<RequirementWorkshop />))

  await user.click(await screen.findByRole('button', { name: /采集需求/ }))
  const drawer = screen.getByRole('dialog', { name: '需求采集单' })
  await user.click(within(drawer).getByRole('combobox', { name: '所属项目' }))
  await user.click(await screen.findByText('PRJ-041 · 消保合规交付'))
  await user.type(within(drawer).getByRole('textbox', { name: '需求标题' }), '交易限额校验')
  await user.type(within(drawer).getByRole('textbox', { name: '业务描述与验收条件' }),
    '付款前校验客户交易限额并保留结果')
  await user.click(within(drawer).getByRole('button', { name: '完成采集并生成文档' }))

  await waitFor(() => expect(JSON.parse(String(requests.find(request =>
    request.path === '/api/v1/requirements' && request.init?.method === 'POST')?.init?.body)))
    .toEqual({
      priority: 'P2', source: '客户访谈', projectId: 41, title: '交易限额校验',
      description: '付款前校验客户交易限额并保留结果',
    }))
  expect(await screen.findByText('需求已创建，调研文档已保存到 Outline')).toBeVisible()
})

it('只为已关联 Outline 的需求显示查看文档并打开最新地址', async () => {
  const open = vi.fn()
  vi.stubGlobal('open', open)
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
    const path = String(input)
    if (path === '/api/v1/requirements/funnel') return json({ L0: 0, L1: 0, L2: 0 })
    if (path === '/api/v1/requirements/51/document') return json({
      linkId: 61, title: '交易限额校验需求调研报告', revision: 2,
      outlineUrl: 'http://localhost:3000/doc/req-51',
    })
    if (path === '/api/v1/requirements') return json([
      { id: 51, projectId: 41, projectCode: 'PRJ-041', projectName: '消保合规交付',
        code: 'REQ-051', title: '交易限额校验', description: '限额校验', priority: 'P1',
        status: 'DRAFT', version: 1, outlineLinkId: 61 },
      { id: 52, projectId: 41, projectCode: 'PRJ-041', projectName: '消保合规交付',
        code: 'REQ-052', title: '历史需求', description: '没有关联文档', priority: 'P2',
        status: 'DRAFT', version: 0 },
    ])
    throw new Error(`unexpected request: ${path}`)
  }))
  const user = userEvent.setup()
  render(providers(<RequirementWorkshop />))

  const documentButtons = await screen.findAllByRole('button', { name: /查看文档/ })
  expect(documentButtons).toHaveLength(1)
  await user.click(documentButtons[0])

  await waitFor(() => expect(open).toHaveBeenCalledWith(
    'http://localhost:3000/doc/req-51', '_blank', 'noopener,noreferrer'))
})

function editableRequirement(): Requirement {
  return {
    id: 61, organizationId: 1, projectId: 41, productId: 8, projectCode: 'PRJ-041',
    projectName: '消保合规交付', code: 'REQ-061', title: '交易限额校验',
    description: '付款前校验客户交易限额', source: '客户访谈', priority: 'P1',
    status: 'DRAFT', version: 3, outlineLinkId: 71,
  }
}

it('编辑需求时回填字段并可仅保存结构化信息', async () => {
  const requests: Array<{ path: string; init?: RequestInit }> = []
  let requirement = editableRequirement()
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    requests.push({ path, init })
    if (path === '/api/v1/requirements/funnel') return json({ L0: 0, L1: 0, L2: 0 })
    if (path === '/api/v1/projects') return json([{ id: 41, code: 'PRJ-041', name: '消保合规交付' }])
    if (path === '/api/v1/requirements/61' && init?.method === 'PUT') {
      const body = JSON.parse(String(init.body))
      requirement = { ...requirement, ...body, version: 4 }
      return json(requirement)
    }
    if (path === '/api/v1/requirements') return json([requirement])
    throw new Error(`unexpected request: ${path}`)
  }))
  const user = userEvent.setup()
  render(providers(<RequirementWorkshop />))

  await user.click(await screen.findByRole('button', { name: /编辑/ }))
  const drawer = screen.getByRole('dialog', { name: '编辑需求' })
  expect(within(drawer).getByRole('combobox', { name: '所属项目' })).toBeDisabled()
  const title = within(drawer).getByRole('textbox', { name: '需求标题' })
  expect(title).toHaveValue('交易限额校验')
  await user.clear(title)
  await user.type(title, '交易限额规则校验')
  await user.click(within(drawer).getByRole('button', { name: '仅保存需求' }))

  await waitFor(() => expect(JSON.parse(String(requests.find(request =>
    request.path === '/api/v1/requirements/61' && request.init?.method === 'PUT')?.init?.body)))
    .toEqual(expect.objectContaining({
      projectId: 41, title: '交易限额规则校验', version: 3, regenerateReport: false,
    })))
})

it('编辑需求时明确确认后可用 AI 覆盖重生成报告', async () => {
  const requests: Array<{ path: string; init?: RequestInit }> = []
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    requests.push({ path, init })
    if (path === '/api/v1/requirements/funnel') return json({ L0: 0, L1: 0, L2: 0 })
    if (path === '/api/v1/projects') return json([{ id: 41, code: 'PRJ-041', name: '消保合规交付' }])
    if (path === '/api/v1/requirements/61' && init?.method === 'PUT') return json({
      ...editableRequirement(), version: 5,
    })
    if (path === '/api/v1/requirements') return json([editableRequirement()])
    throw new Error(`unexpected request: ${path}`)
  }))
  const user = userEvent.setup()
  render(providers(<RequirementWorkshop />))

  await user.click(await screen.findByRole('button', { name: /编辑/ }))
  await user.click(within(screen.getByRole('dialog', { name: '编辑需求' }))
    .getByRole('button', { name: '保存并重新生成报告' }))
  await screen.findAllByText('重新生成需求调研报告？')
  const confirmation = screen.getAllByRole('dialog').find(dialog =>
    within(dialog).queryAllByText('重新生成需求调研报告？').length > 0)!
  await user.click(within(confirmation).getByRole('button', { name: '确认覆盖并生成' }))

  await waitFor(() => expect(JSON.parse(String(requests.find(request =>
    request.path === '/api/v1/requirements/61' && request.init?.method === 'PUT')?.init?.body)))
    .toEqual(expect.objectContaining({ version: 3, regenerateReport: true })))
})

it('可确认废弃需求并以中文终态展示且保留文档入口', async () => {
  const requests: Array<{ path: string; init?: RequestInit }> = []
  let requirement = editableRequirement()
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    requests.push({ path, init })
    if (path === '/api/v1/requirements/funnel') return json({ L0: 0, L1: 0, L2: 0 })
    if (path === '/api/v1/requirements/61/abandon' && init?.method === 'POST') {
      requirement = { ...requirement, status: 'ABANDONED' as const, version: 4 }
      return json(requirement)
    }
    if (path === '/api/v1/requirements') return json([requirement])
    throw new Error(`unexpected request: ${path}`)
  }))
  const user = userEvent.setup()
  render(providers(<RequirementWorkshop />))

  await user.click(await screen.findByRole('button', { name: /废弃/ }))
  await screen.findAllByText('确认废弃该需求？')
  const confirmation = screen.getAllByRole('dialog').find(dialog =>
    within(dialog).queryAllByText('确认废弃该需求？').length > 0)!
  await user.click(within(confirmation).getByRole('button', { name: '确认废弃' }))

  await waitFor(() => expect(JSON.parse(String(requests.find(request =>
    request.path === '/api/v1/requirements/61/abandon' && request.init?.method === 'POST')?.init?.body)))
    .toEqual({ version: 3 }))
  expect(await screen.findByText('已废弃')).toBeVisible()
  expect(screen.getByRole('button', { name: /查看文档/ })).toBeVisible()
  expect(screen.queryByRole('button', { name: /编辑/ })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /分类决策/ })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /功能覆盖/ })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /查重合并/ })).not.toBeInTheDocument()
  await user.click(screen.getByText('看板'))
  const board = screen.getByTestId('requirement-board')
  expect(within(board).getByText('已废弃')).toBeVisible()
  expect(within(board).getByText('交易限额校验')).toBeVisible()
})
