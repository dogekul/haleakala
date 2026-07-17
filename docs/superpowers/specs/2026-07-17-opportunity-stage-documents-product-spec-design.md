# 商机阶段文档与产品功能 Spec 设计

## 目标

在现有“需求调研报告”流程基础上，将商机剩余指定产出物改造成基于知识库模板的 Outline 文档，并补齐其生成所需的产品功能设计资料。

本期交付范围包括：

- 决策评审纪要、甲方诉求清单、差距分析报告、评审会议纪要改为模板文档。
- 需求调研报告继续沿用已实现的模板文档模式。
- 甲方诉求清单和差距分析报告由系统结合需求调研报告、关联产品、版本功能及功能设计 Spec 生成初稿。
- Outline 中为每个产品建立目录，按产品模块结构建立子目录，并为每个功能初始化一份设计 Spec。
- 现有产品与功能可以批量初始化缺失目录和 Spec。
- AI 不可用时保留模板初稿，允许用户人工完成。

以下现有产出物不改为模板文档：讲解材料、POC 得分表、投标文件、中标公示、合同、邮件归档、已盖章合同。

## 方案选择

本期采用方案 A：通用商机阶段文档服务。

通用服务通过固定、受控的配置映射管理商机阶段、产出物类型、知识库模板场景和生成策略。所有模板文档复用准备、读取、保存草稿、生成、提交和导出能力，避免为每种文档复制一套服务。

暂不采用方案 C：可配置工作流引擎。当前只有一套固定五阶段商机流程，工作流定义、实例版本、动态条件、动作补偿和运行实例迁移会显著增加复杂度，尚无实际业务收益。

### 未来升级为工作流引擎的可能性

当出现以下任一需求时，再评估将配置映射升级为工作流引擎：

- 不同组织使用不同商机阶段或关口规则。
- 管理员需要自行增加、删除、排序节点。
- 同一阶段存在条件分支、回退或并行审批。
- 运行中的商机需要在流程版本之间迁移。
- 文档、审批、通知和外部 Agent 需要通过可配置动作编排。

届时可将本期的“阶段—文档—模板—生成策略”映射迁入流程定义表。Outline 正文仍由现有业务键定位，已经生成的商机文档与产品 Spec 无需迁移正文。

## 总体架构

### ProductDocumentService

`ProductDocumentService` 只负责产品资料在 Outline 中的目录映射、功能 Spec 初始化、同步和读取，不承担商机推进逻辑。

Outline 目录结构如下：

```text
产品资料
└── 产品
    └── 模块（支持现有三级结构）
        └── 功能 · 设计 Spec
```

业务键如下：

```text
PRODUCT_ROOT
PRODUCT:{productId}
PRODUCT:{productId}:MODULE:{moduleId}
PRODUCT:{productId}:FEATURE:{featureId}:SPEC
```

新建产品、模块或功能后，系统在业务数据提交成功后尝试补齐 Outline 目录或 Spec。Outline 失败不得回滚产品业务数据；失败状态写入对应的 `outline_document_link`，页面允许重试。

产品或模块改名时同步 Outline 标题。模块父级或功能所属模块改变时同步父级关系，不通过删除重建文档实现，以免丢失已经维护的 Spec 正文。

### OpportunityStageDocumentService

`OpportunityStageDocumentService` 泛化现有需求调研报告流程，统一管理以下文档：

| 商机阶段 | 产出物类型 | 模板场景 | 生成策略 |
|---|---|---|---|
| LEAD | RESEARCH_REPORT | OPPORTUNITY_RESEARCH | 模板初稿，人工填写 |
| OPPORTUNITY | DECISION_MINUTES | OPPORTUNITY_DECISION | 模板初稿，人工填写 |
| POC | CLIENT_REQUESTS | OPPORTUNITY_CLIENT_REQUESTS | AI 生成后人工补充 |
| POC | GAP_ANALYSIS | OPPORTUNITY_GAP_ANALYSIS | AI 生成后人工补充 |
| CONTRACT | REVIEW_MINUTES | OPPORTUNITY_REVIEW | 模板初稿，人工填写 |

服务配置为代码内固定映射，不提供动态条件表达式或管理端流程配置器。映射负责声明阶段、产出物类型、标题、模板场景、是否允许 AI 生成和提交后的动作。

每份商机文档的业务键为：

```text
OPPORTUNITY:{opportunityId}:{artifactType}
```

商机目录继续位于 `OPPORTUNITY_ROOT` 下。Outline 是正文唯一事实来源，数据库只保存业务关联、来源模板和同步元数据。

## 产品功能设计 Spec

知识库新增模板场景 `PRODUCT_FEATURE_SPEC`，展示名称为“产品 · 功能设计 Spec”。该场景只允许存在一份已发布、已启用、必需模板。

创建功能 Spec 时：

1. 确保产品根目录存在。
2. 按模块父子关系递归确保模块目录存在。
3. 读取唯一已发布的功能设计 Spec 模板快照。
4. 替换已知占位符，包括产品名称、产品编码、模块名称、模块编码、功能名称、功能编码和功能说明。
5. 在功能所属模块目录下创建独立 Outline 文档。
6. 将 Outline 链接、来源模板 ID 和来源模板修订保存到 `product_feature`。

模板后续重新发布不覆盖已有功能 Spec。产品结构同步只调整标题和父级，不修改 Spec 正文。

产品详情新增“设计文档”页签：左侧显示产品、模块与功能树及同步状态，右侧使用现有 `DocumentWorkspace` 预览和编辑所选功能 Spec。功能列表提供“查看设计 Spec”快捷入口。

管理页面的 Outline 文档中心增加“初始化产品资料”。该操作遍历当前组织的产品、模块和功能，只创建缺失目录或 Spec，不覆盖现有文档，并展示成功、待处理和失败数量。

## AI 文档生成

甲方诉求清单和差距分析报告使用现有 `AiClient` 与 OpenAI-compatible `chat/completions` 客户端，同步生成结构化 Markdown 初稿，不扩展独立 Agent 队列。

### 输入范围

生成输入必须包含：

- 当前商机及客户基本信息。
- 已提交的需求调研报告 Outline 正文。
- 目标文档的知识库模板正文。
- 商机关联的产品基本信息。
- 功能名称、编码、模块、功能说明、版本可用性和功能 Spec 正文。

商机关联产品版本时，读取该版本中 `INCLUDED` 和 `PLANNED` 的功能，并将可用性一并交给 AI。只关联产品而未关联版本时，读取该产品全部未废弃功能。未关联产品时不编造产品能力，保留模板初稿并明确提示用户补充产品关联或人工填写。

AI 使用严格 JSON Schema 返回 `title` 和 `markdown`。返回值必须非空、符合目标文档类型且不得保留模板占位符；校验通过后才写入 Outline。

### 失败降级

准备文档时先从模板创建 Outline 副本，再调用 AI。AI 未配置、连接失败、超时、返回非法结构或上下文不完整时：

- 不删除已创建的模板初稿。
- 页面展示可理解的失败原因。
- 提供“重新生成”操作。
- 用户仍可编辑、保存草稿和人工提交。

如果文档已被人工修改，重新生成前必须二次确认，避免覆盖用户内容。

## 商机推进交互

### LEAD

点击“推进”打开需求调研报告。提交报告后创建正式产出物并推进到 `OPPORTUNITY`，行为保持兼容。

### OPPORTUNITY

点击 PASS 打开决策评审纪要。提交后创建正式产出物并推进到 `POC`。REJECT 沿用现有逻辑，可直接丢单且不要求纪要。

### POC

点击“推进”打开“POC 推进材料”抽屉，显示四项完成度：

- 讲解材料：现有文件上传。
- 甲方诉求清单：AI 生成的 Outline 模板文档。
- POC 得分表：现有正文填写。
- 差距分析报告：AI 生成的 Outline 模板文档。

两份 Outline 文档分别支持生成、保存草稿和提交。所有四项正式提交后才能推进到 `BIDDING`。提交单份文档不自动推进，最终由材料工作区的“完成并推进”触发既有门禁。

### BIDDING

投标文件继续使用文件上传。PASS 或 REJECT 行为不变。

### CONTRACT

“转交实施”抽屉展示合同阶段材料完成度。评审会议纪要使用 Outline 模板文档；中标公示、合同、邮件归档和已盖章合同继续上传。全部必需材料完成后才允许转交实施。REJECT 沿用现有免除产出物规则。

## 数据设计

`product_feature` 增加：

- `outline_link_id BIGINT NULL`
- `source_template_id BIGINT NULL`
- `source_template_revision BIGINT NULL`

`outline_link_id` 唯一并关联 `outline_document_link`；`source_template_id` 关联 `knowledge_item`。每项功能只有一份设计 Spec。

产品和模块目录不增加专用业务表，继续由 `outline_document_link.business_key` 唯一映射。

商机模板文档继续复用 `opportunity_artifact` 的 `outline_link_id`、`source_template_id` 和 `source_template_revision`。阶段门禁对五类模板文档统一要求 `outline_link_id is not null`，避免旧 Markdown 产出物绕过新流程。

知识库新增模板场景：

- `OPPORTUNITY_DECISION`
- `OPPORTUNITY_CLIENT_REQUESTS`
- `OPPORTUNITY_GAP_ANALYSIS`
- `OPPORTUNITY_REVIEW`
- `PRODUCT_FEATURE_SPEC`

已有 `OPPORTUNITY_RESEARCH` 保留。

## API 设计

通用商机文档接口：

```text
POST /api/v1/opportunities/{id}/documents/{artifactType}/prepare
GET  /api/v1/opportunities/{id}/documents/{artifactType}
PUT  /api/v1/opportunities/{id}/documents/{artifactType}
POST /api/v1/opportunities/{id}/documents/{artifactType}/generate
POST /api/v1/opportunities/{id}/documents/{artifactType}/submit
GET  /api/v1/opportunities/{id}/documents/{artifactType}/export?format=...
```

准备请求携带商机版本。保存与提交请求携带 Outline 修订；推进型提交同时携带商机版本。响应继续复用 `DocumentContent`，准备响应附带来源模板信息，提交响应附带最新商机。

已有 `/research-report` 接口保留，对内委托给通用服务，避免破坏现有前端或外部调用。

产品文档接口：

```text
GET  /api/v1/products/{productId}/documents
POST /api/v1/products/{productId}/documents/sync
GET  /api/v1/products/{productId}/features/{featureId}/spec
PUT  /api/v1/products/{productId}/features/{featureId}/spec
GET  /api/v1/products/{productId}/features/{featureId}/spec/export?format=...
POST /api/v1/admin/documents/products/initialize
```

读取需要 `product:read`，编辑与单产品同步需要 `product:write`，全组织初始化需要 `system:manage`。

## 一致性、幂等与错误处理

- 产品业务写入先提交，随后同步 Outline；Outline 失败不能导致产品、模块或功能创建失败。
- 缺少产品功能 Spec 模板时，目录可以建立，功能 Spec 标记失败并允许重试。
- 商机阶段缺少唯一已发布模板时，准备和提交均被阻止，并指出具体模板场景。
- 商机阶段、商机版本、Outline 修订和模板来源在提交前重新校验。
- Outline 更新成功但业务事务失败时，完全相同内容的重试复用现有修订；不同内容仍返回版本冲突。
- 模板重新发布只影响之后创建的文档，不覆盖已有商机文档和功能 Spec。
- AI 失败只影响自动生成，不阻止人工维护文档。
- AI 不得把缺失产品或 Spec 推断为已具备能力；生成提示必须标注资料缺口。
- POC 单份文档提交幂等，重复提交返回当前状态，不重复创建 `opportunity_artifact`。
- 所有 Outline 文档操作继续记录同步状态和审计日志。

## 前端样式

沿用现有飞书项目风格、Ant Design 组件与 `DocumentWorkspace`，不引入新的编辑器或样式库。

- 商机阶段材料使用宽抽屉，顶部显示商机、客户和当前阶段。
- 多材料阶段使用清单卡片展示“未准备、草稿、已提交、失败”。
- AI 文档显示“生成初稿”“重新生成”和清晰的降级提示。
- 保存草稿与正式提交使用不同按钮和反馈文案。
- 产品设计文档页签采用左树右文档布局，窄屏下改为上下布局。
- 文件上传、POC 得分表和现有合同材料交互保持一致。

## 验证策略

后端测试覆盖：

- 产品、三级模块目录与功能 Spec 的创建、改名、移动、重试和存量回填。
- 功能 Spec 模板唯一性、占位符替换和模板修订快照。
- 商机五类模板文档的准备、读取、草稿、生成、提交、导出和重复提交。
- AI 输入包含需求调研报告、正确范围的版本功能与 Spec。
- 未关联产品、AI 未配置、超时、非法响应和残留占位符时的降级。
- POC 多材料门禁、合同转交门禁、PASS 与 REJECT 路径。
- 组织隔离、读取权限、写入权限和管理端初始化权限。
- Flyway 全量迁移与 Java 1.8 编译。

前端测试覆盖：

- 商机各阶段按钮打开正确的材料工作区。
- 决策纪要提交后推进，POC 单文档提交不提前推进。
- POC 四项材料完成度和最终推进。
- 合同评审纪要与附件完成度。
- AI 生成、失败降级、重新生成确认和草稿保护。
- 产品设计文档树、Spec 编辑、同步失败与重试。
- 只读权限下隐藏写入操作。

最终运行后执行后端相关测试、前端相关测试与生产构建、Docker Compose 重建、健康检查和页面 HTTP 检查。业务验收由用户在本地运行环境完成。

## 不在本期范围

- 可配置工作流设计器、流程定义发布和运行实例迁移。
- 将文件附件转换为 Outline 文档。
- 将 POC 得分表转换为模板文档。
- 模板重新发布后批量覆盖已有文档。
- 新增 AI 供应商适配器或独立 Agent 队列。
- 自动确认 AI 生成结论；所有生成文档仍需用户检查并提交。
