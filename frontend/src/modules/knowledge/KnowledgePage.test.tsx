import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { AuthContext, type AuthState } from '../../app/AuthProvider'
import { KnowledgePage } from './KnowledgePage'

const writerAuth: AuthState = {
  loading: false,
  me: {
    id: 1, organizationId: 1, username: 'admin', displayName: '管理员',
    roles: ['ADMIN'], permissions: ['knowledge:read', 'knowledge:write', 'system:manage'],
  },
  login: vi.fn(), logout: vi.fn(), refresh: vi.fn(),
}

function show(client: QueryClient, auth = writerAuth) {
  return render(<QueryClientProvider client={client}>
    <AuthContext.Provider value={auth}>
      <MemoryRouter><KnowledgePage /></MemoryRouter>
    </AuthContext.Provider>
  </QueryClientProvider>)
}

it('聚合最佳实践、代码片段和培训材料', async () => {
  vi.stubGlobal('fetch', vi.fn((input: string) => {
    const body = input.includes('/api/v1/knowledge?') ? [
      { id: 1, type: 'CASE', title: '月末关账提速', summary: '将关账周期从三天缩短到一天', content: '实践正文', tags: '财务,关账', productName: '企业财务', versionName: 'V5', status: 'PUBLISHED', ownerName: '方案专家', version: 1 },
      { id: 2, type: 'CODE', title: '对账幂等重试', summary: '受控扩展点参考', content: '使用说明', tags: 'Java,对账', language: 'Java', codeText: 'retryOnce(businessKey);', status: 'PUBLISHED', ownerName: '开发专家', version: 1 },
      { id: 3, type: 'TRAINING', title: '交付经理训练营', summary: '七阶段门禁实操', content: '课程大纲', tags: '交付,培训', audience: '交付经理', durationMinutes: 90, status: 'DRAFT', ownerName: '方案专家', version: 0 },
    ] : []
    return Promise.resolve({ ok: true, json: async () => body })
  }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  show(client)
  await waitFor(() => expect(screen.getByText('月末关账提速')).toBeVisible())
  for (const tab of ['全部知识', '最佳实践', '代码片段', '培训材料']) expect(screen.getByText(tab)).toBeVisible()
  await userEvent.click(screen.getByText('代码片段'))
  expect(await screen.findByText('retryOnce(businessKey);')).toBeVisible()
  vi.unstubAllGlobals()
})

it('上传培训附件后显示文件状态并随草稿保存', async () => {
  const requests: Array<{ path: string; init?: RequestInit }> = []
  vi.stubGlobal('fetch', vi.fn((input: string, init?: RequestInit) => {
    requests.push({ path: input, init })
    let body: unknown = []
    if (input === '/api/v1/files') body = { id: 88, originalName: '培训课件.pdf', fileVersion: 1, sizeBytes: 2048 }
    else if (input === '/api/v1/knowledge' && init?.method === 'POST') body = { id: 9 }
    return Promise.resolve({ ok: true, status: input === '/api/v1/files' ? 201 : 200, json: async () => body })
  }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  show(client)

  await userEvent.click(screen.getByRole('button', { name: /创建知识/ }))
  await userEvent.click(screen.getByLabelText('知识类型'))
  const trainingOptions = await screen.findAllByText('培训材料')
  await userEvent.click(trainingOptions[trainingOptions.length - 1])
  const fileInput = document.body.querySelector<HTMLInputElement>('input[type="file"]')
  expect(fileInput).not.toBeNull()
  await userEvent.upload(fileInput!, new File(['slides'], '培训课件.pdf', { type: 'application/pdf' }))

  expect(await screen.findByText('培训课件.pdf')).toBeVisible()
  expect(screen.getByText(/已上传.*v1/)).toBeVisible()
  await userEvent.type(screen.getByLabelText('标题'), '交付经理训练营')
  await userEvent.type(screen.getByLabelText('一句话摘要'), '七阶段门禁实操')
  await userEvent.type(screen.getByLabelText('正文'), '课程内容')
  await userEvent.type(screen.getByLabelText('培训对象'), '交付经理')
  await userEvent.click(screen.getByRole('button', { name: '保存草稿' }))

  await waitFor(() => expect(requests.some(request => request.path === '/api/v1/knowledge' && request.init?.method === 'POST')).toBe(true))
  const saved = requests.find(request => request.path === '/api/v1/knowledge' && request.init?.method === 'POST')
  expect(JSON.parse(String(saved?.init?.body))).toMatchObject({ type: 'TRAINING', fileObjectId: 88 })
  vi.unstubAllGlobals()
})

it('已有培训附件可以下载并上传新版本', async () => {
  const requests: string[] = []
  vi.stubGlobal('fetch', vi.fn((input: string, init?: RequestInit) => {
    requests.push(input)
    let body: unknown = []
    if (input.includes('/api/v1/knowledge?')) body = [{ id: 7, type: 'TRAINING', title: '交付培训', summary: '七阶段课程', content: '课程正文', audience: '交付经理', durationMinutes: 60, fileObjectId: 45, fileOriginalName: '培训课件.pdf', fileVersion: 2, fileSizeBytes: 4096, visibility: 'ORGANIZATION', status: 'DRAFT', ownerUserId: 1, ownerName: '讲师', version: 1 }]
    else if (input === '/api/v1/files/45/versions' && init?.method === 'POST') body = { id: 45, originalName: '培训课件-修订.pdf', fileVersion: 3, sizeBytes: 5120 }
    return Promise.resolve({ ok: true, status: 200, json: async () => body })
  }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  show(client)

  await waitFor(() => expect(screen.getByText('交付培训')).toBeVisible())
  await userEvent.click(screen.getByRole('button', { name: /编\s*辑/ }))
  expect(await screen.findByText('培训课件.pdf')).toBeVisible()
  expect(screen.getByRole('link', { name: /下载/ })).toHaveAttribute('href', '/api/v1/files/45/download')
  const fileInput = document.body.querySelector<HTMLInputElement>('input[type="file"]')
  await userEvent.upload(fileInput!, new File(['revised'], '培训课件-修订.pdf', { type: 'application/pdf' }))

  expect(await screen.findByText('培训课件-修订.pdf')).toBeVisible()
  expect(screen.getByText(/已上传.*v3/)).toBeVisible()
  expect(requests).toContain('/api/v1/files/45/versions')
  vi.unstubAllGlobals()
})

it('展示文档模版并从卡片进入 Outline 文档工作区', async () => {
  vi.stubGlobal('fetch', vi.fn((input: string) => {
    let body: unknown = []
    if (input.includes('/api/v1/knowledge?')) body = [
      {
        id: 20, type: 'TEMPLATE', title: '项目启动检查单', summary: '启动阶段必需检查项',
        content: null, tags: '启动,检查', status: 'PUBLISHED', ownerName: 'PMO', version: 2,
        documentStatus: 'READY', documentRevision: 5, stageCode: 'START',
        requirement: 'REQUIRED', enabled: true, publishedRevision: 5,
      },
      {
        id: 21, type: 'CASE', title: '待迁移知识', summary: '等待 Outline 初始化',
        content: '不应作为成功正文展示', status: 'DRAFT', ownerName: '顾问', version: 0,
        documentStatus: 'PENDING',
      },
      {
        id: 22, type: 'TRAINING', title: '同步失败培训', summary: '非常长的培训摘要'.repeat(20),
        content: '旧正文', status: 'DRAFT', ownerName: '讲师', version: 0,
        documentStatus: 'FAILED', documentError: 'Outline 暂不可用',
      },
    ]
    else if (input === '/api/v1/knowledge/20/document') body = {
      linkId: 20, title: '项目启动检查单', markdown: '# 启动检查\n\n- 范围已确认',
      renderedHtml: '<h1>启动检查</h1><ul><li>范围已确认</li></ul>', revision: 5,
      syncStatus: 'READY', outlineUrl: 'http://localhost:3000/doc/start-template',
    }
    return Promise.resolve({ ok: true, status: 200, json: async () => body })
  }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  show(client)

  expect(await screen.findByText('项目启动检查单')).toBeVisible()
  expect(screen.getByText('文档模版')).toBeVisible()
  expect(screen.getByText('启动 · 必需')).toBeVisible()
  expect(screen.getByText('修订 5')).toBeVisible()
  expect(screen.getByText('待初始化')).toBeVisible()
  expect(screen.getByText('同步失败')).toBeVisible()
  expect(screen.queryByText('# 启动检查')).not.toBeInTheDocument()

  await userEvent.click(screen.getByText('项目启动检查单'))
  expect(await screen.findByText('范围已确认')).toBeVisible()
  expect(screen.getByRole('link', { name: '在 Outline 中打开' }))
    .toHaveAttribute('href', 'http://localhost:3000/doc/start-template')
  vi.unstubAllGlobals()
})

it('创建文档模版时维护阶段、必需性和启用状态', async () => {
  vi.stubGlobal('fetch', vi.fn(() =>
    Promise.resolve({ ok: true, status: 200, json: async () => [] }),
  ))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  show(client)

  await userEvent.click(screen.getByRole('button', { name: /创建知识/ }))
  await userEvent.click(screen.getByLabelText('知识类型'))
  const options = await screen.findAllByText('文档模版')
  await userEvent.click(options[options.length - 1])

  expect(screen.getByText('适用交付阶段')).toBeVisible()
  expect(screen.getByText('项目必需性')).toBeVisible()
  expect(screen.getByText('新项目自动应用')).toBeVisible()
  await userEvent.click(screen.getByLabelText('适用交付阶段'))
  expect(await screen.findAllByText('商机 · 需求调研')).not.toHaveLength(0)
  expect(screen.getAllByText('商机 · 决策评审')).not.toHaveLength(0)
  expect(screen.getAllByText('商机 · 甲方诉求')).not.toHaveLength(0)
  expect(screen.getAllByText('商机 · 差距分析')).not.toHaveLength(0)
  expect(screen.getAllByText('商机 · 评审会议')).not.toHaveLength(0)
  expect(screen.getAllByText('产品 · 功能设计 Spec')).not.toHaveLength(0)
  vi.unstubAllGlobals()
})

it('只读用户只能查看和导出知识文档', async () => {
  vi.stubGlobal('fetch', vi.fn((input: string) => {
    let body: unknown = []
    if (input.includes('/api/v1/knowledge?')) body = [{
      id: 30, type: 'CASE', title: '只读案例', summary: '供全员查看',
      status: 'PUBLISHED', ownerUserId: 9, ownerName: '方案专家', version: 1,
      documentStatus: 'READY',
    }]
    else if (input === '/api/v1/knowledge/30/document') body = {
      linkId: 30, title: '只读案例', markdown: '# 正文',
      renderedHtml: '<h1>正文</h1>', revision: 1, syncStatus: 'READY',
    }
    return Promise.resolve({ ok: true, status: 200, json: async () => body })
  }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  show(client, {
    ...writerAuth,
    me: {
      ...writerAuth.me!,
      id: 8,
      roles: ['VIEWER'],
      permissions: ['knowledge:read'],
    },
  })

  expect(await screen.findByText('只读案例')).toBeVisible()
  expect(screen.queryByRole('button', { name: /创建知识/ })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /编辑|发布/ })).not.toBeInTheDocument()
  await userEvent.click(screen.getByText('只读案例'))
  expect(await screen.findByText('正文')).toBeVisible()
  expect(screen.queryByRole('button', { name: '编辑' })).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: '导出' })).toBeVisible()
  vi.unstubAllGlobals()
})
