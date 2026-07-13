import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { ProjectWorkspace } from './ProjectWorkspace'

const projects = [{
  id: 1,
  code: 'PRJ-001',
  name: '华东银行核心系统交付',
  customerName: '华东银行',
  productName: '智鹿 ERP',
  productVersionName: 'V5.2',
  managerName: '张宁',
  status: 'ACTIVE',
  currentStage: 'REQUIREMENT',
  riskLevel: 'YELLOW',
  startDate: '2026-07-01',
  plannedEndDate: '2026-12-31',
  stages: [], members: [], risks: [], milestones: [], templates: [], artifacts: [], activities: [],
}]

it('默认使用高密度列表并可切换为卡片视图', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => projects }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}>
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <ProjectWorkspace />
    </MemoryRouter>
  </QueryClientProvider>)

  await waitFor(() => expect(screen.getByRole('table')).toBeVisible())
  expect(screen.getByText('华东银行核心系统交付')).toBeVisible()
  await userEvent.click(screen.getByText('卡片视图'))
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
  expect(screen.getByTestId('project-card-1')).toBeVisible()
  vi.unstubAllGlobals()
})
