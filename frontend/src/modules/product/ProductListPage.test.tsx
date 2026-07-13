import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import { ProductListPage } from './ProductListPage'
import { ProductDetailPage } from './ProductDetailPage'

const products = [
  {
    id: 8, organizationId: 1, ownerUserId: 20, code: 'ERP', name: '智鹿 ERP', category: '企业应用',
    description: '覆盖核心经营流程', status: 'ACTIVE', moduleCount: 3, featureCount: 12,
    latestVersionName: 'V2.1', updatedAt: '2026-07-12T08:00:00Z', version: 2,
  },
  {
    id: 9, organizationId: 1, code: 'CRM', name: '客户关系云', category: '客户经营',
    description: '仍在规划中的客户产品', status: 'PLANNING', moduleCount: 1, featureCount: 2,
    updatedAt: '2026-07-11T08:00:00Z', version: 0,
  },
  {
    id: 10, organizationId: 1, code: 'LEGACY', name: '超长归档产品名称用于验证卡片内容不会无限延展到容器之外',
    category: '企业应用', description: '归档产品只读', status: 'ARCHIVED', moduleCount: 4, featureCount: 20,
    latestVersionName: 'V1.0', updatedAt: '2026-07-10T08:00:00Z', version: 4,
  },
]

const json = (value: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(value), {
  status, headers: { 'Content-Type': 'application/json' },
}))

function show() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}>
    <MemoryRouter initialEntries={['/products']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routes>
        <Route path="/products" element={<ProductListPage />} />
        <Route path="/products/:productId" element={<span>产品详情</span>} />
      </Routes>
    </MemoryRouter>
  </QueryClientProvider>)
}

afterEach(() => vi.unstubAllGlobals())

it('筛选产品并在列表和卡片视图间切换', async () => {
  vi.stubGlobal('fetch', vi.fn(() => json(products)))
  const user = userEvent.setup()
  show()

  expect(await screen.findByRole('link', { name: '智鹿 ERP' })).toBeVisible()
  await user.type(screen.getByPlaceholderText('搜索产品名称或编码'), 'CRM')
  expect(screen.getByRole('link', { name: '客户关系云' })).toBeVisible()
  expect(screen.queryByRole('link', { name: '智鹿 ERP' })).not.toBeInTheDocument()

  await user.clear(screen.getByPlaceholderText('搜索产品名称或编码'))
  expect(screen.getByRole('radio', { name: '卡片' })).toBeInTheDocument()
  await user.click(screen.getByText('卡片'))
  expect(screen.getByTestId('product-card-grid')).toBeVisible()
  expect(screen.getByTestId('product-card-10')).toHaveClass('product-card')
  await user.click(within(screen.getByTestId('product-card-8')).getByRole('link', { name: '智鹿 ERP' }))
  expect(await screen.findByText('产品详情')).toBeVisible()
})

it('新建和编辑都提交乐观锁版本且编辑时锁定编码', async () => {
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    if (init?.method === 'POST') return json({ ...products[1], id: 11 }, 201)
    if (init?.method === 'PUT') return json({ ...products[0], name: '智鹿 ERP 企业版', version: 3 })
    return json(products)
  })
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  show()

  await user.click(await screen.findByRole('button', { name: '新建产品' }))
  let drawer = screen.getByRole('dialog', { name: '新建产品' })
  await user.type(within(drawer).getByLabelText('产品编码'), 'OA')
  await user.type(within(drawer).getByLabelText('产品名称'), '协同办公')
  await user.click(within(drawer).getByRole('button', { name: '保存' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/products', expect.objectContaining({
    method: 'POST', body: expect.stringContaining('"version":0'),
  })))

  await user.click(await screen.findByRole('button', { name: '编辑智鹿 ERP' }))
  drawer = screen.getByRole('dialog', { name: '编辑产品' })
  expect(within(drawer).getByLabelText('产品编码')).toBeDisabled()
  const name = within(drawer).getByLabelText('产品名称')
  await user.clear(name)
  await user.type(name, '智鹿 ERP 企业版')
  await user.click(within(drawer).getByRole('button', { name: '保存' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/products/8', expect.objectContaining({
    method: 'PUT', body: expect.stringContaining('"version":2'),
  })))
})

it('归档产品只能查看不能编辑', async () => {
  vi.stubGlobal('fetch', vi.fn(() => json(products)))
  const user = userEvent.setup()
  show()

  await user.click(await screen.findByRole('button', { name: '查看超长归档产品名称用于验证卡片内容不会无限延展到容器之外' }))
  const drawer = screen.getByRole('dialog', { name: '查看产品' })
  expect(within(drawer).getByLabelText('产品名称')).toBeDisabled()
  expect(within(drawer).queryByRole('button', { name: '保存' })).not.toBeInTheDocument()
  expect(within(drawer).getByText('归档产品仅可查看')).toBeVisible()
})

it('产品详情展示结构版本概览和归档只读提示', async () => {
  const fetch = vi.fn((input: RequestInfo | URL) => String(input).endsWith('/versions') ? json([
    { id: 1, productId: 10, versionName: 'V1.0', status: 'RELEASED', version: 1 },
  ]) : json(products[2]))
  vi.stubGlobal('fetch', fetch)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}>
    <MemoryRouter initialEntries={['/products/10']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routes><Route path="/products/:productId" element={<ProductDetailPage />} /></Routes>
    </MemoryRouter>
  </QueryClientProvider>)

  expect(await screen.findByRole('heading', { name: products[2].name })).toBeVisible()
  expect(screen.getByText('产品模块')).toBeVisible()
  expect(screen.getByText('标准功能')).toBeVisible()
  expect(screen.getByText('产品版本')).toBeVisible()
  expect(screen.getByText('该产品已归档，所有配置仅可查看。')).toBeVisible()
  expect(fetch).toHaveBeenCalledWith('/api/v1/products/10', expect.anything())
  expect(fetch).toHaveBeenCalledWith('/api/v1/products/10/versions', expect.anything())
})
