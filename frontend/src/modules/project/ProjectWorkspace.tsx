import {
  AppstoreOutlined, BarsOutlined, CalendarOutlined, PlusOutlined, SearchOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button, Card, Col, DatePicker, Drawer, Empty, Form, Input, Progress, Radio, Row,
  Select, Space, Table, Tag, Typography, message,
} from 'antd'
import dayjs from 'dayjs'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { PageState } from '../../components/PageState'
import { customerApi } from '../customer/customerApi'
import { projectApi } from './projectApi'
import { buildProjectName } from './projectName'
import { stageNames, type Project } from './types'

const riskMeta = {
  RED: { color: 'red', label: '高风险' },
  YELLOW: { color: 'orange', label: '需关注' },
  GREEN: { color: 'green', label: '健康' },
} as const

export function ProjectWorkspace() {
  const [view, setView] = useState<'list' | 'card'>('list')
  const [keyword, setKeyword] = useState('')
  const [risk, setRisk] = useState<string>()
  const [createOpen, setCreateOpen] = useState(false)
  const query = useQuery({ queryKey: ['projects'], queryFn: projectApi.list })
  const data = useMemo(() => (query.data ?? []).filter(project =>
    (!keyword || `${project.name}${project.code}${project.customerName}`.toLowerCase().includes(keyword.toLowerCase()))
    && (!risk || project.riskLevel === risk)), [query.data, keyword, risk])

  const columns = [
    { title: '项目', key: 'project', width: 290, render: (_: unknown, item: Project) => <div className="project-name-cell">
      <Link to={`/projects/${item.id}`}>{item.name}</Link><span>{item.code} · {item.customerName}</span></div> },
    { title: '产品 / 版本', key: 'product', width: 180, render: (_: unknown, item: Project) =>
      <span>{item.productName}<small className="cell-sub">{item.productVersionName}</small></span> },
    { title: '当前阶段', dataIndex: 'currentStage', width: 135,
      render: (value: string) => <Tag color="blue">{stageNames[value] ?? value}</Tag> },
    { title: '健康度', dataIndex: 'riskLevel', width: 105, render: (value: keyof typeof riskMeta) =>
      <Tag color={riskMeta[value].color}>{riskMeta[value].label}</Tag> },
    { title: '交付负责人', dataIndex: 'managerName', width: 130, render: (value: string) => <Space><UserOutlined />{value}</Space> },
    { title: '计划完成', dataIndex: 'plannedEndDate', width: 120 },
    { title: '', key: 'action', width: 80, render: (_: unknown, item: Project) => <Link to={`/projects/${item.id}`}>进入</Link> },
  ]

  return <div className="project-workspace">
    <div className="page-heading compact">
      <div><Typography.Title level={2}>项目空间</Typography.Title>
        <Typography.Paragraph>按七阶段管理项目、风险、里程碑、模板和自动化产出</Typography.Paragraph></div>
      <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>创建项目</Button>
    </div>
    <Card className="filter-surface" variant="borderless">
      <div className="project-toolbar">
        <Space>
          <Input allowClear prefix={<SearchOutlined />} placeholder="搜索项目、客户或编号" value={keyword}
            onChange={event => setKeyword(event.target.value)} style={{ width: 260 }} />
          <Select allowClear placeholder="全部健康度" value={risk} onChange={setRisk} style={{ width: 140 }}
            options={[{ value: 'RED', label: '高风险' }, { value: 'YELLOW', label: '需关注' }, { value: 'GREEN', label: '健康' }]} />
          <span className="result-count">共 {data.length} 个项目</span>
        </Space>
        <Radio.Group value={view} onChange={event => setView(event.target.value)} buttonStyle="solid">
          <Radio.Button value="list" aria-label="列表视图"><BarsOutlined /><span className="sr-only">列表视图</span></Radio.Button>
          <Radio.Button value="card" aria-label="卡片视图"><AppstoreOutlined /><span className="sr-only">卡片视图</span></Radio.Button>
        </Radio.Group>
      </div>
    </Card>
    <PageState loading={query.isLoading} error={query.error} empty={!query.isLoading && data.length === 0}
      onRetry={() => void query.refetch()}>
      {view === 'list' ? <Table rowKey="id" className="project-table" columns={columns} dataSource={data}
        pagination={{ pageSize: 12, hideOnSinglePage: true }} /> :
        <Row gutter={[16, 16]}>{data.map(project => <Col xs={24} xl={12} xxl={8} key={project.id}>
          <ProjectCard project={project} />
        </Col>)}</Row>}
    </PageState>
    <CreateProjectDrawer open={createOpen} onClose={() => setCreateOpen(false)} />
  </div>
}

function ProjectCard({ project }: { project: Project }) {
  const completed = project.stages.filter(item => item.status === 'COMPLETED').length
  const progress = project.stages.length ? Math.round(completed / 7 * 100) : 0
  const risk = riskMeta[project.riskLevel]
  return <Card data-testid={`project-card-${project.id}`} className={`project-card risk-${project.riskLevel.toLowerCase()}`} hoverable>
    <div className="project-card-head"><div><span>{project.code}</span><Link to={`/projects/${project.id}`}>{project.name}</Link></div>
      <Tag color={risk.color}>{risk.label}</Tag></div>
    <div className="project-card-product">{project.productName} · {project.productVersionName}</div>
    <div className="stage-progress"><span>当前：{stageNames[project.currentStage]}</span><span>{progress}%</span></div>
    <Progress percent={progress} showInfo={false} size="small" />
    <div className="project-card-foot"><span><UserOutlined /> {project.managerName}</span>
      <span><CalendarOutlined /> {project.plannedEndDate ?? '待排期'}</span></div>
  </Card>
}

function CreateProjectDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [form] = Form.useForm()
  const lastSuggestedName = useRef<string>()
  const lastSelectionKey = useRef<string>()
  const customerId = Form.useWatch<number>('customerId', form)
  const productId = Form.useWatch<number>('productId', form)
  const productVersionId = Form.useWatch<number>('productVersionId', form)
  const client = useQueryClient()
  const customers = useQuery({ queryKey: ['active-customers'], queryFn: () => customerApi.list({ status: 'ACTIVE' }), enabled: open })
  const products = useQuery({ queryKey: ['bindable-products'], queryFn: projectApi.bindableProducts, enabled: open })
  const versions = useQuery({ queryKey: ['bindable-product-versions', productId],
    queryFn: () => projectApi.bindableVersions(productId!), enabled: !!productId && open })
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
  const create = useMutation({ mutationFn: projectApi.create, onSuccess: async () => {
    await client.invalidateQueries({ queryKey: ['projects'] })
    message.success('项目创建成功，七阶段已初始化')
    form.resetFields(); onClose()
  } })
  return <Drawer width={520} open={open} onClose={close} title="创建交付项目"
    extra={<Button type="primary" loading={create.isPending} onClick={() => form.submit()}>创建项目</Button>}>
    <div className="drawer-hint">项目创建后会自动生成七阶段、初始负责人和项目活动记录。</div>
    <Form form={form} layout="vertical" onFinish={values => create.mutate({ ...values,
      startDate: values.startDate?.format('YYYY-MM-DD'), plannedEndDate: values.plannedEndDate?.format('YYYY-MM-DD') })}
      initialValues={{ gateMode: 'BLOCK', startDate: dayjs() }}>
      <Form.Item label="客户" name="customerId" rules={[{ required: true, message: '请选择客户' }]}>
        <Select showSearch optionFilterProp="label" loading={customers.isLoading} placeholder="选择启用客户"
          notFoundContent={customers.isError ? '客户加载失败，请重试' : <div className="customer-select-empty">
            <span>请先创建启用客户</span><Link to="/customers">前往客户管理</Link>
          </div>}
          options={customers.data?.map(item => ({ value: item.id, label: `${item.name}${item.shortName ? ` · ${item.shortName}` : ''}` }))} />
      </Form.Item>
      <Row gutter={12}><Col span={12}><Form.Item label="产品" name="productId" rules={[{ required: true }]}>
        <Select loading={products.isLoading} onChange={(value) => form.setFieldsValue({ productId: value, productVersionId: undefined })}
          options={products.data?.map(item => ({ value: item.id, label: item.name }))} /></Form.Item></Col>
        <Col span={12}><Form.Item label="标品版本" name="productVersionId" rules={[{ required: true }]}>
          <Select disabled={!productId} loading={versions.isLoading} options={versions.data?.map(item => ({ value: item.id, label: item.versionName }))} /></Form.Item></Col></Row>
      <Form.Item label="项目名称" name="name" extra="项目编号由系统自动生成"
        rules={[{ required: true }]}><Input /></Form.Item>
      <Row gutter={12}><Col span={12}><Form.Item label="开始日期" name="startDate"><DatePicker style={{ width: '100%' }} /></Form.Item></Col>
        <Col span={12}><Form.Item label="计划完成" name="plannedEndDate"><DatePicker style={{ width: '100%' }} /></Form.Item></Col></Row>
      <Form.Item label="阶段门禁模式" name="gateMode"><Radio.Group options={[{ value: 'BLOCK', label: '阻断模式' }, { value: 'WARNING', label: '警告模式' }]} /></Form.Item>
    </Form>
  </Drawer>
}
