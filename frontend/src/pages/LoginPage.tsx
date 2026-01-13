import React, { useEffect } from 'react'
import { Form, Input, Button, Checkbox, App } from 'antd'
import { useNavigate, Link } from 'react-router-dom'
import { useDispatch, useSelector } from 'react-redux'
import { AppDispatch, RootState } from '@/store'
import { login, clearError } from '@/store/slices/authSlice'
import WechatLoginButton from '@/components/WechatLoginButton'
import './LoginPage.css'

const LoginPage: React.FC = () => {
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()
  const { message } = App.useApp()
  const { loading, error, isAuthenticated } = useSelector((state: RootState) => state.auth)

  useEffect(() => {
    if (isAuthenticated) navigate('/')
  }, [isAuthenticated, navigate])

  useEffect(() => {
    if (error) {
      message.error(error)
      dispatch(clearError())
    }
  }, [error, dispatch])

  const onFinish = async (values: any) => {
    try {
      await dispatch(login({
        usernameOrEmail: values.usernameOrEmail,
        password: values.password,
      })).unwrap()
      message.success('登录成功')
      navigate('/')
    } catch (e) {}
  }

  return (
    <div className="login-page">
      {/* 左侧视觉区 */}
      <div className="login-visual">
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
          
          <h1 className="visual-title">欢迎回来</h1>
          <p className="visual-desc">
            登录你的账户，继续你的创作之旅。AI 助手已准备就绪，随时为你提供灵感。
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
      <div className="login-form-section">
        <div className="form-container">
          <div className="form-header">
            <h2 className="form-title">登录账户</h2>
            <p className="form-subtitle">还没有账户？<Link to="/register">立即注册</Link></p>
          </div>

          <Form onFinish={onFinish} layout="vertical" requiredMark={false} initialValues={{ remember: true }}>
            <Form.Item
              name="usernameOrEmail"
              rules={[{ required: true, message: '请输入用户名或邮箱' }]}
            >
              <Input placeholder="用户名或邮箱" />
            </Form.Item>

            <Form.Item
              name="password"
              rules={[{ required: true, message: '请输入密码' }]}
            >
              <Input.Password placeholder="密码" />
            </Form.Item>

            <div className="options-row">
              <Form.Item name="remember" valuePropName="checked" noStyle>
                <Checkbox>记住我</Checkbox>
              </Form.Item>
              <Link to="/forgot-password" className="forgot-link">忘记密码？</Link>
            </div>

            <Form.Item>
              <Button type="primary" htmlType="submit" loading={loading} block className="submit-btn">
                登录
              </Button>
            </Form.Item>
          </Form>

          <div className="divider-row">
            <span>其他登录方式</span>
          </div>

          <div className="social-login">
            <WechatLoginButton block />
          </div>

          <p className="terms">
            登录即表示同意 <a href="#">服务条款</a> 和 <a href="#">隐私政策</a>
          </p>
        </div>
      </div>
    </div>
  )
}

export default LoginPage
