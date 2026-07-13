import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AgentExecutionPanel } from './AgentExecutionPanel'

it('展示六个交付 Skill 并提交任务', async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce({ ok: true, json: async () => [] })
    .mockResolvedValueOnce({ ok: true, json: async () => ({ id: 9, projectId: 1, skillCode: 'deliver-init', status: 'RUNNING', progress: 10 }) })
    .mockResolvedValueOnce({ ok: true, json: async () => [{ id: 9, projectId: 1, skillCode: 'deliver-init', status: 'RUNNING', progress: 10 }] })
  vi.stubGlobal('fetch', fetchMock)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><AgentExecutionPanel projectId={1} /></QueryClientProvider>)

  await waitFor(() => expect(screen.getByText('项目初始化')).toBeVisible())
  expect(screen.getAllByText(/deliver-/)).toHaveLength(6)
  await userEvent.click(screen.getByRole('button', { name: '执行项目初始化' }))
  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/v1/projects/1/agent-jobs', expect.objectContaining({ method: 'POST' })))
  vi.unstubAllGlobals()
})
