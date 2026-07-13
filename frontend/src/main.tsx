import React from 'react'
import ReactDOM from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { BrowserRouter } from 'react-router-dom'
import { App } from './app/App'
import { AuthProvider } from './app/AuthProvider'
import { theme } from './app/theme'
import { appBase } from './services/apiPath'
import './styles/global.css'

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: 1, staleTime: 15_000 } } })

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider locale={zhCN} theme={theme}>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter basename={appBase() || undefined} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
          <AuthProvider><App /></AuthProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </ConfigProvider>
  </React.StrictMode>,
)
