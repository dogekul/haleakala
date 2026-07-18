import { SaveOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Button, Card, Col, Form, Input, Row, Tag, message } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { useAuth } from '../../app/AuthProvider'
import { adminApi } from './adminApi'
import type { AiConfigurationInput, AiConnectionTest } from './types'
import { PageHeading } from './UsersTeamsPage'

const sourceLabels = {
  ENVIRONMENT: '环境变量', ORGANIZATION: '组织配置', MIXED: '混合配置',
}

function validateBaseUrl(_: unknown, value?: string) {
  if (!value?.trim()) return Promise.resolve()
  const candidate = value.trim()
  try {
    const url = new URL(candidate)
    if (
      /^https?:\/\/[^/?#@\\\s]+(?:\/|\/v1)?$/i.test(candidate)
      && ['http:', 'https:'].includes(url.protocol)
      && !url.username
      && !url.password
      && !url.search
      && !url.hash
      && ['', '/', '/v1'].includes(url.pathname)
    ) return Promise.resolve()
  } catch {
    // The validation message below covers malformed URLs too.
  }
  return Promise.reject(new Error('请输入 HTTP(S) 服务地址（仅支持根路径或 /v1）'))
}

export function AiServicePage() {
  const { me } = useAuth()
  if (!me) return null
  return <OrganizationAiService key={me.organizationId} organizationId={me.organizationId} />
}

function OrganizationAiService({ organizationId }: { organizationId: number }) {
  const client = useQueryClient()
  const [form] = Form.useForm<AiConfigurationInput>()
  const [connectionTest, setConnectionTest] = useState<AiConnectionTest>()
  const configurationDirty = useRef(false)
  const configurationQueryKey = ['ai-configuration', organizationId] as const
  const configuration = useQuery({
    queryKey: configurationQueryKey,
    queryFn: adminApi.aiConfiguration,
  })
  useEffect(() => {
    if (!configuration.data || configurationDirty.current) return
    form.setFieldsValue({
      baseUrl: configuration.data.baseUrl,
      model: configuration.data.model,
      apiKey: '',
    })
  }, [configuration.data, form])

  const testConfiguration = useMutation({
    mutationFn: async () => {
      const draft = await form.validateFields()
      return { draft, result: await adminApi.testAiConfiguration(draft) }
    },
    onMutate: () => setConnectionTest(undefined),
    onSuccess: ({ draft, result }) => {
      const current = form.getFieldsValue()
      if (current.baseUrl === draft.baseUrl
        && current.model === draft.model
        && current.apiKey === draft.apiKey) setConnectionTest(result)
    },
    onError: error => {
      if (error instanceof Error) message.error(error.message)
    },
  })
  const saveConfiguration = useMutation({
    mutationFn: adminApi.saveAiConfiguration,
    onSuccess: async () => {
      configurationDirty.current = false
      form.setFieldsValue({ apiKey: '' })
      await client.invalidateQueries({ queryKey: configurationQueryKey })
      message.success('AI 服务配置已保存')
    },
    onError: (error: Error) => message.error(error.message),
  })

  return <section>
    <PageHeading
      title="AI 服务"
      description="配置组织级 AI 服务连接；API Key 仅在提交时发送，不会在页面中回显。"
    />
    {configuration.error && <Alert
      showIcon
      type="error"
      message="AI 服务配置加载失败"
      description={(configuration.error as Error).message}
    />}
    <Card className="admin-surface" loading={configuration.isLoading} title="AI 服务配置">
      <Form
        form={form}
        layout="vertical"
        requiredMark="optional"
        onValuesChange={() => {
          configurationDirty.current = true
          setConnectionTest(undefined)
        }}
        onFinish={values => saveConfiguration.mutate(values)}
      >
        <Row gutter={16}>
          <Col xs={24} lg={12}>
            <Form.Item
              label="Base URL"
              name="baseUrl"
              rules={[
                { required: true, whitespace: true, message: '请输入 Base URL' },
                { validator: validateBaseUrl },
              ]}
            >
              <Input disabled={saveConfiguration.isPending} placeholder="https://api.example.com/v1" />
            </Form.Item>
          </Col>
          <Col xs={24} lg={12}>
            <Form.Item
              label="模型"
              name="model"
              rules={[{ required: true, whitespace: true, message: '请输入模型名称' }]}
            >
              <Input disabled={saveConfiguration.isPending} placeholder="qwen-plus" />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={16}>
          <Col xs={24} lg={12}>
            <Form.Item label="API Key" name="apiKey" extra="留空则保持不变">
              <Input.Password
                aria-label="API Key"
                autoComplete="new-password"
                disabled={saveConfiguration.isPending}
              />
            </Form.Item>
          </Col>
          <Col xs={24} lg={12}>
            <div className="ai-service-status">
              {configuration.data && <Tag color={configuration.data.apiKeyConfigured
                ? 'success'
                : 'default'}>
                API Key {configuration.data.apiKeyConfigured ? '已配置' : '未配置'}
              </Tag>}
              {configuration.data?.source && <Tag>
                配置来源：{sourceLabels[configuration.data.source]}
              </Tag>}
            </div>
          </Col>
        </Row>
        {connectionTest && <Alert
          showIcon
          type="success"
          message={`连接测试成功 · ${connectionTest.model}`}
        />}
        <div className="ai-service-actions">
          <Button
            aria-label="测试连接"
            disabled={testConfiguration.isPending}
            loading={testConfiguration.isPending}
            onClick={() => testConfiguration.mutate()}
          >测试连接</Button>
          <Button
            aria-label="保存配置"
            disabled={saveConfiguration.isPending}
            type="primary"
            icon={<SaveOutlined />}
            loading={saveConfiguration.isPending}
            onClick={() => form.submit()}
          >保存配置</Button>
        </div>
      </Form>
    </Card>
  </section>
}
