import {
  BookOutlined, CodeOutlined, DownloadOutlined, FileDoneOutlined, FileTextOutlined,
  PlusOutlined, ReadOutlined, SearchOutlined, SendOutlined, TeamOutlined, UploadOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, Button, Card, Col, Drawer, Empty, Form, Input, InputNumber, Row, Select,
  Space, Statistic, Switch, Tabs, Tag, Typography, Upload, message,
} from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { DocumentWorkspace } from '../document/DocumentWorkspace'
import { stageNames } from '../project/types'
import { projectApi } from '../project/projectApi'
import { knowledgeApi } from './knowledgeApi'
import type { KnowledgeItem, UploadedFile } from './types'

const typeMeta = {
  CASE: { label: '最佳实践', icon: <FileTextOutlined />, color: 'blue' },
  CODE: { label: '代码片段', icon: <CodeOutlined />, color: 'purple' },
  TRAINING: { label: '培训材料', icon: <ReadOutlined />, color: 'green' },
  TEMPLATE: { label: '文档模版', icon: <FileDoneOutlined />, color: 'geekblue' },
} as const

const documentStatusLabel = {
  PENDING: '待初始化', CREATING: '初始化中', READY: '已同步', FAILED: '同步失败',
} as const

export function KnowledgePage() {
  const [type, setType] = useState('ALL')
  const [keyword, setKeyword] = useState('')
  const [editing, setEditing] = useState<KnowledgeItem | null | undefined>()
  const [detail, setDetail] = useState<KnowledgeItem>()
  const query = useQuery({
    queryKey: ['knowledge', keyword, type],
    queryFn: () => knowledgeApi.search(keyword, type),
  })
  const values = useMemo(
    () => type === 'ALL'
      ? query.data ?? []
      : (query.data ?? []).filter(item => item.type === type),
    [query.data, type],
  )
  const keys = Object.keys(typeMeta) as Array<keyof typeof typeMeta>
  const counts = Object.fromEntries(keys.map(
    key => [key, (query.data ?? []).filter(item => item.type === key).length],
  ))
  const tabs = [
    { key: 'ALL', label: '全部知识' },
    ...keys.map(key => ({ key, label: typeMeta[key].label })),
  ]

  return <div className="knowledge-page">
    <div className="knowledge-hero">
      <div>
        <span className="eyebrow">DELIVERY KNOWLEDGE HUB</span>
        <Typography.Title>让交付经验成为可搜索、可复用的组织资产</Typography.Title>
        <Typography.Paragraph>
          把最佳实践、受控代码扩展、培训材料与文档模版沉淀到 Outline，
          从项目现场直接复用并持续完善。
        </Typography.Paragraph>
        <Button type="primary" size="large" icon={<PlusOutlined />} onClick={() => setEditing(null)}>
          创建知识
        </Button>
      </div>
      <div className="knowledge-hero-metrics">
        <div><BookOutlined /><Statistic value={(query.data ?? []).length} title="知识条目" /></div>
        <div><SendOutlined /><Statistic
          value={(query.data ?? []).filter(item => item.status === 'PUBLISHED').length}
          title="已发布"
        /></div>
        <div><TeamOutlined /><Statistic
          value={new Set((query.data ?? []).map(item => item.ownerName)).size}
          title="贡献者"
        /></div>
      </div>
    </div>
    <Card className="knowledge-toolbar"><div>
      <Input
        allowClear
        prefix={<SearchOutlined />}
        value={keyword}
        onChange={event => setKeyword(event.target.value)}
        placeholder="搜索标题、摘要或正文"
      />
      <span>找到 {values.length} 条知识</span>
    </div></Card>
    <Tabs
      className="knowledge-tabs"
      activeKey={type}
      onChange={setType}
      items={tabs.map(item => ({
        ...item,
        label: <span>{item.label}<b>
          {item.key === 'ALL' ? (query.data ?? []).length : counts[item.key] ?? 0}
        </b></span>,
      }))}
    />
    <Row gutter={[14, 14]}>
      {values.map(item => <Col xs={24} md={12} xl={8} key={item.id}>
        <KnowledgeCard item={item} onOpen={setDetail} onEdit={setEditing} />
      </Col>)}
      {!query.isLoading && !values.length && <Col span={24}>
        <Card><Empty description="没有匹配的知识" /></Card>
      </Col>}
    </Row>
    <KnowledgeDetail value={detail} onClose={() => setDetail(undefined)} />
    <KnowledgeEditor
      value={editing}
      onClose={() => setEditing(undefined)}
      onCreated={setDetail}
    />
  </div>
}

function KnowledgeCard({
  item, onOpen, onEdit,
}: {
  item: KnowledgeItem
  onOpen(value: KnowledgeItem): void
  onEdit(value: KnowledgeItem): void
}) {
  const client = useQueryClient()
  const publish = useMutation({
    mutationFn: () => knowledgeApi.publish(item.id),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ['knowledge'] })
      message.success('知识已发布')
    },
  })
  const meta = typeMeta[item.type]
  const documentStatus = item.documentStatus ?? (item.content ? 'READY' : 'PENDING')
  return <Card
    className={`knowledge-card knowledge-${item.type.toLowerCase()}`}
    hoverable
    onClick={() => onOpen(item)}
  >
    <div className="knowledge-card-head">
      <Tag color={meta.color}>{meta.icon} {item.type}</Tag>
      <Tag color={item.status === 'PUBLISHED' ? 'success' : 'default'}>
        {item.status === 'PUBLISHED' ? '已发布' : '草稿'}
      </Tag>
    </div>
    <h3>{item.title}</h3>
    <p>{item.summary}</p>
    <div className="knowledge-card-content">
      {documentStatus !== 'READY' && <div
        className={`knowledge-sync-state is-${documentStatus.toLowerCase()}`}
      >
        <strong>{documentStatusLabel[documentStatus]}</strong>
        <span>{item.documentError || '正文将在 Outline 初始化完成后可用'}</span>
      </div>}
      {documentStatus === 'READY' && item.type === 'CODE' && <pre><code>{item.codeText}</code></pre>}
      {documentStatus === 'READY' && item.type === 'TRAINING' && <div className="training-meta">
        <TeamOutlined /><span>{item.audience}</span><b>{item.durationMinutes ?? 0} 分钟</b>
      </div>}
      {documentStatus === 'READY' && item.type === 'TEMPLATE' && <div className="template-meta">
        <div>
          <strong>
            {stageNames[item.stageCode ?? ''] ?? item.stageCode}
            {' · '}{item.requirement === 'REQUIRED' ? '必需' : '可选'}
          </strong>
          <span>项目阶段文档</span>
        </div>
        <div>
          <strong>{item.enabled === false ? '已停用' : '新项目自动应用'}</strong>
          <span>修订 {item.publishedRevision ?? item.documentRevision ?? '-'}</span>
        </div>
      </div>}
      {documentStatus === 'READY' && item.type === 'CASE' && <div className="knowledge-document-hint">
        <FileTextOutlined /><span>打开查看 Outline 最新正文</span>
      </div>}
    </div>
    <div className="knowledge-tags">
      {item.tags?.split(',').filter(Boolean).map(tag => <Tag key={tag}>{tag}</Tag>)}
    </div>
    <div className="knowledge-card-foot">
      <span>
        {item.productName ? `${item.productName} / ${item.versionName ?? '全版本'}` : '组织通用'}
        {' · '}{item.ownerName}
      </span>
      <Space onClick={event => event.stopPropagation()}>
        <Button type="link" size="small" onClick={() => onEdit(item)}>编辑</Button>
        {item.status === 'DRAFT' && <Button
          type="link"
          size="small"
          loading={publish.isPending}
          onClick={() => publish.mutate()}
        >发布</Button>}
      </Space>
    </div>
  </Card>
}

function KnowledgeDetail({ value, onClose }: { value?: KnowledgeItem; onClose(): void }) {
  const meta = value ? typeMeta[value.type] : undefined
  return <Drawer
    width="min(1180px, 94vw)"
    title="知识文档"
    open={Boolean(value)}
    onClose={onClose}
    destroyOnClose
  >
    {value && <div className="knowledge-detail">
      <div className="knowledge-detail-heading">
        <Space wrap>
          <Tag color={meta?.color}>{meta?.icon} {meta?.label}</Tag>
          <Tag>{value.status === 'PUBLISHED' ? '已发布' : '草稿'}</Tag>
          {value.type === 'TEMPLATE' && <>
            <Tag color="blue">{stageNames[value.stageCode ?? ''] ?? value.stageCode}</Tag>
            <Tag color={value.requirement === 'REQUIRED' ? 'red' : 'default'}>
              {value.requirement === 'REQUIRED' ? '项目必需' : '项目可选'}
            </Tag>
          </>}
        </Space>
        <Typography.Paragraph>{value.summary}</Typography.Paragraph>
      </div>
      {value.type === 'CODE' && <Alert
        showIcon
        type="info"
        message={`${value.language ?? '代码'} · ${value.usageNotes || '请在受控扩展点中使用'}`}
      />}
      {value.type === 'TRAINING' && <div className="knowledge-training-detail">
        <Alert
          showIcon
          type="success"
          message={`${value.audience} · ${value.durationMinutes ?? 0} 分钟`}
          description={value.fileObjectId
            ? `附件：${value.fileOriginalName ?? `文件 #${value.fileObjectId}`} · v${value.fileVersion ?? 1}`
            : '暂无附件'}
        />
        {value.fileObjectId && <Button
          icon={<DownloadOutlined />}
          href={`/api/v1/files/${value.fileObjectId}/download`}
          target="_blank"
        >下载附件</Button>}
      </div>}
      <DocumentWorkspace
        key={value.id}
        title={value.title}
        load={() => knowledgeApi.loadDocument(value.id)}
        save={input => knowledgeApi.saveDocument(value.id, input)}
        exportUrl={format => knowledgeApi.exportUrl(value.id, format)}
        canEdit
      />
      <footer>{value.ownerName} · {value.productName ?? '组织通用'} {value.versionName ?? ''}</footer>
    </div>}
  </Drawer>
}

function KnowledgeEditor({
  value, onClose, onCreated,
}: {
  value: KnowledgeItem | null | undefined
  onClose(): void
  onCreated(value: KnowledgeItem): void
}) {
  const [form] = Form.useForm()
  const [attachment, setAttachment] = useState<UploadedFile>()
  const client = useQueryClient()
  const selectedType = Form.useWatch('type', form)
  const productId = Form.useWatch('productId', form)
  const products = useQuery({
    queryKey: ['products-knowledge'],
    queryFn: projectApi.products,
    enabled: value !== undefined,
  })
  const versions = useQuery({
    queryKey: ['versions-knowledge', productId],
    queryFn: () => projectApi.versions(productId),
    enabled: Boolean(productId),
  })

  useEffect(() => {
    if (value === undefined) return
    form.resetFields()
    form.setFieldsValue(value ?? {
      type: 'CASE',
      visibility: 'ORGANIZATION',
      requirement: 'REQUIRED',
      enabled: true,
    })
    setAttachment(value?.fileObjectId ? {
      id: value.fileObjectId,
      originalName: value.fileOriginalName ?? `文件 #${value.fileObjectId}`,
      fileVersion: value.fileVersion ?? 1,
      sizeBytes: value.fileSizeBytes ?? 0,
    } : undefined)
  }, [form, value])

  const save = useMutation({
    mutationFn: (input: Record<string, unknown>) => knowledgeApi.save(value?.id, {
      ...input,
      version: value?.version ?? 0,
      documentRevision: value?.documentRevision,
    }),
    onSuccess: async saved => {
      await client.invalidateQueries({ queryKey: ['knowledge'] })
      form.resetFields()
      onClose()
      message.success(value ? '知识元数据已保存' : '知识草稿已保存')
      if (!value && saved.documentStatus) onCreated(saved)
    },
    onError: (error: Error) => message.error(error.message),
  })
  const stages = Object.entries(stageNames).map(([stage, label]) => ({ value: stage, label }))

  return <Drawer
    width={650}
    title={value ? '编辑知识元数据' : '创建知识'}
    open={value !== undefined}
    onClose={onClose}
    extra={<Button type="primary" loading={save.isPending} onClick={() => form.submit()}>
      保存草稿
    </Button>}
  >
    <Form form={form} layout="vertical" onFinish={save.mutate}>
      <Form.Item label="知识类型" name="type" rules={[{ required: true }]}>
        <Select options={Object.entries(typeMeta).map(
          ([key, meta]) => ({ value: key, label: meta.label }),
        )} />
      </Form.Item>
      <Form.Item label="标题" name="title" rules={[{ required: true }]}><Input /></Form.Item>
      <Form.Item label="一句话摘要" name="summary" rules={[{ required: true }]}>
        <Input.TextArea rows={2} />
      </Form.Item>
      {!value && <Form.Item
        label="正文"
        name="content"
        rules={[{ required: true }]}
      ><Input.TextArea className="knowledge-markdown-input" rows={9} /></Form.Item>}
      {selectedType === 'CODE' && <Row gutter={12}>
        <Col span={8}><Form.Item label="语言" name="language" rules={[{ required: true }]}>
          <Input placeholder="Java" />
        </Form.Item></Col>
        <Col span={16}><Form.Item label="使用说明" name="usageNotes"><Input /></Form.Item></Col>
        <Col span={24}><Form.Item label="代码" name="codeText" rules={[{ required: true }]}>
          <Input.TextArea className="code-input" rows={9} />
        </Form.Item></Col>
      </Row>}
      {selectedType === 'TRAINING' && <Row gutter={12}>
        <Col span={14}><Form.Item
          label="培训对象"
          name="audience"
          rules={[{ required: true }]}
        ><Input /></Form.Item></Col>
        <Col span={10}><Form.Item label="时长（分钟）" name="durationMinutes">
          <InputNumber min={0} style={{ width: '100%' }} />
        </Form.Item></Col>
        <Col span={24}>
          <Form.Item name="fileObjectId" hidden><Input /></Form.Item>
          <TrainingAttachment
            attachment={attachment}
            onUploaded={file => {
              setAttachment(file)
              form.setFieldValue('fileObjectId', file.id)
            }}
          />
        </Col>
      </Row>}
      {selectedType === 'TEMPLATE' && <Row gutter={12}>
        <Col span={12}><Form.Item
          label="适用交付阶段"
          name="stageCode"
          rules={[{ required: true }]}
        ><Select options={stages} /></Form.Item></Col>
        <Col span={12}><Form.Item
          label="项目必需性"
          name="requirement"
          rules={[{ required: true }]}
        ><Select options={[
          { value: 'REQUIRED', label: '必需' },
          { value: 'OPTIONAL', label: '可选' },
        ]} /></Form.Item></Col>
        <Col span={24}><Form.Item
          label="新项目自动应用"
          name="enabled"
          valuePropName="checked"
        ><Switch /></Form.Item></Col>
      </Row>}
      <Form.Item label="标签" name="tags"><Input placeholder="用英文逗号分隔" /></Form.Item>
      <Row gutter={12}>
        <Col span={12}><Form.Item label="产品" name="productId">
          <Select
            allowClear
            options={products.data?.map(item => ({ value: item.id, label: item.name }))}
          />
        </Form.Item></Col>
        <Col span={12}><Form.Item label="版本" name="productVersionId">
          <Select
            allowClear
            disabled={!productId}
            options={versions.data?.map(item => ({
              value: item.id, label: item.versionName,
            }))}
          />
        </Form.Item></Col>
      </Row>
      <Form.Item label="可见范围" name="visibility">
        <Select options={[{ value: 'ORGANIZATION', label: '组织内可见' }]} />
      </Form.Item>
    </Form>
  </Drawer>
}

function TrainingAttachment({
  attachment, onUploaded,
}: {
  attachment?: UploadedFile
  onUploaded(file: UploadedFile): void
}) {
  const upload = async (file: File) => {
    const stored = attachment
      ? await knowledgeApi.addFileVersion(attachment.id, file)
      : await knowledgeApi.upload(file)
    onUploaded(stored)
    message.success(attachment ? '附件新版本已上传' : '附件已上传')
  }
  return <Card
    size="small"
    title="培训附件"
    extra={attachment && <Button
      type="link"
      icon={<DownloadOutlined />}
      href={`/api/v1/files/${attachment.id}/download`}
      target="_blank"
    >下载</Button>}
  >
    {attachment && <Alert
      showIcon
      type="success"
      message={attachment.originalName}
      description={`已上传 · v${attachment.fileVersion}${
        attachment.sizeBytes ? ` · ${Math.ceil(attachment.sizeBytes / 1024)} KB` : ''
      }`}
    />}
    <Upload
      accept=".pdf,.ppt,.pptx,.doc,.docx,.zip,.mp4"
      showUploadList={false}
      customRequest={({ file, onSuccess, onError }) =>
        upload(file as File).then(result => onSuccess?.(result)).catch(onError)}
    >
      <Button icon={<UploadOutlined />}>
        {attachment ? '上传新版本' : '选择文件并上传'}
      </Button>
    </Upload>
  </Card>
}
