import { useEffect, useState } from 'react'
import { 
  Form, Input, Switch, Button, message, Alert, Typography, Spin 
} from 'antd'
import { 
  WechatOutlined, SaveOutlined, CheckCircleOutlined, 
  ExclamationCircleOutlined, QuestionCircleOutlined
} from '@ant-design/icons'
import styled from '@emotion/styled'
import { motion } from 'framer-motion'
import { adminWechatConfigService } from '@/services/adminWechatConfigService'
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

const ActionBar = styled.div`
  display: flex;
  gap: 12px;
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
`

const WechatConfigPage = () => {
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
      const config: any = await adminWechatConfigService.getConfig()
      form.setFieldsValue({
        enabled: config.mpEnabled,
        appId: config.mpAppId,
        appSecret: config.mpAppSecret,
        redirectUri: config.redirectUri
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
      await adminWechatConfigService.updateConfig({
        mpEnabled: values.enabled,
        mpAppId: values.appId,
        mpAppSecret: values.appSecret,
        redirectUri: values.redirectUri
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
      setTesting(true)
      const result: any = await adminWechatConfigService.testConfig('mp')
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
      title="微信登录配置"
      description="配置微信公众号参数，启用微信授权登录功能"
      icon={<WechatOutlined />}
      breadcrumb={[{ title: '微信登录' }]}
    >
      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
      >
        <InfoCard
          title="公众号配置"
          description="使用微信公众号（服务号）实现网页授权登录"
          icon={<WechatOutlined />}
          iconGradient="linear-gradient(135deg, #22c55e, #16a34a)"
        >
          <Alert
            message="配置说明"
            description={
              <div>
                <Paragraph style={{ marginBottom: 8, color: 'rgba(250, 250, 250, 0.65)' }}>
                  使用微信公众号（服务号）实现网页授权登录，需要认证的服务号。
                </Paragraph>
                <Paragraph style={{ marginBottom: 8, color: 'rgba(250, 250, 250, 0.65)' }}>
                  1. 登录 <a href="https://mp.weixin.qq.com/" target="_blank" rel="noopener noreferrer" style={{ color: '#0ea5e9' }}>微信公众平台</a>
                </Paragraph>
                <Paragraph style={{ marginBottom: 8, color: 'rgba(250, 250, 250, 0.65)' }}>
                  2. 在「开发 → 基本配置」中获取 AppID 和 AppSecret
                </Paragraph>
                <Paragraph style={{ marginBottom: 8, color: 'rgba(250, 250, 250, 0.65)' }}>
                  3. 在「设置 → 公众号设置 → 功能设置」中配置网页授权域名
                </Paragraph>
                <Paragraph style={{ marginBottom: 0, color: 'rgba(250, 250, 250, 0.65)' }}>
                  4. 回调地址格式：https://你的域名/login
                </Paragraph>
              </div>
            }
            type="info"
            showIcon
            icon={<QuestionCircleOutlined />}
            style={{ marginBottom: 24 }}
          />

          <Form form={form} layout="vertical" requiredMark="optional">
            <Form.Item
              name="enabled"
              label="启用微信登录"
              valuePropName="checked"
            >
              <Switch checkedChildren="开启" unCheckedChildren="关闭" />
            </Form.Item>

            <Form.Item
              name="appId"
              label="公众号 AppID"
              rules={[{ required: true, message: '请输入AppID' }]}
              tooltip="在公众平台「开发 → 基本配置」中获取"
            >
              <Input size="large" placeholder="请输入公众号AppID" />
            </Form.Item>

            <Form.Item
              name="appSecret"
              label="公众号 AppSecret"
              rules={[{ required: true, message: '请输入AppSecret' }]}
              tooltip="在公众平台「开发 → 基本配置」中获取，请妥善保管"
            >
              <Input.Password size="large" placeholder="请输入公众号AppSecret" />
            </Form.Item>

            <Form.Item
              name="redirectUri"
              label="授权回调地址"
              rules={[
                { required: true, message: '请输入回调地址' },
                { type: 'url', message: '请输入有效的URL地址' }
              ]}
              tooltip="微信授权后的回调地址，需要与公众平台配置的域名一致"
            >
              <Input size="large" placeholder="https://你的域名/login" />
            </Form.Item>

            {testResult && (
              <Alert
                message={testResult.valid ? '配置验证通过' : '配置验证失败'}
                description={testResult.message}
                type={testResult.valid ? 'success' : 'error'}
                showIcon
                icon={testResult.valid ? <CheckCircleOutlined /> : <ExclamationCircleOutlined />}
                style={{ marginBottom: 16 }}
                closable
                onClose={() => setTestResult(null)}
              />
            )}

            <ActionBar>
              <Button 
                size="large"
                icon={<CheckCircleOutlined />}
                onClick={handleTest}
                loading={testing}
              >
                测试配置
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
          </Form>
        </InfoCard>
      </motion.div>
    </PageContainer>
  )
}

export default WechatConfigPage
