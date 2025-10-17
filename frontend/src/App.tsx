import React from 'react'
import { Routes, Route, useLocation } from 'react-router-dom'
import { Layout, App as AntdApp } from 'antd'
// 新版组件
import AppHeader from '@/components/layout/AppHeader.new'
import AppSider from '@/components/layout/AppSider'
import HomePage from '@/pages/HomePage.new'
import LoginPage from '@/pages/LoginPage'
import RegisterPage from '@/pages/RegisterPage'
import NovelListPage from '@/pages/NovelListPage.new'
import NovelEditPage from '@/pages/NovelEditPage'
import NovelCreateWizard from '@/pages/NovelCreateWizard.new'
import NovelEditorPage from '@/pages/NovelEditorPage'
import ProfilePage from '@/pages/ProfilePage'
import SettingsPage from '@/pages/SettingsPage'

import NovelCraftStudio from '@/pages/NovelCraftStudio'
import VolumeManagementPage from '@/pages/VolumeManagementPage'
import VolumeWritingStudio from '@/pages/VolumeWritingStudio'
import { WorldViewBuilderPage } from '@/pages/WorldViewBuilderPage'
import AIControlPanelPage from '@/pages/AIControlPanelPage'
import PromptLibraryPage from '@/pages/PromptLibraryPage'
import WelcomeGuide from '@/pages/WelcomeGuide'
import './App.new.css'

const { Content } = Layout

const App: React.FC = () => {
  const location = useLocation()
  
  // 检查是否是写作页面(不显示导航栏和侧边栏)
  const isWritingPage = location.pathname.includes('/writing')
  
  return (
    <AntdApp>
      {isWritingPage ? (
        // 写作页面:全屏模式,无导航栏和侧边栏
        <Layout className="app-layout" style={{ height: '100vh' }}>
          <Routes>
            <Route path="/novels/:novelId/writing" element={<NovelCraftStudio />} />
            <Route path="/novels/:novelId/volumes/:volumeId/writing" element={<VolumeWritingStudio />} />
          </Routes>
        </Layout>
      ) : (
        // 普通页面:显示导航栏和侧边栏
        <Layout className="app-layout">
          <AppHeader />
          <Layout>
            <AppSider />
            <Layout>
              <Content className="app-content">
                <Routes>
                  <Route path="/" element={<HomePage />} />
                  <Route path="/login" element={<LoginPage />} />
                  <Route path="/register" element={<RegisterPage />} />
                  <Route path="/novels" element={<NovelListPage />} />
                  <Route path="/novels/new" element={<NovelCreateWizard />} />
                  <Route path="/novels/:id/edit" element={<NovelEditPage />} />
                  <Route path="/novels/:novelId/editor" element={<NovelEditorPage />} />
                  <Route path="/novels/:novelId/volumes" element={<VolumeManagementPage />} />
                  <Route path="/world-view-builder" element={<WorldViewBuilderPage />} />
                  <Route path="/world-view-builder/:id" element={<WorldViewBuilderPage />} />
                  <Route path="/welcome" element={<WelcomeGuide />} />
                  <Route path="/profile" element={<ProfilePage />} />
                  <Route path="/settings" element={<SettingsPage />} />
                  <Route path="/ai-control-panel" element={<AIControlPanelPage />} />
                  <Route path="/prompt-library" element={<PromptLibraryPage />} />
                </Routes>
              </Content>
            </Layout>
          </Layout>
        </Layout>
      )}
    </AntdApp>
  )
}

export default App 