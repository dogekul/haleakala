import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AuthProvider, useAuth } from './AuthProvider'

function Viewer() {
  const { me, loading } = useAuth()
  return <div>{loading ? '加载中' : me?.displayName ?? '未登录'}</div>
}

function LogoutViewer() {
  const { me, loading, logout } = useAuth()
  return <div>
    <span>{loading ? '加载中' : me?.displayName ?? '未登录'}</span>
    {me && <button type="button" onClick={() => void logout()}>退出登录</button>}
  </div>
}

it('启动时读取当前会话用户', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      id: 1,
      organizationId: 1,
      username: 'admin',
      displayName: '系统管理员',
      roles: ['ADMIN'],
      permissions: ['system:manage'],
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })))

  render(<AuthProvider><Viewer /></AuthProvider>)

  await waitFor(() => expect(screen.getByText('系统管理员')).toBeVisible())
  expect(fetch).toHaveBeenCalledWith('/api/v1/auth/me', expect.objectContaining({ credentials: 'include' }))
  vi.unstubAllGlobals()
})

it('退出接口返回 204 空响应后清除当前用户', async () => {
  const fetch = vi.fn()
    .mockResolvedValueOnce(new Response(JSON.stringify({
      id: 1,
      organizationId: 1,
      username: 'admin',
      displayName: '系统管理员',
      roles: ['ADMIN'],
      permissions: ['system:manage'],
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    .mockResolvedValueOnce(new Response(null, { status: 204 }))
  vi.stubGlobal('fetch', fetch)

  render(<AuthProvider><LogoutViewer /></AuthProvider>)

  const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: '退出登录' }))
  expect(await screen.findByText('未登录')).toBeVisible()
  expect(fetch).toHaveBeenLastCalledWith('/api/v1/auth/logout', expect.objectContaining({ method: 'POST' }))
  vi.unstubAllGlobals()
})
