import {
  CheckCircleOutlined, EditOutlined, PlusOutlined, SearchOutlined, StopOutlined, TeamOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button, Card, Col, Drawer, Form, Input, Row, Segmented, Select, Space, Statistic, Table, Tag,
  Typography, message,
} from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useAuth } from '../../app/AuthProvider'
import { PageState } from '../../components/PageState'
import { customerApi } from './customerApi'
import type { Customer, CustomerStatus } from './types'

const statusMeta: Record<CustomerStatus, { label: string; color: string }> = {
  ACTIVE: { label: '启用', color: 'success' },
  INACTIVE: { label: '停用', color: 'default' },
}

export function CustomerPage() {
  const { me } = useAuth()
  const canWrite = me?.permissions.includes('customer:write') ?? false
  const [view, setView] = useState<'list' | 'card'>('list')
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState<CustomerStatus>()
  const [editing, setEditing] = useState<Customer | null | undefined>()
  const query = useQuery({ queryKey: ['customers'], queryFn: () => customerApi.list() })
  const all = query.data ?? []
  const data = useMemo(() => all.filter(customer => {
    const term = keyword.trim().toLowerCase()
    const searchable = `${customer.name}${customer.shortName ?? ''}${customer.contactName ?? ''}`.toLowerCase()
    return (!term || searchable.includes(term)) && (!status || customer.status === status)
  }), [all, keyword, status])
  const columns = [
    { title: '客户', key: 'customer', width: 240, render: (_: unknown, item: Customer) => <div className="customer-name-cell">
      <strong>{item.name}</strong><span>{item.shortName || '未设置简称'}</span>
    </div> },
    { title: '联系人', key: 'contact', width: 190, render: (_: unknown, item: Customer) => <div className="customer-contact-cell">
      <strong>{item.contactName || '未设置'}</strong><span>{item.phone || item.email || '暂无联系方式'}</span>
    </div> },
    { title: '状态', dataIndex: 'status', width: 90, render: (value: CustomerStatus) => <CustomerStatusTag status={value} /> },
    { title: '关联项目', dataIndex: 'projectCount', width: 100, render: (value: number) => `${value} 个` },
    { title: '最近更新', dataIndex: 'updatedAt', width: 120, render: formatDate },
    ...(canWrite ? [{ title: '', key: 'action', width: 90, render: (_: unknown, item: Customer) => <Button type="link" size="small"
      aria-label={`编辑${item.name}`} icon={<EditOutlined />} onClick={() => setEditing(item)}>编辑</Button> }] : []),
  ]

  return <div className="customer-page">
    <div className="page-heading compact">
      <div><Typography.Title level={2}>客户管理</Typography.Title>
        <Typography.Paragraph>统一维护客户基本信息，为项目创建和历史交付提供可靠的客户主数据。</Typography.Paragraph></div>
      {canWrite && <Button type="primary" aria-label="新建客户" icon={<PlusOutlined />} onClick={() => setEditing(null)}>新建客户</Button>}
    </div>
    <Row className="customer-kpis" gutter={12}>
      <Col xs={24} md={8}><Card><Statistic title="客户总数" value={all.length} prefix={<TeamOutlined />} /></Card></Col>
      <Col xs={24} md={8}><Card><Statistic title="启用客户" value={all.filter(item => item.status === 'ACTIVE').length} prefix={<CheckCircleOutlined />} /></Card></Col>
      <Col xs={24} md={8}><Card><Statistic title="停用客户" value={all.filter(item => item.status === 'INACTIVE').length} prefix={<StopOutlined />} /></Card></Col>
    </Row>
    <Card className="customer-filter" variant="borderless">
      <div className="customer-toolbar"><Space wrap>
        <Input allowClear prefix={<SearchOutlined />} placeholder="搜索客户、简称或联系人" value={keyword}
          onChange={event => setKeyword(event.target.value)} style={{ width: 260 }} />
        <Select aria-label="客户状态筛选" virtual={false} allowClear placeholder="全部状态" value={status} onChange={setStatus}
          style={{ width: 130 }} options={Object.entries(statusMeta).map(([value, meta]) => ({ value, label: meta.label }))} />
        <span className="result-count">共 {data.length} 个客户</span>
      </Space><Segmented value={view} onChange={value => setView(value as 'list' | 'card')} options={[
        { label: '列表', value: 'list' }, { label: '卡片', value: 'card' },
      ]} /></div>
    </Card>
    <PageState loading={query.isLoading} error={query.error} empty={!query.isLoading && data.length === 0} onRetry={() => void query.refetch()}>
      {view === 'list' ? <Table rowKey="id" className="customer-table" columns={columns} dataSource={data}
        pagination={{ pageSize: 12, hideOnSinglePage: true }} scroll={{ x: 780 }} /> :
        <Row gutter={[14, 14]}>{data.map(customer => <Col xs={24} lg={12} xxl={8} key={customer.id}>
          <CustomerCard customer={customer} canWrite={canWrite} onEdit={() => setEditing(customer)} />
        </Col>)}</Row>}
    </PageState>
    <CustomerEditor value={editing} onClose={() => setEditing(undefined)} />
  </div>
}

function CustomerCard({ customer, canWrite, onEdit }: { customer: Customer; canWrite: boolean; onEdit: () => void }) {
  return <Card data-testid={`customer-card-${customer.id}`} className="customer-card" hoverable>
    <div className="customer-card-head"><div><strong>{customer.name}</strong><span>{customer.shortName || '未设置简称'}</span></div>
      <CustomerStatusTag status={customer.status} /></div>
    <div className="customer-card-contact"><span>联系人</span><strong>{customer.contactName || '未设置'}</strong>
      <small>{customer.phone || customer.email || '暂无联系方式'}</small></div>
    <p className="customer-ellipsis" title={customer.address}>{customer.address || '暂无地址'}</p>
    <div className="customer-card-foot"><span>{customer.projectCount} 个关联项目</span><span>{formatDate(customer.updatedAt)}</span>
      {canWrite && <Button type="link" size="small" aria-label={`编辑${customer.name}`} onClick={onEdit}>编辑</Button>}</div>
  </Card>
}

function CustomerEditor({ value, onClose }: { value: Customer | null | undefined; onClose: () => void }) {
  const [form] = Form.useForm()
  const client = useQueryClient()
  useEffect(() => {
    if (value !== undefined) {
      form.resetFields()
      form.setFieldsValue(value ?? { status: 'ACTIVE' })
    }
  }, [form, value])
  const save = useMutation({
    mutationFn: (input: Record<string, unknown>) => customerApi.save(value?.id, { ...input, version: value?.version ?? 0 }),
    onSuccess: async () => {
      await Promise.all([
        client.invalidateQueries({ queryKey: ['customers'] }),
        client.invalidateQueries({ queryKey: ['active-customers'] }),
      ])
      message.success(value ? '客户已更新' : '客户已创建')
      onClose()
    },
    onError: (error: Error) => message.error(error.message),
  })
  return <Drawer title={value ? '编辑客户' : '新建客户'} open={value !== undefined} width={600} onClose={onClose}
    extra={<Button type="primary" aria-label="保存" loading={save.isPending} onClick={() => form.submit()}>保存</Button>}>
    <Form form={form} layout="vertical" onFinish={save.mutate}>
      <Form.Item label="客户名称" name="name" rules={[{ required: true, message: '请输入客户名称' }]}><Input maxLength={180} /></Form.Item>
      <Row gutter={12}><Col span={12}><Form.Item label="客户简称" name="shortName"><Input maxLength={100} /></Form.Item></Col>
        <Col span={12}><Form.Item label="联系人" name="contactName"><Input maxLength={100} /></Form.Item></Col></Row>
      <Row gutter={12}><Col span={12}><Form.Item label="联系电话" name="phone"><Input maxLength={40} /></Form.Item></Col>
        <Col span={12}><Form.Item label="邮箱" name="email" rules={[{ type: 'email', message: '请输入有效邮箱' }]}><Input maxLength={160} /></Form.Item></Col></Row>
      <Form.Item label="地址" name="address"><Input.TextArea rows={3} maxLength={500} /></Form.Item>
      <Form.Item label="状态" name="status"><Select virtual={false} options={Object.entries(statusMeta).map(([status, meta]) => ({ value: status, label: meta.label }))} /></Form.Item>
      <Form.Item label="备注" name="remark"><Input.TextArea rows={4} maxLength={500} showCount /></Form.Item>
    </Form>
  </Drawer>
}

function CustomerStatusTag({ status }: { status: CustomerStatus }) {
  const meta = statusMeta[status]
  return <Tag color={meta.color}>{meta.label}</Tag>
}

function formatDate(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-CN').format(date)
}
