import {
  BellOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  DeleteOutlined,
  PlusOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Avatar,
  Button,
  Card,
  Checkbox,
  DatePicker,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Progress,
  Select,
  Space,
  Switch,
  Tag,
  Typography,
  message,
} from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useEffect, useMemo, useState } from 'react'
import { useAuth } from '../../app/AuthProvider'
import { ApiError } from '../../services/api'
import { projectTaskApi } from './projectTaskApi'
import type {
  ProjectTask,
  ProjectTaskCheckItem,
  ProjectTaskFilter,
  ProjectTaskPriority,
  UpdateProjectTaskInput,
} from './projectTaskTypes'
import { stageNames, type Project } from './types'

interface DetailValues {
  title: string
  description?: string
  priority: ProjectTaskPriority
  assigneeUserId: number
  dueAt?: Dayjs | null
  stageCode?: string | null
  milestoneId?: number | null
  reminderEnabled: boolean
  reminderAt?: Dayjs | null
  checklist: ProjectTaskCheckItem[]
}

const filters: Array<{ key: ProjectTaskFilter; label: string }> = [
  { key: 'mine', label: '我的任务' },
  { key: 'all', label: '全部任务' },
  { key: 'today', label: '今天' },
  { key: 'overdue', label: '已逾期' },
  { key: 'completed', label: '已完成' },
]

const priorityLabels: Record<ProjectTaskPriority, string> = {
  LOW: '低',
  NORMAL: '普通',
  HIGH: '高',
}

export function ProjectTasks({
  project,
  selectedTaskId,
}: {
  project: Project
  selectedTaskId?: number
}) {
  const { me } = useAuth()
  const client = useQueryClient()
  const [filter, setFilter] = useState<ProjectTaskFilter>('mine')
  const [selectedId, setSelectedId] = useState<number | undefined>(selectedTaskId)
  const [quickTitle, setQuickTitle] = useState('')
  const [quickAssignee, setQuickAssignee] = useState(me?.id ?? 0)
  const [quickDueAt, setQuickDueAt] = useState<Dayjs | null>(null)
  const [form] = Form.useForm<DetailValues>()
  const list = useQuery({
    queryKey: ['project-tasks', project.id, filter],
    queryFn: () => projectTaskApi.list(project.id, filter),
  })
  const detail = useQuery({
    queryKey: ['project-task', project.id, selectedId],
    queryFn: () => projectTaskApi.get(project.id, selectedId!),
    enabled: selectedId !== undefined,
  })

  useEffect(() => {
    if (selectedTaskId !== undefined) setSelectedId(selectedTaskId)
  }, [selectedTaskId])

  useEffect(() => {
    if (selectedId === undefined && list.data?.length) setSelectedId(list.data[0].id)
  }, [list.data, selectedId])

  useEffect(() => {
    if (!detail.data) return
    form.setFieldsValue({
      title: detail.data.title,
      description: detail.data.description ?? '',
      priority: detail.data.priority,
      assigneeUserId: detail.data.assigneeUserId,
      dueAt: detail.data.dueAt ? dayjs(detail.data.dueAt) : null,
      stageCode: detail.data.stageCode ?? null,
      milestoneId: detail.data.milestoneId ?? null,
      reminderEnabled: detail.data.reminderEnabled,
      reminderAt: detail.data.reminderAt ? dayjs(detail.data.reminderAt) : null,
      checklist: detail.data.checklist ?? [],
    })
  }, [detail.data, form])

  const refresh = async (taskId?: number) => {
    await Promise.all([
      client.invalidateQueries({ queryKey: ['project-tasks', project.id] }),
      taskId ? client.invalidateQueries({ queryKey: ['project-task', project.id, taskId] }) : Promise.resolve(),
      client.invalidateQueries({ queryKey: ['task-reminders'] }),
    ])
  }
  const showError = (error: Error) => message.error(error instanceof ApiError ? error.message : '操作失败，请重试')
  const create = useMutation({
    mutationFn: () => projectTaskApi.create(project.id, {
      title: quickTitle.trim(),
      assigneeUserId: quickAssignee,
      dueAt: quickDueAt ? quickDueAt.startOf('hour').format('YYYY-MM-DDTHH:00:00') : null,
    }),
    onSuccess: async task => {
      setQuickTitle('')
      setQuickDueAt(null)
      setSelectedId(task.id)
      await refresh(task.id)
      message.success('任务已创建')
    },
    onError: showError,
  })
  const changeStatus = useMutation({
    mutationFn: (task: ProjectTask) => task.status === 'DONE'
      ? projectTaskApi.reopen(project.id, task.id)
      : projectTaskApi.complete(project.id, task.id),
    onSuccess: async task => {
      await refresh(task.id)
      message.success(task.status === 'DONE' ? '任务已完成' : '任务已重新打开')
    },
    onError: showError,
  })
  const save = useMutation({
    mutationFn: (values: DetailValues) => {
      const task = detail.data!
      const input: UpdateProjectTaskInput = {
        title: values.title.trim(),
        description: values.description?.trim() || null,
        priority: values.priority,
        assigneeUserId: values.assigneeUserId,
        dueAt: values.dueAt ? values.dueAt.startOf('hour').format('YYYY-MM-DDTHH:00:00') : null,
        stageCode: values.stageCode || null,
        milestoneId: values.milestoneId || null,
        reminderEnabled: values.reminderEnabled,
        reminderAt: values.reminderEnabled && values.reminderAt
          ? values.reminderAt.second(0).format('YYYY-MM-DDTHH:mm:00')
          : null,
        version: task.version,
        checklist: (values.checklist ?? []).map((item, index) => ({
          content: item.content.trim(),
          completed: Boolean(item.completed),
          sortOrder: index + 1,
        })),
      }
      return projectTaskApi.update(project.id, task.id, input)
    },
    onSuccess: async task => {
      await refresh(task.id)
      message.success('任务已保存')
    },
    onError: showError,
  })
  const remove = useMutation({
    mutationFn: (taskId: number) => projectTaskApi.remove(project.id, taskId),
    onSuccess: async () => {
      setSelectedId(undefined)
      await refresh()
      message.success('任务已删除')
    },
    onError: showError,
  })

  const members = useMemo(() => project.members.map(item => ({
    value: Number(item.userId),
    label: String(item.displayName ?? `用户 ${item.userId}`),
  })).filter(item => Number.isFinite(item.value)), [project.members])
  const groups = useMemo(() => groupTasks(list.data ?? []), [list.data])

  const submitQuick = () => {
    if (!quickTitle.trim()) {
      message.warning('请输入任务标题')
      return
    }
    create.mutate()
  }

  return (
    <div className="project-task-layout">
      <Card className="project-task-filters" size="small" title="任务视图">
        <Space direction="vertical" size={4}>
          {filters.map(item => (
            <Button
              key={item.key}
              type={filter === item.key ? 'primary' : 'text'}
              onClick={() => {
                setFilter(item.key)
                setSelectedId(undefined)
              }}
            >
              {item.label}
            </Button>
          ))}
        </Space>
      </Card>

      <div className="project-task-main">
        <Card className="project-task-quick" size="small">
          <div className="project-task-quick-row">
            <Input
              aria-label="任务标题"
              prefix={<PlusOutlined />}
              placeholder="添加任务，按回车快速创建"
              value={quickTitle}
              onChange={event => setQuickTitle(event.target.value)}
              onPressEnter={submitQuick}
            />
            <Select
              aria-label="负责人"
              value={quickAssignee}
              options={members}
              onChange={setQuickAssignee}
              suffixIcon={<UserOutlined />}
            />
            <DatePicker
              aria-label="截止时间"
              value={quickDueAt}
              onChange={setQuickDueAt}
              showTime={{ format: 'HH:00', showMinute: false, showSecond: false }}
              format="YYYY-MM-DD HH:00"
              placeholder="截止时间（选填）"
            />
            <Button type="primary" loading={create.isPending} onClick={submitQuick}>创建任务</Button>
          </div>
        </Card>

        {list.isError && <Card><Typography.Text type="danger">任务加载失败，请稍后重试</Typography.Text></Card>}
        {!list.isLoading && !groups.length && <Card><Empty description="当前视图暂无任务" /></Card>}
        {groups.map(group => (
          <section className="project-task-group" key={group.label}>
            <div className="project-task-group-title">
              <strong>{group.label}</strong>
              <span>{group.tasks.length}</span>
            </div>
            <List
              dataSource={group.tasks}
              renderItem={task => (
                <List.Item
                  className={`project-task-row ${selectedId === task.id ? 'selected' : ''}`}
                  onClick={() => setSelectedId(task.id)}
                >
                  <Checkbox
                    aria-label={`${task.status === 'DONE' ? '重新打开' : '完成'}：${task.title}`}
                    checked={task.status === 'DONE'}
                    disabled={!task.canEdit || changeStatus.isPending}
                    onClick={event => event.stopPropagation()}
                    onChange={() => changeStatus.mutate(task)}
                  />
                  <div className="project-task-row-body">
                    <div className="project-task-row-title">
                      <span className={task.status === 'DONE' ? 'done' : ''}>{task.title}</span>
                      {task.priority === 'HIGH' && <Tag color="red">高优先级</Tag>}
                    </div>
                    <Space size={12} wrap>
                      <span><UserOutlined /> {task.assigneeName}</span>
                      <span className={isOverdue(task) ? 'task-overdue' : ''}>
                        <ClockCircleOutlined /> {task.dueAt ? dayjs(task.dueAt).format('MM-DD HH:00') : '未设置截止时间'}
                      </span>
                      {task.checklistTotal > 0 && <span>{task.checklistCompleted}/{task.checklistTotal} 项</span>}
                      {task.stageCode && <Tag>{stageNames[task.stageCode] ?? task.stageCode}</Tag>}
                    </Space>
                  </div>
                </List.Item>
              )}
            />
          </section>
        ))}
      </div>

      <Card className="project-task-detail" title="任务详情">
        {!selectedId && <Empty description="选择任务后查看详情" />}
        {selectedId && detail.isLoading && <Typography.Text type="secondary">正在加载任务详情…</Typography.Text>}
        {detail.data && (
          <Form form={form} layout="vertical" onFinish={values => save.mutate(values)}>
            <Form.Item name="title" label="任务标题" rules={[{ required: true, message: '请输入任务标题' }]}>
              <Input disabled={!detail.data.canEdit} />
            </Form.Item>
            <div className="project-task-detail-grid">
              <Form.Item name="assigneeUserId" label="负责人" rules={[{ required: true }]}>
                <Select options={members} disabled={!detail.data.canEdit} />
              </Form.Item>
              <Form.Item name="priority" label="优先级">
                <Select
                  disabled={!detail.data.canEdit}
                  options={(Object.keys(priorityLabels) as ProjectTaskPriority[]).map(value => ({
                    value,
                    label: priorityLabels[value],
                  }))}
                />
              </Form.Item>
            </div>
            <Form.Item name="dueAt" label="截止时间">
              <DatePicker
                disabled={!detail.data.canEdit}
                showTime={{ format: 'HH:00', showMinute: false, showSecond: false }}
                format="YYYY-MM-DD HH:00"
                placeholder="可在任务完成后补录"
              />
            </Form.Item>
            <div className="project-task-detail-grid">
              <Form.Item name="stageCode" label="关联阶段">
                <Select
                  allowClear
                  disabled={!detail.data.canEdit}
                  options={project.stages.map(stage => ({ value: stage.code, label: stage.name }))}
                />
              </Form.Item>
              <Form.Item name="milestoneId" label="关联里程碑">
                <Select
                  allowClear
                  disabled={!detail.data.canEdit}
                  options={project.milestones.map(item => ({
                    value: Number(item.id),
                    label: String(item.name),
                  }))}
                />
              </Form.Item>
            </div>
            <Form.Item name="description" label="任务说明">
              <Input.TextArea rows={4} disabled={!detail.data.canEdit} placeholder="补充任务背景、目标和完成标准" />
            </Form.Item>
            <Form.Item label="检查项">
              <Form.List name="checklist">
                {(fields, { add, remove: removeItem }) => (
                  <Space direction="vertical" className="project-task-checklist">
                    {fields.map(field => (
                      <Space key={field.key} align="baseline">
                        <Form.Item name={[field.name, 'completed']} valuePropName="checked" noStyle>
                          <Checkbox disabled={!detail.data?.canEdit} />
                        </Form.Item>
                        <Form.Item
                          name={[field.name, 'content']}
                          rules={[{ required: true, message: '请输入检查项' }]}
                          noStyle
                        >
                          <Input disabled={!detail.data?.canEdit} placeholder="检查项内容" />
                        </Form.Item>
                        {detail.data?.canEdit && (
                          <Button type="text" danger onClick={() => removeItem(field.name)}>移除</Button>
                        )}
                      </Space>
                    ))}
                    {detail.data?.canEdit && <Button type="dashed" icon={<PlusOutlined />} onClick={() => add({
                      content: '',
                      completed: false,
                    })}>添加检查项</Button>}
                  </Space>
                )}
              </Form.List>
            </Form.Item>
            {detail.data.checklistTotal > 0 && (
              <Progress
                percent={Math.round(detail.data.checklistCompleted / detail.data.checklistTotal * 100)}
                size="small"
              />
            )}
            <div className="project-task-reminder-setting">
              <Form.Item name="reminderEnabled" label="站内提醒" valuePropName="checked">
                <Switch disabled={!detail.data.canEdit} />
              </Form.Item>
              <Form.Item noStyle shouldUpdate={(previous, current) => previous.reminderEnabled !== current.reminderEnabled}>
                {({ getFieldValue }) => getFieldValue('reminderEnabled') && (
                  <Form.Item name="reminderAt" label="提醒时间">
                    <DatePicker
                      disabled={!detail.data.canEdit}
                      showTime={{ format: 'HH:mm', minuteStep: 30, showSecond: false }}
                      format="YYYY-MM-DD HH:mm"
                      prefix={<BellOutlined />}
                    />
                  </Form.Item>
                )}
              </Form.Item>
            </div>
            <Space wrap>
              {detail.data.canEdit && (
                <Button type="primary" htmlType="submit" loading={save.isPending}>保存任务</Button>
              )}
              {detail.data.canEdit && (
                <Button
                  icon={<CheckCircleOutlined />}
                  onClick={() => changeStatus.mutate(detail.data!)}
                >
                  {detail.data.status === 'DONE' ? '重新打开' : '标记完成'}
                </Button>
              )}
              {detail.data.canDelete && (
                <Button
                  danger
                  icon={<DeleteOutlined />}
                  onClick={() => Modal.confirm({
                    title: '确认删除该任务？',
                    content: '删除后任务不会出现在项目列表中。',
                    okText: '确认删除',
                    cancelText: '取消',
                    okButtonProps: { danger: true },
                    onOk: () => remove.mutateAsync(detail.data!.id),
                  })}
                >
                  删除任务
                </Button>
              )}
            </Space>
          </Form>
        )}
      </Card>
    </div>
  )
}

function groupTasks(tasks: ProjectTask[]) {
  const values = new Map<string, ProjectTask[]>()
  for (const task of tasks) {
    const label = task.dueAt
      ? dayjs(task.dueAt).isSame(dayjs(), 'day')
        ? '今天'
        : dayjs(task.dueAt).format('M月D日')
      : '无截止时间'
    values.set(label, [...(values.get(label) ?? []), task])
  }
  return Array.from(values.entries()).map(([label, grouped]) => ({ label, tasks: grouped }))
}

function isOverdue(task: ProjectTask) {
  return task.status === 'TODO' && Boolean(task.dueAt) && dayjs(task.dueAt).isBefore(dayjs().startOf('hour'))
}
