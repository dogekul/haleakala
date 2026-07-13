import {
  BookOutlined, CodeOutlined, FileTextOutlined, PlusOutlined, ReadOutlined,
  SearchOutlined, SendOutlined, TeamOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, Button, Card, Col, Drawer, Empty, Form, Input, InputNumber, Row, Select,
  Space, Statistic, Tabs, Tag, Typography, message,
} from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { projectApi } from '../project/projectApi'
import { knowledgeApi } from './knowledgeApi'
import type { KnowledgeItem } from './types'

const typeMeta = {
  CASE: { label: '最佳实践', icon: <FileTextOutlined />, color: 'blue' },
  CODE: { label: '代码片段', icon: <CodeOutlined />, color: 'purple' },
  TRAINING: { label: '培训材料', icon: <ReadOutlined />, color: 'green' },
} as const

export function KnowledgePage() {
  const [type, setType] = useState('ALL')
  const [keyword, setKeyword] = useState('')
  const [editing, setEditing] = useState<KnowledgeItem | null | undefined>()
  const [detail, setDetail] = useState<KnowledgeItem>()
  const query = useQuery({ queryKey: ['knowledge', keyword, type], queryFn: () => knowledgeApi.search(keyword, type) })
  const values = useMemo(() => type === 'ALL' ? query.data ?? [] : (query.data ?? []).filter(item => item.type === type), [query.data, type])
  const counts = Object.fromEntries(['CASE', 'CODE', 'TRAINING'].map(key => [key, (query.data ?? []).filter(item => item.type === key).length]))
  return <div className="knowledge-page">
    <div className="knowledge-hero"><div><span className="eyebrow">DELIVERY KNOWLEDGE HUB</span><Typography.Title>让交付经验成为可搜索、可复用的组织资产</Typography.Title><Typography.Paragraph>把最佳实践、受控代码扩展和培训材料沉淀到产品版本，从项目现场直接回流产品。</Typography.Paragraph><Button type="primary" size="large" icon={<PlusOutlined />} onClick={() => setEditing(null)}>创建知识</Button></div>
      <div className="knowledge-hero-metrics"><div><BookOutlined /><Statistic value={(query.data ?? []).length} title="知识条目" /></div><div><SendOutlined /><Statistic value={(query.data ?? []).filter(item => item.status === 'PUBLISHED').length} title="已发布" /></div><div><TeamOutlined /><Statistic value={new Set((query.data ?? []).map(item => item.ownerName)).size} title="贡献者" /></div></div></div>
    <Card className="knowledge-toolbar"><div><Input allowClear prefix={<SearchOutlined />} value={keyword} onChange={event => setKeyword(event.target.value)} placeholder="搜索标题、摘要或正文" /><span>找到 {values.length} 条知识</span></div></Card>
    <Tabs className="knowledge-tabs" activeKey={type} onChange={setType} items={[
      { key: 'ALL', label: '全部知识' }, { key: 'CASE', label: '最佳实践' }, { key: 'CODE', label: '代码片段' }, { key: 'TRAINING', label: '培训材料' },
    ].map(item => ({ ...item, label: <span>{item.label}<b>{item.key === 'ALL' ? (query.data ?? []).length : counts[item.key] ?? 0}</b></span> }))} />
    <Row gutter={[14, 14]}>{values.map(item => <Col span={item.type === 'CODE' ? 12 : 8} key={item.id}><KnowledgeCard item={item} onOpen={setDetail} onEdit={setEditing} /></Col>)}
      {!query.isLoading && !values.length && <Col span={24}><Card><Empty description="没有匹配的知识" /></Card></Col>}</Row>
    <KnowledgeDetail value={detail} onClose={() => setDetail(undefined)} />
    <KnowledgeEditor value={editing} onClose={() => setEditing(undefined)} />
  </div>
}

function KnowledgeCard({ item, onOpen, onEdit }: { item: KnowledgeItem; onOpen(value: KnowledgeItem): void; onEdit(value: KnowledgeItem): void }) {
  const client = useQueryClient()
  const publish = useMutation({ mutationFn: () => knowledgeApi.publish(item.id), onSuccess: async () => { await client.invalidateQueries({ queryKey: ['knowledge'] }); message.success('知识已发布') } })
  const meta = typeMeta[item.type]
  return <Card className={`knowledge-card knowledge-${item.type.toLowerCase()}`} hoverable onClick={() => onOpen(item)}>
    <div className="knowledge-card-head"><Tag color={meta.color}>{meta.icon} {item.type}</Tag><Tag color={item.status === 'PUBLISHED' ? 'success' : 'default'}>{item.status === 'PUBLISHED' ? '已发布' : '草稿'}</Tag></div>
    <h3>{item.title}</h3><p>{item.summary}</p>
    {item.type === 'CODE' && <pre><code>{item.codeText}</code></pre>}
    {item.type === 'TRAINING' && <div className="training-meta"><TeamOutlined /><span>{item.audience}</span><b>{item.durationMinutes ?? 0} 分钟</b></div>}
    <div className="knowledge-tags">{item.tags?.split(',').filter(Boolean).map(tag => <Tag key={tag}>{tag}</Tag>)}</div>
    <div className="knowledge-card-foot"><span>{item.productName ? `${item.productName} / ${item.versionName ?? '全版本'}` : '组织通用'} · {item.ownerName}</span><Space onClick={event => event.stopPropagation()}><Button type="link" size="small" onClick={() => onEdit(item)}>编辑</Button>{item.status === 'DRAFT' && <Button type="link" size="small" loading={publish.isPending} onClick={() => publish.mutate()}>发布</Button>}</Space></div>
  </Card>
}

function KnowledgeDetail({ value, onClose }: { value?: KnowledgeItem; onClose(): void }) {
  const meta = value ? typeMeta[value.type] : undefined
  return <Drawer width={700} title="知识阅读" open={Boolean(value)} onClose={onClose}>{value && <article className="knowledge-detail"><Space><Tag color={meta?.color}>{meta?.icon} {meta?.label}</Tag><Tag>{value.status}</Tag></Space><Typography.Title level={2}>{value.title}</Typography.Title><Typography.Paragraph className="knowledge-summary">{value.summary}</Typography.Paragraph>
    {value.type === 'CODE' && <><div className="code-language">{value.language}</div><pre><code>{value.codeText}</code></pre><Alert showIcon type="info" message={value.usageNotes || '请在受控扩展点中使用'} /></>}
    {value.type === 'TRAINING' && <Alert showIcon type="success" message={`${value.audience} · ${value.durationMinutes ?? 0} 分钟`} description={value.fileObjectId ? `附件 ID：${value.fileObjectId}` : '暂无附件'} />}
    <Typography.Title level={4}>正文</Typography.Title><Typography.Paragraph className="knowledge-content">{value.content}</Typography.Paragraph><div>{value.tags?.split(',').map(tag => <Tag key={tag}>{tag}</Tag>)}</div><footer>{value.ownerName} · {value.productName ?? '组织通用'} {value.versionName ?? ''}</footer></article>}</Drawer>
}

function KnowledgeEditor({ value, onClose }: { value: KnowledgeItem | null | undefined; onClose(): void }) {
  const [form] = Form.useForm()
  const client = useQueryClient()
  const selectedType = Form.useWatch('type', form)
  const productId = Form.useWatch('productId', form)
  const products = useQuery({ queryKey: ['products-knowledge'], queryFn: projectApi.products, enabled: value !== undefined })
  const versions = useQuery({ queryKey: ['versions-knowledge', productId], queryFn: () => projectApi.versions(productId), enabled: Boolean(productId) })
  useEffect(() => { if (value !== undefined) form.setFieldsValue(value ?? { type: 'CASE', visibility: 'ORGANIZATION' }) }, [form, value])
  const save = useMutation({ mutationFn: (input: Record<string, unknown>) => knowledgeApi.save(value?.id, { ...input, version: value?.version ?? 0 }), onSuccess: async () => { await client.invalidateQueries({ queryKey: ['knowledge'] }); form.resetFields(); onClose(); message.success('知识草稿已保存') }, onError: (error: Error) => message.error(error.message) })
  return <Drawer width={650} title={value ? '编辑知识' : '创建知识'} open={value !== undefined} onClose={onClose} extra={<Button type="primary" loading={save.isPending} onClick={() => form.submit()}>保存草稿</Button>}><Form form={form} layout="vertical" onFinish={save.mutate}><Form.Item label="知识类型" name="type" rules={[{ required: true }]}><Select options={Object.entries(typeMeta).map(([key, meta]) => ({ value: key, label: meta.label }))} /></Form.Item><Form.Item label="标题" name="title" rules={[{ required: true }]}><Input /></Form.Item><Form.Item label="一句话摘要" name="summary" rules={[{ required: true }]}><Input.TextArea rows={2} /></Form.Item><Form.Item label="正文" name="content" rules={[{ required: true }]}><Input.TextArea rows={7} /></Form.Item>
    {selectedType === 'CODE' && <Row gutter={12}><Col span={8}><Form.Item label="语言" name="language" rules={[{ required: true }]}><Input placeholder="Java" /></Form.Item></Col><Col span={16}><Form.Item label="使用说明" name="usageNotes"><Input /></Form.Item></Col><Col span={24}><Form.Item label="代码" name="codeText" rules={[{ required: true }]}><Input.TextArea className="code-input" rows={9} /></Form.Item></Col></Row>}
    {selectedType === 'TRAINING' && <Row gutter={12}><Col span={14}><Form.Item label="培训对象" name="audience" rules={[{ required: true }]}><Input /></Form.Item></Col><Col span={10}><Form.Item label="时长（分钟）" name="durationMinutes"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item></Col><Col span={24}><Form.Item label="附件 ID" name="fileObjectId"><InputNumber min={1} style={{ width: '100%' }} /></Form.Item></Col></Row>}
    <Form.Item label="标签" name="tags"><Input placeholder="用英文逗号分隔" /></Form.Item><Row gutter={12}><Col span={12}><Form.Item label="产品" name="productId"><Select allowClear options={products.data?.map(item => ({ value: item.id, label: item.name }))} /></Form.Item></Col><Col span={12}><Form.Item label="版本" name="productVersionId"><Select allowClear disabled={!productId} options={versions.data?.map(item => ({ value: item.id, label: item.versionName }))} /></Form.Item></Col></Row><Form.Item label="可见范围" name="visibility"><Select options={[{ value: 'ORGANIZATION', label: '组织内可见' }]} /></Form.Item></Form></Drawer>
}
