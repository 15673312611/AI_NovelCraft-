import React, { useState, useEffect } from 'react'
import {
  Modal,
  Form,
  Input,
  Select,
  Slider,
  InputNumber,
  Button,
  Space,
  Typography,
  Alert,
  Divider,
  Card,
  Row,
  Col,
  message
} from 'antd'
import {
  RobotOutlined,
  DollarOutlined
} from '@ant-design/icons'
import { AITask, AIModel, CostEstimate, aiTaskService } from '@/services/aiTaskService'
import './AITaskCreator.css'

const { TextArea } = Input
const { Option } = Select
const { Title, Text } = Typography

interface AITaskCreatorProps {
  visible: boolean
  onCancel: () => void
  onSuccess: (task: AITask) => void
  novelId?: number
  chapterId?: number
  initialType?: string
}

interface TaskTypeConfig {
  value: string
  label: string
  description: string
  defaultPrompt: string
  icon: string
}

const taskTypes: TaskTypeConfig[] = [
  {
    value: 'plot_generation',
    label: '剧情生成',
    description: '基于现有内容生成新的剧情发展',
    defaultPrompt: '请基于以下内容，生成一个引人入胜的剧情发展：\n\n{content}\n\n要求：\n1. 保持角色性格一致性\n2. 增加冲突和悬念\n3. 推动故事向前发展\n4. 字数控制在500-800字',
    icon: '📖'
  },
  {
    value: 'character_development',
    label: '角色发展',
    description: '深化角色背景和性格特征',
    defaultPrompt: '请为以下角色进行深度发展：\n\n{character}\n\n要求：\n1. 丰富角色背景故事\n2. 深化性格特征\n3. 增加内心冲突\n4. 为后续发展埋下伏笔',
    icon: '👤'
  },
  {
    value: 'dialogue_generation',
    label: '对话生成',
    description: '生成自然流畅的角色对话',
    defaultPrompt: '请为以下场景生成角色对话：\n\n{scene}\n\n要求：\n1. 对话自然流畅\n2. 体现角色性格\n3. 推动情节发展\n4. 增加情感张力',
    icon: '💬'
  },
  {
    value: 'scene_description',
    label: '场景描述',
    description: '生成详细的场景和环境描述',
    defaultPrompt: '请为以下场景生成详细的描述：\n\n{scene}\n\n要求：\n1. 营造氛围和情绪\n2. 细节丰富生动\n3. 符合故事背景\n4. 增强代入感',
    icon: '🏞️'
  },
  {
    value: 'story_outline',
    label: '故事大纲',
    description: '生成完整的故事大纲和结构',
    defaultPrompt: '请为以下故事生成详细大纲：\n\n{story}\n\n要求：\n1. 三幕结构清晰\n2. 冲突层次分明\n3. 高潮设计合理\n4. 结局令人满意',
    icon: '📋'
  },
  {
    value: 'writing_assistance',
    label: '写作辅助',
    description: '提供写作建议和优化建议',
    defaultPrompt: '请对以下内容提供写作建议：\n\n{content}\n\n要求：\n1. 分析写作技巧\n2. 提供改进建议\n3. 优化表达方式\n4. 增强可读性',
    icon: '✍️'
  }
]

const AITaskCreator: React.FC<AITaskCreatorProps> = ({
  visible,
  onCancel,
  onSuccess,
  novelId,
  chapterId,
  initialType
}) => {
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [models, setModels] = useState<AIModel[]>([])
  const [costEstimate, setCostEstimate] = useState<CostEstimate | null>(null)
  const [estimating, setEstimating] = useState(false)
  const [selectedType, setSelectedType] = useState<string>(initialType || 'plot_generation')

  // 加载可用模型
  const loadModels = async () => {
    try {
      const availableModels = await aiTaskService.getAvailableModels()
      setModels(availableModels)
      if (availableModels.length > 0) {
        form.setFieldsValue({ model: availableModels[0].id })
      }
    } catch (error) {
      message.error('加载AI模型失败')
    }
  }

  // 估算成本
  const estimateCost = async () => {
    const values = form.getFieldsValue()
    if (!values.prompt || !values.model || !values.maxTokens) return

    try {
      setEstimating(true)
      const estimate = await aiTaskService.estimateCost(
        values.prompt,
        values.model,
        values.maxTokens
      )
      setCostEstimate(estimate)
    } catch (error) {
      message.error('成本估算失败')
    } finally {
      setEstimating(false)
    }
  }

  // 处理任务类型变化
  const handleTypeChange = (type: string) => {
    setSelectedType(type)
    const typeConfig = taskTypes.find(t => t.value === type)
    if (typeConfig) {
      form.setFieldsValue({
        prompt: typeConfig.defaultPrompt
      })
    }
  }

  // 处理模型变化
  const handleModelChange = (modelId: string) => {
    const model = models.find(m => m.id === modelId)
    form.setFieldsValue({ maxTokens: model?.maxTokens || 1000 })
  }

  // 提交表单
  const handleSubmit = async () => {
    try {
      setLoading(true)
      const values = await form.validateFields()
      
      const taskData = {
        ...values,
        novelId: novelId || 0,
        chapterId: chapterId || undefined
      }
      
      const newTask = await aiTaskService.createAITask(taskData)
      message.success('AI任务创建成功')
      onSuccess(newTask)
      form.resetFields()
      setCostEstimate(null)
    } catch (error) {
      message.error('创建AI任务失败')
    } finally {
      setLoading(false)
    }
  }

  // 重置表单
  const handleReset = () => {
    form.resetFields()
    setCostEstimate(null)
    setSelectedType(initialType || 'plot_generation')
    const typeConfig = taskTypes.find(t => t.value === selectedType)
    if (typeConfig) {
      form.setFieldsValue({
        prompt: typeConfig.defaultPrompt
      })
    }
  }

  useEffect(() => {
    if (visible) {
      loadModels()
      const typeConfig = taskTypes.find(t => t.value === selectedType)
      if (typeConfig) {
        form.setFieldsValue({
          type: selectedType,
          prompt: typeConfig.defaultPrompt
        })
      }
    }
  }, [visible, selectedType])

  return (
    <Modal
      title={
        <Space>
          <RobotOutlined />
          <span>创建AI任务</span>
        </Space>
      }
      open={visible}
      onCancel={onCancel}
      footer={null}
      width={900}
              destroyOnHidden
      className="ai-task-creator-modal"
      destroyOnClose
    >
      <div className="task-creator-content">
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            type: selectedType,
            temperature: 0.7,
            maxRetries: 3,
            timeout: 300
          }}
        >
          {/* 任务类型选择 */}
          <Card title="选择任务类型" className="type-selection-card">
            <Row gutter={[16, 16]}>
              {taskTypes.map(type => (
                <Col xs={24} sm={12} lg={8} key={type.value}>
                  <Card
                    className={`type-card ${selectedType === type.value ? 'selected' : ''}`}
                    onClick={() => handleTypeChange(type.value)}
                    hoverable
                  >
                    <div className="type-icon">{type.icon}</div>
                    <Title level={5}>{type.label}</Title>
                    <Text type="secondary">{type.description}</Text>
                  </Card>
                </Col>
              ))}
            </Row>
          </Card>

          <Divider />

          {/* 基本信息 */}
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="title"
                label="任务标题"
                rules={[
                  { required: true, message: '请输入任务标题' },
                  { max: 100, message: '标题长度不能超过100个字符' }
                ]}
              >
                <Input placeholder="请输入任务标题" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="type"
                label="任务类型"
                rules={[{ required: true, message: '请选择任务类型' }]}
              >
                <Select disabled>
                  {taskTypes.map(type => (
                    <Option key={type.value} value={type.value}>
                      {type.icon} {type.label}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
          </Row>

          <Form.Item
            name="description"
            label="任务描述"
            rules={[
              { required: true, message: '请输入任务描述' },
              { max: 500, message: '描述长度不能超过500个字符' }
            ]}
          >
            <TextArea
              placeholder="请描述这个AI任务的目标和要求"
              rows={3}
              showCount
              maxLength={500}
            />
          </Form.Item>

          <Form.Item
            name="prompt"
            label="AI提示词"
            rules={[
              { required: true, message: '请输入AI提示词' },
              { min: 10, message: '提示词至少需要10个字符' }
            ]}
          >
            <TextArea
              placeholder="请输入详细的AI提示词"
              rows={8}
              showCount
              maxLength={2000}
            />
          </Form.Item>

          <Divider />

          {/* AI参数配置 */}
          <Card title="AI参数配置" className="ai-config-card">
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item
                  name="model"
                  label="AI模型"
                  rules={[{ required: true, message: '请选择AI模型' }]}
                >
                  <Select
                    placeholder="选择AI模型"
                    onChange={handleModelChange}
                    loading={models.length === 0}
                  >
                    {models.length === 0 ? (
                      <Option value="" disabled>暂无可用模型</Option>
                    ) : models.map(model => (
                      <Option key={model.id} value={model.id}>
                        <Space>
                          <Text code>{model.name}</Text>
                          <Text type="secondary">${model.costPer1kTokens}/1K tokens</Text>
                        </Space>
                      </Option>
                    ))}
                  </Select>
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  name="maxTokens"
                  label="最大Token数"
                  rules={[
                    { required: true, message: '请输入最大Token数' },
                    { type: 'number', min: 1, max: 4000, message: 'Token数必须在1-4000之间' }
                  ]}
                >
                  <InputNumber
                    placeholder="最大Token数"
                    min={1}
                    max={4000}
                    style={{ width: '100%' }}
                  />
                </Form.Item>
              </Col>
            </Row>

            <Form.Item
              name="temperature"
              label="温度参数 (创造性)"
              rules={[{ required: true, message: '请设置温度参数' }]}
            >
              <Slider
                min={0}
                max={2}
                step={0.1}
                marks={{
                  0: '保守',
                  0.7: '平衡',
                  1.5: '创意',
                  2: '随机'
                }}
                tooltip={{ formatter: (value) => `${value}` }}
              />
            </Form.Item>

            <Row gutter={16}>
              <Col span={12}>
                <Form.Item
                  name="maxRetries"
                  label="最大重试次数"
                  rules={[
                    { required: true, message: '请输入最大重试次数' },
                    { type: 'number', min: 0, max: 5, message: '重试次数必须在0-5之间' }
                  ]}
                >
                  <InputNumber
                    placeholder="最大重试次数"
                    min={0}
                    max={5}
                    style={{ width: '100%' }}
                  />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  name="timeout"
                  label="超时时间 (秒)"
                  rules={[
                    { required: true, message: '请输入超时时间' },
                    { type: 'number', min: 30, max: 600, message: '超时时间必须在30-600秒之间' }
                  ]}
                >
                  <InputNumber
                    placeholder="超时时间"
                    min={30}
                    max={600}
                    style={{ width: '100%' }}
                  />
                </Form.Item>
              </Col>
            </Row>
          </Card>

          {/* 成本估算 */}
          <Card title="成本估算" className="cost-estimate-card">
            <Space direction="vertical" style={{ width: '100%' }}>
              <Button
                type="dashed"
                icon={<DollarOutlined />}
                onClick={estimateCost}
                loading={estimating}
                disabled={!form.getFieldValue('prompt')}
              >
                估算成本
              </Button>
              
              {costEstimate && (
                <Alert
                  message={
                    <Space>
                      <DollarOutlined />
                      <span>预估成本: ${costEstimate.estimatedCost.toFixed(4)}</span>
                    </Space>
                  }
                  description={
                    <div>
                      <Text>预估Token数: {costEstimate.estimatedTokens}</Text>
                      <br />
                      <Text>模型: {costEstimate.model}</Text>
                      <br />
                      <Text>最大Token数: {costEstimate.maxTokens}</Text>
                    </div>
                  }
                  type="info"
                  showIcon
                />
              )}
            </Space>
          </Card>

          {/* 操作按钮 */}
          <div className="form-actions">
            <Space>
              <Button onClick={handleReset}>重置</Button>
              <Button onClick={onCancel}>取消</Button>
              <Button
                type="primary"
                icon={<RobotOutlined />}
                onClick={handleSubmit}
                loading={loading}
              >
                创建AI任务
              </Button>
            </Space>
          </div>
        </Form>
      </div>
    </Modal>
  )
}

export default AITaskCreator 