import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { AuditLogsPage } from './AuditLogsPage'
import { RolesPage } from './RolesPage'
import { SettingsPage } from './SettingsPage'

const json = (value: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(value), {
  status, headers: { 'Content-Type': 'application/json' },
}))

function show(page: React.ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}>{page}</QueryClientProvider>)
}

afterEach(() => vi.unstubAllGlobals())

it('从角色抽屉保存权限并刷新角色', async () => {
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    if (init?.method === 'PUT') return json({ id: 6, code: 'VIEWER', name: '观察员', description: '', builtIn: true, permissions: ['dashboard:read'] })
    if (url.endsWith('/permissions')) return json([{ code: 'dashboard:read', name: '查看驾驶舱', module: '驾驶舱' }])
    return json([{ id: 6, code: 'VIEWER', name: '观察员', description: '', builtIn: true, permissions: [] }])
  })
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  show(<RolesPage />)

  await user.click(await screen.findByRole('button', { name: /配置权限/ }))
  await user.click(await screen.findByRole('checkbox', { name: /查看驾驶舱/ }))
  await user.click(screen.getByRole('button', { name: /保存权限/ }))

  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/admin/roles/6/permissions', expect.objectContaining({
    method: 'PUT', body: JSON.stringify({ permissionCodes: ['dashboard:read'] }),
  })))
})

it('非内置角色可确认删除且内置角色保持受保护', async () => {
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    if (init?.method === 'DELETE') return Promise.resolve(new Response(null, { status: 204 }))
    if (url.endsWith('/permissions')) return json([])
    return json([
      { id: 1, code: 'ADMIN', name: '系统管理员', description: '', builtIn: true, permissions: [] },
      { id: 100, code: 'CUSTOM', name: '自定义角色', description: '', builtIn: false, permissions: [] },
    ])
  })
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  show(<RolesPage />)

  expect(await screen.findByRole('button', { name: /内置角色不可删除/ })).toBeDisabled()
  await user.click(screen.getByRole('button', { name: '删除角色自定义角色' }))
  await user.click(screen.getByRole('button', { name: /^删\s*除$/ }))

  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/admin/roles/100', expect.objectContaining({ method: 'DELETE' })))
})

it('从设置表单保存平台配置', async () => {
  const original = { platformName: '智鹿交付', environmentLabel: '内部生产环境', timezone: 'Asia/Shanghai', supportEmail: '', agentTimeoutMinutes: 30 }
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    if (init?.method === 'PUT') return json({ ...original, platformName: '智鹿中台' })
    return json(original)
  })
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  show(<SettingsPage />)

  const name = await screen.findByLabelText('平台名称')
  await user.clear(name)
  await user.type(name, '智鹿中台')
  await user.click(screen.getByRole('button', { name: /保存设置/ }))

  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/admin/settings', expect.objectContaining({
    method: 'PUT', body: expect.stringContaining('"智鹿中台"'),
  })))
})

it('审计页的关键字筛选和翻页会更新查询', async () => {
  const fetch = vi.fn((input: RequestInfo | URL) => {
    const url = String(input)
    if (url === '/api/v1/runtime-settings') return json({ platformName: '智鹿交付', environmentLabel: '验收', timezone: 'UTC' })
    const page = new URL(url, 'http://local').searchParams.get('page') ?? '1'
    return json({ items: [], page: Number(page), pageSize: 20, total: 25 })
  })
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  show(<AuditLogsPage />)

  await user.type(await screen.findByPlaceholderText('操作者、资源、Trace ID'), 'TRACE-400')
  await waitFor(() => expect(fetch.mock.calls.map(([url]) => String(url)))
    .toContainEqual(expect.stringContaining('keyword=TRACE-400')))

  await user.click(await screen.findByTitle('2'))
  await waitFor(() => expect(fetch.mock.calls.map(([url]) => String(url)))
    .toContainEqual(expect.stringContaining('page=2')))
})
