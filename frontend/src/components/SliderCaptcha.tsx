import React, { useState, useRef, useEffect } from 'react'
import api from '@/services/api'
import './SliderCaptcha.css'

interface CaptchaProps {
  onSuccess: (token: string) => void
  onClose: () => void
}

interface MousePoint {
  x: number
  y: number
  t: number
}

const ClickCaptcha: React.FC<CaptchaProps> = ({ onSuccess, onClose }) => {
  const [status, setStatus] = useState<'loading' | 'ready' | 'verifying' | 'success' | 'error'>('loading')
  const [token, setToken] = useState('')
  const [errorMsg, setErrorMsg] = useState('')
  
  const mouseTrackRef = useRef<MousePoint[]>([])
  const pageLoadTimeRef = useRef(Date.now())

  useEffect(() => {
    fetchToken()
    pageLoadTimeRef.current = Date.now()
    
    const handleMouseMove = (e: MouseEvent) => {
      mouseTrackRef.current.push({
        x: e.clientX,
        y: e.clientY,
        t: Date.now() - pageLoadTimeRef.current
      })
      if (mouseTrackRef.current.length > 100) {
        mouseTrackRef.current = mouseTrackRef.current.slice(-100)
      }
    }
    
    document.addEventListener('mousemove', handleMouseMove)
    return () => document.removeEventListener('mousemove', handleMouseMove)
  }, [])

  const fetchToken = async () => {
    setStatus('loading')
    setErrorMsg('')
    mouseTrackRef.current = []
    pageLoadTimeRef.current = Date.now()
    
    try {
      const res: any = await api.post('/auth/email/captcha/token')
      setToken(res.token)
      setStatus('ready')
    } catch (e: any) {
      setErrorMsg(e.message || '加载失败')
      setStatus('error')
    }
  }

  const handleClick = async () => {
    if (status !== 'ready') return
    
    setStatus('verifying')
    const clickTime = Date.now() - pageLoadTimeRef.current
    
    try {
      await api.post('/auth/email/captcha/verify', {
        token,
        mouseTrack: mouseTrackRef.current,
        clickTime
      })
      setStatus('success')
      setTimeout(() => onSuccess(token), 600)
    } catch (e: any) {
      setErrorMsg(e.message || '验证失败')
      setStatus('error')
      setTimeout(fetchToken, 1500)
    }
  }

  return (
    <div className="verify-overlay" onClick={onClose}>
      <div className="verify-modal" onClick={e => e.stopPropagation()}>
        {/* 顶部装饰 */}
        <div className="verify-decoration">
          <div className="decoration-circle c1" />
          <div className="decoration-circle c2" />
          <div className="decoration-circle c3" />
        </div>

        {/* 关闭按钮 */}
        <button className="verify-close" onClick={onClose}>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M18 6L6 18M6 6l12 12"/>
          </svg>
        </button>

        {/* 主体内容 */}
        <div className="verify-content">
          {/* 图标 */}
          <div className={`verify-icon-wrapper ${status}`}>
            {status === 'loading' && (
              <div className="verify-spinner" />
            )}
            {status === 'ready' && (
              <svg className="shield-icon" viewBox="0 0 24 24" fill="none">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" stroke="#6366f1" strokeWidth="1.5" fill="none"/>
                <path d="M9 12l2 2 4-4" stroke="#6366f1" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            )}
            {status === 'verifying' && (
              <div className="verify-spinner" />
            )}
            {status === 'success' && (
              <svg className="success-icon" viewBox="0 0 24 24" fill="none">
                <circle cx="12" cy="12" r="10" fill="#22c55e"/>
                <path d="M8 12l3 3 5-6" stroke="#fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            )}
            {status === 'error' && (
              <svg className="error-icon" viewBox="0 0 24 24" fill="none">
                <circle cx="12" cy="12" r="10" fill="#ef4444"/>
                <path d="M15 9l-6 6M9 9l6 6" stroke="#fff" strokeWidth="2" strokeLinecap="round"/>
              </svg>
            )}
          </div>

          {/* 标题 */}
          <h3 className="verify-title">
            {status === 'loading' && '正在加载'}
            {status === 'ready' && '安全验证'}
            {status === 'verifying' && '验证中'}
            {status === 'success' && '验证成功'}
            {status === 'error' && '验证失败'}
          </h3>

          {/* 描述 */}
          <p className="verify-desc">
            {status === 'loading' && '请稍候...'}
            {status === 'ready' && '点击下方按钮完成人机验证'}
            {status === 'verifying' && '正在分析您的操作...'}
            {status === 'success' && '您已通过验证'}
            {status === 'error' && (errorMsg || '请重试')}
          </p>

          {/* 验证按钮 */}
          {(status === 'ready' || status === 'loading') && (
            <button 
              className={`verify-btn ${status === 'loading' ? 'disabled' : ''}`}
              onClick={handleClick}
              disabled={status === 'loading'}
            >
              <span className="btn-text">我不是机器人</span>
              <span className="btn-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M5 12h14M12 5l7 7-7 7"/>
                </svg>
              </span>
            </button>
          )}
        </div>

        {/* 底部 */}
        <div className="verify-footer">
          <svg viewBox="0 0 24 24" fill="none" className="footer-icon">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" stroke="currentColor" strokeWidth="1.5"/>
          </svg>
          <span>安全验证 · 保护您的账户</span>
        </div>
      </div>
    </div>
  )
}

export default ClickCaptcha
