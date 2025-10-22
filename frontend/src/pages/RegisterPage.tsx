import React, { useEffect } from 'react'
import { Form, Input, Button, Typography, message } from 'antd'
import { useNavigate, Link } from 'react-router-dom'
import { useDispatch, useSelector } from 'react-redux'
import { AppDispatch, RootState } from '@/store'
import { register, clearError } from '@/store/slices/authSlice'
import './RegisterPage.css'

const { Title, Text } = Typography

interface RegisterForm {
  username: string
  email: string
  password: string
  confirmPassword: string
}

const RegisterPage: React.FC = () => {
  const navigate = useNavigate()
  const dispatch = useDispatch<AppDispatch>()
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

  const onFinish = async (values: RegisterForm) => {
    try {
      await dispatch(register({
        username: values.username,
        email: values.email,
        password: values.password,
      })).unwrap()
      
      message.success('注册成功！')
      navigate('/')
    } catch (error) {
      // 错误已经在useEffect中处理
    }
  }

  return (
    <div className="register-page">
      {/* 左侧装饰区域 */}
      <div className="register-left">
        <div className="brand-section">
          <div className="brand-logo">
            <svg width="56" height="56" viewBox="0 0 56 56" fill="none">
              <defs>
                <linearGradient id="logoGradientReg" x1="0" y1="0" x2="56" y2="56" gradientUnits="userSpaceOnUse">
                  <stop offset="0%" stopColor="#FFFFFF" />
                  <stop offset="100%" stopColor="rgba(255, 255, 255, 0.9)" />
                </linearGradient>
                <filter id="glowReg">
                  <feGaussianBlur stdDeviation="2" result="coloredBlur"/>
                  <feMerge>
                    <feMergeNode in="coloredBlur"/>
                    <feMergeNode in="SourceGraphic"/>
                  </feMerge>
                </filter>
              </defs>
              <rect width="56" height="56" rx="16" fill="url(#logoGradientReg)" filter="url(#glowReg)"/>
              <path d="M18 16h20v2.5H18v-2.5zm0 7h20v2.5H18v-2.5zm0 7h14v2.5H18v-2.5zm0 7h16v2.5H18v-2.5z" fill="#FF6B35" opacity="0.9" />
              <path d="M32 32l5-5 2.5 2.5-5 5-2.5-2.5z" fill="#FF9F1C" />
              <circle cx="34" cy="34" r="1.5" fill="#FFD23F"/>
            </svg>
          </div>
          <h1 className="brand-title">开始创作之旅</h1>
          <p className="brand-description">
            加入我们的创作者社区<br />
            用AI赋能您的写作灵感
          </p>
          
          <div className="feature-list">
            <div className="feature-item">
              <div className="feature-icon">✨</div>
              <div className="feature-content">
                <div className="feature-title">AI智能辅助</div>
                <div className="feature-desc">提供灵感和创意建议</div>
              </div>
            </div>
            <div className="feature-item">
              <div className="feature-icon">📚</div>
              <div className="feature-content">
                <div className="feature-title">完善的创作工具</div>
                <div className="feature-desc">从大纲到章节的全流程支持</div>
              </div>
            </div>
            <div className="feature-item">
              <div className="feature-icon">🚀</div>
              <div className="feature-content">
                <div className="feature-title">高效的写作体验</div>
                <div className="feature-desc">专注创作，无需分心</div>
              </div>
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

      {/* 右侧注册表单 */}
      <div className="register-right smooth-scroll">
        <div className="register-container">
          <div className="register-header">
            <Title level={2} className="register-title">
              创建新账户
            </Title>
            <Text className="register-subtitle">
              开启您的AI辅助创作之旅
            </Text>
          </div>

          <Form
            name="register"
            className="register-form"
            onFinish={onFinish}
            autoComplete="off"
          >
            <Form.Item
              name="username"
              rules={[
                { required: true, message: '请输入用户名' },
                { min: 3, message: '用户名长度不能少于3位' },
                { max: 20, message: '用户名长度不能超过20位' },
                { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字和下划线' },
              ]}
            >
              <div className="input-wrapper">
                <label className="input-label">用户名</label>
                <Input
                  placeholder="请输入用户名"
                  autoComplete="username"
                  className="modern-input"
                />
              </div>
            </Form.Item>

            <Form.Item
              name="email"
              rules={[
                { required: true, message: '请输入邮箱' },
                { type: 'email', message: '请输入有效的邮箱地址' },
              ]}
            >
              <div className="input-wrapper">
                <label className="input-label">邮箱</label>
                <Input
                  placeholder="请输入邮箱"
                  autoComplete="email"
                  className="modern-input"
                />
              </div>
            </Form.Item>

            <Form.Item
              name="password"
              rules={[
                { required: true, message: '请输入密码' },
                { min: 6, message: '密码长度不能少于6位' },
                { max: 20, message: '密码长度不能超过20位' },
              ]}
            >
              <div className="input-wrapper">
                <label className="input-label">密码</label>
                <Input.Password
                  placeholder="请输入密码（至少6位）"
                  autoComplete="new-password"
                  className="modern-input"
                />
              </div>
            </Form.Item>

            <Form.Item
              name="confirmPassword"
              dependencies={['password']}
              rules={[
                { required: true, message: '请确认密码' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue('password') === value) {
                      return Promise.resolve()
                    }
                    return Promise.reject(new Error('两次输入的密码不一致'))
                  },
                }),
              ]}
            >
              <div className="input-wrapper">
                <label className="input-label">确认密码</label>
                <Input.Password
                  placeholder="请再次输入密码"
                  autoComplete="new-password"
                  className="modern-input"
                />
              </div>
            </Form.Item>

            <Form.Item style={{ marginTop: '32px' }}>
              <Button
                type="primary"
                htmlType="submit"
                className="register-button"
                loading={loading}
                block
                size="large"
              >
                {loading ? '注册中...' : '创建账户'}
              </Button>
            </Form.Item>

            <div className="register-footer">
              <Text className="footer-text">
                已有账户？{' '}
                <Link to="/login" className="login-link">
                  立即登录
                </Link>
              </Text>
            </div>
          </Form>

          {/* 底部提示 */}
          <div className="register-bottom">
            <Text className="bottom-text">
              注册即表示您同意我们的
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

export default RegisterPage 