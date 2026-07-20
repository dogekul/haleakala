import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { AuthContext, type AuthState } from '../../app/AuthProvider'
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
  const client = options.client ?? new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const page = (organizationId: number) => <QueryClientProvider client={client}>
    <AuthContext.Provider value={{
      ...auth,
      me: { ...auth.me!, organizationId },
    }}>
      <DocumentCenterPage />
    </AuthContext.Provider>
  </QueryClientProvider>
  const view = render(page(options.organizationId ?? 1))
  return {
    client,
    ...view,
    rerenderOrganization: (organizationId: number) => view.rerender(page(organizationId)),
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

it('接受与端到端环境一致的单标签 Outline 服务地址', async () => {
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    if (url.endsWith('/config/test') && init?.method === 'POST') return json({
      status: 'READY', collectionId: 'canonical-id', collectionName: '智鹿交付',
    })
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
  })
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  show()

  const serviceUrl = await screen.findByLabelText('服务地址')
  await user.clear(serviceUrl)
  await user.type(serviceUrl, 'http://mock-outline:3000')
  await user.click(screen.getByRole('button', { name: '测试连接' }))

  await waitFor(() => expect(fetch).toHaveBeenCalledWith(
    '/api/v1/admin/document-center/config/test',
    expect.objectContaining({
      method: 'POST',
      body: expect.stringContaining('"baseUrl":"http://mock-outline:3000"'),
    }),
  ))
})

it.each([
  'http:foo',
  'http://foo_bar:3000',
  'http://foo:3000/%2e',
  'http://user:password@outline:3000',
  'http://outline:3000/path',
  'http://outline:3000?tenant=1',
  'http://outline:3000#settings',
])('拒绝不是 HTTP(S) 根地址的 Outline 服务地址：%s', async invalidUrl => {
  const fetch = vi.fn((input: RequestInfo | URL) => {
    const url = String(input)
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
  })
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  show()

  const serviceUrl = await screen.findByLabelText('服务地址')
  await user.clear(serviceUrl)
  await user.type(serviceUrl, invalidUrl)
  await user.click(screen.getByRole('button', { name: '测试连接' }))

  expect(await screen.findByText('请输入 HTTP(S) 根地址')).toBeVisible()
  expect(fetch).not.toHaveBeenCalledWith(
    '/api/v1/admin/document-center/config/test',
    expect.anything(),
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
  await act(() => client.refetchQueries({
    queryKey: ['admin-outline-configuration', 1],
  }))

  expect(serviceUrl).toHaveValue('http://draft-outline:3000')
})

it('未编辑时从缓存值更新为网络配置', async () => {
  let resolveConfiguration!: (response: Response) => void
  const networkConfiguration = new Promise<Response>(resolve => {
    resolveConfiguration = resolve
  })
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  client.setQueryData(['admin-outline-configuration', 1], {
    baseUrl: 'http://cached-outline:3000',
    publicBaseUrl: 'http://cached-outline:3000',
    collectionId: 'cached-collection',
    apiTokenConfigured: true,
    source: 'ORGANIZATION',
  })
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
    const url = String(input)
    if (url.endsWith('/config')) return networkConfiguration
    if (url.endsWith('/status')) return json(notConfiguredStatus)
    if (url.endsWith('/jobs')) return json([])
    return json([])
  }))

  show({ client })
  const serviceUrl = await screen.findByLabelText('服务地址')
  expect(serviceUrl).toHaveValue('http://cached-outline:3000')

  await act(async () => resolveConfiguration(new Response(JSON.stringify({
    baseUrl: 'http://network-outline:3000',
    publicBaseUrl: 'http://network-outline:3000',
    collectionId: 'network-collection',
    apiTokenConfigured: true,
    source: 'ORGANIZATION',
  }), { status: 200, headers: { 'Content-Type': 'application/json' } })))

  await waitFor(() => expect(serviceUrl).toHaveValue('http://network-outline:3000'))
})

it('未编辑时重新加载最新配置', async () => {
  let baseUrl = 'http://initial-outline:3000'
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
    const url = String(input)
    if (url.endsWith('/config')) return json({
      baseUrl,
      publicBaseUrl: 'http://localhost:3000',
      collectionId: 'collection-id',
      apiTokenConfigured: true,
      source: 'ORGANIZATION',
    })
    if (url.endsWith('/status')) return json(notConfiguredStatus)
    if (url.endsWith('/jobs')) return json([])
    return json([])
  }))
  const { client } = show()
  expect(await screen.findByDisplayValue('http://initial-outline:3000')).toBeVisible()

  baseUrl = 'http://refetched-outline:3000'
  await act(() => client.refetchQueries({
    queryKey: ['admin-outline-configuration', 1],
  }))

  expect(await screen.findByDisplayValue('http://refetched-outline:3000')).toBeVisible()
})

it('保存成功后重新加载可以更新表单', async () => {
  let baseUrl = 'http://initial-outline:3000'
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    if (url.endsWith('/config') && init?.method === 'PUT') {
      const values = JSON.parse(String(init.body))
      baseUrl = values.baseUrl
      return json({
        ...values, apiTokenConfigured: true, source: 'ORGANIZATION',
      })
    }
    if (url.endsWith('/config')) return json({
      baseUrl,
      publicBaseUrl: 'http://localhost:3000',
      collectionId: 'collection-id',
      apiTokenConfigured: true,
      source: 'ORGANIZATION',
    })
    if (url.endsWith('/status')) return json(notConfiguredStatus)
    if (url.endsWith('/jobs')) return json([])
    return json([])
  })
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  const { client } = show()
  const serviceUrl = await screen.findByLabelText('服务地址')
  await user.clear(serviceUrl)
  await user.type(serviceUrl, 'http://saved-outline:3000')
  await user.click(screen.getByRole('button', { name: '保存配置' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith(
    '/api/v1/admin/document-center/config', expect.objectContaining({ method: 'PUT' }),
  ))

  baseUrl = 'http://post-save-outline:3000'
  await act(() => client.refetchQueries({
    queryKey: ['admin-outline-configuration', 1],
  }))

  expect(await screen.findByDisplayValue('http://post-save-outline:3000')).toBeVisible()
})

it('连接测试返回的规范 Collection 不会被重新加载覆盖且会用于保存', async () => {
  let collectionId = 'old-collection'
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    if (url.endsWith('/config/test') && init?.method === 'POST') return json({
      status: 'READY', collectionId: 'canonical-collection', collectionName: '智鹿交付',
    })
    if (url.endsWith('/config') && init?.method === 'PUT') return json({
      ...JSON.parse(String(init.body)), apiTokenConfigured: true, source: 'ORGANIZATION',
    })
    if (url.endsWith('/config')) return json({
      baseUrl: 'http://outline.internal:3000',
      publicBaseUrl: 'http://localhost:3000',
      collectionId,
      apiTokenConfigured: true,
      source: 'ORGANIZATION',
    })
    if (url.endsWith('/status')) return json(notConfiguredStatus)
    if (url.endsWith('/jobs')) return json([])
    return json([])
  })
  vi.stubGlobal('fetch', fetch)
  const user = userEvent.setup()
  const { client } = show()
  const collection = await screen.findByLabelText('Collection 链接或 UUID')

  await user.click(screen.getByRole('button', { name: '测试连接' }))
  await waitFor(() => expect(collection).toHaveValue('canonical-collection'))

  collectionId = 'refetched-collection'
  await act(() => client.refetchQueries({
    queryKey: ['admin-outline-configuration', 1],
  }))
  expect(collection).toHaveValue('canonical-collection')

  await user.click(screen.getByRole('button', { name: '保存配置' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith(
    '/api/v1/admin/document-center/config',
    expect.objectContaining({
      method: 'PUT', body: expect.stringContaining('"collectionId":"canonical-collection"'),
    }),
  ))
})

it('同一个查询缓存切换组织时不会回填或保存上一组织配置', async () => {
  let organizationId: 1 | 2 = 1
  let organizationBLoaded = false
  let resolveOrganizationB!: (response: Response) => void
  const organizationBResponse = new Promise<Response>(resolve => {
    resolveOrganizationB = resolve
  })
  const configurations = {
    1: {
      baseUrl: 'http://org-a-outline:3000', publicBaseUrl: 'http://org-a:3000',
      collectionId: 'org-a-collection', collectionName: '组织 A',
      apiTokenConfigured: true, source: 'ORGANIZATION',
    },
    2: {
      baseUrl: 'http://org-b-outline:3000', publicBaseUrl: 'http://org-b:3000',
      collectionId: 'org-b-collection', collectionName: '组织 B',
      apiTokenConfigured: true, source: 'ORGANIZATION',
    },
  } as const
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    if (url.endsWith('/config/test') && init?.method === 'POST') return json({
      status: 'READY',
      collectionId: configurations[organizationId].collectionId,
      collectionName: configurations[organizationId].collectionName,
    })
    if (url.endsWith('/config') && init?.method === 'PUT') return json({
      ...configurations[organizationId],
      ...JSON.parse(String(init.body)),
    })
    if (url.endsWith('/config')) {
      if (organizationId === 2 && !organizationBLoaded) return organizationBResponse
      return json(configurations[organizationId])
    }
    if (url.endsWith('/status')) return json(notConfiguredStatus)
    if (url.endsWith('/jobs')) return json([])
    return json([])
  })
  vi.stubGlobal('fetch', fetch)
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 60_000 } },
  })
  const user = userEvent.setup()
  const { rerenderOrganization } = show({ client, organizationId })

  expect(await screen.findByDisplayValue('http://org-a-outline:3000')).toBeVisible()
  await user.click(screen.getByRole('button', { name: '测试连接' }))
  expect(await screen.findByText('组织 A')).toBeVisible()

  organizationId = 2
  rerenderOrganization(organizationId)
  expect(screen.queryByDisplayValue('http://org-a-outline:3000')).not.toBeInTheDocument()
  expect(screen.queryByText('组织 A')).not.toBeInTheDocument()

  organizationBLoaded = true
  await act(async () => resolveOrganizationB(new Response(JSON.stringify(configurations[2]), {
    status: 200, headers: { 'Content-Type': 'application/json' },
  })))
  expect(await screen.findByDisplayValue('http://org-b-outline:3000')).toBeVisible()

  await user.click(screen.getByRole('button', { name: '保存配置' }))
  await waitFor(() => expect(fetch).toHaveBeenCalledWith(
    '/api/v1/admin/document-center/config',
    expect.objectContaining({
      method: 'PUT', body: expect.stringContaining('"baseUrl":"http://org-b-outline:3000"'),
    }),
  ))
  const saveRequest = fetch.mock.calls.find(([, init]) => init?.method === 'PUT')
  expect(String(saveRequest?.[1]?.body)).not.toContain('org-a')
})

it('配置就绪时操作按钮不再显示未配置提示', async () => {
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
    const url = String(input)
    if (url.endsWith('/config')) return json({
      baseUrl: 'http://outline.internal:3000',
      publicBaseUrl: 'http://localhost:3000',
      collectionId: 'collection-id',
      apiTokenConfigured: true,
      source: 'ORGANIZATION',
    })
    if (url.endsWith('/status')) return json({
      ...notConfiguredStatus, integrationStatus: 'READY', collectionId: 'collection-id',
    })
    if (url.endsWith('/jobs')) return json([])
    return json([])
  }))
  show()

  for (const name of ['初始化目录', '初始化产品文档', '迁移知识文档', '迁移项目文档']) {
    const button = await screen.findByRole('button', { name })
    await waitFor(() => expect(button).toBeEnabled())
    expect(button).not.toHaveAttribute('title')
  }
})

it('从文档中心初始化所有产品目录与功能 Spec', async () => {
  const fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    if (url.endsWith('/config')) return json({
      baseUrl: 'http://outline.internal:3000',
      publicBaseUrl: 'http://localhost:3000',
      collectionId: 'collection-id',
      apiTokenConfigured: true,
      source: 'ORGANIZATION',
    })
    if (url.endsWith('/status')) return json({
      ...notConfiguredStatus, integrationStatus: 'READY', collectionId: 'collection-id',
    })
    if (url.endsWith('/jobs')) return json([])
    if (url.endsWith('/initialize-products') && init?.method === 'POST') {
      return json({ completed: 3, failed: 1 })
    }
    return json([])
  })
  vi.stubGlobal('fetch', fetch)
  show()

  await userEvent.click(await screen.findByRole('button', { name: '初始化产品文档' }))

  await waitFor(() => expect(fetch).toHaveBeenCalledWith(
    '/api/v1/admin/document-center/initialize-products',
    expect.objectContaining({ method: 'POST' }),
  ))
  expect(await screen.findByText('产品文档初始化完成 3 项，失败 1 项')).toBeVisible()
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
