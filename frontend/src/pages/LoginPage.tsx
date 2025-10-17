import React, { useEffect } from 'react'
import { Form, Input, Button, Card, Typography, Checkbox, App } from 'antd'
import { UserOutlined, LockOutlined } from '@ant-design/icons'
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
      navigate('/dashboard')
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
      navigate('/dashboard')
    } catch (error) {
      // 错误已经在useEffect中处理
    }
  }

  return (
    <div className="login-page">
      <div className="login-container">
        <Card className="login-card">
          <div className="login-header">
            <Title level={2} className="login-title">
              📚 小说创作系统
            </Title>
            <Text className="login-subtitle">
              欢迎回来！请登录您的账户，继续您的创作之旅
            </Text>
          </div>

          <Form
            name="login"
            className="login-form"
            onFinish={onFinish}
            autoComplete="off"
            size="large"
          >
            <Form.Item
              name="usernameOrEmail"
              rules={[
                { required: true, message: '请输入用户名或邮箱' },
              ]}
            >
              <Input
                prefix={<UserOutlined />}
                placeholder="用户名或邮箱"
                autoComplete="username"
              />
            </Form.Item>

            <Form.Item
              name="password"
              rules={[
                { required: true, message: '请输入密码' },
                { min: 6, message: '密码长度不能少于6位' },
              ]}
            >
              <Input.Password
                prefix={<LockOutlined />}
                placeholder="密码"
                autoComplete="current-password"
              />
            </Form.Item>

            <Form.Item>
              <Form.Item name="rememberMe" valuePropName="checked" noStyle>
                <Checkbox>记住我</Checkbox>
              </Form.Item>
            </Form.Item>

            <Form.Item>
              <Button
                type="primary"
                htmlType="submit"
                className="login-button"
                loading={loading}
                block
              >
                登录
              </Button>
            </Form.Item>

            <div className="login-footer">
              <Text>
                还没有账户？{' '}
                <Link to="/register" className="register-link">
                  立即注册
                </Link>
              </Text>
            </div>
          </Form>
        </Card>
      </div>
    </div>
  )
}

export default LoginPage 