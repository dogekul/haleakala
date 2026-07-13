import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { expect, it } from 'vitest'
import { AuthContext, type AuthState } from './AuthProvider'
import { LoginPage } from './LoginPage'

it('仅有审计权限的用户登录后进入审计日志', async () => {
  const auth: AuthState = {
    loading: false,
    me: { id: 9, organizationId: 1, username: 'auditor', displayName: '审计员', roles: ['VIEWER'], permissions: ['audit:read'] },
    login: async () => undefined,
    logout: async () => undefined,
    refresh: async () => undefined,
  }
  render(<AuthContext.Provider value={auth}>
    <MemoryRouter initialEntries={[{ pathname: '/login', state: { from: '/dashboard' } }]}
      future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/audit-logs" element={<div>审计工作台</div>} />
      </Routes>
    </MemoryRouter>
  </AuthContext.Provider>)

  expect(await screen.findByText('审计工作台')).toBeVisible()
})
