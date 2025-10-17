import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { Provider } from 'react-redux'
import { ConfigProvider, theme, App as AntdApp } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import App from './App'
import { store } from './store'
import './index.css'

const customTheme = {
  algorithm: theme.defaultAlgorithm,
  token: {
    // 主色调 - 优雅的紫色渐变
    colorPrimary: '#6366f1',
    colorSuccess: '#10b981',
    colorWarning: '#f59e0b',
    colorError: '#ef4444',
    colorInfo: '#3b82f6',

    // 字体
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, "Noto Sans SC", sans-serif',
    fontSize: 14,
    fontSizeHeading1: 32,
    fontSizeHeading2: 24,
    fontSizeHeading3: 20,

    // 圆角
    borderRadius: 8,
    borderRadiusLG: 12,
    borderRadiusSM: 6,

    // 间距
    padding: 16,
    paddingLG: 24,
    paddingSM: 12,
    margin: 16,
    marginLG: 24,
    marginSM: 12,

    // 背景色
    colorBgContainer: '#ffffff',
    colorBgElevated: '#ffffff',
    colorBgLayout: '#f8fafc',
    colorBgSpotlight: '#fafbfc',

    // 边框
    colorBorder: '#e2e8f0',
    colorBorderSecondary: '#f1f5f9',

    // 文字颜色
    colorText: '#1e293b',
    colorTextSecondary: '#64748b',
    colorTextTertiary: '#94a3b8',
    colorTextQuaternary: '#cbd5e1',
  },
  components: {
    Layout: {
      headerBg: '#ffffff',
      headerHeight: 64,
      headerPadding: '0 24px',
      siderBg: '#ffffff',
      bodyBg: '#f8fafc',
      footerBg: '#ffffff',
      footerPadding: '24px 50px',
    },
    Menu: {
      itemBg: 'transparent',
      itemSelectedBg: '#e0e7ff',
      itemHoverBg: '#f8fafc',
      itemSelectedColor: '#6366f1',
      itemColor: '#64748b',
      itemHoverColor: '#6366f1',
      fontSize: 14,
      itemHeight: 44,
      itemMarginInline: 4,
      itemBorderRadius: 6,
    },
    Card: {
      borderRadius: 12,
      boxShadow: '0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06)',
      headerBg: '#fafbfc',
      bodyPadding: 24,
      headerHeight: 56,
    },
    Button: {
      borderRadius: 6,
      fontWeight: 500,
      primaryShadow: '0 2px 8px 0 rgba(99, 102, 241, 0.2)',
      defaultShadow: '0 1px 3px 0 rgba(0, 0, 0, 0.1)',
    },
    Input: {
      borderRadius: 8,
      paddingBlock: 8,
      paddingInline: 12,
    },
    Table: {
      borderRadius: 8,
      headerBg: '#f8fafc',
      headerColor: '#374151',
      headerSplitColor: '#e5e7eb',
      rowHoverBg: '#f9fafb',
    },
    Tabs: {
      cardBg: '#ffffff',
      itemSelectedColor: '#6366f1',
      itemHoverColor: '#6366f1',
      inkBarColor: '#6366f1',
      itemActiveColor: '#6366f1',
    },
    Badge: {
      colorPrimary: '#6366f1',
    },
    Tag: {
      borderRadiusSM: 6,
    },
    Drawer: {
      borderRadius: 0,
    },
    Modal: {
      borderRadius: 12,
    },
    Notification: {
      borderRadius: 8,
    },
    Message: {
      borderRadius: 8,
    }
  }
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <Provider store={store}>
      <ConfigProvider
        locale={zhCN}
        theme={customTheme}
      >
        <BrowserRouter>
          <AntdApp>
            <App />
          </AntdApp>
        </BrowserRouter>
      </ConfigProvider>
    </Provider>
  </React.StrictMode>,
)