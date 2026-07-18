import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { vi } from 'vitest'
import { AuthContext, type AuthState } from '../../app/AuthProvider'
import { AdminPage } from './AdminPage'

const json = (value: unknown) => Promise.resolve(new Response(JSON.stringify(value), {
  status: 200,
  headers: { 'Content-Type': 'application/json' },
}))

const auth: AuthState = {
  loading: false,
  me: {
    id: 1, organizationId: 1, username: 'admin', displayName: '管理员',
    roles: ['ADMIN'], permissions: ['admin:write'],
  },
  login: async () => undefined,
  logout: async () => undefined,
  refresh: async () => undefined,
}

function LocationProbe() {
  return <span data-testid="location">{useLocation().pathname}</span>
}

it('提供六个可用的系统管理入口并默认进入用户团队', async () => {
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
    const url = String(input)
    if (url === '/api/v1/admin/ai-service/config') return json({
      baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
      model: 'qwen-plus', apiKeyConfigured: true, source: 'ORGANIZATION',
    })
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
  for (const name of ['用户与团队', '角色权限', '审计日志', '文档中心', 'AI 服务', '系统设置']) {
    expect(screen.getByRole('link', { name })).toBeVisible()
  }
  expect(screen.queryByRole('link', { name: '产品目录' })).not.toBeInTheDocument()

  await userEvent.click(screen.getByRole('link', { name: 'AI 服务' }))
  expect(await screen.findByRole('heading', { name: 'AI 服务' })).toBeVisible()
})

it('文档中心展示连接、根目录和可重试任务', async () => {
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    if (url === '/api/v1/admin/document-center/config') return json({
      baseUrl: 'http://outline.internal:3000',
      publicBaseUrl: 'http://localhost:3000',
      collectionId: 'collection-id',
      collectionName: '智鹿交付',
      apiTokenConfigured: true,
      source: 'ORGANIZATION',
    })
    if (url === '/api/v1/admin/document-center/status') return json({
      integrationStatus: 'READY',
      collectionId: 'collection-id',
      knowledgeRoot: { linkId: 11, status: 'READY' },
      projectRoot: { linkId: 12, status: 'READY' },
      jobs: { pending: 1, running: 0, success: 8, failed: 1 },
      failedJobs: [{ id: 99 }],
    })
    if (url === '/api/v1/admin/document-center/jobs') return json([{
      id: 99, jobType: 'PROJECT_INIT', businessKey: 'PROJECT:9', businessId: 9,
      status: 'FAILED', attemptCount: 3, lastError: 'Outline 暂不可用',
      updatedAt: '2026-07-16T08:00:00Z',
    }])
    if (url === '/api/v1/admin/document-center/jobs/99/retry' && init?.method === 'POST') {
      return json({ id: 99, status: 'PENDING' })
    }
    return json([])
  })
  vi.stubGlobal('fetch', fetch)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}>
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={['/admin/document-center']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <Routes><Route path="/admin/*" element={<AdminPage />} /></Routes>
      </MemoryRouter>
    </AuthContext.Provider>
  </QueryClientProvider>)

  expect(await screen.findByRole('heading', { name: '文档中心' })).toBeVisible()
  expect(screen.getByText('知识库根目录')).toBeVisible()
  expect(screen.getByText('项目文档根目录')).toBeVisible()
  expect(await screen.findByText('Outline 暂不可用')).toBeVisible()
  await userEvent.click(screen.getByRole('button', { name: '重试' }))
  expect(fetch).toHaveBeenCalledWith(
    '/api/v1/admin/document-center/jobs/99/retry',
    expect.objectContaining({ method: 'POST' }),
  )
})

it('旧产品目录地址单向重定向到产品中心', async () => {
  vi.stubGlobal('fetch', vi.fn(() => json([])))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}>
    <MemoryRouter initialEntries={['/admin/products']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <LocationProbe />
      <Routes>
        <Route path="/admin/*" element={<AdminPage />} />
        <Route path="/products" element={<span>产品中心页面</span>} />
      </Routes>
    </MemoryRouter>
  </QueryClientProvider>)

  expect(await screen.findByText('产品中心页面')).toBeVisible()
  expect(screen.getByTestId('location')).toHaveTextContent('/products')
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
