import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
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
