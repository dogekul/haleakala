import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, vi } from 'vitest'
import { AppShell } from './AppShell'
import { AuthContext, type AuthState } from '../app/AuthProvider'

const auth: AuthState = {
  loading: false,
  me: {
    id: 1,
    organizationId: 1,
    username: 'engineer',
    displayName: '交付工程师',
    roles: ['DELIVERY_ENGINEER'],
    permissions: ['customer:read', 'project:read', 'product:read', 'requirement:read'],
  },
  login: async () => undefined,
  logout: async () => undefined,
  refresh: async () => undefined,
}

afterEach(() => vi.unstubAllGlobals())

it('只显示当前用户有权访问的模块入口', () => {
  vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response(JSON.stringify({
    platformName: '智鹿交付', environmentLabel: '内部生产环境', timezone: 'Asia/Shanghai',
    supportEmail: '', agentTimeoutMinutes: 30,
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }))))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <AuthContext.Provider value={auth}>
        <MemoryRouter initialEntries={['/projects']}
          future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
          <AppShell><div>项目内容</div></AppShell>
        </MemoryRouter>
      </AuthContext.Provider>
    </QueryClientProvider>,
  )

  expect(screen.getByRole('link', { name: /项目空间/ })).toBeVisible()
  expect(screen.getByRole('link', { name: /客户管理/ })).toBeVisible()
  expect(screen.getByRole('link', { name: /产品中心/ })).toBeVisible()
  expect(screen.getByRole('link', { name: /需求工坊/ })).toBeVisible()
  const links = screen.getAllByRole('link').map(link => link.textContent)
  expect(links.indexOf('客户管理')).toBeLessThan(links.indexOf('项目空间'))
  expect(links.indexOf('产品中心')).toBeGreaterThan(links.indexOf('项目空间'))
  expect(links.indexOf('产品中心')).toBeLessThan(links.indexOf('需求工坊'))
  expect(screen.queryByRole('link', { name: /资源中心/ })).not.toBeInTheDocument()
  expect(screen.queryByRole('link', { name: /系统管理/ })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /项目列表/ })).not.toBeInTheDocument()
  expect(document.querySelector('.section-sidebar')).not.toBeInTheDocument()
  expect(screen.getByText('项目内容')).toBeVisible()
})

it('管理员保存的平台名称和环境标识会刷新应用壳层', async () => {
  vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response(JSON.stringify({
    platformName: '智鹿中台', environmentLabel: '验收环境', timezone: 'Asia/Shanghai',
    supportEmail: '', agentTimeoutMinutes: 30,
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }))))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const adminAuth: AuthState = { ...auth, me: { ...auth.me!, roles: ['ADMIN'], permissions: ['system:manage'] } }
  render(<QueryClientProvider client={client}>
    <AuthContext.Provider value={adminAuth}>
      <MemoryRouter initialEntries={['/admin/settings']}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <AppShell><div>设置内容</div></AppShell>
      </MemoryRouter>
    </AuthContext.Provider>
  </QueryClientProvider>)

  expect(await screen.findByRole('link', { name: '智鹿中台首页' })).toBeVisible()
  expect(screen.getByText('验收环境')).toBeVisible()
})

it('审计员只获得审计日志入口', async () => {
  vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response(JSON.stringify({
    platformName: '智鹿交付', environmentLabel: '验收环境', timezone: 'Asia/Shanghai',
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }))))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const auditorAuth: AuthState = { ...auth, me: { ...auth.me!, roles: ['VIEWER'], permissions: ['audit:read'] } }
  render(<QueryClientProvider client={client}>
    <AuthContext.Provider value={auditorAuth}>
      <MemoryRouter initialEntries={['/audit-logs']}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <AppShell><div>审计内容</div></AppShell>
      </MemoryRouter>
    </AuthContext.Provider>
  </QueryClientProvider>)

  expect(screen.getByRole('link', { name: /审计日志/ })).toBeVisible()
  expect(screen.queryByRole('link', { name: /系统管理/ })).not.toBeInTheDocument()
})
