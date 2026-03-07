import { useEffect, useState } from 'react'
import { 
  Form, Input, Switch, Button, message, Space, Alert, Typography, 
  Spin, Select, Row, Col 
} from 'antd'
import { 
  MailOutlined, SaveOutlined, CheckCircleOutlined, ExclamationCircleOutlined,
  LockOutlined, CloudServerOutlined, ClockCircleOutlined, UserOutlined
} from '@ant-design/icons'
import styled from '@emotion/styled'
import { motion } from 'framer-motion'
import request from '@/services/request'
import { PageContainer, InfoCard } from '@/components'

const { Text, Paragraph } = Typography

// Styled Components
const LoadingWrapper = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 100px 0;
`

const TipCard = styled.div`
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 16px 20px;
  background: rgba(14, 165, 233, 0.08);
  border: 1px solid rgba(14, 165, 233, 0.15);
  border-radius: 12px;
  margin-bottom: 24px;
`

const TipIcon = styled.div`
  font-size: 24px;
  flex-shrink: 0;
`

const ActionBar = styled.div`
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
`

const EmailConfigPage = () => {
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [testing, setTesting] = useState(false)
  const [testResult, setTestResult] = useState<{ valid: boolean; message: string } | null>(null)

  useEffect(() => {
    loadConfig()
  }, [])

  const loadConfig = async () => {
    try {
      const config: any = await request.get('/api/admin/email-config')
      form.setFieldsValue({
        enabled: config.enabled,
        smtpHost: config.smtpHost || 'smtp.163.com',
        smtpPort: config.smtpPort || '465',
        smtpSsl: config.smtpSsl !== false,
        smtpUsername: config.smtpUsername,
        smtpPassword: config.smtpPassword,
        fromName: config.fromName || 'AI小说创作系统',
        codeExpireMinutes: config.codeExpireMinutes || '5'
      })
    } catch (error) {
      message.error('加载配置失败')
    } finally {
      setLoading(false)
    }
  }

  const handleSave = async () => {
    try {
      const values = await form.validateFields()
      setSaving(true)
      await request.post('/api/admin/email-config', {
        enabled: values.enabled,
        smtpHost: values.smtpHost,
        smtpPort: values.smtpPort,
        smtpSsl: values.smtpSsl,
        smtpUsername: values.smtpUsername,
        smtpPassword: values.smtpPassword,
        fromName: values.fromName,
        codeExpireMinutes: values.codeExpireMinutes
      })
      message.success('配置保存成功')
      setTestResult(null)
    } catch (error: any) {
      message.error(error.message || '保存失败')
    } finally {
      setSaving(false)
    }
  }

  const handleTest = async () => {
    try {
      const values = await form.validateFields()
      setTesting(true)
      const result: any = await request.post('/api/admin/email-config/test', {
        testEmail: values.smtpUsername
      })
      setTestResult(result)
      if (result.valid) {
        message.success('配置验证通过')
      } else {
        message.warning(result.message)
      }
    } catch (error: any) {
      message.error(error.message || '测试失败')
    } finally {
      setTesting(false)
    }
  }

  if (loading) {
    return (
      <LoadingWrapper>
        <Spin size="large" />
        <Text style={{ marginTop: 16, color: 'rgba(250, 250, 250, 0.45)' }}>加载配置中...</Text>
      </LoadingWrapper>
    )
  }

  return (
    <PageContainer
      title="邮箱验证配置"
      description="配置SMTP邮箱服务，用于用户注册时发送验证码"
      icon={<MailOutlined />}
      breadcrumb={[{ title: '邮箱验证' }]}
      extra={
        <Form.Item name="enabled" valuePropName="checked" noStyle>
          <Switch checkedChildren="已启用" unCheckedChildren="已禁用" />
        </Form.Item>
      }
    >
      <Form form={form} layout="vertical" requiredMark={false}>
        {/* 配置说明 */}
        <TipCard>
          <TipIcon>💡</TipIcon>
          <div style={{ flex: 1 }}>
            <Text style={{ fontWeight: 600, color: '#fafafa' }}>如何获取163邮箱授权码？</Text>
            <Paragraph style={{ margin: '8px 0 0 0', fontSize: 13, color: 'rgba(250, 250, 250, 0.65)' }}>
              登录163邮箱 → 设置 → POP3/SMTP/IMAP → 开启SMTP服务 → 按提示获取授权码（16位字符串）
            </Paragraph>
          </div>
          <Button type="link" href="https://mail.163.com" target="_blank">
            前往163邮箱 →
          </Button>
        </TipCard>

        <Row gutter={20}>
          {/* 服务器配置 */}
          <Col xs={24} lg={12}>
            <motion.div
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.4 }}
            >
              <InfoCard
                title="服务器配置"
                description="配置SMTP服务器信息"
                icon={<CloudServerOutlined />}
                iconGradient="linear-gradient(135deg, #0ea5e9, #06b6d4)"
              >
                <Form.Item
                  name="smtpHost"
                  label="SMTP服务器"
                  rules={[{ required: true, message: '请选择SMTP服务器' }]}
                >
                  <Select size="large" placeholder="选择邮箱服务商">
                    <Select.Option value="smtp.163.com">
                      <Space><MailOutlined /> smtp.163.com（163邮箱）</Space>
                    </Select.Option>
                    <Select.Option value="smtp.126.com">
                      <Space><MailOutlined /> smtp.126.com（126邮箱）</Space>
                    </Select.Option>
                    <Select.Option value="smtp.qq.com">
                      <Space><MailOutlined /> smtp.qq.com（QQ邮箱）</Space>
                    </Select.Option>
                  </Select>
                </Form.Item>

                <Row gutter={16}>
                  <Col span={12}>
                    <Form.Item
                      name="smtpPort"
                      label="端口"
                      rules={[{ required: true, message: '请选择端口' }]}
                    >
                      <Select size="large">
                        <Select.Option value="465">465（SSL）</Select.Option>
                        <Select.Option value="587">587（TLS）</Select.Option>
                      </Select>
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item name="smtpSsl" label="SSL加密" valuePropName="checked">
                      <Switch checkedChildren="开启" unCheckedChildren="关闭" />
                    </Form.Item>
                  </Col>
                </Row>
              </InfoCard>
            </motion.div>
          </Col>

          {/* 账号配置 */}
          <Col xs={24} lg={12}>
            <motion.div
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.4, delay: 0.1 }}
            >
              <InfoCard
                title="账号配置"
                description="配置邮箱账号和授权码"
                icon={<UserOutlined />}
                iconGradient="linear-gradient(135deg, #22c55e, #16a34a)"
              >
                <Form.Item
                  name="smtpUsername"
                  label="邮箱账号"
                  rules={[
                    { required: true, message: '请输入邮箱账号' },
                    { type: 'email', message: '请输入有效的邮箱地址' }
                  ]}
                >
                  <Input 
                    size="large" 
                    prefix={<MailOutlined style={{ color: 'rgba(250, 250, 250, 0.25)' }} />}
                    placeholder="example@163.com" 
                  />
                </Form.Item>

                <Form.Item
                  name="smtpPassword"
                  label="SMTP授权码"
                  rules={[{ required: true, message: '请输入授权码' }]}
                  extra={<Text style={{ fontSize: 12, color: 'rgba(250, 250, 250, 0.45)' }}>授权码不是邮箱密码，需要在邮箱设置中获取</Text>}
                >
                  <Input.Password 
                    size="large"
                    prefix={<LockOutlined style={{ color: 'rgba(250, 250, 250, 0.25)' }} />}
                    placeholder="请输入16位授权码" 
                  />
                </Form.Item>
              </InfoCard>
            </motion.div>
          </Col>
        </Row>

        {/* 其他设置 */}
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.2 }}
          style={{ marginTop: 20 }}
        >
          <InfoCard
            title="其他设置"
            description="配置发件人名称和验证码有效期"
            icon={<ClockCircleOutlined />}
            iconGradient="linear-gradient(135deg, #f97316, #ea580c)"
          >
            <Row gutter={20}>
              <Col xs={24} md={12}>
                <Form.Item name="fromName" label="发件人名称">
                  <Input size="large" placeholder="AI小说创作系统" />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="codeExpireMinutes" label="验证码有效期">
                  <Select size="large">
                    <Select.Option value="3">3 分钟</Select.Option>
                    <Select.Option value="5">5 分钟</Select.Option>
                    <Select.Option value="10">10 分钟</Select.Option>
                    <Select.Option value="15">15 分钟</Select.Option>
                  </Select>
                </Form.Item>
              </Col>
            </Row>

            {/* 测试结果 */}
            {testResult && (
              <Alert
                message={testResult.valid ? '配置验证通过' : '配置验证失败'}
                description={testResult.message}
                type={testResult.valid ? 'success' : 'error'}
                showIcon
                icon={testResult.valid ? <CheckCircleOutlined /> : <ExclamationCircleOutlined />}
                style={{ marginTop: 16 }}
                closable
                onClose={() => setTestResult(null)}
              />
            )}

            {/* 操作按钮 */}
            <ActionBar>
              <Button 
                size="large"
                icon={<CheckCircleOutlined />}
                onClick={handleTest}
                loading={testing}
              >
                验证配置
              </Button>
              <Button 
                type="primary" 
                size="large"
                icon={<SaveOutlined />}
                onClick={handleSave}
                loading={saving}
              >
                保存配置
              </Button>
            </ActionBar>
          </InfoCard>
        </motion.div>
      </Form>
    </PageContainer>
  )
}

export default EmailConfigPage
