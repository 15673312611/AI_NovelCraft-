import { useState, useMemo } from 'react'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { Layout, Menu, Avatar, Dropdown, Badge, Tooltip, Typography } from 'antd'
import {
  DashboardOutlined,
  UserOutlined,
  BookOutlined,
  RobotOutlined,
  FileTextOutlined,
  SettingOutlined,
  FileSearchOutlined,
  LogoutOutlined,
  BellOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  BulbOutlined,
  ThunderboltOutlined,
  ApiOutlined,
  WechatOutlined,
  MailOutlined,
  NotificationOutlined,
} from '@ant-design/icons'
import type { MenuProps } from 'antd'
import styled from '@emotion/styled'
import { motion, AnimatePresence } from 'framer-motion'

const { Header, Sider, Content } = Layout
const { Text } = Typography

// Filter out transient props (starting with $) from being passed to DOM
const shouldForwardProp = (prop: string) => !prop.startsWith('$')

// Styled Components
const StyledLayout = styled(Layout)`
  min-height: 100vh;
`

const StyledSider = styled(Sider)`
  position: fixed !important;
  left: 0;
  top: 0;
  bottom: 0;
  z-index: 100;
  border-right: 1px solid rgba(255, 255, 255, 0.06);
  
  .ant-layout-sider-children {
    display: flex;
    flex-direction: column;
    height: 100%;
  }
`

const LogoWrapper = styled('div', { shouldForwardProp })<{ $collapsed?: boolean }>`
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: ${props => props.$collapsed ? 'center' : 'flex-start'};
  padding: ${props => props.$collapsed ? '0' : '0 20px'};
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  transition: all 0.2s;
`

const LogoIcon = styled.div`
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, #0ea5e9, #06b6d4);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(14, 165, 233, 0.3);
`

const LogoText = styled.div`
  margin-left: 12px;
  overflow: hidden;
`

const MenuWrapper = styled.div`
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 12px 0;
  
  &::-webkit-scrollbar {
    width: 4px;
  }
  
  &::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.1);
    border-radius: 2px;
  }
`

const CollapseButton = styled('div', { shouldForwardProp })<{ $collapsed?: boolean }>`
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin: 8px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 10px;
  cursor: pointer;
  color: rgba(250, 250, 250, 0.45);
  font-size: 13px;
  font-weight: 500;
  transition: all 0.2s;
  
  &:hover {
    background: rgba(255, 255, 255, 0.08);
    border-color: rgba(255, 255, 255, 0.1);
    color: rgba(250, 250, 250, 0.85);
  }
`

const StyledHeader = styled(Header)`
  position: sticky;
  top: 0;
  z-index: 99;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px !important;
  height: 64px !important;
  background: rgba(23, 23, 23, 0.85) !important;
  backdrop-filter: blur(12px) saturate(180%);
  -webkit-backdrop-filter: blur(12px) saturate(180%);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
`

const HeaderLeft = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;
`

const HeaderRight = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;
`

const IconButton = styled.div`
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  cursor: pointer;
  color: rgba(250, 250, 250, 0.65);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
  transition: all 0.2s;
  
  &:hover {
    background: rgba(255, 255, 255, 0.08);
    border-color: rgba(255, 255, 255, 0.1);
    color: #0ea5e9;
    transform: translateY(-1px);
  }
`

const UserButton = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 16px 6px 6px;
  border-radius: 12px;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
  transition: all 0.2s;
  min-width: 140px;
  
  &:hover {
    background: rgba(255, 255, 255, 0.08);
    border-color: rgba(255, 255, 255, 0.1);
    transform: translateY(-1px);
  }
`

const Divider = styled.div`
  width: 1px;
  height: 28px;
  background: rgba(255, 255, 255, 0.08);
`

const StyledContent = styled(Content, { shouldForwardProp })<{ $collapsed?: boolean }>`
  margin-left: ${props => props.$collapsed ? '80px' : '240px'};
  transition: margin-left 0.2s;
  padding: 24px;
  min-height: calc(100vh - 64px);
`

const AdminLayout = () => {
  const [collapsed, setCollapsed] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()

  const menuItems: MenuProps['items'] = useMemo(() => [
    {
      key: '/dashboard',
      icon: <DashboardOutlined />,
      label: '仪表盘',
    },
    {
      key: '/users',
      icon: <UserOutlined />,
      label: '用户管理',
    },
    {
      key: '/novels',
      icon: <BookOutlined />,
      label: '小说管理',
    },
    {
      key: '/ai-tasks',
      icon: <RobotOutlined />,
      label: 'AI 任务',
    },
    {
      key: '/credits',
      icon: <ThunderboltOutlined />,
      label: '字数点管理',
    },
    {
      key: '/ai-models',
      icon: <ApiOutlined />,
      label: 'AI 配置',
    },
    {
      key: '/templates',
      icon: <FileTextOutlined />,
      label: '提示词模板',
    },
    {
      key: '/prompt-optimizer',
      icon: <BulbOutlined />,
      label: '提示词优化',
    },
    {
      key: '/system',
      icon: <SettingOutlined />,
      label: '系统配置',
    },
    {
      key: '/logs',
      icon: <FileSearchOutlined />,
      label: '操作日志',
    },
    {
      key: '/announcement',
      icon: <NotificationOutlined />,
      label: '公告管理',
    },
    { type: 'divider' },
    {
      key: '/wechat-config',
      icon: <WechatOutlined />,
      label: '微信登录',
    },
    {
      key: '/email-config',
      icon: <MailOutlined />,
      label: '邮箱验证',
    },
  ], [])

  const userMenuItems: MenuProps['items'] = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: '个人设置',
    },
    { type: 'divider' },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      danger: true,
      onClick: () => {
        localStorage.removeItem('admin_token')
        navigate('/login')
      },
    },
  ]

  const handleMenuClick: MenuProps['onClick'] = (e) => {
    navigate(e.key)
  }

  return (
    <StyledLayout>
      <StyledSider
        trigger={null}
        collapsible
        collapsed={collapsed}
        width={240}
        collapsedWidth={80}
      >
        {/* Logo */}
        <LogoWrapper $collapsed={collapsed}>
          <LogoIcon>📚</LogoIcon>
          <AnimatePresence>
            {!collapsed && (
              <motion.div
                initial={{ opacity: 0, width: 0 }}
                animate={{ opacity: 1, width: 'auto' }}
                exit={{ opacity: 0, width: 0 }}
                transition={{ duration: 0.2 }}
              >
                <LogoText>
                  <Text style={{ 
                    fontSize: 15, 
                    fontWeight: 700, 
                    color: '#fafafa',
                    display: 'block',
                    lineHeight: 1.2,
                  }}>
                    AI Novel
                  </Text>
                  <Text style={{ 
                    fontSize: 11, 
                    color: 'rgba(250, 250, 250, 0.45)',
                    fontWeight: 500,
                    letterSpacing: '0.5px',
                  }}>
                    ADMIN SYSTEM
                  </Text>
                </LogoText>
              </motion.div>
            )}
          </AnimatePresence>
        </LogoWrapper>

        {/* Menu */}
        <MenuWrapper>
          <Menu
            theme="dark"
            mode="inline"
            selectedKeys={[location.pathname]}
            items={menuItems}
            onClick={handleMenuClick}
            style={{ border: 'none', background: 'transparent' }}
          />
        </MenuWrapper>

        {/* Collapse Button */}
        <CollapseButton 
          $collapsed={collapsed}
          onClick={() => setCollapsed(!collapsed)}
        >
          {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
          <AnimatePresence>
            {!collapsed && (
              <motion.span
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
              >
                收起菜单
              </motion.span>
            )}
          </AnimatePresence>
        </CollapseButton>
      </StyledSider>

      <Layout>
        {/* Header */}
        <StyledHeader>
          <HeaderLeft>
            {collapsed && (
              <IconButton onClick={() => setCollapsed(false)}>
                <MenuUnfoldOutlined style={{ fontSize: 18 }} />
              </IconButton>
            )}
          </HeaderLeft>

          <HeaderRight>
            {/* Notifications */}
            <Tooltip title="通知中心">
              <Badge count={3} size="small" offset={[-4, 4]}>
                <IconButton>
                  <BellOutlined style={{ fontSize: 18 }} />
                </IconButton>
              </Badge>
            </Tooltip>

            <Divider />

            {/* User Menu */}
            <Dropdown 
              menu={{ items: userMenuItems }} 
              placement="bottomRight" 
              trigger={['click']}
            >
              <UserButton>
                <Avatar 
                  size={36}
                  icon={<UserOutlined />}
                  style={{
                    background: 'linear-gradient(135deg, #667eea, #764ba2)',
                    flexShrink: 0,
                  }}
                />
                <div style={{ minWidth: 0 }}>
                  <Text 
                    style={{ 
                      fontSize: 14, 
                      fontWeight: 600, 
                      color: '#fafafa',
                      display: 'block',
                      lineHeight: 1.3,
                    }}
                    ellipsis
                  >
                    Admin
                  </Text>
                  <Text 
                    style={{ 
                      fontSize: 12, 
                      color: 'rgba(250, 250, 250, 0.45)',
                      display: 'block',
                      lineHeight: 1.2,
                    }}
                  >
                    管理员
                  </Text>
                </div>
              </UserButton>
            </Dropdown>
          </HeaderRight>
        </StyledHeader>

        {/* Content */}
        <StyledContent $collapsed={collapsed}>
          <motion.div
            key={location.pathname}
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3 }}
          >
            <Outlet />
          </motion.div>
        </StyledContent>
      </Layout>
    </StyledLayout>
  )
}

export default AdminLayout
