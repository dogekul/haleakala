import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useNavigate } from 'react-router-dom'
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

it('requirementId 每次只自动定位一次且参数变化时定位新需求', async () => {
  let requirementGets = 0
  vi.stubGlobal('fetch', vi.fn((input: string) => {
    if (input.includes('/funnel')) return Promise.resolve({ ok: true, json: async () => ({ L0: 0, L1: 0, L2: 0 }) })
    if (input.includes('/api/v1/requirements')) {
      requirementGets += 1
      const suffix = requirementGets > 1 ? '（刷新）' : ''
      return Promise.resolve({ ok: true, json: async () => [
        { id: 1, projectId: 1, projectCode: 'PRJ-001', code: 'REQ-001', title: `第一条需求${suffix}`, description: '描述一', priority: 'P1', status: 'CONFIRMED' },
        { id: 2, projectId: 1, projectCode: 'PRJ-001', code: 'REQ-002', title: '第二条需求', description: '描述二', priority: 'P2', status: 'SUBMITTED' },
      ] })
    }
    return Promise.resolve({ ok: true, json: async () => [] })
  }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  function NavigateRequirement() {
    const navigate = useNavigate()
    return <button onClick={() => navigate('/requirements?requirementId=2')}>定位第二条需求</button>
  }
  render(<QueryClientProvider client={client}>
    <MemoryRouter initialEntries={['/requirements?requirementId=1']} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <NavigateRequirement />
      <RequirementWorkshop />
    </MemoryRouter>
  </QueryClientProvider>)

  expect(await screen.findByRole('dialog', { name: 'AI 分类决策树' })).toBeVisible()
  await userEvent.click(screen.getByRole('button', { name: 'Close' }))
  await waitFor(() => expect(screen.queryByRole('dialog', { name: 'AI 分类决策树' })).not.toBeInTheDocument())

  await client.invalidateQueries({ queryKey: ['requirements'] })
  expect(await screen.findByText('第一条需求（刷新）')).toBeVisible()
  expect(screen.queryByRole('dialog', { name: 'AI 分类决策树' })).not.toBeInTheDocument()

  await userEvent.click(screen.getByRole('button', { name: '定位第二条需求' }))
  expect(await screen.findByRole('dialog', { name: 'AI 分类决策树' })).toBeVisible()
  expect(screen.getByRole('heading', { name: '第二条需求' })).toBeVisible()
  vi.unstubAllGlobals()
})
