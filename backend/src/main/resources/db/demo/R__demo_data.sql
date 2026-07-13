-- Demo data is loaded only when db/demo is added to spring.flyway.locations.
-- All statements are idempotent so the repeatable migration remains safe after upgrades.

INSERT IGNORE INTO organization(id,name,code) VALUES (100,'智鹿数字科技','ZHILU-DEMO');
INSERT IGNORE INTO team(id,organization_id,name,code) VALUES
  (100,100,'交付管理部','DELIVERY'),(101,100,'产品与方案部','PRODUCT'),(102,100,'技术交付组','TECH');

INSERT IGNORE INTO app_user(id,organization_id,primary_team_id,username,password_hash,display_name,email,status) VALUES
  (100,100,100,'admin','$2y$10$/IZbPDpSd8.Z34VhJDiQCuERWsqBlWvlaYen48YPCYpY8pRTV3S/K','系统管理员','admin@zhilu.local','ACTIVE'),
  (101,100,100,'liran','$2y$10$/IZbPDpSd8.Z34VhJDiQCuERWsqBlWvlaYen48YPCYpY8pRTV3S/K','李然','liran@zhilu.local','ACTIVE'),
  (102,100,100,'wangchen','$2y$10$/IZbPDpSd8.Z34VhJDiQCuERWsqBlWvlaYen48YPCYpY8pRTV3S/K','王晨','wangchen@zhilu.local','ACTIVE'),
  (103,100,102,'chenxi','$2y$10$/IZbPDpSd8.Z34VhJDiQCuERWsqBlWvlaYen48YPCYpY8pRTV3S/K','陈曦','chenxi@zhilu.local','ACTIVE'),
  (104,100,102,'zhouhang','$2y$10$/IZbPDpSd8.Z34VhJDiQCuERWsqBlWvlaYen48YPCYpY8pRTV3S/K','周航','zhouhang@zhilu.local','ACTIVE'),
  (105,100,101,'zhaomin','$2y$10$/IZbPDpSd8.Z34VhJDiQCuERWsqBlWvlaYen48YPCYpY8pRTV3S/K','赵敏','zhaomin@zhilu.local','ACTIVE');
INSERT IGNORE INTO user_team(user_id,team_id) VALUES (100,100),(101,100),(102,100),(103,102),(104,102),(105,101);
INSERT IGNORE INTO user_role(user_id,role_id) VALUES (100,1),(101,2),(102,3),(103,4),(104,5),(105,6);

INSERT IGNORE INTO product(id,organization_id,owner_user_id,code,name,category,status) VALUES
  (100,100,105,'FIN-CLOUD','企业财务云','财务管理','ACTIVE'),
  (101,100,105,'SCM-CLOUD','智能供应链','供应链','ACTIVE');
INSERT IGNORE INTO product_version(id,product_id,version_name,release_date,status) VALUES
  (100,100,'V5.0','2026-03-31','RELEASED'),
  (101,100,'V4.8','2025-11-30','SUNSET'),
  (102,101,'V3.2','2026-05-15','RELEASED');

INSERT IGNORE INTO delivery_project(id,organization_id,code,name,customer_name,product_id,product_version_id,manager_user_id,status,current_stage,risk_level,gate_mode,start_date,planned_end_date,description,created_by) VALUES
  (1000,100,'PRJ-26001','华东银行财务中台','华东银行',100,100,102,'ACTIVE','CUSTOM_DEV','RED','BLOCK','2026-04-01','2026-09-30','银行级财务中台与对账能力建设',100),
  (1001,100,'PRJ-26002','海辰零售供应链','海辰零售',101,102,102,'ACTIVE','GO_LIVE','YELLOW','WARNING','2026-02-10','2026-08-15','多仓调拨与智能补货',100),
  (1002,100,'PRJ-26003','星海制造业财务云','星海制造',100,100,102,'ACTIVE','REQUIREMENT','GREEN','BLOCK','2026-06-03','2026-12-20','集团财务与合并报表升级',100),
  (1003,100,'PRJ-26004','德润保险费控一体化','德润保险',100,100,102,'ACTIVE','TRIAL_HANDOVER','YELLOW','WARNING','2026-01-15','2026-07-31','保险费用管控和移动报销',100),
  (1004,100,'PRJ-26005','云岭集团司库升级','云岭集团',100,100,102,'ACTIVE','STANDARDIZATION','GREEN','BLOCK','2025-11-01','2026-07-20','全球资金可视化和银企直连',100),
  (1005,100,'PRJ-25018','远川能源共享中心','远川能源',100,101,102,'COMPLETED','CLOSE','GREEN','BLOCK','2025-03-10','2026-03-31','财务共享中心一期',100);

INSERT IGNORE INTO project_member(project_id,user_id,project_role,allocation_percent) VALUES
  (1000,102,'DELIVERY_MANAGER',40),(1000,103,'DELIVERY_ENGINEER',70),(1000,104,'TECH_MANAGER',60),(1000,105,'PRODUCT_MANAGER',20),
  (1001,102,'DELIVERY_MANAGER',30),(1001,103,'DELIVERY_ENGINEER',60),(1001,104,'TECH_MANAGER',30),
  (1002,102,'DELIVERY_MANAGER',30),(1002,103,'DELIVERY_ENGINEER',50),(1002,105,'PRODUCT_MANAGER',30),
  (1003,102,'DELIVERY_MANAGER',25),(1003,104,'TECH_MANAGER',30),(1003,105,'PRODUCT_MANAGER',20),
  (1004,102,'DELIVERY_MANAGER',20),(1004,103,'DELIVERY_ENGINEER',30),(1004,105,'PRODUCT_MANAGER',40),
  (1005,102,'DELIVERY_MANAGER',10),(1005,103,'DELIVERY_ENGINEER',10);

INSERT IGNORE INTO stage_instance(id,project_id,stage_code,stage_name,stage_order,status,gate_status,gate_message) VALUES
  (10000,1000,'START','启动',1,'COMPLETED','PASSED',NULL),(10001,1000,'REQUIREMENT','需求采集',2,'COMPLETED','PASSED',NULL),(10002,1000,'CUSTOM_DEV','二开实施',3,'ACTIVE','BLOCKED','存在 P0 技术风险'),(10003,1000,'GO_LIVE','上线切换',4,'PENDING','READY',NULL),(10004,1000,'TRIAL_HANDOVER','试运行与移交',5,'PENDING','READY',NULL),(10005,1000,'STANDARDIZATION','标准化评估',6,'PENDING','READY',NULL),(10006,1000,'CLOSE','项目收尾',7,'PENDING','READY',NULL),
  (10010,1001,'START','启动',1,'COMPLETED','PASSED',NULL),(10011,1001,'REQUIREMENT','需求采集',2,'COMPLETED','PASSED',NULL),(10012,1001,'CUSTOM_DEV','二开实施',3,'COMPLETED','PASSED',NULL),(10013,1001,'GO_LIVE','上线切换',4,'ACTIVE','WARNING','待完成切换演练'),(10014,1001,'TRIAL_HANDOVER','试运行与移交',5,'PENDING','READY',NULL),(10015,1001,'STANDARDIZATION','标准化评估',6,'PENDING','READY',NULL),(10016,1001,'CLOSE','项目收尾',7,'PENDING','READY',NULL),
  (10020,1002,'START','启动',1,'COMPLETED','PASSED',NULL),(10021,1002,'REQUIREMENT','需求采集',2,'ACTIVE','READY',NULL),(10022,1002,'CUSTOM_DEV','二开实施',3,'PENDING','READY',NULL),(10023,1002,'GO_LIVE','上线切换',4,'PENDING','READY',NULL),(10024,1002,'TRIAL_HANDOVER','试运行与移交',5,'PENDING','READY',NULL),(10025,1002,'STANDARDIZATION','标准化评估',6,'PENDING','READY',NULL),(10026,1002,'CLOSE','项目收尾',7,'PENDING','READY',NULL),
  (10030,1003,'START','启动',1,'COMPLETED','PASSED',NULL),(10031,1003,'REQUIREMENT','需求采集',2,'COMPLETED','PASSED',NULL),(10032,1003,'CUSTOM_DEV','二开实施',3,'COMPLETED','PASSED',NULL),(10033,1003,'GO_LIVE','上线切换',4,'COMPLETED','PASSED',NULL),(10034,1003,'TRIAL_HANDOVER','试运行与移交',5,'ACTIVE','WARNING','待客户验收'),(10035,1003,'STANDARDIZATION','标准化评估',6,'PENDING','READY',NULL),(10036,1003,'CLOSE','项目收尾',7,'PENDING','READY',NULL),
  (10040,1004,'START','启动',1,'COMPLETED','PASSED',NULL),(10041,1004,'REQUIREMENT','需求采集',2,'COMPLETED','PASSED',NULL),(10042,1004,'CUSTOM_DEV','二开实施',3,'COMPLETED','PASSED',NULL),(10043,1004,'GO_LIVE','上线切换',4,'COMPLETED','PASSED',NULL),(10044,1004,'TRIAL_HANDOVER','试运行与移交',5,'COMPLETED','PASSED',NULL),(10045,1004,'STANDARDIZATION','标准化评估',6,'ACTIVE','READY',NULL),(10046,1004,'CLOSE','项目收尾',7,'PENDING','READY',NULL),
  (10050,1005,'START','启动',1,'COMPLETED','PASSED',NULL),(10051,1005,'REQUIREMENT','需求采集',2,'COMPLETED','PASSED',NULL),(10052,1005,'CUSTOM_DEV','二开实施',3,'COMPLETED','PASSED',NULL),(10053,1005,'GO_LIVE','上线切换',4,'COMPLETED','PASSED',NULL),(10054,1005,'TRIAL_HANDOVER','试运行与移交',5,'COMPLETED','PASSED',NULL),(10055,1005,'STANDARDIZATION','标准化评估',6,'COMPLETED','PASSED',NULL),(10056,1005,'CLOSE','项目收尾',7,'COMPLETED','PASSED',NULL);

INSERT IGNORE INTO project_risk(id,project_id,title,category,probability,impact,risk_level,status,owner_user_id,mitigation,due_date) VALUES
  (11000,1000,'对账引擎性能不达标','TECHNICAL',5,5,'RED','OPEN',104,'分批压测并引入异步重试','2026-07-18'),
  (11001,1000,'历史数据口径待确认','SCOPE',4,4,'RED','OPEN',102,'由财务负责人签署转换口径','2026-07-16'),
  (11002,1001,'门店网络切换窗口短','SCHEDULE',3,4,'YELLOW','OPEN',103,'安排双通道切换演练','2026-07-20'),
  (11003,1003,'验收测试参与度不足','CUSTOMER',3,3,'YELLOW','OPEN',102,'按业务线设置验收负责人','2026-07-15'),
  (11004,1002,'合并报表边界未冻结','SCOPE',2,3,'YELLOW','OPEN',105,'召开范围冻结会','2026-07-25');
INSERT IGNORE INTO milestone(id,project_id,name,due_date,status,progress,owner_user_id) VALUES
  (12000,1000,'性能压测通过','2026-07-18','IN_PROGRESS',65,104),(12001,1000,'UAT 准入','2026-08-05','PENDING',20,102),
  (12002,1001,'上线切换演练','2026-07-20','IN_PROGRESS',80,103),(12003,1002,'需求基线冻结','2026-07-28','IN_PROGRESS',45,105),
  (12004,1003,'客户验收','2026-07-12','PENDING',75,102),(12005,1004,'标准化评审','2026-07-19','IN_PROGRESS',60,105);
INSERT IGNORE INTO template_instance(id,project_id,template_key,title,content_markdown,status,updated_by) VALUES
  (13000,1000,'project-charter','项目章程','# 华东银行项目章程\n\n目标：建设统一财务中台。','PUBLISHED',102),
  (13001,1000,'cutover-plan','上线切换方案','# 切换方案\n\n- 数据冻结\n- 全量迁移\n- 业务验证','DRAFT',103);
INSERT IGNORE INTO project_activity(id,project_id,actor_user_id,action,summary) VALUES
  (14000,1000,102,'RISK_CREATED','新增性能风险并指定技术负责人'),(14001,1000,103,'MILESTONE_UPDATED','性能压测进度更新为 65%'),
  (14002,1001,102,'STAGE_ADVANCED','项目进入上线切换阶段'),(14003,1004,105,'STANDARDIZATION_STARTED','启动产品标准化评估');

INSERT IGNORE INTO requirement_item(id,organization_id,project_id,requirement_code,title,description,source,priority,status,created_by) VALUES
  (2000,100,1000,'REQ-260001','批量对账重跑','对账批次失败后按业务键幂等重跑','客户访谈','P0','CONFIRMED',103),
  (2001,100,1001,'REQ-260002','门店补货重算','按门店和日期重算补货建议','需求调研','P1','CONFIRMED',103),
  (2002,100,1002,'REQ-260003','合并报表重算','按组织树重算抵销分录','会议纪要','P1','CONFIRMED',103),
  (2003,100,1003,'REQ-260004','报销单批量重提','失败报销单按原单号重新提交','工单反馈','P1','CONFIRMED',103),
  (2004,100,1004,'REQ-260005','银企流水重新拉取','按账户和日期幂等重新拉取','客户访谈','P2','CONFIRMED',103),
  (2005,100,1005,'REQ-250018','共享单据重新派发','按业务键重新派发共享任务','项目复盘','P2','CONFIRMED',103),
  (2010,100,1000,'REQ-260010','标准客户档案导入','使用标品模板导入客户档案','合同范围','P2','CONFIRMED',103),
  (2011,100,1001,'REQ-260011','标准库存预警','使用可配置库存上下限预警','合同范围','P2','CONFIRMED',103),
  (2012,100,1002,'REQ-260012','自定义数据湖平台','建设范围外的全新数据湖','需求调研','P3','CONFIRMED',103),
  (2013,100,1003,'REQ-260013','移动报销标准审批','使用标品审批流与组织权限','需求调研','P2','CONFIRMED',103),
  (2014,100,1004,'REQ-260014','资金预测标准报表','使用标准报表与配置维度','需求调研','P2','CONFIRMED',103);
INSERT IGNORE INTO classification_suggestion(id,requirement_id,suggested_level,confidence,reason,source) VALUES
  (2100,2000,'L1',0.9100,'需要受控扩展点实现幂等重试','AI'),(2101,2010,'L0',0.9600,'标品导入模板已覆盖','AI');
INSERT IGNORE INTO classification_decision(id,requirement_id,confirmed_level,suggestion_level,override_reason,confirmed_by) VALUES
  (2200,2000,'L1','L1',NULL,103),(2201,2001,'L1',NULL,NULL,103),(2202,2002,'L1',NULL,NULL,103),(2203,2003,'L1',NULL,NULL,103),(2204,2004,'L1',NULL,NULL,103),(2205,2005,'L1',NULL,NULL,103),
  (2210,2010,'L0','L0',NULL,103),(2211,2011,'L0',NULL,NULL,103),(2212,2012,'L2',NULL,NULL,105),(2213,2013,'L0',NULL,NULL,103),(2214,2014,'L0',NULL,NULL,103);
INSERT IGNORE INTO custom_dev_task(id,requirement_id,project_id,title,status,technical_owner_id,estimated_person_days,actual_person_days,estimated_cost,actual_cost,extension_point) VALUES
  (2300,2000,1000,'对账幂等重跑','IN_PROGRESS',104,18,12,36000,26000,'reconciliation.retry'),
  (2301,2001,1001,'补货计算重试','DONE',104,12,14,24000,29000,'reconciliation.retry'),
  (2302,2002,1002,'报表计算重试','DONE',104,15,16,30000,34000,'reconciliation.retry'),
  (2303,2003,1003,'单据幂等重提','DONE',104,8,9,16000,19000,'reconciliation.retry'),
  (2304,2004,1004,'流水幂等重拉','DONE',104,10,11,20000,23000,'reconciliation.retry'),
  (2305,2005,1005,'共享任务重派','DONE',104,9,8,18000,17000,'reconciliation.retry');

INSERT IGNORE INTO product_baseline(id,product_version_id,capability_code,capability_name,dimension,scope_description,configuration_options,extension_points,status,owner_user_id) VALUES
  (3000,100,'FIN-AR-001','应收对账','FUNCTION','覆盖标准应收对账、差异识别和调账处理','匹配规则、容差、批次策略','reconciliation.retry', 'ACTIVE',105),
  (3001,100,'FIN-WF-001','财务审批流','CONFIGURATION','覆盖按组织、金额和业务类型的标准审批','审批人、会签、条件分支','workflow.listener','ACTIVE',105),
  (3002,100,'FIN-EXT-001','受控业务扩展','EXTENSION','通过统一上下文、幂等键和审计日志提供扩展能力','超时、重试、降级','reconciliation.retry\nworkflow.listener\nreport.transformer','ACTIVE',105);
INSERT IGNORE INTO standardization_debt(id,product_version_id,pattern_key,title,occurrence_count,distinct_projects,status,owner_user_id,target_version,verification_note) VALUES
  (3100,100,'reconciliation.retry','统一幂等重试框架',6,6,'CANDIDATE',105,NULL,NULL);
INSERT IGNORE INTO standardization_debt_requirement(standardization_debt_id,requirement_id) VALUES (3100,2000);

INSERT IGNORE INTO knowledge_item(id,organization_id,type,title,summary,content_text,tags_text,product_id,product_version_id,visibility,status,owner_user_id,published_at) VALUES
  (4000,100,'CASE','月末关账从三天缩短到一天','通过差异前置、并行核对和责任到人缩短关账周期','先将往来差异检查前置到 T-2，再把账务核对拆分为可并行任务，最后用门禁固化每个责任人的交付物。','财务,关账,最佳实践',100,100,'ORGANIZATION','PUBLISHED',105,current_timestamp),
  (4001,100,'CODE','对账幂等重试扩展点','可带入业务键的受控重试参考实现','只在 reconciliation.retry 扩展点内使用，并保留业务键和审计上下文。','Java,对账,扩展点',100,100,'ORGANIZATION','PUBLISHED',104,current_timestamp),
  (4002,100,'TRAINING','交付经理七阶段训练营','从项目启动到标准化沉淀的门禁实操','课程包含项目章程、需求决策、二开治理、上线演练、移交与标准化复盘。','交付,培训,门禁',NULL,NULL,'ORGANIZATION','PUBLISHED',101,current_timestamp),
  (4003,100,'CASE','银企直连切换零中断','使用双通道、灰度账户和回退清单保障切换','按银行和账户分批切换，每批先验证查询再开启付款，保留可一键回退的原通道。','资金,上线切换',100,100,'ORGANIZATION','PUBLISHED',102,current_timestamp);
INSERT IGNORE INTO code_snippet(knowledge_item_id,language,code_text,usage_notes) VALUES
  (4001,'Java','String key = context.getBusinessKey();\nretryExecutor.execute(key, () -> reconciliation.run(context));','要求 businessKey 在项目内全局唯一，重试不得绕过审计日志。');
INSERT IGNORE INTO training_material(knowledge_item_id,audience,duration_minutes,file_object_id) VALUES (4002,'交付经理、PMO、项目经理',90,NULL);

INSERT IGNORE INTO engineer_profile(user_id,organization_id,job_title,location,weekly_capacity_hours,resource_status) VALUES
  (100,100,'系统管理员','上海',40,'ACTIVE'),(101,100,'PMO 负责人','上海',40,'ACTIVE'),(102,100,'交付总监','上海',40,'ACTIVE'),
  (103,100,'高级实施顾问','杭州',40,'ACTIVE'),(104,100,'技术交付经理','上海',40,'ACTIVE'),(105,100,'产品经理','深圳',40,'ACTIVE');
INSERT IGNORE INTO skill_catalog(id,organization_id,code,name,category,status) VALUES
  (5000,100,'DELIVERY-GATE','交付门禁','DELIVERY','ACTIVE'),(5001,100,'FINANCE-SOLUTION','财务方案','BUSINESS','ACTIVE'),
  (5002,100,'JAVA-EXT','Java 扩展开发','TECHNICAL','ACTIVE'),(5003,100,'DB-MYSQL','MySQL','TECHNICAL','ACTIVE'),(5004,100,'CUTOVER','上线切换','DELIVERY','ACTIVE');
INSERT IGNORE INTO engineer_skill(user_id,skill_id,proficiency,certified,experience_months) VALUES
  (101,5000,5,TRUE,72),(101,5004,4,TRUE,60),(102,5000,5,TRUE,96),(102,5001,4,TRUE,84),(102,5004,5,TRUE,90),
  (103,5000,4,TRUE,48),(103,5001,4,TRUE,42),(103,5003,3,FALSE,30),(104,5002,5,TRUE,72),(104,5003,5,TRUE,84),(104,5004,4,TRUE,50),(105,5001,5,TRUE,70);
INSERT IGNORE INTO resource_assignment(id,organization_id,user_id,project_id,assignment_role,start_date,end_date,allocation_percent,status,created_by) VALUES
  (6000,100,103,1000,'实施顾问','2026-07-01','2026-07-31',70,'ACTIVE',102),(6001,100,103,1001,'上线顾问','2026-07-10','2026-07-24',60,'ACTIVE',102),
  (6002,100,104,1000,'技术经理','2026-07-01','2026-08-15',60,'ACTIVE',102),(6003,100,104,1003,'技术审核','2026-07-08','2026-07-20',30,'ACTIVE',102),
  (6004,100,105,1002,'产品经理','2026-06-15','2026-08-31',30,'ACTIVE',102),(6005,100,102,1000,'交付负责人','2026-04-01','2026-09-30',40,'ACTIVE',100);

INSERT IGNORE INTO agent_job(id,project_id,skill_code,scenario,status,progress,idempotency_key,external_job_id,request_text,created_by,started_at,finished_at,timeout_at) VALUES
  (7000,1000,'deliver-init','normal','SUCCEEDED',100,'demo-init-1000','demo-agent-job-1000','初始化项目章程与清单',102,current_timestamp,current_timestamp,DATE_ADD(current_timestamp, INTERVAL 30 MINUTE));
INSERT IGNORE INTO agent_attempt(id,agent_job_id,attempt_no,outcome,http_status) VALUES (7100,7000,1,'ACCEPTED',202);
INSERT IGNORE INTO audit_log(id,organization_id,actor_user_id,action,resource_type,resource_id,trace_id,details_text) VALUES
  (8000,100,100,'DEMO_SEEDED','SYSTEM','demo','demo-seed-v1','演示数据初始化完成');
