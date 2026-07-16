import { FileAddOutlined, RightOutlined, UploadOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Button, Card, Col, Drawer, Form, Input, Modal, Radio, Row, Select, Space, Tag, Typography, Upload, message } from 'antd'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../app/AuthProvider'
import { PageState } from '../../components/PageState'
import { projectApi } from '../project/projectApi'
import { crmApi } from './crmApi'
import { opportunityStages, stageLabel } from './OpportunityOverviewPage'
import type { Opportunity, OpportunityArtifact, OpportunityStage, UploadedFile } from './types'

const artifactTypes: Record<OpportunityStage, { value: string; label: string; file?: boolean }[]> = {
  LEAD: [{ value: 'RESEARCH_REPORT', label: '商机调研报告' }],
  OPPORTUNITY: [{ value: 'DECISION_MINUTES', label: '决策评审纪要' }],
  POC: [{ value: 'PRESENTATION', label: '讲解材料', file: true }, { value: 'CLIENT_REQUESTS', label: '甲方诉求清单' }, { value: 'POC_SCORE', label: 'POC 得分表' }, { value: 'GAP_ANALYSIS', label: '差距分析报告' }],
  BIDDING: [{ value: 'BID_DOCUMENT', label: '投标文件', file: true }],
  CONTRACT: [{ value: 'AWARD_NOTICE', label: '中标公示', file: true }, { value: 'CONTRACT', label: '合同', file: true }, { value: 'REVIEW_MINUTES', label: '评审会议纪要' }, { value: 'EMAIL_ARCHIVE', label: '邮件归档', file: true }, { value: 'SEALED_CONTRACT', label: '已盖章合同', file: true }],
}

export function PresaleBoardPage() {
  const { me } = useAuth()
  const canWrite = me?.permissions.includes('crm:write') ?? false
  const client = useQueryClient()
  const query = useQuery({ queryKey: ['opportunities', 'presale'], queryFn: () => crmApi.opportunities('?status=OPEN') })
  const [artifactFor, setArtifactFor] = useState<Opportunity>()
  const [artifactError, setArtifactError] = useState('')
  const [handoffFor, setHandoffFor] = useState<Opportunity>()

  const advance = useMutation({
    mutationFn: ({ item, decision }: { item: Opportunity; decision?: 'PASS' | 'REJECT' }) => crmApi.advanceOpportunity(item.id, item.version, decision),
    onSuccess: async () => { await client.invalidateQueries({ queryKey: ['opportunities'] }); message.success('商机阶段已推进') },
    onError: (error: Error, variables) => {
      if (error.message.includes('缺少必需产出物')) { setArtifactFor(variables.item); setArtifactError(error.message) }
      else message.error(error.message)
    },
  })

  const requestAdvance = (item: Opportunity, decision?: 'PASS' | 'REJECT') => {
    if (item.stage === 'CONTRACT' && decision !== 'REJECT') { setHandoffFor(item); return }
    if (decision === 'REJECT') {
      Modal.confirm({ title: '确认丢单？', content: '丢单后商机不可继续推进。', okText: '确认丢单', okButtonProps: { danger: true }, onOk: () => advance.mutate({ item, decision }) })
      return
    }
    advance.mutate({ item, decision })
  }

  return <div className="crm-page presale-page">
    <div className="page-heading compact"><div><Typography.Title level={2}>售前推进</Typography.Title>
      <Typography.Paragraph>以关口和产出物驱动商机从线索走向合同。</Typography.Paragraph></div></div>
    <PageState loading={query.isLoading} error={query.error} empty={!query.isLoading && !query.data?.length} onRetry={() => void query.refetch()}>
      <div className="presale-board">{opportunityStages.map(stage => <section key={stage.value} data-testid={`presale-column-${stage.value}`} className="presale-column">
        <header><strong>{stage.label}</strong><span>{(query.data ?? []).filter(item => item.stage === stage.value).length}</span></header>
        {(query.data ?? []).filter(item => item.stage === stage.value).map(item => <Card key={item.id} size="small" className="presale-card">
          <Link to={`/customers/opportunities/${item.id}`}>{item.title}</Link><p>{item.customerName}</p>
          <Space wrap>{canWrite && <Button size="small" aria-label="产出物" icon={<FileAddOutlined />} onClick={() => { setArtifactFor(item); setArtifactError('') }}>产出物</Button>}
            {canWrite && <AdvanceButtons item={item} loading={advance.isPending} onAdvance={requestAdvance} />}</Space>
        </Card>)}
      </section>)}</div>
    </PageState>
    <ArtifactDrawer opportunity={artifactFor} error={artifactError} canWrite={canWrite} onClose={() => setArtifactFor(undefined)} />
    <HandoffDrawer opportunity={handoffFor} onClose={() => setHandoffFor(undefined)} />
  </div>
}

function AdvanceButtons({ item, loading, onAdvance }: { item: Opportunity; loading: boolean; onAdvance: (item: Opportunity, decision?: 'PASS' | 'REJECT') => void }) {
  if (item.stage === 'CONTRACT') return <><Button size="small" type="primary" aria-label={`转交${item.title}`} onClick={() => onAdvance(item, 'PASS')}>转交实施</Button>
    <Button size="small" danger aria-label={`丢单${item.title}`} onClick={() => onAdvance(item, 'REJECT')}>丢单</Button></>
  if (item.stage === 'OPPORTUNITY' || item.stage === 'BIDDING') return <><Button size="small" type="primary" loading={loading} aria-label={`推进${item.title}`} onClick={() => onAdvance(item, 'PASS')}>PASS</Button>
    <Button size="small" danger aria-label={`丢单${item.title}`} onClick={() => onAdvance(item, 'REJECT')}>REJECT</Button></>
  return <Button size="small" type="primary" loading={loading} icon={<RightOutlined />} aria-label={`推进${item.title}`} onClick={() => onAdvance(item)}>推进</Button>
}

function ArtifactDrawer({ opportunity, error, canWrite, onClose }: { opportunity?: Opportunity; error: string; canWrite: boolean; onClose: () => void }) {
  const [form] = Form.useForm<Partial<OpportunityArtifact>>()
  const client = useQueryClient()
  const [uploaded, setUploaded] = useState<UploadedFile>()
  const type = Form.useWatch('artifactType', form)
  const selected = opportunity && artifactTypes[opportunity.stage].find(item => item.value === type)
  const save = useMutation({
    mutationFn: (input: Partial<OpportunityArtifact>) => crmApi.createArtifact(opportunity!.id, input),
    onSuccess: async () => { await client.invalidateQueries({ queryKey: ['opportunity-artifacts', opportunity?.id] }); message.success('产出物已保存'); form.resetFields(); setUploaded(undefined); onClose() },
    onError: (value: Error) => message.error(value.message),
  })
  useEffect(() => { if (opportunity) { form.resetFields(); setUploaded(undefined) } }, [form, opportunity])
  const upload = async (file: File) => {
    const stored = await crmApi.uploadFile(file)
    setUploaded(stored)
    form.setFieldValue('fileId', stored.id)
    message.success('文件已上传')
    return stored
  }
  return <Drawer title="补充产出物" open={Boolean(opportunity)} width={560} onClose={onClose}
    extra={canWrite ? <Button type="primary" loading={save.isPending} onClick={() => form.submit()}>保存产出物</Button> : undefined}>
    {error && <Alert className="crm-drawer-alert" type="warning" showIcon message={error} />}
    {opportunity && <><p><Tag>{stageLabel(opportunity.stage)}</Tag>{opportunity.title}</p>
      <Form form={form} layout="vertical" onFinish={save.mutate} onValuesChange={changed => {
        if ('artifactType' in changed) { form.setFieldValue('fileId', undefined); setUploaded(undefined) }
      }}>
        <Form.Item name="artifactType" label="产出物类型" rules={[{ required: true }]}><Select virtual={false} options={artifactTypes[opportunity.stage]} /></Form.Item>
        <Form.Item name="title" label="标题" rules={[{ required: true }]}><Input maxLength={240} /></Form.Item>
        {selected?.file ? <><Form.Item name="fileId" hidden rules={[{ required: true, message: '请先上传文件' }]}><Input /></Form.Item>
          {uploaded && <Alert showIcon type="success" message={`${uploaded.originalName} · 已上传`} description={`v${uploaded.fileVersion}${uploaded.sizeBytes ? ` · ${Math.ceil(uploaded.sizeBytes / 1024)} KB` : ''}`} />}
          <Upload showUploadList={false} customRequest={({ file, onSuccess, onError }) => upload(file as File).then(value => onSuccess?.(value)).catch(onError)}>
            <Button icon={<UploadOutlined />} aria-label="选择文件并上传">{uploaded ? '重新上传' : '选择文件并上传'}</Button>
          </Upload></>
          : <Form.Item name="contentMarkdown" label="报告正文" rules={[{ required: true }]}><Input.TextArea rows={10} /></Form.Item>}
      </Form></>}
  </Drawer>
}

function HandoffDrawer({ opportunity, onClose }: { opportunity?: Opportunity; onClose: () => void }) {
  const [form] = Form.useForm()
  const client = useQueryClient()
  const mode = Form.useWatch('mode', form) ?? 'CREATE'
  const productId = Form.useWatch('productId', form)
  const projects = useQuery({ queryKey: ['projects'], queryFn: projectApi.list, enabled: Boolean(opportunity) && mode === 'LINK' })
  const products = useQuery({ queryKey: ['products', 'bindable'], queryFn: projectApi.bindableProducts, enabled: Boolean(opportunity) && mode === 'CREATE' })
  const versions = useQuery({ queryKey: ['product-versions', productId], queryFn: () => projectApi.bindableVersions(productId!), enabled: Boolean(opportunity) && mode === 'CREATE' && Boolean(productId) })
  const owners = useQuery({ queryKey: ['crm-owner-options'], queryFn: crmApi.ownerOptions, enabled: Boolean(opportunity) && mode === 'CREATE' })
  useEffect(() => {
    if (!opportunity) return
    form.resetFields()
    form.setFieldsValue({ mode: 'CREATE', gateMode: 'BLOCK', productId: opportunity.productId,
      productVersionId: opportunity.productVersionId, managerUserId: opportunity.projectManagerUserId })
  }, [form, opportunity])
  const save = useMutation({ mutationFn: (input: Record<string, unknown>) => crmApi.handoff(opportunity!.id,
    input.mode === 'LINK'
      ? { mode: 'LINK', version: opportunity!.version, projectId: input.projectId }
      : { mode: 'CREATE', version: opportunity!.version, project: {
        code: input.code, name: input.name, productId: input.productId,
        productVersionId: input.productVersionId, managerUserId: input.managerUserId,
        gateMode: input.gateMode, startDate: input.startDate, plannedEndDate: input.plannedEndDate,
      } }),
    onSuccess: async () => { await Promise.all([client.invalidateQueries({ queryKey: ['opportunities'] }), client.invalidateQueries({ queryKey: ['projects'] }), client.invalidateQueries({ queryKey: ['implementation'] })]); message.success('已转交实施'); onClose() },
    onError: (error: Error) => message.error(error.message) })
  return <Drawer title="转交实施" open={Boolean(opportunity)} width={620} onClose={onClose} extra={<Button type="primary" onClick={() => form.submit()}>确认转交</Button>}>
    <Form form={form} layout="vertical" initialValues={{ mode: 'CREATE', gateMode: 'BLOCK' }} onFinish={save.mutate}>
      <Form.Item name="mode" label="交接方式"><Radio.Group options={[{ label: '创建项目', value: 'CREATE' }, { label: '关联项目', value: 'LINK' }]} /></Form.Item>
      {mode === 'LINK' ? <Form.Item name="projectId" label="同客户项目" rules={[{ required: true }]}><Select virtual={false}
        options={(projects.data ?? []).filter(item => item.customerId === opportunity?.customerId).map(item => ({ value: item.id, label: `${item.code} · ${item.name}` }))} /></Form.Item>
        : <><Row gutter={12}><Col span={10}><Form.Item name="code" label="项目编码" rules={[{ required: true }]}><Input /></Form.Item></Col><Col span={14}><Form.Item name="name" label="项目名称" rules={[{ required: true }]}><Input /></Form.Item></Col></Row>
          <Row gutter={12}><Col span={12}><Form.Item name="productId" label="产品" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" virtual={false} loading={products.isLoading}
            onChange={() => form.setFieldValue('productVersionId', undefined)} options={(products.data ?? []).map(item => ({ value: item.id, label: item.name }))} /></Form.Item></Col>
          <Col span={12}><Form.Item name="productVersionId" label="产品版本" rules={[{ required: true }]}><Select virtual={false} disabled={!productId} loading={versions.isLoading}
            options={(versions.data ?? []).map(item => ({ value: item.id, label: item.versionName }))} /></Form.Item></Col></Row>
          <Row gutter={12}><Col span={12}><Form.Item name="managerUserId" label="项目经理" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" virtual={false} loading={owners.isLoading}
            options={(owners.data ?? []).map(item => ({ value: item.id, label: item.displayName }))} /></Form.Item></Col><Col span={12}><Form.Item name="gateMode" label="门禁模式"><Select virtual={false} options={[{ value: 'BLOCK', label: '阻断' }, { value: 'WARNING', label: '提醒' }]} /></Form.Item></Col></Row>
          <Row gutter={12}><Col span={12}><Form.Item name="startDate" label="开始日期" rules={[{ required: true }]}><Input type="date" /></Form.Item></Col><Col span={12}><Form.Item name="plannedEndDate" label="计划结束" rules={[{ required: true }]}><Input type="date" /></Form.Item></Col></Row></>}
    </Form>
  </Drawer>
}
