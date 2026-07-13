import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { RequirementWorkshop } from './RequirementWorkshop'

it('展示仅由人工确认决策驱动的三层漏斗并保留看板', async () => {
  vi.stubGlobal('fetch', vi.fn((input: string) => {
    const body = input.includes('/funnel') ? { L0: 8, L1: 5, L2: 2 }
      : input.includes('/api/v1/requirements') ? [{ id: 1, projectId: 1, projectCode: 'PRJ-001', projectName: '银行交付', code: 'REQ-001', title: '对账差异自动定位', description: '自动定位批次对账差异', priority: 'P1', status: 'CONFIRMED', confirmedLevel: 'L1', suggestedLevel: 'L0', confidence: 0.82 }]
        : []
    return Promise.resolve({ ok: true, json: async () => body })
  }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}><RequirementWorkshop /></MemoryRouter></QueryClientProvider>)

  await waitFor(() => expect(screen.getByText('对账差异自动定位')).toBeVisible())
  expect(screen.getByText('标品满足 L0')).toBeVisible()
  expect(screen.getByText('8 条')).toBeVisible()
  await userEvent.click(screen.getByText('看板'))
  expect(screen.getByTestId('requirement-board')).toBeVisible()
  vi.unstubAllGlobals()
})
