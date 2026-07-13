import { EditOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Button, Card, Drawer, Form, Input, Select, Space, Table, Tag, message } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { PageState } from '../../components/PageState'
import { productApi } from './productApi'
import type { Availability, ProductFeature, ProductVersion, VersionManifest, VersionStatus } from './types'

const versionLabels: Record<VersionStatus, string> = { PLANNING: '规划中', RELEASED: '已发布', SUNSET: '停止维护', ARCHIVED: '已归档' }
const nextStatuses: Record<VersionStatus, VersionStatus[]> = {
  PLANNING: ['PLANNING', 'RELEASED', 'ARCHIVED'], RELEASED: ['RELEASED', 'SUNSET'],
  SUNSET: ['SUNSET', 'ARCHIVED'], ARCHIVED: ['ARCHIVED'],
}
const availabilityLabels: Record<Availability, string> = { INCLUDED: '已纳入', PLANNED: '计划中', REMOVED: '已移除' }

export function ProductVersionsTab({ productId, readOnly }: { productId: number; readOnly: boolean }) {
  const client = useQueryClient()
  const versions = useQuery({ queryKey: ['product-versions', productId], queryFn: () => productApi.versions(productId) })
  const features = useQuery({ queryKey: ['product-features', productId], queryFn: () => productApi.features(productId) })
  const [selectedId, setSelectedId] = useState<number>()
  const [editing, setEditing] = useState<ProductVersion | null | undefined>()
  const [keyword, setKeyword] = useState('')
  const [rows, setRows] = useState<Record<number, Availability>>({})

  useEffect(() => {
    if (!selectedId && versions.data?.length) setSelectedId(versions.data[0].id)
  }, [selectedId, versions.data])
  const selected = versions.data?.find(item => item.id === selectedId)
  const manifest = useQuery({
    queryKey: ['product-manifest', productId, selectedId],
    queryFn: () => productApi.manifest(productId, selectedId!),
    enabled: Boolean(selectedId),
  })
  useEffect(() => {
    if (!manifest.data || !features.data) return
    const existing = new Map(manifest.data.entries.map(item => [item.featureId, item.availability]))
    setRows(Object.fromEntries(features.data.map(item => [item.id, existing.get(item.id) ?? 'REMOVED'])))
  }, [features.data, manifest.data])

  const visibleFeatures = useMemo(() => {
    const term = keyword.trim().toLowerCase()
    return (features.data ?? []).filter(item => !term || `${item.code}${item.name}`.toLowerCase().includes(term))
  }, [features.data, keyword])
  const saveManifest = useMutation({
    mutationFn: () => productApi.replaceManifest(productId, selected!.id, {
      version: manifest.data!.version,
      entries: (features.data ?? []).map(item => ({ featureId: item.id, availability: rows[item.id] ?? 'REMOVED' })),
    }),
    onSuccess: async data => {
      client.setQueryData(['product-manifest', productId, selectedId], data)
      await client.invalidateQueries({ queryKey: ['product-versions', productId] })
      message.success('版本功能清单已保存')
    },
    onError: (error: Error) => message.error(error.message),
  })
  return <PageState loading={versions.isLoading || features.isLoading} error={(versions.error || features.error) as Error | null}
    onRetry={() => void Promise.all([versions.refetch(), features.refetch()])}>
    <div className="product-version-layout">
      <Card className="product-version-list" title="产品版本" extra={!readOnly && <Button size="small" icon={<PlusOutlined />}
        aria-label="新建版本" onClick={() => setEditing(null)}>新建版本</Button>}>
        <Table rowKey="id" size="small" pagination={false} dataSource={versions.data ?? []}
          onRow={item => ({ onClick: () => setSelectedId(item.id) })}
          rowClassName={item => item.id === selectedId ? 'is-selected' : ''}
          columns={[
            { title: '版本', dataIndex: 'versionName', render: (name: string) => <strong>{name}</strong> },
            { title: '状态', dataIndex: 'status', width: 88, render: (status: VersionStatus) => <Tag>{versionLabels[status]}</Tag> },
            { title: '', width: 52, render: (_: unknown, item: ProductVersion) => <Button type="text" size="small" icon={<EditOutlined />}
              aria-label={`编辑版本 ${item.versionName}`} onClick={event => { event.stopPropagation(); setSelectedId(item.id); setEditing(item) }} /> },
          ]} />
      </Card>
      <Card className="version-manifest-card" title={selected ? `${selected.versionName} · 功能清单` : '功能清单'}
        extra={<Button type="primary" size="small" aria-label="保存功能清单" disabled={readOnly || !selected || !manifest.data}
          loading={saveManifest.isPending} onClick={() => saveManifest.mutate()}>保存功能清单</Button>}>
        {!selected ? <div className="product-empty-copy">请选择版本</div> : <PageState loading={manifest.isLoading} error={manifest.error}
          onRetry={() => void manifest.refetch()}>
          <Input className="version-manifest-search" allowClear prefix={<SearchOutlined />} placeholder="搜索功能" value={keyword}
            onChange={event => setKeyword(event.target.value)} />
          <div className="version-manifest-list">
            {visibleFeatures.map(feature => <ManifestRow key={feature.id} feature={feature} value={rows[feature.id] ?? 'REMOVED'}
              disabled={readOnly} onChange={value => setRows(current => ({ ...current, [feature.id]: value }))} />)}
          </div>
        </PageState>}
      </Card>
    </div>
    {editing !== undefined && <VersionEditor productId={productId} value={editing} readOnly={readOnly}
      hasIncludedFeature={Boolean(manifest.data && manifest.data.versionId === editing?.id
        && manifest.data.entries.some(item => item.availability === 'INCLUDED'))}
      onClose={() => setEditing(undefined)} />}
  </PageState>
}

function ManifestRow({ feature, value, disabled, onChange }: {
  feature: ProductFeature; value: Availability; disabled: boolean; onChange: (value: Availability) => void
}) {
  return <div className="version-manifest-row">
    <div><strong title={feature.name}>{feature.name}</strong><span>{feature.code}</span></div>
    <select aria-label={`${feature.name}可用性`} value={value} disabled={disabled}
      onChange={event => onChange(event.target.value as Availability)}>
      {(Object.keys(availabilityLabels) as Availability[]).map(option => <option key={option} value={option}>{availabilityLabels[option]}</option>)}
    </select>
  </div>
}

function VersionEditor({ productId, value, readOnly, hasIncludedFeature, onClose }: {
  productId: number; value: ProductVersion | null | undefined; readOnly: boolean; hasIncludedFeature: boolean; onClose: () => void
}) {
  const [form] = Form.useForm()
  const client = useQueryClient()
  useEffect(() => {
    if (value !== undefined) {
      form.resetFields()
      form.setFieldsValue(value ?? { status: 'PLANNING' })
    }
  }, [form, value])
  const status = Form.useWatch('status', form) as VersionStatus | undefined
  const releaseDate = Form.useWatch('releaseDate', form) as string | undefined
  const releaseBlocked = status === 'RELEASED' && (!releaseDate || !hasIncludedFeature)
  const disabled = readOnly || value?.status === 'ARCHIVED'
  const save = useMutation({
    mutationFn: (input: Record<string, unknown>) => productApi.saveVersion(productId, value?.id, { ...input, version: value?.version ?? 0 }),
    onSuccess: async saved => {
      if (value) client.setQueryData<VersionManifest>(['product-manifest', productId, value.id], current =>
        current ? { ...current, version: saved.version } : current)
      await Promise.all([client.invalidateQueries({ queryKey: ['product-versions', productId] }), client.invalidateQueries({ queryKey: ['product', productId] })])
      message.success(value ? '版本已更新' : '版本已创建')
      onClose()
    },
    onError: (error: Error) => message.error(error.message),
  })
  return <Drawer open title={readOnly ? '查看版本' : value ? '编辑版本' : '新建版本'} width={480} onClose={onClose}
    extra={<Space><Button aria-label="关闭" onClick={onClose}>关闭</Button>{!disabled && <Button type="primary" aria-label="保存版本"
      disabled={releaseBlocked} loading={save.isPending} onClick={() => form.submit()}>保存</Button>}</Space>}>
    {disabled && <Alert type="info" showIcon message="当前版本仅可查看" />}
    {releaseBlocked && <Alert className="version-release-alert" type="warning" showIcon
      message={!releaseDate ? '发布前请填写发布日期' : '发布前至少纳入一个功能'} />}
    <Form form={form} layout="vertical" disabled={disabled} onFinish={save.mutate}>
      <Form.Item label="版本名称" name="versionName" rules={[{ required: true, message: '请输入版本名称' }]}><Input disabled={Boolean(value) || disabled} /></Form.Item>
      <Form.Item label="发布日期" name="releaseDate"><Input type="date" /></Form.Item>
      <Form.Item label="状态" name="status"><Select virtual={false} disabled={!value || disabled}
        options={(value ? nextStatuses[value.status] : ['PLANNING'] as VersionStatus[]).map(item => ({ value: item, label: versionLabels[item] }))} /></Form.Item>
    </Form>
  </Drawer>
}
