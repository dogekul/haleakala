import { useQuery } from '@tanstack/react-query'
import { Card, Col, Row, Statistic, Table, Tag } from 'antd'
import { Link } from 'react-router-dom'
import { PageState } from '../../components/PageState'
import { productApi } from './productApi'
import type { CoverageFeature, UncoveredRequirement } from './types'

export function ProductCoverageTab({ productId }: { productId: number }) {
  const query = useQuery({ queryKey: ['product-coverage', productId], queryFn: () => productApi.coverage(productId) })
  const full = query.data?.features.reduce((sum, item) => sum + item.fullCount, 0) ?? 0
  const partial = query.data?.features.reduce((sum, item) => sum + item.partialCount, 0) ?? 0
  return <PageState loading={query.isLoading} error={query.error} onRetry={() => void query.refetch()}>
    <Row gutter={[16, 16]} className="product-coverage-summary">
      <Col xs={24} md={8}><Card><Statistic title="完整覆盖关联" value={full} suffix="项" /></Card></Col>
      <Col xs={24} md={8}><Card><Statistic title="部分覆盖关联" value={partial} suffix="项" /></Card></Col>
      <Col xs={24} md={8}><Card><Statistic title="待标准化需求" value={query.data?.uncoveredRequirements.length ?? 0} suffix="项" /></Card></Col>
    </Row>
    <Card className="product-coverage-card" title="功能覆盖分布">
      <Table rowKey="featureId" size="small" pagination={false} dataSource={query.data?.features ?? []} columns={[
        { title: '产品功能', key: 'feature', render: (_: unknown, item: CoverageFeature) => <div className="product-feature-name"><strong>{item.featureName}</strong><span>{item.featureCode} · {item.moduleName}</span></div> },
        { title: '覆盖情况', key: 'counts', width: 220, render: (_: unknown, item: CoverageFeature) => <div className="product-coverage-counts"><Tag color="success">完整 {item.fullCount}</Tag><Tag color="blue">部分 {item.partialCount}</Tag></div> },
      ]} />
    </Card>
    <Card className="product-coverage-card" title="未完整覆盖需求">
      <Table rowKey="requirementId" size="small" pagination={false} dataSource={query.data?.uncoveredRequirements ?? []} columns={[
        { title: '需求', key: 'requirement', render: (_: unknown, item: UncoveredRequirement) => <div className="product-requirement-name">
          <Link to={`/requirements?requirementId=${item.requirementId}`}>{item.requirementCode} · {item.title}</Link><span>{item.projectCode}</span></div> },
        { title: '标准化候选', dataIndex: 'debtLinked', width: 120, render: (linked: boolean) => <Tag color={linked ? 'blue' : 'default'}>{linked ? '已加入' : '待评估'}</Tag> },
      ]} />
    </Card>
  </PageState>
}
