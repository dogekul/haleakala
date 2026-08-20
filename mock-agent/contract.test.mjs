import assert from 'node:assert/strict'
import test from 'node:test'
import { buildArtifacts, supportedSkills } from './contract.mjs'

test('supports one skill for every corrected project stage', () => {
  assert.equal(supportedSkills.size, 7)
  assert.equal(supportedSkills.has('deliver-test'), true)
})

test('returns one completed project-document artifact per input document', () => {
  const artifacts = buildArtifacts('deliver-test', {
    name: '广州银行消保审查项目',
    customer_name: '广州银行',
    product_name: '消保审查',
    version_name: 'V1.0',
    documents: [{
      projectDocumentId: 4230,
      expectedRevision: 7,
      title: '功能性测试报告',
      markdown: '# 功能性测试报告\n\n## 测试目标\n请填写\n\n## 风险与遗留\n待补充',
    }],
  })

  assert.equal(artifacts.length, 1)
  assert.equal(artifacts[0].artifactType, 'PROJECT_DOCUMENT')
  assert.equal(artifacts[0].projectDocumentId, 4230)
  assert.equal(artifacts[0].expectedRevision, 7)
  assert.equal(artifacts[0].title, '功能性测试报告')
  assert.equal(artifacts[0].name, '功能性测试报告')
  assert.match(artifacts[0].content, /广州银行/)
  assert.match(artifacts[0].content, /消保审查 V1\.0/)
  assert.match(artifacts[0].content, /开发与测试/)
  assert.doesNotMatch(artifacts[0].content, /请填写|待填写|请补充|待补充|TODO|TBD/i)
})

test('keeps the generic artifact contract when documents are absent', () => {
  const artifacts = buildArtifacts('deliver-init', {
    name: '示例项目', customer_name: '示例客户', product_name: '示例产品',
  })

  assert.equal(artifacts.length, 1)
  assert.equal(artifacts[0].artifactType, 'AGENT_OUTPUT')
  assert.equal(artifacts[0].name, 'deliver-init-result.md')
  assert.match(artifacts[0].content, /示例项目/)
})

test('treats an explicit empty documents list as a document job with no targets', () => {
  assert.deepEqual(buildArtifacts('deliver-init', { documents: [] }), [])
})

test('rejects an invalid project-document contract instead of emitting an unsafe draft', () => {
  assert.throws(() => buildArtifacts('deliver-test', {
    documents: [{ title: '功能性测试报告', markdown: '请填写' }],
  }), /projectDocumentId 或 expectedRevision/)
})
