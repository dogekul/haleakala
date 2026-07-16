import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { message, Modal } from 'antd'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AuthContext, type AuthState } from '../../app/AuthProvider'
import { ProjectDetail } from './ProjectDetail'
import { ProjectDocuments } from './ProjectDocuments'
import type { Project } from './types'

afterEach(() => {
  Modal.destroyAll()
  message.destroy()
  vi.unstubAllGlobals()
})

const stages = [
  { id: 1, code: 'START', name: '启动', order: 1, status: 'ACTIVE' as const, gateStatus: 'OPEN' },
  { id: 2, code: 'REQUIREMENT', name: '需求采集', order: 2, status: 'PENDING' as const, gateStatus: 'OPEN' },
  { id: 3, code: 'CUSTOM_DEV', name: '二开实施', order: 3, status: 'PENDING' as const, gateStatus: 'OPEN' },
  { id: 4, code: 'GO_LIVE', name: '上线切换', order: 4, status: 'PENDING' as const, gateStatus: 'OPEN' },
  { id: 5, code: 'TRIAL_HANDOVER', name: '试运行与移交', order: 5, status: 'PENDING' as const, gateStatus: 'OPEN' },
  { id: 6, code: 'STANDARDIZATION', name: '标准化评估', order: 6, status: 'PENDING' as const, gateStatus: 'OPEN' },
  { id: 7, code: 'CLOSE', name: '项目收尾', order: 7, status: 'PENDING' as const, gateStatus: 'OPEN' },
]

const project: Project = {
  id: 9, organizationId: 1, code: 'PRJ-009', name: '核心系统交付',
  customerId: 3, customerName: '华东银行', productId: 4, productName: '智鹿 ERP',
  productVersionId: 5, productVersionName: 'V5.2', managerUserId: 7, managerName: '张宁',
  status: 'ACTIVE', currentStage: 'REQUIREMENT', riskLevel: 'GREEN', gateMode: 'BLOCK',
  documentSpaceStatus: 'READY', version: 0, stages, members: [], risks: [], milestones: [],
  templates: [], artifacts: [], activities: [],
}

const documents = [
  {
    id: 10, stageCode: 'START', title: '启动检查单', requirement: 'REQUIRED',
    status: 'TODO', revision: 1, sourceTemplateId: 100, sourceTemplateRevision: 3,
    lastSyncedAt: '2026-07-16T10:00:00',
  },
  {
    id: 11, stageCode: 'START', title: '启动会议纪要', requirement: 'OPTIONAL',
    status: 'FAILED', revision: 1, sourceTemplateId: 101, sourceTemplateRevision: 2,
    lastError: 'Outline 暂时不可用',
  },
  {
    id: 12, stageCode: 'REQUIREMENT', title: '需求规格说明书', requirement: 'REQUIRED',
    status: 'PENDING_CONFIRMATION', revision: 4, sourceTemplateId: 102,
    sourceTemplateRevision: 4, lastSyncedAt: '2026-07-16T11:00:00',
  },
  {
    id: 13, stageCode: 'REQUIREMENT', title: '需求确认记录', requirement: 'OPTIONAL',
    status: 'COMPLETED', revision: 2, confirmedRevision: 2, confirmedByName: '张宁',
    sourceTemplateId: 103, sourceTemplateRevision: 1,
  },
]

const managerAuth: AuthState = {
  loading: false,
  me: {
    id: 7, organizationId: 1, username: 'manager', displayName: '张宁',
    roles: ['PROJECT_MANAGER'], permissions: ['project:read', 'project:write'],
  },
  login: vi.fn(), logout: vi.fn(), refresh: vi.fn(),
}

function json(value: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(value), {
    status, headers: { 'Content-Type': 'application/json' },
  }))
}

function show(node: React.ReactNode, auth = managerAuth, initialPath = '/') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}>
    <AuthContext.Provider value={auth}>
      <MemoryRouter
        initialEntries={[initialPath]}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      >
        {node}
      </MemoryRouter>
    </AuthContext.Provider>
  </QueryClientProvider>)
}

it('按七阶段展示项目文档卡片并默认定位当前阶段', async () => {
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
    if (String(input) === '/api/v1/projects/9/documents') return json(documents)
    throw new Error(`unexpected request: ${String(input)}`)
  }))
  show(<ProjectDocuments project={project} />)

  expect(await screen.findByText('需求规格说明书')).toBeVisible()
  expect(screen.getByText('待确认')).toBeVisible()
  expect(screen.getByText('已完成')).toBeVisible()
  expect(screen.getByRole('button', { name: /需求采集/ })).toHaveClass('is-active')

  await userEvent.click(screen.getByRole('button', { name: /启动/ }))
  expect(screen.getByText('启动检查单')).toBeVisible()
  expect(screen.getByText('待填写')).toBeVisible()
  expect(screen.getByText('同步失败')).toBeVisible()
  expect(screen.getByText('必需')).toBeVisible()
  expect(screen.getByText('可选')).toBeVisible()
})

it('项目负责人可编辑并确认，普通成员只能编辑查看', async () => {
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    if (path === '/api/v1/projects/9/documents') return json(documents)
    if (path === '/api/v1/projects/9/documents/12' && !init?.method) return json({
      linkId: 88, title: '需求规格说明书', markdown: '# 需求规格说明书\n已补全',
      renderedHtml: '<h1>需求规格说明书</h1><p>已补全</p>', revision: 4,
      syncStatus: 'READY', outlineUrl: 'http://localhost:3000/doc/requirement',
    })
    if (path === '/api/v1/projects/9/documents/12/confirm' && init?.method === 'POST') {
      return json({ ...documents[2], status: 'COMPLETED', confirmedRevision: 4 })
    }
    throw new Error(`unexpected request: ${path}`)
  })
  vi.stubGlobal('fetch', fetch)
  const view = show(<ProjectDocuments project={project} />)
  await userEvent.click(await screen.findByText('需求规格说明书'))
  expect(await screen.findByText('已补全')).toBeVisible()
  await userEvent.click(screen.getByRole('button', { name: '确认文档' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith(
    '/api/v1/projects/9/documents/12/confirm',
    expect.objectContaining({ method: 'POST' }),
  ))

  view.unmount()
  fetch.mockClear()
  const memberAuth: AuthState = {
    ...managerAuth,
    me: { ...managerAuth.me!, id: 8, username: 'member', displayName: '项目成员' },
  }
  show(<ProjectDocuments project={project} />, memberAuth)
  await userEvent.click(await screen.findByText('需求规格说明书'))
  expect(await screen.findByText('已补全')).toBeVisible()
  expect(screen.queryByRole('button', { name: '确认文档' })).not.toBeInTheDocument()
})

it('项目文档初始化失败时展示原因并支持重试', async () => {
  const failed = {
    ...project, documentSpaceStatus: 'FAILED' as const,
    documentSpaceError: 'Outline API Key 未配置',
  }
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    if (path === '/api/v1/projects/9/documents') return json([])
    if (path === '/api/v1/projects/9/documents/retry' && init?.method === 'POST') {
      return json({ ...failed, documentSpaceStatus: 'PENDING' })
    }
    throw new Error(`unexpected request: ${path}`)
  })
  vi.stubGlobal('fetch', fetch)
  show(<ProjectDocuments project={failed} />)

  expect(await screen.findByText('Outline API Key 未配置')).toBeVisible()
  await userEvent.click(screen.getByRole('button', { name: '重试初始化' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith(
    '/api/v1/projects/9/documents/retry',
    expect.objectContaining({ method: 'POST' }),
  ))
})

it('阶段推进遵循持久化门禁模式且不会发送临时覆盖参数', async () => {
  const blockingProject = { ...project, currentStage: 'START' }
  let advanceBody: Record<string, unknown> | undefined
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    if (path === '/api/v1/projects/9') return json(blockingProject)
    if (path === '/api/v1/projects/9/advance' && init?.method === 'POST') {
      advanceBody = JSON.parse(String(init.body))
      return json({
        code: 'CONFLICT',
        message: '未完成必需文档：启动检查单、项目章程',
        traceId: 'trace-1',
      }, 409)
    }
    throw new Error(`unexpected request: ${path}`)
  })
  vi.stubGlobal('fetch', fetch)
  show(<Routes><Route path="/projects/:id" element={<ProjectDetail />} /></Routes>,
    managerAuth, '/projects/9')

  const advance = await screen.findByRole('button', { name: /推进至需求采集/ })
  await waitFor(() => expect(advance).toBeEnabled())
  await userEvent.click(advance)
  const dialog = await screen.findByRole('dialog', { name: '阶段门禁未通过' })
  expect(within(dialog).getByText('启动检查单')).toBeInTheDocument()
  expect(within(dialog).getByText('项目章程')).toBeInTheDocument()
  expect(within(dialog).queryByText('记录警告并推进')).not.toBeInTheDocument()
  expect(advanceBody).toEqual({ targetStage: 'REQUIREMENT' })
})

it('WARNING 项目先确认已知缺失项再由后端记录警告推进', async () => {
  const warningProject = {
    ...project, currentStage: 'START', gateMode: 'WARNING' as const,
  }
  let advanceBody: Record<string, unknown> | undefined
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    if (path === '/api/v1/projects/9') return json(warningProject)
    if (path === '/api/v1/projects/9/documents') return json(documents)
    if (path === '/api/v1/projects/9/advance' && init?.method === 'POST') {
      advanceBody = JSON.parse(String(init.body))
      return json({ ...warningProject, currentStage: 'REQUIREMENT' })
    }
    throw new Error(`unexpected request: ${path}`)
  })
  vi.stubGlobal('fetch', fetch)
  show(<Routes><Route path="/projects/:id" element={<ProjectDetail />} /></Routes>,
    managerAuth, '/projects/9')

  const advance = await screen.findByRole('button', { name: /推进至需求采集/ })
  await waitFor(() => expect(advance).toBeEnabled())
  await userEvent.click(advance)
  const dialog = await screen.findByRole('dialog', { name: '阶段存在未完成项' })
  expect(within(dialog).getByText('未完成必需文档：启动检查单')).toBeInTheDocument()
  expect(advanceBody).toBeUndefined()
  await userEvent.click(within(dialog).getByText('记录警告并推进'))
  await waitFor(() => expect(advanceBody).toEqual({ targetStage: 'REQUIREMENT' }))
})
