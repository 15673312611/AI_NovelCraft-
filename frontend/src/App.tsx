import React from 'react'
import { Routes, Route, useLocation } from 'react-router-dom'
import { Layout, App as AntdApp } from 'antd'
// 新版组件
import ModernLayout from '@/components/layout/ModernLayout'
import ModernDashboard from '@/components/dashboard/ModernDashboard'
import ProtectedRoute from '@/components/ProtectedRoute'
import '@/styles/modern-theme.css'

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
import AIControlPanelPage from '@/pages/AIControlPanelPage'
import PromptLibraryPage from '@/pages/PromptLibraryPage'
import WelcomeGuide from '@/pages/WelcomeGuide'
import AIChatPage from '@/pages/AIChatPage'
import GeneratorListPage from '@/pages/GeneratorListPage'
import WritingStudioPage from '@/pages/WritingStudioPage'
import ShortStoryListPage from '@/pages/shortstory/ShortStoryListPage'
import ShortStoryCreatePage from '@/pages/shortstory/ShortStoryCreatePage'
import ShortStoryWorkflowPage from '@/pages/shortstory/ShortStoryWorkflowPage'
import VideoScriptListPage from '@/pages/videoscript/VideoScriptListPage'
import VideoScriptCreatePage from '@/pages/videoscript/VideoScriptCreatePage'
import VideoScriptWorkflowPage from '@/pages/videoscript/VideoScriptWorkflowPage'
import './App.new.css'


const App: React.FC = () => {
  const location = useLocation()
  
  // 检查是否是写作页面(不显示导航栏和侧边栏)
  // 注意：/short-stories/create 不是写作页面，需要排除
  const isWritingPage = location.pathname.includes('/writing') || 
    (location.pathname.includes('/short-stories/') && !location.pathname.includes('/short-stories/create')) ||
    (location.pathname.includes('/video-scripts/') && !location.pathname.includes('/video-scripts/create'))
  
  // 检查是否是认证页面(登录/注册,不显示导航栏和侧边栏)
  const isAuthPage = location.pathname === '/login' || location.pathname === '/register'
  
  return (
    <AntdApp>
      {isWritingPage ? (
        // 写作页面:全屏模式,无导航栏和侧边栏
        <Layout className="app-layout" style={{ height: '100vh' }}>
          <Routes>
            <Route path="/novels/:novelId/writing" element={<ProtectedRoute><NovelCraftStudio /></ProtectedRoute>} />
            <Route path="/novels/:novelId/volumes/:volumeId/writing" element={<ProtectedRoute><VolumeWritingStudio /></ProtectedRoute>} />
            <Route path="/novels/:novelId/writing-studio" element={<ProtectedRoute><WritingStudioPage /></ProtectedRoute>} />
            <Route path="/short-stories/:id" element={<ProtectedRoute><ShortStoryWorkflowPage /></ProtectedRoute>} />
            <Route path="/video-scripts/:id" element={<ProtectedRoute><VideoScriptWorkflowPage /></ProtectedRoute>} />
          </Routes>
        </Layout>
      ) : isAuthPage ? (
        // 认证页面:全屏模式,无导航栏和侧边栏
        <Layout className="app-layout" style={{ height: '100vh' }}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
          </Routes>
        </Layout>
      ) : (
        // 普通页面:显示现代布局
        <ModernLayout>
          <Routes>
            <Route path="/" element={<ProtectedRoute><ModernDashboard /></ProtectedRoute>} />
            <Route path="/novels" element={<ProtectedRoute><NovelListPage /></ProtectedRoute>} />
            <Route path="/novels/new" element={<ProtectedRoute><NovelCreateWizard /></ProtectedRoute>} />
            <Route path="/novels/:id/edit" element={<ProtectedRoute><NovelEditPage /></ProtectedRoute>} />
            <Route path="/novels/:novelId/editor" element={<ProtectedRoute><NovelEditorPage /></ProtectedRoute>} />
            <Route path="/novels/:novelId/volumes" element={<ProtectedRoute><VolumeManagementPage /></ProtectedRoute>} />
            <Route path="/welcome" element={<ProtectedRoute><WelcomeGuide /></ProtectedRoute>} />
            <Route path="/profile" element={<ProtectedRoute><ProfilePage /></ProtectedRoute>} />
            <Route path="/settings" element={<ProtectedRoute><SettingsPage /></ProtectedRoute>} />
            <Route path="/ai-control-panel" element={<ProtectedRoute><AIControlPanelPage /></ProtectedRoute>} />
            <Route path="/prompt-library" element={<ProtectedRoute><PromptLibraryPage /></ProtectedRoute>} />
            <Route path="/ai-generators" element={<ProtectedRoute><GeneratorListPage /></ProtectedRoute>} />
            <Route path="/ai-chat" element={<ProtectedRoute><AIChatPage /></ProtectedRoute>} />
            {/* 短篇小说路由 */}
            <Route path="/short-stories" element={<ProtectedRoute><ShortStoryListPage /></ProtectedRoute>} />
            <Route path="/short-stories/create" element={<ProtectedRoute><ShortStoryCreatePage /></ProtectedRoute>} />
            {/* 短视频剧本路由 */}
            <Route path="/video-scripts" element={<ProtectedRoute><VideoScriptListPage /></ProtectedRoute>} />
            <Route path="/video-scripts/create" element={<ProtectedRoute><VideoScriptCreatePage /></ProtectedRoute>} />
          </Routes>
        </ModernLayout>
      )}
    </AntdApp>
  )
}

export default App 