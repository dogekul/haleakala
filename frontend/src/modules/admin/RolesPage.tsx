import { LockOutlined, SafetyCertificateOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Card, Checkbox, Col, Drawer, Empty, Row, Space, Tag, Typography, message } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { adminApi } from './adminApi'
import type { Role } from './types'
import { PageHeading } from './UsersTeamsPage'

export function RolesPage() {
  const roles = useQuery({ queryKey: ['admin-roles'], queryFn: adminApi.roles })
  const permissions = useQuery({ queryKey: ['admin-permissions'], queryFn: adminApi.permissions })
  const [editing, setEditing] = useState<Role>()
  return <section>
    <PageHeading title="角色权限" description="按业务角色分配最小必要权限；系统管理员始终保留系统管理权限。" />
    <Row gutter={[14, 14]}>
      {(roles.data ?? []).map(role => <Col span={8} key={role.id}><Card className="role-card" title={<Space><SafetyCertificateOutlined />{role.name}</Space>} extra={role.builtIn && <Tag>内置</Tag>}>
        <Typography.Paragraph>{role.description || '暂无说明'}</Typography.Paragraph>
        <div className="role-permission-count"><strong>{role.permissions.length}</strong><span>项权限</span></div>
        <Space size={[0, 4]} wrap>{role.permissions.slice(0, 4).map(code => <Tag key={code}>{permissions.data?.find(item => item.code === code)?.name ?? code}</Tag>)}{role.permissions.length > 4 && <Tag>+{role.permissions.length - 4}</Tag>}</Space>
        <Button block className="role-edit" icon={<LockOutlined />} onClick={() => setEditing(role)}>配置权限</Button>
      </Card></Col>)}
    </Row>
    {!roles.isLoading && !roles.data?.length && <Card><Empty description="暂无角色" /></Card>}
    <PermissionEditor role={editing} onClose={() => setEditing(undefined)} />
  </section>
}

function PermissionEditor({ role, onClose }: { role?: Role; onClose(): void }) {
  const permissions = useQuery({ queryKey: ['admin-permissions'], queryFn: adminApi.permissions })
  const [selected, setSelected] = useState<string[]>([])
  const client = useQueryClient()
  useEffect(() => { setSelected(role?.permissions ?? []) }, [role])
  const groups = useMemo(() => Object.entries((permissions.data ?? []).reduce<Record<string, typeof permissions.data>>((all, item) => {
    all[item.module] = [...(all[item.module] ?? []), item]
    return all
  }, {})), [permissions.data])
  const save = useMutation({ mutationFn: () => adminApi.saveRolePermissions(role!.id, selected), onSuccess: async () => { await client.invalidateQueries({ queryKey: ['admin-roles'] }); onClose(); message.success('角色权限已更新') }, onError: (error: Error) => message.error(error.message) })
  return <Drawer width={680} title={role ? `配置权限 · ${role.name}` : '配置权限'} open={Boolean(role)} onClose={onClose} extra={<Button type="primary" loading={save.isPending} onClick={() => save.mutate()}>保存权限</Button>}>
    <Typography.Paragraph type="secondary">勾选该角色可访问的功能和操作。修改会立即影响已分配此角色的用户。</Typography.Paragraph>
    {groups.map(([module, items]) => <div className="permission-group" key={module}><strong>{module}</strong><Checkbox.Group value={selected} onChange={values => setSelected(values as string[])}><Row gutter={[8, 8]}>{items?.map(item => <Col span={12} key={item.code}><Checkbox value={item.code} disabled={role?.code === 'ADMIN' && item.code === 'system:manage'}><span>{item.name}</span><small>{item.code}</small></Checkbox></Col>)}</Row></Checkbox.Group></div>)}
  </Drawer>
}
