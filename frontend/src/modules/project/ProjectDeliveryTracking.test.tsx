import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AuthContext, type AuthState } from '../../app/AuthProvider'
import { ProjectDeliveryTracking } from './ProjectDeliveryTracking'
import type { Project } from './types'

const project = {
  id: 9, code: '9', name: '消保合规交付', members: [
    { userId: 7, displayName: '张宁' }, { userId: 8, displayName: '李四' },
  ],
} as unknown as Project

const auth: AuthState = {
  loading: false,
  me: { id: 7, organizationId: 1, username: 'manager', displayName: '张宁',
    roles: ['PROJECT_MANAGER'], permissions: ['project:read', 'project:write'] },
  login: vi.fn(), logout: vi.fn(), refresh: vi.fn(),
}

const rows = [{
  id: 1, itemCode: '9-001', originalRequest: '工作台增加风险提示',
  classification: 'ENHANCEMENT', deliveryEnd: 'B', featurePoint: '风险提示卡片',
  complexity: 'M', dependencyStatus: 'GAP', dependencyNote: 'GAP-12', estimatedDays: 3,
  actualDays: null, deviationPercent: null, reusableLevel: 'SEED', status: 'BLOCKED',
  ownerUserId: 8, ownerName: '李四', version: 0,
}]

it('以汇总、筛选和抽屉表单维护项目交付执行项', async () => {
  let posted: Record<string, unknown> | undefined
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    if (path === '/api/v1/projects/9/delivery-tracking' && !init?.method) {
      return Promise.resolve(new Response(JSON.stringify(rows), { status: 200 }))
    }
    if (path === '/api/v1/projects/9/delivery-tracking' && init?.method === 'POST') {
      posted = JSON.parse(String(init.body))
      return Promise.resolve(new Response(JSON.stringify({ ...rows[0], id: 2, itemCode: '9-002', ...posted }), { status: 201 }))
    }
    throw new Error(`unexpected request: ${path}`)
  }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><AuthContext.Provider value={auth}>
    <ProjectDeliveryTracking project={project} />
  </AuthContext.Provider></QueryClientProvider>)

  expect(await screen.findByText('工作台增加风险提示')).toBeVisible()
  expect(screen.getByText('受阻 / 缺口').closest('.ant-statistic')).toHaveTextContent('1项')

  const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /新增执行项/ }))
  const drawer = screen.getByRole('dialog', { name: '新增交付执行项' })
  await user.type(within(drawer).getByRole('textbox', { name: '甲方需求原始表述' }), '新增还款计划配置')
  await user.type(within(drawer).getByRole('textbox', { name: '功能点' }), '还款计划')
  await choose(user, drawer, '四分类', '① 配置')
  await choose(user, drawer, '端', 'C端')
  await choose(user, drawer, '复杂度', 'S（≤1人天）')
  await user.clear(within(drawer).getByRole('spinbutton', { name: '预估人天' }))
  await user.type(within(drawer).getByRole('spinbutton', { name: '预估人天' }), '0.5')
  await choose(user, drawer, '负责人', '张宁')
    await user.click(within(drawer).getByRole('button', { name: /保\s*存/ }))

  await waitFor(() => expect(posted).toEqual(expect.objectContaining({
    originalRequest: '新增还款计划配置', classification: 'CONFIGURATION', deliveryEnd: 'C',
    featurePoint: '还款计划', complexity: 'S', estimatedDays: 0.5, ownerUserId: 7,
  })))
})

async function choose(user: ReturnType<typeof userEvent.setup>, drawer: HTMLElement, label: string, option: string) {
  await user.click(within(drawer).getByRole('combobox', { name: label }))
  await user.click(await screen.findByRole('option', { name: option }))
}
