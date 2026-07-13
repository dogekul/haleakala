import {
  ArrowLeftOutlined, CheckCircleFilled, ClockCircleOutlined, ExclamationCircleFilled,
  FileTextOutlined, PlusOutlined, RobotOutlined, SettingOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, Button, Card, Col, DatePicker, Descriptions, Empty, Form, Input, InputNumber,
  List, Modal, Progress, Radio, Row, Select, Space, Steps, Table, Tabs, Tag, Timeline,
  Typography, message,
} from 'antd'
import dayjs from 'dayjs'
import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { PageState } from '../../components/PageState'
import { ApiError } from '../../services/api'
import { projectApi } from './projectApi'
import { stageNames, type Project } from './types'

export function ProjectDetail() {
  const id = Number(useParams().id)
  const query = useQuery({ queryKey: ['project', id], queryFn: () => projectApi.get(id), enabled: Number.isFinite(id) })
  return <PageState loading={query.isLoading} error={query.error} onRetry={() => void query.refetch()} empty={!query.data && !query.isLoading}>
    {query.data && <ProjectDetailContent project={query.data} />}
  </PageState>
}

function ProjectDetailContent({ project }: { project: Project }) {
  const [tab, setTab] = useState('lifecycle')
  return <div className="project-detail">
    <div className="detail-back"><Link to="/projects"><ArrowLeftOutlined /> 返回项目列表</Link></div>
    <div className="project-hero">
      <div><Space><Tag color="blue">{project.code}</Tag><Tag color={project.riskLevel === 'RED' ? 'red' : project.riskLevel === 'YELLOW' ? 'orange' : 'green'}>
        {project.riskLevel === 'GREEN' ? '健康' : project.riskLevel === 'YELLOW' ? '需关注' : '高风险'}</Tag></Space>
        <Typography.Title level={2}>{project.name}</Typography.Title>
        <Space split={<span className="dot-divider">·</span>}><span>{project.customerName}</span><span>{project.productName} {project.productVersionName}</span><span>负责人 {project.managerName}</span></Space></div>
      <div className="hero-stage"><span>当前阶段</span><strong>{stageNames[project.currentStage]}</strong></div>
    </div>
    <Tabs activeKey={tab} onChange={setTab} items={[
      { key: 'lifecycle', label: '七阶段看板', children: <Lifecycle project={project} /> },
      { key: 'agent', label: <span><RobotOutlined /> Skill / Agent</span>, children: <AgentPlaceholder /> },
      { key: 'templates', label: '模板中心', children: <Templates project={project} /> },
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
  const advance = useMutation({ mutationFn: (mode: string) => projectApi.advance(project.id, next.code, mode),
    onSuccess: async () => { await client.invalidateQueries({ queryKey: ['project', project.id] }); message.success('阶段推进成功') },
    onError: (error) => { if (error instanceof ApiError && error.status === 409) Modal.confirm({
      title: '阶段门禁未通过', content: `${error.message}。是否以警告模式继续？`, okText: '记录警告并推进',
      onOk: () => advance.mutate('WARNING'),
    }) },
  })
  return <div>
    <Card className="lifecycle-card">
      <Steps current={currentIndex} items={project.stages.map(stage => ({
        title: stage.name,
        status: stage.status === 'COMPLETED' ? 'finish' : stage.status === 'ACTIVE' ? 'process' : 'wait',
        description: stage.gateStatus === 'BLOCKING' ? <Tag color="red">门禁阻断</Tag> : undefined,
      }))} />
      <div className="stage-focus"><div><span>阶段 {currentIndex + 1} / 7</span><h3>{stageNames[project.currentStage]}</h3>
        <p>{project.stages[currentIndex]?.gateMessage ?? '按交付检查清单完成本阶段任务和产出物。'}</p></div>
        {next ? <Button type="primary" loading={advance.isPending} onClick={() => advance.mutate('BLOCK')}>推进至 {next.name}</Button> : <Tag color="green">全部阶段已完成</Tag>}</div>
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

function AgentPlaceholder() {
  return <Card><div className="agent-placeholder"><RobotOutlined /><Typography.Title level={3}>Skill 执行面板</Typography.Title>
    <Typography.Paragraph>支持 deliver-init、deliver-require、deliver-dev、deliver-transition、deliver-standardize 与 deliver-close。</Typography.Paragraph>
    <Tag color="processing">Agent 契约接入中</Tag></div></Card>
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

function Templates({ project }: { project: Project }) {
  const [editing, setEditing] = useState<Record<string, unknown> | null>()
  const [form] = Form.useForm()
  const client = useQueryClient()
  const save = useMutation({ mutationFn: (values: Record<string, unknown>) => projectApi.saveTemplate(project.id,
    editing?.id ? Number(editing.id) : null, { ...values, version: Number(editing?.version ?? 0) }),
    onSuccess: async () => { await client.invalidateQueries({ queryKey: ['project', project.id] }); setEditing(undefined); message.success('模板已保存') },
    onError: (error) => { if (error instanceof ApiError && error.status === 409) message.error(error.message) },
  })
  const presets = ['项目启动检查清单', '项目周报', '风险登记册', '需求采集单', '上线切换检查清单', '项目验收报告']
  return <Card title="项目模板中心" extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditing({}); form.resetFields() }}>新建模板</Button>}>
    <Row gutter={[16, 16]}>{project.templates.map(item => <Col span={8} key={Number(item.id)}><Card size="small" hoverable onClick={() => { setEditing(item); form.setFieldsValue(item) }}>
      <FileTextOutlined className="template-icon" /><h4>{String(item.title)}</h4><Space><Tag>{String(item.status)}</Tag><span>v{Number(item.version) + 1}</span></Space></Card></Col>)}</Row>
    {!project.templates.length && <Empty description="从 34 个交付模板中创建项目实例" />}
    <Modal width={760} title={editing?.id ? '编辑模板' : '创建模板'} open={editing !== undefined} onCancel={() => setEditing(undefined)}
      onOk={() => form.submit()} confirmLoading={save.isPending}>
      <Form form={form} layout="vertical" onFinish={values => save.mutate(values)} initialValues={{ status: 'DRAFT' }}>
        <Row gutter={12}><Col span={10}><Form.Item label="模板" name="templateKey" rules={[{ required: true }]}><Select options={presets.map(value => ({ value, label: value }))} /></Form.Item></Col>
          <Col span={14}><Form.Item label="标题" name="title" rules={[{ required: true }]}><Input /></Form.Item></Col></Row>
        <Form.Item label="Markdown 内容" name="contentMarkdown" rules={[{ required: true }]}><Input.TextArea rows={14} className="markdown-editor" /></Form.Item>
        <Form.Item label="状态" name="status"><Radio.Group options={[{ value: 'DRAFT', label: '草稿' }, { value: 'REVIEWED', label: '已评审' }, { value: 'APPROVED', label: '已批准' }]} /></Form.Item>
      </Form>
    </Modal>
  </Card>
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
      <Row gutter={12}><Col span={8}><Form.Item label="状态" name="status"><Select options={['ACTIVE', 'SUSPENDED', 'CLOSING', 'CLOSED'].map(value => ({ value, label: value }))} /></Form.Item></Col>
        <Col span={8}><Form.Item label="健康度" name="riskLevel"><Select options={['GREEN', 'YELLOW', 'RED'].map(value => ({ value, label: value }))} /></Form.Item></Col>
        <Col span={8}><Form.Item label="计划完成" name="plannedEndDate"><DatePicker style={{ width: '100%' }} /></Form.Item></Col></Row>
      <Form.Item label="阶段门禁模式" name="gateMode"><Radio.Group options={[{ value: 'BLOCK', label: '阻断' }, { value: 'WARNING', label: '仅警告' }]} /></Form.Item>
      <Button type="primary" htmlType="submit" loading={save.isPending}>保存设置</Button>
    </Form></Card></Col>
    <Col span={8}><Alert type="warning" showIcon icon={<ExclamationCircleFilled />} message="项目级权限"
      description="只有项目成员或具备跨项目权限的角色可访问本项目；写操作还需要 project:write 权限。" /></Col></Row>
}
