import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, vi } from 'vitest'
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom'
import { AuthContext, type AuthState } from '../../app/AuthProvider'
import { ProjectDetail } from './ProjectDetail'
import { ProjectTasks } from './ProjectTasks'
import type { Project } from './types'

const auth: AuthState = {
  loading: false,
  me: {
    id: 7,
    organizationId: 1,
    username: 'engineer',
    displayName: '交付工程师',
    roles: ['DELIVERY_ENGINEER'],
    permissions: ['project:read', 'project:write'],
  },
  login: async () => undefined,
  logout: async () => undefined,
  refresh: async () => undefined,
}

const project: Project = {
  id: 9,
  organizationId: 1,
  code: '9',
  name: '银行交付项目',
  customerId: 1,
  customerName: '示例银行',
  productId: 2,
  productName: '消保合规',
  productVersionId: 3,
  productVersionName: 'V1',
  managerUserId: 6,
  managerName: '项目经理',
  status: 'ACTIVE',
  currentStage: 'GO_LIVE',
  riskLevel: 'GREEN',
  version: 0,
  stages: [
    { id: 1, code: 'START', name: '启动', order: 1, status: 'COMPLETED', gateStatus: 'READY' },
    { id: 2, code: 'GO_LIVE', name: '上线切换', order: 4, status: 'ACTIVE', gateStatus: 'READY' },
  ],
  members: [
    { userId: 6, displayName: '项目经理', projectRole: 'MANAGER' },
    { userId: 7, displayName: '交付工程师', projectRole: 'ENGINEER' },
  ],
  milestones: [{ id: 11, name: '生产上线', dueDate: '2026-07-30' }],
  risks: [],
  templates: [],
  artifacts: [],
  activities: [],
}

const existingTask = {
  id: 41,
  projectId: 9,
  title: '确认上线窗口',
  description: '',
  status: 'TODO',
  priority: 'NORMAL',
  creatorUserId: 7,
  creatorName: '交付工程师',
  assigneeUserId: 7,
  assigneeName: '交付工程师',
  dueAt: null,
  stageCode: null,
  milestoneId: null,
  completedByUserId: null,
  completedAt: null,
  reminderId: null,
  reminderAt: null,
  reminderEnabled: false,
  checklist: [],
  checklistCompleted: 0,
  checklistTotal: 0,
  version: 0,
  canEdit: true,
  canDelete: true,
}

afterEach(() => vi.unstubAllGlobals())

function show(fetch: ReturnType<typeof vi.fn>) {
  vi.stubGlobal('fetch', fetch)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AuthContext.Provider value={auth}>
        <ProjectTasks project={project} />
      </AuthContext.Provider>
    </QueryClientProvider>,
  )
}

it('仅填写标题即可快速创建，负责人默认自己且截止时间选填', async () => {
  const requests: Array<{ url: string; body?: Record<string, unknown> }> = []
  const fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    if (init?.method === 'POST' && url.endsWith('/api/v1/projects/9/tasks')) {
      const body = JSON.parse(String(init.body))
      requests.push({ url, body })
      return Response.json({ ...existingTask, id: 42, title: body.title })
    }
    if (url.includes('/api/v1/projects/9/tasks?filter=')) return Response.json([existingTask])
    if (url.endsWith('/api/v1/projects/9/tasks/41')) return Response.json(existingTask)
    return Response.json({})
  })
  show(fetch)
  const user = userEvent.setup()

  expect(await screen.findByRole('button', { name: '我的任务' })).toBeVisible()
  expect(await screen.findByText('无截止时间')).toBeVisible()
  expect(screen.getAllByLabelText('负责人')[0]).toHaveTextContent('交付工程师')
  expect(screen.getAllByLabelText('截止时间').find(item => item.tagName === 'INPUT')).not.toBeRequired()

  await user.type(screen.getByRole('textbox', { name: '任务标题' }), '  确认生产参数  {enter}')

  await waitFor(() => expect(requests).toHaveLength(1))
  expect(requests[0].body).toEqual({
    title: '确认生产参数',
    assigneeUserId: 7,
    dueAt: null,
  })
})

it('支持中文筛选、完成任务和重新打开', async () => {
  let completed = false
  const fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    if (url.endsWith('/complete') && init?.method === 'POST') {
      completed = true
      return Response.json({ ...existingTask, status: 'DONE', version: 1 })
    }
    if (url.endsWith('/reopen') && init?.method === 'POST') {
      completed = false
      return Response.json({ ...existingTask, status: 'TODO', version: 2 })
    }
    if (url.includes('/api/v1/projects/9/tasks?filter=')) {
      return Response.json(completed ? [{ ...existingTask, status: 'DONE', version: 1 }] : [existingTask])
    }
    if (url.endsWith('/api/v1/projects/9/tasks/41')) {
      return Response.json(completed ? { ...existingTask, status: 'DONE', version: 1 } : existingTask)
    }
    return Response.json({})
  })
  show(fetch)
  const user = userEvent.setup()

  expect(await screen.findByText('确认上线窗口')).toBeVisible()
  expect(screen.getByRole('button', { name: '全部任务' })).toBeVisible()
  expect(screen.getByRole('button', { name: '今天' })).toBeVisible()
  expect(screen.getByRole('button', { name: '已逾期' })).toBeVisible()
  expect(screen.getByRole('button', { name: '已完成' })).toBeVisible()

  await user.click(screen.getByRole('checkbox', { name: '完成：确认上线窗口' }))
  await waitFor(() => expect(completed).toBe(true))
})

it('详情可保存并通过中文确认框软删除', async () => {
  let updateBody: Record<string, unknown> | undefined
  let deleted = false
  const fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    if (init?.method === 'PUT' && url.endsWith('/api/v1/projects/9/tasks/41')) {
      updateBody = JSON.parse(String(init.body))
      return Response.json({ ...existingTask, ...updateBody, version: 1 })
    }
    if (init?.method === 'DELETE' && url.endsWith('/api/v1/projects/9/tasks/41')) {
      deleted = true
      return new Response(null, { status: 204 })
    }
    if (url.includes('/api/v1/projects/9/tasks?filter=')) return Response.json([existingTask])
    if (url.endsWith('/api/v1/projects/9/tasks/41')) return Response.json(existingTask)
    return Response.json({})
  })
  show(fetch)
  const user = userEvent.setup()

  const title = await screen.findByDisplayValue('确认上线窗口')
  await user.clear(title)
  await user.type(title, '确认上线窗口与回退方案')
  await user.click(screen.getByRole('button', { name: '保存任务' }))

  await waitFor(() => expect(updateBody).toBeDefined())
  expect(updateBody).toMatchObject({
    title: '确认上线窗口与回退方案',
    assigneeUserId: 7,
    dueAt: null,
    reminderEnabled: false,
    version: 0,
  })

  await user.click(screen.getByRole('button', { name: /删除任务/ }))
  expect(await screen.findAllByText('确认删除该任务？')).not.toHaveLength(0)
  await user.click(screen.getAllByRole('button', { name: '确认删除' })[0])
  await waitFor(() => expect(deleted).toBe(true))
})

it('可从项目详情地址直接打开指定任务', async () => {
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input)
    if (url === '/api/v1/projects/9') return Response.json(project)
    if (url.includes('/api/v1/projects/9/tasks?filter=')) return Response.json([existingTask])
    if (url === '/api/v1/projects/9/tasks/41') return Response.json(existingTask)
    return Response.json({})
  }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  render(
    <QueryClientProvider client={client}>
      <AuthContext.Provider value={auth}>
        <MemoryRouter
          initialEntries={['/projects/9?tab=tasks&taskId=41']}
          future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
        >
          <Routes><Route path="/projects/:id" element={<ProjectDetail />} /></Routes>
        </MemoryRouter>
      </AuthContext.Provider>
    </QueryClientProvider>,
  )

  expect(await screen.findByRole('tab', { name: /项目任务/ })).toHaveAttribute('aria-selected', 'true')
  expect(await screen.findByDisplayValue('确认上线窗口')).toBeVisible()
})

it('同一项目内点击提醒链接时会切换到任务标签', async () => {
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input)
    if (url === '/api/v1/projects/9') return Response.json(project)
    if (url.includes('/api/v1/projects/9/tasks?filter=')) return Response.json([existingTask])
    if (url === '/api/v1/projects/9/tasks/41') return Response.json(existingTask)
    return Response.json({})
  }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const user = userEvent.setup()

  render(
    <QueryClientProvider client={client}>
      <AuthContext.Provider value={auth}>
        <MemoryRouter
          initialEntries={['/projects/9']}
          future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
        >
          <Link to="/projects/9?tab=tasks&taskId=41">打开任务提醒</Link>
          <Routes><Route path="/projects/:id" element={<ProjectDetail />} /></Routes>
        </MemoryRouter>
      </AuthContext.Provider>
    </QueryClientProvider>,
  )

  expect(await screen.findByRole('tab', { name: '七阶段看板' })).toHaveAttribute('aria-selected', 'true')
  await user.click(screen.getByRole('link', { name: '打开任务提醒' }))
  expect(await screen.findByRole('tab', { name: /项目任务/ })).toHaveAttribute('aria-selected', 'true')
  expect(await screen.findByDisplayValue('确认上线窗口')).toBeVisible()
})
