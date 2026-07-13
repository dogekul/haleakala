import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Button, Drawer, Form, Input, InputNumber, Select, message } from 'antd'
import { useEffect } from 'react'
import { useAuth } from '../../app/AuthProvider'
import { productApi } from '../product/productApi'
import { standardizationApi } from './standardizationApi'
import type { StandardizationDebt } from './types'

interface ConversionForm {
  productId: number
  moduleId: number
  productVersionId?: number
  code: string
  name: string
  description?: string
  ownerUserId?: number
}

export function ConvertToFeatureDrawer({ debt, defaultProductId, onClose }: {
  debt?: StandardizationDebt
  defaultProductId?: number
  onClose(): void
}) {
  const [form] = Form.useForm<ConversionForm>()
  const client = useQueryClient()
  const { me } = useAuth()
  const canReadProduct = me?.permissions.includes('product:read') ?? false
  const canWrite = canReadProduct && (me?.permissions.includes('product:write') ?? false)
    && (me?.permissions.includes('standardization:write') ?? false)
  const productId = Form.useWatch('productId', form)
  const products = useQuery({
    queryKey: ['products'], queryFn: productApi.products,
    enabled: Boolean(debt && canReadProduct),
  })
  const modules = useQuery({
    queryKey: ['product-modules', productId], queryFn: () => productApi.modules(productId!),
    enabled: Boolean(debt && productId && canReadProduct),
  })
  const versions = useQuery({
    queryKey: ['product-versions', productId], queryFn: () => productApi.versions(productId!),
    enabled: Boolean(debt && productId && canReadProduct),
  })

  useEffect(() => {
    form.resetFields()
    if (debt) form.setFieldsValue({ productId: defaultProductId, name: debt.title })
  }, [debt, defaultProductId, form])

  const convert = useMutation({
    mutationFn: (values: ConversionForm) => standardizationApi.convertToFeature(debt!.id, {
      ...values,
      description: values.description?.trim() || undefined,
      ownerUserId: values.ownerUserId || undefined,
      version: debt!.version,
    }),
    onSuccess: async (_, values) => {
      const invalidations = [
        client.invalidateQueries({ queryKey: ['standardization-debts'] }),
        client.invalidateQueries({ queryKey: ['requirement-coverage'] }),
        client.invalidateQueries({ queryKey: ['product-features', values.productId], exact: true }),
        client.invalidateQueries({ queryKey: ['product-coverage', values.productId], exact: true }),
      ]
      if (values.productVersionId) invalidations.push(client.invalidateQueries({
        queryKey: ['product-manifest', values.productId, values.productVersionId], exact: true,
      }))
      await Promise.all(invalidations)
      message.success('已转为产品功能')
      onClose()
    },
    onError: (error: Error) => message.error(error.message),
  })
  const error = products.error || modules.error || versions.error

  return <Drawer width={620} title="转为产品功能" open={Boolean(debt)} onClose={onClose}
    extra={canWrite && <Button type="primary" loading={convert.isPending} onClick={() => form.submit()}>创建功能</Button>}>
    {error && <Alert type="error" showIcon message={error.message} />}
    {!canWrite && <Alert type="info" showIcon message="当前为只读模式"
      description="需要标准化写权限以及产品读写权限才能创建功能。" />}
    <Form form={form} layout="vertical" disabled={!canWrite} onFinish={convert.mutate}>
      <Form.Item label="目标产品" name="productId" rules={[{ required: true, message: '请选择目标产品' }]}>
        <Select virtual={false} showSearch optionFilterProp="label" loading={products.isLoading}
          options={products.data?.map(item => ({ value: item.id, label: item.name }))}
          onChange={() => form.setFieldsValue({ moduleId: undefined, productVersionId: undefined })} />
      </Form.Item>
      <Form.Item label="目标模块" name="moduleId" rules={[{ required: true, message: '请选择目标模块' }]}>
        <Select virtual={false} showSearch optionFilterProp="label" loading={modules.isLoading} disabled={!canWrite || !productId}
          options={modules.data?.map(item => ({ value: item.id, label: `${item.code} · ${item.name}` }))} />
      </Form.Item>
      <Form.Item label="加入规划版本" name="productVersionId" extra="可选；只显示规划中版本">
        <Select virtual={false} allowClear loading={versions.isLoading} disabled={!canWrite || !productId}
          options={versions.data?.filter(item => item.status === 'PLANNING').map(item => ({ value: item.id, label: item.versionName }))} />
      </Form.Item>
      <Form.Item label="功能编码" name="code" rules={[{ required: true, whitespace: true, message: '请输入功能编码' }]}>
        <Input placeholder="AR-DIFF" />
      </Form.Item>
      <Form.Item label="功能名称" name="name" rules={[{ required: true, whitespace: true, message: '请输入功能名称' }]}>
        <Input />
      </Form.Item>
      <Form.Item label="功能说明" name="description"><Input.TextArea rows={4} /></Form.Item>
      <Form.Item label="负责人 ID（可选）" name="ownerUserId"><InputNumber min={1} precision={0} style={{ width: '100%' }} /></Form.Item>
    </Form>
  </Drawer>
}
