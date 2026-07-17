import {
  CloudServerOutlined, DatabaseOutlined, FileSyncOutlined, ReloadOutlined, SaveOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, Button, Card, Col, Form, Input, Row, Space, Statistic, Table, Tag, Typography, message,
} from 'antd'
import { useEffect, useRef, useState } from 'react'
import { useAuth } from '../../app/AuthProvider'
import { adminApi } from './adminApi'
import { AdminQueryAlert } from './AdminQueryAlert'
import type {
  DocumentCenterJob, OutlineConfigurationInput, OutlineConnectionTest,
} from './types'
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

const configurationSourceLabels = {
  ENVIRONMENT: '环境变量', ORGANIZATION: '组织配置', MIXED: '混合配置',
}

export function DocumentCenterPage() {
  const { me } = useAuth()
  if (!me) return null
  return <OrganizationDocumentCenter key={me.organizationId} organizationId={me.organizationId} />
}

function validateOutlineRootUrl(_: unknown, value?: string) {
  if (!value?.trim()) return Promise.resolve()
  const candidate = value.trim()
  try {
    const url = new URL(candidate)
    const hostname = url.hostname.endsWith('.') ? url.hostname.slice(0, -1) : url.hostname
    const hostValid = hostname.startsWith('[')
      ? /^\[[0-9a-f:.]+\]$/i.test(hostname)
      : hostname.split('.').every(label =>
        /^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/i.test(label))
    if (
      /^https?:\/\/[^\s/?#\\]+\/?$/i.test(candidate)
      && /^[\x00-\x7f]+$/.test(candidate)
      && !candidate.includes('%')
      && ['http:', 'https:'].includes(url.protocol)
      && hostValid
      && !url.username
      && !url.password
      && !url.search
      && !url.hash
      && (url.pathname === '' || url.pathname === '/')
    ) return Promise.resolve()
  } catch {
    // The validation message below covers malformed URLs too.
  }
  return Promise.reject(new Error('请输入 HTTP(S) 根地址'))
}

function OrganizationDocumentCenter({ organizationId }: { organizationId: number }) {
  const client = useQueryClient()
  const [form] = Form.useForm<OutlineConfigurationInput>()
  const [connectionTest, setConnectionTest] = useState<OutlineConnectionTest>()
  const configurationDirty = useRef(false)
  const configurationQueryKey = ['admin-outline-configuration', organizationId] as const
  const statusQueryKey = ['admin-document-center-status', organizationId] as const
  const jobsQueryKey = ['admin-document-center-jobs', organizationId] as const
  const configuration = useQuery({
    queryKey: configurationQueryKey,
    queryFn: adminApi.outlineConfiguration,
  })
  const status = useQuery({
    queryKey: statusQueryKey,
    queryFn: adminApi.documentCenterStatus,
  })
  const jobs = useQuery({
    queryKey: jobsQueryKey,
    queryFn: () => adminApi.documentCenterJobs(),
  })
  useEffect(() => {
    if (!configuration.data || configurationDirty.current) return
    form.setFieldsValue({
      baseUrl: configuration.data.baseUrl,
      publicBaseUrl: configuration.data.publicBaseUrl,
      collectionId: configuration.data.collectionId,
      apiToken: '',
    })
  }, [configuration.data, form])
  const refresh = async () => {
    await Promise.all([
      client.invalidateQueries({ queryKey: statusQueryKey }),
      client.invalidateQueries({ queryKey: jobsQueryKey }),
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
  const testConfiguration = useMutation({
    mutationFn: async () => adminApi.testOutlineConfiguration(await form.validateFields()),
    onSuccess: value => {
      configurationDirty.current = true
      form.setFieldValue('collectionId', value.collectionId)
      setConnectionTest(value)
      message.success('Outline 连接正常')
    },
    onError: (error: Error) => message.error(error.message),
  })
  const saveConfiguration = useMutation({
    mutationFn: adminApi.saveOutlineConfiguration,
    onSuccess: async value => {
      configurationDirty.current = false
      form.setFieldsValue({
        baseUrl: value.baseUrl,
        publicBaseUrl: value.publicBaseUrl,
        collectionId: value.collectionId,
        apiToken: '',
      })
      client.setQueryData(configurationQueryKey, value)
      await Promise.all([
        client.invalidateQueries({ queryKey: configurationQueryKey }),
        client.invalidateQueries({ queryKey: statusQueryKey }),
        client.invalidateQueries({ queryKey: jobsQueryKey }),
      ])
      message.success('Outline 配置已保存')
    },
    onError: (error: Error) => message.error(error.message),
  })
  const snapshot = status.data
  const integration = snapshot?.integrationStatus
  const configurationPending = testConfiguration.isPending || saveConfiguration.isPending

  return <section>
    <PageHeading
      title="文档中心"
      description="检查私有化 Outline 连接、根目录和后台同步任务，统一处理初始化、迁移与失败重试。"
      action={<Space wrap>
        <Button
          aria-label="初始化目录"
          icon={<CloudServerOutlined />}
          disabled={integration !== 'READY'}
          loading={operation.isPending}
          onClick={() => operation.mutate('initialize')}
          title={integration === 'READY' ? undefined : '请先保存并验证 Outline 配置'}
        >初始化目录</Button>
        <Button
          aria-label="迁移知识文档"
          icon={<DatabaseOutlined />}
          disabled={integration !== 'READY'}
          loading={operation.isPending}
          onClick={() => operation.mutate('knowledge')}
          title={integration === 'READY' ? undefined : '请先保存并验证 Outline 配置'}
        >迁移知识文档</Button>
        <Button
          aria-label="迁移项目文档"
          type="primary"
          icon={<FileSyncOutlined />}
          disabled={integration !== 'READY'}
          loading={operation.isPending}
          onClick={() => operation.mutate('projects')}
          title={integration === 'READY' ? undefined : '请先保存并验证 Outline 配置'}
        >迁移项目文档</Button>
      </Space>}
    />
    <AdminQueryAlert
      errors={[configuration.error, status.error, jobs.error]}
      onRetry={() => {
        void Promise.all([configuration.refetch(), status.refetch(), jobs.refetch()])
      }}
    />
    <Card
      className="admin-surface admin-document-config"
      loading={configuration.isLoading}
      title="Outline 连接配置"
    >
      <Form
        disabled={configurationPending}
        form={form}
        layout="vertical"
        onValuesChange={() => {
          configurationDirty.current = true
          setConnectionTest(undefined)
        }}
        requiredMark="optional"
        onFinish={values => saveConfiguration.mutate(values)}
      >
        <Row gutter={16}>
          <Col xs={24} lg={12}>
            <Form.Item
              label="服务地址"
              name="baseUrl"
              extra="后端访问地址；本地 Compose 通常使用 host.docker.internal。"
              rules={[{ required: true, whitespace: true }, { validator: validateOutlineRootUrl }]}
            >
              <Input placeholder="http://host.docker.internal:3000" />
            </Form.Item>
          </Col>
          <Col xs={24} lg={12}>
            <Form.Item
              label="浏览器访问地址"
              name="publicBaseUrl"
              extra="用于“在 Outline 中打开”的用户可访问地址。"
              rules={[{ validator: validateOutlineRootUrl }]}
            >
              <Input aria-label="浏览器访问地址" placeholder="http://localhost:3000" />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={16}>
          <Col xs={24} lg={12}>
            <Form.Item
              label="Collection 链接或 UUID"
              name="collectionId"
              rules={[{ required: true }]}
            >
              <Input placeholder="粘贴 Outline Collection 链接或 UUID" />
            </Form.Item>
          </Col>
          <Col xs={24} lg={12}>
            <Form.Item
              label="API Token"
              name="apiToken"
              extra={configuration.data?.apiTokenConfigured
                ? 'API Token 已配置，留空将保持不变。'
                : '从 Outline 设置 → API Keys 创建。'}
            >
              <Input.Password aria-label="API Token" autoComplete="new-password" />
            </Form.Item>
          </Col>
        </Row>
        <div className="admin-document-config-actions">
          <Space>
            <Button
              disabled={saveConfiguration.isPending}
              loading={testConfiguration.isPending}
              onClick={() => testConfiguration.mutate()}
            >测试连接</Button>
            <Button
              aria-label="保存配置"
              disabled={testConfiguration.isPending}
              type="primary"
              icon={<SaveOutlined />}
              loading={saveConfiguration.isPending}
              onClick={() => form.submit()}
            >保存配置</Button>
          </Space>
          <Space wrap>
            {configuration.data && <Tag color={configuration.data.apiTokenConfigured
              ? 'success'
              : 'default'}>
              API Token {configuration.data.apiTokenConfigured ? '已配置' : '未配置'}
            </Tag>}
            {configuration.data?.source && <Tag>
              配置来源：{configurationSourceLabels[configuration.data.source]}
            </Tag>}
          </Space>
        </div>
        {connectionTest && <Alert
          className="admin-document-config-result"
          showIcon
          type="success"
          message={connectionTest.collectionName}
          description={`状态：${connectionTest.status} · Collection UUID：${connectionTest.collectionId}`}
        />}
      </Form>
    </Card>
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
