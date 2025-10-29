import React from 'react'
import { Layout, Menu } from 'antd'
import {
  HomeOutlined,
  BookOutlined,
  UserOutlined,
  SettingOutlined,
  DashboardOutlined,
  FileTextOutlined,
  BulbOutlined,
  RobotOutlined,
  StarOutlined,
  CommentOutlined,
} from '@ant-design/icons'
import { useNavigate, useLocation } from 'react-router-dom'
import { useSelector } from 'react-redux'
import { RootState } from '@/store'
import './AppSider.new.css'

const { Sider } = Layout

const AppSider: React.FC = () => {
  const navigate = useNavigate()
  const location = useLocation()
  const { isAuthenticated } = useSelector((state: RootState) => state.auth)
  const { sidebarCollapsed } = useSelector((state: RootState) => state.ui)

  const menuItems = [
    {
      key: '/',
      icon: <HomeOutlined />,
      label: '首页',
      onClick: () => navigate('/'),
    },
    {
      key: '/novels',
      icon: <BookOutlined />,
      label: '我的小说',
      onClick: () => navigate('/novels'),
      hidden: !isAuthenticated,
    },
    {
      key: '/prompt-library',
      icon: <FileTextOutlined />,
      label: '提示词库',
      onClick: () => navigate('/prompt-library'),
      hidden: !isAuthenticated,
    },
    {
      key: '/ai-generators',
      icon: <RobotOutlined />,
      label: '生成器',
      onClick: () => navigate('/ai-generators'),
      hidden: !isAuthenticated,
    },
    {
      key: '/profile',
      icon: <UserOutlined />,
      label: '个人资料',
      onClick: () => navigate('/profile'),
      hidden: !isAuthenticated,
    },
    {
      key: '/settings',
      icon: <SettingOutlined />,
      label: '设置',
      onClick: () => navigate('/settings'),
      hidden: !isAuthenticated,
    },
  ].filter(item => !item.hidden)

  const selectedKey = menuItems.find(item => 
    location.pathname === item.key || location.pathname.startsWith(item.key + '/')
  )?.key || '/'

  return (
    <Sider 
      width={200} 
      collapsed={sidebarCollapsed}
      className="app-sider"
    >
      <Menu
        mode="inline"
        selectedKeys={[selectedKey]}
        items={menuItems}
        className="sider-menu"
      />
    </Sider>
  )
}

export default AppSider 