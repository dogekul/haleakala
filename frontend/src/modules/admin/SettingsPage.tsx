import { ClockCircleOutlined, MailOutlined, SaveOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Button, Card, Col, Form, Input, InputNumber, Row, Select, Space, message } from 'antd'
import { useEffect } from 'react'
import { adminApi } from './adminApi'
import { AdminQueryAlert } from './AdminQueryAlert'
import type { SystemSettings } from './types'
import { PageHeading } from './UsersTeamsPage'

export function SettingsPage() {
  const [form] = Form.useForm<SystemSettings>()
  const query = useQuery({ queryKey: ['admin-settings'], queryFn: adminApi.settings })
  const client = useQueryClient()
  useEffect(() => { if (query.data) form.setFieldsValue(query.data) }, [form, query.data])
  const save = useMutation({ mutationFn: adminApi.saveSettings, onSuccess: async value => { form.setFieldsValue(value); await Promise.all([client.invalidateQueries({ queryKey: ['admin-settings'] }), client.invalidateQueries({ queryKey: ['runtime-settings'] })]); message.success('系统设置已保存') }, onError: (error: Error) => message.error(error.message) })
  return <section>
    <PageHeading title="系统设置" description="设置组织平台标识、默认时区和 Agent 任务执行边界。" action={<Button type="primary" icon={<SaveOutlined />} loading={save.isPending} onClick={() => form.submit()}>保存设置</Button>} />
    <AdminQueryAlert errors={[query.error]} onRetry={() => { void query.refetch() }} />
    <Row gutter={14}><Col span={16}><Card className="admin-surface" title="基础设置" loading={query.isLoading}><Form form={form} layout="vertical" onFinish={save.mutate} requiredMark="optional">
      <Row gutter={16}><Col span={12}><Form.Item label="平台名称" name="platformName" rules={[{ required: true, max: 40 }]}><Input placeholder="智鹿交付" /></Form.Item></Col><Col span={12}><Form.Item label="环境标识" name="environmentLabel" rules={[{ required: true, max: 20 }]}><Input placeholder="内部生产环境" /></Form.Item></Col></Row>
      <Row gutter={16}><Col span={12}><Form.Item label="默认时区" name="timezone" rules={[{ required: true }]}><Select showSearch options={['Asia/Shanghai', 'Asia/Hong_Kong', 'Asia/Singapore', 'UTC', 'Europe/London', 'America/New_York'].map(value => ({ value, label: value }))} /></Form.Item></Col><Col span={12}><Form.Item label="支持邮箱" name="supportEmail" rules={[{ type: 'email' }]}><Input prefix={<MailOutlined />} placeholder="support@example.com" /></Form.Item></Col></Row>
      <Form.Item label="Agent 任务超时（分钟）" name="agentTimeoutMinutes" extra="只影响新提交的任务，已运行任务保持原超时时间。" rules={[{ required: true }]}><InputNumber min={1} max={240} prefix={<ClockCircleOutlined />} style={{ width: 240 }} /></Form.Item>
    </Form></Card></Col><Col span={8}><Card className="admin-note" title="生效说明"><Space direction="vertical" size={16}><Alert showIcon type="info" message="组织级配置" description="设置仅作用于当前组织，不会影响其他租户。" /><Alert showIcon type="success" message="运行时读取" description="Agent 超时在创建任务时读取，无需重启服务。" /></Space></Card></Col></Row>
  </section>
}
