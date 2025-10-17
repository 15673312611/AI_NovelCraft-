import React, { useState, useEffect } from 'react'
import { 
  Layout, 
  Typography, 
  Card, 
  Button, 
  Space, 
  Tabs, 
  Row, 
  Col,
  message,
  Spin,
  Avatar,
  Badge,
  Timeline,
  List,
  Tag,
  Modal,
  Input,
  Form,
  Select,
  Divider
} from 'antd'
import { 
  ArrowLeftOutlined,
  RobotOutlined,
  BulbOutlined,
  UserOutlined,
  BookOutlined,
  HistoryOutlined,
  EyeOutlined,
  EditOutlined,
  PlusOutlined,
  MessageOutlined,
  ThunderboltOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  StarOutlined
} from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import { Novel, novelService } from '@/services/novelService'
import './NovelAIWritingPage.css'

const { Header, Content, Sider } = Layout
const { Title, Text, Paragraph } = Typography
const { TabPane } = Tabs
const { TextArea } = Input
const { Option } = Select

interface ForeshadowingItem {
  id: string
  content: string
  type: 'character' | 'plot' | 'world' | 'relationship' | 'conflict'
  status: 'planned' | 'planted' | 'developing' | 'completed'
  plannedChapter: number
  importance: 1 | 2 | 3 | 4 | 5
  description: string
  relatedElements: string[]
}

interface PlotPoint {
  id: string
  title: string
  type: 'inciting_incident' | 'plot_point_1' | 'midpoint' | 'plot_point_2' | 'climax' | 'resolution'
  plannedChapter: number
  importance: 'critical' | 'high' | 'medium' | 'low'
  description: string
  requirements: string[]
  completed: boolean
}

interface AIRole {
  id: string
  name: string
  avatar: string
  description: string
  isActive: boolean
  lastActivity: string
}

interface ProgressStage {
  name: string
  startChapter: number
  endChapter: number
  description: string
  goals: string[]
  isActive: boolean
  completionRate: number
}

interface KnowledgeFragment {
  id: string
  type: 'character' | 'timeline' | 'worldview' | 'relationship' | 'subplot'
  title: string
  content: string
  tags: string[]
  referencedChapters: number[]
  importance: number
  lastUsed: string
}

const NovelAIWritingPage: React.FC = () => {
  const navigate = useNavigate()
  const { novelId } = useParams<{ novelId: string }>()
  const [novel, setNovel] = useState<Novel | null>(null)
  const [loading, setLoading] = useState(true)
  const [activeTab, setActiveTab] = useState('dashboard')

  // AI协作状态
  const [aiRoles] = useState<AIRole[]>([
    {
      id: 'progress_analyst',
      name: '进度分析师',
      avatar: '📊',
      description: '分析创作进度，评估当前阶段，提供进度建议',
      isActive: true,
      lastActivity: '5分钟前'
    },
    {
      id: 'foreshadowing_manager',
      name: '伏笔管理专家',
      avatar: '🔮',
      description: '管理伏笔系统，提醒伏笔时机，确保伏笔协调性',
      isActive: true,
      lastActivity: '8分钟前'
    },
    {
      id: 'plot_planner',
      name: '剧情规划师',
      avatar: '⚡',
      description: '规划高能剧情，分析情节节奏，建议转折点',
      isActive: false,
      lastActivity: '1小时前'
    },
    {
      id: 'stage_evaluator',
      name: '阶段评估专家',
      avatar: '🎯',
      description: '评估创作阶段，判断目标完成度，建议下一步',
      isActive: true,
      lastActivity: '2分钟前'
    }
  ])

  // 进度管理状态
  const [currentProgress] = useState({
    currentChapter: 12,
    totalPlannedChapters: 30,
    wordCount: 45000,
    completionRate: 0.4,
    currentStage: '发展阶段',
    estimatedCompletion: '2024-03-15'
  })

  const [progressStages] = useState<ProgressStage[]>([
    {
      name: '开篇阶段',
      startChapter: 1,
      endChapter: 5,
      description: '建立世界观，介绍主要角色，设定主要冲突',
      goals: ['世界观构建', '主角出场', '初始冲突设定'],
      isActive: false,
      completionRate: 1.0
    },
    {
      name: '发展阶段',
      startChapter: 6,
      endChapter: 20,
      description: '情节发展，角色成长，伏笔埋设',
      goals: ['角色发展', '冲突升级', '伏笔埋设', '副线展开'],
      isActive: true,
      completionRate: 0.35
    },
    {
      name: '高潮阶段',
      startChapter: 21,
      endChapter: 25,
      description: '主要冲突爆发，情节达到高潮',
      goals: ['主要冲突爆发', '伏笔回收', '角色抉择'],
      isActive: false,
      completionRate: 0
    },
    {
      name: '结局阶段',
      startChapter: 26,
      endChapter: 30,
      description: '冲突解决，故事收尾',
      goals: ['冲突解决', '角色结局', '主题升华'],
      isActive: false,
      completionRate: 0
    }
  ])

  // 伏笔管理状态
  const [foreshadowings, setForeshadowings] = useState<ForeshadowingItem[]>([
    {
      id: '1',
      content: '主角身世之谜',
      type: 'character',
      status: 'planted',
      plannedChapter: 22,
      importance: 5,
      description: '主角的真实身份和家族背景，将在第22章揭露',
      relatedElements: ['主角', '家族', '身世']
    },
    {
      id: '2',
      content: '神秘宝石的力量',
      type: 'plot',
      status: 'developing',
      plannedChapter: 18,
      importance: 4,
      description: '宝石隐藏的力量将成为转折点',
      relatedElements: ['宝石', '力量', '转折']
    },
    {
      id: '3',
      content: '反派的真正目的',
      type: 'conflict',
      status: 'planned',
      plannedChapter: 25,
      importance: 5,
      description: '反派行动的真正动机，将在高潮阶段揭露',
      relatedElements: ['反派', '动机', '目的']
    }
  ])

  // 剧情节点状态
  const [plotPoints, setPlotPoints] = useState<PlotPoint[]>([
    {
      id: '1',
      title: '主角获得神秘力量',
      type: 'inciting_incident',
      plannedChapter: 3,
      importance: 'critical',
      description: '主角意外获得特殊能力，故事正式开始',
      requirements: ['建立世界观', '角色介绍完成'],
      completed: true
    },
    {
      id: '2',
      title: '第一次重大挫折',
      type: 'plot_point_1',
      plannedChapter: 8,
      importance: 'critical',
      description: '主角遭遇第一次重大失败，开始真正成长',
      requirements: ['角色关系建立', '初期成功'],
      completed: true
    },
    {
      id: '3',
      title: '重要伙伴背叛',
      type: 'midpoint',
      plannedChapter: 15,
      importance: 'critical',
      description: '信任的伙伴背叛，故事节奏转变',
      requirements: ['伙伴关系深化', '伏笔埋设完成'],
      completed: false
    }
  ])

  // 知识片段状态
  const [knowledgeFragments] = useState<KnowledgeFragment[]>([
    {
      id: '1',
      type: 'character',
      title: '主角 - 林逸风',
      content: '18岁，孤儿出身，性格坚韧，有强烈的正义感。拥有罕见的双系魔法天赋。',
      tags: ['主角', '魔法', '孤儿', '正义'],
      referencedChapters: [1, 3, 5, 8, 12],
      importance: 10,
      lastUsed: '第12章'
    },
    {
      id: '2',
      type: 'worldview',
      title: '魔法体系 - 元素魔法',
      content: '分为火、水、土、风四大基础元素，每个人只能掌握一种。双系天赋极其罕见。',
      tags: ['魔法体系', '元素', '设定'],
      referencedChapters: [2, 4, 7, 9],
      importance: 8,
      lastUsed: '第9章'
    },
    {
      id: '3',
      type: 'timeline',
      title: '重要时间线 - 魔法学院入学',
      content: '春季入学典礼，主角初次展示能力，引起关注和嫉妒',
      tags: ['时间线', '学院', '入学'],
      referencedChapters: [4, 5, 6],
      importance: 7,
      lastUsed: '第6章'
    }
  ])

  // AI对话状态
  const [aiChatVisible, setAiChatVisible] = useState(false)
  const [selectedAiRole, setSelectedAiRole] = useState<AIRole | null>(null)
  const [chatMessages, setChatMessages] = useState<Array<{
    type: 'user' | 'ai'
    content: string
    time: string
  }>>([])

  // 加载小说数据
  const loadNovel = async () => {
    if (!novelId) return
    
    try {
      setLoading(true)
      const novelData = await novelService.getNovelById(parseInt(novelId))
      setNovel(novelData)
    } catch (error) {
      message.error('加载小说失败')
      navigate('/novels')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadNovel()
  }, [novelId])

  // AI对话处理
  const handleAiChat = (role: AIRole) => {
    setSelectedAiRole(role)
    setAiChatVisible(true)
    setChatMessages([
      {
        type: 'ai',
        content: `你好！我是${role.name}，${role.description}。有什么可以帮助你的吗？`,
        time: new Date().toLocaleTimeString()
      }
    ])
  }

  // 发送消息给AI
  const sendMessageToAi = async (message: string) => {
    if (!selectedAiRole || !message.trim()) return

    // 添加用户消息
    const newUserMessage = {
      type: 'user' as const,
      content: message,
      time: new Date().toLocaleTimeString()
    }

    setChatMessages(prev => [...prev, newUserMessage])

    // 模拟AI回复
    setTimeout(() => {
      const aiResponse = {
        type: 'ai' as const,
        content: generateAiResponse(selectedAiRole, message),
        time: new Date().toLocaleTimeString()
      }
      setChatMessages(prev => [...prev, aiResponse])
    }, 1500)
  }

  // 生成AI回复
  const generateAiResponse = (role: AIRole, userMessage: string): string => {
    switch (role.id) {
      case 'progress_analyst':
        return `根据当前进度分析，您已完成${currentProgress.completionRate * 100}%的内容。建议在接下来的章节中关注角色发展和伏笔埋设。当前处于${currentProgress.currentStage}，需要注意情节节奏的把控。`
      case 'foreshadowing_manager':
        return `检查您的伏笔系统，发现有3个重要伏笔需要关注。建议在第${currentProgress.currentChapter + 3}章开始为"${foreshadowings[0].content}"做铺垫，为后续的高潮做准备。`
      case 'plot_planner':
        return `当前情节发展平稳，建议在第${currentProgress.currentChapter + 2}章加入一个小高潮或转折点，增强读者的阅读兴趣。可以考虑让主角面临道德抉择或揭露一个小秘密。`
      case 'stage_evaluator':
        return `当前阶段评估：发展阶段进度良好，角色塑造和世界观建设较为完善。建议开始为高潮阶段做准备，逐步升级冲突，为第20章左右的转折点做铺垫。`
      default:
        return '我正在分析您的需求，请稍等...'
    }
  }

  if (loading) {
    return (
      <div className="loading-container">
        <Spin size="large" />
        <Text>加载中...</Text>
      </div>
    )
  }

  return (
    <Layout className="novel-ai-writing-page">
      <Header className="page-header">
        <div className="header-left">
          <Space>
            <Button 
              icon={<ArrowLeftOutlined />} 
              onClick={() => navigate('/novels')}
              type="text"
            >
              返回
            </Button>
            <Divider type="vertical" />
            <Title level={3} className="page-title">
              AI写作助手 - {novel?.title}
            </Title>
          </Space>
        </div>
      </Header>

      <Layout>
        <Content className="main-content">
          <Tabs 
            activeKey={activeTab} 
            onChange={setActiveTab}
            className="main-tabs"
          >
            {/* 控制面板 */}
            <TabPane 
              tab={<span><RobotOutlined />AI控制面板</span>} 
              key="dashboard"
            >
              <Row gutter={[24, 24]}>
                <Col span={24}>
                  <Card title="AI写作工具" className="writing-tools-card">
                    <Space direction="vertical" style={{ width: '100%' }}>
                      <Text>选择您需要的AI写作方式：</Text>
                      <Row gutter={[16, 16]}>
                        <Col xs={24} md={8}>
                          <Button 
                            type="primary" 
                            size="large" 
                            block
                            icon={<RobotOutlined />}
                            onClick={() => navigate(`/novels/${novelId}/ai-assistant`)}
                          >
                            高级AI写作助手
                          </Button>
                          <Text type="secondary" style={{ fontSize: '12px', display: 'block', marginTop: 8 }}>
                            完整的AI写作环境，包含内容片段管理、智能引用、多AI协作等高级功能
                          </Text>
                        </Col>
                        <Col xs={24} md={8}>
                          <Button 
                            size="large" 
                            block
                            icon={<EditOutlined />}
                            onClick={() => navigate(`/novels/${novelId}/editor`)}
                          >
                            传统编辑器
                          </Button>
                          <Text type="secondary" style={{ fontSize: '12px', display: 'block', marginTop: 8 }}>
                            经典的富文本编辑器，适合喜欢手动写作的作者
                          </Text>
                        </Col>
                        <Col xs={24} md={8}>
                          <Button 
                            size="large" 
                            block
                            icon={<HistoryOutlined />}
                            onClick={() => navigate(`/world-view-builder?novelId=${novelId}`)}
                          >
                            世界观构建对话
                          </Button>
                          <Text type="secondary" style={{ fontSize: '12px', display: 'block', marginTop: 8 }}>
                            通过AI对话逐步构建完整的小说世界观
                          </Text>
                        </Col>
                      </Row>
                    </Space>
                  </Card>
                </Col>
                <Col xs={24} lg={12}>
                  <Card title="进度概览" className="progress-card">
                    <Space direction="vertical" style={{ width: '100%' }}>
                      <div>
                        <Text strong>当前进度: </Text>
                        <Text>{currentProgress.currentChapter}/{currentProgress.totalPlannedChapters} 章</Text>
                        <Text type="secondary"> ({Math.round(currentProgress.completionRate * 100)}%)</Text>
                      </div>
                      <div>
                        <Text strong>字数统计: </Text>
                        <Text>{currentProgress.wordCount.toLocaleString()} 字</Text>
                      </div>
                      <div>
                        <Text strong>当前阶段: </Text>
                        <Tag color="processing">{currentProgress.currentStage}</Tag>
                      </div>
                      <div>
                        <Text strong>预计完成: </Text>
                        <Text>{currentProgress.estimatedCompletion}</Text>
                      </div>
                    </Space>
                  </Card>
                </Col>

                <Col xs={24} lg={12}>
                  <Card title="AI助手状态" className="ai-roles-card">
                    <List
                      dataSource={aiRoles}
                      renderItem={role => (
                        <List.Item
                          actions={[
                            <Button
                              type="link"
                              icon={<MessageOutlined />}
                              onClick={() => handleAiChat(role)}
                            >
                              对话
                            </Button>
                          ]}
                        >
                          <List.Item.Meta
                            avatar={
                              <Badge dot={role.isActive} color={role.isActive ? 'green' : 'gray'}>
                                <Avatar size={40}>{role.avatar}</Avatar>
                              </Badge>
                            }
                            title={role.name}
                            description={
                              <div>
                                <Text type="secondary">{role.description}</Text>
                                <br />
                                <Text type="secondary" style={{ fontSize: '12px' }}>
                                  最后活动: {role.lastActivity}
                                </Text>
                              </div>
                            }
                          />
                        </List.Item>
                      )}
                    />
                  </Card>
                </Col>

                <Col span={24}>
                  <Card title="创作阶段" className="stages-card">
                    <Timeline>
                      {progressStages.map(stage => (
                        <Timeline.Item
                          key={stage.name}
                          color={stage.isActive ? 'blue' : stage.completionRate === 1 ? 'green' : 'gray'}
                          dot={stage.isActive ? <ClockCircleOutlined /> : stage.completionRate === 1 ? <CheckCircleOutlined /> : undefined}
                        >
                          <div>
                            <Text strong>{stage.name}</Text>
                            <Text type="secondary"> (第{stage.startChapter}-{stage.endChapter}章)</Text>
                            {stage.isActive && (
                              <Tag color="processing" style={{ marginLeft: 8 }}>进行中</Tag>
                            )}
                            {stage.completionRate === 1 && (
                              <Tag color="success" style={{ marginLeft: 8 }}>已完成</Tag>
                            )}
                            <br />
                            <Text type="secondary">{stage.description}</Text>
                            <br />
                            <Text type="secondary">
                              目标: {stage.goals.join('、')} 
                              {stage.isActive && ` (${Math.round(stage.completionRate * 100)}%)`}
                            </Text>
                          </div>
                        </Timeline.Item>
                      ))}
                    </Timeline>
                  </Card>
                </Col>
              </Row>
            </TabPane>

            {/* 伏笔管理 */}
            <TabPane 
              tab={<span><BulbOutlined />伏笔管理</span>} 
              key="foreshadowing"
            >
              <Row gutter={[16, 16]}>
                <Col span={24}>
                  <Card 
                    title="伏笔管理" 
                    extra={
                      <Button type="primary" icon={<PlusOutlined />}>
                        添加伏笔
                      </Button>
                    }
                  >
                    <List
                      dataSource={foreshadowings}
                      renderItem={item => (
                        <List.Item
                          actions={[
                            <Button type="link" icon={<EditOutlined />}>编辑</Button>,
                            <Button type="link" icon={<EyeOutlined />}>查看</Button>
                          ]}
                        >
                          <List.Item.Meta
                            title={
                              <Space>
                                <Text strong>{item.content}</Text>
                                <Tag color={
                                  item.status === 'completed' ? 'green' :
                                  item.status === 'developing' ? 'processing' :
                                  item.status === 'planted' ? 'warning' : 'default'
                                }>
                                  {item.status === 'planned' ? '计划中' :
                                   item.status === 'planted' ? '已埋设' :
                                   item.status === 'developing' ? '发展中' : '已完成'}
                                </Tag>
                                <Space>
                                  {[...Array(item.importance)].map((_, i) => (
                                    <StarOutlined key={i} style={{ color: '#faad14' }} />
                                  ))}
                                </Space>
                              </Space>
                            }
                            description={
                              <div>
                                <Text type="secondary">{item.description}</Text>
                                <br />
                                <Text type="secondary">
                                  计划章节: 第{item.plannedChapter}章 | 
                                  类型: {
                                    item.type === 'character' ? '角色发展' :
                                    item.type === 'plot' ? '情节转折' :
                                    item.type === 'world' ? '世界观' :
                                    item.type === 'relationship' ? '关系发展' : '冲突'
                                  } |
                                  相关元素: {item.relatedElements.join('、')}
                                </Text>
                              </div>
                            }
                          />
                        </List.Item>
                      )}
                    />
                  </Card>
                </Col>
              </Row>
            </TabPane>

            {/* 剧情节点 */}
            <TabPane 
              tab={<span><ThunderboltOutlined />剧情节点</span>} 
              key="plotpoints"
            >
              <Row gutter={[16, 16]}>
                <Col span={24}>
                  <Card 
                    title="关键剧情节点" 
                    extra={
                      <Button type="primary" icon={<PlusOutlined />}>
                        添加节点
                      </Button>
                    }
                  >
                    <List
                      dataSource={plotPoints}
                      renderItem={item => (
                        <List.Item
                          actions={[
                            <Button type="link" icon={<EditOutlined />}>编辑</Button>,
                            item.completed ? 
                              <Tag color="success">已完成</Tag> :
                              <Button type="link">标记完成</Button>
                          ]}
                        >
                          <List.Item.Meta
                            title={
                              <Space>
                                <Text strong style={{ textDecoration: item.completed ? 'line-through' : 'none' }}>
                                  {item.title}
                                </Text>
                                <Tag color={
                                  item.importance === 'critical' ? 'red' :
                                  item.importance === 'high' ? 'orange' :
                                  item.importance === 'medium' ? 'blue' : 'default'
                                }>
                                  {item.importance === 'critical' ? '关键' :
                                   item.importance === 'high' ? '重要' :
                                   item.importance === 'medium' ? '中等' : '一般'}
                                </Tag>
                                {item.completed && <CheckCircleOutlined style={{ color: 'green' }} />}
                              </Space>
                            }
                            description={
                              <div>
                                <Text type="secondary">{item.description}</Text>
                                <br />
                                <Text type="secondary">
                                  计划章节: 第{item.plannedChapter}章 | 
                                  类型: {
                                    item.type === 'inciting_incident' ? '起始事件' :
                                    item.type === 'plot_point_1' ? '转折点1' :
                                    item.type === 'midpoint' ? '中点' :
                                    item.type === 'plot_point_2' ? '转折点2' :
                                    item.type === 'climax' ? '高潮' : '结局'
                                  }
                                </Text>
                                <br />
                                <Text type="secondary">
                                  前置要求: {item.requirements.join('、')}
                                </Text>
                              </div>
                            }
                          />
                        </List.Item>
                      )}
                    />
                  </Card>
                </Col>
              </Row>
            </TabPane>

            {/* 知识片段 */}
            <TabPane 
              tab={<span><BookOutlined />知识片段</span>} 
              key="knowledge"
            >
              <Row gutter={[16, 16]}>
                <Col span={24}>
                  <Card title="碎片化内容库">
                    <Tabs defaultActiveKey="character" size="small">
                      <TabPane tab="角色" key="character">
                        <List
                          dataSource={knowledgeFragments.filter(item => item.type === 'character')}
                          renderItem={item => (
                            <List.Item
                              actions={[
                                <Button type="link" icon={<EditOutlined />}>编辑</Button>
                              ]}
                            >
                              <List.Item.Meta
                                title={
                                  <Space>
                                    <Text strong>{item.title}</Text>
                                    <Text type="secondary">重要度: {item.importance}/10</Text>
                                  </Space>
                                }
                                description={
                                  <div>
                                    <Paragraph ellipsis={{ rows: 2 }}>{item.content}</Paragraph>
                                    <div>
                                      {item.tags.map(tag => (
                                        <Tag key={tag} size="small">{tag}</Tag>
                                      ))}
                                    </div>
                                    <Text type="secondary" style={{ fontSize: '12px' }}>
                                      引用章节: {item.referencedChapters.join('、')} | 
                                      最后使用: {item.lastUsed}
                                    </Text>
                                  </div>
                                }
                              />
                            </List.Item>
                          )}
                        />
                      </TabPane>
                      <TabPane tab="世界观" key="worldview">
                        <List
                          dataSource={knowledgeFragments.filter(item => item.type === 'worldview')}
                          renderItem={item => (
                            <List.Item
                              actions={[
                                <Button type="link" icon={<EditOutlined />}>编辑</Button>
                              ]}
                            >
                              <List.Item.Meta
                                title={
                                  <Space>
                                    <Text strong>{item.title}</Text>
                                    <Text type="secondary">重要度: {item.importance}/10</Text>
                                  </Space>
                                }
                                description={
                                  <div>
                                    <Paragraph ellipsis={{ rows: 2 }}>{item.content}</Paragraph>
                                    <div>
                                      {item.tags.map(tag => (
                                        <Tag key={tag} size="small">{tag}</Tag>
                                      ))}
                                    </div>
                                    <Text type="secondary" style={{ fontSize: '12px' }}>
                                      引用章节: {item.referencedChapters.join('、')} | 
                                      最后使用: {item.lastUsed}
                                    </Text>
                                  </div>
                                }
                              />
                            </List.Item>
                          )}
                        />
                      </TabPane>
                      <TabPane tab="时间线" key="timeline">
                        <List
                          dataSource={knowledgeFragments.filter(item => item.type === 'timeline')}
                          renderItem={item => (
                            <List.Item
                              actions={[
                                <Button type="link" icon={<EditOutlined />}>编辑</Button>
                              ]}
                            >
                              <List.Item.Meta
                                title={
                                  <Space>
                                    <Text strong>{item.title}</Text>
                                    <Text type="secondary">重要度: {item.importance}/10</Text>
                                  </Space>
                                }
                                description={
                                  <div>
                                    <Paragraph ellipsis={{ rows: 2 }}>{item.content}</Paragraph>
                                    <div>
                                      {item.tags.map(tag => (
                                        <Tag key={tag} size="small">{tag}</Tag>
                                      ))}
                                    </div>
                                    <Text type="secondary" style={{ fontSize: '12px' }}>
                                      引用章节: {item.referencedChapters.join('、')} | 
                                      最后使用: {item.lastUsed}
                                    </Text>
                                  </div>
                                }
                              />
                            </List.Item>
                          )}
                        />
                      </TabPane>
                    </Tabs>
                  </Card>
                </Col>
              </Row>
            </TabPane>

            {/* 世界观构建 */}
            <TabPane 
              tab={<span><HistoryOutlined />世界观构建</span>} 
              key="worldview"
            >
              <Row gutter={[16, 16]}>
                <Col span={24}>
                  <Card title="AI对话式世界观构建">
                    <Space direction="vertical" style={{ width: '100%' }}>
                      <Button 
                        type="primary" 
                        size="large" 
                        icon={<MessageOutlined />}
                        onClick={() => navigate(`/world-view-builder?novelId=${novelId}`)}
                      >
                        开始世界观构建对话
                      </Button>
                      <Text type="secondary">
                        通过与AI的互动对话，逐步构建完整的小说世界观。
                        AI将引导您思考世界设定、规则体系、历史背景等重要元素。
                      </Text>
                    </Space>
                  </Card>
                </Col>
              </Row>
            </TabPane>
          </Tabs>
        </Content>
      </Layout>

      {/* AI对话弹窗 */}
      <Modal
        title={`与 ${selectedAiRole?.name} 对话`}
        open={aiChatVisible}
        onCancel={() => setAiChatVisible(false)}
        footer={null}
        width={600}
        destroyOnClose
      >
        <div className="ai-chat-container">
          <div className="chat-messages">
            {chatMessages.map((msg, index) => (
              <div key={index} className={`message ${msg.type === 'user' ? 'user-message' : 'ai-message'}`}>
                <div className="message-content">
                  {msg.content}
                </div>
                <div className="message-time">
                  {msg.time}
                </div>
              </div>
            ))}
          </div>
          <div className="chat-input">
            <Input.Group compact>
              <Input
                style={{ width: 'calc(100% - 80px)' }}
                placeholder="输入您的问题..."
                onPressEnter={(e) => {
                  const target = e.target as HTMLInputElement
                  sendMessageToAi(target.value)
                  target.value = ''
                }}
              />
              <Button 
                type="primary"
                onClick={(e) => {
                  const input = (e.target as HTMLElement).previousElementSibling as HTMLInputElement
                  sendMessageToAi(input.value)
                  input.value = ''
                }}
              >
                发送
              </Button>
            </Input.Group>
          </div>
        </div>
      </Modal>
    </Layout>
  )
}

export default NovelAIWritingPage