import {
  AppstoreOutlined, ArrowRightOutlined, BarsOutlined, CalendarOutlined,
  FireOutlined, HeartOutlined, PlusOutlined, ProjectOutlined, SafetyCertificateOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, Button, Card, Checkbox, Col, DatePicker, Drawer, Form, Input, Progress,
  Row, Segmented, Select, Space, Statistic, Table, Tag, Typography, message,
} from 'antd'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { PageState } from '../../components/PageState'
import { api } from '../../services/api'
import { customerApi } from '../customer/customerApi'
import { projectApi } from '../project/projectApi'
import { buildProjectName } from '../project/projectName'
import type { Project } from '../project/types'
import { stageNames } from '../project/types'
import { dashboardApi, type DashboardFilters } from './dashboardApi'
import type { DashboardProject, MatrixRow, RiskHeatmapRow } from './types'

export function DashboardPage() {
  const [filters, setFilters] = useState<DashboardFilters>({})
  const [view, setView] = useState(() => window.localStorage.getItem('dashboard-project-view') === 'card' ? 'card' : 'list')
  const [createOpen, setCreateOpen] = useState(false)
  const summary = useQuery({ queryKey: ['dashboard-summary', filters], queryFn: () => dashboardApi.summary(filters) })
  const projects = useQuery({ queryKey: ['dashboard-projects', filters], queryFn: () => dashboardApi.projects(filters) })
  const risks = useQuery({ queryKey: ['dashboard-risks'], queryFn: dashboardApi.risks })
  const matrix = useQuery({ queryKey: ['dashboard-matrix'], queryFn: dashboardApi.matrix })
  const loading = summary.isLoading || projects.isLoading
  const error = summary.error ?? projects.error
  return <PageState loading={loading} error={error} onRetry={() => { void summary.refetch(); void projects.refetch() }}>
    <div className="dashboard-page">
      <div className="dashboard-heading"><div><span className="eyebrow dark">DELIVERY COMMAND CENTER</span>
        <Typography.Title level={2}>交付驾驶舱</Typography.Title><Typography.Paragraph>从全局健康度下钻到每个项目、风险与产品矩阵。</Typography.Paragraph></div>
        <Button type="primary" size="large" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>快速创建项目</Button></div>
      {summary.data && <Kpis value={summary.data} />}
      <Card className="dashboard-filter"><Space wrap>
        <Input.Search allowClear placeholder="搜索项目、客户或编号" style={{ width: 280 }} onSearch={keyword => setFilters(value => ({ ...value, keyword }))} />
        <Select allowClear placeholder="风险等级" style={{ width: 130 }} onChange={riskLevel => setFilters(value => ({ ...value, riskLevel }))}
          options={[{ value: 'RED', label: '高风险' }, { value: 'YELLOW', label: '需关注' }, { value: 'GREEN', label: '健康' }]} />
        <Select allowClear placeholder="项目状态" style={{ width: 130 }} onChange={status => setFilters(value => ({ ...value, status }))}
          options={['ACTIVE', 'SUSPENDED', 'CLOSING', 'CLOSED'].map(value => ({ value, label: value }))} />
      </Space></Card>
      <section className="dashboard-section"><div className="section-heading"><div><Typography.Title level={4}>项目全景</Typography.Title><span>{projects.data?.length ?? 0} 个可见项目 · 默认高密度列表</span></div>
        <Segmented value={view} onChange={value => { const next = String(value); setView(next); window.localStorage.setItem('dashboard-project-view', next) }} options={[
          { value: 'list', label: '列表', icon: <BarsOutlined /> }, { value: 'card', label: '卡片', icon: <AppstoreOutlined /> },
        ]} /></div>
        {view === 'list' ? <ProjectTable values={projects.data ?? []} /> : <ProjectCards values={projects.data ?? []} />}
      </section>
      <Row gutter={16} className="dashboard-lower"><Col span={12}><RiskHeatmap values={risks.data ?? []} /></Col><Col span={12}><ProductMatrix values={matrix.data ?? []} /></Col></Row>
      <QuickCreate open={createOpen} onClose={() => setCreateOpen(false)} />
    </div>
  </PageState>
}

function Kpis({ value }: { value: { activeProjects: number; healthScore: number; redProjects: number; openRisks: number; overdueMilestones: number } }) {
  const cards = [
    { label: '在途项目', value: value.activeProjects, suffix: '个', icon: <ProjectOutlined />, tone: 'blue' },
    { label: '项目健康度', value: value.healthScore, suffix: '分', icon: <HeartOutlined />, tone: value.healthScore >= 80 ? 'green' : 'orange' },
    { label: '高风险项目', value: value.redProjects, suffix: '个', icon: <FireOutlined />, tone: 'red' },
    { label: '开放风险', value: value.openRisks, suffix: '项', icon: <SafetyCertificateOutlined />, tone: 'orange' },
    { label: '逾期里程碑', value: value.overdueMilestones, suffix: '个', icon: <CalendarOutlined />, tone: 'purple' },
  ]
  return <Row gutter={12} className="kpi-row">{cards.map(item => <Col flex="1" key={item.label}><Card className={`kpi-card ${item.tone}`}><div className="kpi-icon">{item.icon}</div><Statistic title={item.label} value={item.value} suffix={item.suffix} /></Card></Col>)}</Row>
}

function ProjectTable({ values }: { values: DashboardProject[] }) {
  return <div className="project-table"><Table rowKey="id" pagination={{ pageSize: 8 }} dataSource={values} columns={[
    { title: '项目', dataIndex: 'name', render: (_: string, row: DashboardProject) => <div className="project-name-cell"><Link to={`/projects/${row.id}`}>{row.name}</Link><span>{row.code} · {row.customerName}</span></div> },
    { title: '产品 / 版本', dataIndex: 'productName', render: (_: string, row: DashboardProject) => <div>{row.productName}<span className="cell-sub">{row.productVersionName}</span></div> },
    { title: '阶段', dataIndex: 'currentStage', render: (value: string, row: DashboardProject) => <div className="compact-progress"><span>{stageNames[value] ?? value}</span><Progress percent={row.progress} showInfo={false} size="small" /></div> },
    { title: '健康度', dataIndex: 'riskLevel', width: 100, render: riskTag },
    { title: '风险', dataIndex: 'openRiskCount', width: 80, render: (value: number) => `${value} 项` },
    { title: '负责人', dataIndex: 'managerName', width: 110 },
    { title: '计划完成', dataIndex: 'plannedEndDate', width: 120 },
    { title: '', width: 44, render: (_: unknown, row: DashboardProject) => <Link to={`/projects/${row.id}`}><ArrowRightOutlined /></Link> },
  ]} /></div>
}

function ProjectCards({ values }: { values: DashboardProject[] }) {
  return <Row gutter={[12, 12]}>{values.map(project => <Col span={8} key={project.id}><Card data-testid={`dashboard-project-card-${project.id}`} className={`dashboard-project-card risk-${project.riskLevel.toLowerCase()}`}>
    <div className="project-card-head"><div><span>{project.code}</span><Link to={`/projects/${project.id}`}>{project.name}</Link></div>{riskTag(project.riskLevel)}</div>
    <p>{project.customerName} · {project.productName} {project.productVersionName}</p>
    <div className="card-stage"><span>{stageNames[project.currentStage]}</span><strong>{project.progress}%</strong></div><Progress percent={project.progress} showInfo={false} />
    <div className="project-card-foot"><span>{project.openRiskCount} 项风险</span><span>{project.managerName}</span><span>{project.plannedEndDate ?? '-'}</span></div>
  </Card></Col>)}</Row>
}

function RiskHeatmap({ values }: { values: RiskHeatmapRow[] }) {
  return <Card title="风险热力图" extra={<Tag color="red">实时</Tag>} className="insight-card"><div className="heatmap-head"><span>项目 / 类别</span><span>风险强度</span></div>
    {values.length ? values.slice(0, 8).map(item => <div className="heatmap-row" key={`${item.projectId}-${item.category}`}><div><Link to={`/projects/${item.projectId}`}>{item.projectName}</Link><span>{item.category} · {item.riskCount} 项</span></div>
      <div className={`heat-score score-${item.maxScore >= 20 ? 'high' : item.maxScore >= 10 ? 'medium' : 'low'}`}>{item.maxScore}</div></div>) : <div className="dashboard-empty">暂无开放风险</div>}
  </Card>
}

function ProductMatrix({ values }: { values: MatrixRow[] }) {
  return <Card title="产品 × 项目矩阵" extra={<Link to="/projects">查看全部</Link>} className="insight-card"><Table rowKey="productName" size="small" pagination={false} dataSource={values} columns={[
    { title: '产品', dataIndex: 'productName', width: 130, render: (value: string) => <strong>{value}</strong> },
    { title: '交付项目', dataIndex: 'projects', render: (projects: DashboardProject[]) => <Space wrap>{projects.map(project => <Link key={project.id} to={`/projects/${project.id}`}><Tag color={project.riskLevel === 'RED' ? 'red' : project.riskLevel === 'YELLOW' ? 'orange' : 'green'}>{project.code} · {stageNames[project.currentStage]}</Tag></Link>)}</Space> },
  ]} /></Card>
}

function QuickCreate({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [form] = Form.useForm()
  const lastSuggestedName = useRef<string>()
  const lastSelectionKey = useRef<string>()
  const customerId = Form.useWatch<number>('customerId', form)
  const productId = Form.useWatch<number>('productId', form)
  const productVersionId = Form.useWatch<number>('productVersionId', form)
  const client = useQueryClient()
  const customers = useQuery({ queryKey: ['active-customers'], queryFn: () => customerApi.list({ status: 'ACTIVE' }), enabled: open })
  const products = useQuery({ queryKey: ['bindable-products'], queryFn: projectApi.bindableProducts, enabled: open })
  const versions = useQuery({ queryKey: ['bindable-product-versions', productId], queryFn: () => projectApi.bindableVersions(productId), enabled: open && Boolean(productId) })
  useEffect(() => {
    const selectionKey = JSON.stringify([customerId, productId, productVersionId])
    const selectionChanged = selectionKey !== lastSelectionKey.current
    const customer = customers.data?.find(item => item.id === customerId)
    const product = products.data?.find(item => item.id === productId)
    const version = versions.data?.find(item => item.id === productVersionId)
    const suggestedName = buildProjectName(customer?.name, product?.name, version?.versionName)
    if (!suggestedName) return
    lastSelectionKey.current = selectionKey
    const currentName = form.getFieldValue('name')
    if (selectionChanged || !currentName || currentName === lastSuggestedName.current) form.setFieldValue('name', suggestedName)
    lastSuggestedName.current = suggestedName
  }, [customerId, productId, productVersionId, customers.data, products.data, versions.data, form])
  const close = () => { form.resetFields(); onClose() }
  const create = useMutation({ mutationFn: async (values: Record<string, unknown>) => {
    const initialize = Boolean(values.initializeAgent)
    const project = await projectApi.create({ ...values, initializeAgent: undefined, startDate: formatDate(values.startDate), plannedEndDate: formatDate(values.plannedEndDate), gateMode: 'BLOCK' })
    if (initialize) await api(`/api/v1/projects/${project.id}/agent-jobs`, { method: 'POST', headers: { 'Idempotency-Key': `init-${project.id}` }, body: JSON.stringify({ skill: 'deliver-init', scenario: 'normal' }) })
    return project
  }, onSuccess: async (project: Project) => { await Promise.all([client.invalidateQueries({ queryKey: ['dashboard-summary'] }), client.invalidateQueries({ queryKey: ['dashboard-projects'] })]); form.resetFields(); onClose(); message.success(`${project.name} 已创建`) }, onError: (error: Error) => message.error(error.message) })
  return <Drawer title="快速创建交付项目" width={520} open={open} onClose={close} extra={<Button type="primary" loading={create.isPending} onClick={() => form.submit()}>创建项目</Button>}>
    <Alert className="drawer-hint" type="info" showIcon message="创建后自动初始化七阶段，可选立即执行 deliver-init。" />
    <Form form={form} layout="vertical" initialValues={{ initializeAgent: true }} onFinish={values => create.mutate(values)}>
      <Form.Item label="客户" name="customerId" rules={[{ required: true, message: '请选择客户' }]}>
        <Select showSearch optionFilterProp="label" loading={customers.isLoading} placeholder="选择启用客户"
          notFoundContent={customers.isError ? '客户加载失败，请重试' : <div className="customer-select-empty">
            <span>请先创建启用客户</span><Link to="/customers">前往客户管理</Link>
          </div>}
          options={customers.data?.map(item => ({ value: item.id, label: `${item.name}${item.shortName ? ` · ${item.shortName}` : ''}` }))} />
      </Form.Item>
      <Row gutter={12}><Col span={12}><Form.Item label="产品" name="productId" rules={[{ required: true }]}><Select loading={products.isLoading}
        onChange={(value) => form.setFieldsValue({ productId: value, productVersionId: undefined })} options={products.data?.map(item => ({ value: item.id, label: `${item.code} · ${item.name}` }))} /></Form.Item></Col><Col span={12}><Form.Item label="版本" name="productVersionId" rules={[{ required: true }]}><Select disabled={!productId} loading={versions.isLoading} options={versions.data?.map(item => ({ value: item.id, label: item.versionName }))} /></Form.Item></Col></Row>
      <Form.Item label="项目名称" name="name" extra="项目编号由系统自动生成"
        rules={[{ required: true }]}><Input /></Form.Item>
      <Row gutter={12}><Col span={12}><Form.Item label="计划开始" name="startDate"><DatePicker style={{ width: '100%' }} /></Form.Item></Col><Col span={12}><Form.Item label="计划完成" name="plannedEndDate"><DatePicker style={{ width: '100%' }} /></Form.Item></Col></Row>
      <Form.Item name="initializeAgent" valuePropName="checked"><Checkbox>创建后执行项目初始化 Skill</Checkbox></Form.Item>
    </Form>
  </Drawer>
}

function riskTag(value: string) { const config = value === 'RED' ? ['red', '高风险'] : value === 'YELLOW' ? ['orange', '需关注'] : ['green', '健康']; return <Tag color={config[0]}>{config[1]}</Tag> }
function formatDate(value: unknown) { return value && typeof value === 'object' && 'format' in value ? (value as { format(pattern: string): string }).format('YYYY-MM-DD') : null }
