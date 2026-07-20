-- Preserve the existing Outline hierarchy as an independent document tree.
-- Flyway cannot wrap MySQL DDL migrations, but all durable changes below are InnoDB DML.
-- An explicit transaction keeps the destructive capability rebuild atomic; temporary-table
-- creation and removal do not implicitly commit in MySQL.
SET AUTOCOMMIT = 0;

INSERT INTO product_document_node(
  product_id,parent_id,node_type,code,title,description,sort_order,outline_link_id)
SELECT m.product_id,NULL,'FOLDER',m.code,m.name,m.description,m.sort_order,l.id
FROM product_module m
JOIN product p ON p.id=m.product_id AND p.code='XBHG' AND p.name='消保合规'
JOIN outline_document_link l
  ON l.organization_id=p.organization_id
 AND l.business_key=CONCAT('PRODUCT:',m.product_id,':MODULE:',m.id)
WHERE m.parent_id IS NULL AND m.code LIKE 'DOC-%';

INSERT INTO product_document_node(
  product_id,parent_id,node_type,code,title,description,sort_order,outline_link_id)
SELECT m.product_id,pn.id,'FOLDER',m.code,m.name,m.description,m.sort_order,l.id
FROM product_module m
JOIN product p ON p.id=m.product_id AND p.code='XBHG' AND p.name='消保合规'
JOIN product_module pm ON pm.id=m.parent_id
JOIN product_document_node pn ON pn.product_id=m.product_id AND pn.code=pm.code
JOIN outline_document_link l
  ON l.organization_id=p.organization_id
 AND l.business_key=CONCAT('PRODUCT:',m.product_id,':MODULE:',m.id)
WHERE m.parent_id IS NOT NULL;

INSERT INTO product_document_node(
  product_id,parent_id,node_type,code,title,description,sort_order,outline_link_id)
SELECT f.product_id,pn.id,'DOCUMENT',f.code,f.name,f.description,f.id,f.outline_link_id
FROM product_feature f
JOIN product p ON p.id=f.product_id AND p.code='XBHG' AND p.name='消保合规'
JOIN product_module m ON m.id=f.module_id
JOIN product_document_node pn ON pn.product_id=f.product_id AND pn.code=m.code
WHERE f.outline_link_id IS NOT NULL;

CREATE TEMPORARY TABLE v20_feature_manifest (
  product_version_id BIGINT NOT NULL,
  submodule_code VARCHAR(96) NOT NULL,
  availability VARCHAR(24) NOT NULL,
  PRIMARY KEY (product_version_id,submodule_code)
);
INSERT INTO v20_feature_manifest(product_version_id,submodule_code,availability)
SELECT pvf.product_version_id,f.code,pvf.availability
FROM product_version_feature pvf
JOIN product_feature f ON f.id=pvf.product_feature_id
JOIN product p ON p.id=f.product_id AND p.code='XBHG' AND p.name='消保合规'
JOIN product_module m ON m.id=f.module_id AND m.code LIKE 'CAP-%';

CREATE TEMPORARY TABLE v20_expected_submodule (
  code VARCHAR(96) NOT NULL,
  PRIMARY KEY (code)
);
INSERT INTO v20_expected_submodule(code) VALUES
('RULE-LAW'),('RULE-INTERNAL'),('RULE-ORCHESTRATION'),
('TASK-INTAKE'),('TASK-MATERIAL'),('TASK-WORKFLOW'),
('AI-PARSE'),('AI-RISK'),('AI-EXPLAIN'),
('REVIEW-WORKBENCH'),('REVIEW-COLLAB'),('REVIEW-ESCALATION'),
('RECTIFY-MANAGE'),('RECTIFY-DIFF'),('RECTIFY-REMIND'),
('SPECIAL-PRODUCT'),('SPECIAL-MARKETING'),('SPECIAL-SALES'),('SPECIAL-COMPLAINT'),
('REPORT-GENERATE'),('REPORT-RISK'),('REPORT-VALUE'),
('INTEGRATION-IAM'),('INTEGRATION-BUSINESS'),('INTEGRATION-OPEN'),
('SECURITY-PERMISSION'),('SECURITY-DATA'),('SECURITY-AUDIT'),
('OPS-RULE'),('OPS-MODEL'),('OPS-SYSTEM');

-- Abort before destructive cleanup unless the generated structure is exactly the known dataset.
CREATE TEMPORARY TABLE v20_consumer_protection_guard (
  guard_key INT NOT NULL,
  CONSTRAINT v20_consumer_protection_guard_key UNIQUE (guard_key)
);
INSERT INTO v20_consumer_protection_guard(guard_key)
VALUES (1),(2),(3),(4),(5),(6),(7),(8),(9),(10),(11),(12);
INSERT INTO v20_consumer_protection_guard(guard_key)
SELECT 1 WHERE (SELECT COUNT(*) FROM product WHERE code='XBHG' AND name='消保合规')>1;
INSERT INTO v20_consumer_protection_guard(guard_key)
SELECT 2 WHERE EXISTS (SELECT 1 FROM product WHERE code='XBHG' AND name='消保合规')
  AND (SELECT COUNT(*) FROM product_module m JOIN product p ON p.id=m.product_id
  WHERE p.code='XBHG' AND p.name='消保合规')<>26;
INSERT INTO v20_consumer_protection_guard(guard_key)
SELECT 3 WHERE EXISTS (SELECT 1 FROM product WHERE code='XBHG' AND name='消保合规')
  AND (SELECT COUNT(*) FROM product_feature f JOIN product p ON p.id=f.product_id
  WHERE p.code='XBHG' AND p.name='消保合规')<>70;
INSERT INTO v20_consumer_protection_guard(guard_key)
SELECT 4 WHERE EXISTS (SELECT 1 FROM product WHERE code='XBHG' AND name='消保合规')
  AND (SELECT COUNT(*) FROM product_document_node n JOIN product p ON p.id=n.product_id
  WHERE p.code='XBHG' AND p.name='消保合规' AND n.node_type='FOLDER')<>26;
INSERT INTO v20_consumer_protection_guard(guard_key)
SELECT 5 WHERE EXISTS (SELECT 1 FROM product WHERE code='XBHG' AND name='消保合规')
  AND (SELECT COUNT(*) FROM product_document_node n JOIN product p ON p.id=n.product_id
  WHERE p.code='XBHG' AND p.name='消保合规' AND n.node_type='DOCUMENT')<>70;
INSERT INTO v20_consumer_protection_guard(guard_key)
SELECT 6 WHERE EXISTS (SELECT 1 FROM product WHERE code='XBHG' AND name='消保合规')
  AND (SELECT COUNT(*) FROM v20_feature_manifest)<>31;
INSERT INTO v20_consumer_protection_guard(guard_key)
SELECT 7 WHERE EXISTS (SELECT 1 FROM requirement_product_feature x JOIN product_feature f ON f.id=x.product_feature_id
  JOIN product p ON p.id=f.product_id WHERE p.code='XBHG' AND p.name='消保合规');
INSERT INTO v20_consumer_protection_guard(guard_key)
SELECT 8 WHERE EXISTS (SELECT 1 FROM standardization_debt d JOIN product_feature f ON f.id=d.converted_feature_id
  JOIN product p ON p.id=f.product_id WHERE p.code='XBHG' AND p.name='消保合规');
INSERT INTO v20_consumer_protection_guard(guard_key)
SELECT 9 WHERE EXISTS (SELECT 1 FROM product WHERE code='XBHG' AND name='消保合规')
  AND (SELECT COUNT(*) FROM product_feature f
  JOIN product p ON p.id=f.product_id AND p.code='XBHG' AND p.name='消保合规'
  JOIN product_module m ON m.id=f.module_id AND m.code LIKE 'CAP-%'
  JOIN v20_expected_submodule e ON e.code=f.code)<>31;

-- Promote ten business domains and convert the 31 coarse capability records into submodules.
UPDATE product_module SET parent_id=NULL
WHERE code LIKE 'CAP-%' AND product_id IN (
  SELECT id FROM product WHERE code='XBHG' AND name='消保合规'
);

INSERT INTO product_module(product_id,parent_id,code,name,description,status,sort_order)
SELECT f.product_id,f.module_id,f.code,f.name,f.description,'ACTIVE',f.id
FROM product_feature f
JOIN product p ON p.id=f.product_id AND p.code='XBHG' AND p.name='消保合规'
JOIN product_module m ON m.id=f.module_id AND m.code LIKE 'CAP-%';

DELETE FROM product_version_feature
WHERE product_feature_id IN (
  SELECT f.id FROM product_feature f JOIN product p ON p.id=f.product_id
  WHERE p.code='XBHG' AND p.name='消保合规'
);
UPDATE product_feature SET outline_link_id=NULL,source_template_id=NULL,source_template_revision=NULL
WHERE product_id IN (SELECT id FROM product WHERE code='XBHG' AND name='消保合规');
DELETE FROM product_feature
WHERE product_id IN (SELECT id FROM product WHERE code='XBHG' AND name='消保合规');
DELETE FROM product_module
WHERE product_id IN (SELECT id FROM product WHERE code='XBHG' AND name='消保合规')
  AND code LIKE 'SPRINT-%';
DELETE FROM product_module
WHERE product_id IN (SELECT id FROM product WHERE code='XBHG' AND name='消保合规')
  AND code LIKE 'DOC-%';

-- Build the atomic feature catalog.
INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'LAW-INGEST','法规采集入库','法规采集入库','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='RULE-LAW';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'LAW-VERSION','法规版本管理','法规版本管理','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='RULE-LAW';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'LAW-EFFECT','效力状态管理','效力状态管理','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='RULE-LAW';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'LAW-SCOPE','适用范围标注','适用范围标注','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='RULE-LAW';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'INTERNAL-INGEST','制度入库','制度入库','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='RULE-INTERNAL';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'INTERNAL-VERSION','制度版本管理','制度版本管理','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='RULE-INTERNAL';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'INTERNAL-MAP','法规内规映射','法规内规映射','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='RULE-INTERNAL';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'INTERNAL-OWNER','责任部门维护','责任部门维护','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='RULE-INTERNAL';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'RULE-CONFIG','审查规则配置','审查规则配置','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='RULE-ORCHESTRATION';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'RULE-TEST','规则测试','规则测试','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='RULE-ORCHESTRATION';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'RULE-PUBLISH','规则发布','规则发布','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='RULE-ORCHESTRATION';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'RULE-HISTORY','规则版本回溯','规则版本回溯','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='RULE-ORCHESTRATION';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'TASK-CREATE','任务创建','任务创建','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='TASK-INTAKE';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'TASK-CLASSIFY','任务分类分级','任务分类分级','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='TASK-INTAKE';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'TASK-ASSIGN','任务分派','任务分派','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='TASK-INTAKE';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'TASK-REOPEN','撤回与重开','撤回与重开','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='TASK-INTAKE';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'MATERIAL-UPLOAD','材料上传预览','材料上传预览','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='TASK-MATERIAL';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'MATERIAL-VERSION','材料版本管理','材料版本管理','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='TASK-MATERIAL';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'MATERIAL-CHECK','完整性校验','完整性校验','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='TASK-MATERIAL';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'MATERIAL-SUPPLEMENT','补充材料','补充材料','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='TASK-MATERIAL';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'FLOW-CONFIG','审批流配置','审批流配置','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='TASK-WORKFLOW';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'FLOW-TRANSFER','转办与加签','转办与加签','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='TASK-WORKFLOW';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'FLOW-URGE','催办与超时','催办与超时','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='TASK-WORKFLOW';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'FLOW-SLA','处理时限配置','处理时限配置','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='TASK-WORKFLOW';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'PARSE-OFFICE','Office/PDF 解析','Office/PDF 解析','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='AI-PARSE';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'PARSE-OCR','OCR 识别','OCR 识别','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='AI-PARSE';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'PARSE-TABLE','表格解析','表格解析','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='AI-PARSE';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'PARSE-FIELD','关键字段抽取','关键字段抽取','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='AI-PARSE';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'RISK-RULE','规则命中','规则命中','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='AI-RISK';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'RISK-SEMANTIC','语义风险识别','语义风险识别','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='AI-RISK';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'RISK-MERGE','风险合并去重','风险合并去重','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='AI-RISK';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'RISK-LEVEL','风险自动分级','风险自动分级','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='AI-RISK';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'EXPLAIN-LOCATE','原文定位','原文定位','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='AI-EXPLAIN';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'EXPLAIN-BASIS','规则依据说明','规则依据说明','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='AI-EXPLAIN';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'EXPLAIN-PROCESS','判断过程说明','判断过程说明','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='AI-EXPLAIN';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'EXPLAIN-SUGGEST','修改建议生成','修改建议生成','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='AI-EXPLAIN';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'REVIEW-CONFIRM','结论确认','结论确认','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='REVIEW-WORKBENCH';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'REVIEW-OVERRIDE','人工改判','人工改判','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='REVIEW-WORKBENCH';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'REVIEW-IGNORE','忽略并说明','忽略并说明','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='REVIEW-WORKBENCH';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'REVIEW-BATCH','批量复核','批量复核','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='REVIEW-WORKBENCH';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'COLLAB-ANNOTATE','原文批注','原文批注','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='REVIEW-COLLAB';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'COLLAB-PEOPLE','人员协作','人员协作','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='REVIEW-COLLAB';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'COLLAB-SUMMARY','意见汇总','意见汇总','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='REVIEW-COLLAB';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'COLLAB-RECORD','处理记录','处理记录','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='REVIEW-COLLAB';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'EXPERT-START','会审发起','会审发起','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='REVIEW-ESCALATION';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'EXPERT-ESCALATE','重大风险升级','重大风险升级','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='REVIEW-ESCALATION';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'EXPERT-OPINION','会审意见','会审意见','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='REVIEW-ESCALATION';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'EXPERT-DECISION','决策留痕','决策留痕','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='REVIEW-ESCALATION';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'RECTIFY-ASSIGN','问题指派','问题指派','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='RECTIFY-MANAGE';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'RECTIFY-FEEDBACK','整改反馈','整改反馈','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='RECTIFY-MANAGE';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'RECTIFY-REVIEW','复审','复审','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='RECTIFY-MANAGE';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'RECTIFY-CLOSE','关闭与重开','关闭与重开','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='RECTIFY-MANAGE';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'DIFF-MATERIAL','材料差异对比','材料差异对比','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='RECTIFY-DIFF';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'DIFF-ISSUE','问题前后对比','问题前后对比','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='RECTIFY-DIFF';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'DIFF-OPEN','未整改项识别','未整改项识别','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='RECTIFY-DIFF';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'DIFF-EXPORT','差异结果导出','差异结果导出','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='RECTIFY-DIFF';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'REMIND-DUE','到期提醒','到期提醒','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='RECTIFY-REMIND';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'REMIND-OVERDUE','逾期升级','逾期升级','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='RECTIFY-REMIND';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'REMIND-OWNER','责任追踪','责任追踪','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='RECTIFY-REMIND';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'REMIND-SLA','SLA 统计','SLA 统计','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='RECTIFY-REMIND';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'PRODUCT-ACCESS','产品准入审查','产品准入审查','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SPECIAL-PRODUCT';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'PRODUCT-FAIRNESS','条款公平性检查','条款公平性检查','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SPECIAL-PRODUCT';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'PRODUCT-FEE','收费检查','收费检查','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SPECIAL-PRODUCT';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'PRODUCT-DISCLOSE','信息披露检查','信息披露检查','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SPECIAL-PRODUCT';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'MARKETING-WORD','禁限用语识别','禁限用语识别','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SPECIAL-MARKETING';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'MARKETING-MISLEAD','夸大误导识别','夸大误导识别','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SPECIAL-MARKETING';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'MARKETING-DISCLOSE','风险揭示检查','风险揭示检查','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SPECIAL-MARKETING';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'MARKETING-REVIEW','宣传材料复核','宣传材料复核','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SPECIAL-MARKETING';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'SALES-SUITABILITY','适当性检查','适当性检查','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SPECIAL-SALES';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'SALES-DUTY','告知义务检查','告知义务检查','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SPECIAL-SALES';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'SALES-INDUCE','诱导销售识别','诱导销售识别','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SPECIAL-SALES';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'SALES-MATERIAL','销售材料复核','销售材料复核','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SPECIAL-SALES';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'COMPLAINT-CLASSIFY','投诉分类','投诉分类','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SPECIAL-COMPLAINT';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'COMPLAINT-EVENT','重大事件识别','重大事件识别','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SPECIAL-COMPLAINT';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'COMPLAINT-CAUSE','根因分析','根因分析','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SPECIAL-COMPLAINT';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'COMPLAINT-FEEDBACK','规则回流','规则回流','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SPECIAL-COMPLAINT';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'REPORT-CREATE','报告生成','报告生成','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='REPORT-GENERATE';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'REPORT-TEMPLATE','报告模板管理','报告模板管理','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='REPORT-GENERATE';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'REPORT-EXPORT','报告导出','报告导出','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='REPORT-GENERATE';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'REPORT-ARCHIVE','报告归档','报告归档','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='REPORT-GENERATE';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'BOARD-DISTRIBUTION','风险分布','风险分布','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='REPORT-RISK';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'BOARD-TREND','风险趋势','风险趋势','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='REPORT-RISK';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'BOARD-FREQUENT','高频问题','高频问题','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='REPORT-RISK';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'BOARD-ORG','组织对比','组织对比','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='REPORT-RISK';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'VALUE-EFFICIENCY','审查人效','审查人效','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='REPORT-VALUE';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'VALUE-ADOPTION','人工采纳率','人工采纳率','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='REPORT-VALUE';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'VALUE-RECTIFY','整改效果','整改效果','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='REPORT-VALUE';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'VALUE-ROI','ROI 分析','ROI 分析','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='REPORT-VALUE';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'IAM-SSO','SSO 登录','SSO 登录','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='INTEGRATION-IAM';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'IAM-ORG','组织同步','组织同步','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='INTEGRATION-IAM';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'IAM-USER','用户同步','用户同步','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='INTEGRATION-IAM';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'IAM-ROLE','角色映射','角色映射','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='INTEGRATION-IAM';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'BIZ-OA','OA/BPM 集成','OA/BPM 集成','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='INTEGRATION-BUSINESS';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'BIZ-PRODUCT','产品系统集成','产品系统集成','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='INTEGRATION-BUSINESS';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'BIZ-MARKETING','营销系统集成','营销系统集成','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='INTEGRATION-BUSINESS';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'BIZ-CONTRACT','合同系统集成','合同系统集成','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='INTEGRATION-BUSINESS';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'OPEN-API','API 管理','API 管理','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='INTEGRATION-OPEN';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'OPEN-EVENT','消息订阅','消息订阅','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='INTEGRATION-OPEN';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'OPEN-IMPORT','批量导入','批量导入','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='INTEGRATION-OPEN';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'OPEN-EXPORT','批量导出','批量导出','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='INTEGRATION-OPEN';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'PERMISSION-RBAC','RBAC','RBAC','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SECURITY-PERMISSION';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'PERMISSION-SCOPE','数据范围','数据范围','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SECURITY-PERMISSION';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'PERMISSION-DOWNLOAD','下载控制','下载控制','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SECURITY-PERMISSION';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'PERMISSION-WATERMARK','动态水印','动态水印','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SECURITY-PERMISSION';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'DATA-ENCRYPT','传输存储加密','传输存储加密','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SECURITY-DATA';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'DATA-MASK','敏感字段脱敏','敏感字段脱敏','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SECURITY-DATA';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'DATA-RETENTION','数据保留销毁','数据保留销毁','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SECURITY-DATA';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'DATA-MODEL','模型数据边界','模型数据边界','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SECURITY-DATA';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'AUDIT-OPERATION','操作日志','操作日志','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SECURITY-AUDIT';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'AUDIT-CONCLUSION','结论版本','结论版本','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SECURITY-AUDIT';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'AUDIT-RULE','规则变更记录','规则变更记录','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SECURITY-AUDIT';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'AUDIT-QUERY','审计查询','审计查询','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='SECURITY-AUDIT';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'OPS-RULE-HIT','规则命中监控','规则命中监控','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='OPS-RULE';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'OPS-RULE-QUALITY','规则质量评估','规则质量评估','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='OPS-RULE';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'OPS-RULE-CONFLICT','规则冲突检测','规则冲突检测','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='OPS-RULE';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'OPS-RULE-GRAY','灰度发布','灰度发布','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='OPS-RULE';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'OPS-MODEL-VERSION','模型版本管理','模型版本管理','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='OPS-MODEL';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'OPS-MODEL-DATASET','评测集管理','评测集管理','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='OPS-MODEL';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'OPS-MODEL-EVAL','质量评测','质量评测','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='OPS-MODEL';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'OPS-MODEL-FEEDBACK','人工反馈闭环','人工反馈闭环','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='OPS-MODEL';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'OPS-TASK','任务监控','任务监控','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='OPS-SYSTEM';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'OPS-ALERT','异常告警','异常告警','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='OPS-SYSTEM';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'OPS-LOG','运行日志','运行日志','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='OPS-SYSTEM';

INSERT INTO product_feature(product_id,module_id,code,name,description,status)
SELECT p.id,m.id,'OPS-REPORT','运营报表','运营报表','ACTIVE'
FROM product p JOIN product_module m ON m.product_id=p.id
WHERE p.code='XBHG' AND p.name='消保合规' AND m.code='OPS-SYSTEM';

INSERT INTO product_version_feature(product_version_id,product_feature_id,availability)
SELECT manifest.product_version_id,feature.id,manifest.availability
FROM v20_feature_manifest manifest
JOIN product_module module ON module.code=manifest.submodule_code
JOIN product product ON product.id=module.product_id
  AND product.code='XBHG' AND product.name='消保合规'
JOIN product_feature feature ON feature.product_id=product.id AND feature.module_id=module.id;

UPDATE product_version SET version=version+1,updated_at=current_timestamp
WHERE product_id IN (SELECT id FROM product WHERE code='XBHG' AND name='消保合规');

-- Abort and roll back if any INSERT ... SELECT silently produced fewer rows.
INSERT INTO v20_consumer_protection_guard(guard_key)
SELECT 10 WHERE EXISTS (SELECT 1 FROM product WHERE code='XBHG' AND name='消保合规')
  AND ((SELECT COUNT(*) FROM product_module m JOIN product p ON p.id=m.product_id
        WHERE p.code='XBHG' AND p.name='消保合规' AND m.parent_id IS NULL)<>10
    OR (SELECT COUNT(*) FROM product_module m JOIN product p ON p.id=m.product_id
        WHERE p.code='XBHG' AND p.name='消保合规' AND m.parent_id IS NOT NULL)<>31);
INSERT INTO v20_consumer_protection_guard(guard_key)
SELECT 11 WHERE EXISTS (SELECT 1 FROM product WHERE code='XBHG' AND name='消保合规')
  AND (SELECT COUNT(*) FROM product_feature f JOIN product p ON p.id=f.product_id
       WHERE p.code='XBHG' AND p.name='消保合规')<>124;
INSERT INTO v20_consumer_protection_guard(guard_key)
SELECT 12 WHERE EXISTS (SELECT 1 FROM product WHERE code='XBHG' AND name='消保合规')
  AND ((SELECT COUNT(*) FROM product_document_node n JOIN product p ON p.id=n.product_id
        WHERE p.code='XBHG' AND p.name='消保合规' AND n.parent_id IS NULL)<>11
    OR (SELECT COUNT(*) FROM product_document_node n JOIN product p ON p.id=n.product_id
        WHERE p.code='XBHG' AND p.name='消保合规' AND n.node_type='FOLDER')<>26
    OR (SELECT COUNT(*) FROM product_document_node n JOIN product p ON p.id=n.product_id
        WHERE p.code='XBHG' AND p.name='消保合规' AND n.node_type='DOCUMENT')<>70
    OR (SELECT COUNT(*) FROM product_version_feature pvf JOIN product_version pv ON pv.id=pvf.product_version_id
        JOIN product p ON p.id=pv.product_id WHERE p.code='XBHG' AND p.name='消保合规')<>124
    OR (SELECT COUNT(*) FROM product_version_feature pvf JOIN product_version pv ON pv.id=pvf.product_version_id
        JOIN product p ON p.id=pv.product_id WHERE p.code='XBHG' AND p.name='消保合规'
        AND pvf.availability='INCLUDED')<>8);

COMMIT;
SET AUTOCOMMIT = 1;
