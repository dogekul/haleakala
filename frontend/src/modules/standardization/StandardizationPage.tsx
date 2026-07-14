import {
  ApiOutlined, ArrowRightOutlined, AuditOutlined, BarChartOutlined, CheckCircleOutlined,
  DollarOutlined, ExperimentOutlined, FileProtectOutlined, PlusOutlined, ReloadOutlined,
  RiseOutlined, SettingOutlined, SyncOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, Button, Card, Col, Drawer, Empty, Form, Input, InputNumber, Modal, Progress,
  Row, Select, Space, Statistic, Table, Tabs, Tag, Typography, message,
} from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useAuth } from '../../app/AuthProvider'
import { productApi } from '../product/productApi'
import { ConvertToFeatureDrawer } from './ConvertToFeatureDrawer'
import { projectApi } from '../project/projectApi'
import { requirementApi } from '../requirement/requirementApi'
import { standardizationApi } from './standardizationApi'
import type { Assessment, Baseline, CostSummary, CustomDevTask, Deviation, FlywheelMetric, StandardizationDebt } from './types'

const dimensionLabels = { FUNCTION: '标准功能', CONFIGURATION: '可配置项', EXTENSION: '扩展点' }
const debtStates = ['CANDIDATE', 'PENDING', 'INCLUDED', 'VERIFYING', 'CLOSED'] as const
const debtLabels: Record<string, string> = { CANDIDATE: '候选', PENDING: '待评审', INCLUDED: '已纳入', VERIFYING: '验证中', CLOSED: '已关闭' }

export function StandardizationPage() {
  const { me } = useAuth()
  const canWrite = me?.permissions.includes('standardization:write') ?? false
  const canConvert = canWrite && (me?.permissions.includes('product:read') ?? false)
    && (me?.permissions.includes('product:write') ?? false)
  const [productId, setProductId] = useState<number>()
  const [versionId, setVersionId] = useState<number>()
  const [baselineEditor, setBaselineEditor] = useState<Baseline | null | undefined>()
  const [converting, setConverting] = useState<StandardizationDebt>()
  const products = useQuery({ queryKey: ['products-standardization'], queryFn: productApi.products })
  const versions = useQuery({ queryKey: ['versions-standardization', productId], queryFn: () => productApi.versions(productId!), enabled: Boolean(productId) })
  useEffect(() => { if (!productId && products.data?.length) setProductId(products.data[0].id) }, [productId, products.data])
  useEffect(() => { setVersionId(versions.data?.[0]?.id) }, [versions.data])
  const enabled = Boolean(versionId)
  const baselines = useQuery({ queryKey: ['baselines', versionId], queryFn: () => standardizationApi.baselines(versionId!), enabled })
  const assessment = useQuery({ queryKey: ['assessment', versionId], queryFn: () => standardizationApi.assess(versionId!), enabled: enabled && canWrite })
  const deviations = useQuery({ queryKey: ['deviations', versionId], queryFn: () => standardizationApi.deviations(versionId!), enabled })
  const debts = useQuery({ queryKey: ['standardization-debts', versionId], queryFn: () => standardizationApi.debts(versionId!), enabled })
  const costs = useQuery({ queryKey: ['standardization-costs', versionId], queryFn: () => standardizationApi.costs(versionId!), enabled })
  const tasks = useQuery({ queryKey: ['standardization-tasks', versionId], queryFn: () => standardizationApi.tasks(versionId!), enabled })
  const flywheel = useQuery({ queryKey: ['standardization-flywheel', versionId], queryFn: () => standardizationApi.flywheel(versionId!), enabled: enabled && canWrite })
  const currentProduct = products.data?.find(item => item.id === productId)
  const currentVersion = versions.data?.find(item => item.id === versionId)

  return <div className="standardization-page">
    <div className="workshop-heading"><div><span className="eyebrow dark">PRODUCT STANDARDIZATION</span><Typography.Title level={2}>标准化中心</Typography.Title>
      <Typography.Paragraph>把项目偏离转化为产品能力，让每次交付都降低下一次的成本。</Typography.Paragraph></div>
      <Space><Select aria-label="产品" showSearch optionFilterProp="label" value={productId} loading={products.isLoading} onChange={value => { setProductId(value); setVersionId(undefined) }} style={{ width: 170 }} options={products.data?.map(item => ({ value: item.id, label: item.name }))} />
        <Select aria-label="版本" showSearch optionFilterProp="label" value={versionId} loading={versions.isLoading} onChange={setVersionId} style={{ width: 130 }} options={versions.data?.map(item => ({ value: item.id, label: item.versionName }))} /></Space></div>
    <div className="standardization-context"><div><ApiOutlined /><span>当前基线</span><strong>{currentProduct?.name ?? '请选择产品'} / {currentVersion?.versionName ?? '-'}</strong></div>
      <div><span>评估周期</span><strong>{assessment.data?.period ?? '-'}</strong></div><div><span>标准覆盖</span><strong>{assessment.data ? `${assessment.data.standardCoverage}%` : '-'}</strong></div><div><span>二开实际成本</span><strong>{currency(costs.data?.actualCost)}</strong></div></div>
    {!versionId ? <Card><Empty description="请先选择产品版本" /></Card> : <Tabs className="standardization-tabs" items={[
      { key: 'baseline', label: <span><FileProtectOutlined />能力基线</span>, children: <BaselineView values={baselines.data ?? []} loading={baselines.isLoading} canWrite={canWrite} onEdit={setBaselineEditor} /> },
      { key: 'maturity', label: <span><BarChartOutlined />成熟度</span>, children: <MaturityView value={assessment.data} loading={assessment.isLoading} canWrite={canWrite} unavailable={!canWrite && !assessment.data} onRefresh={() => assessment.refetch()} /> },
      { key: 'deviation', label: <span><ExperimentOutlined />偏离度</span>, children: <DeviationView values={deviations.data ?? []} loading={deviations.isLoading} /> },
      { key: 'debt', label: <span><AuditOutlined />标准化债务</span>, children: <DebtView versionId={versionId} values={debts.data ?? []} loading={debts.isLoading} canWrite={canWrite} canConvert={canConvert} onConvert={setConverting} /> },
      { key: 'cost', label: <span><DollarOutlined />成本归集</span>, children: <CostView versionId={versionId} value={costs.data} tasks={tasks.data ?? []} loading={costs.isLoading || tasks.isLoading} canWrite={canWrite} /> },
      { key: 'flywheel', label: <span><SyncOutlined />产品飞轮</span>, children: <FlywheelView value={flywheel.data} loading={flywheel.isLoading} unavailable={!canWrite && !flywheel.data} /> },
    ]} />}
    <BaselineEditor versionId={versionId} value={baselineEditor} canWrite={canWrite} onClose={() => setBaselineEditor(undefined)} />
    <ConvertToFeatureDrawer debt={converting} defaultProductId={productId} onClose={() => setConverting(undefined)} />
  </div>
}

function BaselineView({ values, loading, canWrite, onEdit }: { values: Baseline[]; loading: boolean; canWrite: boolean; onEdit(value: Baseline | null): void }) {
  const groups = useMemo(() => Object.keys(dimensionLabels).map(dimension => ({ dimension, values: values.filter(item => item.dimension === dimension) })), [values])
  return <div><div className="section-heading"><div><Typography.Title level={4}>产品能力卡</Typography.Title><span>{values.length} 张有效基线，同时说清标准范围、配置和扩展边界</span></div>{canWrite && <Button type="primary" icon={<PlusOutlined />} onClick={() => onEdit(null)}>新建能力卡</Button>}</div>
    {groups.map(group => <section key={group.dimension} className="baseline-section"><div className="baseline-section-title"><strong>{dimensionLabels[group.dimension as keyof typeof dimensionLabels]}</strong><Tag>{group.values.length}</Tag></div>
      <Row gutter={[12, 12]}>{group.values.map(item => <Col span={8} key={item.id}><Card loading={loading} className="baseline-card" hoverable={canWrite} onClick={() => canWrite && onEdit(item)}><div className="baseline-card-head"><Tag color="blue">{item.capabilityCode}</Tag><SettingOutlined /></div><h3>{item.capabilityName}</h3><p>{item.scopeDescription}</p><div><span>{item.ownerName ?? '待指定负责人'}</span><Tag color="success">{item.status}</Tag></div></Card></Col>)}
        {!group.values.length && <Col span={24}><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未定义" /></Col>}</Row></section>)}</div>
}

function MaturityView({ value, loading, canWrite, unavailable, onRefresh }: { value?: Assessment; loading: boolean; canWrite: boolean; unavailable: boolean; onRefresh(): void }) {
  if (unavailable) return <Alert showIcon type="info" message="暂无成熟度快照" description="当前账号无权刷新成熟度评估。" />
  const metrics: Array<[string, number, string]> = value ? [
    ['标准覆盖率', value.standardCoverage, '30%'], ['扩展复用率', value.reuseRate, '40%'], ['文档完备度', value.documentationScore, '10%'],
    ['扩展就绪度', value.extensionReadiness, '10%'], ['交付稳定性', value.deliveryStability, '10%'],
  ] : []
  return <Card loading={loading} className="maturity-card"><div className="maturity-layout"><div className="maturity-score"><Progress type="dashboard" percent={value?.maturityScore ?? 0} strokeColor="#3370ff" size={190} format={percent => <div><b>{percent}</b><span>成熟度得分</span></div>} /><Tag color="blue">{maturityLevel(value?.maturityScore ?? 0)}</Tag>{canWrite && <Button icon={<ReloadOutlined />} onClick={onRefresh}>重新评估</Button>}</div><div className="maturity-bars">{metrics.map(([name, score]) => <div key={name}><span>{name}</span><Progress percent={score} strokeColor="#3370ff" /></div>)}</div></div>
    <Alert showIcon type="info" message="评分由确认需求、扩展复用、能力卡文档和项目稳定性确定性计算。" />
    <Table className="numeric-table" rowKey={row => row[0]} pagination={false} dataSource={metrics} columns={[{ title: '指标', render: (_, row) => row[0] }, { title: '当前值', render: (_, row) => `${row[1]}%` }, { title: '权重', render: (_, row) => row[2] }]} /></Card>
}

function DeviationView({ values, loading }: { values: Deviation[]; loading: boolean }) {
  return <Card loading={loading} title="项目偏离度对比" extra="偏离度 = (L1 + L2) / 已确认需求"><div className="deviation-bars">{values.map(item => <div key={item.projectId}><span>{item.projectCode}</span><Progress percent={item.deviationRate} status={item.deviationRate > 50 ? 'exception' : 'normal'} /></div>)}</div>
    <Table rowKey="projectId" dataSource={values} pagination={false} columns={[{ title: '项目', render: (_, row) => <div><strong>{row.projectName}</strong><span className="cell-sub">{row.projectCode}</span></div> }, { title: '确认总数', dataIndex: 'total' }, { title: 'L0', dataIndex: 'l0' }, { title: 'L1', dataIndex: 'l1' }, { title: 'L2', dataIndex: 'l2' }, { title: '偏离度', dataIndex: 'deviationRate', render: value => <Tag color={value > 50 ? 'red' : value > 25 ? 'orange' : 'green'}>{value}%</Tag> }]} /></Card>
}

function DebtView({ versionId, values, loading, canWrite, canConvert, onConvert }: { versionId: number; values: StandardizationDebt[]; loading: boolean; canWrite: boolean; canConvert: boolean; onConvert(value: StandardizationDebt): void }) {
  const client = useQueryClient()
  const [closing, setClosing] = useState<StandardizationDebt>()
  const [note, setNote] = useState('')
  const evaluate = useMutation({ mutationFn: () => standardizationApi.evaluateDebts(versionId), onSuccess: async () => { await client.invalidateQueries({ queryKey: ['standardization-debts', versionId] }); message.success('债务候选评估完成') } })
  const advance = useMutation({ mutationFn: ({ item, note }: { item: StandardizationDebt; note?: string }) => { const index = debtStates.indexOf(item.status); return standardizationApi.transitionDebt(item.id, debtStates[index + 1], note) }, onSuccess: async () => { setClosing(undefined); setNote(''); await client.invalidateQueries({ queryKey: ['standardization-debts', versionId] }); message.success('状态已推进') } })
  const next = (item: StandardizationDebt) => { if (item.status === 'VERIFYING') setClosing(item); else advance.mutate({ item }) }
  return <Card loading={loading} title={<Space><AuditOutlined />标准化债务队列</Space>} extra={canWrite && <Button type="primary" loading={evaluate.isPending} onClick={() => evaluate.mutate()}>评估高频二开</Button>}><Alert className="debt-alert" showIcon type="warning" message="同一扩展点出现在 5 个及以上项目时自动成为候选；关闭前必须完成版本验证。" />
    <Table rowKey="id" dataSource={values} pagination={false} columns={[{ title: '候选能力', render: (_, row) => <div><strong>{row.title}</strong><code className="cell-sub">{row.patternKey}</code></div> }, { title: '出现次数', dataIndex: 'occurrenceCount' }, { title: '覆盖项目', dataIndex: 'distinctProjects', render: value => `${value} 个` }, { title: '状态', dataIndex: 'status', render: value => <Tag color={value === 'CLOSED' ? 'success' : 'processing'}>{debtLabels[value]}</Tag> }, { title: '操作', render: (_, row) => <Space>{canConvert && !row.convertedFeatureId && ['CANDIDATE', 'PENDING'].includes(row.status) && <Button size="small" type="primary" ghost onClick={() => onConvert(row)}>转为产品功能</Button>}{canWrite && row.status !== 'CLOSED' && <Button size="small" onClick={() => next(row)}>{debtLabels[debtStates[debtStates.indexOf(row.status) + 1]]}<ArrowRightOutlined /></Button>}{row.status === 'CLOSED' && <CheckCircleOutlined className="success-icon" />}</Space> }]} />
    <Modal title="关闭标准化债务" open={Boolean(closing)} onCancel={() => setClosing(undefined)} onOk={() => closing && advance.mutate({ item: closing, note })} okButtonProps={{ disabled: !note.trim(), loading: advance.isPending }}><Alert type="info" showIcon message="请填写版本回归和项目验证结论。" /><Input.TextArea className="verification-note" rows={5} value={note} onChange={event => setNote(event.target.value)} /></Modal></Card>
}

function CostView({ versionId, value, tasks, loading, canWrite }: { versionId: number; value?: CostSummary; tasks: CustomDevTask[]; loading: boolean; canWrite: boolean }) {
  const [editing, setEditing] = useState<CustomDevTask | null | undefined>()
  const overrun = Number(value?.actualCost ?? 0) - Number(value?.estimatedCost ?? 0)
  return <div><Row gutter={12} className="cost-kpis"><Col span={6}><Card loading={loading}><Statistic title="预估人天" value={value?.estimatedPersonDays ?? 0} suffix="人天" /></Card></Col><Col span={6}><Card loading={loading}><Statistic title="实际人天" value={value?.actualPersonDays ?? 0} suffix="人天" /></Card></Col><Col span={6}><Card loading={loading}><Statistic title="实际成本" value={value?.actualCost ?? 0} prefix="¥" /></Card></Col><Col span={6}><Card loading={loading}><Statistic title="成本偏差" value={overrun} prefix="¥" valueStyle={{ color: overrun > 0 ? '#f54a45' : '#2ea869' }} /></Card></Col></Row>
    <Card title="二开任务与成本明细" extra={canWrite && <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing(null)}>新建二开任务</Button>}><Table rowKey="id" loading={loading} dataSource={tasks} pagination={false} columns={[
      { title: '任务', render: (_, item: CustomDevTask) => <div><strong>{item.title}</strong><span className="cell-sub">{item.requirementCode} · {item.projectName}</span></div> },
      { title: '状态', dataIndex: 'status', render: status => <Tag>{taskStatusLabels[status] ?? status}</Tag> },
      { title: '扩展点', dataIndex: 'extensionPoint', render: value => value || '未归类' },
      { title: '预估 / 实际人天', render: (_, item: CustomDevTask) => `${item.estimatedPersonDays ?? 0} / ${item.actualPersonDays ?? 0}` },
      { title: '预估 / 实际成本', render: (_, item: CustomDevTask) => `${currency(item.estimatedCost)} / ${currency(item.actualCost)}` },
      { title: '操作', render: (_, item: CustomDevTask) => canWrite ? <Button size="small" onClick={() => setEditing(item)}>编辑</Button> : null },
    ]} /></Card>
    <Card title="按扩展点归集"><Table rowKey="extension_point" dataSource={value?.byExtensionPoint ?? []} pagination={false} columns={[{ title: '扩展点', dataIndex: 'extension_point' }, { title: '任务数', dataIndex: 'task_count' }, { title: '实际人天', dataIndex: 'person_days' }, { title: '实际成本', dataIndex: 'amount', render: currency }]} /></Card>
    <TaskEditor productVersionId={versionId} value={editing} onClose={() => setEditing(undefined)} />
  </div>
}

const taskStatusLabels: Record<string, string> = { BACKLOG: '待排期', IN_PROGRESS: '进行中', BLOCKED: '阻塞', DONE: '已完成', CANCELLED: '已取消' }

function TaskEditor({ productVersionId, value, onClose }: { productVersionId: number; value: CustomDevTask | null | undefined; onClose(): void }) {
  const [form] = Form.useForm()
  const client = useQueryClient()
  const projectId = Form.useWatch('projectId', form)
  const projects = useQuery({ queryKey: ['projects-standardization-task'], queryFn: projectApi.list, enabled: value !== undefined })
  const requirements = useQuery({ queryKey: ['requirements-standardization-task'], queryFn: requirementApi.list, enabled: value !== undefined })
  useEffect(() => {
    if (value === undefined) return
    form.setFieldsValue(value ?? { status: 'BACKLOG' })
  }, [form, value])
  const save = useMutation({
    mutationFn: (input: Record<string, unknown>) => standardizationApi.saveTask(value?.id, { ...input, version: value?.version ?? 0 }),
    onSuccess: async () => {
      await Promise.all([
        client.invalidateQueries({ queryKey: ['standardization-tasks', productVersionId] }),
        client.invalidateQueries({ queryKey: ['standardization-costs', productVersionId] }),
        client.invalidateQueries({ queryKey: ['standardization-flywheel', productVersionId] }),
      ])
      form.resetFields(); onClose(); message.success(value ? '二开任务成本已更新' : '二开任务已创建')
    },
    onError: (error: Error) => message.error(error.message),
  })
  const projectOptions = projects.data?.filter(project => project.productVersionId === productVersionId).map(project => ({ value: project.id, label: `${project.code} · ${project.name}` }))
  const requirementOptions = requirements.data?.filter(requirement => requirement.projectId === projectId && requirement.status === 'CONFIRMED' && requirement.confirmedLevel === 'L1').map(requirement => ({ value: requirement.id, label: `${requirement.code} · ${requirement.title}` }))
  return <Drawer width={650} title={value ? '编辑二开任务成本' : '新建二开任务'} open={value !== undefined} onClose={onClose} extra={<Button type="primary" loading={save.isPending} onClick={() => form.submit()}>保存任务</Button>}>
    <Form form={form} layout="vertical" onFinish={save.mutate}>
      <Row gutter={12}><Col span={12}><Form.Item label="项目" name="projectId" rules={[{ required: true }]}><Select disabled={Boolean(value)} options={projectOptions} onChange={() => form.setFieldValue('requirementId', undefined)} /></Form.Item></Col><Col span={12}><Form.Item label="L1 需求" name="requirementId" rules={[{ required: true }]}><Select disabled={Boolean(value) || !projectId} options={requirementOptions} /></Form.Item></Col></Row>
      <Form.Item label="任务标题" name="title" rules={[{ required: true }]}><Input /></Form.Item>
      <Row gutter={12}><Col span={12}><Form.Item label="状态" name="status" rules={[{ required: true }]}><Select options={Object.entries(taskStatusLabels).map(([status, label]) => ({ value: status, label }))} /></Form.Item></Col><Col span={12}><Form.Item label="技术负责人 ID" name="technicalOwnerId"><InputNumber min={1} style={{ width: '100%' }} /></Form.Item></Col></Row>
      <Form.Item label="扩展点" name="extensionPoint"><Input placeholder="例如 reconciliation.retry" /></Form.Item>
      <Row gutter={12}><Col span={12}><Form.Item label="预估人天" name="estimatedPersonDays"><InputNumber min={0} precision={2} style={{ width: '100%' }} /></Form.Item></Col><Col span={12}><Form.Item label="实际人天" name="actualPersonDays"><InputNumber min={0} precision={2} style={{ width: '100%' }} /></Form.Item></Col><Col span={12}><Form.Item label="预估成本（元）" name="estimatedCost"><InputNumber min={0} precision={2} style={{ width: '100%' }} /></Form.Item></Col><Col span={12}><Form.Item label="实际成本（元）" name="actualCost"><InputNumber min={0} precision={2} style={{ width: '100%' }} /></Form.Item></Col></Row>
    </Form>
  </Drawer>
}

function FlywheelView({ value, loading, unavailable }: { value?: FlywheelMetric; loading: boolean; unavailable: boolean }) {
  if (unavailable) return <Alert showIcon type="info" message="暂无产品飞轮快照" description="当前账号无权刷新产品飞轮指标。" />
  const steps = [['交付需求', value?.confirmedRequirements ?? 0], ['标准覆盖', `${value?.standardCoverage ?? 0}%`], ['扩展复用', `${value?.reuseRate ?? 0}%`], ['债务闭环', value?.debtClosedCount ?? 0]]
  return <Card loading={loading} className="flywheel-card"><div className="flywheel-heading"><div><RiseOutlined /><Typography.Title level={3}>交付 → 沉淀 → 复用 → 更高标准化</Typography.Title><p>所有指标都可追溯到人工确认需求、实际二开任务和债务状态。</p></div><Tag color="blue">{value?.period}</Tag></div>
    <div className="flywheel-loop">{steps.map(([label, metric], index) => <div key={String(label)}><span>{index + 1}</span><strong>{metric}</strong><small>{label}</small></div>)}</div>
    <Table className="numeric-table" pagination={false} rowKey="name" dataSource={[
      { name: '已确认需求', value: value?.confirmedRequirements ?? 0, source: '需求工坊' }, { name: 'L0 标品满足', value: value?.l0Count ?? 0, source: '人工分类结论' }, { name: 'L1 二开', value: value?.l1Count ?? 0, source: '人工分类结论' }, { name: '已关闭债务', value: value?.debtClosedCount ?? 0, source: '债务流转' }, { name: '实际二开成本', value: currency(value?.customCost), source: '成本归集' },
    ]} columns={[{ title: '指标', dataIndex: 'name' }, { title: '数值', dataIndex: 'value' }, { title: '来源', dataIndex: 'source' }]} /></Card>
}

function BaselineEditor({ versionId, value, canWrite, onClose }: { versionId?: number; value: Baseline | null | undefined; canWrite: boolean; onClose(): void }) {
  const [form] = Form.useForm()
  const client = useQueryClient()
  useEffect(() => { if (value !== undefined) form.setFieldsValue(value ?? { productVersionId: versionId, dimension: 'FUNCTION' }) }, [form, value, versionId])
  const save = useMutation({ mutationFn: (input: Record<string, unknown>) => standardizationApi.saveBaseline(value?.id, { ...input, productVersionId: versionId, version: value?.version ?? 0 }), onSuccess: async () => { await client.invalidateQueries({ queryKey: ['baselines', versionId] }); message.success('能力基线已保存'); onClose() }, onError: (error: Error) => message.error(error.message) })
  return <Drawer width={580} title={value ? '编辑能力卡' : '新建能力卡'} open={value !== undefined} onClose={onClose} extra={canWrite && <Button type="primary" loading={save.isPending} onClick={() => form.submit()}>保存基线</Button>}><Form form={form} layout="vertical" disabled={!canWrite} onFinish={save.mutate}><Row gutter={12}><Col span={9}><Form.Item label="能力编码" name="capabilityCode" rules={[{ required: true }]}><Input placeholder="AR-001" /></Form.Item></Col><Col span={15}><Form.Item label="能力名称" name="capabilityName" rules={[{ required: true }]}><Input /></Form.Item></Col></Row><Form.Item label="能力维度" name="dimension" rules={[{ required: true }]}><Select options={Object.entries(dimensionLabels).map(([value, label]) => ({ value, label }))} /></Form.Item><Form.Item label="标准范围" name="scopeDescription" rules={[{ required: true }]}><Input.TextArea rows={5} placeholder="明确标准流程、输入输出和不包含的边界" /></Form.Item><Form.Item label="配置选项" name="configurationOptions"><Input.TextArea rows={3} /></Form.Item><Form.Item label="扩展点" name="extensionPoints"><Input.TextArea rows={3} placeholder="一行一个受控扩展点" /></Form.Item><Form.Item label="负责人 ID" name="ownerUserId"><InputNumber min={1} style={{ width: '100%' }} /></Form.Item></Form></Drawer>
}

function currency(value?: number) { return `¥${Number(value ?? 0).toLocaleString('zh-CN')}` }
function maturityLevel(score: number) { return score >= 85 ? '规模化复用' : score >= 65 ? '可复制交付' : score >= 40 ? '基线建设中' : '起步阶段' }
