import { lazy, Suspense } from 'react'
import { Spin } from 'antd'
import { Route, Routes } from 'react-router-dom'
import { AppShell } from '../components/AppShell'
import { ForbiddenPage, RequireAuth, RequirePermission } from './AccessPages'
import { LoginPage } from './LoginPage'
import { PlaceholderPage } from './PlaceholderPage'

const ProjectDetail = lazy(() => import('../modules/project/ProjectDetail').then(module => ({ default: module.ProjectDetail })))
const ProjectWorkspace = lazy(() => import('../modules/project/ProjectWorkspace').then(module => ({ default: module.ProjectWorkspace })))
const DashboardPage = lazy(() => import('../modules/dashboard/DashboardPage').then(module => ({ default: module.DashboardPage })))
const RequirementWorkshop = lazy(() => import('../modules/requirement/RequirementWorkshop').then(module => ({ default: module.RequirementWorkshop })))

const routes = [
  ['/standardization', 'standardization', 'standardization:read'],
  ['/knowledge', 'knowledge', 'knowledge:read'],
  ['/resources', 'resources', 'resource:read'],
  ['/admin', 'admin', 'system:manage'],
] as const

export function App() {
  return <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/403" element={<ForbiddenPage />} />
    <Route path="/dashboard/*" element={<RequireAuth><RequirePermission code="dashboard:read">
      <AppShell><LazyPage><DashboardPage /></LazyPage></AppShell>
    </RequirePermission></RequireAuth>} />
    <Route path="/requirements/*" element={<RequireAuth><RequirePermission code="requirement:read">
      <AppShell><LazyPage><RequirementWorkshop /></LazyPage></AppShell>
    </RequirePermission></RequireAuth>} />
    <Route path="/projects" element={<RequireAuth><RequirePermission code="project:read">
      <AppShell><LazyPage><ProjectWorkspace /></LazyPage></AppShell>
    </RequirePermission></RequireAuth>} />
    <Route path="/projects/:id/*" element={<RequireAuth><RequirePermission code="project:read">
      <AppShell><LazyPage><ProjectDetail /></LazyPage></AppShell>
    </RequirePermission></RequireAuth>} />
    {routes.map(([path, module, permission]) => <Route key={path} path={`${path}/*`} element={
      <RequireAuth><RequirePermission code={permission}>
        <AppShell><PlaceholderPage module={module} /></AppShell>
      </RequirePermission></RequireAuth>
    } />)}
    <Route path="*" element={<NavigateHome />} />
  </Routes>
}

function LazyPage({ children }: { children: React.ReactNode }) {
  return <Suspense fallback={<div className="page-loading"><Spin size="large" /></div>}>{children}</Suspense>
}

function NavigateHome() {
  return <RequireAuth><AppShell><LazyPage><DashboardPage /></LazyPage></AppShell></RequireAuth>
}
