import { afterEach, expect, it, vi } from 'vitest'
import { adminApi } from './adminApi'

afterEach(() => vi.unstubAllGlobals())

it('用正确的方法和载荷调用系统管理写接口', async () => {
  const fetch = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => Promise.resolve(new Response('{}', {
    status: 200, headers: { 'Content-Type': 'application/json' },
  })))
  vi.stubGlobal('fetch', fetch)

  await adminApi.saveUser(42, { displayName: '王工', roleCodes: ['TECH_MANAGER'] })
  await adminApi.saveRolePermissions(6, ['dashboard:read'])
  await adminApi.saveSettings({ platformName: '智鹿中台', environmentLabel: '验收', timezone: 'UTC', supportEmail: '', agentTimeoutMinutes: 45 })

  expect(fetch).toHaveBeenNthCalledWith(1, '/api/v1/admin/users/42', expect.objectContaining({
    method: 'PUT', body: JSON.stringify({ displayName: '王工', roleCodes: ['TECH_MANAGER'] }),
  }))
  expect(fetch).toHaveBeenNthCalledWith(2, '/api/v1/admin/roles/6/permissions', expect.objectContaining({
    method: 'PUT', body: JSON.stringify({ permissionCodes: ['dashboard:read'] }),
  }))
  expect(fetch).toHaveBeenNthCalledWith(3, '/api/v1/admin/settings', expect.objectContaining({
    method: 'PUT', body: expect.stringContaining('"agentTimeoutMinutes":45'),
  }))
})

it('审计检索只携带有值的分页和筛选参数', async () => {
  const fetch = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => Promise.resolve(new Response('{"items":[],"total":0}', {
    status: 200, headers: { 'Content-Type': 'application/json' },
  })))
  vi.stubGlobal('fetch', fetch)

  await adminApi.audits({ keyword: '', action: 'UPDATE', resourceType: undefined, page: 2, pageSize: 20 })

  expect(fetch.mock.calls[0][0]).toBe('/api/v1/admin/audit-logs?action=UPDATE&page=2&pageSize=20')
})

it('用相同草稿测试并保存 AI 服务配置', async () => {
  const fetch = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) => Promise.resolve(new Response('{}', {
    status: 200, headers: { 'Content-Type': 'application/json' },
  })))
  vi.stubGlobal('fetch', fetch)
  const input = {
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    model: 'qwen-plus',
    apiKey: 'sk-new',
  }

  await adminApi.testAiConfiguration(input)
  await adminApi.saveAiConfiguration(input)

  expect(fetch).toHaveBeenNthCalledWith(1, '/api/v1/admin/ai-service/config/test', expect.objectContaining({
    method: 'POST', body: JSON.stringify(input),
  }))
  expect(fetch).toHaveBeenNthCalledWith(2, '/api/v1/admin/ai-service/config', expect.objectContaining({
    method: 'PUT', body: JSON.stringify(input),
  }))
})

it('用户、团队和角色删除接口使用 DELETE 方法', async () => {
  const fetch = vi.fn((_input: RequestInfo | URL, _init?: RequestInit) =>
    Promise.resolve(new Response(null, { status: 204 })))
  vi.stubGlobal('fetch', fetch)

  await adminApi.deleteUser(11)
  await adminApi.deleteTeam(22)
  await adminApi.deleteRole(33)

  expect(fetch).toHaveBeenNthCalledWith(1, '/api/v1/admin/users/11', expect.objectContaining({ method: 'DELETE' }))
  expect(fetch).toHaveBeenNthCalledWith(2, '/api/v1/admin/teams/22', expect.objectContaining({ method: 'DELETE' }))
  expect(fetch).toHaveBeenNthCalledWith(3, '/api/v1/admin/roles/33', expect.objectContaining({ method: 'DELETE' }))
})
