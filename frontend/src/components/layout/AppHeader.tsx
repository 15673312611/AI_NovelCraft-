import React, { useEffect, useState } from 'react'
import { Layout, Avatar, Dropdown, Space, Input, message, Tooltip } from 'antd'
import { 
  UserOutlined, 
  LogoutOutlined, 
  EditOutlined,
  StarOutlined,
  GiftOutlined,
  WalletOutlined,
  HistoryOutlined,
  QuestionCircleOutlined,
  CopyOutlined
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useSelector, useDispatch } from 'react-redux'
import { RootState, AppDispatch } from '@/store'
import { logout, clearAuth } from '@/store/slices/authSlice'
import { creditService, UserCreditInfo } from '@/services/creditService'
import RechargeModal from '@/components/RechargeModal'
import './AppHeader.css'

const { Header } = Layout

const AppHeader: React.FC = () => {
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()
  const { user, isAuthenticated } = useSelector((state: RootState) => state.auth)
  const [creditInfo, setCreditInfo] = useState<UserCreditInfo | null>(null)
  const [redeemCode, setRedeemCode] = useState('')
  const [redeemLoading, setRedeemLoading] = useState(false)
  const [rechargeModalVisible, setRechargeModalVisible] = useState(false)

  // 加载字数信息
  useEffect(() => {
    if (isAuthenticated) {
      loadCreditInfo()
    }
  }, [isAuthenticated])

  const loadCreditInfo = async () => {
    try {
      const info = await creditService.getBalance()
      setCreditInfo(info)
    } catch (error) {
      console.error('加载字数信息失败:', error)
    }
  }

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

  // 复制用户ID
  const copyUserId = () => {
    if (user?.id) {
      navigator.clipboard.writeText(String(user.id))
      message.success('用户ID已复制')
    }
  }

  // 兑换码兑换
  const handleRedeem = async () => {
    if (!redeemCode.trim()) {
      message.warning('请输入兑换码')
      return
    }
    setRedeemLoading(true)
    try {
      // TODO: 调用兑换接口
      message.info('兑换功能开发中')
    } catch (error) {
      message.error('兑换失败')
    } finally {
      setRedeemLoading(false)
    }
  }

  // 格式化数字显示
  const formatNumber = (num: number) => {
    if (num >= 10000) {
      return (num / 10000).toFixed(1) + '万'
    }
    return num.toLocaleString()
  }

  // 自定义下拉菜单内容
  const dropdownContent = (
    <div className="user-dropdown-content">
      {/* 用户信息头部 */}
      <div className="dropdown-user-header">
        <Avatar 
          size={48} 
          className="dropdown-avatar"
          style={{ 
            background: 'linear-gradient(145deg, #3b82f6 0%, #1d4ed8 100%)',
            fontSize: '20px',
            fontWeight: 600
          }}
        >
          {user?.username?.[0]?.toUpperCase() || 'U'}
        </Avatar>
        <div className="dropdown-user-info">
          <div className="dropdown-username">
            {user?.username || '用户'}
            <EditOutlined className="edit-icon" onClick={() => navigate('/profile')} />
          </div>
          <div className="dropdown-user-id" onClick={copyUserId}>
            用户ID: {user?.id ? String(user.id).substring(0, 6).toUpperCase() : 'XXXXXX'}
            <CopyOutlined className="copy-icon" />
          </div>
        </div>
      </div>

      {/* 每日免费字数卡片 */}
      {creditInfo?.dailyFreeEnabled && (
        <div className="credit-card daily-free-card">
          <div className="credit-card-header">
            <span className="credit-card-title">今日剩余免费字数</span>
            <span className="daily-refresh-tag">每日刷新</span>
          </div>
          <div className="credit-card-value daily-free-value">
            {formatNumber(creditInfo?.dailyFreeBalance || 0)}
            <Tooltip title={`每日免费字数额度：${formatNumber(creditInfo?.dailyFreeAmount || 0)}，每天0点自动重置`}>
              <QuestionCircleOutlined className="help-icon" />
            </Tooltip>
          </div>
        </div>
      )}

      {/* 字数包余额卡片 */}
      <div className="credit-card package-card">
        <div className="credit-card-header">
          <span className="credit-card-title">字数包剩余可用字数</span>
        </div>
        <div className="credit-card-value package-value">
          {formatNumber(creditInfo?.availableBalance || 0)}
        </div>
      </div>

      {/* 兑换码区域 */}
      <div className="redeem-section">
        <div className="redeem-title">
          <GiftOutlined />
          <span>兑换码</span>
        </div>
        <div className="redeem-input-wrapper">
          <Input
            placeholder="请输入兑换码"
            value={redeemCode}
            onChange={(e) => setRedeemCode(e.target.value)}
            className="redeem-input"
          />
          <button 
            className="redeem-btn"
            onClick={handleRedeem}
            disabled={redeemLoading}
          >
            兑换
          </button>
        </div>
      </div>

      {/* 菜单项 */}
      <div className="dropdown-menu-items">
        <div className="dropdown-menu-item" onClick={() => navigate('/profile')}>
          <UserOutlined />
          <span>个人资料</span>
        </div>
        <div className="dropdown-menu-item" onClick={() => navigate('/settings')}>
          <HistoryOutlined />
          <span>字数消费记录</span>
        </div>
        <div className="dropdown-menu-item" onClick={() => setRechargeModalVisible(true)}>
          <WalletOutlined />
          <span>充值</span>
        </div>
      </div>

      {/* 退出登录 */}
      <div className="dropdown-footer">
        <div className="dropdown-menu-item logout-item" onClick={handleLogout}>
          <LogoutOutlined />
          <span>退出登录</span>
        </div>
      </div>
    </div>
  )

  return (
    <Header className="app-header">
      <div className="header-inner">
        <div className="logo" onClick={() => navigate('/') }>
          小说创作系统
        </div>

        <div className="header-right">
          {isAuthenticated ? (
            <Space size="middle">
              <Dropdown 
                dropdownRender={() => dropdownContent}
                placement="bottomRight"
                trigger={['click']}
                overlayClassName="user-dropdown-overlay"
              >
                <div className="user-info">
                  <div className="user-avatar-container">
                    <Avatar 
                      className="user-avatar" 
                      icon={<UserOutlined />}
                      style={{
                        background: 'linear-gradient(135deg, #4A90E2 0%, #5BA3F5 100%)',
                        border: '2px solid rgba(255, 255, 255, 0.85)'
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
      </div>
      <RechargeModal 
        visible={rechargeModalVisible} 
        onCancel={() => setRechargeModalVisible(false)} 
      />
    </Header>
  )
}

export default AppHeader 