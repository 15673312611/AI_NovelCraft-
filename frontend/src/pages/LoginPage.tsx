import React, { useEffect } from 'react'
import { Form, Input, Button, Typography, Checkbox, App } from 'antd'
import { useNavigate, Link } from 'react-router-dom'
import { useDispatch, useSelector } from 'react-redux'
import { AppDispatch, RootState } from '@/store'
import { login, clearError } from '@/store/slices/authSlice'
import './LoginPage.css'

const { Title, Text } = Typography

interface LoginForm {
  usernameOrEmail: string
  password: string
  rememberMe: boolean
}

const LoginPage: React.FC = () => {
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()
  const { message } = App.useApp()
  const { loading, error, isAuthenticated } = useSelector((state: RootState) => state.auth)

  useEffect(() => {
    if (isAuthenticated) {
      navigate('/')
    }
  }, [isAuthenticated, navigate])

  useEffect(() => {
    if (error) {
      message.error(error)
      dispatch(clearError())
    }
  }, [error, dispatch])

  const onFinish = async (values: LoginForm) => {
    try {
      await dispatch(login({
        usernameOrEmail: values.usernameOrEmail,
        password: values.password,
      })).unwrap()
      
      message.success('登录成功！')
      navigate('/')
    } catch (error) {
      // 错误已经在useEffect中处理
    }
  }

  return (
    <div className="login-page">
      {/* 左侧装饰区域 */}
      <div className="login-left">
        <div className="brand-section">
          <div className="brand-logo">
            <svg width="56" height="56" viewBox="0 0 56 56" fill="none">
              <defs>
                <linearGradient id="logoGradient" x1="0" y1="0" x2="56" y2="56" gradientUnits="userSpaceOnUse">
                  <stop offset="0%" stopColor="#FFFFFF" />
                  <stop offset="100%" stopColor="rgba(255, 255, 255, 0.9)" />
                </linearGradient>
                <filter id="glow">
                  <feGaussianBlur stdDeviation="2" result="coloredBlur"/>
                  <feMerge>
                    <feMergeNode in="coloredBlur"/>
                    <feMergeNode in="SourceGraphic"/>
                  </feMerge>
                </filter>
              </defs>
              <rect width="56" height="56" rx="16" fill="url(#logoGradient)" filter="url(#glow)"/>
              <path d="M18 16h20v2.5H18v-2.5zm0 7h20v2.5H18v-2.5zm0 7h14v2.5H18v-2.5zm0 7h16v2.5H18v-2.5z" fill="#FF6B35" opacity="0.9" />
              <path d="M32 32l5-5 2.5 2.5-5 5-2.5-2.5z" fill="#FF9F1C" />
              <circle cx="34" cy="34" r="1.5" fill="#FFD23F"/>
            </svg>
          </div>
          <h1 className="brand-title">小说创作系统</h1>
          <p className="brand-description">
            AI驱动的智能创作平台<br />
            让每一个故事都精彩绝伦
          </p>
          
          <div className="brand-stats">
            <div className="stat-item">
              <div className="stat-number">10K+</div>
              <div className="stat-label">活跃作者</div>
            </div>
            <div className="stat-item">
              <div className="stat-number">50K+</div>
              <div className="stat-label">创作作品</div>
            </div>
            <div className="stat-item">
              <div className="stat-number">100M+</div>
              <div className="stat-label">累计字数</div>
            </div>
          </div>
        </div>

        {/* 几何装饰 */}
        <div className="geometric-bg">
          <div className="geo-line geo-line-1"></div>
          <div className="geo-line geo-line-2"></div>
          <div className="geo-circle geo-circle-1"></div>
          <div className="geo-circle geo-circle-2"></div>
        </div>
      </div>

      {/* 右侧登录表单 */}
      <div className="login-right">
        <div className="login-container">
          <div className="login-header">
            <Title level={2} className="login-title">
              欢迎回来
            </Title>
            <Text className="login-subtitle">
              登录您的账户，继续创作之旅
            </Text>
          </div>

          <Form
            name="login"
            className="login-form"
            onFinish={onFinish}
            autoComplete="off"
            initialValues={{ rememberMe: true }}
          >
            <Form.Item
              name="usernameOrEmail"
              rules={[
                { required: true, message: '请输入用户名或邮箱' },
              ]}
            >
              <div className="input-wrapper">
                <label className="input-label">用户名或邮箱</label>
                <Input
                  placeholder="请输入用户名或邮箱"
                  autoComplete="username"
                  className="modern-input"
                />
              </div>
            </Form.Item>

            <Form.Item
              name="password"
              rules={[
                { required: true, message: '请输入密码' },
                { min: 6, message: '密码长度不能少于6位' },
              ]}
            >
              <div className="input-wrapper">
                <label className="input-label">密码</label>
                <Input.Password
                  placeholder="请输入密码"
                  autoComplete="current-password"
                  className="modern-input"
                />
              </div>
            </Form.Item>

            <div className="login-options">
              <Form.Item name="rememberMe" valuePropName="checked" noStyle>
                <Checkbox className="modern-checkbox">记住我</Checkbox>
              </Form.Item>
              <Link to="/forgot-password" className="forgot-link">
                忘记密码？
              </Link>
            </div>

            <Form.Item style={{ marginTop: '32px' }}>
              <Button
                type="primary"
                htmlType="submit"
                className="login-button"
                loading={loading}
                block
                size="large"
              >
                {loading ? '登录中...' : '登录'}
              </Button>
            </Form.Item>

            <div className="login-footer">
              <Text className="footer-text">
                还没有账户？{' '}
                <Link to="/register" className="register-link">
                  立即注册
                </Link>
              </Text>
            </div>
          </Form>

          {/* 底部提示 */}
          <div className="login-bottom">
            <Text className="bottom-text">
              登录即表示您同意我们的
              <a href="#" className="policy-link">服务条款</a>
              和
              <a href="#" className="policy-link">隐私政策</a>
            </Text>
          </div>
        </div>
      </div>
    </div>
  )
}

export default LoginPage 