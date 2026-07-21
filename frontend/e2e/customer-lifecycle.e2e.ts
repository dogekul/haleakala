import { expect, type Locator, type Page, test } from '@playwright/test'

test('customer lifecycle flows from opportunity to delivery and closed operation', async ({ page }) => {
  test.setTimeout(120_000)
  const suffix = String(Date.now())
  const customerName = `生命周期客户 ${suffix}`
  const opportunityTitle = `生命周期商机 ${suffix}`
  const projectName = `生命周期交付项目 ${suffix}`

  await page.goto('/login')
  await page.getByLabel('账号').fill('admin')
  await page.getByLabel('密码').fill('Admin@123')
  await page.getByRole('button', { name: /登\s*录/ }).click()
  await expect(page).toHaveURL(/\/dashboard/)

  await page.getByRole('link', { name: /客户中心/ }).click()
  await page.getByRole('button', { name: '新建客户' }).click()
  const customerDrawer = page.getByRole('dialog', { name: '新建客户' })
  await customerDrawer.getByLabel('客户名称').fill(customerName)
  await customerDrawer.getByLabel('客户简称').fill('E2E客户')
  await customerDrawer.getByLabel('联系人').fill('生命周期经理')
  await customerDrawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('客户已创建')).toBeVisible()

  await page.getByRole('link', { name: '商机总览', exact: true }).click()
  await page.getByRole('button', { name: '新建商机' }).click()
  const opportunityDrawer = page.getByRole('dialog', { name: '新建商机' })
  await choose(page, opportunityDrawer, 'customerId', customerName)
  await opportunityDrawer.getByLabel('商机名称').fill(opportunityTitle)
  await opportunityDrawer.getByLabel('预计金额').fill('880000')
  await choose(page, opportunityDrawer, 'productId', '企业财务云')
  await choose(page, opportunityDrawer, 'productVersionId', 'V5.0')
  await choose(page, opportunityDrawer, 'commercialOwnerUserId', '系统管理员')
  await choose(page, opportunityDrawer, 'projectManagerUserId', '系统管理员')
  await choose(page, opportunityDrawer, 'operationOwnerUserId', '系统管理员')
  await opportunityDrawer.getByRole('button', { name: '保存商机' }).click()
  await expect(page.getByText('商机已创建')).toBeVisible()
  await expect(page.getByText(opportunityTitle, { exact: true })).toBeVisible()

  const found = await api<Array<{ id: number; version: number }>>(page,
    `/api/v1/opportunities?keyword=${encodeURIComponent(opportunityTitle)}`)
  expect(found).toHaveLength(1)
  let opportunity = await api<Opportunity>(page, `/api/v1/opportunities/${found[0].id}`)

  await page.getByRole('link', { name: '售前推进', exact: true }).click()
  await expect(page.getByText(opportunityTitle, { exact: true })).toBeVisible()
  await addArtifact(page, opportunityTitle, '商机调研报告', false)
  opportunity = await advanceOpportunity(page, opportunityTitle)
  await addArtifact(page, opportunityTitle, '决策评审纪要', false)
  opportunity = await advanceOpportunity(page, opportunityTitle)
  for (const [title, file] of [
    ['讲解材料', true], ['甲方诉求清单', false], ['POC 得分表', false], ['差距分析报告', false],
  ] as const) await addArtifact(page, opportunityTitle, title, file)
  opportunity = await advanceOpportunity(page, opportunityTitle)
  await addArtifact(page, opportunityTitle, '投标文件', true)
  opportunity = await advanceOpportunity(page, opportunityTitle)
  for (const [title, file] of [
    ['中标公示', true], ['合同', true], ['评审会议纪要', false], ['邮件归档', true], ['已盖章合同', true],
  ] as const) await addArtifact(page, opportunityTitle, title, file)

  const opportunityCard = page.locator('.presale-card').filter({ hasText: opportunityTitle })
  await opportunityCard.getByRole('button', { name: `转交${opportunityTitle}` }).click()
  const handoff = page.getByRole('dialog', { name: '转交实施' })
  await expect(handoff).toBeVisible()
  await choose(page, handoff, 'productId', '企业财务云')
  await choose(page, handoff, 'productVersionId', 'V5.0')
  await choose(page, handoff, 'managerUserId', '系统管理员')
  const projectNameInput = handoff.getByLabel('项目名称')
  await expect(projectNameInput).toHaveValue(`${customerName} - 企业财务云 V5.0 实施项目`)
  await projectNameInput.clear()
  await projectNameInput.pressSequentially(projectName)
  await handoff.getByLabel('开始日期').fill('2026-07-16')
  await handoff.getByLabel('计划结束').fill('2026-12-31')
  const handoffResponse = page.waitForResponse(response => response.url().endsWith(`/api/v1/opportunities/${opportunity.id}/handoff`) && response.request().method() === 'POST')
  await handoff.getByRole('button', { name: '确认转交' }).click()
  opportunity = await (await handoffResponse).json() as Opportunity
  await expect(page.getByText('已转交实施')).toBeVisible()
  expect(opportunity.status).toBe('WON')
  expect(opportunity.projectId).toBeGreaterThan(0)

  await page.getByRole('link', { name: '实施协同', exact: true }).click()
  await expect(page.getByText(opportunityTitle, { exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: projectName })).toHaveAttribute('href', /\/projects\//)
  await page.getByRole('link', { name: projectName }).click()
  for (const stage of ['启动', '需求采集', '二开实施', '上线切换', '试运行与移交', '标准化评估', '项目收尾']) {
    await expect(page.getByText(stage, { exact: true }).first()).toBeVisible()
  }

  for (const targetStage of ['REQUIREMENT', 'CUSTOM_DEV', 'GO_LIVE', 'TRIAL_HANDOVER', 'STANDARDIZATION', 'CLOSE']) {
    await api(page, `/api/v1/projects/${opportunity.projectId}/advance`, {
      method: 'POST', body: { targetStage, mode: 'BLOCK' },
    })
  }
  const operationTitle = projectName
  const allOperations = await api<CustomerOperation[]>(page, '/api/v1/operations')
  const linked = allOperations.filter(item => item.opportunityId === opportunity.id)
  expect(linked).toHaveLength(1)
  let operation = linked[0]
  expect(operation.stage).toBe('MAINTENANCE')

  await page.goto('/customers/operations')
  await expect(page.getByText('回款/维保', { exact: true })).toBeVisible()
  await page.getByPlaceholder('搜索运营或客户').fill(operationTitle)
  await expect(page.getByText(operationTitle, { exact: true })).toBeVisible()

  for (let index = 0; index < 3; index += 1) operation = await advanceOperation(page, operation)
  expect(operation.status).toBe('CLOSED')
  await page.reload()
  await page.getByPlaceholder('搜索运营或客户').fill(operationTitle)
  await page.getByText(operationTitle, { exact: true }).click()
  await expect(page.getByText('已关闭', { exact: true }).first()).toBeVisible()
  await expect(page.getByRole('button', { name: /编辑运营|推进运营/ })).toHaveCount(0)
})

interface Opportunity { id: number; version: number; status: string; projectId: number }
interface CustomerOperation { id: number; opportunityId?: number; title: string; stage: string; status: string; version: number }

async function choose(page: Page, scope: Locator, fieldId: string, option: string) {
  const input = scope.locator(`#${fieldId}`)
  await input.locator('xpath=ancestor::div[contains(concat(" ", normalize-space(@class), " "), " ant-select-selector ")]').click()
  const choice = page.locator(`[id^="${fieldId}_list_"][role="option"]`).filter({
    hasText: new RegExp(`^${option.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`),
  })
  await choice.waitFor({ state: 'attached' })
  await choice.evaluate(element => (element as HTMLElement).click())
}

async function addArtifact(page: Page, opportunityTitle: string, title: string, file: boolean) {
  const card = page.locator('.presale-card').filter({ hasText: opportunityTitle })
  await card.getByRole('button', { name: '产出物' }).click()
  const drawer = page.getByRole('dialog', { name: '补充产出物' })
  await choose(page, drawer, 'artifactType', title)
  await drawer.getByLabel('标题').fill(title)
  if (file) {
    await drawer.locator('input[type="file"]').setInputFiles({
      name: `${title}.pdf`, mimeType: 'application/pdf', buffer: Buffer.from(`${title} E2E`),
    })
    await expect(drawer.getByText(new RegExp(`${title}\\.pdf.*已上传`))).toBeVisible()
  } else {
    await drawer.getByLabel('报告正文').fill(`${title} E2E 验收正文`)
  }
  const response = page.waitForResponse(value => value.url().includes('/artifacts') && value.request().method() === 'POST')
  await drawer.getByRole('button', { name: '保存产出物' }).click()
  expect((await response).ok()).toBe(true)
  await expect(drawer).toBeHidden()
}

async function advanceOpportunity(page: Page, opportunityTitle: string) {
  const card = page.locator('.presale-card').filter({ hasText: opportunityTitle })
  const response = page.waitForResponse(value => value.url().includes('/advance') && value.request().method() === 'POST')
  await card.getByRole('button', { name: `推进${opportunityTitle}` }).click()
  const value = await response
  expect(value.ok()).toBe(true)
  return value.json() as Promise<Opportunity>
}

async function advanceOperation(page: Page, operation: CustomerOperation) {
  const card = page.locator('.operation-card').filter({ hasText: operation.title })
  const response = page.waitForResponse(value => value.url().endsWith(`/api/v1/operations/${operation.id}/advance`) && value.request().method() === 'POST')
  await card.getByRole('button', { name: `推进${operation.title}` }).click()
  const value = await response
  expect(value.ok()).toBe(true)
  return value.json() as Promise<CustomerOperation>
}

async function api<T = unknown>(page: Page, path: string, init: { method?: string; body?: unknown } = {}) {
  return page.evaluate(async ({ path, method, body }) => {
    const headers: Record<string, string> = {}
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    if (method && !['GET', 'HEAD'].includes(method)) {
      const token = document.cookie.split('; ').find(item => item.startsWith('XSRF-TOKEN='))?.split('=')[1]
      if (token) headers['X-XSRF-TOKEN'] = decodeURIComponent(token)
    }
    const response = await fetch(path, {
      method: method ?? 'GET', credentials: 'include', headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
    const value = await response.json().catch(() => undefined)
    if (!response.ok) throw new Error(value?.message ?? `HTTP ${response.status}`)
    return value
  }, { path, method: init.method, body: init.body }) as Promise<T>
}
