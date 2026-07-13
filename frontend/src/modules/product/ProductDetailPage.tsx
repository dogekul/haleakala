import { ArrowLeftOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Card, Col, Row, Statistic, Typography } from 'antd'
import { Link, useParams } from 'react-router-dom'
import { PageState } from '../../components/PageState'
import { productApi } from './productApi'
import { ProductStatusTag } from './ProductListPage'

export function ProductDetailPage() {
  const productId = Number(useParams().productId)
  const validId = Number.isFinite(productId) && productId > 0
  const product = useQuery({ queryKey: ['product', productId], queryFn: () => productApi.product(productId), enabled: validId })
  const versions = useQuery({ queryKey: ['product-versions', productId], queryFn: () => productApi.versions(productId), enabled: validId })
  if (!validId) return <PageState error={new Error('产品地址无效')}>{null}</PageState>

  return <div className="product-detail-page">
    <Link className="product-detail-back" to="/products"><ArrowLeftOutlined /> 返回产品中心</Link>
    <PageState loading={product.isLoading || versions.isLoading} error={(product.error || versions.error) as Error | null}
      onRetry={() => void Promise.all([product.refetch(), versions.refetch()])}>
      {product.data && <>
        <div className="product-detail-heading"><div className="product-detail-title"><span>{product.data.code}</span>
          <Typography.Title level={2}>{product.data.name}</Typography.Title><ProductStatusTag status={product.data.status} /></div>
          <Typography.Paragraph>{product.data.description || '暂无产品说明'}</Typography.Paragraph></div>
        {product.data.status === 'ARCHIVED' && <Alert type="info" showIcon message="该产品已归档，所有配置仅可查看。" />}
        <Row className="product-overview-stats" gutter={[16, 16]}>
          <Col xs={24} md={6}><Card><Statistic title="产品模块" value={product.data.moduleCount} suffix="个" /></Card></Col>
          <Col xs={24} md={6}><Card><Statistic title="标准功能" value={product.data.featureCount} suffix="项" /></Card></Col>
          <Col xs={24} md={6}><Card><Statistic title="产品版本" value={versions.data?.length ?? 0} suffix="个" /></Card></Col>
          <Col xs={24} md={6}><Card><Statistic title="最新版本" value={product.data.latestVersionName || '尚未创建'} /></Card></Col>
        </Row>
        <Card className="product-overview-card" title="产品概览">
          <dl><div><dt>产品分类</dt><dd>{product.data.category || '未分类'}</dd></div>
            <div><dt>负责人</dt><dd>{product.data.ownerUserId ? `#${product.data.ownerUserId}` : '未指定'}</dd></div>
            <div><dt>最近更新</dt><dd>{product.data.updatedAt ? new Date(product.data.updatedAt).toLocaleString('zh-CN') : '—'}</dd></div></dl>
        </Card>
      </>}
    </PageState>
  </div>
}
