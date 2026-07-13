import { LockOutlined, SafetyCertificateOutlined, UserOutlined } from '@ant-design/icons'
import { Alert, Button, Form, Input, Typography } from 'antd'
import { useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { ApiError } from '../services/api'
import { useAuth } from './AuthProvider'

export function LoginPage() {
  const { me, login } = useAuth()
  const location = useLocation()
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  if (me) return <Navigate to={(location.state as { from?: string } | null)?.from ?? '/dashboard'} replace />

  const submit = async (values: { username: string; password: string }) => {
    setSubmitting(true)
    setError('')
    try { await login(values.username, values.password) }
    catch (reason) { setError(reason instanceof ApiError ? reason.message : '登录失败，请稍后重试') }
    finally { setSubmitting(false) }
  }

  return (
    <div className="login-page">
      <section className="login-story">
        <div className="login-brand"><span>鹿</span><strong>智鹿交付</strong></div>
        <div className="login-copy">
          <span className="eyebrow">DELIVERY OPERATING SYSTEM</span>
          <Typography.Title>让每一次交付，都沉淀为下一次的标准能力。</Typography.Title>
          <p>以项目七阶段串联需求、工程、上线与标准化，用数据和自动化推动交付飞轮。</p>
          <div className="login-metrics">
            <div><strong>7</strong><span>项目阶段</span></div>
            <div><strong>28</strong><span>核心功能</span></div>
            <div><strong>6</strong><span>角色协同</span></div>
          </div>
        </div>
        <small>交付范式 v2.0 · 内部项目管理平台</small>
      </section>
      <section className="login-panel">
        <div className="login-card">
          <Typography.Title level={2}>欢迎回来</Typography.Title>
          <Typography.Paragraph type="secondary">登录后进入你的交付工作台</Typography.Paragraph>
          {error && <Alert type="error" showIcon message={error} />}
          <Form layout="vertical" size="large" onFinish={submit} requiredMark={false}>
            <Form.Item label="账号" name="username" rules={[{ required: true, message: '请输入账号' }]}>
              <Input prefix={<UserOutlined />} placeholder="请输入企业账号" autoComplete="username" />
            </Form.Item>
            <Form.Item label="密码" name="password" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password prefix={<LockOutlined />} placeholder="请输入密码" autoComplete="current-password" />
            </Form.Item>
            <Button block type="primary" htmlType="submit" loading={submitting}>登录</Button>
          </Form>
          <div className="sso-divider"><span>或</span></div>
          <Button block icon={<SafetyCertificateOutlined />} href="/oauth2/authorization/enterprise">
            企业统一登录
          </Button>
          <p className="security-note">登录即表示你同意遵守公司信息安全与数据使用规范</p>
        </div>
      </section>
    </div>
  )
}
