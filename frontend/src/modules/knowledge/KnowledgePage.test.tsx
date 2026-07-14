import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { KnowledgePage } from './KnowledgePage'

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
  render(<QueryClientProvider client={client}><MemoryRouter><KnowledgePage /></MemoryRouter></QueryClientProvider>)
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
  render(<QueryClientProvider client={client}><MemoryRouter><KnowledgePage /></MemoryRouter></QueryClientProvider>)

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
  render(<QueryClientProvider client={client}><MemoryRouter><KnowledgePage /></MemoryRouter></QueryClientProvider>)

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
