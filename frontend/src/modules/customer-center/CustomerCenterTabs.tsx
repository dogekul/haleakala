import { NavLink } from 'react-router-dom'
import { useAuth } from '../../app/AuthProvider'

const tabs = [
  { path: '/customers', label: '客户管理', permission: 'customer:read', end: true },
  { path: '/customers/opportunities', label: '商机总览', permission: 'crm:read' },
  { path: '/customers/presale', label: '售前推进', permission: 'crm:read' },
  { path: '/customers/implementation', label: '实施协同', permission: 'crm:read' },
  { path: '/customers/implementation-cockpit', label: '实施驾驶舱', permission: 'crm:read' },
  { path: '/customers/operations', label: '客户运营', permission: 'crm:read' },
]

export function CustomerCenterTabs() {
  const { me } = useAuth()
  return <nav className="customer-center-tabs" aria-label="客户中心导航">
    {tabs.filter(tab => me?.permissions.includes(tab.permission)).map(tab =>
      <NavLink key={tab.path} to={tab.path} end={tab.end}
        className={({ isActive }) => isActive ? 'active' : undefined}>{tab.label}</NavLink>)}
  </nav>
}
