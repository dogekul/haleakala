import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Drawer, Form, Input, Select, message } from 'antd'
import { useEffect } from 'react'
import { customerApi } from '../customer/customerApi'
import { projectApi } from '../project/projectApi'
import { crmApi } from './crmApi'
import type { CustomerOperation, OperationInput } from './types'

export function OperationEditor({ value, open, onClose }: { value?: CustomerOperation; open: boolean; onClose: () => void }) {
  const [form] = Form.useForm<OperationInput>()
  const client = useQueryClient()
  const customers = useQuery({ queryKey: ['customers', 'active-options'], queryFn: () => customerApi.list({ status: 'ACTIVE' }), enabled: open })
  const owners = useQuery({ queryKey: ['crm-owner-options'], queryFn: crmApi.ownerOptions, enabled: open })
  const projects = useQuery({ queryKey: ['projects'], queryFn: projectApi.list, enabled: open })
  const opportunities = useQuery({ queryKey: ['opportunities', 'operation-options'], queryFn: () => crmApi.opportunities('?status=WON'), enabled: open })
  const customerId = Form.useWatch('customerId', form)
  useEffect(() => { if (open) { form.resetFields(); form.setFieldsValue(value ? { ...value } : {}) } }, [form, open, value])
  const save = useMutation({ mutationFn: (input: OperationInput) => value
    ? crmApi.updateOperation(value.id, { ...input, version: value.version }) : crmApi.createOperation(input),
    onSuccess: async () => { await client.invalidateQueries({ queryKey: ['operations'] }); message.success(value ? '运营记录已更新' : '运营记录已创建'); onClose() },
    onError: (error: Error) => message.error(error.message) })
  return <Drawer title={value ? '编辑客户运营' : '新建客户运营'} open={open} width={600} onClose={onClose}
    extra={<Button type="primary" loading={save.isPending} onClick={() => form.submit()}>保存</Button>}>
    <Form form={form} layout="vertical" onFinish={save.mutate}>
      <Form.Item name="customerId" label="客户" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" virtual={false}
        options={(customers.data ?? []).map(item => ({ value: item.id, label: item.name }))} /></Form.Item>
      <Form.Item name="title" label="运营主题" rules={[{ required: true }]}><Input maxLength={180} /></Form.Item>
      <Form.Item name="ownerUserId" label="运营负责人"><Select allowClear virtual={false}
        options={(owners.data ?? []).map(item => ({ value: item.id, label: item.displayName }))} /></Form.Item>
      <Form.Item name="opportunityId" label="来源商机"><Select allowClear virtual={false}
        options={(opportunities.data ?? []).filter(item => !customerId || item.customerId === customerId).map(item => ({ value: item.id, label: item.title }))} /></Form.Item>
      <Form.Item name="projectId" label="来源项目"><Select allowClear virtual={false}
        options={(projects.data ?? []).filter(item => !customerId || item.customerId === customerId).map(item => ({ value: item.id, label: `${item.code} · ${item.name}` }))} /></Form.Item>
    </Form>
  </Drawer>
}
