import { EditOutlined, PlusOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Button, Card, Drawer, Form, Input, InputNumber, Select, Space, Table, Tag, Tree, message } from 'antd'
import type { DataNode } from 'antd/es/tree'
import { useEffect, useMemo, useState } from 'react'
import { PageState } from '../../components/PageState'
import { productApi } from './productApi'
import type { ProductFeature, ProductModule, StructureStatus } from './types'

const statusLabels: Record<StructureStatus, string> = { PLANNING: '规划中', ACTIVE: '已启用', DEPRECATED: '已废弃' }
const nextStatuses: Record<StructureStatus, StructureStatus[]> = {
  PLANNING: ['PLANNING', 'ACTIVE'], ACTIVE: ['ACTIVE', 'DEPRECATED'], DEPRECATED: ['DEPRECATED'],
}

export function buildModuleTree(values: ProductModule[], parentId?: number): DataNode[] {
  return values.filter(item => (item.parentId ?? undefined) === parentId)
    .sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name, 'zh-CN'))
    .map(item => ({ key: item.id, title: `${item.code} · ${item.name}`, children: buildModuleTree(values, item.id) }))
}

export function ProductStructureTab({ productId, readOnly }: { productId: number; readOnly: boolean }) {
  const modules = useQuery({ queryKey: ['product-modules', productId], queryFn: () => productApi.modules(productId) })
  const features = useQuery({ queryKey: ['product-features', productId], queryFn: () => productApi.features(productId) })
  const [selectedModuleId, setSelectedModuleId] = useState<number>()
  const [editingModule, setEditingModule] = useState<ProductModule | null | undefined>()
  const [editingFeature, setEditingFeature] = useState<ProductFeature | null | undefined>()
  const owners = useQuery({
    queryKey: ['product-owner-options'], queryFn: productApi.ownerOptions,
    enabled: !readOnly && (editingModule !== undefined || editingFeature !== undefined),
  })
  const ownerOptions = (owners.data ?? []).map(item => ({ value: item.id, label: item.displayName }))

  useEffect(() => {
    if (!selectedModuleId && modules.data?.length) setSelectedModuleId(modules.data[0].id)
  }, [modules.data, selectedModuleId])

  const filteredFeatures = (features.data ?? []).filter(item => item.moduleId === selectedModuleId)
  const selectedModule = modules.data?.find(item => item.id === selectedModuleId)
  return <PageState loading={modules.isLoading || features.isLoading} error={(modules.error || features.error) as Error | null}
    onRetry={() => void Promise.all([modules.refetch(), features.refetch()])}>
    <div className="product-structure-layout">
      <Card className="product-structure-tree" title="产品模块" extra={!readOnly && <Button size="small" icon={<PlusOutlined />}
        aria-label="新建模块" onClick={() => setEditingModule(null)}>新建模块</Button>}>
        <div className="product-module-tree-scroll" data-testid="product-module-tree-scroll">
          {(modules.data?.length ?? 0) > 0 ? <Tree defaultExpandAll blockNode selectedKeys={selectedModuleId ? [selectedModuleId] : []}
            treeData={buildModuleTree(modules.data ?? [])} onSelect={keys => keys[0] && setSelectedModuleId(Number(keys[0]))} />
            : <div className="product-empty-copy">尚未创建模块</div>}
        </div>
        {selectedModule && !readOnly && <Button className="product-structure-edit-module" type="link" size="small"
          icon={<EditOutlined />} onClick={() => setEditingModule(selectedModule)}>编辑当前模块</Button>}
      </Card>
      <Card className="product-structure-features" title={selectedModule ? `${selectedModule.name} · 标准功能` : '标准功能'}
        extra={!readOnly && selectedModuleId && <Button size="small" icon={<PlusOutlined />} aria-label="新建功能"
          onClick={() => setEditingFeature(null)}>新建功能</Button>}>
        <Table rowKey="id" size="small" dataSource={filteredFeatures} pagination={false} locale={{ emptyText: selectedModuleId ? '当前模块暂无功能' : '请选择模块' }}
          columns={[
            { title: '功能', key: 'feature', render: (_: unknown, item: ProductFeature) => <div className="product-feature-name"><strong>{item.name}</strong><span>{item.code}</span></div> },
            { title: '状态', dataIndex: 'status', width: 90, render: (status: StructureStatus) => <Tag>{statusLabels[status]}</Tag> },
            { title: '', width: 90, render: (_: unknown, item: ProductFeature) => <Button type="link" size="small"
              aria-label={`${readOnly ? '查看' : '编辑'}${item.name}`} onClick={() => setEditingFeature(item)}>{readOnly ? '查看' : '编辑'}</Button> },
          ]} />
      </Card>
    </div>
    {editingModule !== undefined && <ModuleEditor productId={productId} values={modules.data ?? []} value={editingModule} readOnly={readOnly}
      ownerOptions={ownerOptions} ownerOptionsLoading={owners.isLoading}
      onClose={() => setEditingModule(undefined)} />}
    {editingFeature !== undefined && <FeatureEditor productId={productId} modules={modules.data ?? []} defaultModuleId={selectedModuleId}
      value={editingFeature} readOnly={readOnly} ownerOptions={ownerOptions} ownerOptionsLoading={owners.isLoading}
      onClose={() => setEditingFeature(undefined)} />}
  </PageState>
}

function ModuleEditor({ productId, values, value, readOnly, ownerOptions, ownerOptionsLoading, onClose }: {
  productId: number; values: ProductModule[]; value: ProductModule | null | undefined; readOnly: boolean
  ownerOptions: OwnerOption[]; ownerOptionsLoading: boolean; onClose: () => void
}) {
  const [form] = Form.useForm()
  const client = useQueryClient()
  useEffect(() => {
    if (value !== undefined) {
      form.resetFields()
      form.setFieldsValue(value ?? { status: 'PLANNING', sortOrder: 0 })
    }
  }, [form, value])
  const save = useMutation({
    mutationFn: (input: Record<string, unknown>) => productApi.saveModule(productId, value?.id, { ...input, version: value?.version ?? 0 }),
    onSuccess: async () => {
      await Promise.all([
        client.invalidateQueries({ queryKey: ['product-modules', productId] }),
        client.invalidateQueries({ queryKey: ['product-coverage', productId] }),
        client.invalidateQueries({ queryKey: ['product', productId] }),
      ])
      message.success(value ? '模块已更新' : '模块已创建')
      onClose()
    },
    onError: (error: Error) => message.error(error.message),
  })
  const parentOptions = validParentModules(values, value ?? undefined).map(item => ({ value: item.id, label: `${item.code} · ${item.name}` }))
  const disabled = readOnly || value?.status === 'DEPRECATED'
  const selectableOwners = withCurrentOwner(ownerOptions, value)
  return <Drawer open title={readOnly ? '查看模块' : value ? '编辑模块' : '新建模块'} width={520} onClose={onClose}
    extra={!disabled && <Button type="primary" aria-label="保存模块" loading={save.isPending} onClick={() => form.submit()}>保存</Button>}>
    {disabled && <Alert type="info" showIcon message="当前模块仅可查看" />}
    <Form form={form} layout="vertical" disabled={disabled} onFinish={save.mutate}>
      <Form.Item label="父模块" name="parentId"><Select allowClear virtual={false} options={parentOptions} /></Form.Item>
      <Space align="start" className="product-editor-row">
        <Form.Item label="模块编码" name="code" rules={[{ required: true, message: '请输入模块编码' }]}><Input /></Form.Item>
        <Form.Item label="模块名称" name="name" rules={[{ required: true, message: '请输入模块名称' }]}><Input /></Form.Item>
      </Space>
      <Form.Item label="模块说明" name="description"><Input.TextArea rows={3} maxLength={500} showCount /></Form.Item>
      <Space align="start" className="product-editor-row">
        <Form.Item label="负责人" name="ownerUserId"><Select allowClear showSearch optionFilterProp="label" virtual={false}
          loading={ownerOptionsLoading} style={{ width: 220 }}
          notFoundContent="暂无产品负责人，请先在系统管理中配置产品负责人角色" options={selectableOwners} /></Form.Item>
        <Form.Item label="排序" name="sortOrder"><InputNumber min={0} /></Form.Item>
      </Space>
      <Form.Item label="状态" name="status"><Select virtual={false} disabled={!value || disabled}
        options={(value ? nextStatuses[value.status] : ['PLANNING'] as StructureStatus[]).map(status => ({ value: status, label: statusLabels[status] }))} /></Form.Item>
    </Form>
  </Drawer>
}

function FeatureEditor({ productId, modules, defaultModuleId, value, readOnly, ownerOptions, ownerOptionsLoading, onClose }: {
  productId: number; modules: ProductModule[]; defaultModuleId?: number; value: ProductFeature | null | undefined; readOnly: boolean
  ownerOptions: OwnerOption[]; ownerOptionsLoading: boolean; onClose: () => void
}) {
  const [form] = Form.useForm()
  const client = useQueryClient()
  useEffect(() => {
    if (value !== undefined) {
      form.resetFields()
      form.setFieldsValue(value ?? { moduleId: defaultModuleId, status: 'PLANNING' })
    }
  }, [defaultModuleId, form, value])
  const save = useMutation({
    mutationFn: (input: Record<string, unknown>) => productApi.saveFeature(productId, value?.id, { ...input, version: value?.version ?? 0 }),
    onSuccess: async () => {
      await Promise.all([
        client.invalidateQueries({ queryKey: ['product-features', productId] }),
        client.invalidateQueries({ queryKey: ['product-coverage', productId] }),
        client.invalidateQueries({ queryKey: ['product', productId] }),
      ])
      message.success(value ? '功能已更新' : '功能已创建')
      onClose()
    },
    onError: (error: Error) => message.error(error.message),
  })
  const disabled = readOnly || value?.status === 'DEPRECATED'
  const selectableOwners = withCurrentOwner(ownerOptions, value)
  return <Drawer open title={readOnly ? '查看功能' : value ? '编辑功能' : '新建功能'} width={520} onClose={onClose}
    extra={!disabled && <Button type="primary" aria-label="保存功能" loading={save.isPending} onClick={() => form.submit()}>保存</Button>}>
    {disabled && <Alert type="info" showIcon message="当前功能仅可查看" />}
    <Form form={form} layout="vertical" disabled={disabled} onFinish={save.mutate}>
      <Form.Item label="所属模块" name="moduleId" rules={[{ required: true, message: '请选择所属模块' }]}>
        <Select virtual={false} options={modules.map(item => ({ value: item.id, label: `${item.code} · ${item.name}` }))} />
      </Form.Item>
      <Space align="start" className="product-editor-row">
        <Form.Item label="功能编码" name="code" rules={[{ required: true, message: '请输入功能编码' }]}><Input /></Form.Item>
        <Form.Item label="功能名称" name="name" rules={[{ required: true, message: '请输入功能名称' }]}><Input /></Form.Item>
      </Space>
      <Form.Item label="功能说明" name="description"><Input.TextArea rows={3} maxLength={500} showCount /></Form.Item>
      <Form.Item label="负责人" name="ownerUserId"><Select allowClear showSearch optionFilterProp="label" virtual={false}
        loading={ownerOptionsLoading}
        notFoundContent="暂无产品负责人，请先在系统管理中配置产品负责人角色" options={selectableOwners} /></Form.Item>
      <Form.Item label="状态" name="status"><Select virtual={false} disabled={!value || disabled}
        options={(value ? nextStatuses[value.status] : ['PLANNING'] as StructureStatus[]).map(status => ({ value: status, label: statusLabels[status] }))} /></Form.Item>
    </Form>
  </Drawer>
}

type OwnerOption = { value: number; label: string }

function withCurrentOwner(
  options: OwnerOption[], current?: { ownerUserId?: number; ownerName?: string } | null,
) {
  if (!current?.ownerUserId || !current.ownerName || options.some(item => item.value === current.ownerUserId)) return options
  return [...options, { value: current.ownerUserId, label: current.ownerName }]
}

export function validParentModules(values: ProductModule[], current?: ProductModule) {
  const excluded = new Set<number>()
  if (current) {
    excluded.add(current.id)
    const visit = (id: number) => values.filter(item => item.parentId === id).forEach(item => { excluded.add(item.id); visit(item.id) })
    visit(current.id)
  }
  const subtreeHeight = current ? height(current.id, values) : 1
  return values.filter(item => !excluded.has(item.id) && depth(item.id, values) + subtreeHeight <= 3)
}

function depth(id: number, values: ProductModule[]) {
  let result = 1
  let current = values.find(item => item.id === id)
  while (current?.parentId) {
    result += 1
    current = values.find(item => item.id === current?.parentId)
  }
  return result
}

function height(id: number, values: ProductModule[]): number {
  const children = values.filter(item => item.parentId === id)
  return children.length ? 1 + Math.max(...children.map(item => height(item.id, values))) : 1
}
