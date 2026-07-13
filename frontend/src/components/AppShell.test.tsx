import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
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
    permissions: ['project:read', 'requirement:read'],
  },
  login: async () => undefined,
  logout: async () => undefined,
  refresh: async () => undefined,
}

it('只显示当前用户有权访问的模块入口', () => {
  render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={['/projects']}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <AppShell><div>项目内容</div></AppShell>
      </MemoryRouter>
    </AuthContext.Provider>,
  )

  expect(screen.getByRole('link', { name: /项目空间/ })).toBeVisible()
  expect(screen.getByRole('link', { name: /需求工坊/ })).toBeVisible()
  expect(screen.queryByRole('link', { name: /资源中心/ })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /项目列表/ })).not.toBeInTheDocument()
  expect(document.querySelector('.section-sidebar')).not.toBeInTheDocument()
  expect(screen.getByText('项目内容')).toBeVisible()
})
