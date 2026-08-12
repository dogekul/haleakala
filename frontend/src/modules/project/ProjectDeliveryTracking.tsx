import { EditOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button, Card, Col, Drawer, Empty, Form, Input, InputNumber, Row, Select,
  Space, Statistic, Table, Tag, Typography, message,
} from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useAuth } from '../../app/AuthProvider'
import { PageState } from '../../components/PageState'
import { SearchSelect } from '../../components/SearchSelect'
import { projectApi } from './projectApi'
import type { DeliveryTrackingItem, Project } from './types'

const classifications = {
  CONFIGURATION: { label: '① 配置', color: 'blue' },
  INTEGRATION: { label: '② 对接', color: 'cyan' },
  ENHANCEMENT: { label: '③ 能力增强', color: 'purple' },
  NEW_FEATURE: { label: '④ 新功能', color: 'orange' },
} as const
const endLabels = { C: 'C端', B: 'B端', BACKEND: '后端' } as const
const statuses = {
  TODO: { label: '待开始', color: 'default' }, IN_PROGRESS: { label: '进行中', color: 'processing' },
  DONE: { label: '已完成', color: 'success' }, BLOCKED: { label: '受阻', color: 'error' },
  CANCELLED: { label: '已取消', color: 'default' },
} as const
const dependencies = {
  READY: { label: '就绪', color: 'success' }, PROCESSING: { label: '处理中', color: 'processing' },
  GAP: { label: '缺口', color: 'error' }, NA: { label: '不适用', color: 'default' },
} as const
const reusable = { SEED: '种子', GROWING: '成长', MATURE: '成熟', NA: '不可复用' } as const

export function ProjectDeliveryTracking({ project }: { project: Project }) {
  const { me } = useAuth()
  const canWrite = me?.permissions.includes('project:write') ?? false
  const query = useQuery({ queryKey: ['delivery-tracking', project.id], queryFn: () => projectApi.deliveryTracking(project.id) })
  const [keyword, setKeyword] = useState('')
  const [classification, setClassification] = useState<string>()
  const [deliveryEnd, setDeliveryEnd] = useState<string>()
  const [status, setStatus] = useState<string>()
  const [editing, setEditing] = useState<DeliveryTrackingItem | null | undefined>()
  const values = query.data ?? []
  const filtered = useMemo(() => values.filter(item =>
    (!keyword.trim() || `${item.itemCode}${item.originalRequest}${item.featurePoint}${item.ownerName ?? ''}`.toLowerCase().includes(keyword.trim().toLowerCase()))
    && (!classification || item.classification === classification)
    && (!deliveryEnd || item.deliveryEnd === deliveryEnd)
    && (!status || item.status === status)), [values, keyword, classification, deliveryEnd, status])
  const done = values.filter(item => item.status === 'DONE').length
  const warning = values.filter(item => item.status === 'BLOCKED' || item.dependencyStatus === 'GAP').length
  const estimated = values.reduce((sum, item) => sum + Number(item.estimatedDays || 0), 0)
  const columns = [
    { title: '执行项', key: 'request', render: (_: unknown, item: DeliveryTrackingItem) => <div className="tracking-main-cell"><span>{item.itemCode}</span><strong title={item.originalRequest}>{item.originalRequest}</strong><small>{item.featurePoint}</small></div> },
    { title: '分类 / 端', key: 'classification', width: 150, render: (_: unknown, item: DeliveryTrackingItem) => <Space direction="vertical" size={4}><Tag color={classifications[item.classification].color}>{classifications[item.classification].label}</Tag><span className="muted">{endLabels[item.deliveryEnd]}</span></Space> },
    { title: '工作量', key: 'effort', width: 150, render: (_: unknown, item: DeliveryTrackingItem) => <div className="tracking-effort"><strong>{item.complexity}</strong><span>预估 {item.estimatedDays} 人天</span>{item.actualDays != null && <span>实际 {item.actualDays} 人天</span>}{item.deviationPercent != null && <Tag color={Math.abs(item.deviationPercent) > 30 ? 'red' : 'default'}>偏差 {item.deviationPercent}%</Tag>}</div> },
    { title: '标品依赖', key: 'dependency', width: 190, render: (_: unknown, item: DeliveryTrackingItem) => <div className="tracking-dependency"><Tag color={dependencies[item.dependencyStatus].color}>{dependencies[item.dependencyStatus].label}</Tag><span title={item.productDependency}>{item.productDependency || '无标品依赖'}</span>{item.dependencyNote && <small>{item.dependencyNote}</small>}</div> },
    { title: '状态 / 负责人', key: 'status', width: 130, render: (_: unknown, item: DeliveryTrackingItem) => <Space direction="vertical" size={4}><Tag color={statuses[item.status].color}>{statuses[item.status].label}</Tag><span>{item.ownerName || '待分配'}</span></Space> },
    ...(canWrite ? [{ title: '', key: 'action', width: 78, render: (_: unknown, item: DeliveryTrackingItem) => <Button type="link" icon={<EditOutlined />} onClick={() => setEditing(item)}>编辑</Button> }] : []),
  ]

  return <div className="project-tracking">
    <Row gutter={12} className="tracking-stats">
      <Col xs={12} lg={6}><Card><Statistic title="执行项" value={values.length} suffix="项" /></Card></Col>
      <Col xs={12} lg={6}><Card><Statistic title="完成率" value={values.length ? Math.round(done / values.length * 100) : 0} suffix="%" /></Card></Col>
      <Col xs={12} lg={6}><Card><Statistic title="受阻 / 缺口" value={warning} suffix="项" valueStyle={warning ? { color: '#d54941' } : undefined} /></Card></Col>
      <Col xs={12} lg={6}><Card><Statistic title="总预估" value={estimated} precision={1} suffix="人天" /></Card></Col>
    </Row>
    <Card className="tracking-toolbar"><div>
      <Space wrap>
        <Input allowClear prefix={<SearchOutlined />} placeholder="搜索需求、功能点或负责人" value={keyword} onChange={event => setKeyword(event.target.value)} />
        <Select allowClear placeholder="全部分类" value={classification} onChange={setClassification} options={Object.entries(classifications).map(([value, meta]) => ({ value, label: meta.label }))} />
        <Select allowClear placeholder="全部端" value={deliveryEnd} onChange={setDeliveryEnd} options={Object.entries(endLabels).map(([value, label]) => ({ value, label }))} />
        <Select allowClear placeholder="全部状态" value={status} onChange={setStatus} options={Object.entries(statuses).map(([value, meta]) => ({ value, label: meta.label }))} />
      </Space>
      {canWrite && <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing(null)}>新增执行项</Button>}
    </div></Card>
    <PageState loading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
      {filtered.length ? <Card className="tracking-table"><Table rowKey="id" columns={columns} dataSource={filtered} pagination={{ pageSize: 12 }} scroll={{ x: 950 }} /></Card>
        : <Card><Empty description={values.length ? '没有匹配的执行项' : '暂无交付执行项，收到甲方需求后从这里开始跟踪'} /></Card>}
    </PageState>
    <TrackingEditor project={project} value={editing} onClose={() => setEditing(undefined)} />
  </div>
}

function TrackingEditor({ project, value, onClose }: { project: Project; value: DeliveryTrackingItem | null | undefined; onClose(): void }) {
  const [form] = Form.useForm()
  const client = useQueryClient()
  useEffect(() => {
    if (value === undefined) return
    form.resetFields()
    form.setFieldsValue(value ?? { classification: 'CONFIGURATION', deliveryEnd: 'C', complexity: 'S', dependencyStatus: 'NA', estimatedDays: 0, reusableLevel: 'NA', status: 'TODO' })
  }, [form, value])
  const save = useMutation({
    mutationFn: (input: Record<string, unknown>) => projectApi.saveDeliveryTracking(project.id, value?.id, { ...input, version: value?.version ?? 0 }),
    onSuccess: async () => { await client.invalidateQueries({ queryKey: ['delivery-tracking', project.id] }); message.success(value ? '执行项已更新' : '执行项已创建'); onClose() },
    onError: (error: Error) => message.error(error.message),
  })
  const memberOptions = project.members.map(item => ({ value: Number(item.userId), label: String(item.displayName) }))
  return <Drawer title={value ? `编辑 ${value.itemCode}` : '新增交付执行项'} width={720} open={value !== undefined} onClose={onClose}
    extra={<Button type="primary" loading={save.isPending} onClick={() => form.submit()}>保存</Button>}>
    <Typography.Paragraph type="secondary">保留客户原始表述，再补充交付分类、标品依赖和工作量；周会汇总会自动计算。</Typography.Paragraph>
    <Form form={form} layout="vertical" onFinish={save.mutate}>
      <Form.Item label="甲方需求原始表述" name="originalRequest" rules={[{ required: true, whitespace: true }]}><Input.TextArea rows={3} maxLength={1000} showCount /></Form.Item>
      <Row gutter={12}><Col xs={24} md={8}><Form.Item label="四分类" name="classification" rules={[{ required: true }]}><Select options={Object.entries(classifications).map(([value, meta]) => ({ value, label: meta.label }))} /></Form.Item></Col>
        <Col xs={24} md={8}><Form.Item label="端" name="deliveryEnd" rules={[{ required: true }]}><Select options={Object.entries(endLabels).map(([value, label]) => ({ value, label }))} /></Form.Item></Col>
        <Col xs={24} md={8}><Form.Item label="复杂度" name="complexity" rules={[{ required: true }]}><Select options={[{ value: 'S', label: 'S（≤1人天）' }, { value: 'M', label: 'M（1~3人天）' }, { value: 'L', label: 'L（3~10人天）' }, { value: 'XL', label: 'XL（>10人天）' }]} /></Form.Item></Col></Row>
      <Form.Item label="功能点" name="featurePoint" rules={[{ required: true, whitespace: true }]}><Input maxLength={240} /></Form.Item>
      <Row gutter={12}><Col xs={24} md={12}><Form.Item label="标品依赖项" name="productDependency"><Input.TextArea rows={2} placeholder="需要标品提供的机制、SPI 或规范" /></Form.Item></Col>
        <Col xs={24} md={12}><Form.Item label="依赖跟踪说明" name="dependencyNote"><Input.TextArea rows={2} placeholder="SLA 截止日或缺口工单号" /></Form.Item></Col></Row>
      <Row gutter={12}><Col xs={24} md={8}><Form.Item label="标品依赖状态" name="dependencyStatus"><Select options={Object.entries(dependencies).map(([value, meta]) => ({ value, label: meta.label }))} /></Form.Item></Col>
        <Col xs={24} md={16}><Form.Item label="扩展点名称" name="extensionPoint"><Input placeholder="类二、类三填写，如 strategy-node-plugin" /></Form.Item></Col></Row>
      <Row gutter={12}><Col xs={24} md={8}><Form.Item label="预估人天" name="estimatedDays" rules={[{ required: true }]}><InputNumber min={0} precision={2} style={{ width: '100%' }} /></Form.Item></Col>
        <Col xs={24} md={8}><Form.Item label="实际人天" name="actualDays"><InputNumber min={0} precision={2} style={{ width: '100%' }} /></Form.Item></Col>
        <Col xs={24} md={8}><Form.Item label="复用等级" name="reusableLevel"><Select options={Object.entries(reusable).map(([value, label]) => ({ value, label }))} /></Form.Item></Col></Row>
      <Row gutter={12}><Col xs={24} md={12}><Form.Item label="状态" name="status"><Select options={Object.entries(statuses).map(([value, meta]) => ({ value, label: meta.label }))} /></Form.Item></Col>
        <Col xs={24} md={12}><Form.Item label="负责人" name="ownerUserId"><SearchSelect placeholder="从项目成员中选择" options={memberOptions} /></Form.Item></Col></Row>
      <Form.Item label="备注 / 风险" name="notes"><Input.TextArea rows={3} maxLength={1000} showCount /></Form.Item>
    </Form>
  </Drawer>
}
