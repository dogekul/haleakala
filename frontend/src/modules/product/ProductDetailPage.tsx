import { ArrowLeftOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Card, Col, Row, Statistic, Tabs, Typography } from 'antd'
import { useContext } from 'react'
import { Link, useParams } from 'react-router-dom'
import { AuthContext } from '../../app/AuthProvider'
import { PageState } from '../../components/PageState'
import { ProductCoverageTab } from './ProductCoverageTab'
import { ProductDocumentsTab } from './ProductDocumentsTab'
import { productApi } from './productApi'
import { ProductStatusTag } from './ProductListPage'
import { ProductStructureTab } from './ProductStructureTab'
import { ProductVersionsTab } from './ProductVersionsTab'

export function ProductDetailPage() {
  const productId = Number(useParams().productId)
  const validId = Number.isFinite(productId) && productId > 0
  const me = useContext(AuthContext)?.me
  const product = useQuery({ queryKey: ['product', productId], queryFn: () => productApi.product(productId), enabled: validId })
  if (!validId) return <PageState error={new Error('产品地址无效')}>{null}</PageState>

  return <div className="product-detail-page">
    <Link className="detail-back-link" to="/products"><ArrowLeftOutlined /> 返回产品中心</Link>
    <PageState loading={product.isLoading} error={product.error} onRetry={() => void product.refetch()}>
      {product.data && <>
        <div className="product-detail-heading"><div className="product-detail-title"><span>{product.data.code}</span>
          <Typography.Title level={2}>{product.data.name}</Typography.Title><ProductStatusTag status={product.data.status} /></div>
          <Typography.Paragraph>{product.data.description || '暂无产品说明'}</Typography.Paragraph></div>
        {product.data.status === 'ARCHIVED' && <Alert type="info" showIcon message="该产品已归档，所有配置仅可查看。" />}
        <Tabs className="product-detail-tabs" defaultActiveKey="overview" items={[
          { key: 'overview', label: '概览', children: <ProductOverview productId={productId} product={product.data} /> },
          { key: 'structure', label: '模块与功能', children: <ProductStructureTab productId={productId}
            readOnly={!me?.permissions.includes('product:write') || product.data.status === 'ARCHIVED'} /> },
          { key: 'versions', label: '版本', children: <ProductVersionsTab productId={productId}
            readOnly={!me?.permissions.includes('product:write') || product.data.status === 'ARCHIVED'} /> },
          { key: 'documents', label: '产品文档', children: <ProductDocumentsTab productId={productId}
            readOnly={!me?.permissions.includes('product:write') || product.data.status === 'ARCHIVED'} /> },
          { key: 'coverage', label: '覆盖度', children: <ProductCoverageTab productId={productId} /> },
        ]} />
      </>}
    </PageState>
  </div>
}

function ProductOverview({ productId, product }: { productId: number; product: Awaited<ReturnType<typeof productApi.product>> }) {
  const versions = useQuery({ queryKey: ['product-versions', productId], queryFn: () => productApi.versions(productId) })
  return <PageState error={versions.error} onRetry={() => void versions.refetch()}>
    <Row className="product-overview-stats" gutter={[16, 16]}>
      <Col xs={24} md={6}><Card><Statistic title="产品模块" value={product.moduleCount} suffix="个" /></Card></Col>
      <Col xs={24} md={6}><Card><Statistic title="标准功能" value={product.featureCount} suffix="项" /></Card></Col>
      <Col xs={24} md={6}><Card><Statistic title="产品版本" value={versions.data?.length ?? 0} suffix="个" /></Card></Col>
      <Col xs={24} md={6}><Card><Statistic title="最新版本" value={product.latestVersionName || '尚未创建'} /></Card></Col>
    </Row>
    <Card className="product-overview-card" title="产品概览">
      <dl><div><dt>产品分类</dt><dd>{product.category || '未分类'}</dd></div>
        <div><dt>负责人</dt><dd>{product.ownerUserId ? `#${product.ownerUserId}` : '未指定'}</dd></div>
        <div><dt>最近更新</dt><dd>{product.updatedAt ? new Date(product.updatedAt).toLocaleString('zh-CN') : '—'}</dd></div></dl>
    </Card>
  </PageState>
}
