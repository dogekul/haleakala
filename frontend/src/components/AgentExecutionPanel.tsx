import {
  CheckCircleOutlined, CloseCircleOutlined, CodeOutlined, FileSearchOutlined,
  ExperimentOutlined, FlagOutlined, LoadingOutlined, RocketOutlined, RobotOutlined, StopOutlined,
  SwapOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Button, Card, Col, Empty, Progress, Row, Space, Table, Tag, Typography, message } from 'antd'
import { useEffect, useRef } from 'react'
import { api } from '../services/api'
import { projectApi } from '../modules/project/projectApi'
import type { ProjectDocument } from '../modules/project/types'

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
  { code: 'deliver-init', stageCode: 'START', name: '项目立项', description: '完善立项登记与立项评审文档', icon: <RocketOutlined /> },
  { code: 'deliver-require', stageCode: 'REQUIREMENT', name: '调研与启动', description: '完善调研、实施计划与项目管理文档', icon: <FileSearchOutlined /> },
  { code: 'deliver-dev', stageCode: 'CUSTOM_DEV', name: '方案与计划', description: '完善产品方案、实施方案与项目 WBS', icon: <CodeOutlined /> },
  { code: 'deliver-test', stageCode: 'GO_LIVE', name: '开发与测试', description: '归整功能性与性能测试文档', icon: <ExperimentOutlined /> },
  { code: 'deliver-transition', stageCode: 'TRIAL_HANDOVER', name: '验证与发布', description: '完善版本验收、客户确认与发布文档', icon: <SwapOutlined /> },
  { code: 'deliver-standardize', stageCode: 'STANDARDIZATION', name: '验收与结项', description: '完善验收材料、问题闭环与项目总结', icon: <RobotOutlined /> },
  { code: 'deliver-close', stageCode: 'CLOSE', name: '过程跟进', description: '完善风险、变更、周报与阶段复盘', icon: <FlagOutlined /> },
] as const

const terminal = new Set(['SUCCEEDED', 'FAILED', 'TIMED_OUT', 'CANCELLED'])

export function AgentExecutionPanel({ projectId, currentStage }: { projectId: number; currentStage: string }) {
  const client = useQueryClient()
  const refreshedJobs = useRef(new Set<number>())
  const jobsInitialized = useRef(false)
  const query = useQuery({
    queryKey: ['agent-jobs', projectId],
    queryFn: () => api<AgentJob[]>(`/api/v1/projects/${projectId}/agent-jobs`),
    refetchInterval: state => (state.state.data?.some(job => !terminal.has(job.status)) ? 2000 : false),
  })
  const documents = useQuery({
    queryKey: ['project-documents', projectId],
    queryFn: () => projectApi.documents(projectId),
  })
  const submit = useMutation({
    mutationFn: (skill: string) => api<AgentJob>(`/api/v1/projects/${projectId}/agent-jobs`, {
      method: 'POST', headers: { 'Idempotency-Key': idempotencyKey() },
      body: JSON.stringify({ skill, scenario: 'normal' }),
    }),
    onSuccess: async () => { await client.invalidateQueries({ queryKey: ['agent-jobs', projectId] }); message.success('Agent 任务已提交') },
    onError: (error: Error) => message.error(error.message),
  })
  const cancel = useMutation({
    mutationFn: (id: number) => api<AgentJob>(`/api/v1/agent-jobs/${id}/cancel`, { method: 'POST' }),
    onSuccess: async () => client.invalidateQueries({ queryKey: ['agent-jobs', projectId] }),
  })
  const jobs = query.data ?? []
  useEffect(() => {
    if (!query.data) return
    const succeeded = jobs.filter(job => job.status === 'SUCCEEDED' && !refreshedJobs.current.has(job.id))
    if (!jobsInitialized.current) {
      succeeded.forEach(job => refreshedJobs.current.add(job.id))
      jobsInitialized.current = true
      return
    }
    if (!succeeded.length) return
    succeeded.forEach(job => refreshedJobs.current.add(job.id))
    void client.invalidateQueries({ queryKey: ['project-documents', projectId] })
  }, [client, jobs, projectId, query.data])
  return <div className="agent-panel">
    <div className="agent-panel-heading"><div><Typography.Title level={3}>Skill 执行面板</Typography.Title>
      <Typography.Paragraph>按项目七阶段调用 Agent，协助补全对应的 Outline 项目文档。</Typography.Paragraph></div></div>
    <Alert type="info" showIcon message="七阶段文档协作"
      description="每个 Skill 对应一个项目阶段；Agent 生成完整草稿后，文档进入“待确认”，仍需项目负责人确认后才算完成并通过阶段门禁。" />
    {documents.isError && <Alert className="skill-document-alert" type="error" showIcon
      message="项目文档加载失败，暂时不能执行 Skill" />}
    <Row gutter={[12, 12]} className="skill-grid">{skills.map(skill => {
      const progress = documentProgress(documents.data, skill.stageCode)
      const active = jobs.some(job => job.skillCode === skill.code && !terminal.has(job.status))
      const disabled = !documents.isSuccess || progress.total === 0 || progress.actionable === 0
        || active || submit.isPending
      const actionLabel = documents.isLoading ? '加载中' : documents.isError ? '加载失败'
        : progress.total === 0 ? '暂无文档' : progress.actionable === 0
          ? progress.pending > 0 ? '等待确认' : '已完成'
          : active ? '执行中' : '执行'
      return <Col xs={24} md={12} xl={8} key={skill.code}>
      <Card className={`skill-card${currentStage === skill.stageCode ? ' is-current' : ''}`} size="small"><div className="skill-card-icon">{skill.icon}</div><div className="skill-card-copy">
        <div className="skill-card-title"><strong>{skill.name}</strong>{currentStage === skill.stageCode && <Tag color="blue">当前阶段</Tag>}</div>
        <code>{skill.code}</code><p>{skill.description}</p>
        <div className="skill-document-progress">阶段文档：已完成 <b>{progress.completed} / {progress.total}</b> 份
          {progress.pending > 0 && <span> · 待确认 {progress.pending} 份</span>}</div>
        <Button size="small" loading={submit.isPending && submit.variables === skill.code}
          disabled={disabled} onClick={() => submit.mutate(skill.code)}
          aria-label={`执行${skill.name}`}>{actionLabel}</Button>
      </div></Card>
    </Col>})}</Row>
    <Card title="执行记录" className="agent-job-card" extra={query.isFetching ? <Space><LoadingOutlined />同步中</Space> : null}>
      {jobs.length ? <Table rowKey="id" pagination={false} dataSource={jobs} columns={[
        { title: 'Skill', dataIndex: 'skillCode', render: (value: string) => <code>{value}</code> },
        { title: '状态', dataIndex: 'status', width: 120, render: statusTag },
        { title: '进度', dataIndex: 'progress', width: 220, render: (value: number, row: AgentJob) => <Progress percent={value} size="small" status={row.status === 'FAILED' ? 'exception' : row.status === 'SUCCEEDED' ? 'success' : 'active'} /> },
        { title: '异常', dataIndex: 'errorMessage', ellipsis: true },
        { title: '操作', width: 90, render: (_: unknown, row: AgentJob) => terminal.has(row.status) ? '-' : <Button size="small" danger icon={<StopOutlined />} onClick={() => cancel.mutate(row.id)}>取消</Button> },
      ]} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未执行 Skill" />}
    </Card>
  </div>
}

function documentProgress(documents: ProjectDocument[] | undefined, stageCode: string) {
  const stageDocuments = (documents ?? []).filter(document => document.stageCode === stageCode && document.gateRequired)
  return {
    total: stageDocuments.length,
    completed: stageDocuments.filter(document => document.status === 'COMPLETED').length,
    pending: stageDocuments.filter(document => document.status === 'PENDING_CONFIRMATION').length,
    actionable: stageDocuments.filter(document => ['TODO', 'IN_PROGRESS', 'FAILED'].includes(document.status)).length,
  }
}

function statusTag(status: AgentJob['status']) {
  const values: Record<string, { color: string; label: string; icon?: React.ReactNode }> = {
    QUEUED: { color: 'default', label: '排队中' }, RUNNING: { color: 'processing', label: '执行中', icon: <LoadingOutlined /> },
    SUCCEEDED: { color: 'success', label: '执行成功', icon: <CheckCircleOutlined /> }, FAILED: { color: 'error', label: '失败', icon: <CloseCircleOutlined /> },
    TIMED_OUT: { color: 'warning', label: '已超时' }, CANCELLED: { color: 'default', label: '已取消' },
  }
  const value = values[status] ?? values.QUEUED
  return <Tag color={value.color} icon={value.icon}>{value.label}</Tag>
}

function idempotencyKey() {
  return globalThis.crypto?.randomUUID?.() ?? `agent-${Date.now()}-${Math.random()}`
}
