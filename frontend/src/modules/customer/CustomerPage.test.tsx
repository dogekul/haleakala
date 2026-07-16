import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, it, vi } from 'vitest'
import { AuthContext, type AuthState } from '../../app/AuthProvider'
import { CustomerPage } from './CustomerPage'

const customers = [
  { id: 81, organizationId: 1, name: '华东银行', shortName: '华东行', contactName: '王经理', phone: '13800000000', email: 'wang@example.com', address: '上海市浦东新区陆家嘴金融中心超长地址用于验证内容不会溢出列表或卡片', status: 'ACTIVE', remark: '核心客户', projectCount: 3, updatedAt: '2026-07-16T08:00:00', version: 1 },
  { id: 82, organizationId: 1, name: '华南制造', contactName: '李经理', phone: '13900000000', status: 'INACTIVE', projectCount: 0, updatedAt: '2026-07-15T08:00:00', version: 0 },
]

const auth: AuthState = {
  loading: false,
  me: { id: 1, organizationId: 1, username: 'customer-owner', displayName: '客户管理员', roles: ['PMO'], permissions: ['customer:read', 'customer:write'] },
  login: async () => undefined, logout: async () => undefined, refresh: async () => undefined,
}

const json = (value: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(value), {
  status, headers: { 'Content-Type': 'application/json' },
}))

function show(permissions = ['customer:read', 'customer:write']) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}>
    <AuthContext.Provider value={{ ...auth, me: { ...auth.me!, permissions } }}>
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}><CustomerPage /></MemoryRouter>
    </AuthContext.Provider>
  </QueryClientProvider>)
}

afterEach(() => vi.unstubAllGlobals())

it('展示客户概览并支持筛选和卡片视图且长文本不溢出', async () => {
  vi.stubGlobal('fetch', vi.fn(() => json(customers)))
  const user = userEvent.setup()
  show()

  expect(await screen.findByText('华东银行')).toBeVisible()
  expect(screen.getByText('客户总数')).toBeVisible()
  expect(screen.getByText('启用客户')).toBeVisible()
  expect(screen.getByText('停用客户')).toBeVisible()
  await user.type(screen.getByPlaceholderText('搜索客户、简称或联系人'), '王经理')
  expect(screen.getByText('华东银行')).toBeVisible()
  expect(screen.queryByText('华南制造')).not.toBeInTheDocument()
  await user.clear(screen.getByPlaceholderText('搜索客户、简称或联系人'))
  await user.click(screen.getByText('卡片'))
  expect(screen.getByTestId('customer-card-81')).toBeVisible()
  expect(within(screen.getByTestId('customer-card-81')).getByText(/上海市浦东新区/)).toHaveClass('customer-ellipsis')
})

it('新建和编辑只提交基本信息与乐观锁版本', async () => {
  const fetch = vi.fn((_: RequestInfo | URL, init?: RequestInit) => init?.method
    ? json({ ...customers[0], id: 83 }) : json(customers))
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  show()

  await user.click(await screen.findByRole('button', { name: '新建客户' }))
  let drawer = screen.getByRole('dialog', { name: '新建客户' })
  expect(within(drawer).queryByLabelText('客户编码')).not.toBeInTheDocument()
  expect(within(drawer).queryByLabelText('行业')).not.toBeInTheDocument()
  await user.type(within(drawer).getByLabelText('客户名称'), '新客户')
  await user.type(within(drawer).getByLabelText('联系人'), '赵经理')
  await user.click(within(drawer).getByRole('button', { name: '保存' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/customers', expect.objectContaining({
    method: 'POST', body: expect.stringContaining('"version":0'),
  })))

  await user.click(await screen.findByRole('button', { name: '编辑华东银行' }))
  drawer = screen.getByRole('dialog', { name: '编辑客户' })
  await user.clear(within(drawer).getByLabelText('联系电话'))
  await user.type(within(drawer).getByLabelText('联系电话'), '021-88888888')
  await user.click(within(drawer).getByRole('button', { name: '保存' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/customers/81', expect.objectContaining({
    method: 'PUT', body: expect.stringContaining('"version":1'),
  })))
})

it('只读用户可以查看但没有客户写操作', async () => {
  vi.stubGlobal('fetch', vi.fn(() => json(customers)))
  show(['customer:read'])

  expect(await screen.findByText('华东银行')).toBeVisible()
  expect(screen.queryByRole('button', { name: '新建客户' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '编辑华东银行' })).not.toBeInTheDocument()
})
