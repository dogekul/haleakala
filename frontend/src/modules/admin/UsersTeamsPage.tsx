import { DeleteOutlined, PlusOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Card, Col, Form, Input, Modal, Popconfirm, Row, Select, Space, Statistic, Table, Tag, Typography, message } from 'antd'
import { useEffect, useState } from 'react'
import { adminApi } from './adminApi'
import { AdminQueryAlert } from './AdminQueryAlert'
import type { AdminUser, Team } from './types'

export function UsersTeamsPage() {
  const users = useQuery({ queryKey: ['admin-users'], queryFn: adminApi.users })
  const teams = useQuery({ queryKey: ['admin-teams'], queryFn: adminApi.teams })
  const roles = useQuery({ queryKey: ['admin-roles'], queryFn: adminApi.roles })
  const [editingUser, setEditingUser] = useState<AdminUser | null | undefined>()
  const [editingTeam, setEditingTeam] = useState<Team | null | undefined>()
  const client = useQueryClient()
  const status = useMutation({
    mutationFn: ({ id, value }: { id: number; value: AdminUser['status'] }) => adminApi.userStatus(id, value),
    onSuccess: async () => { await client.invalidateQueries({ queryKey: ['admin-users'] }); message.success('用户状态已更新') },
    onError: (error: Error) => message.error(error.message),
  })
  const removeUser = useMutation({
    mutationFn: adminApi.deleteUser,
    onSuccess: async () => { await client.invalidateQueries({ queryKey: ['admin-users'] }); message.success('用户已删除') },
    onError: (error: Error) => message.error(error.message),
  })
  const removeTeam = useMutation({
    mutationFn: adminApi.deleteTeam,
    onSuccess: async () => { await client.invalidateQueries({ queryKey: ['admin-teams'] }); message.success('团队已删除') },
    onError: (error: Error) => message.error(error.message),
  })

  return <section>
    <PageHeading title="用户与团队" description="管理组织成员、归属团队和业务角色，停用用户不会破坏历史交付记录。"
      action={<Space><Button icon={<TeamOutlined />} onClick={() => setEditingTeam(null)}>新建团队</Button><Button type="primary" icon={<PlusOutlined />} onClick={() => setEditingUser(null)}>新建用户</Button></Space>} />
    <AdminQueryAlert errors={[users.error, teams.error, roles.error]} onRetry={() => { void Promise.all([users.refetch(), teams.refetch(), roles.refetch()]) }} />
    <Row gutter={14} className="admin-stats">
      <Col span={8}><Card><Statistic prefix={<UserOutlined />} title="组织用户" value={users.data?.length ?? 0} /></Card></Col>
      <Col span={8}><Card><Statistic prefix={<TeamOutlined />} title="启用团队" value={teams.data?.filter(item => item.enabled).length ?? 0} /></Card></Col>
      <Col span={8}><Card><Statistic title="已启用用户" value={users.data?.filter(item => item.status === 'ACTIVE').length ?? 0} suffix={`/ ${users.data?.length ?? 0}`} /></Card></Col>
    </Row>
    <Card className="admin-surface" title="用户" extra={`${users.data?.length ?? 0} 人`}>
      <Table rowKey="id" loading={users.isLoading} dataSource={users.data} pagination={{ pageSize: 8 }} columns={[
        { title: '成员', key: 'member', render: (_, item: AdminUser) => <div className="admin-main-cell"><strong>{item.displayName}</strong><span>{item.username} · {item.email || '未设置邮箱'}</span></div> },
        { title: '团队', dataIndex: 'primaryTeamName', render: (value: string | null) => value || <span className="muted">未分配</span> },
        { title: '角色', dataIndex: 'roles', render: (values: string[]) => <Space size={[0, 4]} wrap>{values.map(value => <Tag key={value}>{roleName(value, roles.data)}</Tag>)}</Space> },
        { title: '状态', dataIndex: 'status', width: 90, render: (value: string) => <Tag color={value === 'ACTIVE' ? 'success' : 'default'}>{value === 'ACTIVE' ? '启用' : '停用'}</Tag> },
        { title: '操作', width: 230, render: (_, item: AdminUser) => <Space size={2}>
          <Button type="link" size="small" onClick={() => setEditingUser(item)}>编辑</Button>
          <Popconfirm title={`确认${item.status === 'ACTIVE' ? '停用' : '启用'}该用户？`} okText="确认" cancelText="取消" onConfirm={() => status.mutate({ id: item.id, value: item.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' })}><Button type="link" size="small">{item.status === 'ACTIVE' ? '停用' : '启用'}</Button></Popconfirm>
          <Popconfirm title={`确认删除用户“${item.displayName}”？`} description="删除后无法恢复。已有业务记录的用户请改为停用。" okText="删除" cancelText="取消" okButtonProps={{ danger: true }} onConfirm={() => removeUser.mutate(item.id)}><Button type="link" danger size="small" icon={<DeleteOutlined />} loading={removeUser.isPending && removeUser.variables === item.id} aria-label={`删除用户${item.displayName}`}>删除</Button></Popconfirm>
        </Space> },
      ]} />
    </Card>
    <Card className="admin-surface" title="团队结构" extra={`${teams.data?.length ?? 0} 个团队`}>
      <Table rowKey="id" loading={teams.isLoading} dataSource={teams.data} pagination={false} columns={[
        { title: '团队名称', dataIndex: 'name' }, { title: '编码', dataIndex: 'code' },
        { title: '上级团队', dataIndex: 'parentId', render: (value: number | null) => teams.data?.find(item => item.id === value)?.name ?? '—' },
        { title: '状态', dataIndex: 'enabled', render: (value: boolean) => <Tag color={value ? 'success' : 'default'}>{value ? '启用' : '停用'}</Tag> },
        { title: '操作', width: 150, render: (_, item: Team) => <Space size={2}>
          <Button type="link" size="small" onClick={() => setEditingTeam(item)}>编辑</Button>
          <Popconfirm title={`确认删除团队“${item.name}”？`} description="请先移出团队成员并删除下级团队。" okText="删除" cancelText="取消" okButtonProps={{ danger: true }} onConfirm={() => removeTeam.mutate(item.id)}><Button type="link" danger size="small" icon={<DeleteOutlined />} loading={removeTeam.isPending && removeTeam.variables === item.id} aria-label={`删除团队${item.name}`}>删除</Button></Popconfirm>
        </Space> },
      ]} />
    </Card>
    <UserEditor value={editingUser} teams={teams.data ?? []} roles={roles.data ?? []} onClose={() => setEditingUser(undefined)} />
    <TeamEditor value={editingTeam} teams={teams.data ?? []} onClose={() => setEditingTeam(undefined)} />
  </section>
}

function UserEditor({ value, teams, roles, onClose }: { value: AdminUser | null | undefined; teams: Team[]; roles: { code: string; name: string }[]; onClose(): void }) {
  const [form] = Form.useForm()
  const client = useQueryClient()
  useEffect(() => {
    if (value !== undefined) {
      form.resetFields()
      form.setFieldsValue(value ?? { status: 'ACTIVE', roleCodes: [] })
    }
    if (value) form.setFieldValue('roleCodes', value.roles)
  }, [form, value])
  const save = useMutation({ mutationFn: (input: Record<string, unknown>) => adminApi.saveUser(value?.id, input), onSuccess: async () => { await client.invalidateQueries({ queryKey: ['admin-users'] }); onClose(); form.resetFields(); message.success(value ? '用户已更新' : '用户已创建') }, onError: (error: Error) => message.error(error.message) })
  return <Modal title={value ? '编辑用户' : '新建用户'} open={value !== undefined} onCancel={onClose} okText="保存" cancelText="取消" confirmLoading={save.isPending} onOk={() => form.submit()} destroyOnHidden>
    <Form form={form} layout="vertical" preserve={false} onFinish={save.mutate}>
      {!value && <><Form.Item label="用户名" name="username" rules={[{ required: true }]}><Input /></Form.Item><Form.Item label="初始密码" name="password" rules={[{ required: true, min: 8 }]}><Input.Password /></Form.Item></>}
      <Row gutter={12}><Col span={12}><Form.Item label="显示名称" name="displayName" rules={[{ required: true }]}><Input /></Form.Item></Col><Col span={12}><Form.Item label="邮箱" name="email" rules={[{ type: 'email' }]}><Input /></Form.Item></Col></Row>
      <Form.Item label="所属团队" name="primaryTeamId"><Select allowClear options={teams.filter(item => item.enabled).map(item => ({ value: item.id, label: item.name }))} /></Form.Item>
      <Form.Item label="角色" name="roleCodes" rules={[{ required: true, message: '至少选择一个角色' }]}><Select mode="multiple" options={roles.map(item => ({ value: item.code, label: item.name }))} /></Form.Item>
      {value && <Form.Item label="状态" name="status" rules={[{ required: true }]}><Select options={[{ value: 'ACTIVE', label: '启用' }, { value: 'DISABLED', label: '停用' }]} /></Form.Item>}
    </Form>
  </Modal>
}

function TeamEditor({ value, teams, onClose }: { value: Team | null | undefined; teams: Team[]; onClose(): void }) {
  const [form] = Form.useForm()
  const client = useQueryClient()
  useEffect(() => { if (value !== undefined) { form.resetFields(); form.setFieldsValue(value ?? { enabled: true }) } }, [form, value])
  const save = useMutation({ mutationFn: (input: Record<string, unknown>) => adminApi.saveTeam(value?.id, input), onSuccess: async () => { await client.invalidateQueries({ queryKey: ['admin-teams'] }); onClose(); form.resetFields(); message.success(value ? '团队已更新' : '团队已创建') }, onError: (error: Error) => message.error(error.message) })
  return <Modal title={value ? '编辑团队' : '新建团队'} open={value !== undefined} onCancel={onClose} okText="保存" cancelText="取消" confirmLoading={save.isPending} onOk={() => form.submit()} destroyOnHidden>
    <Form form={form} layout="vertical" preserve={false} onFinish={save.mutate}>
      <Form.Item label="团队名称" name="name" rules={[{ required: true }]}><Input /></Form.Item><Form.Item label="团队编码" name="code" rules={[{ required: true }]}><Input /></Form.Item>
      <Form.Item label="上级团队" name="parentId"><Select allowClear options={teams.filter(item => item.id !== value?.id && item.enabled).map(item => ({ value: item.id, label: item.name }))} /></Form.Item>
      {value && <Form.Item label="状态" name="enabled"><Select options={[{ value: true, label: '启用' }, { value: false, label: '停用' }]} /></Form.Item>}
    </Form>
  </Modal>
}

export function PageHeading({ title, description, action }: { title: string; description: string; action?: React.ReactNode }) {
  return <div className="admin-heading"><div><span className="eyebrow dark">SYSTEM ADMINISTRATION</span><Typography.Title level={2}>{title}</Typography.Title><Typography.Paragraph>{description}</Typography.Paragraph></div>{action}</div>
}

function roleName(code: string, roles: { code: string; name: string }[] | undefined) {
  return roles?.find(item => item.code === code)?.name ?? code
}
