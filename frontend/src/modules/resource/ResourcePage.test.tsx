import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { ResourcePage } from './ResourcePage'

it('同时展示人员能力、排期、负载和冲突', async () => {
  vi.stubGlobal('fetch', vi.fn((input: string) => {
    let body: unknown = []
    if (input.includes('/team')) body = [{ userId: 2, username: 'wang', displayName: '王工程师', jobTitle: '高级实施顾问', location: '上海', weeklyCapacityHours: 40, resourceStatus: 'ACTIVE', skills: [{ id: 1, code: 'DB-MYSQL', name: 'MySQL', category: 'TECHNICAL', proficiency: 4, certified: true, experienceMonths: 36 }] }]
    else if (input.includes('/assignments')) body = [{ id: 1, userId: 2, displayName: '王工程师', projectId: 1, projectCode: 'P-001', projectName: '银行交付', role: '实施顾问', startDate: '2026-07-01', endDate: '2026-07-31', allocationPercent: 70, status: 'ACTIVE', version: 0 }]
    else if (input.includes('/load')) body = [{ userId: 2, displayName: '王工程师', jobTitle: '高级实施顾问', weeklyCapacityHours: 40, allocationPercent: 130, availablePercent: 0, loadStatus: 'OVERLOAD' }]
    else if (input.includes('/conflicts')) body = [{ user_id: 2, display_name: '王工程师', first_project_code: 'P-001', first_project_name: '银行交付', second_project_code: 'P-002', second_project_name: '保险交付', first_allocation: 70, second_allocation: 60, total_allocation: 130 }]
    else if (input.includes('/api/v1/projects')) body = []
    else if (input.includes('/skills')) body = []
    return Promise.resolve({ ok: true, json: async () => body })
  }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><MemoryRouter><ResourcePage /></MemoryRouter></QueryClientProvider>)
  await waitFor(() => expect(screen.getByText('王工程师')).toBeVisible())
  for (const tab of ['团队能力', '项目排期', '负载分析', '冲突预警']) expect(screen.getByText(tab)).toBeVisible()
  await userEvent.click(screen.getByText('负载分析'))
  expect(await screen.findByText('130%')).toBeVisible()
  vi.unstubAllGlobals()
})
