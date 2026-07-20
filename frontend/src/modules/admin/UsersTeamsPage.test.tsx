import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { UsersTeamsPage } from './UsersTeamsPage'

afterEach(() => vi.unstubAllGlobals())

it('关闭编辑后新建用户不保留旧表单值', async () => {
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
    const url = String(input)
    const value = url.endsWith('/users') ? [{
      id: 7, organizationId: 1, primaryTeamId: null, primaryTeamName: null,
      username: 'wang', displayName: '旧名称', email: 'wang@example.com', status: 'ACTIVE', roles: ['ADMIN'],
    }] : url.endsWith('/roles') ? [{ id: 1, code: 'ADMIN', name: '系统管理员', description: '', builtIn: true, permissions: [] }] : []
    return Promise.resolve(new Response(JSON.stringify(value), { status: 200, headers: { 'Content-Type': 'application/json' } }))
  }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const user = userEvent.setup()
  render(<QueryClientProvider client={client}><UsersTeamsPage /></QueryClientProvider>)

  await user.click(await screen.findByRole('button', { name: '编辑' }))
  expect(screen.getByLabelText('显示名称')).toHaveValue('旧名称')
  await user.click(screen.getByRole('button', { name: /取\s*消/ }))
  await user.click(screen.getByRole('button', { name: /新建用户/ }))

  expect(screen.getByLabelText('显示名称')).toHaveValue('')
})

it('确认后删除用户和团队并刷新列表', async () => {
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    if (init?.method === 'DELETE') return Promise.resolve(new Response(null, { status: 204 }))
    const value = url.endsWith('/users') ? [{
      id: 7, organizationId: 1, primaryTeamId: 8, primaryTeamName: '测试团队',
      username: 'tester', displayName: '测试用户', email: '', status: 'ACTIVE', roles: ['VIEWER'],
    }] : url.endsWith('/teams') ? [{
      id: 8, organizationId: 1, parentId: null, name: '测试团队', code: 'TEST', enabled: true,
    }] : url.endsWith('/roles') ? [{
      id: 9, code: 'VIEWER', name: '观察员', description: '', builtIn: false, permissions: [],
    }] : []
    return Promise.resolve(new Response(JSON.stringify(value), {
      status: 200, headers: { 'Content-Type': 'application/json' },
    }))
  })
  vi.stubGlobal('fetch', fetch)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const user = userEvent.setup()
  render(<QueryClientProvider client={client}><UsersTeamsPage /></QueryClientProvider>)

  await user.click(await screen.findByRole('button', { name: '删除用户测试用户' }))
  await user.click(screen.getByRole('button', { name: /^删\s*除$/ }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/admin/users/7', expect.objectContaining({ method: 'DELETE' })))

  await user.click(await screen.findByRole('button', { name: '删除团队测试团队' }))
  await user.click(screen.getByRole('button', { name: /^删\s*除$/ }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/admin/teams/8', expect.objectContaining({ method: 'DELETE' })))
  expect(fetch.mock.calls.filter(([url]) => String(url).endsWith('/users')).length).toBeGreaterThan(1)
  expect(fetch.mock.calls.filter(([url]) => String(url).endsWith('/teams')).length).toBeGreaterThan(1)
})
