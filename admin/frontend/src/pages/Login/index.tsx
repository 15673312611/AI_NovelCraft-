import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Form, Input, Button, message, Typography, Space } from 'antd'
import { UserOutlined, LockOutlined, ArrowRightOutlined, ThunderboltOutlined, SafetyOutlined } from '@ant-design/icons'
import { motion } from 'framer-motion'
import styled from '@emotion/styled'
import { adminAuthService } from '@/services/adminAuthService'

const { Text, Title } = Typography

// Filter out transient props (starting with $) from being passed to DOM
const shouldForwardProp = (prop: string) => !prop.startsWith('$')

// Styled Components
const PageWrapper = styled.div`
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #0a0a0a;
  position: relative;
  overflow: hidden;
`

const GridBackground = styled.div`
  position: absolute;
  inset: 0;
  background-image: 
    linear-gradient(rgba(14, 165, 233, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(14, 165, 233, 0.03) 1px, transparent 1px);
  background-size: 60px 60px;
  mask-image: radial-gradient(ellipse 80% 50% at 50% 50%, black 40%, transparent 100%);
  -webkit-mask-image: radial-gradient(ellipse 80% 50% at 50% 50%, black 40%, transparent 100%);
`

interface GlowOrbProps {
  $color?: string
  $size?: number
  $top?: string
  $bottom?: string
  $left?: string
  $right?: string
}

const GlowOrb = styled(motion.div, { shouldForwardProp })<GlowOrbProps>`
  position: absolute;
  width: ${props => props.$size}px;
  height: ${props => props.$size}px;
  background: radial-gradient(circle, ${props => props.$color} 0%, transparent 70%);
  border-radius: 50%;
  filter: blur(80px);
  top: ${props => props.$top};
  bottom: ${props => props.$bottom};
  left: ${props => props.$left};
  right: ${props => props.$right};
`

const ContentWrapper = styled.div`
  width: 100%;
  max-width: 420px;
  padding: 0 20px;
  position: relative;
  z-index: 1;
`

const LogoWrapper = styled.div`
  text-align: center;
  margin-bottom: 40px;
`

const LogoIcon = styled.div`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  background: linear-gradient(135deg, #0ea5e9, #06b6d4);
  border-radius: 20px;
  margin-bottom: 24px;
  font-size: 36px;
  box-shadow: 0 12px 40px rgba(14, 165, 233, 0.35);
  position: relative;
  
  &::before {
    content: '';
    position: absolute;
    inset: -3px;
    background: linear-gradient(135deg, #0ea5e9, #06b6d4);
    border-radius: 22px;
    opacity: 0.3;
    filter: blur(12px);
    z-index: -1;
  }
`

const LoginCard = styled.div`
  background: #171717;
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 20px;
  padding: 36px;
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.4);
`

const FeatureItem = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
`

const FeatureIcon = styled('div', { shouldForwardProp })<{ $color?: string }>`
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: ${props => props.$color}15;
  display: flex;
  align-items: center;
  justify-content: center;
  color: ${props => props.$color};
  font-size: 16px;
`

const Footer = styled.div`
  margin-top: 28px;
  text-align: center;
`

const Login = () => {
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()

  const onFinish = async (values: { username: string; password: string }) => {
    setLoading(true)
    try {
      const response = await adminAuthService.login(values.username, values.password)
      localStorage.setItem('admin_token', response.data?.token || (response as any).token)
      message.success('登录成功')
      navigate('/dashboard')
    } catch (error: any) {
      message.error(error.message || '登录失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <PageWrapper>
      <GridBackground />
      
      {/* Animated Glow Orbs */}
      <GlowOrb
        $color="rgba(14, 165, 233, 0.15)"
        $size={500}
        $top="15%"
        $right="5%"
        animate={{
          x: [0, 30, 0],
          y: [0, -30, 0],
        }}
        transition={{
          duration: 8,
          repeat: Infinity,
          ease: 'easeInOut',
        }}
      />
      <GlowOrb
        $color="rgba(6, 182, 212, 0.12)"
        $size={400}
        $bottom="10%"
        $left="5%"
        animate={{
          x: [0, -20, 0],
          y: [0, 20, 0],
        }}
        transition={{
          duration: 10,
          repeat: Infinity,
          ease: 'easeInOut',
        }}
      />

      <ContentWrapper>
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
        >
          {/* Logo */}
          <LogoWrapper>
            <LogoIcon>📚</LogoIcon>
            <Title 
              level={2} 
              style={{ 
                margin: 0, 
                color: '#fafafa',
                fontWeight: 700,
                letterSpacing: '-0.5px',
              }}
            >
              AI Novel Admin
            </Title>
            <Text style={{ color: 'rgba(250, 250, 250, 0.45)', fontSize: 14 }}>
              智能小说创作管理平台
            </Text>
          </LogoWrapper>

          {/* Login Card */}
          <LoginCard>
            <Form
              name="login"
              onFinish={onFinish}
              autoComplete="off"
              layout="vertical"
              size="large"
            >
              <Form.Item
                name="username"
                rules={[{ required: true, message: '请输入用户名' }]}
              >
                <Input
                  prefix={<UserOutlined style={{ color: 'rgba(250, 250, 250, 0.25)' }} />}
                  placeholder="用户名"
                  style={{ height: 48 }}
                />
              </Form.Item>

              <Form.Item
                name="password"
                rules={[{ required: true, message: '请输入密码' }]}
              >
                <Input.Password
                  prefix={<LockOutlined style={{ color: 'rgba(250, 250, 250, 0.25)' }} />}
                  placeholder="密码"
                  style={{ height: 48 }}
                />
              </Form.Item>

              <Form.Item style={{ marginBottom: 0, marginTop: 8 }}>
                <Button
                  type="primary"
                  htmlType="submit"
                  loading={loading}
                  block
                  icon={!loading && <ArrowRightOutlined />}
                  iconPosition="end"
                  style={{
                    height: 48,
                    fontSize: 15,
                    fontWeight: 600,
                  }}
                >
                  {loading ? '登录中...' : '登录'}
                </Button>
              </Form.Item>
            </Form>

            {/* Features */}
            <div style={{ 
              marginTop: 28, 
              paddingTop: 28, 
              borderTop: '1px solid rgba(255, 255, 255, 0.06)' 
            }}>
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                <FeatureItem>
                  <FeatureIcon $color="#0ea5e9">
                    <ThunderboltOutlined />
                  </FeatureIcon>
                  <div>
                    <Text style={{ fontSize: 13, fontWeight: 600, color: '#fafafa', display: 'block' }}>
                      AI 驱动
                    </Text>
                    <Text style={{ fontSize: 12, color: 'rgba(250, 250, 250, 0.45)' }}>
                      智能创作辅助系统
                    </Text>
                  </div>
                </FeatureItem>
                <FeatureItem>
                  <FeatureIcon $color="#22c55e">
                    <SafetyOutlined />
                  </FeatureIcon>
                  <div>
                    <Text style={{ fontSize: 13, fontWeight: 600, color: '#fafafa', display: 'block' }}>
                      安全可靠
                    </Text>
                    <Text style={{ fontSize: 12, color: 'rgba(250, 250, 250, 0.45)' }}>
                      企业级数据保护
                    </Text>
                  </div>
                </FeatureItem>
              </Space>
            </div>
          </LoginCard>

          {/* Footer */}
          <Footer>
            <Text style={{ color: 'rgba(250, 250, 250, 0.25)', fontSize: 12 }}>
              © 2024 AI Novel Platform. All rights reserved.
            </Text>
          </Footer>
        </motion.div>
      </ContentWrapper>
    </PageWrapper>
  )
}

export default Login
