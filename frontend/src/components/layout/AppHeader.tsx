import React, { useEffect } from 'react'
import { Layout, Menu, Avatar, Dropdown, Space } from 'antd'
import { 
  UserOutlined, 
  LogoutOutlined, 
  SettingOutlined,
  EditOutlined,
  BookOutlined,
  StarOutlined
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useSelector, useDispatch } from 'react-redux'
import { RootState, AppDispatch } from '@/store'
import { logout, clearAuth } from '@/store/slices/authSlice'
import './AppHeader.css'

const { Header } = Layout

const AppHeader: React.FC = () => {
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()
  const { user, isAuthenticated } = useSelector((state: RootState) => state.auth)

  const handleLogout = async () => {
    try {
      await dispatch(logout())
      dispatch(clearAuth())
      navigate('/login')
    } catch (error) {
      // 即使logout API失败，也要清除本地状态
      dispatch(clearAuth())
      navigate('/login')
    }
  }

  // 监听来自API拦截器的logout事件
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

  const guestMenuItems = [
    {
      key: 'login',
      label: '登录',
      onClick: () => navigate('/login'),
    },
    {
      key: 'register',
      label: '注册',
      onClick: () => navigate('/register'),
    },
  ]

  return (
    <Header className="app-header">
      <div className="header-left">
        <div className="logo" onClick={() => navigate('/')}>
          小说创作系统
        </div>
      </div>
      
      <div className="header-right">
        {isAuthenticated ? (
          <Space size="middle">            
            {/* 用户信息下拉菜单 */}
            <Dropdown 
              menu={{ items: userMenuItems }} 
              placement="bottomRight"
              trigger={['click']}
              overlayClassName="user-dropdown"
            >
              <div className="user-info">
                <div className="user-avatar-container">
                  <Avatar 
                    className="user-avatar" 
                    icon={<UserOutlined />}
                    style={{
                      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                      border: '2px solid rgba(255, 255, 255, 0.8)'
                    }}
                  />
                  <div className="user-status-dot"></div>
                </div>
                <div className="user-details">
                  <span className="username">{user?.username}</span>
                  <span className="user-level">创作者</span>
                </div>
              </div>
            </Dropdown>
          </Space>
        ) : (
          <div className="guest-actions">
            <button 
              className="header-btn secondary" 
              onClick={() => navigate('/login')}
            >
              登录
            </button>
            <button 
              className="header-btn primary" 
              onClick={() => navigate('/register')}
            >
              <StarOutlined />
              开始创作
            </button>
          </div>
        )}
      </div>
    </Header>
  )
}

export default AppHeader 