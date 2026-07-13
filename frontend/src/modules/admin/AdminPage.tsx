import { AuditOutlined, LockOutlined, ProductOutlined, SettingOutlined, TeamOutlined } from '@ant-design/icons'
import { Navigate, NavLink, Route, Routes } from 'react-router-dom'
import { AuditLogsPage } from './AuditLogsPage'
import { ProductsPage } from './ProductsPage'
import { RolesPage } from './RolesPage'
import { SettingsPage } from './SettingsPage'
import { UsersTeamsPage } from './UsersTeamsPage'

const sections = [
  { path: 'users', label: '用户与团队', icon: <TeamOutlined /> },
  { path: 'roles', label: '角色权限', icon: <LockOutlined /> },
  { path: 'products', label: '产品目录', icon: <ProductOutlined /> },
  { path: 'audit-logs', label: '审计日志', icon: <AuditOutlined /> },
  { path: 'settings', label: '系统设置', icon: <SettingOutlined /> },
]

export function AdminPage() {
  return <div className="admin-page">
    <nav className="admin-nav" aria-label="系统管理导航">{sections.map(item => <NavLink key={item.path} to={`/admin/${item.path}`} aria-label={item.label} className={({ isActive }) => isActive ? 'active' : ''}>{item.icon}<span>{item.label}</span></NavLink>)}</nav>
    <Routes>
      <Route index element={<Navigate to="users" replace />} />
      <Route path="users" element={<UsersTeamsPage />} />
      <Route path="roles" element={<RolesPage />} />
      <Route path="products" element={<ProductsPage />} />
      <Route path="audit-logs" element={<AuditLogsPage />} />
      <Route path="settings" element={<SettingsPage />} />
      <Route path="*" element={<Navigate to="users" replace />} />
    </Routes>
  </div>
}
