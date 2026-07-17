import { expect, type Locator, type Page, test } from '@playwright/test'

test('Outline drives knowledge templates and project stage documents end to end', async ({
  page, request,
}) => {
  test.setTimeout(240_000)
  const suffix = String(Date.now())
  const templateTitle = `项目启动检查单 ${suffix}`
  const projectCode = `DOC-E2E-${suffix}`
  const projectName = `文档中心项目 ${suffix}`
  const failedProjectCode = `DOC-FAIL-${suffix}`
  const failedProjectName = `文档故障项目 ${suffix}`
  const outlineURL = process.env.E2E_OUTLINE_URL
  if (!outlineURL) throw new Error('E2E_OUTLINE_URL is required')
  page.setDefaultTimeout(15_000)

  await login(page)
  await nav(page, '系统管理').click()
  await page.getByRole('link', { name: '文档中心' }).click()
  await expect(page.getByRole('heading', { name: '文档中心' })).toBeVisible()

  await page.getByLabel('服务地址').fill('http://mock-outline:3000')
  await page.getByLabel('浏览器访问地址').fill(outlineURL)
  await page.getByLabel('Collection 链接或 UUID').fill(
    'a4296a54-2044-4529-ba86-d598a5322e06',
  )
  await page.getByLabel('API Token').fill('ol_api_e2e')
  await page.getByRole('button', { name: '测试连接' }).click()
  await expect(page.getByText('E2E 文档中心')).toBeVisible()
  await page.getByRole('button', { name: '保存配置' }).click()
  await expect(page.getByText('Outline 配置已保存')).toBeVisible()

  await page.reload()
  await expect(page.getByText('API Token 已配置')).toBeVisible()
  await expect(page.getByLabel('API Token')).toHaveValue('')
  await expect(page.getByDisplayValue(
    'a4296a54-2044-4529-ba86-d598a5322e06',
  )).toBeVisible()

  await nav(page, '知识库').click()
  await page.getByRole('button', { name: '创建知识' }).click()
  const knowledge = page.getByRole('dialog', { name: '创建知识' })
  await choose(page, knowledge, '知识类型', '文档模版')
  await knowledge.getByLabel('标题').fill(templateTitle)
  await knowledge.getByLabel('一句话摘要').fill('项目启动阶段必需检查单')
  await knowledge.getByLabel('正文').fill(
    `# ${templateTitle}\n\n## 项目目标\n\n待填写\n\n## 启动条件\n\n待填写`,
  )
  await choose(page, knowledge, '适用交付阶段', '启动')
  await choose(page, knowledge, '项目必需性', '必需')
  await knowledge.getByRole('button', { name: '保存草稿' }).click()

  const detail = page.getByRole('dialog', { name: '知识文档' })
  await expect(detail).toBeVisible()
  await detail.getByRole('button', { name: '编辑' }).click()
  const markdown = detail.getByLabel('Markdown 正文')
  await markdown.fill(
    `# ${templateTitle}\n\n## 项目目标\n\n请填写项目目标。\n\n## 启动条件\n\n- 核心成员已到位`,
  )
  await detail.getByRole('button', { name: '保存' }).click()
  await expect(detail.getByText('核心成员已到位')).toBeVisible()
  await detail.getByRole('button', { name: '关闭' }).click()

  const templateCard = page.locator('.knowledge-card').filter({ hasText: templateTitle })
  await templateCard.getByRole('button', { name: '发布' }).click()
  await expect(templateCard.getByText('已发布')).toBeVisible()

  const projectId = await createProject(page, projectCode, projectName)
  await expect.poll(async () => {
    const value = await api<Project>(page, `/api/v1/projects/${projectId}`)
    return value.documentSpaceStatus
  }, { timeout: 30_000 }).toBe('READY')

  await page.reload()
  await page.getByRole('tab', { name: /项目文档/ }).click()
  await expect(page.getByText(templateTitle, { exact: true })).toBeVisible()
  await page.getByText(templateTitle, { exact: true }).click()
  const projectDocument = page.getByRole('dialog', { name: templateTitle })
  await projectDocument.getByRole('button', { name: '编辑' }).click()
  await projectDocument.getByLabel('Markdown 正文').fill(
    `# ${templateTitle}\n\n## 项目目标\n\n完成核心系统按期交付。\n\n## 启动条件\n\n- 核心成员已到位\n- 客户联系人已确认`,
  )
  await projectDocument.getByRole('button', { name: '保存' }).click()
  await projectDocument.getByRole('button', { name: '确认文档' }).click()
  await expect(projectDocument.getByText('已完成')).toBeVisible()
  await projectDocument.getByRole('button', { name: '关闭' }).click()

  await page.getByRole('tab', { name: '七阶段看板' }).click()
  await page.getByRole('button', { name: /推进至需求采集/ }).click()
  await expect(page.getByText('阶段推进成功')).toBeVisible()

  const projectDocuments = await api<ProjectDocument[]>(
    page, `/api/v1/projects/${projectId}/documents`,
  )
  const startupDocument = projectDocuments.find(item => item.title === templateTitle)
  expect(startupDocument).toBeDefined()
  expect(startupDocument?.status).toBe('COMPLETED')
  const documentContent = await api<DocumentContent>(
    page, `/api/v1/projects/${projectId}/documents/${startupDocument!.id}`,
  )
  const outlineIdentifier = new URL(documentContent.outlineUrl!).pathname.split('/').filter(Boolean).at(-1)
  expect(outlineIdentifier).toBeTruthy()
  const external = await request.post(
    `${outlineURL}/__test__/documents/${encodeURIComponent(outlineIdentifier!)}/external-update`,
    { data: { text: `${documentContent.markdown}\n\nOutline 外部修订` } },
  )
  expect(external.ok()).toBe(true)

  const refreshed = await api<ProjectDocument[]>(
    page, `/api/v1/projects/${projectId}/documents`,
  )
  expect(refreshed.find(item => item.id === startupDocument!.id)?.status)
    .toBe('PENDING_CONFIRMATION')

  for (const [format, contentType] of [
    ['md', 'text/markdown'],
    ['html', 'text/html'],
    ['pdf', 'application/pdf'],
    ['docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'],
  ] as const) {
    const download = await browserDownload(
      page,
      `/api/v1/projects/${projectId}/documents/${startupDocument!.id}/export?format=${format}`,
    )
    expect(download.status).toBe(200)
    expect(download.contentType).toContain(contentType)
    expect(download.size).toBeGreaterThan(20)
  }

  expect((await request.post(`${outlineURL}/__test__/availability`, {
    data: { available: false },
  })).ok()).toBe(true)
  const failedProjectId = await createProject(page, failedProjectCode, failedProjectName)
  await expect.poll(async () => {
    const value = await api<Project>(page, `/api/v1/projects/${failedProjectId}`)
    return value.documentSpaceStatus
  }, { timeout: 30_000 }).toBe('FAILED')

  expect((await request.post(`${outlineURL}/__test__/availability`, {
    data: { available: true },
  })).ok()).toBe(true)
  await page.reload()
  await page.getByRole('tab', { name: /项目文档/ }).click()
  await expect(page.getByText(/Outline request failed|Outline is unavailable/)).toBeVisible()
  await page.getByRole('button', { name: '重试初始化' }).click()
  await expect.poll(async () => {
    const value = await api<Project>(page, `/api/v1/projects/${failedProjectId}`)
    return value.documentSpaceStatus
  }, { timeout: 30_000 }).toBe('READY')
})

interface Project {
  id: number
  documentSpaceStatus: 'PENDING' | 'INITIALIZING' | 'READY' | 'FAILED'
}

interface ProjectDocument {
  id: number
  title: string
  status: string
}

interface DocumentContent {
  markdown: string
  outlineUrl?: string
}

async function login(page: Page) {
  await page.goto('/login')
  await page.getByLabel('账号').fill('admin')
  await page.getByLabel('密码').fill('Admin@123')
  await page.getByRole('button', { name: /登\s*录/ }).click()
  await expect(page).toHaveURL(/\/dashboard/)
}

function nav(page: Page, label: string) {
  return page.getByRole('link', { name: new RegExp(label) })
}

async function createProject(
  page: Page, code: string, name: string,
) {
  await nav(page, '项目空间').click()
  await page.getByRole('button', { name: /创建项目$/ }).click()
  const drawer = page.getByRole('dialog', { name: '创建交付项目' })
  await drawer.getByLabel('项目编号').fill(code)
  await drawer.getByLabel('项目名称').fill(name)
  await choose(page, drawer, '客户', '华东银行', true)
  await choose(page, drawer, '产品', '企业财务云')
  await choose(page, drawer, '标品版本', 'V5.0')
  const response = page.waitForResponse(value =>
    value.url().endsWith('/api/v1/projects')
      && value.request().method() === 'POST')
  await drawer.getByRole('button', { name: '创建项目', exact: true }).click()
  const created = await response
  expect(created.ok()).toBe(true)
  const project = await created.json() as { id: number }
  await page.getByPlaceholder('搜索项目、客户或编号').fill(name)
  await page.getByRole('link', { name, exact: true }).click()
  await expect(page.getByRole('heading', { name })).toBeVisible()
  return project.id
}

async function choose(
  page: Page, scope: Locator, label: string, option: string, partial = false,
) {
  const select = scope.getByRole('combobox', { name: label })
  await select.evaluate(element => {
    element.closest('.ant-select-selector')?.dispatchEvent(
      new MouseEvent('mousedown', { bubbles: true }),
    )
  })
  const choice = page.locator('.ant-select-dropdown:visible .ant-select-item-option')
    .filter({ hasText: partial ? option : new RegExp(`^${escape(option)}$`) })
    .first()
  await choice.waitFor({ state: 'visible', timeout: 10_000 })
  await choice.evaluate(element => (element as HTMLElement).click())
}

async function api<T>(
  page: Page, path: string, init: { method?: string; body?: unknown } = {},
) {
  return page.evaluate(async ({ path, method, body }) => {
    const headers: Record<string, string> = {}
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    if (method && !['GET', 'HEAD'].includes(method)) {
      const token = document.cookie.split('; ')
        .find(item => item.startsWith('XSRF-TOKEN='))?.split('=')[1]
      if (token) headers['X-XSRF-TOKEN'] = decodeURIComponent(token)
    }
    const response = await fetch(path, {
      method: method ?? 'GET',
      credentials: 'include',
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
    const value = await response.json().catch(() => undefined)
    if (!response.ok) throw new Error(value?.message ?? `HTTP ${response.status}`)
    return value
  }, { path, method: init.method, body: init.body }) as Promise<T>
}

async function browserDownload(page: Page, path: string) {
  return page.evaluate(async value => {
    const response = await fetch(value, { credentials: 'include' })
    return {
      status: response.status,
      contentType: response.headers.get('Content-Type') ?? '',
      size: (await response.arrayBuffer()).byteLength,
    }
  }, path)
}

function escape(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
