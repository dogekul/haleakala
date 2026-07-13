import {
  ApartmentOutlined, AppstoreOutlined, BarsOutlined, BulbOutlined, CheckOutlined,
  FilterOutlined, LinkOutlined, MergeCellsOutlined, PlusOutlined, RobotOutlined, WarningOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, Button, Card, Col, Drawer, Empty, Form, Input, List, Modal, Progress,
  Radio, Row, Segmented, Select, Space, Table, Tag, Typography, message,
} from 'antd'
import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ApiError } from '../../services/api'
import { projectApi } from '../project/projectApi'
import { requirementApi } from './requirementApi'
import { FeatureCoverageDrawer } from './FeatureCoverageDrawer'
import type { DuplicateCandidate, Funnel, Requirement } from './types'

const levels = {
  L0: { label: '标品满足', description: '现有标品或配置可覆盖', color: '#2ea869' },
  L1: { label: '需要二开', description: '通过扩展点或定制开发实现', color: '#f5a623' },
  L2: { label: '范围外', description: '不属于当前产品交付范围', color: '#f54a45' },
} as const

export function RequirementWorkshop() {
  const [searchParams] = useSearchParams()
  const focusedRequirementId = Number(searchParams.get('requirementId'))
  const [view, setView] = useState('list')
  const [createOpen, setCreateOpen] = useState(false)
  const [decision, setDecision] = useState<Requirement>()
  const [duplicateOf, setDuplicateOf] = useState<Requirement>()
  const [coverageOf, setCoverageOf] = useState<Requirement>()
  const handledRequirementId = useRef<number>()
  const requirements = useQuery({ queryKey: ['requirements'], queryFn: requirementApi.list })
  const funnel = useQuery({ queryKey: ['requirement-funnel'], queryFn: requirementApi.funnel })
  const focused = Number.isInteger(focusedRequirementId) && focusedRequirementId > 0
    ? requirements.data?.find(item => item.id === focusedRequirementId) : undefined
  const visibleRequirements = focused ? [focused] : requirements.data ?? []
  useEffect(() => {
    if (focused && handledRequirementId.current !== focusedRequirementId) {
      handledRequirementId.current = focusedRequirementId
      setView('list')
      setDecision(focused)
    }
  }, [focused, focusedRequirementId])
  return <div className="requirement-workshop">
    <div className="workshop-heading"><div><span className="eyebrow dark">REQUIREMENT WORKSHOP</span><Typography.Title level={2}>需求工坊</Typography.Title>
      <Typography.Paragraph>AI 提建议，交付工程师做最终判断；只有已确认结论进入漏斗。</Typography.Paragraph></div>
      <Button type="primary" size="large" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>采集需求</Button></div>
    <ClassificationFunnel value={funnel.data ?? { L0: 0, L1: 0, L2: 0 }} />
    <Card className="requirement-toolbar"><div><Space><Input.Search placeholder="搜索需求编号或标题" style={{ width: 280 }} /><Button icon={<FilterOutlined />}>筛选</Button></Space>
      <Segmented value={view} onChange={value => setView(String(value))} options={[{ value: 'list', label: '列表', icon: <BarsOutlined /> }, { value: 'board', label: '看板', icon: <AppstoreOutlined /> }]} /></div></Card>
    {view === 'list' ? <RequirementList values={visibleRequirements} onDecision={setDecision} onDuplicate={setDuplicateOf} onCoverage={setCoverageOf} />
      : <RequirementBoard values={visibleRequirements} onDecision={setDecision} />}
    <CollectionDrawer open={createOpen} onClose={() => setCreateOpen(false)} />
    <DecisionDrawer requirement={decision} onClose={() => setDecision(undefined)} />
    <DuplicateModal requirement={duplicateOf} onClose={() => setDuplicateOf(undefined)} />
    <FeatureCoverageDrawer requirement={coverageOf} onClose={() => setCoverageOf(undefined)} />
  </div>
}

function ClassificationFunnel({ value }: { value: Funnel }) {
  const total = value.L0 + value.L1 + value.L2
  return <Card className="classification-funnel" title={<Space><ApartmentOutlined />三层分类漏斗</Space>} extra={<span>基于 {total} 条人工确认结论</span>}>
    <div className="funnel-track">{(['L0', 'L1', 'L2'] as const).map((level, index) => <div key={level} className={`funnel-level level-${level.toLowerCase()}`} style={{ width: `${100 - index * 13}%` }}>
      <div><strong>{levels[level].label} {level}</strong><span>{levels[level].description}</span></div><b>{value[level]} 条</b>
    </div>)}</div>
    <Alert type="info" showIcon message="AI 建议不会改变漏斗统计；确认或有理由的改判才会生效。" />
  </Card>
}

function RequirementList({ values, onDecision, onDuplicate, onCoverage }: { values: Requirement[]; onDecision(value: Requirement): void; onDuplicate(value: Requirement): void; onCoverage(value: Requirement): void }) {
  return <div className="requirement-table"><Table rowKey="id" dataSource={values} columns={[
    { title: '需求', dataIndex: 'title', render: (_: string, row: Requirement) => <div className="requirement-title"><strong>{row.title}</strong><span>{row.code} · {row.projectCode}</span></div> },
    { title: '优先级', dataIndex: 'priority', width: 90, render: (value: string) => <Tag color={value === 'P0' ? 'red' : value === 'P1' ? 'orange' : 'blue'}>{value}</Tag> },
    { title: 'AI 建议', dataIndex: 'suggestedLevel', width: 130, render: (value: string, row: Requirement) => value ? <Space><Tag>{value}</Tag><span className="confidence">{Math.round((row.confidence ?? 0) * 100)}%</span></Space> : <span className="muted">待分析</span> },
    { title: '人工结论', dataIndex: 'confirmedLevel', width: 120, render: (value: string) => value ? levelTag(value) : <Tag>待确认</Tag> },
    { title: '状态', dataIndex: 'status', width: 110, render: (value: string) => <Tag color={value === 'CONFIRMED' ? 'success' : value === 'MERGED' ? 'default' : 'processing'}>{value}</Tag> },
    { title: '操作', width: 310, render: (_: unknown, row: Requirement) => <Space><Button size="small" type="link" icon={<RobotOutlined />} onClick={() => onDecision(row)}>分类决策</Button><Button size="small" type="link" icon={<LinkOutlined />} onClick={() => onCoverage(row)}>功能覆盖</Button><Button size="small" type="link" icon={<MergeCellsOutlined />} disabled={row.status === 'MERGED'} onClick={() => onDuplicate(row)}>查重合并</Button></Space> },
  ]} /></div>
}

function RequirementBoard({ values, onDecision }: { values: Requirement[]; onDecision(value: Requirement): void }) {
  const groups = ['DRAFT', 'SUBMITTED', 'CONFIRMED', 'MERGED']
  return <Row gutter={12} data-testid="requirement-board" className="requirement-board">{groups.map(status => <Col span={6} key={status}><div className="board-column"><div className="board-column-head"><strong>{status}</strong><span>{values.filter(item => item.status === status).length}</span></div>
    {values.filter(item => item.status === status).map(item => <Card key={item.id} size="small" className="requirement-card" onClick={() => onDecision(item)}><Tag>{item.priority}</Tag><h4>{item.title}</h4><p>{item.projectCode}</p>{item.confirmedLevel && levelTag(item.confirmedLevel)}</Card>)}
    {!values.some(item => item.status === status) && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无需求" />}</div></Col>)}</Row>
}

function CollectionDrawer({ open, onClose }: { open: boolean; onClose(): void }) {
  const [form] = Form.useForm()
  const client = useQueryClient()
  const projects = useQuery({ queryKey: ['projects-for-requirements'], queryFn: projectApi.list, enabled: open })
  const create = useMutation({ mutationFn: requirementApi.create, onSuccess: async value => { await client.invalidateQueries({ queryKey: ['requirements'] }); form.resetFields(); onClose(); if (value.validationWarning) message.warning(value.validationWarning); else message.success('需求已保存') } })
  return <Drawer title="需求采集单" open={open} onClose={onClose} width={560} extra={<Button type="primary" loading={create.isPending} onClick={() => form.submit()}>保存草稿</Button>}>
    <Form form={form} layout="vertical" initialValues={{ priority: 'P2', source: '客户访谈' }} onFinish={values => create.mutate(values)}>
      <Form.Item label="所属项目" name="projectId" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" loading={projects.isLoading} options={projects.data?.map(item => ({ value: item.id, label: `${item.code} · ${item.name}` }))} /></Form.Item>
      <Form.Item label="需求标题" name="title" rules={[{ required: true }]}><Input placeholder="用一句话描述业务目标" /></Form.Item>
      <Form.Item label="业务描述与验收条件" name="description" rules={[{ required: true }]}><Input.TextArea rows={7} placeholder="业务场景、当前问题、期望结果、验收条件……" showCount maxLength={3000} /></Form.Item>
      <Row gutter={12}><Col span={12}><Form.Item label="来源" name="source"><Select options={['客户访谈', '需求调研', '会议纪要', '工单反馈', '合同范围'].map(value => ({ value, label: value }))} /></Form.Item></Col><Col span={12}><Form.Item label="优先级" name="priority"><Radio.Group options={['P0', 'P1', 'P2', 'P3']} optionType="button" /></Form.Item></Col></Row>
      <Alert type="warning" showIcon message="描述过短仍可保存草稿，但系统会提示补充，避免 AI 和人工误判。" />
    </Form>
  </Drawer>
}

function DecisionDrawer({ requirement, onClose }: { requirement?: Requirement; onClose(): void }) {
  const [current, setCurrent] = useState<Requirement>()
  const [selectedLevel, setSelectedLevel] = useState<string>()
  const [aiUnavailable, setAiUnavailable] = useState(false)
  const [reason, setReason] = useState('')
  const client = useQueryClient()
  useEffect(() => { setCurrent(requirement); setSelectedLevel(undefined); setReason(''); setAiUnavailable(false) }, [requirement])
  const selected = selectedLevel ?? current?.confirmedLevel ?? current?.suggestedLevel
  const classify = useMutation({ mutationFn: () => requirementApi.classify(current!.id), onSuccess: async value => { setCurrent(value); setSelectedLevel(value.suggestedLevel); setAiUnavailable(false); await client.invalidateQueries({ queryKey: ['requirements'] }) }, onError: error => { if (error instanceof ApiError && error.code === 'AI_NOT_CONFIGURED') setAiUnavailable(true); else message.error((error as Error).message) } })
  const confirm = useMutation({ mutationFn: () => requirementApi.confirm(current!.id, selected!, reason), onSuccess: async () => { await Promise.all([client.invalidateQueries({ queryKey: ['requirements'] }), client.invalidateQueries({ queryKey: ['requirement-funnel'] })]); message.success('分类结论已确认'); onClose() }, onError: (error: Error) => message.error(error.message) })
  const override = Boolean(current?.suggestedLevel && selected && current.suggestedLevel !== selected)
  return <Drawer width={620} title="AI 分类决策树" open={Boolean(requirement)} onClose={onClose} extra={<Button type="primary" disabled={!selected || (override && !reason.trim())} loading={confirm.isPending} icon={<CheckOutlined />} onClick={() => confirm.mutate()}>确认结论</Button>}>
    {current && <><div className="decision-context"><Tag>{current.priority}</Tag><Typography.Title level={4}>{current.title}</Typography.Title><p>{current.description}</p><span>{current.projectCode} · {current.code}</span></div>
      <Button block icon={<RobotOutlined />} loading={classify.isPending} onClick={() => classify.mutate()}>请求 AI 生成分类建议</Button>
      {aiUnavailable && <Alert className="decision-alert" type="warning" showIcon message="AI 服务未配置" description="仍可由交付工程师手工判断并确认，不影响核心流程。" />}
      {current.suggestedLevel && <Card className="ai-suggestion" size="small"><div><BulbOutlined /><strong> AI 建议 {current.suggestedLevel}</strong><Progress percent={Math.round((current.confidence ?? 0) * 100)} size="small" /></div><p>{current.suggestionReason}</p></Card>}
      <Typography.Title level={5} className="decision-label">人工确认</Typography.Title><Radio.Group className="level-options" value={selected} onChange={event => setSelectedLevel(event.target.value)}>{(['L0', 'L1', 'L2'] as const).map(level => <Radio.Button value={level} key={level}><strong>{level} · {levels[level].label}</strong><span>{levels[level].description}</span></Radio.Button>)}</Radio.Group>
      {override && <Form.Item className="override-reason" label={<Space><WarningOutlined />改判原因（必填）</Space>} required><Input.TextArea rows={4} value={reason} onChange={event => setReason(event.target.value)} placeholder="说明标品边界、客户场景或范围依据" /></Form.Item>}
    </>}
  </Drawer>
}

function DuplicateModal({ requirement, onClose }: { requirement?: Requirement; onClose(): void }) {
  const [candidates, setCandidates] = useState<DuplicateCandidate[]>([])
  const client = useQueryClient()
  const scan = useMutation({ mutationFn: () => requirementApi.duplicates(requirement!.id), onSuccess: setCandidates })
  const merge = useMutation({ mutationFn: (target: number) => requirementApi.merge(requirement!.id, target), onSuccess: async () => { await client.invalidateQueries({ queryKey: ['requirements'] }); message.success('需求已合并，来源追踪已保留'); onClose() } })
  return <Modal width={680} title="需求去重与合并" open={Boolean(requirement)} onCancel={onClose} footer={<Button type="primary" loading={scan.isPending} onClick={() => scan.mutate()}>扫描相似需求</Button>}>
    {requirement && <Alert message={`当前需求：${requirement.title}`} description="使用规范化关键词重叠进行初筛，最终合并由人工决定。" showIcon />}
    <List className="duplicate-list" dataSource={candidates} locale={{ emptyText: '点击扫描相似需求' }} renderItem={item => <List.Item actions={[<Button key="merge" danger size="small" loading={merge.isPending} onClick={() => merge.mutate(item.id)}>合并到此需求</Button>]}><List.Item.Meta title={item.title} description={item.description} /><Progress type="circle" size={42} percent={Math.round(item.similarityScore * 100)} /></List.Item>} />
  </Modal>
}

function levelTag(value: string) { const level = levels[value as keyof typeof levels]; return <Tag color={level?.color}>{value} · {level?.label ?? value}</Tag> }
