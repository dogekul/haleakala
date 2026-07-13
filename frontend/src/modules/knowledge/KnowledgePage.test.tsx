import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { KnowledgePage } from './KnowledgePage'

it('聚合最佳实践、代码片段和培训材料', async () => {
  vi.stubGlobal('fetch', vi.fn((input: string) => {
    const body = input.includes('/api/v1/knowledge?') ? [
      { id: 1, type: 'CASE', title: '月末关账提速', summary: '将关账周期从三天缩短到一天', content: '实践正文', tags: '财务,关账', productName: '企业财务', versionName: 'V5', status: 'PUBLISHED', ownerName: '方案专家', version: 1 },
      { id: 2, type: 'CODE', title: '对账幂等重试', summary: '受控扩展点参考', content: '使用说明', tags: 'Java,对账', language: 'Java', codeText: 'retryOnce(businessKey);', status: 'PUBLISHED', ownerName: '开发专家', version: 1 },
      { id: 3, type: 'TRAINING', title: '交付经理训练营', summary: '七阶段门禁实操', content: '课程大纲', tags: '交付,培训', audience: '交付经理', durationMinutes: 90, status: 'DRAFT', ownerName: '方案专家', version: 0 },
    ] : []
    return Promise.resolve({ ok: true, json: async () => body })
  }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><MemoryRouter><KnowledgePage /></MemoryRouter></QueryClientProvider>)
  await waitFor(() => expect(screen.getByText('月末关账提速')).toBeVisible())
  for (const tab of ['全部知识', '最佳实践', '代码片段', '培训材料']) expect(screen.getByText(tab)).toBeVisible()
  await userEvent.click(screen.getByText('代码片段'))
  expect(await screen.findByText('retryOnce(businessKey);')).toBeVisible()
  vi.unstubAllGlobals()
})
