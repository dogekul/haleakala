import { EditOutlined, EyeOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, Button, Card, Col, Drawer, Form, Input, InputNumber, Row, Segmented, Select, Space,
  Table, Tag, Typography, message,
} from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { PageState } from '../../components/PageState'
import { useAuth } from '../../app/AuthProvider'
import { productApi } from './productApi'
import type { Product, ProductStatus } from './types'

const statusMeta: Record<ProductStatus, { label: string; color: string }> = {
  PLANNING: { label: '规划中', color: 'processing' },
  ACTIVE: { label: '已启用', color: 'success' },
  SUNSET: { label: '停止演进', color: 'warning' },
  ARCHIVED: { label: '已归档', color: 'default' },
}

const allowedStatuses: Record<ProductStatus, ProductStatus[]> = {
  PLANNING: ['PLANNING', 'ACTIVE', 'ARCHIVED'],
  ACTIVE: ['ACTIVE', 'SUNSET'],
  SUNSET: ['SUNSET', 'ARCHIVED'],
  ARCHIVED: ['ARCHIVED'],
}

export function ProductListPage() {
  const { me } = useAuth()
  const canWrite = me?.permissions.includes('product:write') ?? false
  const [view, setView] = useState<'list' | 'card'>('list')
  const [keyword, setKeyword] = useState('')
  const [category, setCategory] = useState<string>()
  const [status, setStatus] = useState<ProductStatus>()
  const [ownerUserId, setOwnerUserId] = useState<number>()
  const [editing, setEditing] = useState<Product | null | undefined>()
  const query = useQuery({ queryKey: ['products'], queryFn: productApi.products })
  const categories = [...new Set((query.data ?? []).map(item => item.category).filter(Boolean))] as string[]
  const owners = [...new Set((query.data ?? []).map(item => item.ownerUserId).filter(Boolean))] as number[]
  const data = useMemo(() => (query.data ?? []).filter(item => {
    const term = keyword.trim().toLowerCase()
    return (!term || `${item.name}${item.code}`.toLowerCase().includes(term))
      && (!category || item.category === category)
      && (!status || item.status === status)
      && (!ownerUserId || item.ownerUserId === ownerUserId)
  }), [query.data, keyword, category, status, ownerUserId])

  const columns = [
    { title: '产品', key: 'product', width: 280, render: (_: unknown, item: Product) => <div className="product-list-name">
      <Link to={`/products/${item.id}`}>{item.name}</Link><span>{item.code} · {item.category || '未分类'}</span>
    </div> },
    { title: '模块 / 功能', key: 'structure', width: 150, render: (_: unknown, item: Product) => `${item.moduleCount} / ${item.featureCount}` },
    { title: '最新版本', dataIndex: 'latestVersionName', width: 130, render: (value?: string) => value || '尚未创建' },
    { title: '负责人', dataIndex: 'ownerUserId', width: 110, render: (value?: number) => value ? `#${value}` : '未指定' },
    { title: '状态', dataIndex: 'status', width: 110, render: (value: ProductStatus) => <ProductStatusTag status={value} /> },
    { title: '最近更新', dataIndex: 'updatedAt', width: 130, render: formatDate },
    { title: '', key: 'action', width: 80, render: (_: unknown, item: Product) => {
      const editable = canWrite && item.status !== 'ARCHIVED'
      return <Button type="link" size="small" aria-label={`${editable ? '编辑' : '查看'}${item.name}`}
        icon={editable ? <EditOutlined /> : <EyeOutlined />}
        onClick={() => setEditing(item)}>{editable ? '编辑' : '查看'}</Button>
    } },
  ]

  return <div className="product-list-page">
    <div className="page-heading compact">
      <div><Typography.Title level={2}>产品中心</Typography.Title>
        <Typography.Paragraph>沉淀产品、版本、模块与标准功能，连接交付需求和标准化演进。</Typography.Paragraph></div>
      {canWrite && <Button type="primary" aria-label="新建产品" icon={<PlusOutlined />}
        onClick={() => setEditing(null)}>新建产品</Button>}
    </div>
    <Card className="filter-surface" variant="borderless">
      <div className="product-list-toolbar">
        <Space wrap>
          <Input allowClear prefix={<SearchOutlined />} placeholder="搜索产品名称或编码" value={keyword}
            onChange={event => setKeyword(event.target.value)} style={{ width: 240 }} />
          <Select aria-label="分类筛选" virtual={false} allowClear placeholder="全部分类" value={category} onChange={setCategory} style={{ width: 140 }}
            options={categories.map(value => ({ value, label: value }))} />
          <Select aria-label="状态筛选" virtual={false} allowClear placeholder="全部状态" value={status} onChange={setStatus} style={{ width: 130 }}
            options={Object.entries(statusMeta).map(([value, meta]) => ({ value, label: meta.label }))} />
          <Select aria-label="负责人筛选" virtual={false} allowClear placeholder="全部负责人" value={ownerUserId} onChange={setOwnerUserId} style={{ width: 140 }}
            options={owners.map(value => ({ value, label: `负责人 #${value}` }))} />
          <span className="result-count">共 {data.length} 个产品</span>
        </Space>
        <Segmented value={view} onChange={value => setView(value as 'list' | 'card')} options={[
          { label: '列表', value: 'list' },
          { label: '卡片', value: 'card' },
        ]} />
      </div>
    </Card>
    <PageState loading={query.isLoading} error={query.error} empty={!query.isLoading && data.length === 0}
      onRetry={() => void query.refetch()}>
      {view === 'list' ? <Table rowKey="id" className="product-list-table" columns={columns} dataSource={data}
        pagination={{ pageSize: 12, hideOnSinglePage: true }} scroll={{ x: 1000 }} /> :
        <Row data-testid="product-card-grid" gutter={[16, 16]}>{data.map(product => <Col xs={24} lg={12} xxl={8} key={product.id}>
          <ProductCard product={product} canWrite={canWrite} onEdit={() => setEditing(product)} />
        </Col>)}</Row>}
    </PageState>
    <ProductEditor value={editing} canWrite={canWrite} onClose={() => setEditing(undefined)} />
  </div>
}

function ProductCard({ product, canWrite, onEdit }: { product: Product; canWrite: boolean; onEdit: () => void }) {
  const editable = canWrite && product.status !== 'ARCHIVED'
  return <Card data-testid={`product-card-${product.id}`} className="product-card" hoverable
    actions={[
      <Button key="edit" type="link" aria-label={`${editable ? '编辑' : '查看'}${product.name}`}
        onClick={onEdit}>{editable ? '编辑' : '查看'}</Button>,
    ]}>
    <div className="product-card-head"><div><span>{product.code}</span><Link to={`/products/${product.id}`}>{product.name}</Link></div>
      <ProductStatusTag status={product.status} /></div>
    <p>{product.description || '暂无产品说明'}</p>
    <div className="product-card-metrics"><span><strong>{product.moduleCount}</strong>模块</span>
      <span><strong>{product.featureCount}</strong>功能</span><span><strong>{product.latestVersionName || '—'}</strong>最新版本</span></div>
    <div className="product-card-foot"><span>{product.category || '未分类'}</span><span>{formatDate(product.updatedAt)}</span></div>
  </Card>
}

function ProductEditor({ value, canWrite, onClose }: { value: Product | null | undefined; canWrite: boolean; onClose: () => void }) {
  const [form] = Form.useForm()
  const client = useQueryClient()
  const readOnly = !canWrite || value?.status === 'ARCHIVED'
  useEffect(() => {
    if (value !== undefined) {
      form.resetFields()
      form.setFieldsValue(value ?? { status: 'PLANNING' })
    }
  }, [form, value])
  const save = useMutation({
    mutationFn: (input: Record<string, unknown>) => productApi.saveProduct(value?.id, { ...input, version: value?.version ?? 0 }),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ['products'] })
      message.success(value ? '产品已更新' : '产品已创建')
      onClose()
    },
    onError: (error: Error) => message.error(error.message),
  })
  const title = readOnly ? '查看产品' : value ? '编辑产品' : '新建产品'
  const statusOptions: ProductStatus[] = value ? allowedStatuses[value.status] : ['PLANNING']
  return <Drawer title={title} open={value !== undefined} width={520} onClose={onClose}
    extra={!readOnly && <Button type="primary" aria-label="保存" loading={save.isPending} onClick={() => form.submit()}>保存</Button>}>
    {readOnly && <Alert type="info" showIcon message={value?.status === 'ARCHIVED' ? '归档产品仅可查看' : '当前账号仅可查看产品'} />}
    <Form form={form} layout="vertical" disabled={readOnly} onFinish={canWrite ? save.mutate : undefined} className="product-editor-form">
      <Row gutter={12}><Col span={10}><Form.Item label="产品编码" name="code" rules={[{ required: true, message: '请输入产品编码' }]}>
        <Input disabled={Boolean(value)} /></Form.Item></Col><Col span={14}><Form.Item label="产品名称" name="name" rules={[{ required: true, message: '请输入产品名称' }]}><Input /></Form.Item></Col></Row>
      <Form.Item label="分类" name="category"><Input placeholder="例如：企业应用" /></Form.Item>
      <Form.Item label="负责人 ID" name="ownerUserId"><InputNumber min={1} style={{ width: '100%' }} placeholder="选填" /></Form.Item>
      <Form.Item label="产品说明" name="description"><Input.TextArea rows={4} maxLength={500} showCount /></Form.Item>
      <Form.Item label="状态" name="status"><Select virtual={false} disabled={!value || readOnly}
        options={statusOptions.map(status => ({ value: status, label: statusMeta[status].label }))} /></Form.Item>
    </Form>
  </Drawer>
}

export function ProductStatusTag({ status }: { status: ProductStatus }) {
  const meta = statusMeta[status]
  return <Tag color={meta.color}>{meta.label}</Tag>
}

function formatDate(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-CN').format(date)
}
