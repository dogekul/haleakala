import { BellOutlined, CheckOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Badge, Button, Empty, Popover, Space, Typography } from 'antd'
import dayjs from 'dayjs'
import { Link } from 'react-router-dom'
import { projectTaskApi } from '../modules/project/projectTaskApi'

export function TaskReminderBell() {
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: ['task-reminders'],
    queryFn: projectTaskApi.unreadReminders,
    refetchInterval: 60_000,
  })
  const reminders = Array.isArray(query.data) ? query.data : []
  const readMutation = useMutation({
    mutationFn: projectTaskApi.readReminder,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['task-reminders'] }),
  })

  const content = reminders.length === 0
    ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无任务提醒" />
    : (
      <div className="task-reminder-list">
        {reminders.map(reminder => (
          <div className="task-reminder-item" key={reminder.id}>
            <Typography.Text strong>{reminder.taskTitle}</Typography.Text>
            <Typography.Text type="secondary">{reminder.projectName}</Typography.Text>
            {reminder.dueAt && (
              <Typography.Text type="secondary">
                截止：{dayjs(reminder.dueAt).format('YYYY-MM-DD HH:00')}
              </Typography.Text>
            )}
            <Space>
              <Link to={`/projects/${reminder.projectId}?tab=tasks&taskId=${reminder.taskId}`}>
                查看任务
              </Link>
              <Button
                type="link"
                size="small"
                icon={<CheckOutlined />}
                loading={readMutation.isPending && readMutation.variables === reminder.id}
                onClick={() => readMutation.mutate(reminder.id)}
              >
                标记已读
              </Button>
            </Space>
          </div>
        ))}
      </div>
    )

  return (
    <Popover title="任务提醒" content={content} trigger="click" placement="bottomRight">
      <Badge count={reminders.length} size="small">
        <Button
          type="text"
          icon={<BellOutlined />}
          aria-label={`任务提醒，${reminders.length}条未读`}
        />
      </Badge>
    </Popover>
  )
}
