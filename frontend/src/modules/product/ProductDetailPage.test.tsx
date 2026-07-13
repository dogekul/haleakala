import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import { AuthContext, type AuthState } from '../../app/AuthProvider'
import { ProductDetailPage } from './ProductDetailPage'

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
  if (path.startsWith('/api/v1/products/8/features')) return json(features)
  if (path === '/api/v1/products/8/versions') return json(versions)
  if (path === '/api/v1/products/8/versions/31/features') return json({ versionId: 31, version: 3, entries: [{ featureId: 21, availability: 'INCLUDED' }] })
  if (path === '/api/v1/products/8/versions/32/features') return json({ versionId: 32, version: 5, entries: [{ featureId: 21, availability: 'INCLUDED' }] })
  if (path === '/api/v1/products/8/coverage') return json(coverage)
  throw new Error(`unexpected request: ${path}`)
}

function show(fetch = vi.fn((input: RequestInfo | URL) => responseFor(String(input))), permissions = ['product:read', 'product:write']) {
  vi.stubGlobal('fetch', fetch)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={client}>
    <AuthContext.Provider value={{ ...auth, me: { ...auth.me!, permissions } }}>
      <MemoryRouter initialEntries={['/products/8']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <Routes><Route path="/products/:productId" element={<ProductDetailPage />} /><Route path="/requirements" element={<span>需求工作台</span>} /></Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  </QueryClientProvider>)
  return fetch
}

afterEach(() => vi.unstubAllGlobals())

it('按标签懒加载数据并渲染三级模块树和模块功能', async () => {
  const fetch = show()
  const user = userEvent.setup()
  expect(await screen.findByRole('tab', { name: '模块与功能' })).toBeVisible()
  expect(fetch).not.toHaveBeenCalledWith('/api/v1/products/8/modules', expect.anything())

  await user.click(screen.getByRole('tab', { name: '模块与功能' }))
  expect(await screen.findByText('FIN · 财务管理')).toBeVisible()
  expect(screen.getByText('AR · 应收管理')).toBeVisible()
  expect(screen.getByText('AR-SETTLE · 对账中心')).toBeVisible()
  await user.click(screen.getByText('AR · 应收管理'))
  expect(await screen.findByText('应收对账')).toBeVisible()
  expect(screen.queryByText('总账处理')).not.toBeInTheDocument()
})

it('创建模块并编辑功能时提交所属模块和乐观锁版本', async () => {
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    if (init?.method === 'POST' && path.endsWith('/modules')) return json({ ...modules[0], id: 14 }, 201)
    if (init?.method === 'PUT' && path.endsWith('/features/22')) return json({ ...features[1], name: '应收智能对账', version: 1 })
    return responseFor(path)
  })
  show(fetch)
  const user = userEvent.setup()
  await user.click(await screen.findByRole('tab', { name: '模块与功能' }))

  await user.click(await screen.findByRole('button', { name: '新建模块' }))
  let drawer = screen.getByRole('dialog', { name: '新建模块' })
  await user.type(within(drawer).getByLabelText('模块编码'), 'TAX')
  await user.type(within(drawer).getByLabelText('模块名称'), '税务管理')
  await user.click(within(drawer).getByRole('button', { name: '保存模块' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/products/8/modules', expect.objectContaining({
    method: 'POST', body: expect.stringContaining('"version":0'),
  })))

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
  expect(screen.getByRole('link', { name: /REQ-41/ })).toHaveAttribute('href', '/requirements?requirementId=41')
})

it('归档产品和无写权限账号的全部编辑入口都只读', async () => {
  const fetch = vi.fn((input: RequestInfo | URL) => {
    const path = String(input)
    if (path === '/api/v1/products/8') return json({ ...product, status: 'ARCHIVED' })
    return responseFor(path)
  })
  show(fetch, ['product:read'])
  const user = userEvent.setup()
  expect(await screen.findByText('该产品已归档，所有配置仅可查看。')).toBeVisible()
  await user.click(screen.getByRole('tab', { name: '模块与功能' }))
  expect(screen.queryByRole('button', { name: '新建模块' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '新建功能' })).not.toBeInTheDocument()
  await user.click(screen.getByRole('tab', { name: '版本' }))
  expect(screen.queryByRole('button', { name: '新建版本' })).not.toBeInTheDocument()
  expect(await screen.findByRole('button', { name: '保存功能清单' })).toBeDisabled()
})
