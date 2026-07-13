import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { ResourcePage } from './ResourcePage'

const assignment = { id: 1, userId: 2, displayName: '王工程师', projectId: 1, projectCode: 'P-001', projectName: '银行交付', role: '实施顾问', startDate: '2026-07-01', endDate: '2026-07-31', allocationPercent: 70, status: 'ACTIVE', version: 0 }
const team = [{ userId: 2, username: 'wang', displayName: '王工程师', jobTitle: '高级实施顾问', location: '上海', weeklyCapacityHours: 40, resourceStatus: 'ACTIVE', skills: [{ id: 1, code: 'DB-MYSQL', name: 'MySQL', category: 'TECHNICAL', proficiency: 4, certified: true, experienceMonths: 36 }] }]
const conflict = { userId: 2, displayName: '王工程师', startDate: '2026-07-10', endDate: '2026-07-20', peakAllocationPercent: 130, assignments: [assignment, { ...assignment, id: 2, projectId: 2, projectCode: 'P-002', projectName: '保险交付', allocationPercent: 60 }] }

function renderResourcePage(failUpdate = false) {
  const fetchMock = vi.fn((input: string, init?: RequestInit) => {
    let body: unknown = []
    if (input.includes('/assignments/1') && init?.method === 'PUT') {
      if (failUpdate) return Promise.resolve({ ok: false, status: 409, json: async () => ({ code: 'CONFLICT', message: '分配冲突，请刷新' }) })
      body = { ...assignment, allocationPercent: 80, version: 1 }
    } else if (input.includes('/team')) body = team
    else if (input.includes('/assignments')) body = [assignment]
    else if (input.includes('/load')) body = [{ userId: 2, displayName: '王工程师', jobTitle: '高级实施顾问', weeklyCapacityHours: 40, allocationPercent: 130, availablePercent: 0, loadStatus: 'OVERLOAD' }]
    else if (input.includes('/conflicts')) body = [conflict]
    else if (input.includes('/api/v1/projects')) body = [{ id: 1, code: 'P-001', name: '银行交付' }, { id: 2, code: 'P-002', name: '保险交付' }]
    return Promise.resolve({ ok: true, status: 200, json: async () => body })
  })
  vi.stubGlobal('fetch', fetchMock)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={client}><MemoryRouter><ResourcePage /></MemoryRouter></QueryClientProvider>)
  return fetchMock
}

afterEach(() => vi.unstubAllGlobals())

it('同时展示人员能力、排期、负载和冲突', async () => {
  renderResourcePage()
  await waitFor(() => expect(screen.getByText('王工程师')).toBeVisible())
  for (const tab of ['团队能力', '项目排期', '负载分析', '冲突预警']) expect(screen.getByText(tab)).toBeVisible()
  await userEvent.click(screen.getByText('负载分析'))
  expect(await screen.findByText('130%')).toBeVisible()
})

it('从冲突预警打开并提交资源分配调整，然后刷新所有资源视图', async () => {
  const user = userEvent.setup()
  const fetchMock = renderResourcePage()
  await user.click(await screen.findByText('冲突预警'))

  await user.click(await screen.findByRole('button', { name: '调整资源分配' }))

  expect(await screen.findByRole('dialog')).toHaveTextContent('调整资源分配')
  expect(screen.getByDisplayValue('实施顾问')).toBeVisible()
  expect(screen.getByText('王工程师 · 高级实施顾问')).toBeVisible()
  expect(screen.getByText('P-001 · 银行交付')).toBeVisible()
  const allocation = screen.getByRole('spinbutton')
  await user.clear(allocation)
  await user.type(allocation, '80')
  await user.click(screen.getByRole('button', { name: '保存分配' }))

  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/v1/resources/assignments/1', expect.objectContaining({ method: 'PUT' })))
  const putCall = fetchMock.mock.calls.find((call) => call[0] === '/api/v1/resources/assignments/1')!
  expect(JSON.parse(String(putCall[1]?.body))).toMatchObject({ userId: 2, projectId: 1, role: '实施顾问', startDate: '2026-07-01', endDate: '2026-07-31', allocationPercent: 80, status: 'ACTIVE', version: 0 })
  const putIndex = fetchMock.mock.calls.indexOf(putCall)
  await waitFor(() => {
    const refreshed = fetchMock.mock.calls.slice(putIndex + 1).map(call => String(call[0]))
    for (const path of ['/team', '/assignments', '/load', '/conflicts']) expect(refreshed.some(url => url.includes(path))).toBe(true)
  })
})

it('资源分配调整失败时展示后端错误', async () => {
  const user = userEvent.setup()
  renderResourcePage(true)
  await user.click(await screen.findByText('冲突预警'))
  await user.click(await screen.findByRole('button', { name: '调整资源分配' }))
  await user.click(await screen.findByRole('button', { name: '保存分配' }))
  expect(await screen.findByText('分配冲突，请刷新')).toBeVisible()
})
