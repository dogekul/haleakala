import { EditOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Button, Card, Col, Input, Row, Segmented, Select, Space, Statistic, Table, Tag, Typography } from 'antd'
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../app/AuthProvider'
import { PageState } from '../../components/PageState'
import { crmApi } from './crmApi'
import { OpportunityEditor } from './OpportunityEditor'
import type { Opportunity, OpportunityStage, OpportunityStatus } from './types'

export const opportunityStages: { value: OpportunityStage; label: string }[] = [
  { value: 'LEAD', label: '线索' }, { value: 'OPPORTUNITY', label: '立项评估' },
  { value: 'POC', label: 'POC 验证' }, { value: 'BIDDING', label: '投标' }, { value: 'CONTRACT', label: '合同' },
]
export const statusLabels: Record<OpportunityStatus, string> = { OPEN: '进行中', WON: '赢单', LOST: '丢单' }

export function OpportunityOverviewPage() {
  const { me } = useAuth()
  const canWrite = me?.permissions.includes('crm:write') ?? false
  const [view, setView] = useState<'list' | 'card'>('list')
  const [keyword, setKeyword] = useState('')
  const [customerId, setCustomerId] = useState<number>()
  const [productId, setProductId] = useState<number>()
  const [commercialOwnerId, setCommercialOwnerId] = useState<number>()
  const [solutionOwnerId, setSolutionOwnerId] = useState<number>()
  const [projectManagerId, setProjectManagerId] = useState<number>()
  const [operationOwnerId, setOperationOwnerId] = useState<number>()
  const [stage, setStage] = useState<OpportunityStage>()
  const [status, setStatus] = useState<OpportunityStatus>()
  const [editing, setEditing] = useState<Opportunity | null>()
  const params = new URLSearchParams()
  if (keyword.trim()) params.set('keyword', keyword.trim())
  if (customerId) params.set('customerId', String(customerId))
  if (productId) params.set('productId', String(productId))
  if (commercialOwnerId) params.set('commercialOwnerUserId', String(commercialOwnerId))
  if (solutionOwnerId) params.set('solutionOwnerUserId', String(solutionOwnerId))
  if (projectManagerId) params.set('projectManagerUserId', String(projectManagerId))
  if (operationOwnerId) params.set('operationOwnerUserId', String(operationOwnerId))
  if (stage) params.set('stage', stage)
  if (status) params.set('status', status)
  const queryString = params.toString() ? `?${params}` : ''
  const query = useQuery({ queryKey: ['opportunities', queryString], queryFn: () => crmApi.opportunities(queryString) })
  const data = query.data ?? []
  const terminal = data.filter(item => item.status !== 'OPEN')
  const won = data.filter(item => item.status === 'WON').length
  const lost = data.filter(item => item.status === 'LOST').length
  const dimensions = useMemo(() => ({
    customers: unique(data, 'customerId', 'customerName'),
    products: unique(data.filter(item => item.productId), 'productId', 'productName'),
    commercialOwners: unique(data.filter(item => item.commercialOwnerUserId), 'commercialOwnerUserId', 'commercialOwnerName'),
    solutionOwners: unique(data.filter(item => item.solutionOwnerUserId), 'solutionOwnerUserId', 'solutionOwnerName'),
    projectManagers: unique(data.filter(item => item.projectManagerUserId), 'projectManagerUserId', 'projectManagerName'),
    operationOwners: unique(data.filter(item => item.operationOwnerUserId), 'operationOwnerUserId', 'operationOwnerName'),
  }), [data])
  const columns = [
    { title: '商机', key: 'title', render: (_: unknown, item: Opportunity) => <div className="crm-name-cell"><Link to={`/customers/opportunities/${item.id}`}>{item.title}</Link><span>{item.customerName}</span></div> },
    { title: '阶段', dataIndex: 'stage', render: (value: OpportunityStage) => stageLabel(value) },
    { title: '金额', dataIndex: 'amount', render: currency },
    { title: '负责人', dataIndex: 'commercialOwnerName', render: (value?: string) => value ?? '未分配' },
    { title: '状态', dataIndex: 'status', render: (value: OpportunityStatus) => <Tag color={value === 'WON' ? 'success' : value === 'LOST' ? 'default' : 'processing'}>{statusLabels[value]}</Tag> },
    { title: '阶段停留', key: 'age', render: (_: unknown, item: Opportunity) => <StageAge item={item} /> },
    ...(canWrite ? [{ title: '', key: 'action', render: (_: unknown, item: Opportunity) => <Button type="link" icon={<EditOutlined />} aria-label={`编辑${item.title}`} onClick={() => setEditing(item)}>编辑</Button> }] : []),
  ]

  return <div className="crm-page opportunity-overview">
    <div className="page-heading compact"><div><Typography.Title level={2}>商机总览</Typography.Title>
      <Typography.Paragraph>从线索到合同统一观察商机质量、金额与阶段停留。</Typography.Paragraph></div>
      {canWrite && <Button type="primary" aria-label="新建商机" icon={<PlusOutlined />} onClick={() => setEditing(null)}>新建商机</Button>}
    </div>
    <Row gutter={12} className="crm-kpis">
      <Col xs={12} lg={5}><Card data-testid="opportunity-total"><Statistic title="商机总数" value={data.length} /></Card></Col>
      <Col xs={12} lg={5}><Card data-testid="open-amount"><Statistic title="开放金额" value={data.filter(i => i.status === 'OPEN').reduce((sum, i) => sum + Number(i.amount), 0)} /></Card></Col>
      <Col xs={8} lg={4}><Card data-testid="won-count"><Statistic title="赢单" value={won} /></Card></Col>
      <Col xs={8} lg={4}><Card data-testid="lost-count"><Statistic title="丢单" value={lost} /></Card></Col>
      <Col xs={8} lg={6}><Card data-testid="win-rate"><Statistic title="赢单率" value={terminal.length ? Math.round(won / terminal.length * 100) : 0} suffix="%" /></Card></Col>
    </Row>
    <Card className="opportunity-funnel" title="五阶段漏斗">
      <div className="funnel-inline">{opportunityStages.map((item, index) => <div key={item.value} data-testid={`funnel-${item.value}`} style={{ width: `${100 - index * 11}%` }}>
        <span>{item.label}</span><strong>{data.filter(opportunity => opportunity.stage === item.value).length}</strong>
      </div>)}</div>
    </Card>
    <Card className="crm-filter" variant="borderless"><div className="crm-toolbar"><Space wrap>
      <Input allowClear prefix={<SearchOutlined />} placeholder="搜索商机或客户" value={keyword} onChange={event => setKeyword(event.target.value)} />
      <FilterSelect label="客户筛选" value={customerId} onChange={setCustomerId} options={dimensions.customers} />
      <FilterSelect label="产品筛选" value={productId} onChange={setProductId} options={dimensions.products} />
      <FilterSelect label="商务负责人筛选" value={commercialOwnerId} onChange={setCommercialOwnerId} options={dimensions.commercialOwners} />
      <FilterSelect label="方案负责人筛选" value={solutionOwnerId} onChange={setSolutionOwnerId} options={dimensions.solutionOwners} />
      <FilterSelect label="项目经理筛选" value={projectManagerId} onChange={setProjectManagerId} options={dimensions.projectManagers} />
      <FilterSelect label="运营负责人筛选" value={operationOwnerId} onChange={setOperationOwnerId} options={dimensions.operationOwners} />
      <Select aria-label="阶段筛选" allowClear placeholder="全部阶段" virtual={false} value={stage} onChange={setStage} options={opportunityStages} />
      <Select aria-label="状态筛选" allowClear placeholder="全部状态" virtual={false} value={status} onChange={setStatus}
        options={Object.entries(statusLabels).map(([value, label]) => ({ value, label }))} />
    </Space><Segmented value={view} onChange={value => setView(value as 'list' | 'card')} options={['列表', '卡片']} /></div></Card>
    <PageState loading={query.isLoading} error={query.error} empty={!query.isLoading && data.length === 0} onRetry={() => void query.refetch()}>
      {view === 'list' ? <Table rowKey="id" columns={columns} dataSource={data} pagination={{ pageSize: 12 }} scroll={{ x: 1000 }} />
        : <Row gutter={[12, 12]}>{data.map(item => <Col xs={24} lg={12} xxl={8} key={item.id}><Card className="crm-ellipsis-card" data-testid={`opportunity-card-${item.id}`}>
          <div className="crm-card-head"><Link to={`/customers/opportunities/${item.id}`}>{item.title}</Link><Tag>{stageLabel(item.stage)}</Tag></div>
          <p title={item.title}>{item.customerName} · {item.productName ?? '未关联产品'}</p>
          <div className="crm-card-foot"><strong>{currency(item.amount)}</strong><StageAge item={item} /></div>
        </Card></Col>)}</Row>}
    </PageState>
    <OpportunityEditor value={editing ?? undefined} open={editing !== undefined} onClose={() => setEditing(undefined)} />
  </div>
}

function StageAge({ item }: { item: Opportunity }) {
  const days = Math.max(0, Math.floor((Date.now() - new Date(item.stageEnteredAt).getTime()) / 86400000))
  return days > 14 && item.status === 'OPEN' ? <Tag color="warning">阶段停留 {days} 天</Tag> : <span>{days} 天</span>
}

function FilterSelect({ label, value, onChange, options }: { label: string; value?: number; onChange: (value?: number) => void; options: { value: number; label: string }[] }) {
  return <Select aria-label={label} allowClear placeholder={label.replace('筛选', '')} virtual={false} value={value} onChange={onChange} options={options} />
}

function unique(items: Opportunity[], idKey: keyof Opportunity, nameKey: keyof Opportunity) {
  const values = new Map<number, string>()
  items.forEach(item => { const id = item[idKey]; const name = item[nameKey]; if (typeof id === 'number' && typeof name === 'string') values.set(id, name) })
  return [...values].map(([value, label]) => ({ value, label }))
}

export function stageLabel(stage: OpportunityStage) { return opportunityStages.find(item => item.value === stage)?.label ?? stage }
export function currency(value: number) { return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY', maximumFractionDigits: 0 }).format(value) }
