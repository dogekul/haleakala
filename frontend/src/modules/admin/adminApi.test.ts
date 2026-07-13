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
  await adminApi.saveProduct(undefined, { code: 'ERP', name: '智鹿 ERP' })
  await adminApi.saveVersion(8, 9, { versionName: 'V2.1', status: 'ACTIVE' })
  await adminApi.saveSettings({ platformName: '智鹿中台', environmentLabel: '验收', timezone: 'UTC', supportEmail: '', agentTimeoutMinutes: 45 })

  expect(fetch).toHaveBeenNthCalledWith(1, '/api/v1/admin/users/42', expect.objectContaining({
    method: 'PUT', body: JSON.stringify({ displayName: '王工', roleCodes: ['TECH_MANAGER'] }),
  }))
  expect(fetch).toHaveBeenNthCalledWith(2, '/api/v1/admin/roles/6/permissions', expect.objectContaining({
    method: 'PUT', body: JSON.stringify({ permissionCodes: ['dashboard:read'] }),
  }))
  expect(fetch).toHaveBeenNthCalledWith(3, '/api/v1/products', expect.objectContaining({ method: 'POST' }))
  expect(fetch).toHaveBeenNthCalledWith(4, '/api/v1/products/8/versions/9', expect.objectContaining({ method: 'PUT' }))
  expect(fetch).toHaveBeenNthCalledWith(5, '/api/v1/admin/settings', expect.objectContaining({
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
