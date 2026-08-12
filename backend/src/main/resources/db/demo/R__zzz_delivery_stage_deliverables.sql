-- The 25 templates below are the complete project-stage checklist.
-- Remove every previous stage template and project copy so the two sets cannot coexist.

DROP TEMPORARY TABLE IF EXISTS obsolete_stage_template;
CREATE TEMPORARY TABLE obsolete_stage_template(id BIGINT PRIMARY KEY);
INSERT INTO obsolete_stage_template(id)
SELECT k.id
FROM knowledge_item k
JOIN document_template_config c ON c.knowledge_item_id=k.id
WHERE k.organization_id=100
  AND c.stage_code IN ('START','REQUIREMENT','CUSTOM_DEV','GO_LIVE',
    'TRIAL_HANDOVER','STANDARDIZATION','CLOSE')
  AND k.id NOT BETWEEN 4200 AND 4264;

DROP TEMPORARY TABLE IF EXISTS obsolete_stage_link;
CREATE TEMPORARY TABLE obsolete_stage_link(id BIGINT PRIMARY KEY);
INSERT IGNORE INTO obsolete_stage_link(id)
SELECT pd.outline_link_id
FROM project_document pd
JOIN delivery_project p ON p.id=pd.project_id
WHERE p.organization_id=100 AND pd.source_template_id IN (
  SELECT id FROM obsolete_stage_template) AND pd.outline_link_id IS NOT NULL;
INSERT IGNORE INTO obsolete_stage_link(id)
SELECT k.outline_link_id FROM knowledge_item k
WHERE k.id IN (SELECT id FROM obsolete_stage_template) AND k.outline_link_id IS NOT NULL;

DELETE j FROM document_job j
JOIN obsolete_stage_template old ON old.id=j.business_id
WHERE j.organization_id=100 AND j.job_type='KNOWLEDGE_MIGRATION';
DELETE pd FROM project_document pd
JOIN delivery_project p ON p.id=pd.project_id
WHERE p.organization_id=100 AND pd.source_template_id IN (
  SELECT id FROM obsolete_stage_template);
DELETE FROM outline_document_link
WHERE id IN (SELECT id FROM obsolete_stage_link);
DELETE c FROM document_template_config c
JOIN obsolete_stage_template old ON old.id=c.knowledge_item_id;
DELETE k FROM knowledge_item k
JOIN obsolete_stage_template old ON old.id=k.id;

INSERT IGNORE INTO knowledge_item(
  id,organization_id,type,title,summary,content_text,tags_text,visibility,status,
  owner_user_id,published_at
) VALUES
  (4200,100,'TEMPLATE','项目立项登记表','项目立项关键卡点',
   '# 项目立项登记表\n\n> 用于登记项目立项依据与基本信息。\n\n## 项目基本信息\n- 客户及项目名称：请填写\n- 产品及版本：请填写\n- 项目负责人：请填写\n- 计划周期：请填写\n\n## 立项依据\n- 商机及合同背景：请填写\n- 战略价值与预期目标：请填写\n- 交付范围及边界：请填写\n\n## 前置条件与风险\n- 前置条件：请填写\n- 主要风险与应对：请填写\n- 立项结论：请填写','项目,立项,门禁','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4201,100,'TEMPLATE','项目立项评审纪要','项目立项评审关键卡点',
   '# 项目立项评审纪要\n\n> 用于固化部门立项评审结论。\n\n## 会议信息\n- 时间及地点：请填写\n- 主持人与参会人：请填写\n\n## 评审内容\n- 项目目标及范围评审：请填写\n- 资源与计划评审：请填写\n- 风险与约束评审：请填写\n\n## 结论与行动项\n- 评审结论：请填写\n- 领导要求：请填写\n- 行动项、责任人及期限：请填写','项目,立项,评审','ORGANIZATION','PUBLISHED',100,current_timestamp),

  (4210,100,'TEMPLATE','项目调研计划','调研与启动关键过程',
   '# 项目调研计划\n\n> 用于规划业务与技术调研。\n\n## 调研目标与范围\n请填写\n\n## 访谈对象与议题\n请填写\n\n## 日程、责任人与输出物\n请填写\n\n## 客户确认\n请填写','项目,调研,计划','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4211,100,'TEMPLATE','业务基础调研表','业务基础调研关键过程',
   '# 业务基础调研表\n\n> 用于记录客户业务现状。\n\n## 组织、角色与职责\n请填写\n\n## 业务流程与规则\n请填写\n\n## 业务数据与指标\n请填写\n\n## 痛点、诉求与优先级\n请填写','项目,业务调研','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4212,100,'TEMPLATE','技术调研表','技术调研关键过程',
   '# 技术调研表\n\n> 用于记录技术环境及对接条件。\n\n## 基础设施与部署环境\n请填写\n\n## 网络、安全与权限\n请填写\n\n## 系统、接口与数据\n请填写\n\n## 技术风险及待办\n请填写','项目,技术调研','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4213,100,'TEMPLATE','功能执行跟踪表','产品执行跟踪关键过程',
   '# 功能执行跟踪表\n\n> 与项目“交付执行跟踪”数据配套归档。\n\n## 功能范围与分类结论\n请填写\n\n## 负责人、计划与当前状态\n请填写\n\n## 产品依赖及差距\n请填写\n\n## 风险、问题与结论\n请填写','项目,功能,执行跟踪','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4214,100,'TEMPLATE','运营执行跟踪表','运营方案执行关键过程',
   '# 运营执行跟踪表\n\n> 用于跟踪客户运营方案。\n\n## 运营目标与指标\n请填写\n\n## 运营事项、责任人与计划\n请填写\n\n## 执行进展与数据\n请填写\n\n## 问题及后续行动\n请填写','项目,运营,执行跟踪','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4215,100,'TEMPLATE','项目实施计划','项目启动关键卡点',
   '# 项目实施计划\n\n> 用于形成里程碑与交付版本规划。\n\n## 目标、范围与交付策略\n请填写\n\n## 阶段、里程碑与版本计划\n请填写\n\n## 资源与职责\n请填写\n\n## 风险、依赖及基线确认\n请填写','项目,实施计划,门禁','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4216,100,'TEMPLATE','项目管理计划','项目启动关键卡点',
   '# 项目管理计划\n\n> 用于明确项目治理与管理机制。\n\n## 组织架构与职责\n请填写\n\n## 会议、汇报与升级机制\n请填写\n\n## 范围、进度、质量与变更机制\n请填写\n\n## 风险、问题与文档管理机制\n请填写','项目,管理计划,门禁','ORGANIZATION','PUBLISHED',100,current_timestamp),

  (4220,100,'TEMPLATE','产品设计方案','需求澄清及方案评审关键过程',
   '# 产品设计方案\n\n> 用于归类标准功能与定制功能并固化产品设计。\n\n## 需求基线与设计目标\n请填写\n\n## 标准功能适配方案\n请填写\n\n## 定制功能及交互设计\n请填写\n\n## 数据、接口、权限与验收标准\n请填写\n\n## 评审结论\n请填写','项目,产品设计,方案','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4221,100,'TEMPLATE','系统实施方案','系统实施关键过程',
   '# 系统实施方案\n\n> 用于固化部署实施及系统对接方案。\n\n## 总体架构与部署拓扑\n请填写\n\n## 环境、网络与安全方案\n请填写\n\n## 接口、数据迁移与初始化方案\n请填写\n\n## 实施步骤、验证及回退方案\n请填写','项目,系统实施,方案','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4222,100,'TEMPLATE','项目实施 WBS','任务拆解关键过程',
   '# 项目实施 WBS\n\n> 用于把任务落实到人、时间落实到天。\n\n## 工作包与交付物\n请填写\n\n## 任务、负责人及协作人\n请填写\n\n## 起止时间、工期与依赖\n请填写\n\n## 里程碑、风险及基线确认\n请填写','项目,WBS,计划','ORGANIZATION','PUBLISHED',100,current_timestamp),

  (4230,100,'TEMPLATE','功能性测试报告','功能测试归档门禁',
   '# 功能性测试报告\n\n> 用于归档功能测试范围与结果。\n\n## 测试版本、环境与范围\n请填写\n\n## 用例执行与通过率\n请填写\n\n## 缺陷、遗留问题及影响\n请填写\n\n## 测试结论与签署\n请填写','项目,功能测试,报告','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4231,100,'TEMPLATE','性能测试报告','性能测试归档门禁',
   '# 性能测试报告\n\n> 用于归档性能指标、场景与结论。\n\n## 测试目标、环境与工具\n请填写\n\n## 场景、数据量与指标\n请填写\n\n## 测试结果与瓶颈分析\n请填写\n\n## 优化建议与结论\n请填写','项目,性能测试,报告','ORGANIZATION','PUBLISHED',100,current_timestamp),

  (4240,100,'TEMPLATE','版本验收报告','客户验证版本关键卡点',
   '# 版本验收报告\n\n> 用于记录迭代版本验收与闭环确认。\n\n## 验收版本、范围与依据\n请填写\n\n## 验收场景与结果\n请填写\n\n## 问题、遗留项与关闭计划\n请填写\n\n## 验收结论与客户确认\n请填写','项目,版本验收,门禁','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4241,100,'TEMPLATE','客户验收确认邮件','客户验收确认关键卡点',
   '# 客户验收确认邮件\n\n> 用于形成可发送并归档的客户确认邮件。\n\n## 收件人、抄送人与主题\n请填写\n\n## 本次验收范围与结果\n请填写\n\n## 遗留事项及双方约定\n请填写\n\n## 客户明确确认内容\n请填写','项目,客户验收,邮件','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4242,100,'TEMPLATE','上线发布公告','上线发布关键卡点',
   '# 上线发布公告\n\n> 用于明确交付内容、业务价值及后续验收依据。\n\n## 发布版本、时间与范围\n请填写\n\n## 新增及变更内容\n请填写\n\n## 用户影响、操作指引与支持入口\n请填写\n\n## 风险、回退及发布结论\n请填写','项目,上线,发布公告','ORGANIZATION','PUBLISHED',100,current_timestamp),

  (4250,100,'TEMPLATE','项目验收问题跟进表','项目验收关键过程',
   '# 项目验收问题跟进表\n\n> 用于登记验收问题及客户诉求并闭环。\n\n## 问题编号、现象与来源\n请填写\n\n## 影响、优先级与责任人\n请填写\n\n## 处理方案、计划与状态\n请填写\n\n## 验证结果与关闭依据\n请填写','项目,验收,问题跟进','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4251,100,'TEMPLATE','项目验收报告','项目验收关键卡点',
   '# 项目验收报告\n\n> 用于固化项目完成交付及客户验收结论。\n\n## 验收依据、范围与交付物\n请填写\n\n## 功能、性能、数据与文档验收结果\n请填写\n\n## 遗留问题及双方约定\n请填写\n\n## 验收结论、签署人与日期\n请填写','项目,验收,报告,门禁','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4252,100,'TEMPLATE','项目总结报告','项目总结归档门禁',
   '# 项目总结报告\n\n> 用于沉淀项目目标达成及经验。\n\n## 项目概况与目标达成\n请填写\n\n## 进度、质量、成本与客户评价\n请填写\n\n## 关键成果、问题与经验教训\n请填写\n\n## 后续运营及改进建议\n请填写','项目,总结,复盘','ORGANIZATION','PUBLISHED',100,current_timestamp),

  (4260,100,'TEMPLATE','项目风险及问题登记表','全过程风险问题关键过程',
   '# 项目风险及问题登记表\n\n> 用于全程登记并闭环风险与问题。\n\n## 风险或问题描述及来源\n请填写\n\n## 概率、影响与等级\n请填写\n\n## 责任人、措施与期限\n请填写\n\n## 状态、验证与关闭结论\n请填写','项目,风险,问题','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4261,100,'TEMPLATE','项目变更记录表','全过程变更关键过程',
   '# 项目变更记录表\n\n> 用于记录范围、方案、计划及资源变更。\n\n## 变更背景、内容与原因\n请填写\n\n## 对范围、进度、成本及质量的影响\n请填写\n\n## 评审、审批与生效时间\n请填写\n\n## 执行结果与基线更新\n请填写','项目,变更,记录','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4262,100,'TEMPLATE','项目周例会会议纪要','项目周例会关键过程',
   '# 项目周例会会议纪要\n\n> 用于每周进度与协作对齐。\n\n## 时间、参会人与会议目标\n请填写\n\n## 上周进展与本周计划\n请填写\n\n## 风险、问题与变更\n请填写\n\n## 决议、行动项、责任人与期限\n请填写','项目,周例会,纪要','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4263,100,'TEMPLATE','项目周报','项目周报关键过程',
   '# 项目周报\n\n> 用于每周向客户同步项目情况。\n\n## 本周总体状态与摘要\n请填写\n\n## 已完成事项与交付物\n请填写\n\n## 下周计划与里程碑\n请填写\n\n## 风险、问题及需客户协同事项\n请填写','项目,周报,客户同步','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4264,100,'TEMPLATE','项目阶段复盘报告','阶段复盘关键过程',
   '# 项目阶段复盘报告\n\n> 用于形成阶段性问题及后续措施。\n\n## 阶段目标与完成情况\n请填写\n\n## 成果、偏差与原因分析\n请填写\n\n## 经验、教训与改进措施\n请填写\n\n## 后续行动、责任人与期限\n请填写','项目,阶段复盘,报告','ORGANIZATION','PUBLISHED',100,current_timestamp);

INSERT IGNORE INTO document_template_config(
  knowledge_item_id,stage_code,requirement,enabled,condition_code,published_revision,
  published_title_snapshot,published_markdown_snapshot
)
SELECT k.id,
  CASE
    WHEN k.id BETWEEN 4200 AND 4209 THEN 'START'
    WHEN k.id BETWEEN 4210 AND 4219 THEN 'REQUIREMENT'
    WHEN k.id BETWEEN 4220 AND 4229 THEN 'CUSTOM_DEV'
    WHEN k.id BETWEEN 4230 AND 4239 THEN 'GO_LIVE'
    WHEN k.id BETWEEN 4240 AND 4249 THEN 'TRIAL_HANDOVER'
    WHEN k.id BETWEEN 4250 AND 4259 THEN 'STANDARDIZATION'
    ELSE 'CLOSE'
  END,
  'REQUIRED',true,'ALWAYS',1,k.title,k.content_text
FROM knowledge_item k
WHERE k.organization_id=100 AND k.id BETWEEN 4200 AND 4264 AND k.type='TEMPLATE';

INSERT IGNORE INTO project_document(
  project_id,stage_code,source_template_id,source_template_revision,
  source_title_snapshot,source_markdown_snapshot,requirement,condition_code,status
)
SELECT p.id,c.stage_code,k.id,c.published_revision,
  c.published_title_snapshot,c.published_markdown_snapshot,c.requirement,c.condition_code,'PENDING'
FROM delivery_project p
JOIN knowledge_item k ON k.organization_id=p.organization_id AND k.id BETWEEN 4200 AND 4264
JOIN document_template_config c ON c.knowledge_item_id=k.id
WHERE p.organization_id=100 AND k.status='PUBLISHED' AND c.enabled=true;

INSERT IGNORE INTO document_job(
  organization_id,job_type,business_key,business_id,status
)
SELECT k.organization_id,'KNOWLEDGE_MIGRATION',CONCAT('KNOWLEDGE:',k.id),k.id,'PENDING'
FROM knowledge_item k
WHERE k.organization_id=100 AND k.id BETWEEN 4200 AND 4264 AND k.outline_link_id IS NULL;

INSERT IGNORE INTO document_job(
  organization_id,job_type,business_key,business_id,status
)
SELECT p.organization_id,'PROJECT_TEMPLATE_SYNC',CONCAT('PROJECT:',p.id),p.id,'PENDING'
FROM delivery_project p
WHERE p.organization_id=100
  AND EXISTS (
    SELECT 1 FROM project_document pd
    WHERE pd.project_id=p.id AND pd.source_template_id BETWEEN 4200 AND 4264
      AND pd.outline_link_id IS NULL
  );

UPDATE document_job j
SET j.status='PENDING',j.attempt_count=0,j.next_attempt_at=current_timestamp,
    j.last_error=NULL,j.started_at=NULL,j.completed_at=NULL,j.lease_token=NULL,
    j.lease_expires_at=NULL,j.updated_at=current_timestamp,j.version=j.version+1
WHERE j.organization_id=100 AND j.job_type IN ('KNOWLEDGE_MIGRATION','PROJECT_TEMPLATE_SYNC')
  AND j.status<>'RUNNING'
  AND ((j.job_type='KNOWLEDGE_MIGRATION' AND j.business_id BETWEEN 4200 AND 4264)
    OR (j.job_type='PROJECT_TEMPLATE_SYNC' AND EXISTS (
      SELECT 1 FROM project_document pd
      WHERE pd.project_id=j.business_id AND pd.source_template_id BETWEEN 4200 AND 4264
        AND pd.outline_link_id IS NULL
    )));

UPDATE delivery_project p
SET p.document_snapshot_at=COALESCE(p.document_snapshot_at,current_timestamp),
    p.document_space_status='PENDING',p.document_space_error=NULL,
    p.updated_at=current_timestamp
WHERE p.organization_id=100
  AND EXISTS (
    SELECT 1 FROM project_document pd
    WHERE pd.project_id=p.id AND pd.source_template_id BETWEEN 4200 AND 4264
      AND pd.outline_link_id IS NULL
  );

DROP TEMPORARY TABLE IF EXISTS obsolete_stage_link;
DROP TEMPORARY TABLE IF EXISTS obsolete_stage_template;
