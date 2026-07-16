import { expect, type Locator, type Page, test } from '@playwright/test'

test('customer lifecycle flows from opportunity to delivery and closed operation', async ({ page }) => {
  test.setTimeout(120_000)
  const suffix = String(Date.now())
  const customerName = `生命周期客户 ${suffix}`
  const opportunityTitle = `生命周期商机 ${suffix}`
  const projectCode = `CRM-E2E-${suffix}`
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

  await artifact(page, opportunity.id, 'RESEARCH_REPORT', '商机调研报告', false)
  opportunity = await advanceOpportunity(page, opportunity)
  await artifact(page, opportunity.id, 'DECISION_MINUTES', '决策评审纪要', false)
  opportunity = await advanceOpportunity(page, opportunity, 'PASS')
  for (const [type, title, file] of [
    ['PRESENTATION', '讲解材料', true], ['CLIENT_REQUESTS', '甲方诉求清单', false],
    ['POC_SCORE', 'POC 得分表', false], ['GAP_ANALYSIS', '差距分析报告', false],
  ] as const) await artifact(page, opportunity.id, type, title, file)
  opportunity = await advanceOpportunity(page, opportunity)
  await artifact(page, opportunity.id, 'BID_DOCUMENT', '投标文件', true)
  opportunity = await advanceOpportunity(page, opportunity, 'PASS')
  for (const [type, title, file] of [
    ['AWARD_NOTICE', '中标公示', true], ['CONTRACT', '合同', true],
    ['REVIEW_MINUTES', '评审会议纪要', false], ['EMAIL_ARCHIVE', '邮件归档', true],
    ['SEALED_CONTRACT', '已盖章合同', true],
  ] as const) await artifact(page, opportunity.id, type, title, file)

  opportunity = await api<Opportunity>(page, `/api/v1/opportunities/${opportunity.id}/handoff`, {
    method: 'POST', body: { mode: 'CREATE', version: opportunity.version, project: {
      code: projectCode, name: projectName, productId: 100, productVersionId: 100,
      managerUserId: 100, startDate: '2026-07-16', plannedEndDate: '2026-12-31', gateMode: 'BLOCK',
    } },
  })
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
  const operationTitle = `${opportunityTitle}客户运营`
  const allOperations = await api<CustomerOperation[]>(page, '/api/v1/operations')
  const linked = allOperations.filter(item => item.opportunityId === opportunity.id)
  expect(linked).toHaveLength(1)
  let operation = linked[0]
  expect(operation.stage).toBe('MAINTENANCE')

  await page.goto('/customers/operations')
  await expect(page.getByText('回款/维保', { exact: true })).toBeVisible()
  await page.getByPlaceholder('搜索运营或客户').fill(operationTitle)
  await expect(page.getByText(operationTitle, { exact: true })).toBeVisible()

  for (let index = 0; index < 3; index += 1) {
    operation = await api<CustomerOperation>(page, `/api/v1/operations/${operation.id}/advance`, {
      method: 'POST', body: { version: operation.version },
    })
  }
  expect(operation.status).toBe('CLOSED')
  await page.reload()
  await page.getByPlaceholder('搜索运营或客户').fill(operationTitle)
  await page.getByText(operationTitle, { exact: true }).click()
  await expect(page.getByText('已关闭', { exact: true }).first()).toBeVisible()
  await expect(page.getByRole('button', { name: /编辑运营|推进运营/ })).toHaveCount(0)
})

interface Opportunity { id: number; version: number; status: string; projectId: number }
interface CustomerOperation { id: number; opportunityId?: number; stage: string; status: string; version: number }

async function choose(page: Page, scope: Locator, fieldId: string, option: string) {
  await scope.locator(`#${fieldId}`).click()
  await page.locator(`[id^="${fieldId}_list_"][role="option"]`).filter({ hasText: option }).click()
}

async function artifact(page: Page, opportunityId: number, artifactType: string, title: string, file: boolean) {
  await api(page, `/api/v1/opportunities/${opportunityId}/artifacts`, {
    method: 'POST', body: file
      ? { artifactType, title, fileId: 9000 }
      : { artifactType, title, contentMarkdown: `${title} E2E 验收正文` },
  })
}

async function advanceOpportunity(page: Page, opportunity: Opportunity, decision?: 'PASS' | 'REJECT') {
  return api<Opportunity>(page, `/api/v1/opportunities/${opportunity.id}/advance`, {
    method: 'POST', body: { version: opportunity.version, decision },
  })
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
