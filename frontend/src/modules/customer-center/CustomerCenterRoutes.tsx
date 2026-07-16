import { Navigate, Route, Routes } from 'react-router-dom'
import { RequirePermission } from '../../app/AccessPages'
import { CustomerPage } from '../customer/CustomerPage'
import { CustomerCenterTabs } from './CustomerCenterTabs'
import { OpportunityDetailPage } from './OpportunityDetailPage'
import { OpportunityOverviewPage } from './OpportunityOverviewPage'
import { PresaleBoardPage } from './PresaleBoardPage'
import { ImplementationCockpitPage } from './ImplementationCockpitPage'
import { ImplementationPage } from './ImplementationPage'
import { OperationBoardPage } from './OperationBoardPage'
import { OperationDetailPage } from './OperationDetailPage'
import { useAuth } from '../../app/AuthProvider'

function Crm({ children }: { children: React.ReactNode }) {
  return <RequirePermission code="crm:read">{children}</RequirePermission>
}

function CustomerCenterIndex() {
  const { me } = useAuth()
  return me?.permissions.includes('customer:read')
    ? <CustomerPage />
    : <Navigate to="/customers/opportunities" replace />
}

export function CustomerCenterRoutes() {
  return <div className="customer-center">
    <CustomerCenterTabs />
    <Routes>
      <Route index element={<CustomerCenterIndex />} />
      <Route path="opportunities" element={<Crm><OpportunityOverviewPage /></Crm>} />
      <Route path="opportunities/:id" element={<Crm><OpportunityDetailPage /></Crm>} />
      <Route path="presale" element={<Crm><PresaleBoardPage /></Crm>} />
      <Route path="implementation" element={<Crm><ImplementationPage /></Crm>} />
      <Route path="implementation-cockpit" element={<Crm><ImplementationCockpitPage /></Crm>} />
      <Route path="cockpit" element={<Navigate to="/customers/implementation-cockpit" replace />} />
      <Route path="operations" element={<Crm><OperationBoardPage /></Crm>} />
      <Route path="operations/:id" element={<Crm><OperationDetailPage /></Crm>} />
      <Route path="*" element={<Navigate to="/customers" replace />} />
    </Routes>
  </div>
}
