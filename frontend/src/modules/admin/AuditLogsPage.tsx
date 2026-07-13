import { SearchOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Card, DatePicker, Input, Select, Space, Table, Tag, Typography } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useState } from 'react'
import { adminApi } from './adminApi'
import type { AuditLog } from './types'
import { PageHeading } from './UsersTeamsPage'

export function AuditLogsPage() {
  const [keyword, setKeyword] = useState('')
  const [action, setAction] = useState<string>()
  const [resourceType, setResourceType] = useState<string>()
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null)
  const [page, setPage] = useState(1)
  const query = useQuery({ queryKey: ['admin-audits', keyword, action, resourceType, range?.[0]?.valueOf(), range?.[1]?.valueOf(), page], queryFn: () => adminApi.audits({ keyword, action, resourceType, from: range?.[0]?.startOf('day').format('YYYY-MM-DDTHH:mm:ss'), to: range?.[1]?.endOf('day').format('YYYY-MM-DDTHH:mm:ss'), page, pageSize: 20 }) })
  const change = (setter: (value: string | undefined) => void) => (value: string | undefined) => { setter(value); setPage(1) }
  return <section>
    <PageHeading title="审计日志" description="追踪系统管理和交付操作，支持按操作者、动作、资源与时间检索。" />
    <Card className="admin-filter"><Space wrap>
      <Input allowClear prefix={<SearchOutlined />} placeholder="操作者、资源、Trace ID" value={keyword} onChange={event => { setKeyword(event.target.value); setPage(1) }} style={{ width: 250 }} />
      <Select allowClear placeholder="动作" value={action} onChange={change(setAction)} style={{ width: 190 }} options={['CREATE', 'UPDATE', 'USER_CREATED', 'USER_UPDATED', 'USER_STATUS_UPDATED', 'TEAM_CREATED', 'TEAM_UPDATED', 'ROLE_PERMISSIONS_UPDATED', 'AGENT_JOB_SUBMITTED'].map(value => ({ value, label: value }))} />
      <Select allowClear placeholder="资源类型" value={resourceType} onChange={change(setResourceType)} style={{ width: 180 }} options={['USER', 'TEAM', 'ROLE', 'PRODUCT', 'PRODUCT_VERSION', 'SYSTEM_SETTING', 'AGENT_JOB'].map(value => ({ value, label: value }))} />
      <DatePicker.RangePicker value={range} onChange={value => { setRange(value); setPage(1) }} />
    </Space></Card>
    <Card className="admin-surface audit-table" title="操作记录" extra={`共 ${query.data?.total ?? 0} 条`}><Table rowKey="id" loading={query.isLoading} dataSource={query.data?.items} pagination={{ current: page, pageSize: 20, total: query.data?.total ?? 0, showSizeChanger: false, onChange: setPage }} columns={[
      { title: '时间', dataIndex: 'createdAt', width: 175, render: (value: string) => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '—' },
      { title: '操作者', key: 'actor', width: 140, render: (_, item: AuditLog) => <div className="admin-main-cell"><strong>{item.actorName || '系统'}</strong><span>{item.actorUserId ? `用户 #${item.actorUserId}` : '自动任务'}</span></div> },
      { title: '动作', dataIndex: 'action', width: 190, render: (value: string) => <Tag color="blue">{value}</Tag> },
      { title: '资源', key: 'resource', width: 170, render: (_, item: AuditLog) => <div className="admin-main-cell"><strong>{item.resourceType}</strong><span>#{item.resourceId || '—'}</span></div> },
      { title: '详情', dataIndex: 'details', ellipsis: true, render: (value: string | null) => value || <Typography.Text type="secondary">无</Typography.Text> },
      { title: 'Trace ID', dataIndex: 'traceId', width: 180, ellipsis: true, render: (value: string) => <Typography.Text code copyable={{ text: value }}>{value}</Typography.Text> },
    ]} /></Card>
  </section>
}
