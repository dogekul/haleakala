import {
  CheckCircleOutlined, CloseCircleOutlined, CodeOutlined, FileSearchOutlined,
  FlagOutlined, LoadingOutlined, RocketOutlined, RobotOutlined, StopOutlined,
  SwapOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Button, Card, Col, Empty, Progress, Row, Select, Space, Table, Tag, Typography, message } from 'antd'
import { useState } from 'react'
import { api } from '../services/api'

export interface AgentJob {
  id: number
  projectId: number
  skillCode: string
  scenario: string
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'TIMED_OUT' | 'CANCELLED'
  progress: number
  errorMessage?: string
  createdAt?: string
  finishedAt?: string
}

const skills = [
  { code: 'deliver-init', name: '项目初始化', description: '生成启动检查清单与项目基础文档', icon: <RocketOutlined /> },
  { code: 'deliver-require', name: '需求梳理', description: '归整需求、能力对标与分类输入', icon: <FileSearchOutlined /> },
  { code: 'deliver-dev', name: '二开实施', description: '生成开发实施计划与技术检查项', icon: <CodeOutlined /> },
  { code: 'deliver-transition', name: '上线移交', description: '准备切换、试运行和移交清单', icon: <SwapOutlined /> },
  { code: 'deliver-standardize', name: '标准化评估', description: '识别复用机会与产品化候选', icon: <RobotOutlined /> },
  { code: 'deliver-close', name: '项目收尾', description: '汇总验收材料、复盘与归档', icon: <FlagOutlined /> },
] as const

const terminal = new Set(['SUCCEEDED', 'FAILED', 'TIMED_OUT', 'CANCELLED'])

export function AgentExecutionPanel({ projectId }: { projectId: number }) {
  const client = useQueryClient()
  const [scenario, setScenario] = useState('normal')
  const query = useQuery({
    queryKey: ['agent-jobs', projectId],
    queryFn: () => api<AgentJob[]>(`/api/v1/projects/${projectId}/agent-jobs`),
    refetchInterval: state => (state.state.data?.some(job => !terminal.has(job.status)) ? 2000 : false),
  })
  const submit = useMutation({
    mutationFn: (skill: string) => api<AgentJob>(`/api/v1/projects/${projectId}/agent-jobs`, {
      method: 'POST', headers: { 'Idempotency-Key': idempotencyKey() },
      body: JSON.stringify({ skill, scenario }),
    }),
    onSuccess: async () => { await client.invalidateQueries({ queryKey: ['agent-jobs', projectId] }); message.success('Agent 任务已提交') },
    onError: (error: Error) => message.error(error.message),
  })
  const cancel = useMutation({
    mutationFn: (id: number) => api<AgentJob>(`/api/v1/agent-jobs/${id}/cancel`, { method: 'POST' }),
    onSuccess: async () => client.invalidateQueries({ queryKey: ['agent-jobs', projectId] }),
  })
  const jobs = query.data ?? []
  return <div className="agent-panel">
    <div className="agent-panel-heading"><div><Typography.Title level={3}>Skill 执行面板</Typography.Title>
      <Typography.Paragraph>调用独立 Agent 服务，任务状态、回调与产出物均由本平台可靠记录。</Typography.Paragraph></div>
      <Space><span className="scenario-label">运行场景</span><Select value={scenario} onChange={setScenario} style={{ width: 128 }} options={[
        { value: 'normal', label: '正常完成' }, { value: 'failure', label: '模拟失败' }, { value: 'timeout', label: '模拟超时' },
      ]} /></Space></div>
    <Alert type="info" showIcon message="稳定 Agent 契约"
      description="平台通过 HMAC-SHA256 验签、幂等事件和终态保护接入外部团队 Agent；界面每 2 秒轮询本地任务状态。" />
    <Row gutter={[12, 12]} className="skill-grid">{skills.map(skill => <Col span={8} key={skill.code}>
      <Card className="skill-card" size="small"><div className="skill-card-icon">{skill.icon}</div><div className="skill-card-copy">
        <strong>{skill.name}</strong><code>{skill.code}</code><p>{skill.description}</p>
        <Button size="small" loading={submit.isPending && submit.variables === skill.code}
          onClick={() => submit.mutate(skill.code)} aria-label={`执行${skill.name}`}>执行</Button>
      </div></Card>
    </Col>)}</Row>
    <Card title="执行记录" className="agent-job-card" extra={query.isFetching ? <Space><LoadingOutlined />同步中</Space> : null}>
      {jobs.length ? <Table rowKey="id" pagination={false} dataSource={jobs} columns={[
        { title: 'Skill', dataIndex: 'skillCode', render: (value: string) => <code>{value}</code> },
        { title: '场景', dataIndex: 'scenario', width: 100 },
        { title: '状态', dataIndex: 'status', width: 120, render: statusTag },
        { title: '进度', dataIndex: 'progress', width: 220, render: (value: number, row: AgentJob) => <Progress percent={value} size="small" status={row.status === 'FAILED' ? 'exception' : row.status === 'SUCCEEDED' ? 'success' : 'active'} /> },
        { title: '异常', dataIndex: 'errorMessage', ellipsis: true },
        { title: '操作', width: 90, render: (_: unknown, row: AgentJob) => terminal.has(row.status) ? '-' : <Button size="small" danger icon={<StopOutlined />} onClick={() => cancel.mutate(row.id)}>取消</Button> },
      ]} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未执行 Skill" />}
    </Card>
  </div>
}

function statusTag(status: AgentJob['status']) {
  const values: Record<string, { color: string; label: string; icon?: React.ReactNode }> = {
    QUEUED: { color: 'default', label: '排队中' }, RUNNING: { color: 'processing', label: '执行中', icon: <LoadingOutlined /> },
    SUCCEEDED: { color: 'success', label: '已完成', icon: <CheckCircleOutlined /> }, FAILED: { color: 'error', label: '失败', icon: <CloseCircleOutlined /> },
    TIMED_OUT: { color: 'warning', label: '已超时' }, CANCELLED: { color: 'default', label: '已取消' },
  }
  const value = values[status] ?? values.QUEUED
  return <Tag color={value.color} icon={value.icon}>{value.label}</Tag>
}

function idempotencyKey() {
  return globalThis.crypto?.randomUUID?.() ?? `agent-${Date.now()}-${Math.random()}`
}
