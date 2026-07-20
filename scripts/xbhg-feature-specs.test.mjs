import assert from 'node:assert/strict';
import test from 'node:test';
import {
  FEATURE_DEFINITIONS,
  generateFeatureSpec,
  parseArguments,
  validateFeatureSpec,
} from './xbhg-feature-specs.mjs';

const EXPECTED_CODES = `
LAW-INGEST LAW-VERSION LAW-EFFECT LAW-SCOPE
INTERNAL-INGEST INTERNAL-VERSION INTERNAL-MAP INTERNAL-OWNER
RULE-CONFIG RULE-TEST RULE-PUBLISH RULE-HISTORY
TASK-CREATE TASK-CLASSIFY TASK-ASSIGN TASK-REOPEN
MATERIAL-UPLOAD MATERIAL-VERSION MATERIAL-CHECK MATERIAL-SUPPLEMENT
FLOW-CONFIG FLOW-TRANSFER FLOW-URGE FLOW-SLA
PARSE-OFFICE PARSE-OCR PARSE-TABLE PARSE-FIELD
RISK-RULE RISK-SEMANTIC RISK-MERGE RISK-LEVEL
EXPLAIN-LOCATE EXPLAIN-BASIS EXPLAIN-PROCESS EXPLAIN-SUGGEST
REVIEW-CONFIRM REVIEW-OVERRIDE REVIEW-IGNORE REVIEW-BATCH
COLLAB-ANNOTATE COLLAB-PEOPLE COLLAB-SUMMARY COLLAB-RECORD
EXPERT-START EXPERT-ESCALATE EXPERT-OPINION EXPERT-DECISION
RECTIFY-ASSIGN RECTIFY-FEEDBACK RECTIFY-REVIEW RECTIFY-CLOSE
DIFF-MATERIAL DIFF-ISSUE DIFF-OPEN DIFF-EXPORT
REMIND-DUE REMIND-OVERDUE REMIND-OWNER REMIND-SLA
PRODUCT-ACCESS PRODUCT-FAIRNESS PRODUCT-FEE PRODUCT-DISCLOSE
MARKETING-WORD MARKETING-MISLEAD MARKETING-DISCLOSE MARKETING-REVIEW
SALES-SUITABILITY SALES-DUTY SALES-INDUCE SALES-MATERIAL
COMPLAINT-CLASSIFY COMPLAINT-EVENT COMPLAINT-CAUSE COMPLAINT-FEEDBACK
REPORT-CREATE REPORT-TEMPLATE REPORT-EXPORT REPORT-ARCHIVE
BOARD-DISTRIBUTION BOARD-TREND BOARD-FREQUENT BOARD-ORG
VALUE-EFFICIENCY VALUE-ADOPTION VALUE-RECTIFY VALUE-ROI
IAM-SSO IAM-ORG IAM-USER IAM-ROLE
BIZ-OA BIZ-PRODUCT BIZ-MARKETING BIZ-CONTRACT
OPEN-API OPEN-EVENT OPEN-IMPORT OPEN-EXPORT
PERMISSION-RBAC PERMISSION-SCOPE PERMISSION-DOWNLOAD PERMISSION-WATERMARK
DATA-ENCRYPT DATA-MASK DATA-RETENTION DATA-MODEL
AUDIT-OPERATION AUDIT-CONCLUSION AUDIT-RULE AUDIT-QUERY
OPS-RULE-HIT OPS-RULE-QUALITY OPS-RULE-CONFLICT OPS-RULE-GRAY
OPS-MODEL-VERSION OPS-MODEL-DATASET OPS-MODEL-EVAL OPS-MODEL-FEEDBACK
OPS-TASK OPS-ALERT OPS-LOG OPS-REPORT
`.trim().split(/\s+/);

function context(code) {
  const definition = FEATURE_DEFINITIONS.get(code);
  return {
    productId: 102,
    productCode: 'XBHG',
    productName: '消保合规',
    featureId: 1000,
    code,
    name: definition.name,
    moduleName: definition.module,
    rootModuleName: definition.domain,
    description: definition.purpose,
    definition,
  };
}

test('defines exactly the 124 active XBHG features', () => {
  assert.equal(FEATURE_DEFINITIONS.size, 124);
  assert.deepEqual([...FEATURE_DEFINITIONS.keys()].sort(), [...EXPECTED_CODES].sort());
});

test('all definitions generate complete and feature-specific specs', () => {
  const bodies = new Set();
  for (const code of EXPECTED_CODES) {
    const specContext = context(code);
    const markdown = generateFeatureSpec(specContext);
    assert.deepEqual(validateFeatureSpec(markdown, specContext), [], code);
    assert.match(markdown, new RegExp(`\\b${code}\\b`));
    assert.ok(markdown.includes(specContext.definition.control), code);
    bodies.add(markdown.replaceAll(code, 'CODE').replaceAll(specContext.name, '功能'));
  }
  assert.equal(bodies.size, 124, '每个功能正文都应包含独有的业务内容');
});

test('representative specs contain domain-real controls', () => {
  assert.match(generateFeatureSpec(context('LAW-INGEST')), /发文机关.*发布日期.*原文哈希/);
  assert.match(generateFeatureSpec(context('RISK-SEMANTIC')), /置信度.*误报.*人工复核/);
  assert.match(generateFeatureSpec(context('PRODUCT-FAIRNESS')), /格式条款.*权利义务.*显失公平/);
  assert.match(generateFeatureSpec(context('IAM-SSO')), /SAML|OIDC/);
  assert.match(generateFeatureSpec(context('DATA-RETENTION')), /保留期限.*销毁审批.*不可恢复/);
  assert.match(generateFeatureSpec(context('OPS-MODEL-EVAL')), /精确率.*召回率.*基准集/);
  assert.match(generateFeatureSpec(context('REPORT-ARCHIVE')), /归档编号.*保管期限.*只读/);
});

test('validator rejects incomplete or placeholder content', () => {
  const specContext = context('LAW-INGEST');
  const valid = generateFeatureSpec(specContext);
  assert.ok(validateFeatureSpec(valid.replace('## 13. 验收标准', '## 验收')).some((item) => item.includes('章节')));
  assert.ok(validateFeatureSpec(valid.replace(/^- BR-0[5-8].*$/gm, '- 缺失规则'), specContext)
      .some((item) => item.includes('业务规则')));
  assert.ok(validateFeatureSpec(valid.replace(/^- AC-0[6-8].*$/gm, '- 缺失验收'), specContext)
      .some((item) => item.includes('验收标准')));
  assert.ok(validateFeatureSpec(`${valid}\nTODO`, specContext).some((item) => item.includes('占位')));
});

test('validator accepts Outline-normalized unordered list markers', () => {
  const specContext = context('LAW-INGEST');
  const normalized = generateFeatureSpec(specContext).replace(/^- (BR|AC)/gm, '* $1');
  assert.deepEqual(validateFeatureSpec(normalized, specContext), []);
});

test('parses a precise feature retry list', () => {
  const options = parseArguments([
    '--base-url=http://localhost:8082', '--product-id=102',
    '--codes=REMIND-DUE,REMIND-OWNER,REPORT-CREATE,OPEN-EVENT',
  ]);
  assert.deepEqual(options.codes, ['REMIND-DUE', 'REMIND-OWNER', 'REPORT-CREATE', 'OPEN-EVENT']);
});
