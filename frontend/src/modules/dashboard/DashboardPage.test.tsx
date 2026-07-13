import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { DashboardPage } from './DashboardPage'

it('默认展示高密度项目列表并保留卡片视图', async () => {
  window.localStorage.clear()
  vi.stubGlobal('fetch', vi.fn((input: string) => {
    const body = input.includes('/summary') ? { activeProjects: 3, totalProjects: 3, redProjects: 1, yellowProjects: 1, healthScore: 68, openRisks: 4, overdueMilestones: 1, stageDistribution: {}, productDistribution: {} }
      : input.includes('/projects') ? [{ id: 1, code: 'PRJ-001', name: '华东银行核心系统交付', customerName: '华东银行', productName: '智鹿 ERP', productVersionName: 'V5', managerName: '张宁', status: 'ACTIVE', currentStage: 'REQUIREMENT', riskLevel: 'RED', progress: 17, openRiskCount: 2, overdueMilestoneCount: 1 }]
        : []
    return Promise.resolve({ ok: true, json: async () => body })
  }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}><DashboardPage /></MemoryRouter></QueryClientProvider>)

  await waitFor(() => expect(screen.getAllByRole('table')[0]).toBeVisible())
  expect(screen.getByText('项目健康度')).toBeVisible()
  expect(screen.getByText('华东银行核心系统交付')).toBeVisible()
  await userEvent.click(screen.getByText('卡片'))
  expect(screen.getByTestId('dashboard-project-card-1')).toBeVisible()
  expect(window.localStorage.getItem('dashboard-project-view')).toBe('card')
  vi.unstubAllGlobals()
})
