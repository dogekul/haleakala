import { AppstoreOutlined, PlusOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Card, Col, DatePicker, Form, Input, Modal, Row, Select, Space, Table, Tag, message } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useEffect, useState } from 'react'
import { adminApi } from './adminApi'
import type { Product, ProductVersion } from './types'
import { PageHeading } from './UsersTeamsPage'

export function ProductsPage() {
  const products = useQuery({ queryKey: ['admin-products'], queryFn: adminApi.products })
  const users = useQuery({ queryKey: ['admin-users'], queryFn: adminApi.users })
  const [selectedId, setSelectedId] = useState<number>()
  const [editing, setEditing] = useState<Product | null | undefined>()
  const [editingVersion, setEditingVersion] = useState<ProductVersion | null | undefined>()
  useEffect(() => {
    if (!selectedId && products.data?.length) setSelectedId(products.data[0].id)
  }, [products.data, selectedId])
  const versions = useQuery({ queryKey: ['admin-product-versions', selectedId], queryFn: () => adminApi.versions(selectedId!), enabled: Boolean(selectedId) })
  const selected = products.data?.find(item => item.id === selectedId)

  return <section>
    <PageHeading title="产品目录" description="维护可用于项目交付的产品及版本；停用后保留既有项目关联。" action={<Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing(null)}>新建产品</Button>} />
    <Row gutter={14}>
      <Col span={14}><Card className="admin-surface" title="产品" extra={`${products.data?.length ?? 0} 个`}><Table rowKey="id" size="middle" loading={products.isLoading} dataSource={products.data} pagination={false} rowClassName={item => item.id === selectedId ? 'selected-row' : ''} onRow={item => ({ onClick: () => setSelectedId(item.id) })} columns={[
        { title: '产品', key: 'product', render: (_, item: Product) => <div className="admin-main-cell"><strong>{item.name}</strong><span>{item.code} · {item.category || '未分类'}</span></div> },
        { title: '负责人', dataIndex: 'ownerUserId', render: (value: number | null) => users.data?.find(item => item.id === value)?.displayName ?? '—' },
        { title: '状态', dataIndex: 'status', width: 80, render: (value: string) => <Tag color={value === 'ACTIVE' ? 'success' : 'default'}>{value === 'ACTIVE' ? '启用' : '停用'}</Tag> },
        { title: '', width: 60, render: (_, item: Product) => <Button type="link" size="small" onClick={event => { event.stopPropagation(); setEditing(item) }}>编辑</Button> },
      ]} /></Card></Col>
      <Col span={10}><Card className="admin-surface product-versions" title={<Space><AppstoreOutlined />{selected ? `${selected.name} · 版本` : '产品版本'}</Space>} extra={<Button type="link" disabled={!selected} icon={<PlusOutlined />} onClick={() => setEditingVersion(null)}>新建版本</Button>}><Table rowKey="id" size="small" loading={versions.isLoading} dataSource={versions.data} pagination={false} columns={[
        { title: '版本', dataIndex: 'versionName' },
        { title: '发布日期', dataIndex: 'releaseDate', render: (value: string | null) => value || '待定' },
        { title: '状态', dataIndex: 'status', render: (value: string) => <Tag color={value === 'ACTIVE' ? 'success' : 'default'}>{value === 'ACTIVE' ? '启用' : '停用'}</Tag> },
        { title: '', width: 60, render: (_, item: ProductVersion) => <Button type="link" size="small" onClick={() => setEditingVersion(item)}>编辑</Button> },
      ]} /></Card></Col>
    </Row>
    <ProductEditor value={editing} users={users.data ?? []} onClose={() => setEditing(undefined)} />
    {selected && <VersionEditor product={selected} value={editingVersion} onClose={() => setEditingVersion(undefined)} />}
  </section>
}

function ProductEditor({ value, users, onClose }: { value: Product | null | undefined; users: { id: number; displayName: string }[]; onClose(): void }) {
  const [form] = Form.useForm()
  const client = useQueryClient()
  useEffect(() => { if (value !== undefined) form.setFieldsValue(value ?? { status: 'ACTIVE' }) }, [form, value])
  const save = useMutation({ mutationFn: (input: Record<string, unknown>) => adminApi.saveProduct(value?.id, input), onSuccess: async () => { await client.invalidateQueries({ queryKey: ['admin-products'] }); onClose(); form.resetFields(); message.success(value ? '产品已更新' : '产品已创建') }, onError: (error: Error) => message.error(error.message) })
  return <Modal title={value ? '编辑产品' : '新建产品'} open={value !== undefined} onCancel={onClose} okText="保存" cancelText="取消" confirmLoading={save.isPending} onOk={() => form.submit()} destroyOnHidden><Form form={form} layout="vertical" onFinish={save.mutate}>
    <Row gutter={12}><Col span={10}><Form.Item label="产品编码" name="code" rules={[{ required: true }]}><Input disabled={Boolean(value)} /></Form.Item></Col><Col span={14}><Form.Item label="产品名称" name="name" rules={[{ required: true }]}><Input /></Form.Item></Col></Row>
    <Form.Item label="分类" name="category"><Input placeholder="例如：企业应用" /></Form.Item><Form.Item label="负责人" name="ownerUserId"><Select allowClear options={users.map(item => ({ value: item.id, label: item.displayName }))} /></Form.Item>
    {value && <Form.Item label="状态" name="status"><Select options={[{ value: 'ACTIVE', label: '启用' }, { value: 'DISABLED', label: '停用' }]} /></Form.Item>}
  </Form></Modal>
}

function VersionEditor({ product, value, onClose }: { product: Product; value: ProductVersion | null | undefined; onClose(): void }) {
  const [form] = Form.useForm()
  const client = useQueryClient()
  useEffect(() => { if (value !== undefined) form.setFieldsValue(value ? { ...value, releaseDate: value.releaseDate ? dayjs(value.releaseDate) : null } : { status: 'ACTIVE' }) }, [form, value])
  const save = useMutation({ mutationFn: (input: { versionName: string; releaseDate?: Dayjs; status: string }) => adminApi.saveVersion(product.id, value?.id, { ...input, releaseDate: input.releaseDate?.format('YYYY-MM-DD') }), onSuccess: async () => { await client.invalidateQueries({ queryKey: ['admin-product-versions', product.id] }); onClose(); form.resetFields(); message.success(value ? '版本已更新' : '版本已创建') }, onError: (error: Error) => message.error(error.message) })
  return <Modal title={`${value ? '编辑' : '新建'}版本 · ${product.name}`} open={value !== undefined} onCancel={onClose} okText="保存" cancelText="取消" confirmLoading={save.isPending} onOk={() => form.submit()} destroyOnHidden><Form form={form} layout="vertical" onFinish={save.mutate}>
    <Form.Item label="版本名称" name="versionName" rules={[{ required: true }]}><Input disabled={Boolean(value)} placeholder="例如：V2.1" /></Form.Item><Form.Item label="发布日期" name="releaseDate"><DatePicker style={{ width: '100%' }} /></Form.Item>
    {value && <Form.Item label="状态" name="status"><Select options={[{ value: 'ACTIVE', label: '启用' }, { value: 'DISABLED', label: '停用' }]} /></Form.Item>}
  </Form></Modal>
}
