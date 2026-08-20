import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AgentExecutionPanel, type AgentJob } from './AgentExecutionPanel'

it('展示七阶段 Skill、文档进度并固定按正常场景提交', async () => {
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    if (path === '/api/v1/projects/1/documents') return Promise.resolve(new Response(JSON.stringify([
      { id: 1, stageCode: 'START', title: '项目立项登记表', status: 'COMPLETED', gateRequired: true },
      { id: 2, stageCode: 'START', title: '项目立项评审纪要', status: 'TODO', gateRequired: true },
    ]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    if (path === '/api/v1/projects/1/agent-jobs' && init?.method === 'POST') return Promise.resolve(new Response(JSON.stringify({
      id: 9, projectId: 1, skillCode: 'deliver-init', scenario: 'normal', status: 'RUNNING', progress: 10,
    }), { status: 201, headers: { 'Content-Type': 'application/json' } }))
    if (path === '/api/v1/projects/1/agent-jobs') return Promise.resolve(new Response('[]', { status: 200 }))
    throw new Error(`unexpected request: ${path}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><AgentExecutionPanel projectId={1} currentStage="START" /></QueryClientProvider>)

  await waitFor(() => expect(screen.getByText('项目立项')).toBeVisible())
  expect(screen.getAllByText(/deliver-/)).toHaveLength(7)
  expect(screen.getByText('开发与测试')).toBeVisible()
  expect(screen.queryByText('模拟失败')).not.toBeInTheDocument()
  expect(await screen.findByText('1 / 2')).toBeVisible()
  expect(screen.getByText('当前阶段')).toBeVisible()
  await userEvent.click(screen.getByRole('button', { name: '执行项目立项' }))
  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/v1/projects/1/agent-jobs', expect.objectContaining({ method: 'POST' })))
  const request = fetchMock.mock.calls.find(([path, init]) => String(path) === '/api/v1/projects/1/agent-jobs' && init?.method === 'POST')
  expect(JSON.parse(String(request?.[1]?.body))).toEqual({ skill: 'deliver-init', scenario: 'normal' })
  vi.unstubAllGlobals()
})

it('初始化中或没有可补全文档时禁止执行', async () => {
  const fetchMock = vi.fn((input: RequestInfo | URL) => {
    const path = String(input)
    if (path === '/api/v1/projects/1/documents') return Promise.resolve(new Response(JSON.stringify([
      { id: 1, stageCode: 'START', title: '项目立项登记表', status: 'PENDING_CONFIRMATION', gateRequired: true },
    ]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    if (path === '/api/v1/projects/1/agent-jobs') return Promise.resolve(new Response('[]', { status: 200 }))
    throw new Error(`unexpected request: ${path}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><AgentExecutionPanel projectId={1} currentStage="START" /></QueryClientProvider>)

  expect(await screen.findByRole('button', { name: '执行项目立项' })).toBeDisabled()
  await waitFor(() => expect(screen.getByRole('button', { name: '执行项目立项' })).toHaveTextContent('等待确认'))
  expect(screen.getByRole('button', { name: '执行开发与测试' })).toBeDisabled()
  expect(screen.getByRole('button', { name: '执行开发与测试' })).toHaveTextContent('暂无文档')
  vi.unstubAllGlobals()
})

it('Agent 从运行中变为成功后刷新项目文档', async () => {
  let jobLoads = 0
  let documentLoads = 0
  const fetchMock = vi.fn((input: RequestInfo | URL) => {
    const path = String(input)
    if (path === '/api/v1/projects/1/documents') {
      documentLoads += 1
      return Promise.resolve(new Response('[]', { status: 200 }))
    }
    if (path === '/api/v1/projects/1/agent-jobs') {
      jobLoads += 1
      const status = jobLoads === 1 ? 'RUNNING' : 'SUCCEEDED'
      return Promise.resolve(new Response(JSON.stringify([{ id: 9, projectId: 1, skillCode: 'deliver-init', scenario: 'normal', status, progress: status === 'RUNNING' ? 50 : 100 }]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    }
    throw new Error(`unexpected request: ${path}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const invalidate = vi.spyOn(client, 'invalidateQueries')
  render(<QueryClientProvider client={client}><AgentExecutionPanel projectId={1} currentStage="START" /></QueryClientProvider>)

  await waitFor(() => expect(client.getQueryData<AgentJob[]>(['agent-jobs', 1])?.[0]?.status).toBe('RUNNING'))
  await client.invalidateQueries({ queryKey: ['agent-jobs', 1] })
  await waitFor(() => expect(client.getQueryData<AgentJob[]>(['agent-jobs', 1])?.[0]?.status).toBe('SUCCEEDED'))
  await waitFor(() => expect(invalidate).toHaveBeenCalledWith({ queryKey: ['project-documents', 1] }))
  expect(documentLoads).toBeGreaterThanOrEqual(1)
  vi.unstubAllGlobals()
})
