import { EditOutlined, PlusOutlined, RightOutlined, SearchOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Card, Input, Select, Space, Table, Typography, message } from 'antd'
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../app/AuthProvider'
import { PageState } from '../../components/PageState'
import { crmApi } from './crmApi'
import { OperationEditor } from './OperationEditor'
import type { CustomerOperation, OperationStage, OperationStatus } from './types'

export const operationStages: { value: OperationStage; label: string }[] = [
  { value: 'MAINTENANCE', label: '回款/维保' }, { value: 'OPERATING', label: '持续运营' },
  { value: 'REPURCHASE', label: '复购' },
]

export function OperationBoardPage() {
  const { me } = useAuth()
  const canWrite = me?.permissions.includes('crm:write') ?? false
  const [keyword, setKeyword] = useState('')
  const [owner, setOwner] = useState<number>()
  const [customer, setCustomer] = useState<number>()
  const [stage, setStage] = useState<OperationStage>()
  const [status, setStatus] = useState<OperationStatus>()
  const [editing, setEditing] = useState<CustomerOperation | null>()
  const client = useQueryClient()
  const params = new URLSearchParams()
  if (keyword.trim()) params.set('keyword', keyword.trim())
  if (owner) params.set('ownerUserId', String(owner))
  if (customer) params.set('customerId', String(customer))
  if (stage) params.set('stage', stage)
  if (status) params.set('status', status)
  const queryString = params.toString() ? `?${params}` : ''
  const query = useQuery({ queryKey: ['operations', queryString], queryFn: () => crmApi.operations(queryString) })
  const data = query.data ?? []
  const owners = useMemo(() => { const values = new Map<number, string>(); (query.data ?? []).forEach(item => { if (item.ownerUserId) values.set(item.ownerUserId, item.ownerName ?? `用户 ${item.ownerUserId}`) }); return [...values].map(([value, label]) => ({ value, label })) }, [query.data])
  const customers = useMemo(() => { const values = new Map<number, string>(); (query.data ?? []).forEach(item => values.set(item.customerId, item.customerName)); return [...values].map(([value, label]) => ({ value, label })) }, [query.data])
  const advance = useMutation({ mutationFn: (item: CustomerOperation) => crmApi.advanceOperation(item.id, item.version),
    onSuccess: async () => { await client.invalidateQueries({ queryKey: ['operations'] }); message.success('运营阶段已推进') },
    onError: (error: Error) => message.error(error.message) })
  const closed = data.filter(item => item.status === 'CLOSED')
  return <div className="crm-page operation-page"><div className="page-heading compact"><div>
    <Typography.Title level={2}>客户运营</Typography.Title><Typography.Paragraph>从客户维护到持续经营和复购，保持赢单后的价值增长。</Typography.Paragraph>
  </div>{canWrite && <Button type="primary" aria-label="新建运营" icon={<PlusOutlined />} onClick={() => setEditing(null)}>新建运营</Button>}</div>
  <Card className="crm-filter operation-filter"><div className="crm-toolbar operation-toolbar"><Space className="operation-filter-fields" wrap size={8}>
    <Input allowClear prefix={<SearchOutlined />} placeholder="搜索运营或客户" value={keyword} onChange={event => setKeyword(event.target.value)} />
    <Select aria-label="运营负责人筛选" allowClear placeholder="全部负责人" virtual={false} value={owner} onChange={setOwner} options={owners} />
    <Select aria-label="运营客户筛选" allowClear placeholder="全部客户" virtual={false} value={customer} onChange={setCustomer} options={customers} />
    <Select aria-label="运营阶段筛选" allowClear placeholder="全部阶段" virtual={false} value={stage} onChange={setStage}
      options={[...operationStages, { value: 'CLOSED' as OperationStage, label: '已关闭' }]} />
    <Select aria-label="运营状态筛选" allowClear placeholder="全部状态" virtual={false} value={status} onChange={setStatus}
      options={[{ value: 'OPEN', label: '进行中' }, { value: 'CLOSED', label: '已关闭' }]} />
  </Space><span className="operation-summary" data-testid="operation-summary">开放 {data.filter(item => item.status === 'OPEN').length} · 已关闭 {closed.length}</span></div></Card>
  <PageState loading={query.isLoading} error={query.error} empty={!query.isLoading && data.length === 0} onRetry={() => void query.refetch()}>
    <div className="operation-board">{operationStages.map(stage => <section key={stage.value} data-testid={`operation-column-${stage.value}`} className="operation-column">
      <header><strong>{stage.label}</strong><span className="crm-board-count">{data.filter(item => item.status === 'OPEN' && item.stage === stage.value).length}</span></header>
      {data.filter(item => item.status === 'OPEN' && item.stage === stage.value).map(item => <Card size="small" key={item.id} className="operation-card crm-board-card">
        <Link title={item.title} to={`/customers/operations/${item.id}`}>{item.title}</Link>
        <p className="crm-board-card-meta" title={`${item.customerName} · ${item.ownerName ?? '未分配负责人'}`}>{item.customerName} · {item.ownerName ?? '未分配负责人'}</p>
        {canWrite && <Space className="crm-board-card-actions" wrap size={[4, 4]}><Button size="small" icon={<EditOutlined />} aria-label={`编辑${item.title}`} onClick={() => setEditing(item)}>编辑</Button>
          <Button size="small" type={item.stage === 'REPURCHASE' ? 'default' : 'primary'} danger={item.stage === 'REPURCHASE'} icon={<RightOutlined />} aria-label={`推进${item.title}`} onClick={() => advance.mutate(item)}>{item.stage === 'REPURCHASE' ? '关闭运营' : '推进阶段'}</Button></Space>}
      </Card>)}</section>)}</div>
    <Card className="closed-operations" title="已关闭运营记录"><Table rowKey="id" size="small" dataSource={closed} pagination={false} columns={[
      { title: '运营主题', key: 'title', render: (_: unknown, item: CustomerOperation) => <Link to={`/customers/operations/${item.id}`}>{item.title}</Link> },
      { title: '客户', dataIndex: 'customerName' }, { title: '负责人', dataIndex: 'ownerName' }, { title: '更新时间', dataIndex: 'updatedAt', render: formatDate },
    ]} /></Card>
  </PageState><OperationEditor value={editing ?? undefined} open={editing !== undefined} onClose={() => setEditing(undefined)} /></div>
}

export function operationStageLabel(stage: OperationStage) { return operationStages.find(item => item.value === stage)?.label ?? (stage === 'CLOSED' ? '已关闭' : stage) }
export function formatDate(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '—' }
