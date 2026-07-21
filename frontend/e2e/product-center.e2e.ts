import { expect, type Locator, type Page, test } from '@playwright/test'

test.describe.configure({ mode: 'serial' })

test('product capability flows from catalog to delivery and back', async ({ page }, testInfo) => {
  test.setTimeout(120_000)
  const suffix = String(Date.now())
  const productName = `产品中心 E2E ${suffix}`
  const plannedProductName = `E2E 未启用产品 ${suffix}`
  const releasedVersion = `V-${suffix}`
  const planningVersion = `NEXT-${suffix}`
  const rootModule = `E2E 根模块 ${suffix}`
  const childModule = `E2E 二级模块 ${suffix}`
  const leafModule = `E2E 三级模块 ${suffix}`
  const catalogFeature = `E2E 标准功能 ${suffix}`
  const projectName = `E2E 产品交付项目 ${suffix}`
  const customerName = `E2E 追溯客户 ${suffix}`
  const customerOption = `${customerName} · E2E客户`
  const requirementTitle = `E2E 产品追溯需求 ${suffix}`
  const tracedFeature = `E2E 标准化回流功能 ${suffix}`
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
  await newProductDrawer.getByLabel('产品名称').fill(productName)
  await newProductDrawer.getByLabel('分类', { exact: true }).fill('E2E')
  await newProductDrawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('产品已创建')).toBeVisible()
  await page.getByPlaceholder('搜索产品名称或编码').fill(productName)
  await page.getByRole('link', { name: productName }).click()
  await expect(page.getByRole('heading', { name: productName })).toBeVisible()
  const productId = Number(new URL(page.url()).pathname.split('/').filter(Boolean).at(-1))
  expect(productId).toBeGreaterThan(0)

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
  await seedScrollableModules(page, productId, suffix)
  await assertModuleTreeScroll(page)
  await assertVisual(page, testInfo.outputPath('product-structure-1440.png'))

  await page.getByRole('tab', { name: '版本', exact: true }).click()
  await page.getByRole('button', { name: '新建版本' }).click()
  const newVersionDrawer = page.getByRole('dialog', { name: '新建版本' })
  await newVersionDrawer.getByLabel('版本名称').fill(releasedVersion)
  await newVersionDrawer.getByRole('button', { name: '保存版本' }).click()
  await expect(page.getByText('版本已创建')).toBeVisible()
  await expect(page.locator('.product-detail-title').getByText('规划中', { exact: true })).toBeVisible()
  await expect(page.getByRole('row', { name: new RegExp(`${releasedVersion}.*规划中`) })).toBeVisible()
  await page.getByLabel(`${catalogFeature}可用性`).selectOption('INCLUDED')
  await page.getByRole('button', { name: '保存功能清单' }).click()
  await expect(page.getByText('版本功能清单已保存')).toBeVisible()

  await nav(page, '产品中心').click()
  await page.getByPlaceholder('搜索产品名称或编码').fill(productName)
  await page.getByRole('button', { name: `编辑${productName}` }).click()
  const activationDrawer = page.getByRole('dialog', { name: '编辑产品' })
  await choose(page, activationDrawer, '状态', '已启用')
  await activationDrawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('产品已更新')).toBeVisible()
  await page.getByRole('link', { name: productName }).click()
  await page.getByRole('tab', { name: '版本', exact: true }).click()
  await expect(page.getByRole('row', { name: new RegExp(`${releasedVersion}.*规划中`) })).toBeVisible()
  await expect(page.getByLabel(`${catalogFeature}可用性`)).toHaveValue('INCLUDED')
  await page.getByRole('button', { name: `编辑版本 ${releasedVersion}` }).click()
  const editVersionDrawer = page.getByRole('dialog', { name: '编辑版本' })
  await editVersionDrawer.getByLabel('发布日期').fill('2026-12-31')
  await choose(page, editVersionDrawer, '状态', '已发布')
  await editVersionDrawer.getByRole('button', { name: '保存版本' }).click()
  await expect(page.getByText('版本已更新')).toBeVisible()
  await expect(page.getByRole('row', { name: new RegExp(`${releasedVersion}.*已发布`) })).toBeVisible()
  await assertVisual(page, testInfo.outputPath('product-version-1440.png'))

  await page.getByRole('button', { name: '新建版本' }).click()
  const planningVersionDrawer = page.getByRole('dialog', { name: '新建版本' })
  await planningVersionDrawer.getByLabel('版本名称').fill(planningVersion)
  await planningVersionDrawer.getByRole('button', { name: '保存版本' }).click()
  await expect(page.getByText('版本已创建')).toBeVisible()
  await expect(page.getByRole('row', { name: new RegExp(`${planningVersion}.*规划中`) })).toBeVisible()

  await page.getByRole('tab', { name: '概览' }).click()
  await expect(page.getByRole('tabpanel', { name: '概览' }).getByText(releasedVersion, { exact: true })).toBeVisible()
  await page.getByRole('tab', { name: '覆盖度' }).click()
  await expect(page.getByText('功能覆盖分布')).toBeVisible()

  await nav(page, '产品中心').click()
  const productSearch = page.getByPlaceholder('搜索产品名称或编码')
  await productSearch.fill('')
  await page.getByRole('button', { name: '新建产品' }).click()
  const unbindableProductDrawer = page.getByRole('dialog', { name: '新建产品' })
  await unbindableProductDrawer.getByLabel('产品名称').fill(plannedProductName)
  await unbindableProductDrawer.getByLabel('分类', { exact: true }).fill('E2E')
  await unbindableProductDrawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('产品已创建')).toBeVisible()

  await nav(page, '客户中心').click()
  await expect(page.getByRole('heading', { name: '客户管理' })).toBeVisible()
  await page.getByRole('button', { name: '新建客户' }).click()
  const customerDrawer = page.getByRole('dialog', { name: '新建客户' })
  await customerDrawer.getByLabel('客户名称').fill(customerName)
  await customerDrawer.getByLabel('客户简称').fill('E2E客户')
  await customerDrawer.getByLabel('联系人').fill('E2E经理')
  await customerDrawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('客户已创建')).toBeVisible()
  await page.getByPlaceholder('搜索客户、简称或联系人').fill(customerName)
  await page.getByRole('button', { name: `编辑${customerName}` }).click()
  const editCustomerDrawer = page.getByRole('dialog', { name: '编辑客户' })
  await editCustomerDrawer.getByLabel('联系电话').fill('13800000000')
  await editCustomerDrawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('客户已更新')).toBeVisible()

  await nav(page, '项目空间').click()
  await expect(page.getByRole('heading', { name: '项目空间' })).toBeVisible()
  await page.getByRole('button', { name: /创建项目$/ }).click()
  const projectDrawer = page.getByRole('dialog', { name: '创建交付项目' })
  await projectDrawer.getByLabel('项目名称').fill(projectName)
  await choose(page, projectDrawer, '客户', customerOption, true)
  const projectProductSelect = projectDrawer.getByRole('combobox', { name: '产品' })
  await projectProductSelect.click()
  const productDropdown = page.locator('.ant-select-dropdown:visible')
  await expect(productDropdown.getByText(productName, { exact: true })).toBeVisible()
  await expect(productDropdown.getByText(plannedProductName, { exact: true })).toHaveCount(0)
  await productDropdown.getByText(productName, { exact: true }).click()
  const projectVersionSelect = projectDrawer.getByRole('combobox', { name: '标品版本' })
  await expect(projectVersionSelect).toBeEnabled()
  await projectVersionSelect.click()
  const versionDropdown = page.locator('.ant-select-dropdown:visible')
  await expect(versionDropdown.getByText(releasedVersion, { exact: true })).toBeVisible()
  await expect(versionDropdown.getByText(planningVersion, { exact: true })).toHaveCount(0)
  await versionDropdown.getByText(releasedVersion, { exact: true }).click()
  const projectResponse = page.waitForResponse(response =>
    response.url().endsWith('/api/v1/projects') && response.request().method() === 'POST')
  await projectDrawer.getByRole('button', { name: '创建项目', exact: true }).click()
  const createdProject = await (await projectResponse).json() as { id: number; code: string }
  const projectCode = String(createdProject.id)
  expect(createdProject.code).toBe(projectCode)
  await expect(page.getByText('项目创建成功，七阶段已初始化')).toBeVisible()
  await page.getByRole('link', { name: projectName, exact: true }).click()
  await expect(page.getByRole('heading', { name: projectName })).toBeVisible()
  await expect(page.getByText(`${productName} ${releasedVersion}`, { exact: true })).toBeVisible()

  await nav(page, '客户中心').click()
  await page.getByPlaceholder('搜索客户、简称或联系人').fill(customerName)
  await page.getByRole('button', { name: `编辑${customerName}` }).click()
  const stopCustomerDrawer = page.getByRole('dialog', { name: '编辑客户' })
  await choose(page, stopCustomerDrawer, '状态', '停用')
  await stopCustomerDrawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('客户已更新')).toBeVisible()
  await nav(page, '项目空间').click()
  await page.getByRole('button', { name: /创建项目$/ }).click()
  const inactiveCustomerProjectDrawer = page.getByRole('dialog', { name: '创建交付项目' })
  const activeCustomerSelect = inactiveCustomerProjectDrawer.getByRole('combobox', { name: '客户' })
  await activeCustomerSelect.fill(customerName)
  await expect(page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({ hasText: customerOption })).toHaveCount(0)
  await inactiveCustomerProjectDrawer.getByRole('button', { name: '关闭' }).click()

  await nav(page, '需求工坊').click()
  await page.getByRole('button', { name: '采集需求' }).click()
  const requirementDrawer = page.getByRole('dialog', { name: '需求采集单' })
  await choose(page, requirementDrawer, '所属项目', `${projectCode} · ${projectName}`, true)
  await requirementDrawer.getByLabel('需求标题').fill(requirementTitle)
  await requirementDrawer.getByLabel('业务描述与验收条件').fill('客户需要在已发布标品功能上增加可追溯的扩展流程，并能回流为产品标准功能。')
  await requirementDrawer.getByRole('button', { name: '保存草稿' }).click()
  await expect(page.getByText('需求已保存')).toBeVisible()
  const tracedRequirementRow = page.getByRole('row').filter({ hasText: requirementTitle })
  await expect(tracedRequirementRow).toContainText(projectCode)
  await tracedRequirementRow.getByRole('button', { name: '功能覆盖' }).click()
  const tracedCoverageDrawer = page.getByRole('dialog', { name: '功能覆盖' })
  await tracedCoverageDrawer.getByRole('button', { name: '添加功能' }).click()
  await choose(page, tracedCoverageDrawer, '产品功能', `CAP-${suffix} · ${catalogFeature}`)
  await choose(page, tracedCoverageDrawer, '覆盖方式', '部分覆盖')
  await tracedCoverageDrawer.getByRole('button', { name: '保存覆盖' }).click()
  await expect(page.getByText('功能覆盖已保存')).toBeVisible()

  await page.getByRole('row').filter({ hasText: requirementTitle }).getByRole('button', { name: '功能覆盖' }).click()
  const candidateDrawer = page.getByRole('dialog', { name: '功能覆盖' })
  await candidateDrawer.getByRole('button', { name: '加入标准化候选' }).click()
  await expect(page.locator('.ant-message-notice-content').getByText('已加入标准化候选', { exact: true })).toBeVisible()
  await candidateDrawer.getByRole('button', { name: '关闭' }).click()

  await nav(page, '标准化中心').click()
  await choose(page, page, '产品', productName, true)
  await choose(page, page, '版本', releasedVersion, true)
  await expect(page.getByText(`${productName} / ${releasedVersion}`, { exact: true })).toBeVisible()
  await page.getByRole('tab', { name: '标准化债务' }).click()
  const tracedDebtRow = page.getByRole('row').filter({ hasText: requirementTitle })
  await expect(tracedDebtRow).toBeVisible()
  await tracedDebtRow.getByRole('button', { name: '转为产品功能' }).click()
  const tracedConversionDrawer = page.getByRole('dialog', { name: '转为产品功能' })
  await choose(page, tracedConversionDrawer, '目标模块', `LEAF-${suffix} · ${leafModule}`)
  const optionalVersionSelect = tracedConversionDrawer.getByRole('combobox', { name: '加入规划版本' })
  await optionalVersionSelect.click()
  await expect(page.getByRole('option', { name: planningVersion, exact: true })).toBeVisible()
  await expect(page.getByRole('option', { name: releasedVersion, exact: true })).toHaveCount(0)
  await optionalVersionSelect.press('Escape')
  await tracedConversionDrawer.getByLabel('功能编码').fill(`STD-${suffix}`)
  await tracedConversionDrawer.getByLabel('功能名称').fill(tracedFeature)
  await tracedConversionDrawer.getByRole('button', { name: '创建功能' }).click()
  await expect(page.getByText('已转为产品功能')).toBeVisible()
  await expect(tracedDebtRow.getByText('已纳入', { exact: true })).toBeVisible()

  await nav(page, '产品中心').click()
  await page.getByPlaceholder('搜索产品名称或编码').fill(productName)
  await page.getByRole('link', { name: productName }).click()
  await page.getByRole('tab', { name: '模块与功能' }).click()
  await page.getByText(`LEAF-${suffix} · ${leafModule}`, { exact: true }).click()
  await expect(page.getByText(catalogFeature, { exact: true })).toBeVisible()
  await expect(page.getByText(tracedFeature, { exact: true })).toBeVisible()
  await page.getByRole('tab', { name: '覆盖度' }).click()
  const catalogCoverageRow = page.getByRole('row').filter({ hasText: catalogFeature })
  const tracedFeatureCoverageRow = page.getByRole('row').filter({ hasText: tracedFeature })
  await expect(catalogCoverageRow.getByText('部分 1', { exact: true })).toBeVisible()
  await expect(tracedFeatureCoverageRow.getByText('部分 1', { exact: true })).toBeVisible()
  const requirementTraceRow = page.getByRole('row').filter({ hasText: requirementTitle })
  await expect(requirementTraceRow).toContainText(projectCode)
  await expect(requirementTraceRow.getByText('已加入', { exact: true })).toBeVisible()
  await expect(requirementTraceRow.getByRole('link')).toHaveAttribute('href', /\/requirements\?requirementId=\d+/)

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
  const sunsetProductDrawer = page.getByRole('dialog', { name: '编辑产品' })
  await choose(page, sunsetProductDrawer, '状态', '停止演进')
  await sunsetProductDrawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('产品已更新')).toBeVisible()
  await page.getByRole('button', { name: `编辑${productName}` }).click()
  const archiveProductDrawer = page.getByRole('dialog', { name: '编辑产品' })
  await choose(page, archiveProductDrawer, '状态', '已归档')
  await archiveProductDrawer.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('产品已更新')).toBeVisible()
  await page.getByRole('link', { name: productName }).click()
  await expect(page.getByText('该产品已归档，所有配置仅可查看。')).toBeVisible()
  await page.getByRole('tab', { name: '模块与功能' }).click()
  await expect(page.getByRole('button', { name: '新建模块' })).toHaveCount(0)
  await assertModuleTreeScroll(page)
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

async function seedScrollableModules(page: Page, productId: number, suffix: string) {
  await page.evaluate(async ({ productId, suffix }) => {
    const encodedCsrf = document.cookie.split('; ')
      .find(item => item.startsWith('XSRF-TOKEN='))
      ?.slice('XSRF-TOKEN='.length)
    if (!encodedCsrf) throw new Error('Missing XSRF-TOKEN while seeding scroll modules')

    for (let index = 1; index <= 18; index += 1) {
      const response = await fetch(`/api/v1/products/${productId}/modules`, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
          'X-XSRF-TOKEN': decodeURIComponent(encodedCsrf),
        },
        body: JSON.stringify({
          code: `SCROLL-${suffix}-${index}`,
          name: `滚动验证模块 ${String(index).padStart(2, '0')}`,
          status: 'PLANNING',
          sortOrder: index + 10,
          version: 0,
        }),
      })
      if (!response.ok) throw new Error(`Failed to seed scroll module ${index}: ${response.status}`)
    }
  }, { productId, suffix })

  await page.reload()
  await page.getByRole('tab', { name: '模块与功能' }).click()
  await expect(page.getByText(`SCROLL-${suffix}-18 · 滚动验证模块 18`, { exact: true })).toBeAttached()
}

async function assertModuleTreeScroll(page: Page) {
  const tree = page.getByTestId('product-module-tree-scroll')
  await expect(tree).toBeVisible()
  await expect.poll(() => tree.evaluate(element => element.scrollHeight > element.clientHeight)).toBe(true)
  await tree.evaluate(element => { element.scrollTop = element.scrollHeight })
  await expect.poll(() => tree.evaluate(element => element.scrollTop)).toBeGreaterThan(0)
}

async function choose(page: Page, scope: Page | Locator, label: string, option: string, search = false) {
  const select = scope.getByRole('combobox', { name: label }).last()
  const selectRoot = select.locator('xpath=ancestor::*[contains(concat(" ", normalize-space(@class), " "), " ant-select ")][1]')
  await expect(select).toBeEnabled()

  for (let attempt = 0; attempt < 4; attempt += 1) {
    if (await hasSelectedOption(selectRoot, option)) return

    try {
      await expect(selectRoot).not.toHaveClass(/ant-select-loading/, { timeout: 2_000 })
      if (await hasSelectedOption(selectRoot, option)) return
      if (search) await select.fill(option, { timeout: 2_000 })
      else await select.press('ArrowDown', { timeout: 2_000 })
      if (await hasSelectedOption(selectRoot, option)) return

      const optionRow = page.locator('.ant-select-dropdown:visible .ant-select-item-option')
        .filter({ hasText: new RegExp(`^${escapeRegExp(option)}$`) })
      await expect(optionRow).toBeVisible({ timeout: 2_000 })
      if (await hasSelectedOption(selectRoot, option)) return
      await optionRow.click({ timeout: 2_000 })
    } catch (error) {
      if (await hasSelectedOption(selectRoot, option)) return
      if (attempt === 3) throw error
      continue
    }

    if (await hasSelectedOption(selectRoot, option)) return
  }

  throw new Error(`Select "${label}" did not settle on option "${option}"`)
}

async function hasSelectedOption(selectRoot: Locator, option: string) {
  const selected = selectRoot.locator('.ant-select-selection-item').last()
  if (!await selected.count()) return false
  return ((await selected.getAttribute('title')) ?? (await selected.textContent()) ?? '').trim() === option
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
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
