import {
  BookOutlined,
  AuditOutlined,
  DashboardOutlined,
  FolderOpenOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  ProductOutlined,
  SettingOutlined,
  TeamOutlined,
  ToolOutlined,
} from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Avatar, Button, Dropdown, Tooltip } from 'antd'
import { useMemo, type ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../app/AuthProvider'
import { adminApi } from '../modules/admin/adminApi'

interface ModuleNav {
  path: string
  label: string
  permission: string
  icon: ReactNode
}

const modules: ModuleNav[] = [
  { path: '/dashboard', label: '驾驶舱', permission: 'dashboard:read', icon: <DashboardOutlined /> },
  { path: '/projects', label: '项目空间', permission: 'project:read', icon: <FolderOpenOutlined /> },
  { path: '/requirements', label: '需求工坊', permission: 'requirement:read', icon: <ToolOutlined /> },
  { path: '/standardization', label: '标准化中心', permission: 'standardization:read', icon: <ProductOutlined /> },
  { path: '/knowledge', label: '知识库', permission: 'knowledge:read', icon: <BookOutlined /> },
  { path: '/resources', label: '资源中心', permission: 'resource:read', icon: <TeamOutlined /> },
  { path: '/audit-logs', label: '审计日志', permission: 'audit:read', icon: <AuditOutlined /> },
  { path: '/admin', label: '系统管理', permission: 'system:manage', icon: <SettingOutlined /> },
]

export function AppShell({ children }: { children?: ReactNode }) {
  const { me, logout } = useAuth()
  const location = useLocation()
  const settings = useQuery({ queryKey: ['runtime-settings'], queryFn: adminApi.runtimeSettings, enabled: Boolean(me) })
  const visible = useMemo(() => modules.filter(item => me?.permissions.includes(item.permission)
    && !(item.path === '/audit-logs' && me.permissions.includes('system:manage'))), [me])
  const active = visible.find(item => location.pathname.startsWith(item.path)) ?? visible[0]
  const platformName = settings.data?.platformName ?? '智鹿交付'
  const environmentLabel = settings.data?.environmentLabel ?? '内部生产环境'

  return (
    <div className="app-shell">
      <aside className="module-rail" aria-label="主模块导航">
        <Tooltip title={platformName} placement="right">
          <Link to="/dashboard" className="brand-mark" aria-label={`${platformName}首页`}>
            <span className="brand-deer">鹿</span>
          </Link>
        </Tooltip>
        <nav className="rail-nav">
          {visible.map(item => (
            <Tooltip key={item.path} title={item.label} placement="right">
              <Link className={`rail-item ${active?.path === item.path ? 'active' : ''}`} to={item.path}>
                <span className="rail-icon">{item.icon}</span>
                <span>{item.label}</span>
              </Link>
            </Tooltip>
          ))}
        </nav>
        <Button className="rail-collapse" type="text" icon={<MenuFoldOutlined />} aria-label="收起导航" />
      </aside>

      <div className="workspace">
        <header className="topbar">
          <div>
            <strong>{active?.label ?? '工作台'}</strong>
          </div>
          <div className="topbar-actions">
            <span className="env-pill">{environmentLabel}</span>
            <Dropdown menu={{ items: [
              { key: 'profile', label: me?.username, disabled: true },
              { type: 'divider' },
              { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: () => void logout() },
            ] }}>
              <button className="user-button" type="button">
                <Avatar size={30}>{me?.displayName.slice(0, 1)}</Avatar>
                <span>{me?.displayName}</span>
              </button>
            </Dropdown>
          </div>
        </header>
        <main className="page-content">{children}</main>
      </div>
    </div>
  )
}
