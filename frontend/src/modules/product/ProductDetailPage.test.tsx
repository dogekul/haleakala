import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import { AuthContext, type AuthState } from '../../app/AuthProvider'
import { RequirementWorkshop } from '../requirement/RequirementWorkshop'
import { ProductDetailPage } from './ProductDetailPage'
import { buildModuleTree, validParentModules } from './ProductStructureTab'
import type { ProductModule } from './types'

const product = {
  id: 8, organizationId: 1, ownerUserId: 20, code: 'ERP', name: '智鹿 ERP', category: '企业应用',
  description: '覆盖核心经营流程', status: 'ACTIVE', moduleCount: 3, featureCount: 2,
  latestVersionName: 'V5.2', updatedAt: '2026-07-12T08:00:00Z', version: 2,
}
const modules = [
  { id: 11, productId: 8, code: 'FIN', name: '财务管理', status: 'ACTIVE', sortOrder: 1, version: 2 },
  { id: 12, productId: 8, parentId: 11, code: 'AR', name: '应收管理', status: 'ACTIVE', sortOrder: 1, version: 1 },
  { id: 13, productId: 8, parentId: 12, code: 'AR-SETTLE', name: '对账中心', status: 'PLANNING', sortOrder: 1, version: 0 },
]
const features = [
  { id: 21, productId: 8, moduleId: 11, code: 'FIN-GL', name: '总账处理', status: 'ACTIVE', version: 1 },
  { id: 22, productId: 8, moduleId: 12, code: 'AR-RECON', name: '应收对账', status: 'PLANNING', version: 0 },
]
const versions = [
  { id: 31, productId: 8, versionName: 'V5.2', releaseDate: '2026-08-01', status: 'PLANNING', version: 3 },
  { id: 32, productId: 8, versionName: 'V5.1', releaseDate: '2026-06-01', status: 'RELEASED', version: 5 },
]
const coverage = {
  productId: 8,
  features: [{ featureId: 21, featureCode: 'FIN-GL', featureName: '总账处理', moduleName: '财务管理', fullCount: 3, partialCount: 1 }],
  uncoveredRequirements: [{ requirementId: 41, requirementCode: 'REQ-41', title: '跨区域核算差异支持和特别长的需求标题', projectCode: 'PRJ-1', debtLinked: false }],
}
const productDocuments = [
  { id: 101, productId: 8, nodeType: 'FOLDER', code: 'DOC-01', title: '01 产品总纲', sortOrder: 1, syncStatus: 'READY', version: 0 },
  { id: 102, productId: 8, parentId: 101, nodeType: 'DOCUMENT', code: 'DOC-01-01', title: '产品一页纸', sortOrder: 1, syncStatus: 'READY', version: 0 },
]
const featureSpec = {
  linkId: 88, title: '产品一页纸', markdown: '# 产品一页纸',
  renderedHtml: '<h1>产品一页纸</h1>', revision: 3, updatedAt: '2026-07-17T08:00:00Z',
  syncStatus: 'READY', outlineUrl: 'http://outline/doc/product-one-pager',
}

const auth: AuthState = {
  loading: false,
  me: { id: 1, organizationId: 1, username: 'owner', displayName: '产品负责人', roles: ['PRODUCT_OWNER'], permissions: ['product:read', 'product:write'] },
  login: async () => undefined, logout: async () => undefined, refresh: async () => undefined,
}

const json = (value: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(value), {
  status, headers: { 'Content-Type': 'application/json' },
}))

function responseFor(path: string) {
  if (path === '/api/v1/products/8') return json(product)
  if (path === '/api/v1/products/8/modules') return json(modules)
  if (path === '/api/v1/products/8/document-nodes/102/content') return json(featureSpec)
  if (path.startsWith('/api/v1/products/8/features')) return json(features)
  if (path === '/api/v1/products/8/versions') return json(versions)
  if (path === '/api/v1/products/8/versions/31/features') return json({ versionId: 31, version: 3, entries: [{ featureId: 21, availability: 'INCLUDED' }] })
  if (path === '/api/v1/products/8/versions/32/features') return json({ versionId: 32, version: 5, entries: [{ featureId: 21, availability: 'INCLUDED' }] })
  if (path === '/api/v1/products/8/coverage') return json(coverage)
  if (path === '/api/v1/products/8/document-nodes') return json(productDocuments)
  if (path === '/api/v1/requirements/funnel') return json({ L0: 1, L1: 0, L2: 0 })
  if (path === '/api/v1/requirements') return json([{
    id: 41, organizationId: 1, projectId: 51, projectCode: 'PRJ-1', projectName: '区域财务项目', code: 'REQ-41',
    title: '跨区域核算差异支持和特别长的需求标题', description: '需要支持跨区域核算差异', priority: 'P1',
    status: 'CONFIRMED', confirmedLevel: 'L0', version: 1,
  }])
  throw new Error(`unexpected request: ${path}`)
}

function show(fetch = vi.fn((input: RequestInfo | URL) => responseFor(String(input))), permissions = ['product:read', 'product:write'],
  client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })) {
  vi.stubGlobal('fetch', fetch)
  render(<QueryClientProvider client={client}>
    <AuthContext.Provider value={{ ...auth, me: { ...auth.me!, permissions } }}>
      <MemoryRouter initialEntries={['/products/8']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <Routes><Route path="/products/:productId" element={<ProductDetailPage />} /><Route path="/requirements" element={<RequirementWorkshop />} /></Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  </QueryClientProvider>)
  return fetch
}

afterEach(() => vi.unstubAllGlobals())

it('构建模块树不修改输入且父级候选排除后代与超三级节点', () => {
  const hierarchy: ProductModule[] = [
    { id: 1, productId: 8, code: 'R1', name: '根一', status: 'ACTIVE', sortOrder: 2, version: 0 },
    { id: 2, productId: 8, parentId: 1, code: 'B1', name: '分支一', status: 'ACTIVE', sortOrder: 1, version: 0 },
    { id: 3, productId: 8, parentId: 2, code: 'L1', name: '叶一', status: 'ACTIVE', sortOrder: 1, version: 0 },
    { id: 4, productId: 8, code: 'R2', name: '根二', status: 'ACTIVE', sortOrder: 1, version: 0 },
    { id: 5, productId: 8, parentId: 4, code: 'B2', name: '分支二', status: 'ACTIVE', sortOrder: 1, version: 0 },
    { id: 6, productId: 8, parentId: 5, code: 'L2', name: '叶二', status: 'ACTIVE', sortOrder: 1, version: 0 },
  ]
  const snapshot = hierarchy.map(item => ({ ...item }))
  const tree = buildModuleTree(hierarchy)

  expect(hierarchy).toEqual(snapshot)
  expect(tree.map(item => item.key)).toEqual([4, 1])
  expect(validParentModules(hierarchy, hierarchy[1]).map(item => item.id)).toEqual([1, 4])
})

it('按标签懒加载数据并渲染三级模块树和模块功能', async () => {
  const fetch = show()
  const user = userEvent.setup()
  expect(await screen.findByRole('tab', { name: '模块与功能' })).toBeVisible()
  expect(screen.getByRole('link', { name: /返回产品中心/ })).toHaveClass('detail-back-link')
  expect(fetch).not.toHaveBeenCalledWith('/api/v1/products/8/modules', expect.anything())

  await user.click(screen.getByRole('tab', { name: '模块与功能' }))
  expect(await screen.findByTestId('product-module-tree-scroll')).toBeVisible()
  expect(await screen.findByText('FIN · 财务管理')).toBeVisible()
  expect(screen.getByText('AR · 应收管理')).toBeVisible()
  expect(screen.getByText('AR-SETTLE · 对账中心')).toBeVisible()
  await user.click(screen.getByText('AR · 应收管理'))
  expect(await screen.findByText('应收对账')).toBeVisible()
  expect(screen.queryByText('总账处理')).not.toBeInTheDocument()
})

it('产品文档使用独立目录并支持编辑 Outline 正文', async () => {
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    if (init?.method === 'PUT' && path === '/api/v1/products/8/document-nodes/102/content') {
      return json({ ...featureSpec, markdown: '# 产品一页纸\n\n补充边界', revision: 4 })
    }
    return responseFor(path)
  })
  show(fetch)
  const user = userEvent.setup()
  await user.click(await screen.findByRole('tab', { name: '产品文档' }))
  expect(await screen.findByText('独立文档工作区 · 内容同步至 Outline')).toBeVisible()
  expect(screen.queryByText('产品结构与 Outline 实时对应')).not.toBeInTheDocument()
  await user.click(await screen.findByText('产品一页纸'))
  expect(await screen.findByRole('heading', { name: '产品一页纸' })).toBeVisible()
  await user.click(screen.getByRole('button', { name: '编辑' }))
  await user.type(screen.getByLabelText('Markdown 正文'), '\n\n补充边界')
  await user.click(screen.getByRole('button', { name: '保存' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith(
    '/api/v1/products/8/document-nodes/102/content', expect.objectContaining({
      method: 'PUT', body: expect.stringContaining('"revision":3'),
    }),
  ))
})

it('创建模块并编辑功能时提交乐观锁版本并刷新覆盖度', async () => {
  let coverageGets = 0
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    if (!init?.method && path.endsWith('/coverage')) {
      coverageGets += 1
      return json(coverage)
    }
    if (init?.method === 'POST' && path.endsWith('/modules')) return json({ ...modules[0], id: 14 }, 201)
    if (init?.method === 'PUT' && path.endsWith('/features/22')) return json({ ...features[1], name: '应收智能对账', version: 1 })
    return responseFor(path)
  })
  show(fetch)
  const user = userEvent.setup()
  await user.click(await screen.findByRole('tab', { name: '覆盖度' }))
  expect(await screen.findByText('完整 3')).toBeVisible()
  expect(coverageGets).toBe(1)
  await user.click(await screen.findByRole('tab', { name: '模块与功能' }))

  await user.click(await screen.findByRole('button', { name: '新建模块' }))
  let drawer = screen.getByRole('dialog', { name: '新建模块' })
  await user.type(within(drawer).getByLabelText('模块编码'), 'TAX')
  await user.type(within(drawer).getByLabelText('模块名称'), '税务管理')
  await user.click(within(drawer).getByRole('button', { name: '保存模块' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/products/8/modules', expect.objectContaining({
    method: 'POST', body: expect.stringContaining('"version":0'),
  })))
  await waitFor(() => expect(coverageGets).toBe(2))

  await user.click(screen.getByText('AR · 应收管理'))
  await user.click(await screen.findByRole('button', { name: '编辑应收对账' }))
  drawer = screen.getByRole('dialog', { name: '编辑功能' })
  const name = within(drawer).getByLabelText('功能名称')
  await user.clear(name)
  await user.type(name, '应收智能对账')
  await user.click(within(drawer).getByRole('button', { name: '保存功能' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/products/8/features/22', expect.objectContaining({
    method: 'PUT', body: expect.stringMatching(/"moduleId":12.*"version":0|"version":0.*"moduleId":12/),
  })))
  await waitFor(() => expect(coverageGets).toBe(3))
})

it('移动模块时提交新父级和当前乐观锁版本', async () => {
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    if (init?.method === 'PUT' && path.endsWith('/modules/13')) return json({ ...modules[2], parentId: 11, version: 1 })
    return responseFor(path)
  })
  show(fetch)
  const user = userEvent.setup()
  await user.click(await screen.findByRole('tab', { name: '模块与功能' }))
  await user.click(await screen.findByText('AR-SETTLE · 对账中心'))
  await user.click(screen.getByRole('button', { name: /编辑当前模块/ }))
  const drawer = screen.getByRole('dialog', { name: '编辑模块' })
  await user.click(within(drawer).getByRole('combobox', { name: '父模块' }))
  await user.click(await screen.findByRole('option', { name: 'FIN · 财务管理' }))
  await user.click(within(drawer).getByRole('button', { name: '保存模块' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/products/8/modules/13', expect.objectContaining({
    method: 'PUT', body: expect.stringMatching(/"parentId":11.*"version":0|"version":0.*"parentId":11/),
  })))
})

it('编辑版本并以当前版本号全量替换功能清单', async () => {
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    if (init?.method === 'PUT' && path.endsWith('/versions/31/features')) {
      return json({ versionId: 31, version: 4, entries: [{ featureId: 21, availability: 'PLANNED' }, { featureId: 22, availability: 'INCLUDED' }] })
    }
    if (init?.method === 'PUT' && path.endsWith('/versions/31')) return json({ ...versions[0], status: 'RELEASED', version: 4 })
    return responseFor(path)
  })
  show(fetch)
  const user = userEvent.setup()
  await user.click(await screen.findByRole('tab', { name: '版本' }))
  await user.click(await screen.findByRole('button', { name: '编辑版本 V5.2' }))
  const drawer = screen.getByRole('dialog', { name: '编辑版本' })
  expect(within(drawer).getByLabelText('版本名称')).toBeDisabled()
  await user.click(within(drawer).getByRole('button', { name: '关闭' }))

  await user.selectOptions(await screen.findByLabelText('总账处理可用性'), 'PLANNED')
  await user.selectOptions(screen.getByLabelText('应收对账可用性'), 'INCLUDED')
  await user.click(screen.getByRole('button', { name: '保存功能清单' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/products/8/versions/31/features', expect.objectContaining({
    method: 'PUT', body: JSON.stringify({ version: 3, entries: [
      { featureId: 21, availability: 'PLANNED' }, { featureId: 22, availability: 'INCLUDED' },
    ] }),
  })))
})

it('只有规划版本可编辑功能清单且切换版本会同步只读状态', async () => {
  const sunset = { id: 33, productId: 8, versionName: 'V5.0', releaseDate: '2026-05-01', status: 'SUNSET', version: 6 }
  const archived = { id: 34, productId: 8, versionName: 'V4.9', releaseDate: '2026-04-01', status: 'ARCHIVED', version: 7 }
  const fetch = vi.fn((input: RequestInfo | URL) => {
    const path = String(input)
    if (path === '/api/v1/products/8/versions') return json([...versions, sunset, archived])
    if (path === '/api/v1/products/8/versions/33/features') return json({ versionId: 33, version: 6, entries: [] })
    if (path === '/api/v1/products/8/versions/34/features') return json({ versionId: 34, version: 7, entries: [] })
    return responseFor(path)
  })
  show(fetch)
  const user = userEvent.setup()
  await user.click(await screen.findByRole('tab', { name: '版本' }))

  const save = await screen.findByRole('button', { name: '保存功能清单' })
  expect(save).toBeEnabled()
  expect(screen.getByLabelText('总账处理可用性')).toBeEnabled()

  await user.click(screen.getByText('V5.1'))
  await waitFor(() => expect(screen.getByLabelText('总账处理可用性')).toBeDisabled())
  expect(save).toBeDisabled()
  await user.selectOptions(screen.getByLabelText('总账处理可用性'), 'PLANNED')
  expect(fetch).not.toHaveBeenCalledWith('/api/v1/products/8/versions/32/features', expect.objectContaining({ method: 'PUT' }))
  await user.click(screen.getByRole('button', { name: '编辑版本 V5.1' }))
  const drawer = screen.getByRole('dialog', { name: '编辑版本' })
  expect(within(drawer).getByRole('button', { name: '保存版本' })).toBeEnabled()
  await user.click(within(drawer).getByRole('combobox', { name: '状态' }))
  expect(await screen.findByRole('option', { name: '停止维护' })).toBeVisible()
  expect(screen.queryByRole('option', { name: '规划中' })).not.toBeInTheDocument()
  await user.click(within(drawer).getByRole('button', { name: '关闭' }))

  for (const versionName of ['V5.0', 'V4.9']) {
    await user.click(screen.getByRole('cell', { name: versionName }))
    await waitFor(() => expect(screen.getByLabelText('总账处理可用性')).toBeDisabled())
    expect(save).toBeDisabled()
  }

  await user.click(screen.getByRole('cell', { name: 'V5.2' }))
  await waitFor(() => expect(screen.getByLabelText('总账处理可用性')).toBeEnabled())
  expect(save).toBeEnabled()
})

it('清单保存 pending 时切换版本不会污染另一版本缓存', async () => {
  let resolveVersionA!: (value: Response) => void
  const pendingVersionA = new Promise<Response>(resolve => { resolveVersionA = resolve })
  const manifestA = { versionId: 31, version: 3, entries: [{ featureId: 21, availability: 'INCLUDED' }] }
  const savedManifestA = { ...manifestA, version: 4 }
  const manifestB = { versionId: 32, version: 5, entries: [
    { featureId: 21, availability: 'PLANNED' }, { featureId: 22, availability: 'INCLUDED' },
  ] }
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    if (!init?.method && path === '/api/v1/products/8/versions') {
      return json([versions[0], { ...versions[1], status: 'PLANNING' }])
    }
    if (path.endsWith('/versions/31/features') && init?.method === 'PUT') return pendingVersionA
    if (path.endsWith('/versions/32/features') && init?.method === 'PUT') return json({ ...manifestB, version: 6 })
    if (!init?.method && path.endsWith('/versions/31/features')) return json(manifestA)
    if (!init?.method && path.endsWith('/versions/32/features')) return json(manifestB)
    return responseFor(path)
  })
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  show(fetch, ['product:read', 'product:write'], client)
  const user = userEvent.setup()
  await user.click(await screen.findByRole('tab', { name: '版本' }))
  expect(await screen.findByLabelText('总账处理可用性')).toHaveValue('INCLUDED')
  await user.click(screen.getByRole('button', { name: '保存功能清单' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/products/8/versions/31/features', expect.objectContaining({
    method: 'PUT', body: expect.stringContaining('"version":3'),
  })))

  await user.click(screen.getByText('V5.1'))
  expect(await screen.findByLabelText('总账处理可用性')).toHaveValue('PLANNED')
  void json(savedManifestA).then(resolveVersionA)

  await waitFor(() => expect(client.getQueryData(['product-manifest', 8, 31])).toEqual(savedManifestA))
  expect(client.getQueryData(['product-manifest', 8, 32])).toEqual(manifestB)
  expect(screen.getByLabelText('总账处理可用性')).toHaveValue('PLANNED')

  await user.click(screen.getByRole('button', { name: '保存功能清单' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/products/8/versions/32/features', expect.objectContaining({
    method: 'PUT', body: expect.stringContaining('"version":5'),
  })))
})

it('更新版本后保存清单使用服务端返回的新乐观锁版本', async () => {
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    if (init?.method === 'PUT' && path.endsWith('/versions/31/features')) {
      return json({ versionId: 31, version: 5, entries: [{ featureId: 21, availability: 'INCLUDED' }] })
    }
    if (init?.method === 'PUT' && path.endsWith('/versions/31')) return json({ ...versions[0], releaseDate: '2026-08-02', version: 4 })
    return responseFor(path)
  })
  show(fetch)
  const user = userEvent.setup()
  await user.click(await screen.findByRole('tab', { name: '版本' }))
  await user.click(await screen.findByRole('button', { name: '编辑版本 V5.2' }))
  const drawer = screen.getByRole('dialog', { name: '编辑版本' })
  const date = within(drawer).getByLabelText('发布日期')
  await user.clear(date)
  await user.type(date, '2026-08-02')
  await user.click(within(drawer).getByRole('button', { name: '保存版本' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/products/8/versions/31', expect.objectContaining({
    method: 'PUT', body: expect.stringMatching(/"releaseDate":"2026-08-02".*"version":3|"version":3.*"releaseDate":"2026-08-02"/),
  })))
  await waitFor(() => expect(screen.queryByRole('dialog', { name: '编辑版本' })).not.toBeInTheDocument())
  await user.click(screen.getByRole('button', { name: '保存功能清单' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/products/8/versions/31/features', expect.objectContaining({
    method: 'PUT', body: expect.stringContaining('"version":4'),
  })))
})

it('版本更新会阻止进行中的旧清单请求回填过期版本', async () => {
  let resolveOldManifest!: (value: Response) => void
  const oldManifest = new Promise<Response>(resolve => { resolveOldManifest = resolve })
  let manifestGets = 0
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    if (!init?.method && path.endsWith('/versions/31/features')) {
      manifestGets += 1
      return manifestGets === 1 ? oldManifest
        : json({ versionId: 31, version: 4, entries: [{ featureId: 21, availability: 'INCLUDED' }] })
    }
    if (init?.method === 'PUT' && path.endsWith('/versions/31')) return json({ ...versions[0], releaseDate: '2026-08-03', version: 4 })
    if (init?.method === 'PUT' && path.endsWith('/versions/31/features')) {
      return json({ versionId: 31, version: 5, entries: [{ featureId: 21, availability: 'INCLUDED' }] })
    }
    return responseFor(path)
  })
  show(fetch)
  const user = userEvent.setup()
  await user.click(await screen.findByRole('tab', { name: '版本' }))
  await user.click(await screen.findByRole('button', { name: '编辑版本 V5.2' }))
  const drawer = screen.getByRole('dialog', { name: '编辑版本' })
  const date = within(drawer).getByLabelText('发布日期')
  await user.clear(date)
  await user.type(date, '2026-08-03')
  await user.click(within(drawer).getByRole('button', { name: '保存版本' }))
  await waitFor(() => expect(screen.queryByRole('dialog', { name: '编辑版本' })).not.toBeInTheDocument())
  void json({ versionId: 31, version: 3, entries: [{ featureId: 21, availability: 'INCLUDED' }] }).then(resolveOldManifest)
  await waitFor(() => expect(screen.getByRole('button', { name: '保存功能清单' })).toBeEnabled())
  await user.click(screen.getByRole('button', { name: '保存功能清单' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/products/8/versions/31/features', expect.objectContaining({
    method: 'PUT', body: expect.stringContaining('"version":4'),
  })))
})

it('版本 PUT 期间新启动的旧清单 GET 不会覆盖服务端新版本', async () => {
  let resolveVersionPut!: (value: Response) => void
  const versionPut = new Promise<Response>(resolve => { resolveVersionPut = resolve })
  let resolveStaleManifest!: (value: Response) => void
  const staleManifest = new Promise<Response>(resolve => { resolveStaleManifest = resolve })
  let manifestGets = 0
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    if (!init?.method && path.endsWith('/versions/31/features')) {
      manifestGets += 1
      return manifestGets === 1
        ? json({ versionId: 31, version: 3, entries: [{ featureId: 21, availability: 'INCLUDED' }] })
        : staleManifest
    }
    if (init?.method === 'PUT' && path.endsWith('/versions/31')) return versionPut
    if (init?.method === 'PUT' && path.endsWith('/versions/31/features')) {
      return json({ versionId: 31, version: 5, entries: [{ featureId: 21, availability: 'INCLUDED' }] })
    }
    return responseFor(path)
  })
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  show(fetch, ['product:read', 'product:write'], client)
  const user = userEvent.setup()
  await user.click(await screen.findByRole('tab', { name: '版本' }))
  await waitFor(() => expect(manifestGets).toBe(1))
  await user.click(await screen.findByRole('button', { name: '编辑版本 V5.2' }))
  const drawer = screen.getByRole('dialog', { name: '编辑版本' })
  const date = within(drawer).getByLabelText('发布日期')
  await user.clear(date)
  await user.type(date, '2026-08-04')
  await user.click(within(drawer).getByRole('button', { name: '保存版本' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/products/8/versions/31', expect.objectContaining({ method: 'PUT' })))

  const staleRefetch = client.refetchQueries({ queryKey: ['product-manifest', 8, 31], exact: true })
  await waitFor(() => expect(manifestGets).toBe(2))
  void json({ ...versions[0], releaseDate: '2026-08-04', version: 4 }).then(resolveVersionPut)
  await waitFor(() => expect(screen.queryByRole('dialog', { name: '编辑版本' })).not.toBeInTheDocument())
  void json({ versionId: 31, version: 3, entries: [{ featureId: 21, availability: 'INCLUDED' }] }).then(resolveStaleManifest)
  await staleRefetch
  await waitFor(() => expect(client.isFetching({ queryKey: ['product-manifest', 8, 31], exact: true })).toBe(0))

  await user.click(screen.getByRole('button', { name: '保存功能清单' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/products/8/versions/31/features', expect.objectContaining({
    method: 'PUT', body: expect.stringContaining('"version":4'),
  })))
})

it('初始清单 GET 被取消且版本 PUT 失败时会自动恢复清单', async () => {
  let resolveInitialManifest!: (value: Response) => void
  const initialManifest = new Promise<Response>(resolve => { resolveInitialManifest = resolve })
  let manifestGets = 0
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    if (!init?.method && path.endsWith('/versions/31/features')) {
      manifestGets += 1
      return manifestGets === 1 ? initialManifest
        : json({ versionId: 31, version: 3, entries: [{ featureId: 21, availability: 'INCLUDED' }] })
    }
    if (init?.method === 'PUT' && path.endsWith('/versions/31')) return json({ code: 'CONFLICT', message: '版本保存冲突' }, 409)
    return responseFor(path)
  })
  show(fetch)
  const user = userEvent.setup()
  await user.click(await screen.findByRole('tab', { name: '版本' }))
  await waitFor(() => expect(manifestGets).toBe(1))
  await user.click(await screen.findByRole('button', { name: '编辑版本 V5.2' }))
  const drawer = screen.getByRole('dialog', { name: '编辑版本' })
  const date = within(drawer).getByLabelText('发布日期')
  await user.clear(date)
  await user.type(date, '2026-08-05')
  await user.click(within(drawer).getByRole('button', { name: '保存版本' }))

  expect(await screen.findByText('版本保存冲突')).toBeVisible()
  await waitFor(() => expect(manifestGets).toBe(2))
  expect(await screen.findByLabelText('总账处理可用性')).toHaveValue('INCLUDED')
  expect(screen.getByRole('button', { name: '保存功能清单' })).toBeEnabled()
  void json({ versionId: 31, version: 3, entries: [] }).then(resolveInitialManifest)
})

it('编辑非当前版本时按该版本清单执行发布门禁', async () => {
  const planning = { id: 33, productId: 8, versionName: 'V6.0', releaseDate: '2026-09-01', status: 'PLANNING', version: 0 }
  const fetch = vi.fn((input: RequestInfo | URL) => {
    const path = String(input)
    if (path === '/api/v1/products/8/versions') return json([...versions, planning])
    if (path === '/api/v1/products/8/versions/33/features') return json({ versionId: 33, version: 0, entries: [] })
    return responseFor(path)
  })
  show(fetch)
  const user = userEvent.setup()
  await user.click(await screen.findByRole('tab', { name: '版本' }))
  await user.click(await screen.findByRole('button', { name: '编辑版本 V6.0' }))
  const drawer = screen.getByRole('dialog', { name: '编辑版本' })
  await user.click(within(drawer).getByRole('combobox', { name: '状态' }))
  await user.click(await screen.findByRole('option', { name: '已发布' }))
  expect(within(drawer).getByRole('button', { name: '保存版本' })).toBeDisabled()
  expect(within(drawer).getByText('发布前至少纳入一个功能')).toBeVisible()
})

it('展示覆盖聚合、需求上下文链接并可从错误状态重试', async () => {
  let coverageAttempts = 0
  const fetch = vi.fn((input: RequestInfo | URL) => {
    const path = String(input)
    if (path.endsWith('/coverage') && coverageAttempts++ === 0) return json({ code: 'FAILED', message: '覆盖数据加载失败' }, 500)
    return responseFor(path)
  })
  show(fetch)
  const user = userEvent.setup()
  await user.click(await screen.findByRole('tab', { name: '覆盖度' }))
  expect(await screen.findByText('覆盖数据加载失败')).toBeVisible()
  await user.click(screen.getByRole('button', { name: '重新加载' }))
  expect(await screen.findByText('总账处理')).toBeVisible()
  expect(screen.getByText('完整 3')).toBeVisible()
  expect(screen.getByText('部分 1')).toBeVisible()
  const link = screen.getByRole('link', { name: /REQ-41/ })
  expect(link).toHaveAttribute('href', '/requirements?requirementId=41')
  await user.click(link)
  const drawer = await screen.findByRole('dialog', { name: 'AI 分类决策树' })
  expect(within(drawer).getByRole('heading', { name: coverage.uncoveredRequirements[0].title })).toBeVisible()
})

it('有写权限的账号仍不能编辑归档产品', async () => {
  const fetch = vi.fn((input: RequestInfo | URL) => {
    const path = String(input)
    if (path === '/api/v1/products/8') return json({ ...product, status: 'ARCHIVED' })
    return responseFor(path)
  })
  show(fetch)
  const user = userEvent.setup()
  expect(await screen.findByText('该产品已归档，所有配置仅可查看。')).toBeVisible()
  await user.click(screen.getByRole('tab', { name: '模块与功能' }))
  expect(screen.queryByRole('button', { name: '新建模块' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '新建功能' })).not.toBeInTheDocument()
  await user.click(screen.getByRole('tab', { name: '版本' }))
  expect(screen.queryByRole('button', { name: '新建版本' })).not.toBeInTheDocument()
  expect(await screen.findByRole('button', { name: '保存功能清单' })).toBeDisabled()
})

it('启用中的产品对无 product:write 账号保持只读', async () => {
  show(undefined, ['product:read'])
  const user = userEvent.setup()
  expect(await screen.findByRole('heading', { name: product.name })).toBeVisible()
  expect(screen.queryByText('该产品已归档，所有配置仅可查看。')).not.toBeInTheDocument()
  await user.click(screen.getByRole('tab', { name: '模块与功能' }))
  expect(screen.queryByRole('button', { name: '新建模块' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '新建功能' })).not.toBeInTheDocument()
  await user.click(screen.getByRole('tab', { name: '版本' }))
  expect(screen.queryByRole('button', { name: '新建版本' })).not.toBeInTheDocument()
  expect(await screen.findByRole('button', { name: '保存功能清单' })).toBeDisabled()
})
