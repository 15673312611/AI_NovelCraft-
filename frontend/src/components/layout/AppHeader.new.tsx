import React, { useEffect } from 'react'
import { Layout, Dropdown, Avatar, Space, Menu, App } from 'antd'
import { 
  UserOutlined, 
  LogoutOutlined, 
  SettingOutlined,
  EditOutlined,
  BookOutlined,
  HomeOutlined,
  DashboardOutlined
} from '@ant-design/icons'
import { useNavigate, useLocation } from 'react-router-dom'
import { useSelector, useDispatch } from 'react-redux'
import { RootState, AppDispatch } from '@/store'
import { logout, clearAuth } from '@/store/slices/authSlice'
import './AppHeader.new.css'

const { Header } = Layout

// SVG Logo Component
const LogoIcon: React.FC = () => (
  <svg 
    width="32" 
    height="32" 
    viewBox="0 0 32 32" 
    fill="none" 
    xmlns="http://www.w3.org/2000/svg"
    className="logo-svg"
  >
    {/* 书本背景 */}
    <path
      d="M6 4C6 2.89543 6.89543 2 8 2H24C25.1046 2 26 2.89543 26 4V28C26 29.1046 25.1046 30 24 30H8C6.89543 30 6 29.1046 6 28V4Z"
      fill="url(#gradient1)"
    />
    {/* 书页装饰 */}
    <path
      d="M10 8H22M10 12H22M10 16H18M10 20H20"
      stroke="white"
      strokeWidth="1.5"
      strokeLinecap="round"
      opacity="0.9"
    />
    {/* 笔的图标 */}
    <path
      d="M19 22L23 18L25 20L21 24L19 22Z"
      fill="url(#gradient2)"
    />
    <path
      d="M23 18L24.5 16.5C25.3284 15.6716 25.3284 14.3284 24.5 13.5C23.6716 12.6716 22.3284 12.6716 21.5 13.5L20 15L23 18Z"
      fill="url(#gradient3)"
    />
    {/* 渐变定义 */}
    <defs>
      <linearGradient id="gradient1" x1="6" y1="2" x2="26" y2="30" gradientUnits="userSpaceOnUse">
        <stop stopColor="#4A90E2" />
        <stop offset="1" stopColor="#5BA3F5" />
      </linearGradient>
      <linearGradient id="gradient2" x1="19" y1="18" x2="25" y2="24" gradientUnits="userSpaceOnUse">
        <stop stopColor="#5BA3F5" />
        <stop offset="1" stopColor="#7BB8FF" />
      </linearGradient>
      <linearGradient id="gradient3" x1="20" y1="13.5" x2="24.5" y2="18" gradientUnits="userSpaceOnUse">
        <stop stopColor="#7BB8FF" />
        <stop offset="1" stopColor="#DCEBFF" />
      </linearGradient>
    </defs>
  </svg>
)

const AppHeader: React.FC = () => {
  const navigate = useNavigate()
  const location = useLocation()
  const dispatch = useDispatch<AppDispatch>()
  const { message } = App.useApp()
  const { user, isAuthenticated } = useSelector((state: RootState) => state.auth)

  const handleLogout = async () => {
    try {
      await dispatch(logout()).unwrap()
      dispatch(clearAuth())
      message.success('已退出登录')
    } catch (error) {
      // 即使logout失败也要清除本地状态
      dispatch(clearAuth())
      message.info('已退出登录')
    } finally {
      // 确保跳转到登录页
      navigate('/login')
    }
  }

  useEffect(() => {
    const handleAuthLogout = () => {
      dispatch(clearAuth())
    }

    window.addEventListener('auth:logout', handleAuthLogout)
    
    return () => {
      window.removeEventListener('auth:logout', handleAuthLogout)
    }
  }, [dispatch])

  const userMenuItems = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: '个人资料',
      onClick: () => navigate('/profile'),
    },
    {
      key: 'works',
      icon: <BookOutlined />,
      label: '我的作品',
      onClick: () => navigate('/novels'),
    },
    {
      key: 'writing',
      icon: <EditOutlined />,
      label: '创作中心',
      onClick: () => navigate('/novels'),
    },
    {
      type: 'divider' as const,
    },
    {
      key: 'settings',
      icon: <SettingOutlined />,
      label: '设置',
      onClick: () => navigate('/settings'),
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: handleLogout,
      danger: true,
    },
  ]

  return (
    <Header className="modern-header">
      <div className="header-container">
        {/* Logo - 移到最左边 */}
        <div className="header-logo" onClick={() => navigate('/')}>
          <LogoIcon />
          <span className="logo-text">小说创作系统</span>
        </div>

        {/* Right Section */}
        <div className="header-actions">
          {isAuthenticated ? (
            <Space size={16}>
              <Dropdown 
                menu={{ items: userMenuItems }} 
                placement="bottomRight"
                trigger={['click']}
                overlayClassName="modern-user-dropdown"
              >
                <div className="user-button">
                  <Avatar 
                    size={36}
                    icon={<UserOutlined />}
                    style={{
                      background: 'linear-gradient(135deg, #4A90E2 0%, #5BA3F5 100%)',
                      cursor: 'pointer'
                    }}
                  />
                  <div className="user-info">
                    <div className="user-name">{user?.username || '用户'}</div>
                    <div className="user-hint">点击查看更多</div>
                  </div>
                </div>
              </Dropdown>
              
              {/* 快速退出按钮 */}
              <button 
                className="header-btn btn-ghost logout-btn" 
                onClick={handleLogout}
                title="退出登录"
              >
                <LogoutOutlined />
                <span>退出</span>
              </button>
            </Space>
          ) : (
            <Space size={12}>
              <button 
                className="header-btn btn-ghost" 
                onClick={() => navigate('/login')}
              >
                登录
              </button>
              <button 
                className="header-btn btn-primary" 
                onClick={() => navigate('/register')}
              >
                开始创作
              </button>
            </Space>
          )}
        </div>
      </div>
    </Header>
  )
}

export default AppHeader

