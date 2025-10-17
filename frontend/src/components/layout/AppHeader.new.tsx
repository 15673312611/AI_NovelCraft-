import React, { useEffect } from 'react'
import { Layout, Dropdown, Avatar, Space, Menu } from 'antd'
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

const AppHeader: React.FC = () => {
  const navigate = useNavigate()
  const location = useLocation()
  const dispatch = useDispatch<AppDispatch>()
  const { user, isAuthenticated } = useSelector((state: RootState) => state.auth)

  const handleLogout = async () => {
    try {
      await dispatch(logout())
      dispatch(clearAuth())
      navigate('/login')
    } catch (error) {
      dispatch(clearAuth())
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
        {/* Logo */}
        <div className="header-logo" onClick={() => navigate('/')}>
          <div className="logo-icon">📚</div>
          <span className="logo-text">小说创作系统</span>
        </div>

        {/* Right Section */}
        <div className="header-actions">
          {isAuthenticated ? (
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
                    background: 'var(--primary-500)',
                    cursor: 'pointer'
                  }}
                />
                <div className="user-name">{user?.username}</div>
              </div>
            </Dropdown>
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

