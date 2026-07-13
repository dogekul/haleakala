import { lazy, Suspense } from 'react'
import { Spin } from 'antd'
import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from '../components/AppShell'
import { ForbiddenPage, RequireAuth, RequirePermission } from './AccessPages'
import { LoginPage } from './LoginPage'
import { useAuth } from './AuthProvider'
import { homeRoute } from './homeRoute'

const ProjectDetail = lazy(() => import('../modules/project/ProjectDetail').then(module => ({ default: module.ProjectDetail })))
const ProjectWorkspace = lazy(() => import('../modules/project/ProjectWorkspace').then(module => ({ default: module.ProjectWorkspace })))
const DashboardPage = lazy(() => import('../modules/dashboard/DashboardPage').then(module => ({ default: module.DashboardPage })))
const RequirementWorkshop = lazy(() => import('../modules/requirement/RequirementWorkshop').then(module => ({ default: module.RequirementWorkshop })))
const StandardizationPage = lazy(() => import('../modules/standardization/StandardizationPage').then(module => ({ default: module.StandardizationPage })))
const KnowledgePage = lazy(() => import('../modules/knowledge/KnowledgePage').then(module => ({ default: module.KnowledgePage })))
const ResourcePage = lazy(() => import('../modules/resource/ResourcePage').then(module => ({ default: module.ResourcePage })))
const AdminPage = lazy(() => import('../modules/admin/AdminPage').then(module => ({ default: module.AdminPage })))
const AuditLogsPage = lazy(() => import('../modules/admin/AuditLogsPage').then(module => ({ default: module.AuditLogsPage })))

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
    <Route path="/standardization/*" element={<RequireAuth><RequirePermission code="standardization:read">
      <AppShell><LazyPage><StandardizationPage /></LazyPage></AppShell>
    </RequirePermission></RequireAuth>} />
    <Route path="/knowledge/*" element={<RequireAuth><RequirePermission code="knowledge:read">
      <AppShell><LazyPage><KnowledgePage /></LazyPage></AppShell>
    </RequirePermission></RequireAuth>} />
    <Route path="/resources/*" element={<RequireAuth><RequirePermission code="resource:read">
      <AppShell><LazyPage><ResourcePage /></LazyPage></AppShell>
    </RequirePermission></RequireAuth>} />
    <Route path="/projects" element={<RequireAuth><RequirePermission code="project:read">
      <AppShell><LazyPage><ProjectWorkspace /></LazyPage></AppShell>
    </RequirePermission></RequireAuth>} />
    <Route path="/projects/:id/*" element={<RequireAuth><RequirePermission code="project:read">
      <AppShell><LazyPage><ProjectDetail /></LazyPage></AppShell>
    </RequirePermission></RequireAuth>} />
    <Route path="/admin/*" element={<RequireAuth><RequirePermission code="system:manage">
      <AppShell><LazyPage><AdminPage /></LazyPage></AppShell>
    </RequirePermission></RequireAuth>} />
    <Route path="/audit-logs" element={<RequireAuth><RequirePermission code="audit:read">
      <AppShell><LazyPage><AuditLogsPage /></LazyPage></AppShell>
    </RequirePermission></RequireAuth>} />
    <Route path="*" element={<NavigateHome />} />
  </Routes>
}

function LazyPage({ children }: { children: React.ReactNode }) {
  return <Suspense fallback={<div className="page-loading"><Spin size="large" /></div>}>{children}</Suspense>
}

function NavigateHome() {
  const { me } = useAuth()
  return <RequireAuth><Navigate to={homeRoute(me?.permissions ?? [])} replace /></RequireAuth>
}
