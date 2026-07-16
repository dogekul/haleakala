# 客户全生命周期模块设计

> 日期：2026-07-16
> 参考工程：`/Users/dogekul/IdeaProjects/Rainier`
> 前置设计：`2026-07-16-customer-management-design.md`

## 目标

参考 Rainier 的客户板块，在智鹿交付中建设客户全生命周期能力。范围覆盖商机总览、售前推进、实施协同、实施驾驶舱和客户运营；客户主数据及客户管理页面由并行的客户管理任务负责，本设计只依赖其稳定接口，不重复实现。

本次采用“原生融合”而非代码复制：Rainier 只提供业务语义和交互参考，实现继续使用本项目的 Java 8、Spring Boot、JdbcTemplate、MySQL、React、TypeScript、Ant Design 和现有组织权限体系。系统不读取、同步或双写 Rainier 数据。

## 范围

### 包含

- 客户中心统一导航及客户管理之外的五个工作区。
- 商机查询、新建、编辑、筛选、漏斗和阶段停留预警。
- 线索到合同的售前状态机、活动清单、决策关口和产出物门禁。
- 商机详情和“客户 → 商机 → 项目 → 运营”全链视图。
- 赢单商机原子创建或关联现有交付项目。
- 基于现有项目数据的实施协同和实施驾驶舱。
- 项目收尾后自动创建运营单，以及运营单的线性推进。
- 组织隔离、权限、审计、乐观锁、幂等和端到端测试。

### 不包含

- 客户表、客户增改查接口和客户管理页面内部实现。
- Rainier 数据导入、同步、双写或兼容层。
- 第二套实施状态机、第二套项目风险/里程碑/产出物数据。
- 通用 BPMN/流程引擎、可视化流程配置器或门禁配置表。
- 商机和运营单的物理删除；丢单、关闭和历史追踪替代删除。
- 报价、发票、真实收款流水、邮件发送和外部 CRM 集成。

## 方案选择

评估过三种方案：

1. **原生融合（采用）**：售前状态归商机，实施状态归现有项目，运营状态归运营单。边界清晰，没有同步问题。
2. **十阶段映射**：保留 Rainier 十阶段商机并同步后五阶段到项目七阶段。页面接近原版，但会产生两套实施状态和失败补偿逻辑。
3. **直接复制**：复制 Rainier 的 JPA 领域和页面。初期快，但与本项目技术栈、组织权限和现有交付模块冲突。

采用方案 1。它保留 Rainier 的客户全流程价值，同时复用本项目已经存在且更完整的项目、需求、文件、风险、里程碑和驾驶舱能力。

## 领域边界

### 客户管理

客户管理任务拥有 `customer` 表、`CustomerService`、`/api/v1/customers` 和客户管理页面。CRM 创建商机时调用 `CustomerService.get(organizationId, customerId)`，要求客户属于当前组织且状态为 `ACTIVE`。客户停用后，历史商机、项目和运营单继续保留名称快照并可读取。

### CRM

新增 `opportunity` 包，负责商机、活动、产出物、售前状态机、项目转交和全链只读模型；新增 `operation` 包，负责客户运营单。门禁类型和状态迁移使用少量 Java 常量/映射表达，不引入流程引擎。

### 项目

项目模块继续唯一持有七阶段生命周期：

`START → REQUIREMENT → CUSTOM_DEV → GO_LIVE → TRIAL_HANDOVER → STANDARDIZATION → CLOSE`

商机赢单后只保存 `project_id` 关联。实施协同和驾驶舱读取 `delivery_project`、`stage_instance`、`project_risk`、`milestone`、`project_artifact` 和 `project_activity`，不在商机表复制实施阶段、风险或进度。

## 售前生命周期

商机阶段为：

`LEAD → OPPORTUNITY → POC → BIDDING → CONTRACT`

商机状态为 `OPEN`、`WON`、`LOST`。阶段只能前进，不能回退或跳级。`LOST` 和 `WON` 都是终态；编辑基本资料不能修改阶段、状态和项目关联。

| 来源阶段 | 前进结果 | 决策 | 必需产出物 |
| --- | --- | --- | --- |
| `LEAD` | `OPPORTUNITY` | 无 | 商机调研报告 |
| `OPPORTUNITY` | `POC` 或 `LOST` | `PASS` / `REJECT` | 决策评审纪要，两种决策都必需 |
| `POC` | `BIDDING` | 无 | 讲解材料、甲方诉求清单、POC 得分表、差距分析报告 |
| `BIDDING` | `CONTRACT` 或 `LOST` | `PASS` / `REJECT` | `PASS` 时需要投标文件；`REJECT` 不要求前进材料 |
| `CONTRACT` | `WON` + 项目转交，或 `LOST` | `PASS` / `REJECT` | `PASS` 时需要中标公示、合同、评审会议纪要、邮件归档、已盖章合同；`REJECT` 不要求前进材料 |

报告类产出物保存标题和 Markdown 正文；文件类产出物引用现有 `file_object.id`，不沿用 Rainier 的外部 URL 占位方案。产出物只追加，不修改或删除。

每个阶段可添加活动清单，活动状态为 `TODO` 或 `DONE`。活动用于协作和详情展示，不作为固定门禁；只有上表列出的业务产出物和决策决定能否推进。

## 项目转交

合同 `PASS` 使用独立的“转交实施”操作，而不是普通字段更新。请求支持二选一：

- 创建新项目：提交项目编号、名称、产品、已发布产品版本、项目负责人、日期和门禁模式。
- 关联已有项目：提交当前组织内、客户一致且未关联其他商机的项目 ID。

服务端在同一事务中完成：重新校验客户、产品、版本、负责人和必需合同产出物；调用现有 `ProjectService.create` 或校验已有项目；写入商机 `project_id`、`status=WON` 和审计记录。任何一步失败都必须整体回滚，不能留下赢单但无项目的中间状态。

新项目沿用客户管理任务定义的 `customer_id` 和客户名称快照。一个商机最多关联一个项目，一个项目最多作为一个商机的转交目标，数据库唯一约束保证重试安全。

## 客户运营

运营阶段为：

`MAINTENANCE（回款/维保） → OPERATING（持续运营） → REPURCHASE（复购） → CLOSED`

当来源商机关联的项目从 `STANDARDIZATION` 推进到本项目现有终段 `CLOSE（项目收尾）` 时，系统幂等创建一条运营单。当前项目没有独立 `ACCEPTANCE` 阶段，因此不新增虚构阶段；验收报告由项目模板/产出物和收尾门禁承载。

运营单也可手工创建，必须选择启用客户，可选关联同组织项目和来源商机。自动创建时复制客户名称快照、项目名称作为标题、运营负责人和来源关联。一个来源商机最多自动生成一条运营单。已关闭运营单不可继续推进，但历史详情保持可读。

## 数据模型

Flyway `V13` 在客户管理的 `V12` 之后创建以下表和权限。

### `sales_opportunity`

- `id`、`organization_id`。
- `customer_id`、`customer_name_snapshot`。
- `title`、`note`、`amount`。
- 可空 `product_id`、`product_version_id`。
- 可空 `commercial_owner_user_id`、`solution_owner_user_id`、`project_manager_user_id`、`operation_owner_user_id`。
- `stage`、`status`、可空 `project_id`、`stage_entered_at`。
- `created_by`、`created_at`、`updated_at`、`version`。
- `project_id` 唯一且可空；常用组织、状态、阶段和负责人索引。

### `opportunity_activity`

- `id`、`organization_id`、`opportunity_id`、`stage_code`。
- `title`、`status`、`sort_order`。
- `created_by`、`created_at`、`completed_at`、`version`。

### `opportunity_artifact`

- `id`、`organization_id`、`opportunity_id`、`stage_from`、`artifact_type`。
- `title`、可空 `content_markdown`、可空 `file_id`、可空 `decision`。
- `created_by`、`created_at`。

服务层保证报告类有正文、文件类有 `file_id`。门禁按“某商机、来源阶段、类型至少存在一条”判断。

### `customer_operation`

- `id`、`organization_id`、`customer_id`、`customer_name_snapshot`。
- `title`、`stage`、`status`、可空 `owner_user_id`。
- 可空 `project_id`、可空且唯一的 `opportunity_id`。
- `created_by`、`created_at`、`updated_at`、`version`。

外键继续使用现有组织、用户、客户、产品、版本、项目和文件表。所有业务查询显式限制 `organization_id`。

## 后端接口

### 商机

- `GET /api/v1/opportunities`：按本组织查询，支持关键字、客户、产品、负责人、阶段和状态。
- `POST /api/v1/opportunities`：创建 `LEAD/OPEN` 商机，必须选择启用客户。
- `GET /api/v1/opportunities/{id}`：商机详情和名称 enrich。
- `PUT /api/v1/opportunities/{id}`：按 `version` 编辑基本资料。
- `POST /api/v1/opportunities/{id}/advance`：推进非合同阶段或执行关口决策。
- `POST /api/v1/opportunities/{id}/handoff`：合同通过并创建/关联项目。
- `GET/POST /api/v1/opportunities/{id}/activities`：活动列查和添加。
- `PUT /api/v1/opportunities/{id}/activities/{activityId}`：按版本完成或恢复活动。
- `GET/POST /api/v1/opportunities/{id}/artifacts`：产出物列查和追加。
- `GET /api/v1/opportunities/{id}/full-link`：聚合客户、商机、项目和运营节点。

### 实施视图

- `GET /api/v1/crm/implementation`：返回已转项目商机的实施协同列表。
- `GET /api/v1/crm/implementation-cockpit`：返回项目健康度、风险、里程碑和收尾指标的聚合只读模型。
- `GET /api/v1/crm/owner-options`：返回当前组织启用用户的 `id/displayName`，供四类负责人选择，不暴露系统管理字段。

前两个实施接口只聚合既有项目数据，不提供项目阶段写接口。

### 运营

- `GET /api/v1/operations`：支持关键字、客户、负责人、阶段和状态。
- `POST /api/v1/operations`：手工创建运营单。
- `GET /api/v1/operations/{id}`：运营详情和来源全链。
- `PUT /api/v1/operations/{id}`：按 `version` 编辑标题和负责人等基本资料。
- `POST /api/v1/operations/{id}/advance`：线性推进，末阶段推进后关闭。

不提供删除接口。

## 前端信息架构

主导航将客户管理任务新增的入口统一命名为“客户中心”，继续放在“驾驶舱”和“项目空间”之间。客户管理页面作为默认路由，其内部逻辑不由本任务修改。

客户中心路由：

- `/customers`：客户管理。
- `/customers/opportunities`：商机总览。
- `/customers/opportunities/:id`：商机详情。
- `/customers/presale`：售前推进。
- `/customers/implementation`：实施协同。
- `/customers/implementation-cockpit`：实施驾驶舱。
- `/customers/operations`：客户运营。
- `/customers/operations/:id`：运营详情。

客户中心各列表页使用一致的顶部页签、筛选工具条、加载/空/错误状态和分页策略。新增依赖只使用项目已经安装的 Ant Design、React Query 和 React Router。

### 商机总览

顶部展示商机总数、开放金额、赢单数、丢单数和赢单率；中部展示售前漏斗和阶段停留预警；下部提供高密度列表/卡片切换。支持客户、产品、四类负责人、阶段、状态和关键词筛选。

### 售前推进

按五个售前阶段展示可操作商机。推进时先检查缺失产出物；缺件则打开补充抽屉，关口阶段再收集 `PASS/REJECT`。丢单必须二次确认，合同通过进入转交实施抽屉。

### 商机详情

展示客户、标题、金额、产品、四类负责人、备注、阶段停留时长和状态。页签包含阶段活动、产出物和全链路；关联项目和运营单使用深链跳转现有项目详情或运营详情。

### 实施协同与驾驶舱

实施协同展示商机、客户、关联项目、项目七阶段、负责人、风险、最近里程碑和计划完成日期；写操作跳转项目空间。实施驾驶舱展示实施中项目、红色风险、逾期里程碑、收尾项目等指标，并按健康度、项目阶段、负责人和客户筛选。

### 客户运营

以三列看板展示活动运营单，提供手工新建、详情、编辑和推进。详情展示来源客户、商机、项目、负责人、更新时间及全链路。

## 权限与安全

新增：

- `crm:read`：查看客户中心非客户管理页面、商机、实施聚合和运营数据。
- `crm:write`：创建/编辑/推进商机，追加活动与产出物，转交项目，创建/编辑/推进运营单。

`customer:read` 继续控制客户管理和客户选择器。所有后端接口以当前用户为准，不信任前端提交的组织、客户名称、负责人名称或状态。客户、产品、版本、用户、项目和文件均重新校验组织归属；跨组织记录按不存在处理。

负责人选择使用 CRM 自己的最小只读选项接口，避免依赖仅限管理员的用户管理接口。产品和已发布版本选择复用现有 `/api/v1/products` 只读接口，因此需要使用产品选择器的角色同时具备 `product:read`。

默认把 `crm:read`、`crm:write` 授予现有管理员和 PMO；其他角色通过系统管理配置。前端隐藏无权限入口，后端仍独立执行权限检查。

## 错误处理与一致性

- `400`：参数不合法、客户已停用、产品版本不可用、缺少门禁产出物或请求组合不合法。
- `403`：缺少权限。
- `404`：记录不存在或跨组织访问。
- `409`：非法阶段推进、终态继续推进、重复项目转交、运营单已关闭或乐观锁冲突。

所有更新使用 `version` 乐观锁。状态推进、项目转交、自动运营单和审计记录处于同一事务边界。`project_id` 和 `opportunity_id` 唯一约束承担最终幂等保护；重试不得产生重复项目关联或重复运营单。

## 并行开发与合并

客户管理任务当前位于独立工作区和分支，拥有 `V12`、客户后端和后续客户前端。本任务在客户管理分支稳定后创建独立 `codex/customer-lifecycle` worktree，并以其最新提交为基线。

CRM 开发不修改客户表、客户接口或客户管理页面内部逻辑。共享文件只在最终集成阶段调整：`App.tsx`、`AppShell.tsx`、`homeRoute.ts`、`SecurityConfig.java`、项目阶段推进钩子和迁移序号。集成前先同步客户管理最新提交，解决共享导航和路由的一次性冲突。

## 测试与验收

### 后端

- Flyway `V13` 在 `V12` 后建立全部约束、索引和权限。
- 商机创建要求本组织启用客户；停用客户的历史商机仍可读。
- 五阶段正常推进、所有门禁材料、关口 `PASS/REJECT` 和终态限制。
- 客户、产品、版本、负责人、项目和文件的跨组织拒绝。
- 项目转交创建/关联两条路径、事务回滚、唯一约束和重复请求。
- 实施接口只读取现有项目七阶段、风险和里程碑。
- 项目进入 `CLOSE` 自动创建且只创建一条运营单。
- 运营三阶段推进、关闭限制和手工创建。
- `crm:read` / `crm:write` 权限和审计日志。

### 前端

- 客户中心入口、页签、路由和无权限隐藏。
- 商机漏斗、筛选、列表/卡片、长文本和空状态。
- 售前推进、缺件补充、决策确认、丢单确认和项目转交。
- 商机详情活动、产出物和全链路。
- 实施协同和驾驶舱显示项目真实阶段，不维护本地实施状态。
- 运营看板、详情、手工创建和推进。
- API 错误、加载、重试和乐观锁冲突提示。

### 完整验收

1. 创建启用客户。
2. 使用该客户创建线索，补齐每阶段产出物并通过售前关口。
3. 合同通过后创建交付项目，确认商机、项目和客户关联一致。
4. 在实施协同和驾驶舱看到项目真实阶段、风险和里程碑。
5. 将项目推进至 `CLOSE`，确认只生成一条运营单。
6. 将运营单推进至复购并关闭，确认全链路仍可追踪。
7. 停用客户，确认不能再创建新商机，但历史全链可读取。
8. 运行后端、前端全量测试与构建，启动 Docker 环境并在浏览器完成冒烟验收。

## 实施切片

实现按一个领域设计、五个可独立验证的切片推进：

1. 数据库、权限、商机基础 API 和组织隔离。
2. 售前状态机、活动、产出物门禁和商机详情。
3. 项目转交、实施协同和实施驾驶舱。
4. 运营单、项目收尾钩子和全链路。
5. 客户中心路由整合、前端完善、全量回归和浏览器验收。

每个切片遵循测试先行并形成独立可回退提交。
