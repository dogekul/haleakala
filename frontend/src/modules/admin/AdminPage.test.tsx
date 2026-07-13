import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { vi } from 'vitest'
import { AdminPage } from './AdminPage'

const json = (value: unknown) => Promise.resolve(new Response(JSON.stringify(value), {
  status: 200,
  headers: { 'Content-Type': 'application/json' },
}))

function LocationProbe() {
  return <span data-testid="location">{useLocation().pathname}</span>
}

it('提供五个可用的系统管理入口并默认进入用户团队', async () => {
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
    const url = String(input)
    if (url.includes('/settings')) return json({
      platformName: '智鹿交付', environmentLabel: '内部生产环境', timezone: 'Asia/Shanghai',
      supportEmail: '', agentTimeoutMinutes: 30,
    })
    return json([])
  }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}>
    <MemoryRouter initialEntries={['/admin']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routes><Route path="/admin/*" element={<AdminPage />} /></Routes>
    </MemoryRouter>
  </QueryClientProvider>)

  expect(await screen.findByRole('heading', { name: '用户与团队' })).toBeVisible()
  expect(screen.getByRole('link', { name: '用户与团队' })).toHaveClass('active')
  for (const name of ['用户与团队', '角色权限', '产品目录', '审计日志', '系统设置']) {
    expect(screen.getByRole('link', { name })).toBeVisible()
  }

  await userEvent.click(screen.getByRole('link', { name: '系统设置' }))
  expect(await screen.findByRole('heading', { name: '系统设置' })).toBeVisible()
})

it('数据请求失败时显示可重试的错误态', async () => {
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
    if (String(input).includes('/admin/users')) return Promise.resolve(new Response(JSON.stringify({
      code: 'REQUEST_FAILED', message: '用户数据暂时不可用',
    }), { status: 503, headers: { 'Content-Type': 'application/json' } }))
    return json([])
  }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}>
    <MemoryRouter initialEntries={['/admin/users']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routes><Route path="/admin/*" element={<AdminPage />} /></Routes>
    </MemoryRouter>
  </QueryClientProvider>)

  expect(await screen.findByText('管理数据加载失败')).toBeVisible()
  expect(screen.getByText('用户数据暂时不可用')).toBeVisible()
  expect(screen.getByRole('button', { name: /重\s*试/ })).toBeVisible()
})

it('未知的系统管理地址只重定向一次并回到用户页', async () => {
  vi.stubGlobal('fetch', vi.fn(() => json([])))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}>
    <MemoryRouter initialEntries={['/admin/audit']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <LocationProbe />
      <Routes><Route path="/admin/*" element={<AdminPage />} /></Routes>
    </MemoryRouter>
  </QueryClientProvider>)

  expect(await screen.findByRole('heading', { name: '用户与团队' })).toBeVisible()
  expect(screen.getByTestId('location')).toHaveTextContent('/admin/users')
})
