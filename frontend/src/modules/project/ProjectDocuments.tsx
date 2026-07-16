import {
  CheckCircleOutlined, ClockCircleOutlined, FileTextOutlined, ReloadOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, Button, Card, Col, Drawer, Empty, Row, Space, Spin, Tag, Typography, message,
} from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../app/AuthProvider'
import { DocumentWorkspace } from '../document/DocumentWorkspace'
import { projectApi } from './projectApi'
import { stageNames, type Project, type ProjectDocument } from './types'

const stageCodes = [
  'START', 'REQUIREMENT', 'CUSTOM_DEV', 'GO_LIVE', 'TRIAL_HANDOVER',
  'STANDARDIZATION', 'CLOSE',
]

const statusMeta: Record<ProjectDocument['status'], {
  label: string
  color: string
  icon: React.ReactNode
}> = {
  PENDING: { label: '待初始化', color: 'default', icon: <ClockCircleOutlined /> },
  TODO: { label: '待填写', color: 'processing', icon: <FileTextOutlined /> },
  PENDING_CONFIRMATION: { label: '待确认', color: 'warning', icon: <WarningOutlined /> },
  COMPLETED: { label: '已完成', color: 'success', icon: <CheckCircleOutlined /> },
  FAILED: { label: '同步失败', color: 'error', icon: <WarningOutlined /> },
}

export function ProjectDocuments({ project }: { project: Project }) {
  const { me } = useAuth()
  const client = useQueryClient()
  const [stage, setStage] = useState(project.currentStage)
  const [selected, setSelected] = useState<ProjectDocument>()
  const query = useQuery({
    queryKey: ['project-documents', project.id],
    queryFn: () => projectApi.documents(project.id),
  })
  const retry = useMutation({
    mutationFn: () => projectApi.retryDocuments(project.id),
    onSuccess: async () => {
      await Promise.all([
        client.invalidateQueries({ queryKey: ['project', project.id] }),
        client.invalidateQueries({ queryKey: ['project-documents', project.id] }),
      ])
      message.success('文档空间已重新进入初始化队列')
    },
  })
  const confirm = useMutation({
    mutationFn: (documentId: number) => projectApi.confirmDocument(project.id, documentId),
    onSuccess: async value => {
      setSelected(value)
      await client.invalidateQueries({ queryKey: ['project-documents', project.id] })
      message.success('文档已确认')
    },
  })
  const documents = query.data ?? []
  useEffect(() => {
    if (!selected) return
    const refreshed = documents.find(item => item.id === selected.id)
    if (refreshed && refreshed !== selected) setSelected(refreshed)
  }, [documents, selected])
  const counts = useMemo(() => documents.reduce<Record<string, number>>((all, item) => {
    all[item.stageCode] = (all[item.stageCode] ?? 0) + 1
    return all
  }, {}), [documents])
  const visible = documents.filter(item => item.stageCode === stage)
  const canEdit = me?.permissions.includes('project:write') ?? false
  const canConfirm = me?.id === project.managerUserId
    || (me?.permissions.includes('system:manage') ?? false)

  return <div className="project-documents">
    {project.documentSpaceStatus === 'FAILED' && <Alert
      className="project-document-alert"
      type="error"
      showIcon
      message="项目文档空间初始化失败"
      description={project.documentSpaceError || '请检查 Outline 配置后重试'}
      action={<Button
        aria-label="重试初始化"
        icon={<ReloadOutlined />}
        loading={retry.isPending}
        onClick={() => retry.mutate()}
      >重试初始化</Button>}
    />}
    {['PENDING', 'INITIALIZING'].includes(project.documentSpaceStatus ?? '') && <Alert
      className="project-document-alert"
      type="info"
      showIcon
      message={project.documentSpaceStatus === 'INITIALIZING'
        ? '正在创建 Outline 项目目录与阶段文档'
        : '项目文档初始化已进入队列'}
      description="项目本身可以继续使用，文档准备完成后会自动显示。"
    />}
    <div className="project-document-layout">
      <aside className="project-document-stages" aria-label="项目文档阶段">
        <div className="project-document-stage-heading">
          <span>PROJECT DOCUMENTS</span>
          <strong>七阶段文档</strong>
        </div>
        {stageCodes.map((code, index) => <button
          type="button"
          key={code}
          className={stage === code ? 'is-active' : ''}
          onClick={() => setStage(code)}
          aria-label={`${stageNames[code]} ${counts[code] ?? 0} 份文档`}
        >
          <b>{String(index + 1).padStart(2, '0')}</b>
          <span>{stageNames[code]}</span>
          <em>{counts[code] ?? 0}</em>
        </button>)}
        <Link className="project-template-link" to="/knowledge">
          在知识库维护文档模版
        </Link>
      </aside>
      <main className="project-document-main">
        <header>
          <div>
            <Typography.Title level={3}>{stageNames[stage]}</Typography.Title>
            <Typography.Text type="secondary">
              补全文档内容，负责人确认后用于阶段推进门禁。
            </Typography.Text>
          </div>
          <Space>
            <Tag color="blue">{visible.filter(item => item.requirement === 'REQUIRED').length} 份必需</Tag>
            <Tag>{visible.length} 份文档</Tag>
          </Space>
        </header>
        {query.isLoading ? <div className="project-document-loading"><Spin /></div>
          : query.error ? <Alert
              type="error"
              showIcon
              message="项目文档加载失败"
              description={(query.error as Error).message}
              action={<Button onClick={() => void query.refetch()}>重试</Button>}
            />
          : <Row gutter={[14, 14]}>
            {visible.map(item => <Col xs={24} lg={12} xxl={8} key={item.id}>
              <DocumentCard
                document={item}
                onOpen={() => !['PENDING', 'FAILED'].includes(item.status) && setSelected(item)}
              />
            </Col>)}
            {!visible.length && <Col span={24}><Card className="project-document-empty">
              <Empty description="本阶段暂无文档模版" />
            </Card></Col>}
          </Row>}
      </main>
    </div>
    <Drawer
      title={selected?.title}
      width="min(1180px, 94vw)"
      open={Boolean(selected)}
      destroyOnHidden
      onClose={() => setSelected(undefined)}
    >
      {selected && <>
        <div className="project-document-drawer-meta">
          <Space wrap>
            <Tag color={selected.requirement === 'REQUIRED' ? 'red' : 'default'}>
              {selected.requirement === 'REQUIRED' ? '必需文档' : '可选文档'}
            </Tag>
            <Tag color={statusMeta[selected.status].color}>{statusMeta[selected.status].label}</Tag>
            <Typography.Text type="secondary">
              来源模版 #{selected.sourceTemplateId} · 发布修订 {selected.sourceTemplateRevision}
            </Typography.Text>
          </Space>
          {canConfirm && selected.status === 'PENDING_CONFIRMATION' && <Button
            aria-label="确认文档"
            type="primary"
            icon={<CheckCircleOutlined />}
            loading={confirm.isPending}
            onClick={() => confirm.mutate(selected.id)}
          >确认文档</Button>}
        </div>
        <DocumentWorkspace
          title={selected.title}
          load={() => projectApi.loadDocument(project.id, selected.id)}
          save={input => projectApi.saveDocument(project.id, selected.id, input)}
          exportUrl={format => projectApi.exportUrl(project.id, selected.id, format)}
          canEdit={canEdit}
          onSaved={() => void client.invalidateQueries({
            queryKey: ['project-documents', project.id],
          })}
        />
      </>}
    </Drawer>
  </div>
}

function DocumentCard({
  document, onOpen,
}: {
  document: ProjectDocument
  onOpen(): void
}) {
  const meta = statusMeta[document.status]
  const enabled = !['PENDING', 'FAILED'].includes(document.status)
  return <Card
    className={`project-document-card is-${document.status.toLowerCase()}`}
    hoverable={enabled}
    onClick={onOpen}
  >
    <div className="project-document-card-head">
      <Tag color={document.requirement === 'REQUIRED' ? 'red' : 'default'}>
        {document.requirement === 'REQUIRED' ? '必需' : '可选'}
      </Tag>
      <Tag color={meta.color}>{meta.icon} {meta.label}</Tag>
    </div>
    <h3>{document.title}</h3>
    <p>来源模版 #{document.sourceTemplateId} · 发布修订 {document.sourceTemplateRevision}</p>
    {document.status === 'FAILED' && <Alert
      type="error"
      showIcon
      message={document.lastError || 'Outline 同步失败'}
    />}
    <footer>
      <span>当前修订 {document.revision ?? '-'}</span>
      <span>{document.lastSyncedAt
        ? `同步于 ${new Date(document.lastSyncedAt).toLocaleString()}`
        : '尚未同步'}</span>
    </footer>
  </Card>
}
