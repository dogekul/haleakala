import { pathToFileURL } from 'node:url';

const definitions = [];

function group(domain, module, actor, rows) {
  for (const [code, name, purpose, object, control, evidence, metric] of rows) {
    definitions.push([code, { code, name, domain, module, actor, purpose, object, control, evidence, metric }]);
  }
}

group('法规与规则中心', '法规库', '法规管理员', [
  ['LAW-INGEST', '法规采集入库', '把监管原文转化为可检索、可追溯的正式法规资产', '监管法规原文及附件', '发文机关、文号、发布日期、生效日期与原文哈希必须同时校验', '采集来源、原文快照和入库校验单', '入库成功率与重复拦截率'],
  ['LAW-VERSION', '法规版本管理', '维护法规修订、废止和替代关系，保证审查引用正确版本', '同一法规的历次版本', '新版本必须关联前序版本并记录修订条款、施行日期和替代关系', '版本链、条款差异和发布审批记录', '版本链完整率'],
  ['LAW-EFFECT', '效力状态管理', '按时间和监管状态控制法规是否参与审查', '法规效力状态', '现行、尚未生效、失效、废止四类状态由日期与人工确认共同决定', '状态变更记录和依据文件', '效力状态准确率'],
  ['LAW-SCOPE', '适用范围标注', '明确法规适用的机构、地区、渠道、客群和产品范围', '法规适用边界', '适用范围必须至少标注机构类型、业务类型、地区和生效区间', '范围标签及复核记录', '范围标签覆盖率'],
]);
group('法规与规则中心', '内规库', '制度管理员', [
  ['INTERNAL-INGEST', '制度入库', '沉淀经批准的内部制度并支持合规检索', '内部制度正文及附件', '制度编号、发布部门、审批单、生效日期和正文哈希完整后方可入库', '审批单、正文快照和入库记录', '制度入库完整率'],
  ['INTERNAL-VERSION', '制度版本管理', '管理制度草拟、现行、废止及历史版本', '内部制度版本', '现行版本唯一，换版必须保留修订说明并冻结旧版正文', '版本链、修订说明和审批历史', '现行版本唯一率'],
  ['INTERNAL-MAP', '法规内规映射', '建立监管条款到内部制度控制措施的双向追溯', '法规条款与制度条款映射', '每条映射必须包含映射类型、责任人、有效期和覆盖结论', '条款映射矩阵与复核意见', '监管条款覆盖率'],
  ['INTERNAL-OWNER', '责任部门维护', '明确制度及条款的牵头与协同责任部门', '制度责任归属', '牵头部门唯一，协同部门可多选，责任变更必须经制度管理员确认', '责任分工记录与变更日志', '责任人有效率'],
]);
group('法规与规则中心', '规则编排', '规则运营人员', [
  ['RULE-CONFIG', '审查规则配置', '把法规要求配置为可执行的审查条件和结论', '结构化审查规则', '规则必须包含适用条件、检测逻辑、风险等级、法规依据和建议动作', '规则配置快照与审批记录', '配置完整率'],
  ['RULE-TEST', '规则测试', '在发布前验证规则命中、漏报和误报表现', '候选规则与测试样本', '正例、反例、边界例均通过且无阻断级缺陷方可提交发布', '测试集、执行结果和缺陷清单', '测试通过率'],
  ['RULE-PUBLISH', '规则发布', '受控地将已验证规则投放到生产审查链路', '待发布规则版本', '发布必须经过复核，指定生效时间、适用范围并具备一键回滚版本', '发布单、审批流和投放记录', '发布成功率'],
  ['RULE-HISTORY', '规则版本回溯', '查询任一审查时点实际使用的规则内容', '规则历史版本', '历史版本只读并保留版本号、差异、发布人、生效区间和关联结论', '版本快照与引用任务清单', '历史可追溯率'],
]);
group('审查任务中心', '任务受理', '审查经办人', [
  ['TASK-CREATE', '任务创建', '建立一次可追踪的消保审查工作单', '审查任务', '业务类型、送审部门、材料清单、期望完成时间和联系人完整后生成任务编号', '创建表单、任务编号和操作日志', '一次创建成功率'],
  ['TASK-CLASSIFY', '任务分类分级', '按业务场景和风险影响确定任务处理策略', '待受理审查任务', '分类结果由材料类型、发布范围、客户影响和监管敏感度共同计算', '分类依据、风险等级和人工调整理由', '分类准确率'],
  ['TASK-ASSIGN', '任务分派', '将任务分配给具备权限和能力的审查人员', '已分类审查任务', '仅可分派给有对应业务权限且容量未超阈值的在岗人员', '分派记录、人员负载和接收确认', '首次分派及时率'],
  ['TASK-REOPEN', '撤回与重开', '在受控条件下撤回未完成任务或重开已结束任务', '审查任务状态', '撤回需说明原因；重开需关联原结论、触发原因和新的处理时限', '状态轨迹、原因和审批记录', '异常状态闭环率'],
]);
group('审查任务中心', '材料管理', '送审人员', [
  ['MATERIAL-UPLOAD', '材料上传预览', '安全上传并在线预览送审材料', 'Office、PDF、图片及压缩附件', '扩展名、MIME、病毒扫描、大小和哈希校验全部通过后才可入库', '文件元数据、扫描结果和预览快照', '上传与预览成功率'],
  ['MATERIAL-VERSION', '材料版本管理', '保留材料换版过程并明确当前审查基线', '送审材料版本', '同一材料仅有一个当前版，新版必须说明变更点且旧版不可覆盖', '版本链、差异摘要和上传人记录', '版本链完整率'],
  ['MATERIAL-CHECK', '完整性校验', '依据业务类型自动核对必交材料是否齐备', '任务材料清单', '必交项按任务分类动态加载，缺失、过期、打不开均判定不完整', '校验明细和缺件清单', '完整性识别准确率'],
  ['MATERIAL-SUPPLEMENT', '补充材料', '闭环收集审查过程中缺失或需要澄清的材料', '补件请求与补充文件', '补件请求必须指明材料项、原因、责任人和截止时间，提交后重新校验', '补件通知、文件和签收记录', '按期补齐率'],
]);
group('审查任务中心', '流程编排', '流程管理员', [
  ['FLOW-CONFIG', '审批流配置', '按任务类型配置审查、复核和决策节点', '审批流程定义', '流程必须有唯一开始结束节点，条件分支有兜底路径且不存在不可达节点', '流程图、校验报告和发布版本', '流程配置有效率'],
  ['FLOW-TRANSFER', '转办与加签', '处理经办人变化和额外专业意见需求', '在途审批节点', '转办移交全部责任；加签保留原处理人且明确前加签或后加签顺序', '转办单、加签意见和节点轨迹', '协同处理及时率'],
  ['FLOW-URGE', '催办与超时', '提醒待办人员并对超时节点实施升级', '流程待办', '临期、到期和逾期按配置触发一次，重复催办受冷却时间限制', '通知回执、催办日志和升级记录', '节点按时完成率'],
  ['FLOW-SLA', '处理时限配置', '为不同任务和风险等级设定可计算的服务时限', 'SLA 日历与规则', '时限按工作日历计算，暂停须有原因和授权，恢复后自动重算截止时间', 'SLA 配置、暂停区间和计算明细', 'SLA 达成率'],
]);
group('智能审查引擎', '内容解析', '算法运营人员', [
  ['PARSE-OFFICE', 'Office/PDF 解析', '将 Office/PDF 转为保留页码和层级的结构化文本', 'Office 与 PDF 文件', '解析必须保留页码、段落、标题、批注和坐标，失败文件进入人工处理队列', '解析文本、版面坐标和错误报告', '页面解析成功率'],
  ['PARSE-OCR', 'OCR 识别', '识别扫描件和图片中的中文、数字及印章区域', '图片与扫描 PDF', '低于置信度阈值的字符必须标红并禁止直接作为高风险结论唯一依据', 'OCR 文本、坐标和字符置信度', '字符准确率'],
  ['PARSE-TABLE', '表格解析', '还原跨页表格的行列、表头和合并关系', '文档表格区域', '跨页续表需合并表头，单元格坐标与原页位置必须可双向定位', '结构化表格、单元格坐标和解析告警', '表格结构准确率'],
  ['PARSE-FIELD', '关键字段抽取', '抽取产品、费用、收益、风险和客户条件等审查字段', '解析后的文档内容', '字段值必须保留原文证据、置信度和抽取模型版本，冲突值进入复核', '字段清单、证据片段和置信度', '字段抽取 F1'],
]);
group('智能审查引擎', '风险识别', '合规审查人员', [
  ['RISK-RULE', '规则命中', '执行确定性规则并生成带依据的风险项', '结构化材料与现行规则', '仅执行审查时点有效且适用范围匹配的规则，每次命中记录输入快照', '命中规则、证据位置和法规依据', '规则命中准确率'],
  ['RISK-SEMANTIC', '语义风险识别', '发现规则难以穷举的误导、弱化和不公平语义', '材料文本与语义模型', '置信度低于阈值或误报高发类别必须进入人工复核，不能自动形成最终结论', '模型版本、提示上下文和风险解释', '精确率与人工采纳率'],
  ['RISK-MERGE', '风险合并去重', '合并同一证据和同一监管义务产生的重复风险', '规则与模型风险候选项', '相同材料位置、法规义务和整改动作的候选项合并，保留全部来源链路', '合并簇、来源风险和主风险项', '重复风险压降率'],
  ['RISK-LEVEL', '风险自动分级', '按监管影响和客户影响给风险确定优先级', '已去重风险项', '等级由违规性质、影响客群、传播范围、可逆性和历史频次综合计算', '评分明细、等级和人工调整理由', '分级一致率'],
]);
group('智能审查引擎', '可解释输出', '合规审查人员', [
  ['EXPLAIN-LOCATE', '原文定位', '从风险项一键回到材料中的准确证据位置', '风险证据坐标', '定位需精确到文件版本、页码、段落或单元格，材料换版后不得错链', '高亮片段和双向定位链接', '定位成功率'],
  ['EXPLAIN-BASIS', '规则依据说明', '展示风险结论对应的监管和内规依据', '风险项与规则引用', '依据必须展示法规名称、条款、效力状态、适用范围和引用时点', '依据卡片与版本快照', '依据完整率'],
  ['EXPLAIN-PROCESS', '判断过程说明', '向复核人员说明规则或模型如何得到当前结论', '检测执行轨迹', '说明内容区分事实输入、判断条件、模型推断和最终结论，不暴露敏感系统提示', '判断步骤、阈值和模型信息', '解释可理解率'],
  ['EXPLAIN-SUGGEST', '修改建议生成', '基于风险依据给出可执行且不过度承诺的修改建议', '已确认风险及原文', '建议必须保持业务事实不变、对应具体风险、标识 AI 生成并由人工确认后使用', '建议文本、差异预览和采纳记录', '建议采纳率'],
]);
group('人工复核中心', '复核工作台', '复核人员', [
  ['REVIEW-CONFIRM', '结论确认', '确认机器识别风险并形成正式人工结论', '待复核风险项', '确认前必须查看原文、依据和建议，确认后记录复核人、时间与意见', '确认结论和电子留痕', '复核完成率'],
  ['REVIEW-OVERRIDE', '人工改判', '允许有权限人员纠正风险等级或结论', '机器风险结论', '改判必须选择原因、填写事实依据，重大风险降级需二次复核', '改判前后值、理由和审批记录', '改判一致率'],
  ['REVIEW-IGNORE', '忽略并说明', '将不适用或可接受的命中从整改范围中排除', '误报或例外风险项', '忽略必须选择不适用、重复、已获豁免等原因并上传支撑材料', '忽略理由、附件和授权记录', '忽略复查通过率'],
  ['REVIEW-BATCH', '批量复核', '提高同质低风险项的复核效率', '同任务同规则风险集合', '仅同规则、同等级且无重大风险的项目可批量处理，操作前展示影响数量', '批次编号、项目清单和统一意见', '批量复核节省时长'],
]);
group('人工复核中心', '协同批注', '审查协作人员', [
  ['COLLAB-ANNOTATE', '原文批注', '围绕具体材料位置开展审查讨论', '材料原文选区', '批注锚点绑定文件版本和坐标，正文换版后显示为历史批注而非静默迁移', '批注内容、锚点和版本信息', '批注定位有效率'],
  ['COLLAB-PEOPLE', '人员协作', '邀请业务、法务和产品人员参与指定问题处理', '任务协作者', '邀请范围受数据权限约束，外部门人员仅可访问被点名的任务与材料', '邀请记录、访问范围和已读状态', '协作响应率'],
  ['COLLAB-SUMMARY', '意见汇总', '把分散批注整理为可决策的意见清单', '批注与回复线程', '汇总按风险项归并并标识一致、分歧、待确认，不得丢失原始意见链接', '意见摘要、分歧项和来源链接', '意见归并准确率'],
  ['COLLAB-RECORD', '处理记录', '完整保留协同过程中的操作和结论变化', '协作活动流', '新增、编辑、回复、解决和重开动作均按时间顺序不可篡改记录', '活动日志与操作者信息', '协同记录完整率'],
]);
group('人工复核中心', '专家会审', '会审秘书', [
  ['EXPERT-START', '会审发起', '对复杂或重大消保风险组织跨专业会审', '重大风险及会审议题', '发起时必须明确议题、材料、参会角色、主持人、截止时间和决策方式', '会审单、参会名单和材料包', '会审按期召开率'],
  ['EXPERT-ESCALATE', '重大风险升级', '将符合阈值的风险升级至有权决策层级', '重大风险项', '涉及群体客户、监管处罚或大范围传播的风险不得在原层级直接关闭', '升级原因、接收人和处置时限', '重大风险升级及时率'],
  ['EXPERT-OPINION', '会审意见', '结构化收集专家判断、依据和保留意见', '会审议题', '每位专家意见独立留存，修改保留版本，少数意见不得被汇总过程删除', '专家意见、引用依据和版本', '专家意见提交率'],
  ['EXPERT-DECISION', '决策留痕', '形成具有责任主体和执行要求的会审决策', '会审结论', '决策必须包含结论、责任人、整改动作、期限、表决结果和异议处理', '决策单、签署记录和任务关联', '决策要素完整率'],
]);
group('整改闭环', '整改管理', '整改责任人', [
  ['RECTIFY-ASSIGN', '问题指派', '把已确认风险转化为明确责任和期限的整改任务', '已确认风险项', '责任部门、责任人、整改要求、验收标准和截止日期完整后方可下发', '整改任务单和签收记录', '指派及时率'],
  ['RECTIFY-FEEDBACK', '整改反馈', '提交整改说明、修订材料和完成证据', '在途整改任务', '反馈必须逐项对应整改要求，材料换版需标注修改位置并重新接受审查', '整改说明、新版材料和证据附件', '一次反馈完整率'],
  ['RECTIFY-REVIEW', '复审', '验证整改结果是否消除原风险且未引入新风险', '整改反馈和新旧材料', '复审必须重跑相关规则并由非原整改责任人确认通过或退回', '复审结论、复测结果和退回原因', '一次复审通过率'],
  ['RECTIFY-CLOSE', '关闭与重开', '受控关闭已通过整改并在复发时重开问题', '整改问题状态', '仅复审通过可关闭；新证据、材料再变更或抽查失败可重开并继承历史', '关闭证明、重开原因和状态轨迹', '闭环率与重开率'],
]);
group('整改闭环', '版本对比', '复审人员', [
  ['DIFF-MATERIAL', '材料差异对比', '准确展示整改前后材料的文字和版式变化', '两个材料版本', '对比按段落、表格单元格和图片区域展示新增、删除、修改并过滤无意义格式变化', '差异清单和双栏视图', '有效差异识别率'],
  ['DIFF-ISSUE', '问题前后对比', '验证每个风险点在新版材料中的处理结果', '风险项与前后证据', '每个问题必须标记已解决、部分解决、未解决或无法定位并保留前后证据', '问题对比卡和复审意见', '问题映射完整率'],
  ['DIFF-OPEN', '未整改项识别', '自动筛出仍未消除或仅部分消除的风险', '问题对比结果', '原风险证据仍存在、替换文本仍命中或无法定位时不得自动判定已整改', '未整改清单和判定依据', '未整改识别召回率'],
  ['DIFF-EXPORT', '差异结果导出', '输出可用于沟通和归档的版本差异报告', '材料及问题差异', '导出内容包含任务、版本、差异摘要、问题状态、生成时间和水印', 'PDF/Word 差异报告与导出日志', '导出成功率'],
]);
group('整改闭环', '提醒升级', '整改管理员', [
  ['REMIND-DUE', '到期提醒', '在整改截止前提醒责任人准备反馈', '未完成整改任务', '按风险等级配置提前天数，同一节点同一渠道每日最多提醒一次', '提醒计划、发送回执和已读状态', '到期前触达率'],
  ['REMIND-OVERDUE', '逾期升级', '对超过截止时间的整改任务逐级升级', '逾期整改任务', '逾期后按 1、3、5 个工作日升级至负责人、部门主管和合规负责人', '升级层级、通知回执和响应记录', '逾期响应时长'],
  ['REMIND-OWNER', '责任追踪', '持续明确整改任务当前责任主体和转移过程', '整改责任链', '责任人离职或转岗时任务不得悬空，转移需新责任人确认并保留原记录', '责任变更链和签收状态', '无主任务数量'],
  ['REMIND-SLA', 'SLA 统计', '统计各组织和风险等级的整改时效表现', '整改时限数据', 'SLA 扣除经批准暂停时段，逾期按首次有效反馈和最终关闭分别统计', 'SLA 明细、组织汇总和口径版本', 'SLA 达成率'],
]);
group('消保专项', '产品服务审查', '产品合规经理', [
  ['PRODUCT-ACCESS', '产品准入审查', '在产品上线前验证消费者权益保护准入条件', '产品方案、协议和流程', '准入必须覆盖目标客群、风险等级、定价、退出机制、投诉渠道和消保评审结论', '准入检查表和上线意见', '准入问题拦截率'],
  ['PRODUCT-FAIRNESS', '条款公平性检查', '识别格式条款中权利义务失衡或显失公平的约定', '产品协议与服务条款', '格式条款须检查权利义务对等、免责限制、单方变更和显失公平情形', '问题条款、法规依据和公平性结论', '不公平条款识别率'],
  ['PRODUCT-FEE', '收费检查', '验证收费项目、标准和披露是否合规一致', '价目表、协议和展示页面', '收费名称、金额、计费方式、减免条件和提前解约费用必须一致且可理解', '收费对照表和差异问题', '收费一致率'],
  ['PRODUCT-DISCLOSE', '信息披露检查', '确保客户决策所需关键信息完整、清晰、及时展示', '产品说明和签约页面', '收益、风险、费用、限制条件和退出方式必须在关键决策前显著披露', '披露清单、页面证据和缺失项', '披露完整率'],
]);
group('消保专项', '营销宣传审查', '营销合规人员', [
  ['MARKETING-WORD', '禁限用语识别', '识别宣传材料中的绝对化、承诺性和监管禁限用表达', '广告文案、海报和视频字幕', '禁用语直接标为高风险，限制用语必须结合资质、数据来源和限定条件判断', '命中词、上下文和规则依据', '禁限用语召回率'],
  ['MARKETING-MISLEAD', '夸大误导识别', '发现片面比较、虚构效果和弱化限制条件等误导表达', '营销内容及数据声明', '效果或排名声明必须有可验证来源、统计口径和有效期，不得突出收益弱化风险', '误导片段、事实核验和修改建议', '误导识别精确率'],
  ['MARKETING-DISCLOSE', '风险揭示检查', '检查宣传场景是否同步、显著地揭示必要风险', '营销页面和话术', '风险揭示需与收益表述同屏或同时出现，字号、时长和可见性达到渠道标准', '风险文案、展示参数和页面截图', '风险揭示合格率'],
  ['MARKETING-REVIEW', '宣传材料复核', '形成营销材料发布前的最终消保复核结论', '已完成机器审查的宣传材料', '重大风险未关闭、材料版本变化或批准有效期届满时禁止使用原复核结论', '复核单、准用版本和有效期限', '复核按期完成率'],
]);
group('消保专项', '销售行为审查', '销售合规人员', [
  ['SALES-SUITABILITY', '适当性检查', '验证产品风险与客户风险承受能力和需求相匹配', '客户画像、产品等级和销售记录', '客户测评有效、产品风险不高于承受等级且特殊客群保护措施满足后方可通过', '匹配结果、测评快照和例外审批', '适当性匹配率'],
  ['SALES-DUTY', '告知义务检查', '检查销售过程是否完成必要说明、风险提示和客户确认', '双录、签署和销售话术', '关键风险、费用、限制和退出条件须明确告知并取得可验证的客户确认', '告知节点、录音录像片段和签署记录', '告知义务完成率'],
  ['SALES-INDUCE', '诱导销售识别', '识别承诺收益、隐瞒风险、代客操作和不当利益诱导', '销售录音、聊天和操作日志', '出现保本承诺、代签代录、催促跳过阅读或贬损竞品时进入高风险人工调查', '可疑片段、行为序列和调查结论', '诱导行为识别率'],
  ['SALES-MATERIAL', '销售材料复核', '确认一线使用材料与批准版本一致且仍在有效期', '销售端展示和发送材料', '材料哈希、版本号、批准编号和有效期任一不匹配即禁止继续使用', '版本比对、渠道清单和处置记录', '未批准材料拦截率'],
]);
group('消保专项', '客诉与事件', '消保事件经理', [
  ['COMPLAINT-CLASSIFY', '投诉分类', '统一识别投诉业务、问题、渠道、客群和严重程度', '客户投诉工单', '分类至少包含产品、问题类型、责任环节、客户影响和紧急程度，并保留人工修正', '分类标签和置信度', '投诉分类准确率'],
  ['COMPLAINT-EVENT', '重大事件识别', '从投诉聚集和单体严重案例中识别重大消保事件', '投诉、舆情和损失数据', '涉及群体性、重大损失、敏感客群或监管转办时立即升级且不得等待批量统计', '事件告警、触发条件和升级回执', '重大事件发现时长'],
  ['COMPLAINT-CAUSE', '根因分析', '定位投诉背后的产品、流程、人员或系统根因', '投诉案例与业务链路', '根因结论必须有样本、数据和流程证据支撑，并区分直接原因与系统性原因', '因果图、证据清单和改进建议', '根因验证通过率'],
  ['COMPLAINT-FEEDBACK', '规则回流', '把已验证投诉根因转化为审查规则和改进需求', '已关闭投诉根因', '回流项需指定目标规则、样本、责任人和验证指标，发布前通过历史数据回放', '规则需求、样本集和验证结果', '回流规则有效率'],
]);
group('报告与洞察', '审查报告', '报告编制人员', [
  ['REPORT-CREATE', '报告生成', '将任务、材料、风险和整改信息生成正式审查报告', '审查任务全量结果', '报告仅引用当前有效结论，重大风险、依据、整改要求和未决事项不得遗漏', '报告正文、数据快照和生成记录', '报告一次生成成功率'],
  ['REPORT-TEMPLATE', '报告模板管理', '维护不同业务场景的受控报告版式和章节', '报告模板', '模板需定义适用任务、必填章节、字段映射、页眉水印和发布版本', '模板版本、预览和审批记录', '模板渲染一致率'],
  ['REPORT-EXPORT', '报告导出', '将报告安全导出为可流转的 PDF、Word 或 Markdown', '已生成审查报告', '导出必须保留标题层级、表格、引用和水印，并根据权限控制可选格式', '导出文件、文件哈希和下载日志', '多格式导出成功率'],
  ['REPORT-ARCHIVE', '报告归档', '把已定稿报告作为不可覆盖的审查档案保存', '已批准审查报告', '归档编号、保管期限、定稿哈希完整后转为只读，修订必须形成新归档版本', '归档文件、审批单和版本链', '归档完整率'],
]);
group('报告与洞察', '风险看板', '合规管理人员', [
  ['BOARD-DISTRIBUTION', '风险分布', '按业务、组织、等级和规则查看风险结构', '审查风险明细', '筛选口径与明细一致，权限过滤先于聚合且总数可下钻核对', '聚合指标、筛选条件和明细链接', '看板数据一致率'],
  ['BOARD-TREND', '风险趋势', '识别风险数量、等级和闭环时长的周期变化', '按日聚合的风险数据', '趋势按发生、确认、关闭三种时间口径切换，跨期对比使用同一规则版本口径', '时间序列和同比环比结果', '趋势数据准时率'],
  ['BOARD-FREQUENT', '高频问题', '发现重复出现且影响范围大的规则或问题类型', '已确认风险和投诉', '高频排名同时考虑出现次数、涉及客户数、组织数和持续周期，支持排除已知测试数据', '问题榜单、影响范围和样本', '高频问题整改率'],
  ['BOARD-ORG', '组织对比', '比较各组织风险暴露与整改表现并支持公平评价', '组织维度指标', '对比需同时展示业务量归一化指标和绝对量，小样本组织明确标识不可直接排名', '组织指标、样本量和口径说明', '组织数据覆盖率'],
]);
group('报告与洞察', '价值度量', '消保运营人员', [
  ['VALUE-EFFICIENCY', '审查人效', '度量自动化对审查处理能力和时长的改善', '任务工时和处理量', '人效按有效完成任务计算，剔除暂停时间并区分机器、人工和协同耗时', '工时明细、处理量和基线', '单任务平均工时'],
  ['VALUE-ADOPTION', '人工采纳率', '衡量机器风险和修改建议被人工接受的程度', '机器建议与人工结论', '采纳、改判、忽略按最终人工动作统计，批量操作仍按风险项计数', '建议状态、原因和模型版本', '人工采纳率'],
  ['VALUE-RECTIFY', '整改效果', '评价问题是否真正减少并防止重复发生', '整改前后风险和复发数据', '效果同时考察一次复审通过率、重开率、同类问题复发率和关闭周期', '整改指标、前后基线和趋势', '同类问题复发率'],
  ['VALUE-ROI', 'ROI 分析', '量化系统节省成本、避免损失与投入之间的关系', '人效、风险损失和投入数据', 'ROI 分子分母口径、数据来源和假设必须可查看，避免损失单独展示不与已实现收益混算', '成本收益明细、假设和计算版本', '年度 ROI'],
]);
group('集成开放', '身份组织集成', '系统管理员', [
  ['IAM-SSO', 'SSO 登录', '通过企业身份源统一认证并保留本地应急入口', '企业用户身份', '支持 SAML 或 OIDC，校验签名、issuer、audience、nonce 和账号状态后建立会话', '认证事件、身份源和失败原因', 'SSO 登录成功率'],
  ['IAM-ORG', '组织同步', '同步企业组织树并保持层级和状态一致', '外部组织目录', '以外部唯一标识幂等同步，删除组织先停用且存在成员或业务数据时不得物理删除', '同步批次、差异和失败明细', '组织同步一致率'],
  ['IAM-USER', '用户同步', '同步员工账号、组织、岗位和在离职状态', '企业用户目录', '用户名或外部 ID 唯一，离职立即禁用登录但保留历史审计归属', '用户差异、状态变更和同步日志', '用户同步成功率'],
  ['IAM-ROLE', '角色映射', '将身份源群组或岗位映射为系统业务角色', '外部群组与系统角色', '映射遵循最小权限，冲突规则明确优先级，高权限角色变更需人工审批', '映射规则、权限差异和审批记录', '角色映射准确率'],
]);
group('集成开放', '业务系统集成', '集成管理员', [
  ['BIZ-OA', 'OA/BPM 集成', '与 OA/BPM 双向传递审批任务、状态和意见', '审批单与待办', '外部单号全局幂等，状态只允许顺序推进，回调验签失败不得更新本地任务', '请求响应、状态映射和回调日志', '状态同步成功率'],
  ['BIZ-PRODUCT', '产品系统集成', '从产品系统获取产品、版本、条款和上下架状态', '产品主数据', '产品编码和版本唯一，来源字段只读，本地扩展字段不得在同步时覆盖', '产品快照、字段映射和同步批次', '产品数据一致率'],
  ['BIZ-MARKETING', '营销系统集成', '接收待发布营销材料并回传审查结论', '营销活动与素材', '活动 ID、素材版本和渠道作为幂等键，未通过结论必须阻断发布状态回传', '素材包、结论回执和重试日志', '结论回传及时率'],
  ['BIZ-CONTRACT', '合同系统集成', '接收合同条款审查请求并同步风险与定稿版本', '合同及条款版本', '合同编号和版本唯一，定稿前重新校验材料哈希，风险关闭状态与合同版本绑定', '合同快照、风险清单和定稿回执', '合同版本匹配率'],
]);
group('集成开放', '开放接口', '开放平台管理员', [
  ['OPEN-API', 'API 管理', '统一发布、授权、限流和下线开放 API', 'API 定义与客户端', '每个 API 必须定义版本、鉴权、权限范围、限流、错误码和弃用日期', '接口定义、密钥授权和调用日志', 'API 可用率'],
  ['OPEN-EVENT', '消息订阅', '向授权系统可靠推送任务、风险和整改领域事件', '事件主题与订阅者', '事件含唯一 ID 和版本，至少一次投递，消费者可幂等处理并支持失败重放', '订阅配置、投递记录和死信', '事件投递成功率'],
  ['OPEN-IMPORT', '批量导入', '通过受控模板批量录入法规、任务或配置数据', '结构化导入文件', '先校验后落库，错误行不影响正确行需明确选择，重复键按导入策略处理', '校验报告、导入批次和行级结果', '导入有效行成功率'],
  ['OPEN-EXPORT', '批量导出', '按权限异步导出大批量业务数据', '查询结果集', '导出沿用查询数据权限，敏感字段脱敏，文件限时有效且达到阈值必须审批', '导出任务、文件哈希和下载记录', '导出任务成功率'],
]);
group('安全审计', '权限控制', '安全管理员', [
  ['PERMISSION-RBAC', 'RBAC', '通过用户、角色和权限点控制系统能力访问', '角色权限关系', '默认拒绝、最小授权，角色变更即时生效，高权限角色必须双人审批', '授权关系、审批单和鉴权日志', '越权拦截率'],
  ['PERMISSION-SCOPE', '数据范围', '按组织、本人、参与任务和自定义范围过滤业务数据', '业务对象可见范围', '所有列表、详情、导出和统计使用同一数据范围策略，聚合前先过滤', '策略命中、数据范围和访问日志', '数据范围一致率'],
  ['PERMISSION-DOWNLOAD', '下载控制', '控制材料、报告和导出文件的下载资格与次数', '受保护文件', '下载需同时满足文件权限、业务权限和有效期，大批量或敏感文件触发审批', '下载授权、次数和终端信息', '未授权下载拦截率'],
  ['PERMISSION-WATERMARK', '动态水印', '在预览和下载内容上标识访问者以降低泄露风险', '文档预览与导出文件', '水印包含姓名、账号、时间和追踪码，密度不可由客户端关闭且不遮挡正文', '水印参数、追踪码和访问记录', '水印覆盖率'],
]);
group('安全审计', '数据安全', '数据安全管理员', [
  ['DATA-ENCRYPT', '传输存储加密', '保护材料、凭据和敏感业务数据在传输与存储中的机密性', '敏感数据与密钥', '外部传输使用 TLS，敏感字段和文件静态加密，密钥分权管理并定期轮换', '加密策略、密钥版本和轮换日志', '加密覆盖率'],
  ['DATA-MASK', '敏感字段脱敏', '按角色和使用场景隐藏客户与员工敏感信息', '证件号、手机号、账户等字段', '列表默认脱敏，查看明文需权限、业务理由和短时授权，导出采用更严格策略', '脱敏规则、明文访问审批和日志', '敏感字段脱敏率'],
  ['DATA-RETENTION', '数据保留销毁', '按档案和隐私要求管理数据生命周期', '业务数据、文件和日志', '每类数据配置保留期限，期满进入销毁审批，执行后验证主存储与备份均不可恢复', '保留策略、销毁审批和不可恢复验证报告', '到期数据处置及时率'],
  ['DATA-MODEL', '模型数据边界', '限制审查材料在模型训练和推理过程中的使用范围', '模型输入输出与训练样本', '生产材料默认不得用于训练，推理最小化传输，跨境、第三方模型和样本入集需单独授权', '数据使用目的、模型调用和授权记录', '越界调用数量'],
]);
group('安全审计', '审计追踪', '审计人员', [
  ['AUDIT-OPERATION', '操作日志', '记录关键业务和管理操作以支持责任追溯', '用户操作事件', '创建、修改、删除、审批、导出、登录和权限变更记录操作者、时间、对象、前后值与来源', '不可篡改操作日志', '关键操作覆盖率'],
  ['AUDIT-CONCLUSION', '结论版本', '保留审查结论每次变化及其依据', '风险与任务结论', '机器初判、人工改判、会审决策和复审结果分别成版，不允许覆盖历史结论', '结论版本链和差异', '结论可追溯率'],
  ['AUDIT-RULE', '规则变更记录', '审计规则从配置到发布、回滚的完整变化', '审查规则版本', '条件、等级、依据、范围、状态任一变化均记录前后值、操作者和审批单', '规则差异、审批和生效记录', '规则变更留痕率'],
  ['AUDIT-QUERY', '审计查询', '按人、时间、对象和动作快速检索审计证据', '审计日志索引', '查询结果受审计权限控制，支持组合条件、精确对象定位和带水印导出', '查询条件、结果快照和导出日志', '审计查询响应时间'],
]);
group('运营管理', '规则运营', '规则运营人员', [
  ['OPS-RULE-HIT', '规则命中监控', '监控规则命中量、分布和异常波动', '规则执行指标', '按规则版本统计命中、确认、忽略和改判，超出历史基线自动标记异常', '指标时序、异常点和关联任务', '监控数据延迟'],
  ['OPS-RULE-QUALITY', '规则质量评估', '用人工结论评价规则精确率、召回和业务价值', '规则命中与人工标签', '样本量达到阈值后计算精确率，漏报通过抽检集估算并展示置信区间', '质量评分、样本和改进建议', '低质量规则改进率'],
  ['OPS-RULE-CONFLICT', '规则冲突检测', '发现同一条件产生相反结论或重复处置的规则', '有效规则集合', '发布前检测条件重叠、等级矛盾、建议冲突和法规版本不一致，阻断严重冲突', '冲突对、影响范围和处理结论', '严重冲突拦截率'],
  ['OPS-RULE-GRAY', '灰度发布', '先在小范围验证新规则效果再全量投放', '候选规则版本与灰度范围', '灰度按组织、业务或流量比例控制，设置观察期、成功阈值和自动回滚条件', '实验分组、指标和放量记录', '灰度异常回滚时长'],
]);
group('运营管理', '模型运营', '模型运营人员', [
  ['OPS-MODEL-VERSION', '模型版本管理', '管理模型包、提示配置、依赖和生产状态', '审查模型版本', '模型版本不可覆盖，发布关联代码、参数、训练数据摘要、评测结果和回滚版本', '版本卡、发布单和部署记录', '模型版本可追溯率'],
  ['OPS-MODEL-DATASET', '评测集管理', '维护覆盖关键消保场景且标签可靠的基准样本', '脱敏评测样本与标签', '样本入集需授权和脱敏，训练集与基准集隔离，标签修改经过双人复核', '样本来源、标签版本和授权记录', '评测集场景覆盖率'],
  ['OPS-MODEL-EVAL', '质量评测', '用稳定基准集量化模型上线前后的风险识别质量', '候选模型与基准集', '必须报告精确率、召回率、F1、分场景结果和基准集版本，关键指标退化即阻断发布', '评测报告、错误样本和门禁结论', '评测门禁通过率'],
  ['OPS-MODEL-FEEDBACK', '人工反馈闭环', '把人工改判和标注转化为模型优化数据', '复核反馈与困难样本', '反馈先去除敏感信息并经质量审核，禁止未经确认的人工操作直接污染训练集', '反馈队列、审核结论和入集批次', '有效反馈入集率'],
]);
group('运营管理', '系统运营', '平台运维人员', [
  ['OPS-TASK', '任务监控', '观察审查、解析、导出和同步任务的运行状态', '异步任务实例', '任务展示排队、运行、成功、失败、取消状态，超时或重复执行可安全重试', '任务轨迹、耗时和重试记录', '任务成功率'],
  ['OPS-ALERT', '异常告警', '对服务、任务、接口和数据异常及时通知责任人', '监控指标与异常事件', '告警按严重度路由，重复事件聚合，恢复自动通知且未确认告警逐级升级', '告警事件、通知回执和处置记录', '重大告警响应时长'],
  ['OPS-LOG', '运行日志', '集中检索应用、接口和任务日志以定位问题', '结构化运行日志', '日志包含追踪 ID、服务、级别和时间，禁止记录密码、token 与未脱敏客户信息', '日志索引、查询条件和访问记录', '日志检索响应时间'],
  ['OPS-REPORT', '运营报表', '定期汇总系统使用、质量、容量和故障表现', '运营指标', '报表口径固定版本，数据延迟和缺失明确标识，支持按日周月自动生成与订阅', '运营报表、口径说明和发送记录', '报表准时生成率'],
]);

export const FEATURE_DEFINITIONS = new Map(definitions);

const REQUIRED_HEADINGS = [
  '## 1. 背景与目标', '## 2. 范围与非目标', '## 3. 角色与权限',
  '## 4. 前置条件与触发', '## 5. 主流程', '## 6. 异常与补偿',
  '## 7. 界面与字段', '## 8. 状态与消息', '## 9. 业务规则',
  '## 10. 数据设计', '## 11. 接口与事件', '## 12. 安全、审计与非功能要求',
  '## 13. 验收标准', '## 14. 依赖与演进',
];

export function generateFeatureSpec(context) {
  const d = context.definition ?? FEATURE_DEFINITIONS.get(context.code);
  if (!d) throw new Error(`缺少功能定义: ${context.code}`);
  const title = `${context.name} · 设计 Spec`;
  const apiName = context.code.toLowerCase().replaceAll('-', '/');
  return `# ${title}

> 产品：${context.productName}（${context.productCode}）  
> 一级模块：${context.rootModuleName}  
> 二级模块：${context.moduleName}  
> 功能编码：${context.code}  
> 文档状态：已设计  
> 版本：1.0

## 1. 背景与目标

${d.purpose}。当前功能围绕“${d.object}”建立标准化处理能力，减少线下表格、口头确认和不可追溯操作。

上线目标：

1. 让${d.actor}在一个闭环中完成${d.name}；
2. 形成“${d.evidence}”，可被审查报告和审计查询复用；
3. 以“${d.metric}”作为持续运营指标，并能下钻到具体业务记录。

## 2. 范围与非目标

### 2.1 本期范围

- 支持${d.object}的创建或接入、校验、处理、查询和留痕；
- 支持按组织和业务权限查看处理结果；
- 支持异常重试、人工修正、版本追踪和审计导出；
- 向下游报告、看板或业务系统输出已确认结果。

### 2.2 非目标

- 不替代监管解释、法律意见或有权审批人的最终判断；
- 不在本功能内维护与${d.name}无关的客户、产品或组织主数据；
- 不允许通过客户端参数绕过数据权限、状态机或审计记录。

## 3. 角色与权限

| 角色 | 权限 | 边界 |
| --- | --- | --- |
| ${d.actor} | 创建、处理、提交和查看${d.name}记录 | 仅限授权组织和参与业务 |
| 复核/审批人员 | 复核、退回、批准或否决 | 不得审批本人发起且要求职责分离的记录 |
| 系统管理员 | 配置字典、权限和运行参数 | 不得代替业务人员形成合规结论 |
| 审计人员 | 只读查询和审计导出 | 默认不可修改业务正文 |

## 4. 前置条件与触发

- 当前用户已登录且拥有 \`${context.code}:operate\` 或映射后的产品权限；
- 所属组织、关联任务及${d.object}处于有效状态；
- 依赖的法规、规则、材料或主数据已同步到可用版本；
- 触发方式包括页面操作、上游流程到达和经授权的 API/事件调用。

## 5. 主流程

1. 系统接收${d.object}并生成全局唯一业务编号和幂等键；
2. 校验必填字段、数据权限、对象状态和关联版本，不通过时返回可操作的错误明细；
3. 执行${d.control}；
4. ${d.actor}核对处理结果，必要时补充依据、修正结果或发起复核；
5. 系统保存${d.evidence}，发布领域事件，并更新“${d.metric}”统计。

## 6. 异常与补偿

| 场景 | 系统处理 | 用户反馈 |
| --- | --- | --- |
| 输入缺失或格式错误 | 不落正式数据，返回字段级错误 | 明确显示字段、原因和修正示例 |
| 对象版本已变化 | 拒绝覆盖，返回最新版本号 | 提示刷新后重新确认差异 |
| 外部服务超时 | 保留幂等键并进入有限重试 | 显示处理中，可查询最终状态 |
| 权限或数据范围不足 | 拒绝访问并记录安全日志 | 仅提示无权操作，不泄露对象内容 |
| 处理结果存在冲突 | 标记待人工复核，不自动形成最终结论 | 展示冲突来源和建议处理人 |

补偿原则：重复请求返回同一业务结果；跨系统调用失败不得提交半成品状态；人工修正必须追加新版本而不是覆盖历史。

## 7. 界面与字段

页面采用“列表筛选 + 详情抽屉/工作台 + 处理记录”的布局，并保持与飞书项目风格一致。

| 字段 | 类型 | 必填 | 校验与说明 |
| --- | --- | --- | --- |
| 业务编号 | string | 是 | 系统生成、全局唯一、只读 |
| ${d.object}名称/摘要 | string | 是 | 1–200 字，去除首尾空格 |
| 所属组织 | organization | 是 | 仅可选择用户数据范围内组织 |
| 当前状态 | enum | 是 | 由状态机驱动，不允许手工写入任意值 |
| 业务版本 | long | 是 | 乐观锁版本，每次有效修改递增 |
| 处理依据 | reference[] | 是 | 至少关联一项可追溯证据或规则 |
| 处理意见 | text | 条件必填 | 退回、改判、忽略或异常处置时必填，10–2000 字 |
| 更新时间/更新人 | audit | 是 | 系统记录、只读 |

## 8. 状态与消息

状态机：\`DRAFT（草稿） → PROCESSING（处理中） → PENDING_REVIEW（待复核） → APPROVED（已通过）/REJECTED（已拒绝） → ARCHIVED（已归档）\`。

- 草稿可编辑和删除；进入处理中后仅允许有权限人员修改业务字段；
- 待复核可通过或退回，已通过结果发生来源版本变化时自动标记“需重新确认”；
- 已归档只读，任何修订产生新版本；
- 成功消息使用“${d.name}已保存/已提交”，失败消息包含追踪号但不展示技术堆栈。

## 9. 业务规则

- BR-01：${d.control}。
- BR-02：${d.object}以“产品编码 + 功能编码 + 来源业务号 + 来源版本”组成幂等键，重复提交不得生成第二条正式记录。
- BR-03：形成正式结果前必须保存${d.evidence}；证据缺失时状态只能停留在 \`PENDING_REVIEW\`。
- BR-04：${d.actor}只能处理授权组织内数据；跨组织查看、下载、导出均在服务端再次鉴权。
- BR-05：任何影响${d.metric}的状态或口径变化都记录变更前值、变更后值、操作者、时间和原因。
- BR-06：上游对象或依据版本变化后，既有通过结果显示为“需重新确认”，不得继续作为新任务的现行依据。
- BR-07：高风险、批量处理、人工降级或例外放行必须触发职责分离复核，发起人与最终审批人不能相同。
- BR-08：失败重试使用原幂等键；达到重试上限后进入人工队列并保留每次错误码和时间。

## 10. 数据设计

### 10.1 核心实体

| 实体 | 主键与关键字段 | 用途 |
| --- | --- | --- |
| feature_record | id、organization_id、business_no、status、version | 保存${d.name}主记录 |
| feature_evidence | id、record_id、source_type、source_id、source_version、hash | 保存${d.evidence} |
| feature_decision | id、record_id、decision、reason、actor_id、created_at | 保存人工复核与审批结论 |
| feature_event | id、record_id、event_type、payload_hash、occurred_at | 支持可靠事件投递和审计 |

所有表包含 \`created_at\`、\`updated_at\`；业务更新采用 \`version\` 乐观锁；常用索引为 \`(organization_id, status, updated_at)\`、幂等键唯一索引和证据来源联合索引。

## 11. 接口与事件

### 11.1 REST API

- \`GET /api/v1/compliance/${apiName}\`：按权限分页查询，支持状态、组织、时间和关键字筛选；
- \`POST /api/v1/compliance/${apiName}\`：创建记录，要求 \`Idempotency-Key\`；
- \`GET /api/v1/compliance/${apiName}/{id}\`：读取详情、证据和处理轨迹；
- \`PUT /api/v1/compliance/${apiName}/{id}\`：按 \`version\` 更新，版本冲突返回 HTTP 409；
- \`POST /api/v1/compliance/${apiName}/{id}/submit\`：提交处理或复核，非法状态返回 HTTP 422。

### 11.2 领域事件

- 事件名：\`compliance.${context.code.toLowerCase().replaceAll('-', '_')}.changed.v1\`；
- 必含字段：\`eventId\`、\`organizationId\`、\`recordId\`、\`businessNo\`、\`status\`、\`version\`、\`occurredAt\`；
- 事件采用事务消息表投递，消费者以 \`eventId\` 幂等，失败进入可重放死信队列。

## 12. 安全、审计与非功能要求

- 安全：服务端实施 RBAC 与组织数据范围；传输使用 TLS；敏感字段按角色脱敏；日志不得记录密码、token 或完整客户敏感信息；
- 审计：创建、修改、提交、复核、导出和权限拒绝均留痕，审计记录至少包含追踪号和对象版本；
- 性能：普通查询 P95 ≤ 2 秒，单条提交 P95 ≤ 3 秒；大文件或批量处理转异步并在 2 秒内返回任务号；
- 可用性：月度可用性目标 99.9%，外部依赖故障不得破坏已提交业务数据；
- 容量：单组织支持 100 万条${d.object}记录，列表必须分页，导出超过 1 万条转异步；
- 可观测性：记录成功率、耗时、重试、积压和“${d.metric}”，重大异常触发告警。

## 13. 验收标准

- AC-01：给定具备权限且字段完整的${d.object}，首次提交成功并生成唯一业务编号、版本 1 和完整审计记录。
- AC-02：给定相同幂等键重复提交，系统返回同一记录且数据库中只有一条正式业务数据。
- AC-03：给定不满足“${d.control}”的数据，系统阻止形成正式结果并返回具体失败项。
- AC-04：给定无当前组织权限的用户访问详情或导出，系统返回 403，响应和日志均不泄露${d.object}内容。
- AC-05：给定两个用户基于同一版本编辑，后提交者收到 409，并能刷新查看最新内容和差异。
- AC-06：给定外部依赖连续超时，系统按原幂等键重试；超过上限后进入人工队列且无半成品正式状态。
- AC-07：给定已通过记录的上游依据版本发生变化，记录自动标识“需重新确认”，旧证据仍可审计回放。
- AC-08：完成处理后，${d.evidence}可从详情和审计查询中打开，报表中的“${d.metric}”可下钻到该记录。

## 14. 依赖与演进

### 14.1 依赖

- 依赖统一身份、组织、RBAC、审计日志、文件存储和消息投递能力；
- 业务侧依赖${context.rootModuleName}下的现行法规、规则、材料或任务数据；
- 下游由报告中心、风险看板和经授权业务系统消费已确认结果。

### 14.2 后续演进

1. 基于“${d.metric}”和人工反馈优化规则阈值与处理路径；
2. 增加按业务场景配置的字段、流程和审批策略，但保持事件向后兼容；
3. 对批量和智能能力先灰度验证，再按组织逐步开放。
`;
}

export function validateFeatureSpec(markdown, context = {}) {
  const errors = [];
  for (const heading of REQUIRED_HEADINGS) {
    if (!markdown.includes(heading)) errors.push(`缺少章节: ${heading}`);
  }
  if ((markdown.match(/^[*-] BR-\d{2}：/gm) ?? []).length < 5) errors.push('业务规则少于 5 条');
  if ((markdown.match(/^[*-] AC-\d{2}：/gm) ?? []).length < 6) errors.push('验收标准少于 6 条');
  if (/\b(?:TODO|TBD)\b|请补充|\{\{|\}\}|\|[ \t]*\|/.test(markdown)) errors.push('正文包含占位内容或空表格');
  if (context.code && !markdown.includes(context.code)) errors.push(`正文缺少功能编码: ${context.code}`);
  if (context.name && !markdown.includes(context.name)) errors.push(`正文缺少功能名称: ${context.name}`);
  if (markdown.length < 4500) errors.push('正文内容深度不足 4500 字符');
  return errors;
}

class HttpError extends Error {
  constructor(status, body, url) {
    super(`${status} ${body?.message ?? body?.error ?? 'request failed'}: ${url}`);
    this.status = status;
    this.body = body;
  }
}

class ApiSession {
  constructor(baseUrl) {
    this.baseUrl = baseUrl.replace(/\/$/, '');
    this.cookies = new Map();
  }

  captureCookies(response) {
    const values = response.headers.getSetCookie?.() ?? [];
    for (const value of values) {
      const [pair] = value.split(';', 1);
      const separator = pair.indexOf('=');
      if (separator > 0) this.cookies.set(pair.slice(0, separator), pair.slice(separator + 1));
    }
  }

  async request(path, { method = 'GET', body } = {}) {
    const headers = { Accept: 'application/json' };
    if (this.cookies.size) headers.Cookie = [...this.cookies].map(([key, value]) => `${key}=${value}`).join('; ');
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (!['GET', 'HEAD'].includes(method) && this.cookies.has('XSRF-TOKEN')) {
      headers['X-XSRF-TOKEN'] = decodeURIComponent(this.cookies.get('XSRF-TOKEN'));
    }
    const url = `${this.baseUrl}${path}`;
    const response = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    this.captureCookies(response);
    const text = await response.text();
    let value = null;
    if (text) {
      try { value = JSON.parse(text); } catch { value = { message: text }; }
    }
    if (!response.ok) throw new HttpError(response.status, value, url);
    return value;
  }

  async login(username, password) {
    await this.request('/api/v1/auth/login', { method: 'POST', body: { username, password } });
    await this.request('/api/v1/auth/me');
  }
}

export function parseArguments(argv) {
  const options = {
    baseUrl: 'http://localhost:8082', productId: 102, verifyOnly: false, codes: [],
  };
  for (const value of argv) {
    if (value.startsWith('--base-url=')) options.baseUrl = value.slice('--base-url='.length);
    else if (value.startsWith('--product-id=')) options.productId = Number(value.slice('--product-id='.length));
    else if (value.startsWith('--codes=')) {
      options.codes = value.slice('--codes='.length).split(',').map((code) => code.trim()).filter(Boolean);
    }
    else if (value === '--verify-only') options.verifyOnly = true;
    else throw new Error(`不支持的参数: ${value}`);
  }
  return options;
}

const sleep = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));

async function retry(operation, label) {
  const delays = [0, 2000, 4000, 8000, 16000];
  let lastError;
  for (let attempt = 0; attempt < delays.length; attempt += 1) {
    if (delays[attempt]) await sleep(delays[attempt]);
    try {
      return await operation();
    } catch (error) {
      lastError = error;
      const retryable = error instanceof HttpError
        && ([429, 502, 503, 504].includes(error.status) || error.body?.code === 'OUTLINE_RATE_LIMIT');
      if (!retryable || attempt === delays.length - 1) throw error;
      console.warn(`[重试 ${attempt + 1}/4] ${label}: ${error.message}`);
    }
  }
  throw lastError;
}

function buildContexts(product, modules, features) {
  const modulesById = new Map(modules.map((module) => [Number(module.id), module]));
  return features.filter((feature) => feature.status === 'ACTIVE').map((feature) => {
    const definition = FEATURE_DEFINITIONS.get(feature.code);
    if (!definition) throw new Error(`数据库存在未设计的有效功能: ${feature.code}`);
    const module = modulesById.get(Number(feature.moduleId));
    const root = module?.parentId == null ? module : modulesById.get(Number(module.parentId));
    if (!module || !root) throw new Error(`功能 ${feature.code} 的模块层级无效`);
    if (feature.name !== definition.name || module.name !== definition.module || root.name !== definition.domain) {
      throw new Error(`功能结构与设计不一致: ${feature.code}，数据库=${root.name}/${module.name}/${feature.name}，设计=${definition.domain}/${definition.module}/${definition.name}`);
    }
    return {
      productId: Number(product.id), productCode: product.code, productName: product.name,
      featureId: Number(feature.id), code: feature.code, name: feature.name,
      moduleName: module.name, rootModuleName: root.name,
      description: feature.description, definition,
    };
  }).sort((left, right) => left.featureId - right.featureId);
}

async function run(argv = process.argv.slice(2)) {
  const options = parseArguments(argv);
  const username = process.env.ZHILU_USERNAME;
  const password = process.env.ZHILU_PASSWORD;
  if (!username || !password) throw new Error('必须通过 ZHILU_USERNAME 和 ZHILU_PASSWORD 提供本地系统凭据');

  const api = new ApiSession(options.baseUrl);
  await api.login(username, password);
  const [product, modules, features] = await Promise.all([
    api.request(`/api/v1/products/${options.productId}`),
    api.request(`/api/v1/products/${options.productId}/modules`),
    api.request(`/api/v1/products/${options.productId}/features`),
  ]);
  if (product.code !== 'XBHG' || product.name !== '消保合规') {
    throw new Error(`拒绝处理非消保合规产品: ${product.code}/${product.name}`);
  }
  const allContexts = buildContexts(product, modules, features);
  if (allContexts.length !== 124 || FEATURE_DEFINITIONS.size !== 124) {
    throw new Error(`有效功能数量必须为 124，数据库=${allContexts.length}，设计=${FEATURE_DEFINITIONS.size}`);
  }
  const selected = new Set(options.codes);
  const contexts = selected.size
    ? allContexts.filter((context) => selected.has(context.code)) : allContexts;
  if (selected.size && contexts.length !== selected.size) {
    const found = new Set(contexts.map((context) => context.code));
    throw new Error(`指定功能不存在: ${[...selected].filter((code) => !found.has(code)).join(', ')}`);
  }

  const report = { productId: options.productId, targeted: contexts.length, generated: 0, updated: 0, unchanged: 0, verified: 0, failed: 0, failures: [] };
  for (let index = 0; index < contexts.length; index += 1) {
    const context = contexts[index];
    try {
      const markdown = generateFeatureSpec(context);
      const localErrors = validateFeatureSpec(markdown, context);
      if (localErrors.length) throw new Error(localErrors.join('；'));
      report.generated += 1;

      if (!options.verifyOnly) {
        const current = await retry(
          () => api.request(`/api/v1/products/${options.productId}/features/${context.featureId}/spec/sync`, { method: 'POST' }),
          `${context.code} 同步目录`,
        );
        const title = `${context.name} · 设计 Spec`;
        if (current.title === title && current.markdown === markdown) {
          report.unchanged += 1;
        } else {
          await retry(
            () => api.request(`/api/v1/products/${options.productId}/features/${context.featureId}/spec`, {
              method: 'PUT', body: { title, markdown, revision: current.revision },
            }),
            `${context.code} 写入正文`,
          );
          report.updated += 1;
        }
      }

      const saved = await retry(
        () => api.request(`/api/v1/products/${options.productId}/features/${context.featureId}/spec`),
        `${context.code} 回读验收`,
      );
      const remoteErrors = validateFeatureSpec(saved.markdown ?? '', context);
      if (saved.title !== `${context.name} · 设计 Spec`) remoteErrors.push(`标题不正确: ${saved.title}`);
      if (!saved.outlineUrl) remoteErrors.push('Outline URL 为空');
      if (saved.syncStatus !== 'READY') remoteErrors.push(`同步状态不是 READY: ${saved.syncStatus}`);
      if (remoteErrors.length) throw new Error(remoteErrors.join('；'));
      report.verified += 1;
      console.log(`[${index + 1}/${contexts.length}] ${context.code} ${options.verifyOnly ? '验收通过' : '写入并验收通过'}`);
      if (!options.verifyOnly && index < contexts.length - 1) await sleep(1100);
    } catch (error) {
      report.failed += 1;
      report.failures.push({ code: context.code, message: error.message });
      console.error(`[${index + 1}/${contexts.length}] ${context.code} 失败: ${error.message}`);
      if (!options.verifyOnly && index < contexts.length - 1) await sleep(1100);
    }
  }
  console.log(JSON.stringify(report, null, 2));
  if (report.failed) process.exitCode = 1;
  return report;
}

const isMain = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href;
if (isMain) run().catch((error) => {
  console.error(error.stack ?? error.message);
  process.exitCode = 1;
});
