import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import { TaskReminderBell } from './TaskReminderBell'

afterEach(() => vi.unstubAllGlobals())

it('展示未读任务提醒并可标记已读', async () => {
  let markedRead = false
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    if (url.endsWith('/api/v1/task-reminders/unread')) {
      return Promise.resolve(new Response(JSON.stringify([{
        id: 5,
        taskId: 88,
        projectId: 12,
        projectName: '消保合规项目',
        taskTitle: '准备项目周报',
        dueAt: '2026-07-24T18:00:00',
        remindAt: '2026-07-24T17:30:00',
      }]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    }
    if (url.endsWith('/api/v1/task-reminders/5/read') && init?.method === 'POST') {
      markedRead = true
      return Promise.resolve(new Response(null, { status: 204 }))
    }
    return Promise.reject(new Error(`unexpected request ${url}`))
  }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const user = userEvent.setup()

  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <TaskReminderBell />
      </MemoryRouter>
    </QueryClientProvider>,
  )

  await user.click(await screen.findByRole('button', { name: '任务提醒，1条未读' }))
  expect(await screen.findByText('准备项目周报')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: '查看任务' })).toHaveAttribute(
    'href',
    '/projects/12?tab=tasks&taskId=88',
  )
  await user.click(screen.getByRole('button', { name: /标记已读/ }))
  await waitFor(() => expect(markedRead).toBe(true))
})
