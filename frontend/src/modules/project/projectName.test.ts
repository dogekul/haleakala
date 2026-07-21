import { buildProjectName } from './projectName'

it('builds a complete project name from customer product and version', () => {
  expect(buildProjectName('华东银行', '企业财务云', 'V5.0'))
    .toBe('华东银行 - 企业财务云 V5.0 实施项目')
})

it('does not build a partial project name', () => {
  expect(buildProjectName('华东银行', '企业财务云', undefined)).toBeUndefined()
})
