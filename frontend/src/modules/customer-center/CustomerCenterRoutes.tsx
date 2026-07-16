import { Navigate, Route, Routes } from 'react-router-dom'
import { RequirePermission } from '../../app/AccessPages'
import { CustomerPage } from '../customer/CustomerPage'
import { CustomerCenterTabs } from './CustomerCenterTabs'

function PendingPage({ title }: { title: string }) {
  return <div className="crm-page"><div className="page-heading compact"><div><h2>{title}</h2>
    <p>客户全生命周期工作区</p></div></div></div>
}

function Crm({ children }: { children: React.ReactNode }) {
  return <RequirePermission code="crm:read">{children}</RequirePermission>
}

export function CustomerCenterRoutes() {
  return <div className="customer-center">
    <CustomerCenterTabs />
    <Routes>
      <Route index element={<RequirePermission code="customer:read"><CustomerPage /></RequirePermission>} />
      <Route path="opportunities" element={<Crm><PendingPage title="商机总览" /></Crm>} />
      <Route path="opportunities/:id" element={<Crm><PendingPage title="商机详情" /></Crm>} />
      <Route path="presale" element={<Crm><PendingPage title="售前推进" /></Crm>} />
      <Route path="implementation" element={<Crm><PendingPage title="实施协同" /></Crm>} />
      <Route path="cockpit" element={<Crm><PendingPage title="实施驾驶舱" /></Crm>} />
      <Route path="operations" element={<Crm><PendingPage title="客户运营" /></Crm>} />
      <Route path="operations/:id" element={<Crm><PendingPage title="运营详情" /></Crm>} />
      <Route path="*" element={<Navigate to="/customers" replace />} />
    </Routes>
  </div>
}
