import { useQuery } from '@tanstack/react-query'
import { Card, Col, Row, Select, Space, Statistic, Table, Tag, Typography } from 'antd'
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { PageState } from '../../components/PageState'
import { crmApi } from './crmApi'
import { projectStages } from './ImplementationPage'
import type { Health, ImplementationItem } from './types'

export function ImplementationCockpitPage() {
  const query = useQuery({ queryKey: ['implementation-cockpit'], queryFn: crmApi.cockpit })
  const [health, setHealth] = useState<Health>()
  const [stage, setStage] = useState<string>()
  const [manager, setManager] = useState<number>()
  const [customer, setCustomer] = useState<number>()
  const items = useMemo(() => (query.data?.items ?? []).filter(item =>
    (!health || item.health === health) && (!stage || item.projectStage === stage)
    && (!manager || item.managerUserId === manager) && (!customer || item.customerId === customer)),
  [query.data?.items, health, stage, manager, customer])
  const options = useMemo(() => ({
    managers: distinct(query.data?.items ?? [], 'managerUserId', 'managerName'),
    customers: distinct(query.data?.items ?? [], 'customerId', 'customerName'),
  }), [query.data?.items])
  const columns = [
    { title: '客户', dataIndex: 'customerName' },
    { title: '项目', key: 'project', render: (_: unknown, item: ImplementationItem) => <Link to={`/projects/${item.projectId}`}>{item.projectName}</Link> },
    { title: '阶段', dataIndex: 'projectStage', render: (value: string) => projectStages[value] ?? value },
    { title: '负责人', dataIndex: 'managerName' },
    { title: '健康度', dataIndex: 'health', render: (value: Health) => <Tag color={value === 'RED' ? 'error' : value === 'YELLOW' ? 'warning' : 'success'}>{healthLabel(value)}</Tag> },
    { title: '逾期里程碑', dataIndex: 'overdueMilestoneCount' },
  ]
  return <div className="crm-page cockpit-page"><div className="page-heading compact"><div>
    <Typography.Title level={2}>实施驾驶舱</Typography.Title><Typography.Paragraph>集中识别风险、逾期和临近收尾的实施项目。</Typography.Paragraph>
  </div></div><Row gutter={12} className="crm-kpis">
    <Col xs={12} lg={6}><Card data-testid="implementation-count"><Statistic title="实施中" value={query.data?.implementationProjects ?? 0} /></Card></Col>
    <Col xs={12} lg={6}><Card data-testid="red-risk-count"><Statistic title="红色风险项目" value={query.data?.redRiskProjects ?? 0} /></Card></Col>
    <Col xs={12} lg={6}><Card data-testid="overdue-count"><Statistic title="逾期里程碑" value={query.data?.overdueMilestones ?? 0} /></Card></Col>
    <Col xs={12} lg={6}><Card data-testid="closing-count"><Statistic title="收尾项目" value={query.data?.closingProjects ?? 0} /></Card></Col>
  </Row><Card className="crm-filter"><div className="crm-toolbar"><Space wrap>
    <Select aria-label="健康度筛选" allowClear placeholder="全部健康度" virtual={false} value={health} onChange={setHealth}
      options={[{ value: 'GREEN', label: '绿色' }, { value: 'YELLOW', label: '黄色' }, { value: 'RED', label: '红色' }]} />
    <Select aria-label="阶段筛选" allowClear placeholder="全部阶段" virtual={false} value={stage} onChange={setStage}
      options={Object.entries(projectStages).map(([value, label]) => ({ value, label }))} />
    <Select aria-label="负责人筛选" allowClear placeholder="全部负责人" virtual={false} value={manager} onChange={setManager} options={options.managers} />
    <Select aria-label="客户筛选" allowClear placeholder="全部客户" virtual={false} value={customer} onChange={setCustomer} options={options.customers} />
  </Space><span>当前筛选 {items.length} 个项目</span></div></Card>
  <Card className="crm-surface"><PageState loading={query.isLoading} error={query.error} empty={!query.isLoading && items.length === 0} onRetry={() => void query.refetch()}>
    <Table rowKey="projectId" columns={columns} dataSource={items} pagination={{ pageSize: 12 }} />
  </PageState></Card></div>
}

function healthLabel(value: Health) { return value === 'RED' ? '红色' : value === 'YELLOW' ? '黄色' : '绿色' }
function distinct(items: ImplementationItem[], id: keyof ImplementationItem, name: keyof ImplementationItem) {
  const values = new Map<number, string>(); items.forEach(item => values.set(Number(item[id]), String(item[name])))
  return [...values].map(([value, label]) => ({ value, label }))
}
