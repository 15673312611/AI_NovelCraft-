import React, { useEffect, useState, useRef } from 'react'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { useSelector, useDispatch } from 'react-redux'
import { message, Input, Tooltip, Modal } from 'antd'
import { RootState, AppDispatch } from '@/store'
import { getProfile, logout } from '@/store/slices/authSlice'
import { 
  HomeIcon, 
  BookOpenIcon, 
  CommandLineIcon, 
  SparklesIcon, 
  UserIcon,
  BoltIcon,
  FilmIcon,
  Cog6ToothIcon,
  ArrowRightOnRectangleIcon,
  ChevronDownIcon,
  QuestionMarkCircleIcon,
  GiftIcon,
  WalletIcon,
  ClockIcon,
  ClipboardDocumentIcon,
  ChatBubbleLeftRightIcon
} from '@heroicons/react/24/outline'
import { creditService, UserCreditInfo } from '@/services/creditService'
import AnnouncementModal from '@/components/AnnouncementModal'
import RechargeModal from '@/components/RechargeModal'
import './ModernLayout.css'

const ModernLayout: React.FC<{ children?: React.ReactNode }> = ({ children }) => {
  const navigate = useNavigate()
  const location = useLocation()
  const dispatch = useDispatch<AppDispatch>()
  const { isAuthenticated, user } = useSelector((state: RootState) => state.auth)
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const dropdownRef = useRef<HTMLDivElement>(null)
  const [creditInfo, setCreditInfo] = useState<UserCreditInfo | null>(null)
  const [redeemCode, setRedeemCode] = useState('')
  const [redeemLoading, setRedeemLoading] = useState(false)
  const [contactModalVisible, setContactModalVisible] = useState(false)
  const [rechargeModalVisible, setRechargeModalVisible] = useState(false)

  useEffect(() => {
    if (isAuthenticated && !user) {
      dispatch(getProfile())
    }
  }, [isAuthenticated, user, dispatch])

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

  // 点击外部关闭下拉框
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setDropdownOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  // 格式化数字显示
  const formatNumber = (num: number) => {
    if (num >= 10000) {
      return (num / 10000).toFixed(1) + '万'
    }
    return num.toLocaleString()
  }

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

  const getPageTitle = () => {
    const path = location.pathname
    if (path === '/') return '创作中心'
    if (path.startsWith('/novels')) return '我的小说'
    if (path.startsWith('/short-stories')) return '短篇工厂'
    if (path.startsWith('/video-scripts')) return '剧本工厂'
    if (path.startsWith('/ai-')) return 'AI 助手'
    if (path.startsWith('/profile')) return '个人中心'
    if (path.startsWith('/settings')) return '设置'
    return '工作台'
  }

  const handleLogout = async () => {
    setDropdownOpen(false)
    try {
      await dispatch(logout()).unwrap()
      message.success('已退出登录')
    } catch (error) {
      // 即使logout失败也要清除本地状态
      message.info('已退出登录')
    } finally {
      // 确保跳转到登录页
      navigate('/login')
    }
  }

  const handleMenuClick = (path: string) => {
    setDropdownOpen(false)
    navigate(path)
  }

  const navItems = [
    { name: '首页', path: '/', icon: HomeIcon },
    { name: '我的小说', path: '/novels', icon: BookOpenIcon },
    { name: '短篇工厂', path: '/short-stories', icon: BoltIcon },
    { name: '剧本工厂', path: '/video-scripts', icon: FilmIcon },
  ]

  const toolItems = [
    { name: '生成器', path: '/ai-generators', icon: SparklesIcon },
    { name: '提示词库', path: '/prompt-library', icon: CommandLineIcon },
  ]

  const sidebarUserItems = [
    { name: '个人资料', path: '/profile', icon: UserIcon },
    { name: '设置', path: '/settings', icon: Cog6ToothIcon },
  ]

  const NavItem = ({ item }: { item: any }) => {
    const isActive = location.pathname === item.path
    return (
      <div 
        className={`nav-item ${isActive ? 'active' : ''}`}
        onClick={() => navigate(item.path)}
      >
        <item.icon />
        <span>{item.name}</span>
      </div>
    )
  }

  return (
    <div className="modern-layout">
      {/* 公告弹窗 */}
      <AnnouncementModal />
      
      {/* Sidebar */}
      <aside className="modern-sidebar">
        <div className="sidebar-logo">
          <SparklesIcon style={{ width: 28, height: 28 }} />
          <span>NovelCraft</span>
        </div>

        <nav className="sidebar-nav">
          <div className="nav-group">
            <div className="nav-category">创作中心</div>
            {navItems.map(item => <NavItem key={item.path} item={item} />)}
          </div>

          <div className="nav-group">
            <div className="nav-category">AI 助手</div>
            {toolItems.map(item => <NavItem key={item.path} item={item} />)}
          </div>

          <div className="nav-group" style={{ marginTop: 'auto' }}>
            <div className="nav-category">账户</div>
            {sidebarUserItems.map(item => <NavItem key={item.path} item={item} />)}
          </div>
        </nav>
      </aside>

      {/* 客服弹窗 */}
      <Modal
        title={null}
        open={contactModalVisible}
        onCancel={() => setContactModalVisible(false)}
        footer={null}
        width={360}
        centered
        className="contact-support-modal"
      >
        <div style={{ textAlign: 'center', padding: '20px 10px 10px' }}>
          <h3 style={{ marginBottom: '20px', fontWeight: 600, color: '#1e293b', fontSize: '18px' }}>
            扫码添加客服微信
          </h3>
          <div style={{ 
            background: '#f8fafc', 
            padding: '20px', 
            borderRadius: '16px',
            display: 'inline-block',
            marginBottom: '20px'
          }}>
            <img 
              src="/img/wx.png" 
              alt="微信二维码" 
              style={{ 
                width: '200px', 
                height: '200px', 
                borderRadius: '8px',
                display: 'block'
              }} 
            />
          </div>
          <div style={{ fontSize: '14px', color: '#64748b', lineHeight: '1.6' }}>
            遇到问题？<br/>
            请添加客服微信获取实时技术支持
          </div>
        </div>
      </Modal>

      {/* Recharge Modal */}
      <RechargeModal 
        visible={rechargeModalVisible} 
        onCancel={() => setRechargeModalVisible(false)}
        onSuccess={() => {
          setRechargeModalVisible(false)
          loadCreditInfo()
        }}
      />

      {/* Main Content */}
      <main className="modern-main">
        <header className="modern-header">
          <div className="header-left">
            <div className="header-title">
              <span style={{ color: 'var(--text-tertiary)', fontWeight: 500 }}>NovelCraft</span>
              <span style={{ color: 'var(--border-color)', margin: '0 4px' }}>/</span>
              <span>{getPageTitle()}</span>
            </div>
          </div>

          <div className="header-right">
            <button 
              className="header-contact-btn"
              onClick={() => setContactModalVisible(true)}
            >
              <ChatBubbleLeftRightIcon style={{ width: 18 }} />
              <span>联系客服</span>
            </button>
            
            {/* User Dropdown - 带字数信息 */}
            <div className="user-dropdown-wrapper" ref={dropdownRef}>
              <div 
                className={`user-profile-trigger ${dropdownOpen ? 'active' : ''}`}
                onClick={() => setDropdownOpen(!dropdownOpen)}
              >
                <div className="user-avatar">
                  {user?.avatarUrl ? (
                    <img src={user.avatarUrl} alt="avatar" />
                  ) : (
                    user?.nickname?.[0] || user?.username?.[0]?.toUpperCase() || 'U'
                  )}
                </div>
                <span className="user-name">{user?.nickname || user?.username || 'User'}</span>
                <ChevronDownIcon 
                  style={{ 
                    width: 14, 
                    color: 'var(--text-tertiary)',
                    transition: 'transform 0.2s',
                    transform: dropdownOpen ? 'rotate(180deg)' : 'rotate(0)'
                  }} 
                />
              </div>

              {/* 下拉菜单 - 新版带字数信息 */}
              {dropdownOpen && (
                <div className="user-dropdown-menu-v2">
                  {/* 用户信息头部 */}
                  <div className="dropdown-user-header-v2">
                    <div className="dropdown-avatar-v2">
                      {user?.avatarUrl ? (
                        <img src={user.avatarUrl} alt="avatar" />
                      ) : (
                        user?.nickname?.[0] || user?.username?.[0]?.toUpperCase() || 'U'
                      )}
                    </div>
                    <div className="dropdown-user-info-v2">
                      <div className="dropdown-username-v2">
                        {user?.nickname || user?.username || '用户'}
                        <span className="edit-icon-v2" onClick={() => handleMenuClick('/profile')}>✏️</span>
                      </div>
                      <div className="dropdown-user-id-v2" onClick={copyUserId}>
                        用户ID: {user?.id ? String(user.id).substring(0, 6).toUpperCase() : 'XXXXXX'}
                        <ClipboardDocumentIcon style={{ width: 12, height: 12, marginLeft: 4, opacity: 0.6 }} />
                      </div>
                    </div>
                  </div>

                  {/* 每日免费字数卡片 */}
                  {creditInfo?.dailyFreeEnabled && (
                    <div className="credit-card-v2 daily-free-card-v2">
                      <div className="credit-card-header-v2">
                        <span className="credit-card-title-v2">今日剩余免费字数</span>
                        <span className="daily-refresh-tag-v2">每日刷新</span>
                      </div>
                      <div className="credit-card-value-v2 daily-free-value-v2">
                        {formatNumber(creditInfo?.dailyFreeBalance || 0)}
                        <Tooltip title={`每日免费字数额度：${formatNumber(creditInfo?.dailyFreeAmount || 0)}，每天0点自动重置`}>
                          <QuestionMarkCircleIcon style={{ width: 16, height: 16, marginLeft: 6, opacity: 0.6, cursor: 'pointer' }} />
                        </Tooltip>
                      </div>
                    </div>
                  )}

                  {/* 字数包余额卡片 */}
                  <div className="credit-card-v2 package-card-v2">
                    <div className="credit-card-header-v2">
                      <span className="credit-card-title-v2">字数包剩余可用字数</span>
                    </div>
                    <div className="credit-card-value-v2 package-value-v2">
                      {formatNumber(creditInfo?.availableBalance || 0)}
                    </div>
                  </div>

                  {/* 兑换码区域 */}
                  <div className="redeem-section-v2">
                    <div className="redeem-title-v2">
                      <GiftIcon style={{ width: 16, height: 16 }} />
                      <span>兑换码</span>
                    </div>
                    <div className="redeem-input-wrapper-v2">
                      <Input
                        placeholder="请输入兑换码"
                        value={redeemCode}
                        onChange={(e) => setRedeemCode(e.target.value)}
                        className="redeem-input-v2"
                        size="small"
                      />
                      <button 
                        className="redeem-btn-v2"
                        onClick={handleRedeem}
                        disabled={redeemLoading}
                      >
                        兑换
                      </button>
                    </div>
                  </div>

                  {/* 菜单项 */}
                  <div className="dropdown-menu-items-v2">
                    <div className="dropdown-menu-item-v2" onClick={() => handleMenuClick('/profile')}>
                      <UserIcon style={{ width: 16, height: 16 }} />
                      <span>个人资料</span>
                    </div>
                    <div className="dropdown-menu-item-v2" onClick={() => handleMenuClick('/settings')}>
                      <ClockIcon style={{ width: 16, height: 16 }} />
                      <span>字数消费记录</span>
                    </div>
                    <div className="dropdown-menu-item-v2" onClick={() => setRechargeModalVisible(true)}>
                      <WalletIcon style={{ width: 16, height: 16 }} />
                      <span>充值</span>
                    </div>
                  </div>

                  {/* 退出登录 */}
                  <div className="dropdown-footer-v2">
                    <div className="dropdown-menu-item-v2 logout-v2" onClick={handleLogout}>
                      <ArrowRightOnRectangleIcon style={{ width: 16, height: 16 }} />
                      <span>退出登录</span>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        </header>

        <div className="modern-content">
          {children || <Outlet />}
        </div>
      </main>
    </div>
  )
}

export default ModernLayout
