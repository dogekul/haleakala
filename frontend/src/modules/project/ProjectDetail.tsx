import {
  ArrowLeftOutlined, CheckCircleFilled, CheckSquareOutlined, ClockCircleOutlined, ExclamationCircleFilled,
  FileTextOutlined, PlusOutlined, ProfileOutlined, RobotOutlined, SettingOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, Button, Card, Col, DatePicker, Descriptions, Empty, Form, Input, InputNumber,
  List, Modal, Progress, Radio, Row, Select, Space, Steps, Table, Tabs, Tag, Timeline,
  Typography, message,
} from 'antd'
import dayjs from 'dayjs'
import { useEffect, useMemo, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { PageState } from '../../components/PageState'
import { AgentExecutionPanel } from '../../components/AgentExecutionPanel'
import { ApiError } from '../../services/api'
import { ProjectDocuments } from './ProjectDocuments'
import { ProjectDeliveryTracking } from './ProjectDeliveryTracking'
import { ProjectTasks } from './ProjectTasks'
import { projectApi } from './projectApi'
import { stageNames, type Project } from './types'

type StageDeliverable = { title: string; type: '关键卡点' | '关键过程' | '仅归档'; note: string }
type StageMatter = { matter: string; deliverables: StageDeliverable[] }

const stageGuides: Record<string, StageMatter[]> = {
  START: [{ matter: '4. 项目立项', deliverables: [
    { title: '项目立项登记表', type: '关键卡点', note: '前置立项：战略客户、高意向客户、POC 本地化等' },
    { title: '项目立项评审纪要', type: '关键卡点', note: '部门立项评审结论及领导要求' },
  ] }],
  REQUIREMENT: [
    { matter: '5. 需求及技术调研', deliverables: [
      { title: '项目调研计划', type: '关键过程', note: '提前与客户沟通约定的调研实施计划' },
      { title: '业务基础调研表', type: '关键过程', note: '业务基础调研信息' },
      { title: '技术调研表', type: '关键过程', note: '技术环境及对接调研信息' },
      { title: '功能执行跟踪表', type: '关键过程', note: '产品执行跟踪；在“交付执行跟踪”页维护' },
      { title: '运营执行跟踪表', type: '关键过程', note: '运营方案及执行跟踪' },
    ] },
    { matter: '6. 项目启动会', deliverables: [
      { title: '项目实施计划', type: '关键卡点', note: '里程碑及交付版本规划' },
      { title: '项目管理计划', type: '关键卡点', note: '实施计划、管理过程及各项机制' },
    ] },
  ],
  CUSTOM_DEV: [
    { matter: '7. 需求澄清及技术方案评审', deliverables: [
      { title: '产品设计方案', type: '关键过程', note: '归类标准化功能、定制化功能并输出产品设计' },
      { title: '系统实施方案', type: '关键过程', note: '系统现场部署实施及对接方案' },
    ] },
    { matter: '8. 任务拆解及项目计划制定', deliverables: [
      { title: '项目实施 WBS', type: '关键过程', note: '任务到人、时间到天' },
    ] },
  ],
  GO_LIVE: [{ matter: '9. 编码与测试跟进', deliverables: [
    { title: '功能性测试报告', type: '仅归档', note: '归档功能测试范围、结果和遗留问题' },
    { title: '性能测试报告', type: '仅归档', note: '归档性能指标、场景和测试结论' },
  ] }],
  TRIAL_HANDOVER: [
    { matter: '10. 组织客户验证版本', deliverables: [
      { title: '版本验收报告', type: '关键卡点', note: '记录迭代版本验收通过情况并闭环确认' },
      { title: '客户验收确认邮件', type: '关键卡点', note: '保留客户邮件回复或等效确认依据' },
    ] },
    { matter: '11. 发布组织与试运行跟进', deliverables: [
      { title: '上线发布公告', type: '关键卡点', note: '明确交付内容、业务价值及后续验收依据' },
    ] },
  ],
  STANDARDIZATION: [
    { matter: '12. 项目验收组织及交接', deliverables: [
      { title: '项目验收问题跟进表', type: '关键过程', note: '登记验收问题及客户诉求并闭环跟进' },
      { title: '项目验收报告', type: '关键卡点', note: '明确完成交付及客户验收结论，作为回款和正式结束依据' },
    ] },
    { matter: '13. 项目复盘及总结', deliverables: [
      { title: '项目总结报告', type: '仅归档', note: '按时保质完成结论及经验总结' },
    ] },
  ],
  CLOSE: [{ matter: '项目经理每周跟进', deliverables: [
    { title: '项目风险及问题登记表', type: '关键过程', note: '全程相关问题记录' },
    { title: '项目变更记录表', type: '关键过程', note: '范围、方案、计划、资源变更记录' },
    { title: '项目周例会会议纪要', type: '关键过程', note: '每周进度、协作对齐' },
    { title: '项目周报', type: '关键过程', note: '每周同步客户' },
    { title: '项目阶段复盘报告', type: '关键过程', note: '阶段性问题及后续措施建议' },
  ] }],
}

const nodeColors = { 关键卡点: 'red', 关键过程: 'blue', 仅归档: 'green' } as const

export function ProjectDetail() {
  const id = Number(useParams().id)
  const query = useQuery({ queryKey: ['project', id], queryFn: () => projectApi.get(id), enabled: Number.isFinite(id) })
  return <PageState loading={query.isLoading} error={query.error} onRetry={() => void query.refetch()} empty={!query.data && !query.isLoading}>
    {query.data && <ProjectDetailContent project={query.data} />}
  </PageState>
}

function ProjectDetailContent({ project }: { project: Project }) {
  const [searchParams, setSearchParams] = useSearchParams()
  const [tab, setTab] = useState(searchParams.get('tab') ?? 'lifecycle')
  const taskId = Number(searchParams.get('taskId'))
  const selectedTaskId = Number.isFinite(taskId) && taskId > 0 ? taskId : undefined
  useEffect(() => {
    if (searchParams.get('tab')) setTab(searchParams.get('tab')!)
  }, [searchParams])
  const changeTab = (nextTab: string) => {
    setTab(nextTab)
    const next = new URLSearchParams(searchParams)
    if (nextTab !== 'lifecycle') next.set('tab', nextTab)
    else {
      next.delete('tab')
      next.delete('taskId')
    }
    setSearchParams(next, { replace: true })
  }
  return <div className="project-detail">
    <div className="detail-back"><Link className="detail-back-link" to="/projects"><ArrowLeftOutlined /> 返回项目列表</Link></div>
    <div className="project-hero">
      <div><Space><Tag color="blue">{project.code}</Tag><Tag color={project.riskLevel === 'RED' ? 'red' : project.riskLevel === 'YELLOW' ? 'orange' : 'green'}>
        {project.riskLevel === 'GREEN' ? '健康' : project.riskLevel === 'YELLOW' ? '需关注' : '高风险'}</Tag></Space>
        <Typography.Title level={2}>{project.name}</Typography.Title>
        <Space split={<span className="dot-divider">·</span>}><span>{project.customerName}</span><span>{project.productName} {project.productVersionName}</span><span>负责人 {project.managerName}</span></Space></div>
      <div className="hero-stage"><span>当前阶段</span><strong>{stageNames[project.currentStage]}</strong></div>
    </div>
    <Tabs activeKey={tab} onChange={changeTab} items={[
      { key: 'lifecycle', label: '七阶段看板', children: <Lifecycle project={project} /> },
      { key: 'tasks', label: <span><CheckSquareOutlined /> 项目任务</span>, children: <ProjectTasks project={project} selectedTaskId={selectedTaskId} /> },
      { key: 'tracking', label: <span><ProfileOutlined /> 交付执行跟踪</span>, children: <ProjectDeliveryTracking project={project} /> },
      { key: 'documents', label: <span><FileTextOutlined /> 项目文档</span>, children: <ProjectDocuments project={project} /> },
      { key: 'agent', label: <span><RobotOutlined /> Skill / Agent</span>, children: <AgentExecutionPanel projectId={project.id} /> },
      { key: 'templates', label: '模板中心', children: <Templates /> },
      { key: 'risks', label: `风险登记册 (${project.risks.length})`, children: <Risks project={project} /> },
      { key: 'milestones', label: '里程碑与时间线', children: <Milestones project={project} /> },
      { key: 'settings', label: <span><SettingOutlined /> 项目信息与设置</span>, children: <Settings project={project} /> },
    ]} />
  </div>
}

function Lifecycle({ project }: { project: Project }) {
  const client = useQueryClient()
  const currentIndex = project.stages.findIndex(item => item.code === project.currentStage)
  const next = project.stages[currentIndex + 1]
  const nextName = next ? stageNames[next.code] ?? next.name : undefined
  const guide = stageGuides[project.currentStage] ?? []
  const documents = useQuery({
    queryKey: ['project-documents', project.id],
    queryFn: () => projectApi.documents(project.id),
    enabled: project.gateMode === 'WARNING',
  })
  const advance = useMutation({ mutationFn: () => projectApi.advance(project.id, next.code),
    onSuccess: async () => { await client.invalidateQueries({ queryKey: ['project', project.id] }); message.success('阶段推进成功') },
    onError: (error) => {
      if (error instanceof ApiError && error.status === 409) {
        const missing = gateMessages(error.message)
        Modal.error({
          title: '阶段门禁未通过',
          content: <List
            size="small"
            dataSource={missing}
            renderItem={item => <List.Item>{item}</List.Item>}
          />,
        })
      }
    },
  })
  const close = useMutation({
    mutationFn: () => projectApi.close(project.id),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ['project', project.id] })
      message.success('项目已完成收尾并关闭')
    },
    onError: (error) => {
      if (error instanceof ApiError && error.status === 409) {
        Modal.error({
          title: '项目关闭门禁未通过',
          content: <List
            size="small"
            dataSource={gateMessages(error.message)}
            renderItem={item => <List.Item>{item}</List.Item>}
          />,
        })
      }
    },
  })
  const requestAdvance = () => {
    if (project.gateMode !== 'WARNING') {
      advance.mutate()
      return
    }
    const current = project.stages[currentIndex]
    const warnings = [
      ...(current?.gateStatus === 'BLOCKING'
        ? [current.gateMessage || '阶段门禁未通过']
        : []),
      ...(documents.data ?? [])
        .filter(item => item.stageCode === project.currentStage
          && (item.gateRequired ?? item.requirement === 'REQUIRED')
          && item.status !== 'COMPLETED')
        .map(item => `未完成必需文档：${item.title}`),
    ]
    if (!warnings.length) {
      advance.mutate()
      return
    }
    Modal.confirm({
      title: '阶段存在未完成项',
      content: <List
        size="small"
        dataSource={warnings}
        renderItem={item => <List.Item>{item}</List.Item>}
      />,
      okText: '记录警告并推进',
      cancelText: '继续完善',
      onOk: () => advance.mutate(),
    })
  }
  const requestClose = () => {
    Modal.confirm({
      title: '确认关闭项目',
      content: project.gateMode === 'WARNING'
        ? '系统将记录当前未完成项并完成项目关闭，请确认已接受相关风险。'
        : '系统会检查过程跟进阶段的全部必需文档，通过后项目将不可再推进。',
      okText: '确认关闭',
      cancelText: '继续完善',
      onOk: () => close.mutate(),
    })
  }
  return <div>
    <Card className="lifecycle-card">
      <Steps current={currentIndex} items={project.stages.map(stage => ({
        title: stage.name,
        status: stage.status === 'COMPLETED' ? 'finish' : stage.status === 'ACTIVE' ? 'process' : 'wait',
        description: stage.gateStatus === 'BLOCKING' ? <Tag color="red">门禁阻断</Tag> : undefined,
      }))} />
      <div className="stage-focus"><div><span>交付阶段 {currentIndex + 2} / {project.stages.length + 1}</span><h3>{stageNames[project.currentStage]}</h3>
        <p>{project.stages[currentIndex]?.gateMessage ?? '按交付检查清单完成本阶段任务和产出物。'}</p></div>
        {next ? <Button
          aria-label={`推进至${nextName}`}
          type="primary"
          loading={advance.isPending || documents.isLoading}
          onClick={requestAdvance}
        >推进至 {nextName}</Button> : project.status === 'CLOSED'
          ? <Tag color="green">项目已关闭</Tag>
          : <Button
              aria-label="完成并关闭项目"
              type="primary"
              danger
              loading={close.isPending}
              onClick={requestClose}
            >完成并关闭项目</Button>}</div>
    </Card>
    <Card className="stage-guide-card" title="本阶段事项与交付物" extra={<Space><Tag color="red">关键卡点</Tag><Tag color="blue">关键过程</Tag><Tag color="green">仅归档</Tag></Space>}>
      <div className="stage-guide-head"><span>事项</span><span>交付物</span><span>节点类型</span><span>说明</span></div>
      {guide.flatMap(group => group.deliverables.map((deliverable, index) =>
        <div className="stage-guide-row" key={`${group.matter}-${deliverable.title}`}>
          <strong>{index === 0 ? group.matter : ''}</strong>
          <span>{deliverable.title}</span>
          <span><Tag color={nodeColors[deliverable.type]}>{deliverable.type}</Tag></span>
          <span>{deliverable.note}</span>
        </div>))}
    </Card>
    <Row gutter={16} className="detail-grid"><Col span={16}><Card title="最近活动">
      <Timeline items={project.activities.slice(0, 8).map(activity => ({ children: <div><strong>{String(activity.summary)}</strong><p>{String(activity.actorName ?? '系统')} · {String(activity.createdAt ?? '')}</p></div> }))} />
      {!project.activities.length && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} />}</Card></Col>
      <Col span={8}><Card title="项目概览"><Descriptions column={1} size="small" items={[
        { key: 'status', label: '项目状态', children: project.status },
        { key: 'period', label: '计划周期', children: `${project.startDate ?? '-'} 至 ${project.plannedEndDate ?? '-'}` },
        { key: 'member', label: '项目成员', children: `${project.members.length} 人` },
        { key: 'artifact', label: '交付产出', children: `${project.artifacts.length} 份` },
      ]} /></Card></Col></Row>
  </div>
}

function Risks({ project }: { project: Project }) {
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm()
  const client = useQueryClient()
  const add = useMutation({ mutationFn: (values: Record<string, unknown>) => projectApi.addRisk(project.id, values), onSuccess: async () => {
    await client.invalidateQueries({ queryKey: ['project', project.id] }); form.resetFields(); setOpen(false); message.success('风险已登记')
  } })
  const columns = [
    { title: '风险事项', dataIndex: 'title' }, { title: '类别', dataIndex: 'category', width: 110 },
    { title: '等级', dataIndex: 'riskLevel', width: 90, render: (value: string) => <Tag color={value === 'RED' ? 'red' : value === 'YELLOW' ? 'orange' : 'green'}>{value}</Tag> },
    { title: '状态', dataIndex: 'status', width: 100 }, { title: '缓解措施', dataIndex: 'mitigation' }, { title: '到期日', dataIndex: 'dueDate', width: 120 },
  ]
  return <Card title="项目风险登记册" extra={<Button icon={<PlusOutlined />} type="primary" onClick={() => setOpen(true)}>登记风险</Button>}>
    <Table rowKey="id" columns={columns} dataSource={project.risks} locale={{ emptyText: '暂无开放风险' }} />
    <Modal open={open} onCancel={() => setOpen(false)} onOk={() => form.submit()} confirmLoading={add.isPending} title="登记项目风险">
      <Form form={form} layout="vertical" onFinish={values => add.mutate({ ...values, dueDate: values.dueDate?.format('YYYY-MM-DD') })}>
        <Form.Item label="风险事项" name="title" rules={[{ required: true }]}><Input /></Form.Item>
        <Row gutter={12}><Col span={12}><Form.Item label="类别" name="category" rules={[{ required: true }]}><Select options={['进度', '需求', '技术', '人员', '客户'].map(value => ({ value, label: value }))} /></Form.Item></Col>
          <Col span={6}><Form.Item label="概率" name="probability" rules={[{ required: true }]}><InputNumber min={1} max={5} /></Form.Item></Col>
          <Col span={6}><Form.Item label="影响" name="impact" rules={[{ required: true }]}><InputNumber min={1} max={5} /></Form.Item></Col></Row>
        <Form.Item label="缓解措施" name="mitigation"><Input.TextArea rows={3} /></Form.Item>
        <Form.Item label="到期日" name="dueDate"><DatePicker /></Form.Item>
      </Form>
    </Modal>
  </Card>
}

function Milestones({ project }: { project: Project }) {
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm()
  const client = useQueryClient()
  const add = useMutation({ mutationFn: (values: Record<string, unknown>) => projectApi.addMilestone(project.id, values), onSuccess: async () => {
    await client.invalidateQueries({ queryKey: ['project', project.id] }); setOpen(false); form.resetFields(); message.success('里程碑已创建')
  } })
  return <Row gutter={16}><Col span={16}><Card title="交付时间线" extra={<Button icon={<PlusOutlined />} onClick={() => setOpen(true)}>新增里程碑</Button>}>
    {project.milestones.length ? <Timeline mode="left" items={project.milestones.map(item => ({
      color: item.status === 'COMPLETED' ? 'green' : 'blue', label: String(item.dueDate),
      children: <div className="milestone-item"><strong>{String(item.name)}</strong><Progress percent={Number(item.progress)} size="small" /></div>,
    }))} /> : <Empty description="暂无里程碑" />}</Card></Col>
    <Col span={8}><Card title="阶段节奏"><List dataSource={project.stages} renderItem={stage => <List.Item>
      <List.Item.Meta avatar={stage.status === 'COMPLETED' ? <CheckCircleFilled className="success-icon" /> : <ClockCircleOutlined />}
        title={stage.name} description={stage.status} /></List.Item>} /></Card></Col>
    <Modal title="新增里程碑" open={open} onCancel={() => setOpen(false)} onOk={() => form.submit()} confirmLoading={add.isPending}>
      <Form form={form} layout="vertical" onFinish={values => add.mutate({ ...values, dueDate: values.dueDate.format('YYYY-MM-DD') })}>
        <Form.Item label="里程碑名称" name="name" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item label="计划日期" name="dueDate" rules={[{ required: true }]}><DatePicker /></Form.Item>
      </Form>
    </Modal>
  </Row>
}

function Templates() {
  return <Card className="project-template-migration">
    <FileTextOutlined />
    <Typography.Title level={3}>项目文档模版已统一迁移到知识库</Typography.Title>
    <Typography.Paragraph>
      请在“知识库 → 文档模版”维护适用阶段、必需性和 Outline 正文。
      新建项目会自动复制当前已发布模版，既有项目副本不会被后续模版修改覆盖。
    </Typography.Paragraph>
    <Button type="primary"><Link to="/knowledge">前往文档模版</Link></Button>
  </Card>
}

function gateMessages(messageText: string) {
  return messageText.split('；').flatMap(part => {
    const trimmed = part.trim()
    if (!trimmed.startsWith('未完成必需文档')) return trimmed ? [trimmed] : []
    return trimmed.replace(/^未完成必需文档[：:]/, '').split('、').filter(Boolean)
  })
}

function Settings({ project }: { project: Project }) {
  const client = useQueryClient()
  const [form] = Form.useForm()
  const save = useMutation({ mutationFn: (values: Record<string, unknown>) => projectApi.settings(project.id, {
    ...values, plannedEndDate: values.plannedEndDate ? (values.plannedEndDate as dayjs.Dayjs).format('YYYY-MM-DD') : null, version: project.version,
  }), onSuccess: async () => { await client.invalidateQueries({ queryKey: ['project', project.id] }); message.success('项目设置已保存') } })
  const initial = useMemo(() => ({ name: project.name, status: project.status, riskLevel: project.riskLevel,
    gateMode: project.gateMode ?? 'BLOCK', plannedEndDate: project.plannedEndDate ? dayjs(project.plannedEndDate) : undefined }), [project])
  return <Row gutter={16}><Col span={16}><Card title="项目信息与设置">
    <Form form={form} layout="vertical" initialValues={initial} onFinish={values => save.mutate(values)}>
      <Form.Item label="项目名称" name="name" rules={[{ required: true }]}><Input /></Form.Item>
      <Row gutter={12}><Col span={8}><Form.Item label="状态" name="status"><Select
        disabled={['CLOSING', 'CLOSED'].includes(project.status)}
        options={(project.status === 'ACTIVE' ? ['ACTIVE', 'SUSPENDED']
          : project.status === 'SUSPENDED' ? ['SUSPENDED', 'ACTIVE']
            : [project.status]).map(value => ({ value, label: value }))}
      /></Form.Item></Col>
        <Col span={8}><Form.Item label="健康度" name="riskLevel"><Select options={['GREEN', 'YELLOW', 'RED'].map(value => ({ value, label: value }))} /></Form.Item></Col>
        <Col span={8}><Form.Item label="计划完成" name="plannedEndDate"><DatePicker style={{ width: '100%' }} /></Form.Item></Col></Row>
      <Form.Item label="阶段门禁模式" name="gateMode"><Radio.Group options={[{ value: 'BLOCK', label: '阻断' }, { value: 'WARNING', label: '仅警告' }]} /></Form.Item>
      <Button
        type="primary"
        htmlType="submit"
        loading={save.isPending}
        disabled={['CLOSING', 'CLOSED'].includes(project.status)}
      >保存设置</Button>
    </Form></Card></Col>
    <Col span={8}><Alert type="warning" showIcon icon={<ExclamationCircleFilled />} message="项目级权限"
      description="只有项目成员或具备跨项目权限的角色可访问本项目；写操作还需要 project:write 权限。" /></Col></Row>
}
