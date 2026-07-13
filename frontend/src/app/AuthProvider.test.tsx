import { render, screen, waitFor } from '@testing-library/react'
import { AuthProvider, useAuth } from './AuthProvider'

function Viewer() {
  const { me, loading } = useAuth()
  return <div>{loading ? '加载中' : me?.displayName ?? '未登录'}</div>
}

it('启动时读取当前会话用户', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
    ok: true,
    json: async () => ({
      id: 1,
      organizationId: 1,
      username: 'admin',
      displayName: '系统管理员',
      roles: ['ADMIN'],
      permissions: ['system:manage'],
    }),
  }))

  render(<AuthProvider><Viewer /></AuthProvider>)

  await waitFor(() => expect(screen.getByText('系统管理员')).toBeVisible())
  expect(fetch).toHaveBeenCalledWith('/api/v1/auth/me', expect.objectContaining({ credentials: 'include' }))
  vi.unstubAllGlobals()
})
