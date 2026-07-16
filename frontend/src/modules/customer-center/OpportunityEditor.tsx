import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Col, Drawer, Form, Input, InputNumber, Row, Select, message } from 'antd'
import { useEffect } from 'react'
import { customerApi } from '../customer/customerApi'
import { projectApi } from '../project/projectApi'
import { crmApi } from './crmApi'
import type { Opportunity, OpportunityInput } from './types'

export function OpportunityEditor({ value, open, onClose }: {
  value?: Opportunity; open: boolean; onClose: () => void
}) {
  const [form] = Form.useForm<OpportunityInput>()
  const client = useQueryClient()
  const customers = useQuery({ queryKey: ['customers', 'active-options'], queryFn: () => customerApi.list({ status: 'ACTIVE' }), enabled: open })
  const products = useQuery({ queryKey: ['products', 'bindable'], queryFn: projectApi.bindableProducts, enabled: open })
  const owners = useQuery({ queryKey: ['crm-owner-options'], queryFn: crmApi.ownerOptions, enabled: open })
  const productId = Form.useWatch('productId', form)
  const versions = useQuery({ queryKey: ['product-versions', productId], queryFn: () => projectApi.bindableVersions(productId!), enabled: open && Boolean(productId) })

  useEffect(() => {
    if (!open) return
    form.resetFields()
    form.setFieldsValue(value ? { ...value } : { amount: 0 })
  }, [form, open, value])

  const save = useMutation({
    mutationFn: (input: OpportunityInput) => value
      ? crmApi.updateOpportunity(value.id, { ...input, version: value.version })
      : crmApi.createOpportunity(input),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ['opportunities'] })
      message.success(value ? '商机已更新' : '商机已创建')
      onClose()
    },
    onError: (error: Error) => message.error(error.message),
  })

  const ownerOptions = (owners.data ?? []).map(item => ({ value: item.id, label: item.displayName }))
  return <Drawer title={value ? '编辑商机' : '新建商机'} open={open} width={680} onClose={onClose}
    extra={<Button type="primary" aria-label="保存商机" loading={save.isPending} onClick={() => form.submit()}>保存</Button>}>
    <Form form={form} layout="vertical" onFinish={save.mutate}>
      <Form.Item name="customerId" label="客户" rules={[{ required: true, message: '请选择客户' }]}>
        <Select showSearch optionFilterProp="label" virtual={false} loading={customers.isLoading}
          options={(customers.data ?? []).map(item => ({ value: item.id, label: item.name }))} />
      </Form.Item>
      <Form.Item name="title" label="商机名称" rules={[{ required: true, message: '请输入商机名称' }]}>
        <Input maxLength={180} />
      </Form.Item>
      <Row gutter={12}><Col span={12}><Form.Item name="amount" label="预计金额" rules={[{ required: true }]}>
        <InputNumber min={0} precision={2} style={{ width: '100%' }} />
      </Form.Item></Col><Col span={12}><Form.Item name="commercialOwnerUserId" label="商务负责人">
        <Select allowClear virtual={false} options={ownerOptions} />
      </Form.Item></Col></Row>
      <Row gutter={12}><Col span={12}><Form.Item name="productId" label="产品">
        <Select allowClear virtual={false} loading={products.isLoading}
          onChange={() => form.setFieldValue('productVersionId', undefined)}
          options={(products.data ?? []).map(item => ({ value: item.id, label: item.name }))} />
      </Form.Item></Col><Col span={12}><Form.Item name="productVersionId" label="产品版本">
        <Select allowClear virtual={false} loading={versions.isLoading}
          options={(versions.data ?? []).map(item => ({ value: item.id, label: item.versionName }))} />
      </Form.Item></Col></Row>
      <Row gutter={12}><Col span={8}><Form.Item name="solutionOwnerUserId" label="方案负责人"><Select allowClear virtual={false} options={ownerOptions} /></Form.Item></Col>
        <Col span={8}><Form.Item name="projectManagerUserId" label="项目经理"><Select allowClear virtual={false} options={ownerOptions} /></Form.Item></Col>
        <Col span={8}><Form.Item name="operationOwnerUserId" label="运营负责人"><Select allowClear virtual={false} options={ownerOptions} /></Form.Item></Col></Row>
      <Form.Item name="note" label="备注"><Input.TextArea rows={4} maxLength={1000} showCount /></Form.Item>
    </Form>
  </Drawer>
}
