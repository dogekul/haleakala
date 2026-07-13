import { Route, Routes } from 'react-router-dom'
import { AppShell } from '../components/AppShell'
import { ForbiddenPage, RequireAuth, RequirePermission } from './AccessPages'
import { LoginPage } from './LoginPage'
import { PlaceholderPage } from './PlaceholderPage'

const routes = [
  ['/dashboard', 'dashboard', 'dashboard:read'],
  ['/projects', 'projects', 'project:read'],
  ['/requirements', 'requirements', 'requirement:read'],
  ['/standardization', 'standardization', 'standardization:read'],
  ['/knowledge', 'knowledge', 'knowledge:read'],
  ['/resources', 'resources', 'resource:read'],
  ['/admin', 'admin', 'system:manage'],
] as const

export function App() {
  return <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/403" element={<ForbiddenPage />} />
    {routes.map(([path, module, permission]) => <Route key={path} path={`${path}/*`} element={
      <RequireAuth><RequirePermission code={permission}>
        <AppShell><PlaceholderPage module={module} /></AppShell>
      </RequirePermission></RequireAuth>
    } />)}
    <Route path="*" element={<NavigateHome />} />
  </Routes>
}

function NavigateHome() {
  return <RequireAuth><AppShell><PlaceholderPage module="dashboard" /></AppShell></RequireAuth>
}
