import {
  CloudServerOutlined, DatabaseOutlined, FileSyncOutlined, ReloadOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, Button, Card, Col, Row, Space, Statistic, Table, Tag, Typography, message,
} from 'antd'
import { adminApi } from './adminApi'
import { AdminQueryAlert } from './AdminQueryAlert'
import type { DocumentCenterJob } from './types'
import { PageHeading } from './UsersTeamsPage'

const jobTypeLabels: Record<DocumentCenterJob['jobType'], string> = {
  PROJECT_INIT: '项目文档初始化',
  PROJECT_MIGRATION: '项目文档迁移',
  KNOWLEDGE_MIGRATION: '知识文档迁移',
}

const statusColors: Record<DocumentCenterJob['status'], string> = {
  PENDING: 'processing', RUNNING: 'blue', RETRY: 'warning',
  DONE: 'success', FAILED: 'error',
}

export function DocumentCenterPage() {
  const client = useQueryClient()
  const status = useQuery({
    queryKey: ['admin-document-center-status'],
    queryFn: adminApi.documentCenterStatus,
  })
  const jobs = useQuery({
    queryKey: ['admin-document-center-jobs'],
    queryFn: () => adminApi.documentCenterJobs(),
  })
  const refresh = async () => {
    await Promise.all([
      client.invalidateQueries({ queryKey: ['admin-document-center-status'] }),
      client.invalidateQueries({ queryKey: ['admin-document-center-jobs'] }),
    ])
  }
  const operation = useMutation({
    mutationFn: async (action: 'initialize' | 'knowledge' | 'projects') => {
      if (action === 'initialize') return adminApi.initializeDocumentCenter()
      if (action === 'knowledge') return adminApi.migrateKnowledgeDocuments()
      return adminApi.migrateProjectDocuments()
    },
    onSuccess: async value => {
      await refresh()
      const count = 'enqueued' in value ? Number(value.enqueued) : undefined
      message.success(count === undefined ? '文档中心已初始化' : `已加入队列 ${count} 项`)
    },
    onError: (error: Error) => message.error(error.message),
  })
  const retry = useMutation({
    mutationFn: adminApi.retryDocumentJob,
    onSuccess: async () => {
      await refresh()
      message.success('任务已重新进入队列')
    },
    onError: (error: Error) => message.error(error.message),
  })
  const snapshot = status.data
  const integration = snapshot?.integrationStatus

  return <section>
    <PageHeading
      title="文档中心"
      description="检查私有化 Outline 连接、根目录和后台同步任务，统一处理初始化、迁移与失败重试。"
      action={<Space wrap>
        <Button
          icon={<CloudServerOutlined />}
          loading={operation.isPending}
          onClick={() => operation.mutate('initialize')}
        >初始化目录</Button>
        <Button
          icon={<DatabaseOutlined />}
          loading={operation.isPending}
          onClick={() => operation.mutate('knowledge')}
        >迁移知识文档</Button>
        <Button
          type="primary"
          icon={<FileSyncOutlined />}
          loading={operation.isPending}
          onClick={() => operation.mutate('projects')}
        >迁移项目文档</Button>
      </Space>}
    />
    <AdminQueryAlert
      errors={[status.error, jobs.error]}
      onRetry={() => { void Promise.all([status.refetch(), jobs.refetch()]) }}
    />
    {integration && integration !== 'READY' && <Alert
      className="admin-document-alert"
      showIcon
      type={integration === 'FAILED' ? 'error' : 'warning'}
      message={integration === 'FAILED' ? 'Outline 连接失败' : 'Outline 尚未配置'}
      description={snapshot?.recentError
        || '请配置服务地址、API Token 和 Collection UUID 后重新初始化。'}
    />}
    <Row gutter={14} className="admin-stats">
      <Col span={6}><Card><Statistic
        title="Outline 连接"
        value={integration === 'READY' ? '正常' : integration === 'FAILED' ? '失败' : '未配置'}
        prefix={<CloudServerOutlined />}
      /></Card></Col>
      <Col span={6}><Card><Statistic
        title="待执行"
        value={(snapshot?.jobs.pending ?? 0) + (snapshot?.jobs.running ?? 0)}
        suffix="项"
      /></Card></Col>
      <Col span={6}><Card><Statistic
        title="已完成"
        value={snapshot?.jobs.success ?? 0}
        suffix="项"
      /></Card></Col>
      <Col span={6}><Card><Statistic
        title="失败"
        value={snapshot?.jobs.failed ?? 0}
        suffix="项"
        valueStyle={{ color: snapshot?.jobs.failed ? '#f54a45' : undefined }}
      /></Card></Col>
    </Row>
    <Row gutter={14}>
      <Col span={12}><RootCard title="知识库根目录" value={snapshot?.knowledgeRoot} /></Col>
      <Col span={12}><RootCard title="项目文档根目录" value={snapshot?.projectRoot} /></Col>
    </Row>
    <Card
      className="admin-surface"
      title="同步任务"
      extra={<Button
        icon={<ReloadOutlined />}
        loading={jobs.isFetching}
        onClick={() => void jobs.refetch()}
      >刷新</Button>}
    >
      <Table
        rowKey="id"
        loading={jobs.isLoading}
        dataSource={jobs.data ?? []}
        pagination={{ pageSize: 10 }}
        columns={[
          {
            title: '任务',
            key: 'job',
            render: (_, item: DocumentCenterJob) => <div className="admin-main-cell">
              <strong>{jobTypeLabels[item.jobType]}</strong>
              <span>#{item.id} · {item.businessKey}</span>
            </div>,
          },
          {
            title: '状态',
            dataIndex: 'status',
            width: 100,
            render: (value: DocumentCenterJob['status']) =>
              <Tag color={statusColors[value]}>{value}</Tag>,
          },
          { title: '尝试次数', dataIndex: 'attemptCount', width: 100 },
          {
            title: '更新时间',
            dataIndex: 'updatedAt',
            width: 180,
            render: (value: string) => value ? new Date(value).toLocaleString() : '—',
          },
          {
            title: '结果',
            dataIndex: 'lastError',
            ellipsis: true,
            render: (value?: string) => value
              ? <Typography.Text type="danger">{value}</Typography.Text>
              : <Typography.Text type="secondary">—</Typography.Text>,
          },
          {
            title: '操作',
            width: 90,
            render: (_, item: DocumentCenterJob) =>
              ['FAILED', 'RETRY'].includes(item.status) ? <Button
                type="link"
                size="small"
                loading={retry.isPending && retry.variables === item.id}
                onClick={() => retry.mutate(item.id)}
              >重试</Button> : '—',
          },
        ]}
      />
    </Card>
  </section>
}

function RootCard({
  title, value,
}: {
  title: string
  value?: { linkId?: number; status: string; lastError?: string }
}) {
  return <Card className="admin-document-root">
    <div>
      <DatabaseOutlined />
      <span><strong>{title}</strong><small>Link #{value?.linkId ?? '—'}</small></span>
    </div>
    <Tag color={value?.status === 'READY' ? 'success' : value?.status === 'FAILED' ? 'error' : 'default'}>
      {value?.status ?? 'PENDING'}
    </Tag>
    {value?.lastError && <Typography.Text type="danger">{value.lastError}</Typography.Text>}
  </Card>
}
