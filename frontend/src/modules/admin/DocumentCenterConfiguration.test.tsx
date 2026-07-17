import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { DocumentCenterPage } from './DocumentCenterPage'
import type { DocumentCenterStatus } from './types'

const notConfiguredStatus: DocumentCenterStatus = {
  integrationStatus: 'NOT_CONFIGURED',
  collectionId: '',
  knowledgeRoot: { status: 'PENDING' },
  projectRoot: { status: 'PENDING' },
  jobs: { pending: 0, running: 0, success: 0, failed: 0 },
  failedJobs: [],
}

const json = (value: unknown, status = 200) =>
  Promise.resolve(new Response(JSON.stringify(value), {
    status, headers: { 'Content-Type': 'application/json' },
  }))

function show() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return {
    client,
    ...render(
    <QueryClientProvider client={client}>
      <DocumentCenterPage />
    </QueryClientProvider>,
    ),
  }
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

it('测试并保存 Outline 配置且不会回填 Token', async () => {
  const consoleError = vi.spyOn(console, 'error')
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    if (url.endsWith('/config') && !init?.method) return json({
      baseUrl: 'http://outline.internal:3000',
      publicBaseUrl: 'http://localhost:3000',
      collectionId: 'old-collection',
      collectionName: '旧目录',
      apiTokenConfigured: true,
      source: 'ORGANIZATION',
    })
    if (url.endsWith('/config/test') && init?.method === 'POST') return json({
      status: 'READY',
      collectionId: '11111111-1111-4111-8111-111111111111',
      collectionName: '智鹿交付',
    })
    if (url.endsWith('/config') && init?.method === 'PUT') return json({
      baseUrl: 'http://outline.internal:3000',
      publicBaseUrl: 'http://localhost:3000',
      collectionId: '11111111-1111-4111-8111-111111111111',
      collectionName: '智鹿交付',
      apiTokenConfigured: true,
      source: 'ORGANIZATION',
    })
    if (url.endsWith('/status')) return json(notConfiguredStatus)
    if (url.endsWith('/jobs')) return json([])
    return json([])
  })
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  show()

  expect(await screen.findByDisplayValue('http://outline.internal:3000')).toBeVisible()
  expect(screen.getByText('API Token 已配置')).toBeVisible()
  expect(screen.getByLabelText('API Token')).toHaveValue('')

  const collection = screen.getByLabelText('Collection 链接或 UUID')
  await user.clear(collection)
  await user.type(
    collection,
    'http://localhost:3000/collection/delivery-D4rIACBrmU/',
  )
  await user.type(screen.getByLabelText('API Token'), 'ol_api_new')
  await user.click(screen.getByRole('button', { name: '测试连接' }))

  expect(await screen.findByText('智鹿交付')).toBeVisible()
  expect(collection).toHaveValue('11111111-1111-4111-8111-111111111111')

  await user.click(screen.getByRole('button', { name: '保存配置' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith(
    '/api/v1/admin/document-center/config',
    expect.objectContaining({
      method: 'PUT',
      body: expect.stringContaining('"apiToken":"ol_api_new"'),
    }),
  ))
  await waitFor(() => expect(screen.getByLabelText('API Token')).toHaveValue(''))
  expect(consoleError).not.toHaveBeenCalledWith(
    'Warning: Warning: There may be circular references',
  )
})

it('未配置时禁用初始化和迁移操作', async () => {
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
    const url = String(input)
    if (url.endsWith('/config')) return json({
      baseUrl: 'http://outline.internal:3000',
      publicBaseUrl: 'http://localhost:3000',
      collectionId: '',
      collectionName: '',
      apiTokenConfigured: false,
      source: 'ENVIRONMENT',
    })
    if (url.endsWith('/status')) return json(notConfiguredStatus)
    if (url.endsWith('/jobs')) return json([])
    return json([])
  }))
  show()
  expect(await screen.findByRole('button', { name: '初始化目录' })).toBeDisabled()
  expect(screen.getByRole('button', { name: '迁移知识文档' })).toBeDisabled()
  expect(screen.getByRole('button', { name: '迁移项目文档' })).toBeDisabled()
})

it('配置重新加载不覆盖已编辑的草稿', async () => {
  let baseUrl = 'http://outline.internal:3000'
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
    const url = String(input)
    if (url.endsWith('/config')) return json({
      baseUrl,
      publicBaseUrl: 'http://localhost:3000',
      collectionId: 'old-collection',
      apiTokenConfigured: true,
      source: 'ORGANIZATION',
    })
    if (url.endsWith('/status')) return json(notConfiguredStatus)
    if (url.endsWith('/jobs')) return json([])
    return json([])
  }))
  const user = userEvent.setup()
  const { client } = show()
  const serviceUrl = await screen.findByLabelText('服务地址')
  await user.clear(serviceUrl)
  await user.type(serviceUrl, 'http://draft-outline:3000')

  baseUrl = 'http://refetched-outline:3000'
  await act(() => client.refetchQueries({ queryKey: ['admin-outline-configuration'] }))

  expect(serviceUrl).toHaveValue('http://draft-outline:3000')
})

it('测试连接期间禁用配置编辑和保存', async () => {
  let resolveTest!: (response: Response) => void
  const testResponse = new Promise<Response>(resolve => { resolveTest = resolve })
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    if (url.endsWith('/config/test') && init?.method === 'POST') return testResponse
    if (url.endsWith('/config')) return json({
      baseUrl: 'http://outline.internal:3000',
      publicBaseUrl: 'http://localhost:3000',
      collectionId: 'old-collection',
      apiTokenConfigured: true,
      source: 'ORGANIZATION',
    })
    if (url.endsWith('/status')) return json(notConfiguredStatus)
    if (url.endsWith('/jobs')) return json([])
    return json([])
  }))
  const user = userEvent.setup()
  show()
  const serviceUrl = await screen.findByLabelText('服务地址')

  await user.click(screen.getByRole('button', { name: '测试连接' }))
  await waitFor(() => expect(serviceUrl).toBeDisabled())
  expect(screen.getByRole('button', { name: '保存配置' })).toBeDisabled()

  await act(() => {
    resolveTest(new Response(JSON.stringify({
      status: 'READY', collectionId: 'canonical-id', collectionName: '智鹿交付',
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    return testResponse
  })
})
