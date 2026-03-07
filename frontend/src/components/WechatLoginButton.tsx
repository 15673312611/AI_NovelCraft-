import React, { useEffect, useState } from 'react'
import { Button, message } from 'antd'
import { WechatOutlined } from '@ant-design/icons'
import { wechatAuthService, WechatConfig } from '../services/wechatAuthService'
import { useNavigate } from 'react-router-dom'
import { useDispatch } from 'react-redux'
import { AppDispatch } from '@/store'
import { setCredentials } from '@/store/slices/authSlice'
import './WechatLoginButton.css'

interface WechatLoginButtonProps {
  onSuccess?: () => void
  text?: string
  block?: boolean
}

const WechatLoginButton: React.FC<WechatLoginButtonProps> = ({ 
  onSuccess, 
  text = '微信登录',
  block = false 
}) => {
  const [config, setConfig] = useState<WechatConfig | null>(null)
  const [loading, setLoading] = useState(true)
  const [authLoading, setAuthLoading] = useState(false)
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()

  useEffect(() => {
    loadConfig()
    handleWechatCallback()
  }, [])

  const loadConfig = async () => {
    try {
      const cfg = await wechatAuthService.getConfig()
      setConfig(cfg)
    } catch (error) {
      console.error('加载微信配置失败:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleWechatCallback = async () => {
    const urlParams = new URLSearchParams(window.location.search)
    const code = urlParams.get('code')
    const state = urlParams.get('state')
    const savedState = sessionStorage.getItem('wechat_state')

    if (code && state && savedState === state) {
      sessionStorage.removeItem('wechat_state')
      
      try {
        const result = await wechatAuthService.login(code, state, 'mp')
        
        localStorage.setItem('token', result.token)
        dispatch(setCredentials({ user: result.user, token: result.token }))
        
        message.success('微信登录成功！')
        
        window.history.replaceState({}, document.title, window.location.pathname)
        
        onSuccess?.()
        navigate('/')
      } catch (error: any) {
        message.error(error.message || '微信登录失败')
      }
    }
  }

  const handleWechatLogin = async () => {
    if (!config?.mpEnabled) {
      message.warning('微信登录暂未开放')
      return
    }

    setAuthLoading(true)
    try {
      const { authUrl, state } = await wechatAuthService.getAuthUrl('mp')
      
      sessionStorage.setItem('wechat_state', state)
      
      window.location.href = authUrl
    } catch (error: any) {
      message.error(error.message || '获取微信授权失败')
    } finally {
      setAuthLoading(false)
    }
  }

  if (loading) {
    return null
  }

  if (!config?.mpEnabled) {
    return null
  }

  return (
    <Button
      className="wechat-login-btn"
      icon={<WechatOutlined />}
      onClick={handleWechatLogin}
      loading={authLoading}
      block={block}
      size="large"
    >
      {text}
    </Button>
  )
}

export default WechatLoginButton
