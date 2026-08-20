export const supportedSkills = new Set([
  'deliver-init',
  'deliver-require',
  'deliver-dev',
  'deliver-test',
  'deliver-transition',
  'deliver-standardize',
  'deliver-close',
])

const stageNames = {
  'deliver-init': '项目立项',
  'deliver-require': '调研与启动',
  'deliver-dev': '方案与计划',
  'deliver-test': '开发与测试',
  'deliver-transition': '验证与发布',
  'deliver-standardize': '验收与结项',
  'deliver-close': '过程跟进',
}

const placeholders = /(请填写|待填写|请补充|待补充|在此填写|\bTODO\b|\bTBD\b)/gi

export function buildArtifacts(skill, context = {}) {
  if (!Array.isArray(context.documents)) {
    return [{
      name: `${skill}-result.md`,
      mimeType: 'text/markdown',
      artifactType: 'AGENT_OUTPUT',
      content: genericArtifact(skill, context),
    }]
  }

  return context.documents.map((document, index) => projectDocumentArtifact(
    skill, context, document ?? {}, index,
  ))
}

function projectDocumentArtifact(skill, context, document, index) {
  const projectDocumentId = number(document.projectDocumentId ?? document.id)
  const expectedRevision = number(document.expectedRevision ?? document.revision)
  if (projectDocumentId == null || expectedRevision == null) {
    throw new Error(`第 ${index + 1} 份项目文档缺少 projectDocumentId 或 expectedRevision`)
  }
  const title = text(document.title ?? document.name) || `${stage(skill, context)}文档 ${index + 1}`
  const markdown = completeMarkdown(
    text(document.markdown ?? document.content ?? document.sourceMarkdown),
    title,
    skill,
    context,
  )
  return {
    name: safeName(title),
    title,
    mimeType: 'text/markdown',
    artifactType: 'PROJECT_DOCUMENT',
    projectDocumentId,
    expectedRevision,
    content: markdown,
  }
}

function completeMarkdown(markdown, title, skill, context) {
  const source = markdown ? markdown.replace(/\\n/g, '\n') : `# ${title}\n\n请填写`
  const lines = source.split(/\r?\n/)
  let section = title
  const completed = lines.map(line => {
    const heading = line.match(/^\s{0,3}#{1,6}\s+(.+?)\s*$/)
    if (heading) section = heading[1].replace(/[*_`]/g, '').trim()
    if (!placeholders.test(line)) return line
    placeholders.lastIndex = 0
    return line.replace(placeholders, draftFor(section, skill, context))
  })
  const contextNote = `> Agent 初稿上下文：项目“${project(context)}”；客户“${customer(context)}”；产品“${product(context)}”；阶段“${stage(skill, context)}”。内容需由项目负责人和相关干系人复核确认。`
  const firstHeading = completed.findIndex(line => /^\s{0,3}#\s+/.test(line))
  completed.splice(firstHeading >= 0 ? firstHeading + 1 : 0, 0, '', contextNote)
  return completed.join('\n').replace(/\n{3,}/g, '\n\n').trim() + '\n'
}

function draftFor(section, skill, context) {
  const base = `${customer(context)}的“${project(context)}”项目在${stage(skill, context)}阶段，围绕${product(context)}推进${section}`
  if (/风险|问题|异常|遗留/.test(section)) {
    return `${base}；初步识别需求边界变化、外部依赖延期和环境差异等风险，责任人需逐项确认影响、措施与完成时限。`
  }
  if (/计划|日程|里程碑|WBS|时间|期限/.test(section)) {
    return `${base}；按“事项、负责人、计划时间、依赖条件、验收依据”建立执行计划，并在评审后固化基线。`
  }
  if (/目标|范围|背景|依据|概况/.test(section)) {
    return `${base}；目标是形成范围清晰、责任明确、结果可验证的交付基线，具体边界以合同、需求和双方评审结论为准。`
  }
  if (/结论|确认|签署|意见|评审/.test(section)) {
    return `${base}；当前已形成可评审初稿，需由客户代表、项目负责人及相关专业负责人核对事实并确认最终结论。`
  }
  if (/资源|组织|角色|职责|责任人/.test(section)) {
    return `${base}；由项目负责人统筹，业务、产品、研发、测试及客户接口人按职责协同，具体人员与投入在评审时确认。`
  }
  return `${base}；已形成初步内容和核对要点，需结合现场事实、数据证据及双方意见逐项复核后定稿。`
}

function genericArtifact(skill, context) {
  return `# ${skill} 执行结果\n\n- 项目：${project(context)}\n- 客户：${customer(context)}\n- 产品：${product(context)}\n- 阶段：${stage(skill, context)}\n- 状态：执行成功\n\n> 此文件由 Mock Agent 按稳定契约生成，可无缝替换为外部团队 Agent。\n`
}

function stage(skill, context) {
  return text(context.stage_name ?? context.stageName)
    || stageNames[skill]
    || text(context.current_stage ?? context.currentStage)
    || '当前'
}

function project(context) {
  return text(context.name ?? context.projectName) || '未命名项目'
}

function customer(context) {
  return text(context.customer_name ?? context.customerName) || '待确认客户'
}

function product(context) {
  const name = text(context.product_name ?? context.productName) || '待确认产品'
  const version = text(context.version_name ?? context.versionName)
  return version ? `${name} ${version}` : name
}

function safeName(value) {
  return value.replace(/[\\/:*?"<>|\r\n]+/g, ' ').trim() || 'project-document'
}

function number(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function text(value) {
  return value == null ? '' : String(value).trim()
}
