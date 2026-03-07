import { useState, useEffect, useRef } from 'react'
import {
  Card,
  Input,
  Button,
  Space,
  Typography,
  Tag,
  Modal,
  Form,
  message,
  Tooltip,
  Popconfirm,
  Empty,
  Spin,
  Divider,
  Progress,
} from 'antd'
import {
  PlusOutlined,
  DeleteOutlined,
  PlayCircleOutlined,
  StopOutlined,
  RobotOutlined,
  MessageOutlined,
  CheckCircleOutlined,
  HistoryOutlined,
  CopyOutlined,
  EditOutlined,
} from '@ant-design/icons'

const { TextArea } = Input
const { Title, Text, Paragraph } = Typography

// 类型定义
interface AIModel {
  id: string
  name: string
  apiKey: string
  baseUrl: string
  model: string
  color: string
}

interface DiscussionMessage {
  id: string
  modelId: string
  modelName: string
  content: string
  timestamp: number
  round: number
  type: 'critique' | 'suggestion' | 'final'
}

interface OptimizationTask {
  id: string
  originalPrompt: string
  currentPrompt: string
  models: string[]
  messages: DiscussionMessage[]
  currentRound: number
  maxRounds: number
  status: 'idle' | 'running' | 'completed' | 'stopped'
  createdAt: number
  updatedAt: number
}

// 预设颜色
const MODEL_COLORS = [
  '#0ea5e9', '#22c55e', '#f97316', '#a855f7', '#ec4899',
  '#14b8a6', '#eab308', '#6366f1', '#ef4444', '#84cc16'
]

// 本地存储 key
const STORAGE_KEYS = {
  MODELS: 'prompt_optimizer_models',
  TASKS: 'prompt_optimizer_tasks',
  CURRENT_TASK: 'prompt_optimizer_current_task',
}

const PromptOptimizer = () => {
  // 状态
  const [models, setModels] = useState<AIModel[]>([])
  const [tasks, setTasks] = useState<OptimizationTask[]>([])
  const [currentTask, setCurrentTask] = useState<OptimizationTask | null>(null)
  const [modelModalVisible, setModelModalVisible] = useState(false)
  const [historyModalVisible, setHistoryModalVisible] = useState(false)
  const [editingModel, setEditingModel] = useState<AIModel | null>(null)
  const [inputPrompt, setInputPrompt] = useState('')
  const [selectedModels, setSelectedModels] = useState<string[]>([])
  const [isRunning, setIsRunning] = useState(false)
  const abortControllerRef = useRef<AbortController | null>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const [form] = Form.useForm()

  // 加载本地数据
  useEffect(() => {
    const savedModels = localStorage.getItem(STORAGE_KEYS.MODELS)
    const savedTasks = localStorage.getItem(STORAGE_KEYS.TASKS)
    const savedCurrentTask = localStorage.getItem(STORAGE_KEYS.CURRENT_TASK)

    if (savedModels) setModels(JSON.parse(savedModels))
    if (savedTasks) setTasks(JSON.parse(savedTasks))
    if (savedCurrentTask) setCurrentTask(JSON.parse(savedCurrentTask))
  }, [])

  // 保存数据到本地
  useEffect(() => {
    localStorage.setItem(STORAGE_KEYS.MODELS, JSON.stringify(models))
  }, [models])

  useEffect(() => {
    localStorage.setItem(STORAGE_KEYS.TASKS, JSON.stringify(tasks))
  }, [tasks])

  useEffect(() => {
    if (currentTask) {
      localStorage.setItem(STORAGE_KEYS.CURRENT_TASK, JSON.stringify(currentTask))
    }
  }, [currentTask])

  // 滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [currentTask?.messages])

  // 添加/编辑模型
  const handleSaveModel = (values: any) => {
    const modelData: AIModel = {
      id: editingModel?.id || `model_${Date.now()}`,
      name: values.name,
      apiKey: values.apiKey,
      baseUrl: values.baseUrl,
      model: values.model,
      color: editingModel?.color || MODEL_COLORS[models.length % MODEL_COLORS.length],
    }

    if (editingModel) {
      setModels(models.map(m => m.id === editingModel.id ? modelData : m))
    } else {
      setModels([...models, modelData])
    }

    setModelModalVisible(false)
    setEditingModel(null)
    form.resetFields()
    message.success(editingModel ? '模型已更新' : '模型已添加')
  }

  // 删除模型
  const handleDeleteModel = (id: string) => {
    setModels(models.filter(m => m.id !== id))
    setSelectedModels(selectedModels.filter(mid => mid !== id))
    message.success('模型已删除')
  }

  // 调用 AI API
  const callAI = async (model: AIModel, messages: { role: string; content: string }[], signal: AbortSignal): Promise<string> => {
    const response = await fetch(`${model.baseUrl}/chat/completions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${model.apiKey}`,
      },
      body: JSON.stringify({
        model: model.model,
        messages,
        temperature: 0.7,
        max_tokens: 2000,
      }),
      signal,
    })

    if (!response.ok) {
      throw new Error(`API 调用失败: ${response.status}`)
    }

    const data = await response.json()
    return data.choices[0].message.content
  }


  // 开始优化任务
  const startOptimization = async () => {
    if (!inputPrompt.trim()) {
      message.warning('请输入要优化的提示词')
      return
    }
    if (selectedModels.length < 2) {
      message.warning('请至少选择2个AI模型参与讨论')
      return
    }

    const task: OptimizationTask = {
      id: `task_${Date.now()}`,
      originalPrompt: inputPrompt,
      currentPrompt: inputPrompt,
      models: selectedModels,
      messages: [],
      currentRound: 0,
      maxRounds: 5,
      status: 'running',
      createdAt: Date.now(),
      updatedAt: Date.now(),
    }

    setCurrentTask(task)
    setIsRunning(true)
    abortControllerRef.current = new AbortController()

    try {
      await runDiscussion(task)
    } catch (error: any) {
      if (error.name !== 'AbortError') {
        message.error(`优化过程出错: ${error.message}`)
      }
    } finally {
      setIsRunning(false)
    }
  }

  // 运行讨论
  const runDiscussion = async (task: OptimizationTask) => {
    let currentPrompt = task.currentPrompt
    let round = 1
    let consensusReached = false

    while (round <= task.maxRounds && !consensusReached) {
      // 更新轮次
      task.currentRound = round
      task.updatedAt = Date.now()
      setCurrentTask({ ...task })

      const roundMessages: DiscussionMessage[] = []
      const critiques: string[] = []

      // 每个模型轮流发言
      for (let i = 0; i < task.models.length; i++) {
        if (abortControllerRef.current?.signal.aborted) {
          throw new DOMException('Aborted', 'AbortError')
        }

        const modelId = task.models[i]
        const model = models.find(m => m.id === modelId)
        if (!model) continue

        // 构建上下文
        const previousCritiques = roundMessages
          .filter(m => m.round === round)
          .map(m => `${m.modelName}: ${m.content}`)
          .join('\n\n')

        const systemPrompt = `你是一个专业的提示词优化专家，名为"${model.name}"。你的任务是分析和优化提示词。

当前是第 ${round} 轮讨论。

规则：
1. 仔细分析当前提示词的问题和不足
2. 如果有其他AI的意见，你可以同意或反驳，但必须给出理由
3. 提出具体的改进建议
4. 如果你认为提示词已经足够好，明确说明"我认为当前提示词已经足够完善"
5. 保持专业、客观的态度
6. 回复要简洁有力，不要过于冗长`

        const userPrompt = previousCritiques
          ? `当前提示词：
"""
${currentPrompt}
"""

其他AI的意见：
${previousCritiques}

请分析这个提示词，可以对其他AI的意见进行评价（同意或反驳并说明理由），然后给出你的改进建议。`
          : `当前提示词：
"""
${currentPrompt}
"""

请分析这个提示词存在的问题，并给出你的改进建议。`

        try {
          const response = await callAI(
            model,
            [
              { role: 'system', content: systemPrompt },
              { role: 'user', content: userPrompt },
            ],
            abortControllerRef.current!.signal
          )

          const msg: DiscussionMessage = {
            id: `msg_${Date.now()}_${i}`,
            modelId: model.id,
            modelName: model.name,
            content: response,
            timestamp: Date.now(),
            round,
            type: 'critique',
          }

          roundMessages.push(msg)
          critiques.push(`${model.name}: ${response}`)
          task.messages = [...task.messages, msg]
          task.updatedAt = Date.now()
          setCurrentTask({ ...task })

          // 检查是否达成共识
          if (response.includes('已经足够完善') || response.includes('没有问题') || response.includes('非常好')) {
            // 统计认为完善的数量
            const approvalCount = task.messages
              .filter(m => m.round === round)
              .filter(m => 
                m.content.includes('已经足够完善') || 
                m.content.includes('没有问题') || 
                m.content.includes('非常好')
              ).length

            if (approvalCount >= Math.ceil(task.models.length * 0.7)) {
              consensusReached = true
            }
          }
        } catch (error: any) {
          if (error.name === 'AbortError') throw error
          
          const errorMsg: DiscussionMessage = {
            id: `msg_${Date.now()}_${i}`,
            modelId: model.id,
            modelName: model.name,
            content: `[调用失败: ${error.message}]`,
            timestamp: Date.now(),
            round,
            type: 'critique',
          }
          task.messages = [...task.messages, errorMsg]
          setCurrentTask({ ...task })
        }

        // 添加延迟避免请求过快
        await new Promise(resolve => setTimeout(resolve, 500))
      }

      // 如果没有达成共识，让最后一个模型生成新的提示词
      if (!consensusReached && round < task.maxRounds) {
        const lastModelId = task.models[task.models.length - 1]
        const lastModel = models.find(m => m.id === lastModelId)
        
        if (lastModel) {
          const synthesisPrompt = `你是提示词优化专家。根据以下讨论，生成一个优化后的新提示词。

原始提示词：
"""
${currentPrompt}
"""

本轮讨论意见：
${critiques.join('\n\n')}

请综合所有意见，生成一个改进后的提示词。只输出新的提示词内容，不要有任何解释或前缀。`

          try {
            const newPrompt = await callAI(
              lastModel,
              [
                { role: 'system', content: '你是一个提示词优化专家，请根据讨论结果生成优化后的提示词。' },
                { role: 'user', content: synthesisPrompt },
              ],
              abortControllerRef.current!.signal
            )

            const finalMsg: DiscussionMessage = {
              id: `msg_${Date.now()}_final`,
              modelId: lastModel.id,
              modelName: lastModel.name,
              content: `📝 **第 ${round} 轮优化后的提示词：**\n\n${newPrompt}`,
              timestamp: Date.now(),
              round,
              type: 'final',
            }

            task.messages = [...task.messages, finalMsg]
            task.currentPrompt = newPrompt
            currentPrompt = newPrompt
            setCurrentTask({ ...task })
          } catch (error: any) {
            if (error.name === 'AbortError') throw error
            message.error(`生成新提示词失败: ${error.message}`)
          }
        }
      }

      round++
    }

    // 完成
    task.status = consensusReached ? 'completed' : 'stopped'
    task.updatedAt = Date.now()
    setCurrentTask({ ...task })
    setTasks([task, ...tasks])
    
    message.success(consensusReached ? '优化完成！所有AI达成共识' : `已完成 ${task.maxRounds} 轮讨论`)
  }

  // 停止优化
  const stopOptimization = () => {
    abortControllerRef.current?.abort()
    if (currentTask) {
      const updatedTask = { ...currentTask, status: 'stopped' as const, updatedAt: Date.now() }
      setCurrentTask(updatedTask)
      setTasks([updatedTask, ...tasks.filter(t => t.id !== currentTask.id)])
    }
    setIsRunning(false)
    message.info('已停止优化')
  }

  // 复制文本
  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text)
    message.success('已复制到剪贴板')
  }

  // 加载历史任务
  const loadTask = (task: OptimizationTask) => {
    setCurrentTask(task)
    setInputPrompt(task.originalPrompt)
    setSelectedModels(task.models)
    setHistoryModalVisible(false)
  }

  // 获取模型信息
  const getModelInfo = (modelId: string) => {
    return models.find(m => m.id === modelId)
  }


  return (
    <div style={{ display: 'flex', gap: 20, height: 'calc(100vh - 140px)' }}>
      {/* 左侧配置面板 */}
      <Card
        style={{ width: 360, flexShrink: 0, display: 'flex', flexDirection: 'column' }}
        styles={{ body: { flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' } }}
      >
        <div style={{ marginBottom: 16 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
            <Title level={5} style={{ margin: 0 }}>
              <RobotOutlined style={{ marginRight: 8 }} />
              AI 模型配置
            </Title>
            <Space>
              <Tooltip title="历史记录">
                <Button
                  type="text"
                  icon={<HistoryOutlined />}
                  onClick={() => setHistoryModalVisible(true)}
                />
              </Tooltip>
              <Button
                type="primary"
                size="small"
                icon={<PlusOutlined />}
                onClick={() => {
                  setEditingModel(null)
                  form.resetFields()
                  setModelModalVisible(true)
                }}
              >
                添加模型
              </Button>
            </Space>
          </div>

          {/* 模型列表 */}
          <div style={{ maxHeight: 200, overflowY: 'auto', marginBottom: 16 }}>
            {models.length === 0 ? (
              <Empty description="暂无模型，请先添加" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
              <Space direction="vertical" style={{ width: '100%' }} size={8}>
                {models.map(model => (
                  <div
                    key={model.id}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '8px 12px',
                      background: selectedModels.includes(model.id) ? 'rgba(14, 165, 233, 0.15)' : 'var(--bg-hover)',
                      borderRadius: 8,
                      border: `1px solid ${selectedModels.includes(model.id) ? model.color : 'var(--border-primary)'}`,
                      cursor: 'pointer',
                      transition: 'all 0.2s',
                    }}
                    onClick={() => {
                      if (selectedModels.includes(model.id)) {
                        setSelectedModels(selectedModels.filter(id => id !== model.id))
                      } else {
                        setSelectedModels([...selectedModels, model.id])
                      }
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <div
                        style={{
                          width: 8,
                          height: 8,
                          borderRadius: '50%',
                          background: model.color,
                        }}
                      />
                      <Text strong style={{ fontSize: 13 }}>{model.name}</Text>
                      <Text type="secondary" style={{ fontSize: 11 }}>{model.model}</Text>
                    </div>
                    <Space size={4}>
                      <Button
                        type="text"
                        size="small"
                        icon={<EditOutlined />}
                        onClick={(e) => {
                          e.stopPropagation()
                          setEditingModel(model)
                          form.setFieldsValue(model)
                          setModelModalVisible(true)
                        }}
                      />
                      <Popconfirm
                        title="确定删除此模型？"
                        onConfirm={(e) => {
                          e?.stopPropagation()
                          handleDeleteModel(model.id)
                        }}
                        onCancel={(e) => e?.stopPropagation()}
                      >
                        <Button
                          type="text"
                          size="small"
                          danger
                          icon={<DeleteOutlined />}
                          onClick={(e) => e.stopPropagation()}
                        />
                      </Popconfirm>
                    </Space>
                  </div>
                ))}
              </Space>
            )}
          </div>

          {selectedModels.length > 0 && (
            <div style={{ marginBottom: 12 }}>
              <Text type="secondary" style={{ fontSize: 12 }}>
                已选择 {selectedModels.length} 个模型参与讨论
              </Text>
            </div>
          )}
        </div>

        <Divider style={{ margin: '12px 0' }} />

        {/* 提示词输入 */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
          <Title level={5} style={{ margin: '0 0 12px 0' }}>
            <MessageOutlined style={{ marginRight: 8 }} />
            输入提示词
          </Title>
          <TextArea
            value={inputPrompt}
            onChange={(e) => setInputPrompt(e.target.value)}
            placeholder="请输入需要优化的提示词..."
            style={{ flex: 1, minHeight: 150, resize: 'none' }}
            disabled={isRunning}
          />
          <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
            {isRunning ? (
              <Button
                danger
                icon={<StopOutlined />}
                onClick={stopOptimization}
                block
              >
                停止优化
              </Button>
            ) : (
              <Button
                type="primary"
                icon={<PlayCircleOutlined />}
                onClick={startOptimization}
                disabled={!inputPrompt.trim() || selectedModels.length < 2}
                block
              >
                开始优化
              </Button>
            )}
          </div>
        </div>
      </Card>

      {/* 右侧讨论面板 */}
      <Card
        style={{ flex: 1, display: 'flex', flexDirection: 'column' }}
        styles={{ body: { flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', padding: 0 } }}
      >
        {/* 头部状态 */}
        <div style={{ padding: '16px 20px', borderBottom: '1px solid var(--border-primary)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <Title level={5} style={{ margin: 0 }}>
                AI 协作讨论
              </Title>
              {currentTask && (
                <Text type="secondary" style={{ fontSize: 12 }}>
                  第 {currentTask.currentRound} / {currentTask.maxRounds} 轮
                  {currentTask.status === 'completed' && ' · 已达成共识'}
                  {currentTask.status === 'stopped' && ' · 已停止'}
                </Text>
              )}
            </div>
            {currentTask && (
              <Space>
                <Tag color={
                  currentTask.status === 'running' ? 'processing' :
                  currentTask.status === 'completed' ? 'success' : 'default'
                }>
                  {currentTask.status === 'running' ? '讨论中' :
                   currentTask.status === 'completed' ? '已完成' : '已停止'}
                </Tag>
                {currentTask.currentPrompt !== currentTask.originalPrompt && (
                  <Tooltip title="复制最终提示词">
                    <Button
                      type="text"
                      icon={<CopyOutlined />}
                      onClick={() => copyToClipboard(currentTask.currentPrompt)}
                    />
                  </Tooltip>
                )}
              </Space>
            )}
          </div>
          {currentTask && isRunning && (
            <Progress
              percent={(currentTask.currentRound / currentTask.maxRounds) * 100}
              showInfo={false}
              strokeColor="#0ea5e9"
              style={{ marginTop: 8 }}
            />
          )}
        </div>

        {/* 消息列表 */}
        <div style={{ flex: 1, overflow: 'auto', padding: 20 }}>
          {!currentTask ? (
            <div style={{ 
              height: '100%', 
              display: 'flex', 
              alignItems: 'center', 
              justifyContent: 'center',
              flexDirection: 'column',
              gap: 16,
            }}>
              <RobotOutlined style={{ fontSize: 48, color: 'var(--text-tertiary)' }} />
              <Text type="secondary">配置模型并输入提示词开始优化</Text>
            </div>
          ) : currentTask.messages.length === 0 ? (
            <div style={{ 
              height: '100%', 
              display: 'flex', 
              alignItems: 'center', 
              justifyContent: 'center',
              flexDirection: 'column',
              gap: 16,
            }}>
              <Spin size="large" />
              <Text type="secondary">AI 正在分析提示词...</Text>
            </div>
          ) : (
            <Space direction="vertical" style={{ width: '100%' }} size={16}>
              {/* 按轮次分组显示 */}
              {Array.from(new Set(currentTask.messages.map(m => m.round))).map(round => (
                <div key={round}>
                  <Divider orientation="left" style={{ margin: '8px 0 16px' }}>
                    <Tag color="blue">第 {round} 轮讨论</Tag>
                  </Divider>
                  <Space direction="vertical" style={{ width: '100%' }} size={12}>
                    {currentTask.messages
                      .filter(m => m.round === round)
                      .map(msg => {
                        const modelInfo = getModelInfo(msg.modelId)
                        return (
                          <div
                            key={msg.id}
                            style={{
                              padding: 16,
                              background: msg.type === 'final' 
                                ? 'linear-gradient(135deg, rgba(14, 165, 233, 0.1), rgba(6, 182, 212, 0.1))'
                                : 'var(--bg-hover)',
                              borderRadius: 12,
                              border: `1px solid ${msg.type === 'final' ? 'rgba(14, 165, 233, 0.3)' : 'var(--border-primary)'}`,
                            }}
                          >
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                              <div
                                style={{
                                  width: 28,
                                  height: 28,
                                  borderRadius: '50%',
                                  background: modelInfo?.color || '#666',
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  fontSize: 14,
                                }}
                              >
                                <RobotOutlined style={{ color: '#fff' }} />
                              </div>
                              <Text strong style={{ color: modelInfo?.color }}>{msg.modelName}</Text>
                              {msg.type === 'final' && (
                                <Tag color="cyan" style={{ marginLeft: 'auto' }}>优化结果</Tag>
                              )}
                            </div>
                            <Paragraph
                              style={{ 
                                margin: 0, 
                                whiteSpace: 'pre-wrap',
                                fontSize: 13,
                                lineHeight: 1.7,
                              }}
                            >
                              {msg.content}
                            </Paragraph>
                          </div>
                        )
                      })}
                  </Space>
                </div>
              ))}
              <div ref={messagesEndRef} />
            </Space>
          )}
        </div>

        {/* 最终结果 */}
        {currentTask && currentTask.status !== 'running' && currentTask.currentPrompt !== currentTask.originalPrompt && (
          <div style={{ 
            padding: 16, 
            borderTop: '1px solid var(--border-primary)',
            background: 'rgba(34, 197, 94, 0.05)',
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
              <Text strong style={{ color: '#22c55e' }}>
                <CheckCircleOutlined style={{ marginRight: 8 }} />
                最终优化结果
              </Text>
              <Button
                type="primary"
                size="small"
                icon={<CopyOutlined />}
                onClick={() => copyToClipboard(currentTask.currentPrompt)}
              >
                复制
              </Button>
            </div>
            <div style={{ 
              padding: 12, 
              background: 'var(--bg-hover)', 
              borderRadius: 8,
              maxHeight: 150,
              overflow: 'auto',
            }}>
              <Text style={{ whiteSpace: 'pre-wrap', fontSize: 13 }}>
                {currentTask.currentPrompt}
              </Text>
            </div>
          </div>
        )}
      </Card>


      {/* 添加/编辑模型弹窗 */}
      <Modal
        title={editingModel ? '编辑模型' : '添加 AI 模型'}
        open={modelModalVisible}
        onCancel={() => {
          setModelModalVisible(false)
          setEditingModel(null)
          form.resetFields()
        }}
        footer={null}
        width={480}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSaveModel}
          initialValues={{
            baseUrl: 'https://api.openai.com/v1',
            model: 'gpt-3.5-turbo',
          }}
        >
          <Form.Item
            name="name"
            label="模型名称"
            rules={[{ required: true, message: '请输入模型名称' }]}
          >
            <Input placeholder="例如：GPT-4、Claude、通义千问" />
          </Form.Item>
          <Form.Item
            name="baseUrl"
            label="API 地址"
            rules={[{ required: true, message: '请输入 API 地址' }]}
            extra="OpenAI 兼容格式，例如：https://api.openai.com/v1"
          >
            <Input placeholder="https://api.openai.com/v1" />
          </Form.Item>
          <Form.Item
            name="apiKey"
            label="API Key"
            rules={[{ required: true, message: '请输入 API Key' }]}
          >
            <Input.Password placeholder="sk-..." />
          </Form.Item>
          <Form.Item
            name="model"
            label="模型标识"
            rules={[{ required: true, message: '请输入模型标识' }]}
            extra="模型的具体名称，例如：gpt-4、gpt-3.5-turbo、claude-3-opus"
          >
            <Input placeholder="gpt-3.5-turbo" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => {
                setModelModalVisible(false)
                setEditingModel(null)
                form.resetFields()
              }}>
                取消
              </Button>
              <Button type="primary" htmlType="submit">
                {editingModel ? '保存' : '添加'}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* 历史记录弹窗 */}
      <Modal
        title="历史记录"
        open={historyModalVisible}
        onCancel={() => setHistoryModalVisible(false)}
        footer={null}
        width={600}
      >
        {tasks.length === 0 ? (
          <Empty description="暂无历史记录" />
        ) : (
          <div style={{ maxHeight: 400, overflow: 'auto' }}>
            <Space direction="vertical" style={{ width: '100%' }} size={12}>
              {tasks.map(task => (
                <div
                  key={task.id}
                  style={{
                    padding: 16,
                    background: 'var(--bg-hover)',
                    borderRadius: 8,
                    border: '1px solid var(--border-primary)',
                    cursor: 'pointer',
                    transition: 'all 0.2s',
                  }}
                  onClick={() => loadTask(task)}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.borderColor = 'var(--border-hover)'
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.borderColor = 'var(--border-primary)'
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 8 }}>
                    <div style={{ flex: 1 }}>
                      <Text 
                        style={{ 
                          display: 'block',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                          maxWidth: 400,
                        }}
                      >
                        {task.originalPrompt.substring(0, 100)}...
                      </Text>
                    </div>
                    <Tag color={task.status === 'completed' ? 'success' : 'default'}>
                      {task.status === 'completed' ? '已完成' : '已停止'}
                    </Tag>
                  </div>
                  <div style={{ display: 'flex', gap: 16 }}>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {task.currentRound} 轮讨论
                    </Text>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {task.models.length} 个模型
                    </Text>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {new Date(task.createdAt).toLocaleString()}
                    </Text>
                  </div>
                </div>
              ))}
            </Space>
          </div>
        )}
      </Modal>
    </div>
  )
}

export default PromptOptimizer
