import {
  AppstoreOutlined,
  BookOutlined,
  ControlOutlined,
  DashboardOutlined,
  FolderOpenOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  ProductOutlined,
  SettingOutlined,
  TeamOutlined,
  ToolOutlined,
} from '@ant-design/icons'
import { Avatar, Button, Dropdown, Tooltip } from 'antd'
import { useMemo, type ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../app/AuthProvider'

interface ModuleNav {
  path: string
  label: string
  short: string
  permission: string
  icon: ReactNode
  menus: string[]
}

const modules: ModuleNav[] = [
  { path: '/dashboard', label: '驾驶舱', short: 'M1', permission: 'dashboard:read', icon: <DashboardOutlined />,
    menus: ['项目卡片墙', '风险热力图', '全局矩阵视图', '快速创建项目'] },
  { path: '/projects', label: '项目空间', short: 'M2', permission: 'project:read', icon: <FolderOpenOutlined />,
    menus: ['项目列表', '七阶段看板', 'Skill 执行', '模板中心', '风险登记册', '里程碑时间线', '项目设置'] },
  { path: '/requirements', label: '需求工坊', short: 'M3', permission: 'requirement:read', icon: <ToolOutlined />,
    menus: ['需求采集', 'AI 分类决策', '三层漏斗', '去重与合并', '需求列表与看板'] },
  { path: '/standardization', label: '标准化中心', short: 'M4', permission: 'standardization:read', icon: <ProductOutlined />,
    menus: ['标品能力卡', '成熟度评估', '偏离度分析', '标准化债务', '二开成本归因', '飞轮仪表盘'] },
  { path: '/knowledge', label: '知识库', short: 'M5', permission: 'knowledge:read', icon: <BookOutlined />,
    menus: ['范例检索', '代码片段库', '培训材料'] },
  { path: '/resources', label: '资源中心', short: 'M6', permission: 'resource:read', icon: <TeamOutlined />,
    menus: ['团队人员墙', '项目人力配置', '资源冲突检测', '团队负载看板'] },
  { path: '/admin', label: '系统管理', short: 'SYS', permission: 'system:manage', icon: <SettingOutlined />,
    menus: ['用户与团队', '角色权限', '产品与版本', '审计日志', '系统配置'] },
]

export function AppShell({ children }: { children?: ReactNode }) {
  const { me, logout } = useAuth()
  const location = useLocation()
  const visible = useMemo(() => modules.filter(item => me?.permissions.includes(item.permission)), [me])
  const active = visible.find(item => location.pathname.startsWith(item.path)) ?? visible[0]

  return (
    <div className="app-shell">
      <aside className="module-rail" aria-label="主模块导航">
        <Link to="/dashboard" className="brand-mark" aria-label="智鹿交付首页">
          <span className="brand-deer">鹿</span>
        </Link>
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

      <aside className="section-sidebar">
        <div className="section-brand">
          <div>
            <strong>智鹿交付</strong>
            <span>项目管理平台</span>
          </div>
          <ControlOutlined />
        </div>
        <div className="section-title">
          <span>{active?.short}</span>
          <strong>{active?.label ?? '工作台'}</strong>
        </div>
        <nav className="section-nav">
          {active?.menus.map((menu, index) => (
            <button key={menu} className={index === 0 ? 'active' : ''} type="button">
              <AppstoreOutlined />{menu}
            </button>
          ))}
        </nav>
        <div className="section-footer">
          <span>交付范式 v2.0</span>
          <small>6 模块 · 28 个功能</small>
        </div>
      </aside>

      <div className="workspace">
        <header className="topbar">
          <div>
            <strong>{active?.label ?? '工作台'}</strong>
            <span className="topbar-path">/ {active?.menus[0]}</span>
          </div>
          <div className="topbar-actions">
            <span className="env-pill">内部生产环境</span>
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
