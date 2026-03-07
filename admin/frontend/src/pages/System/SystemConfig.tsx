import { Form, Input, Button, message, Switch, Row, Col } from 'antd'
import { 
  ApiOutlined, RobotOutlined, DatabaseOutlined, SettingOutlined, SaveOutlined
} from '@ant-design/icons'
import styled from '@emotion/styled'
import { motion } from 'framer-motion'
import { PageContainer, InfoCard } from '@/components'

// Styled Components
const ActionBar = styled.div`
  display: flex;
  justify-content: center;
  margin-top: 24px;
`

const SystemConfig = () => {
  const [form] = Form.useForm()

  const onFinish = (values: any) => {
    console.log('保存配置:', values)
    message.success('配置保存成功')
  }

  return (
    <PageContainer
      title="系统配置"
      description="配置系统运行参数和第三方服务"
      icon={<SettingOutlined />}
      breadcrumb={[{ title: '系统配置' }]}
    >
      <Form form={form} layout="vertical" onFinish={onFinish}>
        {/* AI配置 */}
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4 }}
        >
          <InfoCard
            title="AI 配置"
            description="配置 OpenAI API 相关参数"
            icon={<RobotOutlined />}
            iconGradient="linear-gradient(135deg, #0ea5e9, #06b6d4)"
          >
            <Row gutter={20}>
              <Col xs={24} md={12}>
                <Form.Item 
                  name="openaiApiKey" 
                  label="OpenAI API Key"
                  tooltip="用于调用OpenAI API的密钥"
                >
                  <Input.Password 
                    size="large"
                    prefix={<ApiOutlined style={{ color: 'rgba(250, 250, 250, 0.25)' }} />}
                    placeholder="sk-..." 
                  />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item 
                  name="openaiModel" 
                  label="默认模型"
                  tooltip="默认使用的AI模型"
                >
                  <Input size="large" placeholder="gpt-4" />
                </Form.Item>
              </Col>
            </Row>
            <Row gutter={20}>
              <Col xs={24} md={12}>
                <Form.Item 
                  name="maxTokens" 
                  label="最大Token数"
                  tooltip="单次请求的最大token数量"
                >
                  <Input type="number" size="large" placeholder="4000" />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item 
                  name="temperature" 
                  label="Temperature"
                  tooltip="控制输出的随机性，范围0-2"
                >
                  <Input type="number" step="0.1" size="large" placeholder="0.7" />
                </Form.Item>
              </Col>
            </Row>
          </InfoCard>
        </motion.div>

        {/* 爬虫配置 */}
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.1 }}
          style={{ marginTop: 20 }}
        >
          <InfoCard
            title="爬虫配置"
            description="配置七猫数据爬取参数"
            icon={<DatabaseOutlined />}
            iconGradient="linear-gradient(135deg, #a855f7, #9333ea)"
          >
            <Row gutter={20}>
              <Col xs={24} md={8}>
                <Form.Item 
                  name="qimaoEnabled" 
                  label="启用七猫爬虫" 
                  valuePropName="checked"
                >
                  <Switch checkedChildren="开启" unCheckedChildren="关闭" />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item 
                  name="qimaoInterval" 
                  label="爬取间隔(秒)"
                  tooltip="两次爬取之间的时间间隔"
                >
                  <Input type="number" size="large" placeholder="60" />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item 
                  name="qimaoMaxRetry" 
                  label="最大重试次数"
                  tooltip="爬取失败时的最大重试次数"
                >
                  <Input type="number" size="large" placeholder="3" />
                </Form.Item>
              </Col>
            </Row>
          </InfoCard>
        </motion.div>

        {/* 系统参数 */}
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.2 }}
          style={{ marginTop: 20 }}
        >
          <InfoCard
            title="系统参数"
            description="配置系统运行基础参数"
            icon={<SettingOutlined />}
            iconGradient="linear-gradient(135deg, #f97316, #ea580c)"
          >
            <Row gutter={20}>
              <Col xs={24} md={12}>
                <Form.Item 
                  name="maxUploadSize" 
                  label="最大上传文件大小(MB)"
                  tooltip="单个文件的最大上传大小限制"
                >
                  <Input type="number" size="large" placeholder="10" />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item 
                  name="sessionTimeout" 
                  label="会话超时时间(分钟)"
                  tooltip="用户会话的超时时间"
                >
                  <Input type="number" size="large" placeholder="30" />
                </Form.Item>
              </Col>
            </Row>
          </InfoCard>
        </motion.div>

        {/* 保存按钮 */}
        <ActionBar>
          <Button 
            type="primary" 
            htmlType="submit"
            size="large"
            icon={<SaveOutlined />}
            style={{ minWidth: 200, height: 48 }}
          >
            保存所有配置
          </Button>
        </ActionBar>
      </Form>
    </PageContainer>
  )
}

export default SystemConfig
