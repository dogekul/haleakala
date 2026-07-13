import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { StandardizationPage } from './StandardizationPage'

it('同时提供六个标准化视图和可核对的数字口径', async () => {
  vi.stubGlobal('fetch', vi.fn((input: string) => {
    let body: unknown = []
    if (input.endsWith('/api/v1/products')) body = [{ id: 1, code: 'ERP', name: '企业财务', status: 'ACTIVE' }]
    else if (input.includes('/versions')) body = [{ id: 11, productId: 1, versionName: 'V5.0', status: 'ACTIVE' }]
    else if (input.includes('/baselines')) body = [{ id: 1, productVersionId: 11, capabilityCode: 'AR-001', capabilityName: '应收对账', dimension: 'FUNCTION', scopeDescription: '标准应收对账与差异识别', status: 'ACTIVE', version: 0 }]
    else if (input.includes('/assessments')) body = { period: '2026-07', standardCoverage: 72, reuseRate: 64, documentationScore: 80, extensionReadiness: 56, deliveryStability: 90, maturityScore: 71 }
    else if (input.includes('/deviations')) body = [{ projectId: 1, projectCode: 'P-001', projectName: '银行项目', total: 20, l0: 12, l1: 6, l2: 2, deviationRate: 40 }]
    else if (input.includes('/debts')) body = [{ id: 1, patternKey: 'reconciliation.retry', title: '对账重跑', occurrenceCount: 8, distinctProjects: 5, status: 'CANDIDATE' }]
    else if (input.includes('/costs')) body = { estimatedPersonDays: 80, actualPersonDays: 92, estimatedCost: 160000, actualCost: 184000, byExtensionPoint: [] }
    else if (input.includes('/flywheel')) body = { period: '2026-07', confirmedRequirements: 50, l0Count: 36, l1Count: 12, reuseRate: 64, debtClosedCount: 3, customCost: 184000, standardCoverage: 72 }
    return Promise.resolve({ ok: true, json: async () => body })
  }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><MemoryRouter><StandardizationPage /></MemoryRouter></QueryClientProvider>)

  await waitFor(() => expect(screen.getByText('应收对账')).toBeVisible())
  for (const tab of ['能力基线', '成熟度', '偏离度', '标准化债务', '成本归集', '产品飞轮']) expect(screen.getByText(tab)).toBeVisible()
  await userEvent.click(screen.getByText('成熟度'))
  expect(await screen.findByText('71')).toBeVisible()
  expect(screen.getByRole('table')).toBeVisible()
  vi.unstubAllGlobals()
})
