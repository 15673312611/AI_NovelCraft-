import React, { useState } from 'react'
import { 
  Layout, 
  Typography, 
  Card, 
  Button, 
  Steps,
  Space,
  Tag,
  Alert,
  Row,
  Col,
  Divider
} from 'antd'
import { 
  RobotOutlined,
  EditOutlined,
  BulbOutlined,
  PlayCircleOutlined,
  CheckCircleOutlined,
  ArrowRightOutlined,
  UserOutlined,
  FileTextOutlined
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import './WelcomeGuide.css'

const { Content } = Layout
const { Title, Text, Paragraph } = Typography
const { Step } = Steps

const WelcomeGuide: React.FC = () => {
  const navigate = useNavigate()
  const [currentStep, setCurrentStep] = useState(0)

  const steps = [
    {
      title: '创建你的小说',
      icon: <FileTextOutlined />,
      content: '首先创建一个新的小说项目，设置基本信息如标题、类型等',
      example: '《觉醒者》- 都市超能力小说'
    },
    {
      title: '描述你的想法',
      icon: <BulbOutlined />,
      content: '在AI助手面板中描述你想写的内容，可以很简单，比如一个场景或对话',
      example: '"让主角在学校里遇到一个神秘的转学生"'
    },
    {
      title: 'AI为你创作',
      icon: <RobotOutlined />,
      content: 'AI会根据你的描述生成高质量的小说内容，通常500-800字',
      example: '生成包含对话、描写、情节的完整段落'
    },
    {
      title: '选择和编辑',
      icon: <EditOutlined />,
      content: '你可以接受AI生成的内容，或者手动编辑，完全掌控创作方向',
      example: '接受→继续，拒绝→重新生成或手动修改'
    }
  ]

  const features = [
    {
      title: '🤖 智能AI助手',
      description: '专业的小说创作AI，理解你的想法并生成高质量内容',
      benefits: ['多种写作风格', '智能上下文理解', '创意度可调节']
    },
    {
      title: '✍️ 半自动创作',
      description: '你控制方向，AI负责执行，完美的人机协作',
      benefits: ['用户掌控剧情', 'AI负责具体写作', '效率大幅提升']
    },
    {
      title: '📊 进度管理',
      description: '实时跟踪写作进度，让你的创作有条不紊',
      benefits: ['字数统计', '进度可视化', '写作历史记录']
    },
    {
      title: '💡 智能建议',
      description: 'AI实时分析你的内容，提供个性化写作建议',
      benefits: ['剧情发展建议', '写作技巧提示', '质量评估反馈']
    }
  ]

  return (
    <Layout className="welcome-guide">
      <Content style={{ padding: '40px 24px' }}>
        <div style={{ maxWidth: 1200, margin: '0 auto' }}>
          {/* 主标题 */}
          <div style={{ textAlign: 'center', marginBottom: 48 }}>
            <Title level={1} style={{ fontSize: 48, background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
              AI小说创作助手
            </Title>
            <Paragraph style={{ fontSize: 18, color: '#666', maxWidth: 600, margin: '16px auto' }}>
              让AI成为你的创作伙伴，用简单的描述就能创作出精彩的长篇小说。
              你负责创意和方向，AI负责优美的文字表达。
            </Paragraph>
            <Space size="large" style={{ marginTop: 24 }}>
              <Button 
                type="primary" 
                size="large"
                icon={<PlayCircleOutlined />}
                onClick={() => navigate('/novels')}
              >
                开始创作
              </Button>
              <Button 
                size="large"
                onClick={() => setCurrentStep(currentStep < steps.length - 1 ? currentStep + 1 : 0)}
              >
                了解更多
              </Button>
            </Space>
          </div>

          {/* 使用流程 */}
          <Card style={{ marginBottom: 48 }}>
            <Title level={2} style={{ textAlign: 'center', marginBottom: 32 }}>
              🚀 四步开始你的创作之旅
            </Title>
            
            <Steps current={currentStep} style={{ marginBottom: 32 }}>
              {steps.map((step, index) => (
                <Step 
                  key={index}
                  title={step.title} 
                  icon={step.icon}
                  onClick={() => setCurrentStep(index)}
                  style={{ cursor: 'pointer' }}
                />
              ))}
            </Steps>

            <Card 
              style={{ 
                background: 'linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)',
                border: 'none',
                minHeight: 200
              }}
            >
              <Row gutter={24} align="middle">
                <Col span={12}>
                  <div style={{ fontSize: 24, marginBottom: 16 }}>
                    {steps[currentStep].icon}
                    <span style={{ marginLeft: 12 }}>{steps[currentStep].title}</span>
                  </div>
                  <Paragraph style={{ fontSize: 16, marginBottom: 16 }}>
                    {steps[currentStep].content}
                  </Paragraph>
                  <Alert
                    message="示例"
                    description={steps[currentStep].example}
                    type="info"
                    showIcon
                  />
                </Col>
                <Col span={12}>
                  <div style={{ textAlign: 'center' }}>
                    {currentStep === 0 && (
                      <div style={{ background: 'white', padding: 20, borderRadius: 8, boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }}>
                        <FileTextOutlined style={{ fontSize: 48, color: '#1890ff', marginBottom: 16 }} />
                        <div>创建新小说项目</div>
                      </div>
                    )}
                    {currentStep === 1 && (
                      <div style={{ background: 'white', padding: 20, borderRadius: 8, boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }}>
                        <BulbOutlined style={{ fontSize: 48, color: '#faad14', marginBottom: 16 }} />
                        <div>输入创作想法</div>
                      </div>
                    )}
                    {currentStep === 2 && (
                      <div style={{ background: 'white', padding: 20, borderRadius: 8, boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }}>
                        <RobotOutlined style={{ fontSize: 48, color: '#52c41a', marginBottom: 16 }} />
                        <div>AI智能生成</div>
                      </div>
                    )}
                    {currentStep === 3 && (
                      <div style={{ background: 'white', padding: 20, borderRadius: 8, boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }}>
                        <CheckCircleOutlined style={{ fontSize: 48, color: '#722ed1', marginBottom: 16 }} />
                        <div>完成创作</div>
                      </div>
                    )}
                  </div>
                </Col>
              </Row>
            </Card>
          </Card>

          {/* 核心特性 */}
          <Title level={2} style={{ textAlign: 'center', marginBottom: 32 }}>
            ✨ 为什么选择我们的AI创作助手
          </Title>
          
          <Row gutter={[24, 24]} style={{ marginBottom: 48 }}>
            {features.map((feature, index) => (
              <Col span={12} key={index}>
                <Card 
                  hoverable
                  style={{ height: '100%' }}
                  bodyStyle={{ height: '100%', display: 'flex', flexDirection: 'column' }}
                >
                  <Title level={4} style={{ marginBottom: 16 }}>
                    {feature.title}
                  </Title>
                  <Paragraph style={{ flex: 1, marginBottom: 16 }}>
                    {feature.description}
                  </Paragraph>
                  <Space wrap>
                    {feature.benefits.map((benefit, idx) => (
                      <Tag key={idx} color="blue">{benefit}</Tag>
                    ))}
                  </Space>
                </Card>
              </Col>
            ))}
          </Row>

          {/* 实际效果展示 */}
          <Card style={{ marginBottom: 48 }}>
            <Title level={2} style={{ textAlign: 'center', marginBottom: 32 }}>
              📝 看看AI能为你创作什么
            </Title>
            
            <Row gutter={24}>
              <Col span={8}>
                <Card size="small" title="📝 用户输入">
                  <Text style={{ fontStyle: 'italic', color: '#666' }}>
                    "让主角在深夜回家的路上遇到神秘事件"
                  </Text>
                </Card>
              </Col>
              
              <Col span={8} style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <ArrowRightOutlined style={{ fontSize: 24, color: '#1890ff' }} />
              </Col>
              
              <Col span={8}>
                <Card size="small" title="🤖 AI生成">
                  <div style={{ 
                    background: '#f8f9fa', 
                    padding: 12, 
                    borderRadius: 4,
                    maxHeight: 150,
                    overflow: 'auto',
                    fontSize: 14,
                    lineHeight: 1.6
                  }}>
                    夜色深沉，林晨独自走在空荡的街道上。路灯投下斑驳的光影，他的脚步声在寂静中格外清晰。
                    
                    突然，一道银光从天而降...
                  </div>
                  <div style={{ marginTop: 8, textAlign: 'right' }}>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      约500字精彩内容
                    </Text>
                  </div>
                </Card>
              </Col>
            </Row>
          </Card>

          {/* 开始按钮 */}
          <div style={{ textAlign: 'center' }}>
            <Card style={{ background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)', border: 'none' }}>
              <div style={{ color: 'white', padding: '20px 0' }}>
                <Title level={2} style={{ color: 'white', marginBottom: 16 }}>
                  准备好开始你的创作之旅了吗？
                </Title>
                <Paragraph style={{ color: 'white', opacity: 0.9, marginBottom: 24 }}>
                  只需要几分钟，你就能创建第一个AI辅助的小说项目
                </Paragraph>
                <Button 
                  type="primary" 
                  size="large"
                  icon={<PlayCircleOutlined />}
                  onClick={() => navigate('/novels')}
                  style={{ 
                    background: 'white', 
                    borderColor: 'white', 
                    color: '#1890ff',
                    boxShadow: '0 4px 12px rgba(255,255,255,0.3)'
                  }}
                >
                  立即开始创作
                </Button>
              </div>
            </Card>
          </div>
        </div>
      </Content>
    </Layout>
  )
}

export default WelcomeGuide