import { apiPath } from './apiPath'

it('在子路径部署时给 API 路径添加应用前缀', () => {
  expect(apiPath('/api/v1/projects', '/zhilu/')).toBe('/zhilu/api/v1/projects')
  expect(apiPath('/api/v1/projects', '/')).toBe('/api/v1/projects')
})
