import React, { useEffect, useState } from 'react'
import { Form, Input, Button, App } from 'antd'
import { useNavigate, Link } from 'react-router-dom'
import { useDispatch, useSelector } from 'react-redux'
import { AppDispatch, RootState } from '@/store'
import { clearError, setCredentials } from '@/store/slices/authSlice'
import SliderCaptcha from '@/components/SliderCaptcha'
import api from '@/services/api'
import './RegisterPage.css'

const RegisterPage: React.FC = () => {
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()
  const { message } = App.useApp()
  const { error, isAuthenticated } = useSelector((state: RootState) => state.auth)
  const [loading, setLoading] = useState(false)
  const [sendingCode, setSendingCode] = useState(false)
  const [countdown, setCountdown] = useState(0)
  const [showCaptcha, setShowCaptcha] = useState(false)
  const [form] = Form.useForm()

  useEffect(() => {
    if (isAuthenticated) navigate('/')
  }, [isAuthenticated, navigate])

  useEffect(() => {
    if (error) {
      message.error(error)
      dispatch(clearError())
    }
  }, [error, dispatch])

  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => setCountdown(countdown - 1), 1000)
      return () => clearTimeout(timer)
    }
  }, [countdown])

  const handleClickSendCode = () => {
    const email = form.getFieldValue('email')
    if (!email) return message.error('请先输入邮箱')
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return message.error('邮箱格式不正确')
    setShowCaptcha(true)
  }

  const handleCaptchaSuccess = async (captchaToken: string) => {
    setShowCaptcha(false)
    const email = form.getFieldValue('email')
    
    try {
      setSendingCode(true)
      await api.post('/auth/email/send-code', { email, type: 'REGISTER', captchaToken })
      message.success('验证码已发送')
      setCountdown(60)
    } catch (e: any) {
      message.error(e.message || '发送失败')
    } finally {
      setSendingCode(false)
    }
  }

  const onFinish = async (values: any) => {
    try {
      setLoading(true)
      const res: any = await api.post('/auth/register', {
        username: values.username,
        email: values.email,
        code: values.code,
        password: values.password,
      })
      
      // 后端返回格式: { success: true, data: { user, token } }
      const { user, token } = res.data || res
      dispatch(setCredentials({ user, token }))
      message.success('注册成功')
      navigate('/')
    } catch (e: any) {
      message.error(e.message || '注册失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="register-page">
      {/* 滑块验证 */}
      {showCaptcha && (
        <SliderCaptcha 
          onSuccess={handleCaptchaSuccess} 
          onClose={() => setShowCaptcha(false)} 
        />
      )}

      {/* 左侧视觉区 */}
      <div className="register-visual">
        <div className="visual-shapes">
          <div className="shape shape-1" />
          <div className="shape shape-2" />
          <div className="shape shape-3" />
        </div>
        
        <div className="visual-content">
          <div className="visual-logo">
            <svg viewBox="0 0 24 24" fill="none">
              <path d="M4 6h16M4 10h16M4 14h12M4 18h14" stroke="#fff" strokeWidth="2" strokeLinecap="round"/>
            </svg>
          </div>
          
          <h1 className="visual-title">开启你的创作旅程</h1>
          <p className="visual-desc">
            加入数千名创作者，使用 AI 辅助工具让写作更高效、更有灵感。
          </p>

          <div className="feature-cards">
            <div className="feature-card">
              <div className="feature-icon">
                <svg viewBox="0 0 24 24" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>
                </svg>
              </div>
              <span className="feature-text">智能大纲，快速构建故事结构</span>
            </div>
            <div className="feature-card">
              <div className="feature-icon">
                <svg viewBox="0 0 24 24" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                  <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                </svg>
              </div>
              <span className="feature-text">AI 续写润色，提升内容质量</span>
            </div>
            <div className="feature-card">
              <div className="feature-icon">
                <svg viewBox="0 0 24 24" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                  <line x1="9" y1="9" x2="15" y2="15"/>
                  <line x1="15" y1="9" x2="9" y2="15"/>
                </svg>
              </div>
              <span className="feature-text">云端同步，随时随地创作</span>
            </div>
          </div>
        </div>
      </div>

      {/* 右侧表单区 */}
      <div className="register-form-section">
        <div className="form-container">
          <div className="form-header">
            <h2 className="form-title">创建账户</h2>
            <p className="form-subtitle">已有账户？<Link to="/login">立即登录</Link></p>
          </div>

          <Form form={form} onFinish={onFinish} layout="vertical" requiredMark={false}>
            <Form.Item
              name="username"
              rules={[
                { required: true, message: '请输入用户名' },
                { min: 3, max: 20, message: '3-20个字符' },
                { pattern: /^[a-zA-Z0-9_]+$/, message: '仅支持字母、数字、下划线' },
              ]}
            >
              <Input placeholder="用户名" />
            </Form.Item>

            <Form.Item
              name="password"
              rules={[
                { required: true, message: '请输入密码' },
                { min: 6, max: 20, message: '6-20个字符' },
              ]}
            >
              <Input.Password placeholder="密码" />
            </Form.Item>

            <Form.Item
              name="confirmPassword"
              dependencies={['password']}
              rules={[
                { required: true, message: '请确认密码' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue('password') === value) return Promise.resolve()
                    return Promise.reject(new Error('密码不一致'))
                  },
                }),
              ]}
            >
              <Input.Password placeholder="确认密码" />
            </Form.Item>

            <div className="section-divider">
              <span>邮箱验证</span>
            </div>

            <Form.Item
              name="email"
              rules={[
                { required: true, message: '请输入邮箱' },
                { type: 'email', message: '邮箱格式不正确' },
              ]}
            >
              <Input placeholder="邮箱地址" />
            </Form.Item>

            <Form.Item
              name="code"
              rules={[
                { required: true, message: '请输入验证码' },
                { pattern: /^\d{6}$/, message: '6位数字验证码' },
              ]}
            >
              <div className="code-row">
                <Input placeholder="验证码" maxLength={6} />
                <div 
                  className={`send-code-btn ${countdown > 0 || sendingCode ? 'disabled' : ''}`}
                  onClick={countdown > 0 || sendingCode ? undefined : handleClickSendCode}
                >
                  {sendingCode ? '发送中...' : countdown > 0 ? `${countdown}s` : '获取验证码'}
                </div>
              </div>
            </Form.Item>

            <Form.Item>
              <Button type="primary" htmlType="submit" loading={loading} block className="submit-btn">
                创建账户
              </Button>
            </Form.Item>
          </Form>

          <p className="terms">
            注册即表示同意 <a href="#">服务条款</a> 和 <a href="#">隐私政策</a>
          </p>
        </div>
      </div>
    </div>
  )
}

export default RegisterPage
