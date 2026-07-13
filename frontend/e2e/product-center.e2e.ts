import { expect, type Locator, type Page, test } from '@playwright/test'

test.describe.configure({ mode: 'serial' })

test('product capability flows from catalog to delivery and back', async ({ page }, testInfo) => {
  const suffix = String(Date.now())
  const productName = `产品中心 E2E ${suffix}`
  const productCode = `E2E-${suffix}`
  const rootModule = `E2E 根模块 ${suffix}`
  const childModule = `E2E 二级模块 ${suffix}`
  const leafModule = `E2E 三级模块 ${suffix}`
  const catalogFeature = `E2E 标准功能 ${suffix}`
  const financeModule = `E2E 对账模块 ${suffix}`
  const requirementFeature = `E2E 需求功能 ${suffix}`
  const convertedFeature = `E2E 债务功能 ${suffix}`
  const browserErrors: string[] = []

  await page.goto('/login')
  await page.getByLabel('账号').fill('admin')
  await page.getByLabel('密码').fill('Admin@123')
  await page.getByRole('button', { name: /登\s*录/ }).click()
  page.on('pageerror', error => browserErrors.push(`pageerror: ${error.message}`))
  page.on('console', message => {
    if (message.type() === 'error') browserErrors.push(`console: ${message.text()}`)
  })
  await nav(page, '产品中心').click()
  await expect(page.getByRole('heading', { name: '产品中心' })).toBeVisible()
  await expect(page.getByRole('link', { name: '企业财务云' })).toBeVisible()
  await assertVisual(page, testInfo.outputPath('products-1440.png'))

  await page.getByRole('button', { name: '新建产品' }).click()
  const newProductDrawer = page.getByRole('dialog', { name: '新建产品' })
  await newProductDrawer.getByLabel('产品编码').fill(productCode)
  await newProductDrawer.getByLabel('产品名称').fill(productName)
  await newProductDrawer.getByLabel('分类', { exact: true }).fill('E2E')
  await newProductDrawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('产品已创建')).toBeVisible()
  await page.getByPlaceholder('搜索产品名称或编码').fill(productName)
  await page.getByRole('link', { name: productName }).click()
  await expect(page.getByRole('heading', { name: productName })).toBeVisible()

  await page.getByRole('tab', { name: '模块与功能' }).click()
  await createModule(page, `ROOT-${suffix}`, rootModule)
  await createModule(page, `CHILD-${suffix}`, childModule, `ROOT-${suffix} · ${rootModule}`)
  await createModule(page, `LEAF-${suffix}`, leafModule, `CHILD-${suffix} · ${childModule}`)
  await page.getByRole('button', { name: '新建功能' }).click()
  const featureDrawer = page.getByRole('dialog', { name: '新建功能' })
  await choose(page, featureDrawer, '所属模块', `LEAF-${suffix} · ${leafModule}`)
  await featureDrawer.getByLabel('功能编码').fill(`CAP-${suffix}`)
  await featureDrawer.getByLabel('功能名称').fill(catalogFeature)
  await featureDrawer.getByRole('button', { name: '保存功能' }).click()
  await expect(page.getByText('功能已创建')).toBeVisible()
  await assertVisual(page, testInfo.outputPath('product-structure-1440.png'))

  await page.getByRole('tab', { name: '版本', exact: true }).click()
  await page.getByRole('button', { name: '新建版本' }).click()
  const newVersionDrawer = page.getByRole('dialog', { name: '新建版本' })
  await newVersionDrawer.getByLabel('版本名称').fill(`V-${suffix}`)
  await newVersionDrawer.getByRole('button', { name: '保存版本' }).click()
  await expect(page.getByText('版本已创建')).toBeVisible()
  await page.getByLabel(`${catalogFeature}可用性`).selectOption('INCLUDED')
  await page.getByRole('button', { name: '保存功能清单' }).click()
  await expect(page.getByText('版本功能清单已保存')).toBeVisible()
  await page.getByRole('button', { name: `编辑版本 V-${suffix}` }).click()
  const editVersionDrawer = page.getByRole('dialog', { name: '编辑版本' })
  await editVersionDrawer.getByLabel('发布日期').fill('2026-12-31')
  await choose(page, editVersionDrawer, '状态', '已发布')
  await editVersionDrawer.getByRole('button', { name: '保存版本' }).click()
  await expect(page.getByText('版本已更新')).toBeVisible()
  await expect(page.getByRole('row', { name: new RegExp(`V-${suffix}.*已发布`) })).toBeVisible()
  await assertVisual(page, testInfo.outputPath('product-version-1440.png'))

  await page.getByRole('tab', { name: '概览' }).click()
  await expect(page.getByRole('tabpanel', { name: '概览' }).getByText(`V-${suffix}`, { exact: true })).toBeVisible()
  await page.getByRole('tab', { name: '覆盖度' }).click()
  await expect(page.getByText('功能覆盖分布')).toBeVisible()

  await nav(page, '产品中心').click()
  await page.setViewportSize({ width: 1024, height: 768 })
  await page.getByText('卡片', { exact: true }).click()
  await expect(page.getByTestId('product-card-grid')).toBeVisible()
  await assertVisual(page, testInfo.outputPath('products-card-1024.png'))
  await page.getByText('列表', { exact: true }).click()
  await page.getByPlaceholder('搜索产品名称或编码').fill('企业财务云')
  await page.getByRole('link', { name: '企业财务云' }).click()
  await page.getByRole('tab', { name: '模块与功能' }).click()
  await createModule(page, `FIN-E2E-${suffix}`, financeModule)
  await page.getByText(`FIN-E2E-${suffix} · ${financeModule}`, { exact: true }).click()
  await page.getByRole('button', { name: '新建功能' }).click()
  const financeFeatureDrawer = page.getByRole('dialog', { name: '新建功能' })
  await financeFeatureDrawer.getByLabel('功能编码').fill(`FIN-REQ-${suffix}`)
  await financeFeatureDrawer.getByLabel('功能名称').fill(requirementFeature)
  await financeFeatureDrawer.getByRole('button', { name: '保存功能' }).click()
  await expect(page.getByText('功能已创建')).toBeVisible()

  await page.getByRole('link', { name: '需求工坊' }).click()
  const requirementRow = page.getByRole('row').filter({ hasText: 'REQ-260001' })
  await requirementRow.getByRole('button', { name: '功能覆盖' }).click()
  const coverageDrawer = page.getByRole('dialog', { name: '功能覆盖' })
  await expect(coverageDrawer.getByText('REQ-260001')).toBeVisible()
  await assertDialogVisual(page, coverageDrawer, testInfo.outputPath('requirement-coverage-1024.png'))
  await coverageDrawer.getByRole('button', { name: '添加功能' }).click()
  await choose(page, coverageDrawer, '产品功能', `FIN-REQ-${suffix} · ${requirementFeature}`)
  await coverageDrawer.getByRole('button', { name: '保存覆盖' }).click()
  await expect(page.getByText('功能覆盖已保存')).toBeVisible()

  await page.getByRole('link', { name: '标准化中心' }).click()
  await choose(page, page, '产品', '企业财务云', true)
  await expect(page.getByText('企业财务云 / V5.0')).toBeVisible()
  await page.getByRole('tab', { name: '标准化债务' }).click()
  const debtRow = page.getByRole('row').filter({ hasText: 'reconciliation.retry' })
  await expect(debtRow).toBeVisible()
  await debtRow.getByRole('button', { name: '转为产品功能' }).click()
  const conversionDrawer = page.getByRole('dialog', { name: '转为产品功能' })
  await assertDialogVisual(page, conversionDrawer, testInfo.outputPath('conversion-drawer-1024.png'))
  await choose(page, conversionDrawer, '目标模块', `FIN-E2E-${suffix} · ${financeModule}`)
  await conversionDrawer.getByLabel('功能编码').fill(`FIN-DEBT-${suffix}`)
  await conversionDrawer.getByLabel('功能名称').fill(convertedFeature)
  await conversionDrawer.getByRole('button', { name: '创建功能' }).click()
  await expect(page.getByText('已转为产品功能')).toBeVisible()
  await expect(debtRow.getByText('已纳入', { exact: true })).toBeVisible()

  await page.getByRole('link', { name: '需求工坊' }).click()
  await page.getByRole('row').filter({ hasText: 'REQ-260001' }).getByRole('button', { name: '功能覆盖' }).click()
  const traceDrawer = page.getByRole('dialog', { name: '功能覆盖' })
  await expect(traceDrawer.getByText(requirementFeature, { exact: false })).toBeVisible()
  await expect(traceDrawer.getByText(convertedFeature, { exact: false })).toBeVisible()
  await traceDrawer.getByRole('button', { name: '关闭' }).click()

  await nav(page, '产品中心').click()
  await page.getByPlaceholder('搜索产品名称或编码').fill(productName)
  await page.getByRole('button', { name: `编辑${productName}` }).click()
  const productDrawer = page.getByRole('dialog', { name: '编辑产品' })
  await choose(page, productDrawer, '状态', '已归档')
  await productDrawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('产品已更新')).toBeVisible()
  await page.getByRole('link', { name: productName }).click()
  await expect(page.getByText('该产品已归档，所有配置仅可查看。')).toBeVisible()
  await page.getByRole('tab', { name: '模块与功能' }).click()
  await expect(page.getByRole('button', { name: '新建模块' })).toHaveCount(0)
  await assertVisual(page, testInfo.outputPath('archived-product-1024.png'))

  expect(browserErrors, 'browser console and page errors').toEqual([])
})

async function createModule(page: Page, code: string, name: string, parent?: string) {
  await page.getByRole('button', { name: '新建模块' }).click()
  const drawer = page.getByRole('dialog', { name: '新建模块' })
  if (parent) await choose(page, drawer, '父模块', parent)
  await drawer.getByLabel('模块编码').fill(code)
  await drawer.getByLabel('模块名称').fill(name)
  await drawer.getByRole('button', { name: '保存模块' }).click()
  await expect(drawer).toBeHidden()
  await expect(page.getByText('模块已创建').last()).toBeVisible()
}

async function choose(page: Page, scope: Page | Locator, label: string, option: string, search = false) {
  const select = scope.getByRole('combobox', { name: label }).last()
  if (search) {
    await select.fill(option)
    await page.locator('.ant-select-dropdown:visible').getByText(option, { exact: true }).click()
    return
  }
  await select.press('ArrowDown')
  await page.getByRole('option', { name: option, exact: true }).click()
}

async function assertVisual(page: Page, path: string) {
  await page.mouse.move(500, 70)
  await expect(page.locator('.ant-dropdown:not(.ant-dropdown-hidden)')).toHaveCount(0)
  await expect(page.locator('.ant-tooltip:not(.ant-tooltip-hidden)')).toHaveCount(0)
  await expect(page.locator('.ant-message-notice')).toHaveCount(0, { timeout: 5_000 })
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path, fullPage: true })
}

async function assertDialogVisual(page: Page, dialog: Locator, path: string) {
  await expect.poll(() => dialog.evaluate(element => {
    const rect = element.getBoundingClientRect()
    return rect.left >= 0 && rect.right <= window.innerWidth + 1
  })).toBe(true)
  await assertVisual(page, path)
}

function nav(page: Page, name: string) {
  return page.getByRole('complementary', { name: '主模块导航' }).getByRole('link', { name })
}
