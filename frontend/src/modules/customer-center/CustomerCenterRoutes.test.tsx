import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, vi } from 'vitest'
import { AuthContext, type AuthState } from '../../app/AuthProvider'
import { CustomerCenterRoutes } from './CustomerCenterRoutes'

const auth: AuthState = {
  loading: false,
  me: { id: 1, organizationId: 1, username: 'crm-owner', displayName: '客户负责人', roles: ['PMO'], permissions: [] },
  login: async () => undefined, logout: async () => undefined, refresh: async () => undefined,
}

function show(path: string, permissions: string[]) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}>
    <AuthContext.Provider value={{ ...auth, me: { ...auth.me!, permissions } }}>
      <MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <Routes><Route path="/customers/*" element={<CustomerCenterRoutes />} /></Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  </QueryClientProvider>)
}

afterEach(() => vi.unstubAllGlobals())

it('按固定顺序展示客户中心六个页签', () => {
  vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response('[]', {
    status: 200, headers: { 'Content-Type': 'application/json' },
  }))))
  show('/customers/opportunities', ['customer:read', 'crm:read'])

  const tabs = screen.getAllByRole('link').map(link => link.textContent)
  expect(tabs).toEqual(['客户管理', '商机总览', '售前推进', '实施协同', '实施驾驶舱', '客户运营'])
  expect(screen.getByRole('link', { name: '实施驾驶舱' })).toHaveAttribute('href', '/customers/implementation-cockpit')
})

it('客户主数据和CRM工作区使用独立读权限', () => {
  vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response('[]', {
    status: 200, headers: { 'Content-Type': 'application/json' },
  }))))
  const customerOnly = show('/customers', ['customer:read'])
  expect(screen.getByRole('link', { name: '客户管理' })).toBeVisible()
  expect(screen.queryByRole('link', { name: '商机总览' })).not.toBeInTheDocument()
  customerOnly.unmount()

  show('/customers/opportunities', ['crm:read'])
  expect(screen.queryByRole('link', { name: '客户管理' })).not.toBeInTheDocument()
  expect(screen.getByRole('link', { name: '商机总览' })).toBeVisible()
  expect(screen.getByRole('heading', { name: '商机总览' })).toBeVisible()
})

it('CRM用户从客户中心入口自动进入商机总览', async () => {
  vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response('[]', {
    status: 200, headers: { 'Content-Type': 'application/json' },
  }))))
  show('/customers', ['crm:read'])

  expect(await screen.findByRole('heading', { name: '商机总览' })).toBeVisible()
  expect(screen.queryByText('无权访问')).not.toBeInTheDocument()
})

it('CRM只读用户看不到写操作', () => {
  vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response('[]', {
    status: 200, headers: { 'Content-Type': 'application/json' },
  }))))
  show('/customers/opportunities', ['crm:read'])
  expect(screen.queryByRole('button', { name: /新建|编辑|推进/ })).not.toBeInTheDocument()
})
