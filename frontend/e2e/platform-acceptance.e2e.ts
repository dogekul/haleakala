import { expect, test } from '@playwright/test'

test('admin can log in, explore the platform, and capture a requirement', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('账号').fill('admin')
  await page.getByLabel('密码').fill('Admin@123')
  await page.getByRole('button', { name: /登\s*录/ }).click()
  await expect(page).toHaveURL(/\/dashboard/)

  await expect(page.getByRole('heading', { name: '交付驾驶舱' })).toBeVisible()
  await expect(page.getByRole('table').first()).toBeVisible()
  await page.getByText('卡片', { exact: true }).click()
  await expect(page.locator('[data-testid^="dashboard-project-card-"]').first()).toBeVisible()
  await page.getByText('列表', { exact: true }).click()
  await expect(page.getByRole('table').first()).toBeVisible()

  for (const module of [
    { label: '项目空间', path: /\/projects$/, heading: '项目空间' },
    { label: '标准化中心', path: /\/standardization/, heading: '标准化中心' },
    { label: '知识库', path: /\/knowledge/, heading: '让交付经验成为可搜索、可复用的组织资产' },
    { label: '资源中心', path: /\/resources/, heading: '资源中心' },
  ]) {
    await page.getByRole('link', { name: new RegExp(module.label) }).click()
    await expect(page).toHaveURL(module.path)
    await expect(page.getByRole('heading', { name: module.heading })).toBeVisible()
  }

  await page.getByRole('link', { name: /系统管理/ }).click()
  await expect(page).toHaveURL(/\/admin\/users/)
  await expect(page.getByRole('heading', { name: '用户与团队' })).toBeVisible()
  await expect(page.getByRole('link', { name: '系统设置' })).toBeVisible()

  await page.getByRole('link', { name: /需求工坊/ }).click()
  await expect(page.getByRole('heading', { name: '需求工坊' })).toBeVisible()
  await page.getByRole('button', { name: /采集需求/ }).click()
  await page.getByLabel('所属项目').click()
  await page.getByText('PRJ-26001 · 华东银行财务中台', { exact: true }).click()
  const title = `E2E 验收需求 ${Date.now()}`
  await page.getByLabel('需求标题').fill(title)
  await page.getByLabel('业务描述与验收条件').fill('验证真实浏览器登录、模块导航与需求写入链路，验收条件为保存后可在列表中检索到。')
  await page.getByRole('button', { name: /保\s*存\s*草\s*稿/ }).click()
  await expect(page.getByText(title, { exact: true })).toBeVisible()
})
