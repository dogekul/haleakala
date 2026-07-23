-- Seven-stage delivery gate templates. Loaded only with the demo Flyway location.
-- INSERT IGNORE preserves any administrator edits made after the initial seed.

INSERT IGNORE INTO knowledge_item(
  id,organization_id,type,title,summary,content_text,tags_text,visibility,status,
  owner_user_id,published_at
) VALUES
  (4100,100,'TEMPLATE','售前交付交接检查清单','启动门禁：确认商务、方案与风险完整交接','# 售前交付交接检查清单\n\n## 合同与承诺\n请填写合同范围、关键承诺和排除项。\n\n## 方案与环境\n请填写方案版本、部署环境和依赖条件。\n\n## 风险与遗留\n请填写未决事项、责任人和完成时限。','项目,启动,门禁','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4101,100,'TEMPLATE','项目启动检查清单','启动门禁：逐项确认项目启动条件','# 项目启动检查清单\n\n## 团队与职责\n请填写项目经理、核心成员和职责分工。\n\n## 计划与资源\n请填写里程碑、资源投入和工作日历。\n\n## 启动结论\n请填写检查结论、例外项和批准人。','项目,启动,检查','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4102,100,'TEMPLATE','项目章程与 Kick-off 纪要','启动门禁：固化项目目标、范围与启动共识','# 项目章程与 Kick-off 纪要\n\n## 项目目标与范围\n请填写业务目标、交付范围和成功标准。\n\n## 治理机制\n请填写会议机制、汇报路径和升级规则。\n\n## 会议决议\n请填写关键决议、行动项、责任人和截止日期。','项目,启动,Kick-off','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4103,100,'TEMPLATE','合同与 SOW 转换记录','将商务合同转换为可执行交付范围','# 合同与 SOW 转换记录\n\n## 交付项映射\n请填写合同条款、交付物和验收标准映射。\n\n## 边界与假设\n请填写不在范围事项、前置假设和客户责任。','项目,合同,SOW','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4104,100,'TEMPLATE','干系人沟通计划','记录干系人诉求、影响力与沟通节奏','# 干系人沟通计划\n\n## 干系人清单\n请填写角色、诉求、影响力和项目态度。\n\n## 沟通安排\n请填写沟通主题、频率、渠道和负责人。','项目,沟通,干系人','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4105,100,'TEMPLATE','项目周报','统一呈现项目进展、风险与下周计划','# 项目周报\n\n## 本周进展\n请填写已完成事项和里程碑变化。\n\n## 风险与决策\n请填写风险、阻塞、需要的决策和责任人。\n\n## 下周计划\n请填写计划事项、负责人和目标日期。','项目,周报','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4106,100,'TEMPLATE','风险登记册','维护项目风险识别与应对记录','# 风险登记册\n\n## 风险描述\n请填写风险分类、触发条件、概率和影响。\n\n## 应对计划\n请填写缓解措施、责任人、截止日期和当前状态。','项目,风险','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4107,100,'TEMPLATE','多厂商协同记录','记录跨厂商依赖、接口和协同承诺','# 多厂商协同记录\n\n## 参与方与边界\n请填写各厂商职责、交付边界和接口人。\n\n## 依赖与行动项\n请填写依赖事项、承诺日期、责任方和升级路径。','项目,协同,厂商','ORGANIZATION','PUBLISHED',100,current_timestamp),

  (4110,100,'TEMPLATE','需求采集与规格说明书','需求门禁：形成可验证的需求规格','# 需求采集与规格说明书\n\n## 业务背景与目标\n请填写现状、痛点、目标和价值指标。\n\n## 需求明细\n请填写场景、角色、业务规则、数据和异常路径。\n\n## 验收标准\n请填写逐项可验证的验收标准和优先级。','项目,需求,SRS','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4111,100,'TEMPLATE','需求基线确认书','需求门禁：由客户确认冻结后的需求基线','# 需求基线确认书\n\n## 基线范围\n请填写纳入基线的需求编号、版本和冻结日期。\n\n## 遗留与例外\n请填写未决项、范围外事项和变更处理方式。\n\n## 确认记录\n请填写客户确认人、项目确认人和确认日期。','项目,需求,基线','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4112,100,'TEMPLATE','需求分类与范围确认表','需求门禁：确认 L0-L3 分类及交付边界','# 需求分类与范围确认表\n\n## 分类结果\n请填写需求编号、分类级别、判定依据和复核人。\n\n## 范围处理\n请填写标品、配置、二开或范围外的处理结论。\n\n## 客户确认\n请填写确认意见、确认人和日期。','项目,需求,分类','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4113,100,'TEMPLATE','需求去重与合并矩阵','识别重复、冲突及可合并需求','# 需求去重与合并矩阵\n\n## 比对结果\n请填写源需求、目标需求、相似点和冲突点。\n\n## 处置结论\n请填写保留、合并或关闭决定及责任人。','项目,需求,去重','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4114,100,'TEMPLATE','需求变更记录','记录需求基线后的变更审批与影响','# 需求变更记录\n\n## 变更内容\n请填写变更前后内容、原因和提出方。\n\n## 影响评估\n请填写范围、工期、成本、质量和风险影响。\n\n## 审批结论\n请填写批准或拒绝结论、审批人和生效日期。','项目,需求,变更','ORGANIZATION','PUBLISHED',100,current_timestamp),

  (4120,100,'TEMPLATE','二开阶段门禁确认单','二开门禁：明确是否存在二开及阶段结论','# 二开阶段门禁确认单\n\n## 适用性判断\n请填写本项目是否存在二开任务及判断依据。\n\n## 阶段结论\n请填写无二开放行结论，或有二开时的任务清单与状态。\n\n## 确认记录\n请填写技术负责人、项目经理和确认日期。','项目,二开,门禁','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4121,100,'TEMPLATE','二开设计文档','有二开时：固化技术方案与影响边界','# 二开设计文档\n\n## 方案概述\n请填写需求映射、总体方案和关键流程。\n\n## 接口与数据\n请填写接口、数据模型、权限和异常处理。\n\n## 风险与回退\n请填写兼容性风险、上线步骤和回退方案。','项目,二开,设计','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4122,100,'TEMPLATE','测试与代码审查报告','有二开时：证明代码审查和测试通过','# 测试与代码审查报告\n\n## 代码审查\n请填写审查范围、审查人、问题和关闭情况。\n\n## 测试结果\n请填写单元、集成、回归及性能测试结果。\n\n## 放行结论\n请填写遗留缺陷、风险接受人和放行结论。','项目,二开,测试,审查','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4123,100,'TEMPLATE','二开上线检查清单','有二开时：确认上线前全部技术条件','# 二开上线检查清单\n\n## 版本与制品\n请填写代码版本、制品、配置和发布负责人。\n\n## 前置检查\n请填写测试、备份、监控和权限检查结果。\n\n## 上线批准\n请填写批准人、上线窗口和回退触发条件。','项目,二开,上线','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4124,100,'TEMPLATE','安全合规检查单','检查二开涉及的安全、隐私与合规要求','# 安全合规检查单\n\n## 数据与权限\n请填写数据分级、访问权限、脱敏和留存要求。\n\n## 安全验证\n请填写漏洞扫描、审计日志和整改结论。','项目,安全,合规','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4125,100,'TEMPLATE','扩展点使用清单','记录二开使用的产品扩展点及版本约束','# 扩展点使用清单\n\n## 扩展点明细\n请填写扩展点名称、版本、用途和调用方式。\n\n## 兼容性约束\n请填写升级影响、限制和维护责任人。','项目,二开,扩展点','ORGANIZATION','PUBLISHED',100,current_timestamp),

  (4130,100,'TEMPLATE','UAT 测试及客户签字报告','上线门禁：证明 UAT 通过且客户确认','# UAT 测试及客户签字报告\n\n## 测试范围与结果\n请填写用例范围、通过率、P0/P1 缺陷和遗留项。\n\n## 条件通过事项\n请填写条件通过项、责任人和关闭日期。\n\n## 客户确认\n请填写客户结论、签字人和日期。','项目,UAT,验收','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4131,100,'TEMPLATE','上线切换与回退方案','上线门禁：明确切换步骤及可执行回退','# 上线切换与回退方案\n\n## 上线窗口与步骤\n请填写窗口、任务顺序、责任人和验证点。\n\n## 回退方案\n请填写触发条件、回退步骤、数据恢复和预计耗时。\n\n## 演练记录\n请填写演练时间、结果、问题和批准结论。','项目,上线,切换,回退','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4132,100,'TEMPLATE','上线执行确认单','上线门禁：记录实际执行及业务确认','# 上线执行确认单\n\n## 执行记录\n请填写实际开始结束时间、执行人和步骤结果。\n\n## 验证结果\n请填写技术验证、业务验证和监控状态。\n\n## 最终结论\n请填写成功、回退或带条件上线结论及批准人。','项目,上线,执行','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4133,100,'TEMPLATE','业务规则配置表','记录上线使用的业务规则与配置项','# 业务规则配置表\n\n## 配置明细\n请填写配置项、环境、取值、来源和责任人。\n\n## 验证记录\n请填写验证场景、结果和变更审批。','项目,配置,规则','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4134,100,'TEMPLATE','数据迁移验证报告','记录迁移范围、执行和数据一致性验证','# 数据迁移验证报告\n\n## 迁移范围与策略\n请填写源系统、数据范围、转换规则和批次。\n\n## 对账与异常\n请填写总量、金额、关键字段对账及异常处理。\n\n## 验证结论\n请填写业务确认人和一致性结论。','项目,数据迁移,验证','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4135,100,'TEMPLATE','客户培训计划','规划上线前客户培训对象、内容和安排','# 客户培训计划\n\n## 培训对象与目标\n请填写角色、人数、目标和先决条件。\n\n## 课程与安排\n请填写课程、讲师、时间、地点和材料。\n\n## 考核方式\n请填写签到、考试和补训规则。','项目,培训,计划','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4136,100,'TEMPLATE','上线指挥手册','明确上线指挥体系和应急沟通机制','# 上线指挥手册\n\n## 指挥体系\n请填写总指挥、各工作组、联系方式和职责。\n\n## 升级机制\n请填写事件分级、通报频率和决策路径。\n\n## 应急场景\n请填写关键故障场景、处置人和预案入口。','项目,上线,指挥','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4137,100,'TEMPLATE','安全合规交付检查单','上线前确认安全与合规交付项','# 安全合规交付检查单\n\n## 合规要求\n请填写适用法规、客户制度和交付证据。\n\n## 安全控制\n请填写账户、权限、加密、审计和漏洞整改结果。\n\n## 放行结论\n请填写安全负责人意见和批准日期。','项目,上线,安全,合规','ORGANIZATION','PUBLISHED',100,current_timestamp),

  (4140,100,'TEMPLATE','试运行退出报告','移交门禁：证明试运行指标满足退出条件','# 试运行退出报告\n\n## 运行指标\n请填写试运行周期、可用性、性能、故障和工单指标。\n\n## 问题清零\n请填写 L1/L2 问题、遗留项和风险接受情况。\n\n## 退出结论\n请填写客户确认人、退出日期和结论。','项目,试运行,退出','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4141,100,'TEMPLATE','运营移交清单','移交门禁：完成系统、资料和责任移交','# 运营移交清单\n\n## 系统与权限\n请填写环境、账号、权限、监控和备份移交。\n\n## 资料与知识\n请填写手册、配置、接口、FAQ 和已知问题。\n\n## 责任确认\n请填写移交方、接收方、支持边界和生效日期。','项目,运营,移交','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4142,100,'TEMPLATE','客户培训完成及考核记录','移交门禁：证明关键用户完成培训并合格','# 客户培训完成及考核记录\n\n## 培训执行\n请填写课程、日期、讲师、参训人和签到情况。\n\n## 考核结果\n请填写考核方式、通过率、不合格人员和补训结果。\n\n## 客户确认\n请填写客户培训负责人意见和日期。','项目,培训,考核','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4143,100,'TEMPLATE','常见问题 FAQ','沉淀试运行期间的高频问题与处理方法','# 常见问题 FAQ\n\n## 问题与场景\n请填写问题现象、适用角色和触发场景。\n\n## 解决方法\n请填写排查步骤、解决方法和升级入口。','项目,FAQ,知识','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4144,100,'TEMPLATE','驻场管理记录','记录驻场人员、工作内容和客户反馈','# 驻场管理记录\n\n## 驻场安排\n请填写人员、地点、周期、职责和联系方式。\n\n## 工作与问题\n请填写当期工作、问题、处置和待协调事项。','项目,驻场,管理','ORGANIZATION','PUBLISHED',100,current_timestamp),

  (4150,100,'TEMPLATE','项目标准化评估报告','标准化门禁：评估本项目标准化与复用水平','# 项目标准化评估报告\n\n## 评估结果\n请填写标品覆盖率、配置复用率、二开比例和文档完整度。\n\n## 差距分析\n请填写主要差距、根因和业务影响。\n\n## 评审结论\n请填写评审人、评分和改进方向。','项目,标准化,评估','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4151,100,'TEMPLATE','标准化债务处置清单','标准化门禁：明确非标准项的后续处置','# 标准化债务处置清单\n\n## 债务明细\n请填写非标准项、来源、影响范围和重复次数。\n\n## 处置计划\n请填写产品化、重构、保留或关闭结论及目标版本。\n\n## 责任确认\n请填写负责人、截止日期和验证方式。','项目,标准化,债务','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4152,100,'TEMPLATE','二开成本归因报告','有二开时：归集人天、金额和成本归因','# 二开成本归因报告\n\n## 二开范围\n请填写二开任务、需求来源和分类结论。\n\n## 成本明细\n请填写预估与实际人天、金额、偏差和原因。\n\n## 归因结论\n请填写客户特有、产品缺口或交付效率归因及负责人。','项目,二开,成本','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4153,100,'TEMPLATE','项目复盘与知识沉淀清单','标准化门禁：沉淀经验、教训与知识资产','# 项目复盘与知识沉淀清单\n\n## 目标与结果\n请填写目标达成、关键指标和偏差。\n\n## 经验与教训\n请填写有效做法、失败原因和改进措施。\n\n## 知识资产\n请填写已沉淀案例、模板、FAQ、代码或培训材料链接。','项目,复盘,知识','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4154,100,'TEMPLATE','交付产品飞轮建议','将项目经验转化为产品与交付改进建议','# 交付产品飞轮建议\n\n## 可复用机会\n请填写可产品化能力、适用客户和预期价值。\n\n## 建议行动\n请填写产品、交付或工具改进项、负责人和目标版本。','项目,产品飞轮,建议','ORGANIZATION','PUBLISHED',100,current_timestamp),

  (4160,100,'TEMPLATE','项目验收报告','关闭门禁：固化验收范围、结果与客户签字','# 项目验收报告\n\n## 验收范围与依据\n请填写合同、SOW、需求基线和验收标准。\n\n## 验收结果\n请填写功能、数据、性能、文档和培训验收结论。\n\n## 客户签字\n请填写客户意见、签字人和验收日期。','项目,关闭,验收','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4161,100,'TEMPLATE','项目关闭检查清单','关闭门禁：确认交付、移交与行政收尾完整','# 项目关闭检查清单\n\n## 交付与问题\n请填写交付物归档、遗留问题、风险和责任人。\n\n## 资源与权限\n请填写资源释放、临时权限回收和环境处置。\n\n## 关闭批准\n请填写项目经理、客户和 PMO 的关闭意见。','项目,关闭,检查','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4162,100,'TEMPLATE','合同回款与费用结算确认','关闭门禁：确认合同义务、回款与费用结算','# 合同回款与费用结算确认\n\n## 合同履约\n请填写交付义务、验收条款和履约状态。\n\n## 回款与费用\n请填写应收、已收、未收、项目费用和结算状态。\n\n## 财务确认\n请填写责任人、例外处理和确认日期。','项目,关闭,回款,结算','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4163,100,'TEMPLATE','客户满意度评估','收集客户对交付过程和结果的评价','# 客户满意度评估\n\n## 评分\n请填写质量、进度、沟通、专业度和整体满意度评分。\n\n## 反馈与改进\n请填写客户表扬、问题、建议和跟进责任人。','项目,客户满意度','ORGANIZATION','PUBLISHED',100,current_timestamp),
  (4164,100,'TEMPLATE','续约与增购评估','识别项目关闭后的续约、增购与运营机会','# 续约与增购评估\n\n## 客户价值与关系\n请填写价值实现、关键关系和健康度。\n\n## 机会判断\n请填写续约、增购或新场景机会、金额和时间窗口。\n\n## 跟进计划\n请填写运营负责人、下一步和计划日期。','项目,续约,增购','ORGANIZATION','PUBLISHED',100,current_timestamp);

INSERT IGNORE INTO document_template_config(
  knowledge_item_id,stage_code,requirement,enabled,condition_code,published_revision,
  published_title_snapshot,published_markdown_snapshot
)
SELECT k.id,
  CASE
    WHEN k.id BETWEEN 4100 AND 4107 THEN 'START'
    WHEN k.id BETWEEN 4110 AND 4114 THEN 'REQUIREMENT'
    WHEN k.id BETWEEN 4120 AND 4125 THEN 'CUSTOM_DEV'
    WHEN k.id BETWEEN 4130 AND 4137 THEN 'GO_LIVE'
    WHEN k.id BETWEEN 4140 AND 4144 THEN 'TRIAL_HANDOVER'
    WHEN k.id BETWEEN 4150 AND 4154 THEN 'STANDARDIZATION'
    ELSE 'CLOSE'
  END,
  CASE WHEN k.id IN (
    4100,4101,4102,4110,4111,4112,4120,4121,4122,4123,
    4130,4131,4132,4140,4141,4142,4150,4151,4152,4153,4160,4161,4162
  ) THEN 'REQUIRED' ELSE 'OPTIONAL' END,
  true,
  CASE WHEN k.id IN (4121,4122,4123,4152) THEN 'HAS_CUSTOM_DEV' ELSE 'ALWAYS' END,
  1,k.title,k.content_text
FROM knowledge_item k
WHERE k.organization_id=100 AND k.id BETWEEN 4100 AND 4164 AND k.type='TEMPLATE';

INSERT IGNORE INTO project_document(
  project_id,stage_code,source_template_id,source_template_revision,
  source_title_snapshot,source_markdown_snapshot,requirement,condition_code,status
)
SELECT p.id,c.stage_code,k.id,c.published_revision,
  c.published_title_snapshot,c.published_markdown_snapshot,c.requirement,c.condition_code,'PENDING'
FROM delivery_project p
JOIN knowledge_item k ON k.organization_id=p.organization_id AND k.id BETWEEN 4100 AND 4164
JOIN document_template_config c ON c.knowledge_item_id=k.id
WHERE p.organization_id=100 AND p.status<>'CLOSED' AND k.status='PUBLISHED' AND c.enabled=true;

INSERT IGNORE INTO document_job(
  organization_id,job_type,business_key,business_id,status
)
SELECT k.organization_id,'KNOWLEDGE_MIGRATION',CONCAT('KNOWLEDGE:',k.id),k.id,'PENDING'
FROM knowledge_item k
WHERE k.organization_id=100 AND k.id BETWEEN 4100 AND 4164 AND k.outline_link_id IS NULL;

INSERT IGNORE INTO document_job(
  organization_id,job_type,business_key,business_id,status
)
SELECT p.organization_id,'PROJECT_TEMPLATE_SYNC',CONCAT('PROJECT:',p.id),p.id,'PENDING'
FROM delivery_project p
WHERE p.organization_id=100 AND p.status<>'CLOSED'
  AND EXISTS (
    SELECT 1 FROM project_document pd
    WHERE pd.project_id=p.id AND pd.outline_link_id IS NULL
  );

UPDATE document_job j
SET j.status='PENDING',j.attempt_count=0,j.next_attempt_at=current_timestamp,
    j.last_error=NULL,j.started_at=NULL,j.completed_at=NULL,j.lease_token=NULL,
    j.lease_expires_at=NULL,j.updated_at=current_timestamp,j.version=j.version+1
WHERE j.organization_id=100 AND j.job_type='KNOWLEDGE_MIGRATION'
  AND EXISTS (
    SELECT 1 FROM knowledge_item k
    WHERE k.id=j.business_id AND k.outline_link_id IS NULL
  );

UPDATE document_job j
SET j.status='PENDING',j.attempt_count=0,j.next_attempt_at=current_timestamp,
    j.last_error=NULL,j.started_at=NULL,j.completed_at=NULL,j.lease_token=NULL,
    j.lease_expires_at=NULL,j.updated_at=current_timestamp,j.version=j.version+1
WHERE j.organization_id=100 AND j.job_type='PROJECT_TEMPLATE_SYNC'
  AND EXISTS (
    SELECT 1 FROM project_document pd
    WHERE pd.project_id=j.business_id AND pd.outline_link_id IS NULL
  );

UPDATE delivery_project p
SET p.document_snapshot_at=COALESCE(p.document_snapshot_at,current_timestamp),
    p.document_space_status='PENDING',p.document_space_error=NULL,
    p.updated_at=current_timestamp
WHERE p.organization_id=100 AND p.status<>'CLOSED'
  AND EXISTS (
    SELECT 1 FROM project_document pd
    WHERE pd.project_id=p.id AND pd.outline_link_id IS NULL
  );

UPDATE delivery_project
SET status='CLOSED',updated_at=current_timestamp
WHERE organization_id=100 AND current_stage='CLOSE' AND status='COMPLETED';
