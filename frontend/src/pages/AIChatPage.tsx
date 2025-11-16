import React, { useState, useEffect, useRef } from 'react';
import { Avatar, Button, Input, Modal, Form, message as antMessage, Divider } from 'antd';
import { useSearchParams } from 'react-router-dom';
import {
  PlusOutlined,
  UserOutlined,
  FileTextOutlined,
  TranslationOutlined,
  BulbOutlined,
  SendOutlined,
  MessageOutlined,
  SettingOutlined,
  RobotOutlined,
  DeleteOutlined,
  DownOutlined,
  EditOutlined,
  CheckOutlined,
  CloseOutlined,
  CopyOutlined,
  StopOutlined,
  DownCircleOutlined,
} from '@ant-design/icons';
import ReactMarkdown from 'react-markdown';
// @ts-ignore
import remarkGfm from 'remark-gfm';
// @ts-ignore
import rehypeRaw from 'rehype-raw';
// @ts-ignore
import rehypeHighlight from 'rehype-highlight';
import 'highlight.js/styles/github-dark.css';
import './AIChatPage.css';
import { getGeneratorById, AiGenerator } from '../services/aiGeneratorService';

const { TextArea } = Input;

interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
}

interface ChatSession {
  id: string;
  title: string;
  messages: Message[];
  timestamp: Date;
}

interface AIConfig {
  apiBaseUrl: string;  // 基础域名
  apiKey: string;
}

interface ModelOption {
  id: string;
  name: string;
  value: string;
}

// 预置常用模型
const DEFAULT_MODELS: ModelOption[] = [
  { id: '1', name: 'GPT-3.5 Turbo', value: 'gpt-3.5-turbo' },
  { id: '2', name: 'GPT-4', value: 'gpt-4' },
  { id: '3', name: 'GPT-4 Turbo', value: 'gpt-4-turbo-preview' },
  { id: '4', name: 'Claude 3 Opus', value: 'claude-3-opus-20240229' },
  { id: '5', name: 'Claude 3 Sonnet', value: 'claude-3-sonnet-20240229' },
  { id: '6', name: 'Claude 3 Haiku', value: 'claude-3-haiku-20240307' },
  { id: '7', name: 'Grok', value: 'grok-1' },
  { id: '8', name: 'DeepSeek Chat', value: 'deepseek-chat' },
  { id: '9', name: 'DeepSeek Coder', value: 'deepseek-coder' },
];

const AIChatPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const [chatSessions, setChatSessions] = useState<ChatSession[]>([]);
  const [currentSessionId, setCurrentSessionId] = useState<string | null>(null);
  const [inputValue, setInputValue] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [configModalVisible, setConfigModalVisible] = useState(false);
  const [modelModalVisible, setModelModalVisible] = useState(false);
  const [aiConfig, setAiConfig] = useState<AIConfig>({
    apiBaseUrl: '',
    apiKey: '',
  });
  const [currentModel, setCurrentModel] = useState<string>('gpt-3.5-turbo');
  const [modelList, setModelList] = useState<ModelOption[]>(DEFAULT_MODELS);
  const [customModelValue, setCustomModelValue] = useState('');
  const [editingMessageId, setEditingMessageId] = useState<string | null>(null);
  const [editingContent, setEditingContent] = useState('');
  const [showScrollButton, setShowScrollButton] = useState(false);
  const [autoScroll, setAutoScroll] = useState(true);
  const [currentGenerator, setCurrentGenerator] = useState<AiGenerator | null>(null);
  const [form] = Form.useForm();
  const abortControllerRef = useRef<AbortController | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const messagesContainerRef = useRef<HTMLDivElement>(null);

  const currentSession = chatSessions.find(s => s.id === currentSessionId);

  // 智能滚动：只在用户位于底部时自动滚动
  useEffect(() => {
    if (autoScroll && messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [currentSession?.messages, autoScroll]);

  // 监听滚动，判断用户是否在底部
  const handleScroll = () => {
    if (!messagesContainerRef.current) return;
    
    const { scrollTop, scrollHeight, clientHeight } = messagesContainerRef.current;
    const isNearBottom = scrollHeight - scrollTop - clientHeight < 100;
    
    setAutoScroll(isNearBottom);
    setShowScrollButton(!isNearBottom);
  };

  // 滚动到底部
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    setAutoScroll(true);
    setShowScrollButton(false);
  };

  // 加载 generator 从 URL
  useEffect(() => {
    const generatorId = searchParams.get('generatorId');
    if (generatorId) {
      getGeneratorById(Number(generatorId))
        .then(generator => {
          setCurrentGenerator(generator);
          antMessage.success(`已选择生成器: ${generator.name}`);
        })
        .catch(err => {
          console.error('加载生成器失败:', err);
          antMessage.error('加载生成器失败');
        });
    }
  }, [searchParams]);

  // 加载配置和聊天记录
  useEffect(() => {
    // 加载API配置
    const savedConfig = localStorage.getItem('ai-chat-config');
    if (savedConfig) {
      try {
        const config = JSON.parse(savedConfig);
        setAiConfig(config);
      } catch (e) {
        console.error('Failed to parse config:', e);
      }
    }

    // 加载当前模型
    const savedModel = localStorage.getItem('ai-chat-current-model');
    if (savedModel) {
      setCurrentModel(savedModel);
    }

    // 加载模型列表
    const savedModels = localStorage.getItem('ai-chat-models');
    if (savedModels) {
      try {
        const models = JSON.parse(savedModels);
        setModelList(models);
      } catch (e) {
        console.error('Failed to parse models:', e);
      }
    }

    // 加载聊天会话
    const savedSessions = localStorage.getItem('ai-chat-sessions');
    if (savedSessions) {
      try {
        const sessions = JSON.parse(savedSessions);
        // 恢复 Date 对象
        const restoredSessions = sessions.map((session: any) => ({
          ...session,
          timestamp: new Date(session.timestamp),
          messages: session.messages.map((msg: any) => ({
            ...msg,
            timestamp: new Date(msg.timestamp),
          })),
        }));
        setChatSessions(restoredSessions);
      } catch (e) {
        console.error('Failed to parse sessions:', e);
      }
    }

    // 加载当前会话ID
    const savedCurrentSessionId = localStorage.getItem('ai-chat-current-session-id');
    if (savedCurrentSessionId) {
      setCurrentSessionId(savedCurrentSessionId);
    }
  }, []);

  // 保存聊天会话到 localStorage
  useEffect(() => {
    if (chatSessions.length > 0) {
      try {
        localStorage.setItem('ai-chat-sessions', JSON.stringify(chatSessions));
      } catch (e) {
        console.error('Failed to save sessions:', e);
      }
    }
  }, [chatSessions]);

  // 保存当前会话ID到 localStorage
  useEffect(() => {
    if (currentSessionId) {
      localStorage.setItem('ai-chat-current-session-id', currentSessionId);
    }
  }, [currentSessionId]);

  // 保存配置
  const saveConfig = (values: AIConfig) => {
    localStorage.setItem('ai-chat-config', JSON.stringify(values));
    setAiConfig(values);
    setConfigModalVisible(false);
    antMessage.success('配置已保存');
  };

  // 切换模型
  const handleModelChange = (value: string) => {
    setCurrentModel(value);
    localStorage.setItem('ai-chat-current-model', value);
  };

  // 添加自定义模型
  const handleAddCustomModel = () => {
    if (!customModelValue.trim()) {
      antMessage.warning('请输入模型值');
      return;
    }

    const newModel: ModelOption = {
      id: Date.now().toString(),
      name: customModelValue.trim(), // 使用模型值作为名称
      value: customModelValue.trim(),
    };

    const updatedModels = [...modelList, newModel];
    setModelList(updatedModels);
    localStorage.setItem('ai-chat-models', JSON.stringify(updatedModels));
    
    setCustomModelValue('');
    antMessage.success('模型添加成功');
  };

  // 删除自定义模型
  const handleDeleteModel = (modelId: string) => {
    const updatedModels = modelList.filter(m => m.id !== modelId && !DEFAULT_MODELS.find(dm => dm.id === modelId));
    setModelList(updatedModels);
    localStorage.setItem('ai-chat-models', JSON.stringify(updatedModels));
    antMessage.success('模型已删除');
  };

  // 打开配置模态框
  const openConfigModal = () => {
    form.setFieldsValue(aiConfig);
    setConfigModalVisible(true);
  };

  const createNewChat = () => {
    const newSession: ChatSession = {
      id: Date.now().toString(),
      title: '新对话',
      messages: [],
      timestamp: new Date(),
    };
    setChatSessions([newSession, ...chatSessions]);
    setCurrentSessionId(newSession.id);
  };

  // 删除对话
  const deleteChat = (sessionId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    const updatedSessions = chatSessions.filter(s => s.id !== sessionId);
    setChatSessions(updatedSessions);
    
    // 如果删除的是当前对话，切换到第一个对话或清空
    if (currentSessionId === sessionId) {
      if (updatedSessions.length > 0) {
        setCurrentSessionId(updatedSessions[0].id);
      } else {
        setCurrentSessionId(null);
      }
    }
    
    antMessage.success('对话已删除');
  };


  // 复制消息内容
  const copyMessage = (content: string) => {
    navigator.clipboard.writeText(content).then(() => {
      antMessage.success('已复制到剪贴板');
    }).catch(() => {
      antMessage.error('复制失败');
    });
  };

  // 中断请求
  const stopGeneration = () => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      setIsLoading(false);
      antMessage.info('已中断生成');
    }
  };

  const handleSendMessage = async () => {
    if (!inputValue.trim()) return;

    // 检查配置
    if (!aiConfig.apiBaseUrl || !aiConfig.apiKey) {
      antMessage.warning('请先配置API地址和API Key');
      setConfigModalVisible(true);
      return;
    }

    // 如果没有当前会话，自动创建一个
    let sessionId = currentSessionId;
    if (!sessionId) {
      const newSession: ChatSession = {
        id: Date.now().toString(),
        title: inputValue.trim().slice(0, 20),
        messages: [],
        timestamp: new Date(),
      };
      setChatSessions([newSession, ...chatSessions]);
      sessionId = newSession.id;
      setCurrentSessionId(sessionId);
    }

    const userMessage: Message = {
      id: Date.now().toString(),
      role: 'user',
      content: inputValue.trim(),
      timestamp: new Date(),
    };

    // 添加用户消息
    setChatSessions(prev =>
      prev.map(session =>
        session.id === sessionId
          ? {
              ...session,
              messages: [...session.messages, userMessage],
              title: session.messages.length === 0 ? inputValue.trim().slice(0, 20) : session.title,
            }
          : session
      )
    );

    const userInput = inputValue.trim();
    setInputValue('');
    setIsLoading(true);

    // 创建AI消息
    const aiMessageId = (Date.now() + 1).toString();
    const aiMessage: Message = {
      id: aiMessageId,
      role: 'assistant',
      content: '',
      timestamp: new Date(),
    };

    // 添加空的AI消息
    setChatSessions(prev =>
      prev.map(session =>
        session.id === sessionId
          ? { ...session, messages: [...session.messages, aiMessage] }
          : session
      )
    );

    try {
      // 获取当前会话的历史消息
      const session = chatSessions.find(s => s.id === sessionId);
      
      // 构建消息数组
      let messages: Array<{ role: 'user' | 'assistant' | 'system', content: string }> = [];
      
      // 如果有 generator，添加 system prompt
      if (currentGenerator && session?.messages.length === 0) {
        messages.push({
          role: 'system',
          content: currentGenerator.prompt,
        });
      }
      
      // 添加历史消息
      messages = [
        ...messages,
        ...(session?.messages || []).map(msg => ({
          role: msg.role,
          content: msg.content,
        })),
        { role: 'user' as const, content: userInput },
      ];

      // 调用API - 流式输出
      abortControllerRef.current = new AbortController();
      
      // 拼接完整API地址
      const apiUrl = `${aiConfig.apiBaseUrl.replace(/\/$/, '')}/v1/chat/completions`;
      
      const response = await fetch(apiUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${aiConfig.apiKey}`,
        },
        body: JSON.stringify({
          model: currentModel,
          messages: messages,
          stream: true,
        }),
        signal: abortControllerRef.current.signal,
      });

      if (!response.ok) {
        throw new Error(`API请求失败: ${response.status} ${response.statusText}`);
      }

      const reader = response.body?.getReader();
      const decoder = new TextDecoder();
      let fullContent = '';

      if (reader) {
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          const chunk = decoder.decode(value, { stream: true });
          const lines = chunk.split('\n');

          for (const line of lines) {
            if (line.startsWith('data: ')) {
              const data = line.slice(6);
              if (data === '[DONE]') continue;

              try {
                const json = JSON.parse(data);
                const content = json.choices?.[0]?.delta?.content;
                
                if (content) {
                  fullContent += content;
                  
                  // 更新消息内容
                  setChatSessions(prev =>
                    prev.map(session =>
                      session.id === sessionId
                        ? {
                            ...session,
                            messages: session.messages.map(msg =>
                              msg.id === aiMessageId
                                ? { ...msg, content: fullContent }
                                : msg
                            ),
                          }
                        : session
                    )
                  );
                }
              } catch (e) {
                console.error('JSON parse error:', e);
              }
            }
          }
        }
      }

      setIsLoading(false);
    } catch (error: any) {
      console.error('API调用失败:', error);
      
      if (error.name === 'AbortError') {
        antMessage.info('已取消请求');
      } else {
        antMessage.error(`请求失败: ${error.message}`);
        
        // 更新消息显示错误
        setChatSessions(prev =>
          prev.map(session =>
            session.id === sessionId
              ? {
                  ...session,
                  messages: session.messages.map(msg =>
                    msg.id === aiMessageId
                      ? { ...msg, content: `错误: ${error.message}` }
                      : msg
                  ),
                }
              : session
          )
        );
      }
      
      setIsLoading(false);
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  // 开始编辑消息
  const startEditMessage = (messageId: string, content: string) => {
    setEditingMessageId(messageId);
    setEditingContent(content);
  };

  // 取消编辑
  const cancelEdit = () => {
    setEditingMessageId(null);
    setEditingContent('');
  };

  // 保存编辑并重新发送
  const saveEditAndResend = async (messageId: string) => {
    if (!editingContent.trim() || !currentSessionId) return;

    // 找到该消息的索引
    const session = chatSessions.find(s => s.id === currentSessionId);
    if (!session) return;

    const messageIndex = session.messages.findIndex(m => m.id === messageId);
    if (messageIndex === -1) return;

    // 删除该消息及之后的所有消息
    const messagesBeforeEdit = session.messages.slice(0, messageIndex);
    
    // 更新会话，只保留编辑消息之前的消息
    setChatSessions(prev =>
      prev.map(s =>
        s.id === currentSessionId
          ? { ...s, messages: messagesBeforeEdit }
          : s
      )
    );

    // 重置编辑状态
    setEditingMessageId(null);
    const newContent = editingContent.trim();
    setEditingContent('');

    // 添加新的用户消息
    const newUserMessage: Message = {
      id: Date.now().toString(),
      role: 'user',
      content: newContent,
      timestamp: new Date(),
    };

    setChatSessions(prev =>
      prev.map(s =>
        s.id === currentSessionId
          ? { ...s, messages: [...messagesBeforeEdit, newUserMessage] }
          : s
      )
    );

    // 检查配置
    if (!aiConfig.apiBaseUrl || !aiConfig.apiKey) {
      antMessage.warning('请先配置API地址和API Key');
      setConfigModalVisible(true);
      return;
    }

    setIsLoading(true);

    // 创建AI消息
    const aiMessageId = (Date.now() + 1).toString();
    const aiMessage: Message = {
      id: aiMessageId,
      role: 'assistant',
      content: '',
      timestamp: new Date(),
    };

    // 添加空的AI消息
    setChatSessions(prev =>
      prev.map(s =>
        s.id === currentSessionId
          ? { ...s, messages: [...messagesBeforeEdit, newUserMessage, aiMessage] }
          : s
      )
    );

    try {
      // 获取编辑消息之前的历史
      let messages: Array<{ role: 'user' | 'assistant' | 'system', content: string }> = [];
      
      // 如果有 generator 且是第一条消息，添加 system prompt
      if (currentGenerator && messagesBeforeEdit.length === 0) {
        messages.push({
          role: 'system',
          content: currentGenerator.prompt,
        });
      }
      
      messages = [
        ...messages,
        ...messagesBeforeEdit.map(msg => ({
          role: msg.role,
          content: msg.content,
        })),
        { role: 'user' as const, content: newContent },
      ];

      // 调用API - 流式输出
      abortControllerRef.current = new AbortController();
      
      const apiUrl = `${aiConfig.apiBaseUrl.replace(/\/$/, '')}/v1/chat/completions`;
      
      const response = await fetch(apiUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${aiConfig.apiKey}`,
        },
        body: JSON.stringify({
          model: currentModel,
          messages: messages,
          stream: true,
        }),
        signal: abortControllerRef.current.signal,
      });

      if (!response.ok) {
        throw new Error(`API请求失败: ${response.status} ${response.statusText}`);
      }

      const reader = response.body?.getReader();
      const decoder = new TextDecoder();
      let fullContent = '';

      if (reader) {
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          const chunk = decoder.decode(value, { stream: true });
          const lines = chunk.split('\n');

          for (const line of lines) {
            if (line.startsWith('data: ')) {
              const data = line.slice(6);
              if (data === '[DONE]') continue;

              try {
                const json = JSON.parse(data);
                const content = json.choices?.[0]?.delta?.content;
                
                if (content) {
                  fullContent += content;
                  
                  // 更新消息内容
                  setChatSessions(prev =>
                    prev.map(s =>
                      s.id === currentSessionId
                        ? {
                            ...s,
                            messages: s.messages.map(msg =>
                              msg.id === aiMessageId
                                ? { ...msg, content: fullContent }
                                : msg
                            ),
                          }
                        : s
                    )
                  );
                }
              } catch (e) {
                console.error('JSON parse error:', e);
              }
            }
          }
        }
      }

      setIsLoading(false);
    } catch (error: any) {
      console.error('API调用失败:', error);
      
      if (error.name === 'AbortError') {
        antMessage.info('已取消请求');
      } else {
        antMessage.error(`请求失败: ${error.message}`);
        
        // 更新消息显示错误
        setChatSessions(prev =>
          prev.map(s =>
            s.id === currentSessionId
              ? {
                  ...s,
                  messages: s.messages.map(msg =>
                    msg.id === aiMessageId
                      ? { ...msg, content: `错误: ${error.message}` }
                      : msg
                  ),
                }
              : s
          )
        );
      }
      
      setIsLoading(false);
    }
  };

  return (
    <div className="ai-chat-page-wrapper">
      <div className="ai-chat-page">
      <div className="chat-sidebar">
        <div className="sidebar-header">
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={createNewChat}
            className="new-chat-btn"
            block
          >
            新建对话
          </Button>
        </div>
        <div className="chat-list">
          {chatSessions.length === 0 ? (
            <div className="empty-chat-list">
              <MessageOutlined className="empty-icon" />
              <div className="empty-text">暂无对话记录</div>
            </div>
          ) : (
            chatSessions.map(session => (
              <div
                key={session.id}
                className={`chat-item ${currentSessionId === session.id ? 'active' : ''}`}
                onClick={() => setCurrentSessionId(session.id)}
              >
                <div className="chat-item-content">
                  <div className="chat-item-title">{session.title}</div>
                  <div className="chat-item-time">
                    {session.timestamp.toLocaleTimeString('zh-CN', {
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </div>
                </div>
                <Button
                  type="text"
                  size="small"
                  danger
                  icon={<DeleteOutlined />}
                  className="chat-item-delete"
                  onClick={(e) => deleteChat(session.id, e)}
                />
              </div>
            ))
          )}
        </div>
        
        {/* 配置按钮 */}
        <div className="sidebar-footer">
          <Button
            icon={<SettingOutlined />}
            onClick={openConfigModal}
            className="config-btn"
            block
          >
            API配置
          </Button>
        </div>
      </div>

      <div className="chat-main">
        {/* 消息区域 */}
        <div 
          className="chat-messages" 
          ref={messagesContainerRef}
          onScroll={handleScroll}
        >
          {!currentSession || currentSession.messages.length === 0 ? (
            <div className="chat-welcome">
              <div className="welcome-content">
                <Avatar size={80} className="ai-avatar">
                  AI
                </Avatar>
                <h2 className="welcome-title">AI智能助手</h2>
                <p className="welcome-description">
                  你是一位资深的文案策划师，拥有10年以上的营销文案创作经验。你擅长创作各类营销推广文案，包括产品介绍、广告语、社交媒体文案等。你的文案风格多变，能根据不同受众调整语气，既能写出专业严肃的商务文案，也能创作轻松活泼的社交媒体内容。请用简洁有力的语言，抓住重点突出卖点。
                </p>
                <div className="action-buttons">
                  <Button type="default" className="action-btn">
                    💡 复制
                  </Button>
                  <Button type="default" className="action-btn">
                    ⚡ 重新生成
                  </Button>
                </div>
              </div>
            </div>
          ) : (
            <>
              {currentSession.messages.map((message) => {
                const isEditing = editingMessageId === message.id;
                const isUser = message.role === 'user';
                
                return (
                  <div
                    key={message.id}
                    className={`message-item ${isUser ? 'user-message' : 'ai-message'}`}
                  >
                    <Avatar
                      size={40}
                      className="message-avatar"
                      icon={isUser ? <UserOutlined /> : undefined}
                    >
                      {message.role === 'assistant' ? 'AI' : ''}
                    </Avatar>
                    <div className="message-content">
                      {isEditing ? (
                        <div className="message-edit-container">
                          <div className="message-edit-box">
                            <div className="edit-header">
                              <span className="edit-title">✏️ 编辑消息</span>
                              <span className="edit-hint">编辑后将重新生成AI回复</span>
                            </div>
                            <TextArea
                              value={editingContent}
                              onChange={(e) => setEditingContent(e.target.value)}
                              autoSize={{ minRows: 4, maxRows: 20 }}
                              className="message-edit-input"
                              autoFocus
                              placeholder="输入你的消息..."
                            />
                            <div className="message-edit-actions">
                              <Button
                                type="primary"
                                icon={<CheckOutlined />}
                                onClick={() => saveEditAndResend(message.id)}
                                loading={isLoading}
                                className="edit-save-btn"
                              >
                                保存并重新发送
                              </Button>
                              <Button
                                icon={<CloseOutlined />}
                                onClick={cancelEdit}
                                className="edit-cancel-btn"
                              >
                                取消
                              </Button>
                            </div>
                          </div>
                        </div>
                      ) : (
                        <>
                          <div className="message-text markdown-body">
                            <ReactMarkdown
                              remarkPlugins={[remarkGfm]}
                              rehypePlugins={[rehypeRaw, rehypeHighlight]}
                            >
                              {message.content}
                            </ReactMarkdown>
                          </div>
                          <div className="message-footer">
                            <div className="message-time">
                              {message.timestamp.toLocaleTimeString('zh-CN', {
                                hour: '2-digit',
                                minute: '2-digit',
                              })}
                            </div>
                            <div className="message-actions">
                              {!isUser && message.content && (
                                <Button
                                  type="text"
                                  size="small"
                                  icon={<CopyOutlined />}
                                  className="message-action-btn"
                                  onClick={() => copyMessage(message.content)}
                                >
                                  复制
                                </Button>
                              )}
                              {isUser && !isLoading && (
                                <Button
                                  type="text"
                                  size="small"
                                  icon={<EditOutlined />}
                                  className="message-action-btn"
                                  onClick={() => startEditMessage(message.id, message.content)}
                                >
                                  编辑
                                </Button>
                              )}
                            </div>
                          </div>
                        </>
                      )}
                    </div>
                  </div>
                );
              })}
              {isLoading && (
                <div className="message-item ai-message">
                  <Avatar size={40} className="message-avatar">
                    AI
                  </Avatar>
                  <div className="message-content">
                    <div className="message-loading">
                      <span className="loading-dot"></span>
                      <span className="loading-dot"></span>
                      <span className="loading-dot"></span>
                    </div>
                  </div>
                </div>
              )}
              <div ref={messagesEndRef} />
            </>
          )}
        </div>

        {/* 滚动到底部按钮 */}
        {showScrollButton && (
          <Button
            className="scroll-to-bottom-btn"
            shape="circle"
            size="large"
            icon={<DownCircleOutlined />}
            onClick={scrollToBottom}
          />
        )}

        {/* 输入区域 - 始终显示 */}
        <div className="chat-input-area">
          {/* Generator 信息显示 */}
          {currentGenerator && (
            <div style={{
              background: 'linear-gradient(135deg, #eef2ff 0%, #e0e7ff 100%)',
              border: '2px solid #c7d2fe',
              borderRadius: '12px',
              padding: '16px',
              marginBottom: '16px',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ flex: 1 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '8px' }}>
                    <RobotOutlined style={{ fontSize: 24, color: '#667eea' }} />
                    <div>
                      <div style={{ fontWeight: 600, fontSize: 16, color: '#4338ca' }}>
                        {currentGenerator.name}
                      </div>
                      <div style={{ fontSize: 13, color: '#6366f1', marginTop: '4px' }}>
                        {currentGenerator.description}
                      </div>
                    </div>
                  </div>
                </div>
                <Button
                  size="small"
                  onClick={() => setCurrentGenerator(null)}
                  icon={<CloseOutlined />}
                >
                  取消
                </Button>
              </div>
            </div>
          )}
          
          <div className="input-toolbar">
            <Button 
              className="model-dropdown-btn"
              onClick={() => setModelModalVisible(true)}
            >
              <RobotOutlined />
              {modelList.find(m => m.value === currentModel)?.name || '选择模型'}
              <DownOutlined style={{ marginLeft: 8, fontSize: 10 }} />
            </Button>
            <Button icon={<UserOutlined />} className="toolbar-btn">
              选择角色
            </Button>
            <Button icon={<FileTextOutlined />} className="toolbar-btn">
              参考文章
            </Button>
            <Button icon={<TranslationOutlined />} className="toolbar-btn">
              翻译
            </Button>
            <Button icon={<BulbOutlined />} className="toolbar-btn">
              深度推理
            </Button>
          </div>
          <div className="input-wrapper">
            <TextArea
              value={inputValue}
              onChange={e => setInputValue(e.target.value)}
              onKeyPress={handleKeyPress}
              placeholder="输入您的创作需求..."
              autoSize={{ minRows: 1, maxRows: 6 }}
              className="chat-input"
              style={{ minHeight: '40px' }}
              disabled={isLoading}
            />
            {isLoading ? (
              <Button
                danger
                icon={<StopOutlined />}
                onClick={stopGeneration}
                className="stop-btn"
              >
                中断
              </Button>
            ) : (
              <Button
                type="primary"
                icon={<SendOutlined />}
                onClick={handleSendMessage}
                disabled={!inputValue.trim()}
                className="send-btn"
              />
            )}
          </div>
          <div className="input-hint">
            Enter 发送 • Shift+Enter 换行
          </div>
        </div>
      </div>
    </div>

      {/* 配置模态框 */}
      <Modal
        title="API配置"
        open={configModalVisible}
        onCancel={() => setConfigModalVisible(false)}
        footer={null}
        width={500}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={saveConfig}
          initialValues={aiConfig}
        >
          <Form.Item
            label="API基础地址"
            name="apiBaseUrl"
            rules={[
              { required: true, message: '请输入API基础地址' },
              { type: 'url', message: '请输入有效的URL' },
            ]}
            extra="只需填写域名，例如: https://api.openai.com (后面的路径会自动拼接)"
          >
            <Input
              placeholder="https://api.openai.com"
              size="large"
            />
          </Form.Item>

          <Form.Item
            label="API Key"
            name="apiKey"
            rules={[{ required: true, message: '请输入API Key' }]}
          >
            <Input.Password
              placeholder="sk-xxxxxxxxxxxxxxxx"
              size="large"
            />
          </Form.Item>

          <Form.Item>
            <Button type="primary" htmlType="submit" block size="large">
              保存配置
            </Button>
          </Form.Item>
        </Form>
      </Modal>

      {/* 模型选择模态框 */}
      <Modal
        title={
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <span>选择AI模型</span>
            <span style={{ fontSize: 14, fontWeight: 400, color: '#999' }}>
              当前: {modelList.find(m => m.value === currentModel)?.value || '未选择'}
            </span>
          </div>
        }
        open={modelModalVisible}
        onCancel={() => {
          setModelModalVisible(false);
          setCustomModelValue('');
        }}
        footer={null}
        width={800}
      >
        <div>
          {/* 模型列表 - 网格布局 */}
          <div style={{ 
            display: 'grid', 
            gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
            gap: '12px',
            maxHeight: '400px',
            overflowY: 'auto',
            padding: '4px'
          }}>
            {modelList.map(model => {
              const isSelected = model.value === currentModel;
              const isCustom = !DEFAULT_MODELS.find(dm => dm.id === model.id);
              
              return (
                <div
                  key={model.id}
                  onClick={() => {
                    handleModelChange(model.value);
                    setModelModalVisible(false);
                  }}
                  className="model-card"
                  style={{
                    position: 'relative',
                    padding: '16px 12px',
                    background: isSelected ? 'linear-gradient(135deg, #eef2ff 0%, #e0e7ff 100%)' : '#fff',
                    border: `2px solid ${isSelected ? '#667eea' : '#e5e7eb'}`,
                    borderRadius: 12,
                    cursor: 'pointer',
                    transition: 'all 0.2s',
                    textAlign: 'center',
                    boxShadow: isSelected ? '0 4px 12px rgba(102, 126, 234, 0.2)' : '0 2px 4px rgba(0, 0, 0, 0.05)',
                  }}
                  onMouseEnter={(e) => {
                    if (!isSelected) {
                      e.currentTarget.style.borderColor = '#c7d2fe';
                      e.currentTarget.style.background = '#f9fafb';
                      e.currentTarget.style.transform = 'translateY(-2px)';
                      e.currentTarget.style.boxShadow = '0 6px 16px rgba(102, 126, 234, 0.15)';
                    }
                  }}
                  onMouseLeave={(e) => {
                    if (!isSelected) {
                      e.currentTarget.style.borderColor = '#e5e7eb';
                      e.currentTarget.style.background = '#fff';
                      e.currentTarget.style.transform = 'translateY(0)';
                      e.currentTarget.style.boxShadow = '0 2px 4px rgba(0, 0, 0, 0.05)';
                    }
                  }}
                >
                  {isCustom && (
                    <Button
                      type="text"
                      danger
                      size="small"
                      icon={<DeleteOutlined />}
                      onClick={(e) => {
                        e.stopPropagation();
                        handleDeleteModel(model.id);
                      }}
                      style={{
                        position: 'absolute',
                        top: 4,
                        right: 4,
                        minWidth: 24,
                        height: 24,
                        padding: 0,
                      }}
                    />
                  )}
                  <RobotOutlined style={{ 
                    fontSize: 24, 
                    color: isSelected ? '#667eea' : '#9ca3af',
                    marginBottom: 8
                  }} />
                  <div style={{ 
                    fontSize: 13,
                    fontWeight: isSelected ? 600 : 500,
                    color: isSelected ? '#667eea' : '#333',
                    wordBreak: 'break-all',
                    lineHeight: 1.4
                  }}>
                    {model.value}
                  </div>
                  {isSelected && (
                    <div style={{ 
                      marginTop: 8, 
                      fontSize: 11, 
                      color: '#667eea',
                      fontWeight: 500
                    }}>
                      ✓ 使用中
                    </div>
                  )}
                </div>
              );
            })}
          </div>

          <Divider style={{ margin: '20px 0' }} />

          {/* 添加自定义模型 */}
          <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}>
            <div style={{ flex: 1 }}>
              <label style={{ display: 'block', marginBottom: 8, fontWeight: 500, fontSize: 14 }}>
                添加自定义模型
              </label>
              <Input
                placeholder="输入模型值，例如: gpt-4, claude-3-opus-20240229"
                value={customModelValue}
                onChange={e => setCustomModelValue(e.target.value)}
                onPressEnter={handleAddCustomModel}
                size="large"
              />
            </div>
            <Button
              type="primary"
              onClick={handleAddCustomModel}
              size="large"
              icon={<PlusOutlined />}
            >
              添加
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};

export default AIChatPage;

