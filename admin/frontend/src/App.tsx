import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { ConfigProvider, theme } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import AdminLayout from './components/Layout/AdminLayout'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import UserList from './pages/Users/UserList'
import NovelList from './pages/Novels/NovelList'
import NovelDetail from './pages/Novels/NovelDetail'
import AITaskList from './pages/AITasks/AITaskList'
import TemplateList from './pages/Templates/TemplateList'
import QimaoList from './pages/Qimao/QimaoList'
import SystemConfig from './pages/System/SystemConfig'
import OperationLogs from './pages/Logs/OperationLogs'
import PromptOptimizer from './pages/PromptOptimizer'
import CreditsPage from './pages/Credits'
import AIModelsPage from './pages/AIModels'
import WechatConfigPage from './pages/WechatConfig'
import EmailConfigPage from './pages/EmailConfig'
import AnnouncementPage from './pages/Announcement'

function App() {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: theme.darkAlgorithm,
        token: {
          // 现代化配色方案 - 蓝绿色系
          colorPrimary: '#0ea5e9',
          colorSuccess: '#22c55e',
          colorWarning: '#f97316',
          colorError: '#ef4444',
          colorInfo: '#06b6d4',
          borderRadius: 10,
          fontSize: 14,
          fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", "SF Pro Display", Roboto, "Helvetica Neue", Arial, sans-serif',
          colorBgBase: '#0a0a0a',
          colorBgContainer: 'rgba(23, 23, 23, 0.8)',
          colorBorder: 'rgba(255, 255, 255, 0.08)',
          colorText: '#fafafa',
          colorTextSecondary: 'rgba(250, 250, 250, 0.65)',
          colorTextTertiary: 'rgba(250, 250, 250, 0.45)',
          colorBgElevated: '#171717',
          lineWidth: 1,
          controlHeight: 38,
        },
        components: {
          Button: {
            controlHeight: 38,
            borderRadius: 8,
            fontWeight: 500,
            primaryShadow: '0 0 0 0 transparent',
          },
          Input: {
            controlHeight: 38,
            borderRadius: 8,
            paddingBlock: 8,
            paddingInline: 12,
          },
          Select: {
            controlHeight: 38,
            borderRadius: 8,
          },
          Card: {
            borderRadiusLG: 12,
            paddingLG: 20,
          },
          Table: {
            borderRadiusLG: 12,
            headerBg: 'rgba(255, 255, 255, 0.03)',
            rowHoverBg: 'rgba(14, 165, 233, 0.08)',
          },
          Menu: {
            itemBorderRadius: 8,
            itemHeight: 40,
            itemMarginBlock: 2,
            itemMarginInline: 8,
          },
          Modal: {
            borderRadiusLG: 16,
          },
          Dropdown: {
            borderRadiusLG: 12,
          },
        },
      }}
    >
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/" element={<AdminLayout />}>
            <Route index element={<Navigate to="/dashboard" replace />} />
            <Route path="dashboard" element={<Dashboard />} />
            <Route path="users" element={<UserList />} />
            <Route path="novels" element={<NovelList />} />
            <Route path="novels/:id" element={<NovelDetail />} />
            <Route path="ai-tasks" element={<AITaskList />} />
            <Route path="credits" element={<CreditsPage />} />
            <Route path="ai-models" element={<AIModelsPage />} />
            <Route path="wechat-config" element={<WechatConfigPage />} />
            <Route path="email-config" element={<EmailConfigPage />} />
            <Route path="announcement" element={<AnnouncementPage />} />
            <Route path="templates" element={<TemplateList />} />
            <Route path="prompt-optimizer" element={<PromptOptimizer />} />
            <Route path="qimao" element={<QimaoList />} />
            <Route path="system" element={<SystemConfig />} />
            <Route path="logs" element={<OperationLogs />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </ConfigProvider>
  )
}

export default App
