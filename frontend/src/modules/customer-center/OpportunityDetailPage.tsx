import { ArrowLeftOutlined, CheckOutlined, PlusOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Card, Col, Drawer, Form, Input, List, Row, Space, Tag, Typography, message } from 'antd'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuth } from '../../app/AuthProvider'
import { PageState } from '../../components/PageState'
import { DocumentWorkspace } from '../document/DocumentWorkspace'
import { crmApi } from './crmApi'
import { currency, stageLabel, statusLabels } from './OpportunityOverviewPage'
import type { OpportunityActivity } from './types'

export function OpportunityDetailPage() {
  const id = Number(useParams().id)
  const { me } = useAuth()
  const canWrite = me?.permissions.includes('crm:write') ?? false
  const client = useQueryClient()
  const [reportOpen, setReportOpen] = useState(false)
  const opportunity = useQuery({ queryKey: ['opportunity', id], queryFn: () => crmApi.opportunity(id), enabled: Boolean(id) })
  const activities = useQuery({ queryKey: ['opportunity-activities', id], queryFn: () => crmApi.activities(id), enabled: Boolean(id) })
  const artifacts = useQuery({ queryKey: ['opportunity-artifacts', id], queryFn: () => crmApi.artifacts(id), enabled: Boolean(id) })
  const fullLink = useQuery({ queryKey: ['opportunity-full-link', id], queryFn: () => crmApi.fullLink(id), enabled: Boolean(id) })
  const complete = useMutation({ mutationFn: (item: OpportunityActivity) => crmApi.updateActivity(id, item.id, 'DONE', item.version),
    onSuccess: async () => { await client.invalidateQueries({ queryKey: ['opportunity-activities', id] }); message.success('活动已完成') },
    onError: (error: Error) => message.error(error.message) })
  const add = useMutation({ mutationFn: (value: { title: string }) => crmApi.createActivity(id, value.title),
    onSuccess: async () => { await client.invalidateQueries({ queryKey: ['opportunity-activities', id] }); message.success('活动已添加') },
    onError: (error: Error) => message.error(error.message) })

  return <div className="crm-page opportunity-detail">
    <Link className="detail-back-link" to="/customers/opportunities"><ArrowLeftOutlined /> 返回商机总览</Link>
    <PageState loading={opportunity.isLoading} error={opportunity.error} empty={!opportunity.data} onRetry={() => void opportunity.refetch()}>
      {opportunity.data && <><div className="crm-detail-hero"><div><Space><Tag color="blue">{stageLabel(opportunity.data.stage)}</Tag><Tag>{statusLabels[opportunity.data.status]}</Tag></Space>
        <Typography.Title level={2}>{opportunity.data.title}</Typography.Title><p>{opportunity.data.customerName} · {opportunity.data.productName ?? '未关联产品'}</p></div>
        <strong>{currency(opportunity.data.amount)}</strong></div>
        <Card className="crm-basic-card" title="基本资料"><Row gutter={[16, 16]}>
          <Col span={6}><LabelValue label="商务负责人" value={opportunity.data.commercialOwnerName} /></Col>
          <Col span={6}><LabelValue label="方案负责人" value={opportunity.data.solutionOwnerName} /></Col>
          <Col span={6}><LabelValue label="项目经理" value={opportunity.data.projectManagerName} /></Col>
          <Col span={6}><LabelValue label="运营负责人" value={opportunity.data.operationOwnerName} /></Col>
          <Col span={24}><LabelValue label="备注" value={opportunity.data.note} /></Col>
        </Row></Card></>}
    </PageState>
    <Row gutter={14} className="crm-detail-grid"><Col xs={24} xl={12}><Card title="跟进活动" extra={canWrite ? <ActivityForm onAdd={add.mutate} loading={add.isPending} /> : undefined}>
      <PageState loading={activities.isLoading} error={activities.error} empty={!activities.isLoading && !activities.data?.length} onRetry={() => void activities.refetch()}>
        <List dataSource={activities.data ?? []} renderItem={item => <List.Item actions={canWrite && item.status === 'TODO' ? [<Button key="done" size="small" icon={<CheckOutlined />} aria-label={`完成${item.title}`} onClick={() => complete.mutate(item)}>完成</Button>] : []}>
          <List.Item.Meta title={item.title} description={<Space><Tag>{stageLabel(item.stageCode)}</Tag><span>{item.status === 'DONE' ? '已完成' : '待办'}</span></Space>} />
        </List.Item>} />
      </PageState>
    </Card></Col><Col xs={24} xl={12}><Card title="阶段产出物">
      <PageState loading={artifacts.isLoading} error={artifacts.error} empty={!artifacts.isLoading && !artifacts.data?.length} onRetry={() => void artifacts.refetch()}>
        <List dataSource={artifacts.data ?? []} renderItem={item => <List.Item
          actions={item.outlineLinkId ? [<Button
            key="preview-report"
            type="link"
            onClick={() => setReportOpen(true)}
          >预览报告</Button>] : []}
        ><List.Item.Meta title={<Space>{item.title}<Tag>{item.artifactType}</Tag></Space>}
          description={item.outlineLinkId
            ? `Outline 文档 · 模版修订 ${item.sourceTemplateRevision ?? '-'}`
            : item.contentMarkdown ?? (item.fileName
              ? `${item.fileName} · 文件 #${item.fileId}` : `文件 #${item.fileId}`)} /></List.Item>} />
      </PageState>
    </Card></Col></Row>
    <Card className="crm-full-link" title="客户全链路">
      <PageState loading={fullLink.isLoading} error={fullLink.error} empty={!fullLink.data} onRetry={() => void fullLink.refetch()}>
        {fullLink.data && <div className="full-link-track"><div><span>客户</span><strong>{fullLink.data.customer.name}</strong></div><i>→</i>
          <div><span>商机</span><strong>{fullLink.data.opportunity.title}</strong></div><i>→</i>
          <div><span>项目</span>{fullLink.data.project ? <><strong>{fullLink.data.project.name}</strong><Link to={`/projects/${fullLink.data.project.id}`}>进入项目</Link></> : <em>待交接</em>}</div><i>→</i>
          <div><span>运营</span>{fullLink.data.operation ? <><strong>{fullLink.data.operation.title}</strong><Link to={`/customers/operations/${fullLink.data.operation.id}`}>进入运营</Link></> : <em>待转运营</em>}</div></div>}
      </PageState>
    </Card>
    <Drawer
      title="需求调研报告"
      open={reportOpen}
      width="min(1180px, 94vw)"
      onClose={() => setReportOpen(false)}
      destroyOnClose
    >
      {reportOpen && <DocumentWorkspace
        title="需求调研报告"
        load={() => crmApi.researchReport(id)}
        save={input => crmApi.saveResearchReport(id, input)}
        exportUrl={format => crmApi.researchReportExportUrl(id, format)}
        canEdit={false}
      />}
    </Drawer>
  </div>
}

function LabelValue({ label, value }: { label: string; value?: string }) { return <div className="crm-label-value"><span>{label}</span><strong>{value || '未设置'}</strong></div> }

function ActivityForm({ onAdd, loading }: { onAdd: (value: { title: string }) => void; loading: boolean }) {
  const [form] = Form.useForm<{ title: string }>()
  return <Form form={form} layout="inline" onFinish={value => { onAdd(value); form.resetFields() }}><Form.Item name="title" rules={[{ required: true }]}><Input size="small" placeholder="新增跟进活动" /></Form.Item>
    <Button size="small" htmlType="submit" loading={loading} icon={<PlusOutlined />} aria-label="新增活动" /></Form>
}
