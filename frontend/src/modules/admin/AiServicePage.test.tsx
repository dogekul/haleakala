import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { message } from 'antd'
import { afterAll, afterEach, beforeAll, expect, it, vi } from 'vitest'
import { AuthContext, type AuthState } from '../../app/AuthProvider'
import { AiServicePage } from './AiServicePage'

const configuration = {
  baseUrl: 'https://ai.example.com/v1',
  model: 'qwen-plus',
  apiKeyConfigured: true,
  source: 'ORGANIZATION',
} as const

const json = (value: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(value), {
  status, headers: { 'Content-Type': 'application/json' },
}))

const auth: AuthState = {
  loading: false,
  me: {
    id: 1, organizationId: 1, username: 'admin', displayName: '管理员',
    roles: ['ADMIN'], permissions: ['admin:write'],
  },
  login: async () => undefined,
  logout: async () => undefined,
  refresh: async () => undefined,
}

function show(options: { client?: QueryClient, organizationId?: number } = {}) {
  const client = options.client ?? new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const page = (organizationId: number) => <QueryClientProvider client={client}>
    <AuthContext.Provider value={{
      ...auth,
      me: { ...auth.me!, organizationId },
    }}>
      <AiServicePage />
    </AuthContext.Provider>
  </QueryClientProvider>
  const view = render(page(options.organizationId ?? 1))
  return {
    client,
    ...view,
    rerenderOrganization: (organizationId: number) => view.rerender(page(organizationId)),
  }
}

beforeAll(() => act(() => message.config({ duration: 0 })))
afterAll(() => act(() => message.config({ duration: 3 })))

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

it('加载公开配置但不回填 API Key', async () => {
  vi.stubGlobal('fetch', vi.fn(() => json(configuration)))
  show()

  expect(await screen.findByDisplayValue(configuration.baseUrl)).toBeVisible()
  expect(screen.getByDisplayValue(configuration.model)).toBeVisible()
  expect(screen.getByText('API Key 已配置')).toBeVisible()
  expect(screen.getByLabelText('API Key')).toHaveValue('')
  expect(screen.getByText('留空则保持不变')).toBeVisible()
})

it('配置重新加载不覆盖已编辑的草稿', async () => {
  let baseUrlValue: string = configuration.baseUrl
  const fetch = vi.fn(() => json({ ...configuration, baseUrl: baseUrlValue }))
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  const { client } = show()
  const baseUrl = await screen.findByLabelText('Base URL')
  const apiKey = screen.getByLabelText('API Key')
  await user.clear(baseUrl)
  await user.type(baseUrl, 'https://draft.example.com/v1')
  await user.type(apiKey, 'sk-draft')

  baseUrlValue = 'https://refetched.example.com/v1'
  await act(async () => {
    await client.refetchQueries({ queryKey: ['ai-configuration', 1] })
  })
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2))
  await waitFor(() => expect(client.getQueryData(['ai-configuration', 1])).toMatchObject({
    baseUrl: 'https://refetched.example.com/v1',
  }))

  expect(baseUrl).toHaveValue('https://draft.example.com/v1')
  expect(apiKey).toHaveValue('sk-draft')
})

it('切换组织时不会显示或保存上一组织的配置', async () => {
  let organizationId: 1 | 2 = 1
  let resolveOrganizationB!: (response: Response) => void
  const organizationBResponse = new Promise<Response>(resolve => {
    resolveOrganizationB = resolve
  })
  const configurations = {
    1: { ...configuration, baseUrl: 'https://org-a.example.com/v1', model: 'model-a' },
    2: {
      ...configuration, baseUrl: 'https://org-b.example.com/v1', model: 'model-b',
      apiKeyConfigured: false, source: 'ENVIRONMENT',
    },
  } as const
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    if (init?.method === 'PUT') return json({
      ...configurations[organizationId], ...JSON.parse(String(init.body)),
    })
    if (organizationId === 2) return organizationBResponse
    return json(configurations[organizationId])
  })
  vi.stubGlobal('fetch', fetch)
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 60_000 } },
  })
  const user = userEvent.setup()
  const { rerenderOrganization } = show({ client, organizationId })
  expect(await screen.findByDisplayValue('https://org-a.example.com/v1')).toBeVisible()
  await user.type(screen.getByLabelText('API Key'), 'sk-org-a-draft')

  organizationId = 2
  rerenderOrganization(organizationId)

  expect(screen.queryByDisplayValue('https://org-a.example.com/v1')).not.toBeInTheDocument()
  expect(screen.queryByDisplayValue('model-a')).not.toBeInTheDocument()
  expect(screen.queryByText('API Key 已配置')).not.toBeInTheDocument()
  expect(screen.queryByDisplayValue('sk-org-a-draft')).not.toBeInTheDocument()

  await act(async () => resolveOrganizationB(await json(configurations[2])))
  expect(await screen.findByDisplayValue('https://org-b.example.com/v1')).toBeVisible()
  expect(screen.getByDisplayValue('model-b')).toBeVisible()
  expect(screen.getByLabelText('API Key')).toHaveValue('')
  expect(screen.getByText('API Key 未配置')).toBeVisible()

  await user.click(screen.getByRole('button', { name: '保存配置' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith(
    '/api/v1/admin/ai-service/config',
    expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({
        baseUrl: 'https://org-b.example.com/v1', model: 'model-b', apiKey: '',
      }),
    }),
  ))
  const saveRequest = fetch.mock.calls.find(([, init]) => init?.method === 'PUT')
  expect(String(saveRequest?.[1]?.body)).not.toContain('org-a')
  expect(String(saveRequest?.[1]?.body)).not.toContain('model-a')
  expect(String(saveRequest?.[1]?.body)).not.toContain('sk-org-a-draft')
  expect(await screen.findByText('AI 服务配置已保存')).toBeVisible()
  await act(async () => message.destroy())
  await waitFor(() => expect(screen.queryByText('AI 服务配置已保存')).not.toBeInTheDocument())
})

it('提交前校验 Base URL 和模型必填', async () => {
  vi.stubGlobal('fetch', vi.fn(() => json(configuration)))
  const user = userEvent.setup()
  show()
  const baseUrl = await screen.findByLabelText('Base URL')
  await user.clear(baseUrl)
  await user.clear(screen.getByLabelText('模型'))

  await user.click(screen.getByRole('button', { name: '测试连接' }))

  expect(await screen.findByText('请输入 Base URL')).toBeVisible()
  expect(screen.getByText('请输入模型名称')).toBeVisible()
})

it.each([
  'ftp://ai.example.com/v1',
  'https://user:password@ai.example.com/v1',
  'https://ai.example.com/v2',
  'https://ai.example.com/v1/..',
  'https://ai.example.com/v1?',
  'https://ai.example.com/v1#',
  'https://ai.example.com/v1?tenant=1',
  'https://ai.example.com/v1#models',
])('拒绝不安全或非根路径的 Base URL：%s', async invalidUrl => {
  const fetch = vi.fn(() => json(configuration))
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  show()
  const baseUrl = await screen.findByLabelText('Base URL')
  await user.clear(baseUrl)
  await user.type(baseUrl, invalidUrl)

  await user.click(screen.getByRole('button', { name: '测试连接' }))

  expect(await screen.findByText('请输入 HTTP(S) 服务地址（仅支持根路径或 /v1）')).toBeVisible()
  expect(fetch).not.toHaveBeenCalledWith(
    '/api/v1/admin/ai-service/config/test',
    expect.anything(),
  )
})

it('测试连接发送当前草稿并保留表单值', async () => {
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    if (String(input).endsWith('/config/test') && init?.method === 'POST') {
      return json({ status: 'READY', model: 'qwen-plus' })
    }
    return json(configuration)
  })
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  show()
  const apiKey = await screen.findByLabelText('API Key')
  await user.type(apiKey, 'sk-draft')

  await user.click(screen.getByRole('button', { name: '测试连接' }))

  await waitFor(() => expect(fetch).toHaveBeenCalledWith(
    '/api/v1/admin/ai-service/config/test',
    expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        baseUrl: configuration.baseUrl, model: configuration.model, apiKey: 'sk-draft',
      }),
    }),
  ))
  expect(await screen.findByText('连接测试成功 · qwen-plus')).toBeVisible()
  expect(apiKey).toHaveValue('sk-draft')
})

it('保存配置后清空 API Key 并重新加载配置', async () => {
  let getCount = 0
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    if (String(input).endsWith('/config') && init?.method === 'PUT') {
      return json(configuration)
    }
    getCount += 1
    return json(configuration)
  })
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  show()
  const apiKey = await screen.findByLabelText('API Key')
  await user.type(apiKey, 'sk-new')

  await user.click(screen.getByRole('button', { name: '保存配置' }))

  await waitFor(() => expect(fetch).toHaveBeenCalledWith(
    '/api/v1/admin/ai-service/config',
    expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({
        baseUrl: configuration.baseUrl, model: configuration.model, apiKey: 'sk-new',
      }),
    }),
  ))
  expect(await screen.findByText('AI 服务配置已保存')).toBeVisible()
  await waitFor(() => expect(apiKey).toHaveValue(''))
  await waitFor(() => expect(getCount).toBeGreaterThanOrEqual(2))
})

it('显示后端返回的安全错误消息', async () => {
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    if (String(input).endsWith('/config/test') && init?.method === 'POST') {
      return json({ code: 'AI_CONNECTION_FAILED', message: 'AI 服务暂时不可用' }, 503)
    }
    return json(configuration)
  })
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  show()
  await screen.findByDisplayValue(configuration.baseUrl)

  await user.click(screen.getByRole('button', { name: '测试连接' }))

  expect(await screen.findByText('AI 服务暂时不可用')).toBeVisible()
})

it('新的连接测试失败时清除上一次成功结果', async () => {
  let testCount = 0
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    if (String(input).endsWith('/config/test') && init?.method === 'POST') {
      testCount += 1
      return testCount === 1
        ? json({ status: 'READY', model: 'qwen-plus' })
        : json({ code: 'AI_CONNECTION_FAILED', message: '新草稿连接失败' }, 503)
    }
    return json(configuration)
  })
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  show()
  await screen.findByDisplayValue(configuration.baseUrl)
  await user.click(screen.getByRole('button', { name: '测试连接' }))
  expect(await screen.findByText('连接测试成功 · qwen-plus')).toBeVisible()

  await user.click(screen.getByRole('button', { name: '测试连接' }))

  expect(await screen.findByText('新草稿连接失败')).toBeVisible()
  expect(screen.queryByText('连接测试成功 · qwen-plus')).not.toBeInTheDocument()
})

it('测试请求期间仅禁用测试按钮', async () => {
  let resolveTest!: (response: Response) => void
  const pending = new Promise<Response>(resolve => { resolveTest = resolve })
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL, init?: RequestInit) =>
    String(input).endsWith('/config/test') && init?.method === 'POST'
      ? pending
      : json(configuration)))
  const user = userEvent.setup()
  show()
  await screen.findByDisplayValue(configuration.baseUrl)

  await user.click(screen.getByRole('button', { name: '测试连接' }))

  await waitFor(() => expect(screen.getByRole('button', { name: '测试连接' })).toBeDisabled())
  expect(screen.getByRole('button', { name: '保存配置' })).toBeEnabled()
  await act(async () => resolveTest(await json({ status: 'READY', model: 'qwen-plus' })))
  await waitFor(() => expect(screen.getByRole('button', { name: '测试连接' })).toBeEnabled())
})

it('忽略表单编辑前启动的连接测试成功结果', async () => {
  let resolveTest!: (response: Response) => void
  const pending = new Promise<Response>(resolve => { resolveTest = resolve })
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) =>
    String(input).endsWith('/config/test') && init?.method === 'POST'
      ? pending
      : json(configuration))
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  show()
  await screen.findByDisplayValue(configuration.baseUrl)

  await user.click(screen.getByRole('button', { name: '测试连接' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith(
    '/api/v1/admin/ai-service/config/test',
    expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        baseUrl: configuration.baseUrl, model: configuration.model, apiKey: '',
      }),
    }),
  ))
  await waitFor(() => expect(screen.getByRole('button', { name: '测试连接' })).toBeDisabled())
  const model = screen.getByLabelText('模型')
  await user.clear(model)
  await user.type(model, 'edited-model')
  await act(async () => resolveTest(await json({ status: 'READY', model: 'qwen-plus' })))
  await waitFor(() => expect(screen.getByRole('button', { name: '测试连接' })).toBeEnabled())

  expect(screen.queryByText('连接测试成功 · qwen-plus')).not.toBeInTheDocument()
})

it('保存请求期间仅禁用保存按钮', async () => {
  let resolveSave!: (response: Response) => void
  const pending = new Promise<Response>(resolve => { resolveSave = resolve })
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL, init?: RequestInit) =>
    String(input).endsWith('/config') && init?.method === 'PUT'
      ? pending
      : json(configuration)))
  const user = userEvent.setup()
  show()
  await screen.findByDisplayValue(configuration.baseUrl)

  await user.click(screen.getByRole('button', { name: '保存配置' }))

  await waitFor(() => expect(screen.getByRole('button', { name: '保存配置' })).toBeDisabled())
  expect(screen.getByRole('button', { name: '测试连接' })).toBeEnabled()
  const baseUrl = screen.getByLabelText('Base URL')
  const model = screen.getByLabelText('模型')
  const apiKey = screen.getByLabelText('API Key')
  expect(baseUrl).toBeDisabled()
  expect(model).toBeDisabled()
  expect(apiKey).toBeDisabled()
  await user.type(apiKey, 'sk-too-late')
  expect(apiKey).toHaveValue('')
  await act(async () => resolveSave(await json(configuration)))
  await waitFor(() => expect(screen.getByRole('button', { name: '保存配置' })).toBeEnabled())
})
