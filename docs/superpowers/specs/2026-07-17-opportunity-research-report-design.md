# 商机推进需求调研报告设计

> 日期：2026-07-17  
> 状态：已确认采用方案 A，待书面规格复核

## 目标

替换商机 `LEAD` 阶段现有的“标题 + Markdown 正文”产出物表单。用户点击推进商机时，系统从知识库中已启用、已发布的《需求调研报告》文档模版创建一份独立 Outline 副本；用户在系统内完成填写并提交后，该文档作为 `RESEARCH_REPORT` 产出物关联当前商机，并完成 `LEAD → OPPORTUNITY` 推进。

本功能不修改需求工坊，不在创建商机时提前生成报告，也不修改其他售前阶段的产出物流程。

## 采用方案

采用“Outline 独立副本 + 系统内文档编辑 + 提交推进”方案：

- 知识库继续维护源模版及发布修订。
- 每个商机拥有独立的需求调研报告 Outline 文档。
- MySQL 只保存商机、模版和 Outline 文档映射，不保存新报告正文副本。
- 用户在现有 `DocumentWorkspace` 文档体验中预览、编辑、保存草稿、导出和提交。
- 报告提交成功后才形成商机产出物并推进阶段。

不采用以下方案：

- 跳转 Outline 外部页面完成全部操作：编辑体验与商机推进割裂，系统难以准确识别提交时点。
- 仅把模版正文预填到现有文本框：继续在 MySQL 重复保存 Markdown，违背 Outline 作为文档存储中心的既定架构。
- 直接编辑知识库源模版：不同商机会互相覆盖，无法形成独立业务档案。

## 用户流程

### 正常流程

1. 用户创建商机，商机保持 `LEAD / OPEN`，此时不创建报告。
2. 用户在售前推进页点击该商机的“推进”。
3. 系统查找当前组织唯一一份适用于“商机需求调研”的已启用、已发布知识库模版。
4. 系统创建或恢复该商机的 Outline 目录和需求调研报告副本。
5. 页面打开“需求调研报告”宽抽屉，默认显示文档预览，并允许切换编辑。
6. 用户可以保存草稿；保存草稿只更新 Outline，不改变商机阶段，也不生成正式产出物。
7. 用户点击“提交报告并推进”，系统保存当前标题和 Markdown，登记 `RESEARCH_REPORT` 产出物，并将商机推进为 `OPPORTUNITY / OPEN`。
8. 提交成功后关闭抽屉、刷新售前看板，并在商机详情“阶段产出物”中显示需求调研报告入口。

### 恢复流程

- 用户关闭抽屉后再次点击推进，系统复用同一份报告，不重复创建目录或文档。
- 已保存草稿从 Outline 重新加载。
- 提交完成后报告只读保留，可在商机详情中预览、导出或在 Outline 中打开。

## 模版识别

知识库文档模版继续使用 `document_template_config.stage_code` 表达使用场景，新增稳定场景编码：

`OPPORTUNITY_RESEARCH`

知识库模版编辑页把该编码显示为“商机 · 需求调研”。项目七阶段仍使用原有阶段编码。项目文档初始化只能选择七个项目阶段编码，必须显式排除 `OPPORTUNITY_RESEARCH`，避免商机模版被复制到每个交付项目。

系统不通过标题或本地文件名猜测模版。当前组织必须恰好存在一份满足以下条件的模版：

- `knowledge_item.type = TEMPLATE`
- `knowledge_item.status = PUBLISHED`
- `document_template_config.stage_code = OPPORTUNITY_RESEARCH`
- `document_template_config.enabled = true`
- 已记录发布标题、发布 Markdown 快照和 Outline 发布修订

未找到时提示“请先在知识库发布商机需求调研模版”；找到多份时提示“商机需求调研模版配置重复”，均不创建空文档、不推进商机。

工作区现有 `需求调研报告模板.md` 可作为初始化该知识库模版的内容来源，但应用运行时不直接读取该文件。

## Outline 目录与幂等键

目录结构：

```text
商机文档
└── 客户名称 · 商机名称
    └── 需求调研报告
```

使用以下稳定业务键：

- 商机文档根目录：`OPPORTUNITY_ROOT`
- 商机目录：`OPPORTUNITY:<opportunityId>`
- 需求调研报告：`OPPORTUNITY:<opportunityId>:RESEARCH_REPORT`

`outline_document_link` 继续作为唯一映射依据，文档使用确定性 Outline 兼容 UUID。重复准备、网络超时后的重试和并发点击均恢复同一映射，不按标题查重。

报告文档记录来源模版 ID 和发布修订。模版后续重新发布不覆盖已生成的商机报告。

## 数据模型

为 `opportunity_artifact` 增加：

- `outline_link_id BIGINT NULL`
- `source_template_id BIGINT NULL`
- `source_template_revision BIGINT NULL`

新增外键分别关联 `outline_document_link` 和 `knowledge_item`。`outline_link_id` 建立唯一约束；MySQL 允许其他旧产出物继续保存多个 `NULL`。

新建需求调研报告正文只存 Outline。`content_markdown` 保留兼容历史商机报告，不在本次删除。读取产出物时：

- 有 `outline_link_id`：通过文档中心读取、预览和导出。
- 无 `outline_link_id`：继续展示旧 `content_markdown`。

准备报告时只创建 `outline_document_link`，不提前写 `opportunity_artifact`。正式提交并推进成功时才幂等写入产出物，避免未填写的空报告绕过推进门禁。

## 后端接口

新增：

- `POST /api/v1/opportunities/{id}/research-report/prepare`
  - 校验当前组织、`crm:write`、商机 `LEAD / OPEN` 和乐观锁版本。
  - 定位发布模版，确保 Outline 根目录、商机目录和报告文档存在。
  - 返回文档内容及来源模版元数据。
- `GET /api/v1/opportunities/{id}/research-report`
  - 读取已准备或已提交的报告；历史已提交报告允许只读访问。
- `PUT /api/v1/opportunities/{id}/research-report`
  - 保存草稿到 Outline，使用 Outline 修订号处理并发冲突。
  - 只允许 `LEAD / OPEN` 编辑。
- `POST /api/v1/opportunities/{id}/research-report/submit`
  - 请求包含当前商机版本、文档标题、Markdown 和 Outline 修订号。
  - 先保存 Outline，再在本地事务中幂等登记产出物、校验门禁、推进阶段并记录审计。
- `GET /api/v1/opportunities/{id}/research-report/export?format=md|html|pdf|docx`
  - 复用现有文档导出能力。

原 `POST /api/v1/opportunities/{id}/artifacts` 对 `LEAD + RESEARCH_REPORT` 拒绝旧式 Markdown 新建请求，并提示从商机推进流程填写。其他产出物类型保持兼容。

原普通 `advance` 接口对 `LEAD` 不再接受仅凭旧式 Markdown 产出物直接推进；必须存在带 `outline_link_id` 的正式 `RESEARCH_REPORT`。历史已完成数据保持可读，不做破坏性迁移。

## 一致性与失败处理

Outline 是外部系统，不能与 MySQL 组成同一事务。提交按以下顺序处理：

1. 校验商机仍为请求中的 `LEAD / OPEN / version`。
2. 使用 Outline 修订号保存文档。
3. 开启本地事务，再次锁定并校验商机版本。
4. 幂等插入产出物、推进阶段、写审计。

如果第 2 步失败，商机和产出物不变，编辑器保留本地内容。如果第 3—4 步因并发失败，Outline 已保存的正文作为草稿保留；用户刷新后可基于最新商机和文档修订重试，不会重复创建报告。

错误语义：

- `400`：模版未配置、模版重复、报告仍为空或仍包含未替换占位内容。
- `404`：商机、模版或文档不属于当前组织。
- `409`：商机版本变化、商机已推进、Outline 修订冲突或报告正在初始化。
- `502/503`：Outline 不可用；不推进商机。

提交校验至少要求 Markdown 非空，并拒绝仍包含 `{{...}}` 占位符的报告，防止原样模版成为正式产出物。保存草稿不执行该完整性校验。

## 前端设计

只改动 `LEAD` 阶段推进体验：

- 点击“推进”后不再先调用普通推进接口，也不打开旧“补充产出物”表单。
- 展示文档准备状态；完成后打开宽度接近项目文档抽屉的报告工作区。
- 复用 `DocumentWorkspace` 的预览、编辑、修订冲突、Outline 深链和四格式导出能力。
- 为 `DocumentWorkspace` 增加可选的“提交”动作；普通项目和知识文档行为保持不变。
- 报告工作区提供“保存草稿”和主按钮“提交报告并推进”。
- 提交成功刷新 `opportunities`、商机详情和产出物查询。
- 模版或 Outline 失败时在抽屉内展示明确原因和重试，不回退到空白文本框。

旧“补充产出物”抽屉在 `LEAD` 阶段不再列出 `RESEARCH_REPORT`；其他阶段及文件上传逻辑不变。

商机详情对新报告显示“预览报告”“在 Outline 中打开”“导出”入口，不在列表中展开完整 Markdown。历史旧报告继续按原正文展示。

## 权限与安全

- 读取报告沿用 `crm:read`，准备、保存和提交沿用 `crm:write`。
- 所有商机、模版、Outline 映射和产出物查询显式限制当前组织。
- 后端不信任前端提交的模版 ID、Outline link ID、组织 ID、阶段或产出物类型。
- Outline API Token 继续只从组织级加密配置读取，不返回前端。
- Markdown 预览继续使用现有服务端渲染与 HTML 清理链路。

## 测试设计

后端：

- 准备报告时从唯一发布模版生成独立 Outline 副本。
- 重复准备和超时恢复不产生重复目录、文档或映射。
- 缺少模版、重复模版、未发布模版、Outline 失败均不推进商机。
- 保存草稿不产生正式产出物、不推进阶段。
- 提交后正文保存在 Outline，产出物关联 link 和来源修订，商机推进到 `OPPORTUNITY`。
- 空正文或残留 `{{...}}` 占位符不能提交。
- Outline 修订冲突、商机乐观锁冲突和重复提交保持幂等。
- 组织隔离及 `crm:read` / `crm:write` 权限。
- 历史 `content_markdown` 报告仍可读取。

前端：

- `LEAD` 推进打开报告工作区，不出现旧标题和正文表单。
- 模版内容加载后可预览、编辑和保存草稿。
- 提交调用报告专用接口，成功后关闭抽屉并刷新商机阶段。
- 模版缺失、Outline 失败和修订冲突显示可恢复提示。
- 其他阶段产出物表单和文件上传行为不回归。
- 商机详情能打开新报告，并兼容历史报告展示。

用户负责最终浏览器验收；开发过程仍保留自动化回归测试和构建检查，避免把基础编译或接口错误交给人工发现。

## 不在本次范围

- 修改需求工坊或从调研报告自动拆分需求。
- 创建商机时提前生成报告。
- 为其他售前产出物全面引入模版体系。
- 通用审批流、模版规则引擎或动态门禁配置器。
- 自动把工作区 Markdown 文件作为运行时模版读取。
- 回填历史 Markdown 报告到 Outline；历史迁移可后续单独执行。

## 验收标准

1. 新建商机后不产生报告；在 `LEAD` 点击推进才生成或恢复报告。
2. 报告初始内容来自知识库当前发布的 `OPPORTUNITY_RESEARCH` 模版快照。
3. 用户可在系统中填写、保存草稿并提交，报告正文只以 Outline 为准。
4. 提交成功后商机进入 `OPPORTUNITY`，详情可追踪报告、来源模版和发布修订。
5. 失败或并发冲突不误推进、不重复创建、不丢失已保存草稿。
6. 现有其他售前阶段、项目文档和知识库文档功能保持正常。
