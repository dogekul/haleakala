import { Navigate, Route, Routes } from 'react-router-dom'
import { ProductDetailPage } from './ProductDetailPage'
import { ProductListPage } from './ProductListPage'

export function ProductCenterRoutes() {
  return <Routes>
    <Route index element={<ProductListPage />} />
    <Route path=":productId" element={<ProductDetailPage />} />
    <Route path="*" element={<Navigate to="/products" replace />} />
  </Routes>
}
