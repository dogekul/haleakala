import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Button, Drawer, Form, Select, Space, Typography, message } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useAuth } from '../../app/AuthProvider'
import { ApiError } from '../../services/api'
import { productApi } from '../product/productApi'
import { requirementApi } from './requirementApi'
import type { Requirement } from './types'

type CoverageRow = { featureId: number; coverageType: 'FULL' | 'PARTIAL' }

export function FeatureCoverageDrawer({ requirement, onClose }: { requirement?: Requirement; onClose(): void }) {
  const [form] = Form.useForm<{ entries: CoverageRow[] }>()
  const client = useQueryClient()
  const { me } = useAuth()
  const [candidateCreated, setCandidateCreated] = useState(false)
  const canReadProduct = me?.permissions.includes('product:read') ?? false
  const canEdit = canReadProduct && (me?.permissions.includes('requirement:write') ?? false)
  const canCreateCandidate = me?.permissions.some(permission => permission === 'requirement:write'
    || permission === 'standardization:write') ?? false
  const requirementId = requirement?.id
  const productId = requirement?.productId
  const coverage = useQuery({
    queryKey: ['requirement-coverage', requirementId],
    queryFn: () => requirementApi.coverage(requirementId!),
    enabled: Boolean(requirementId),
  })
  const features = useQuery({
    queryKey: ['product-features', productId],
    queryFn: () => productApi.features(productId!),
    enabled: Boolean(requirementId && productId && canReadProduct),
  })

  useEffect(() => {
    form.resetFields()
    setCandidateCreated(false)
  }, [form, requirementId])
  useEffect(() => {
    const value = coverage.data
    if (value && value.requirementId === requirementId) {
      form.setFieldsValue({ entries: value.entries.map(({ featureId, coverageType }) => ({ featureId, coverageType })) })
    }
  }, [coverage.data, form, requirementId])

  const options = useMemo(() => {
    const values = new Map<number, { value: number; label: string }>()
    coverage.data?.entries.forEach(item => values.set(item.featureId, {
      value: item.featureId, label: `${item.featureCode} · ${item.featureName}`,
    }))
    features.data?.forEach(item => values.set(item.id, { value: item.id, label: `${item.code} · ${item.name}` }))
    return [...values.values()]
  }, [coverage.data, features.data])
  const rows = Form.useWatch('entries', form) ?? []
  const hasFullCoverage = rows.some(item => item?.coverageType === 'FULL')
  const invalidate = async () => {
    await Promise.all([
      client.invalidateQueries({ queryKey: ['requirements'] }),
      client.invalidateQueries({ queryKey: ['requirement-coverage', requirementId], exact: true }),
      client.invalidateQueries({ queryKey: ['standardization-debts'] }),
      client.invalidateQueries({ queryKey: ['product-coverage', productId], exact: true }),
    ])
  }
  const save = useMutation({
    mutationFn: (values: { entries?: CoverageRow[] }) => requirementApi.replaceCoverage(requirementId!, values.entries ?? []),
    onSuccess: async () => { await invalidate(); message.success('功能覆盖已保存'); onClose() },
    onError: (error: Error) => message.error(error.message),
  })
  const createCandidate = useMutation({
    mutationFn: () => requirementApi.createStandardizationCandidate(requirementId!),
    onSuccess: async () => { setCandidateCreated(true); await invalidate(); message.success('已加入标准化候选') },
    onError: (error: Error) => {
      if (error instanceof ApiError && error.status === 409 && error.message.includes('已进入标准化候选')) {
        setCandidateCreated(true)
      }
      message.error(error.message)
    },
  })

  const candidateButton = coverage.isSuccess && !hasFullCoverage && canCreateCandidate
    ? <Button disabled={candidateCreated} loading={createCandidate.isPending} onClick={() => createCandidate.mutate()}>
      {candidateCreated ? '已加入标准化候选' : '加入标准化候选'}
    </Button> : null
  return <Drawer width={680} title="功能覆盖" open={Boolean(requirement)} onClose={onClose}
    extra={<Space>{candidateButton}{canEdit && <Button type="primary" loading={save.isPending}
      onClick={() => form.submit()}>保存覆盖</Button>}</Space>}>
    {requirement && <>
      <Typography.Title level={5}>{requirement.code} · {requirement.title}</Typography.Title>
      <Typography.Paragraph type="secondary">一条需求可关联多个产品功能，完全覆盖表示无需额外产品化工作。</Typography.Paragraph>
    </>}
    {(coverage.error || features.error) && <Alert type="error" showIcon message={(coverage.error || features.error)?.message} />}
    {!canEdit && <Alert type="info" showIcon message="当前为只读模式" description="需要需求写权限和产品读权限才能编辑功能覆盖。" />}
    <Form form={form} layout="vertical" onFinish={save.mutate} disabled={!canEdit}>
      <Form.List name="entries">
        {(fields, { add, remove }) => <Space direction="vertical" size="small" style={{ width: '100%' }}>
          {fields.map((field, index) => <Space key={field.key} align="start" style={{ display: 'flex' }}>
            <Form.Item label="产品功能" name={[field.name, 'featureId']} style={{ width: 360 }} rules={[
              { required: true, message: '请选择产品功能' },
              { validator: (_, value) => value && form.getFieldValue('entries')?.filter((item: CoverageRow) => item?.featureId === value).length > 1
                ? Promise.reject(new Error('不能重复选择产品功能')) : Promise.resolve() },
            ]}>
              <Select virtual={false} showSearch optionFilterProp="label" loading={features.isLoading} options={options} />
            </Form.Item>
            <Form.Item label="覆盖方式" name={[field.name, 'coverageType']}
              rules={[{ required: true, message: '请选择覆盖方式' }]}>
              <Select virtual={false} style={{ width: 150 }} options={[
                { value: 'FULL', label: '完全覆盖' }, { value: 'PARTIAL', label: '部分覆盖' },
              ]} />
            </Form.Item>
            {canEdit && <Button aria-label={`删除功能 ${index + 1}`} type="text" danger icon={<DeleteOutlined />}
              style={{ marginTop: 30 }} onClick={() => remove(field.name)} />}
          </Space>)}
          {canEdit && <Button block type="dashed" icon={<PlusOutlined />} onClick={() => add({ coverageType: 'PARTIAL' })}>添加功能</Button>}
        </Space>}
      </Form.List>
    </Form>
  </Drawer>
}
