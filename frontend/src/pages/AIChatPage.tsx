import React, { useState, useEffect, useRef } from 'react';
import { Avatar, Button, Input, Modal, message as antMessage, Empty, Spin } from 'antd';
import { useSearchParams } from 'react-router-dom';
import {
  PlusOutlined,
  UserOutlined,
  SendOutlined,
  MessageOutlined,
  RobotOutlined,
  DeleteOutlined,
  EditOutlined,
  CheckOutlined,
  CloseOutlined,
  CopyOutlined,
  StopOutlined,
  DownCircleOutlined,
  AppstoreOutlined,
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
import { getGeneratorById, getAllGenerators, AiGenerator } from '../services/aiGeneratorService';
import { creditService, AIModel } from '../services/creditService';

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
  generatorId?: number;
}

interface ModelOption {
  id: string;
  name: string;
  value: string;
  description?: string;
  costMultiplier?: number;
  temperature?: number;
  isDefault?: boolean;
}

const AIChatPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const [chatSessions, setChatSessions] = useState<ChatSession[]>([]);
  const [currentSessionId, setCurrentSessionId] = useState<string | null>(null);
  const [inputValue, setInputValue] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [generatorModalVisible, setGeneratorModalVisible] = useState(false);
  const [modelModalVisible, setModelModalVisible] = useState(false);
  const [currentModel, setCurrentModel] = useState<string>('');
  const [modelList, setModelList] = useState<ModelOption[]>([]);
  const [editingMessageId, setEditingMessageId] = useState<string | null>(null);
  const [editingContent, setEditingContent] = useState('');
  const [showScrollButton, setShowScrollButton] = useState(false);
  const [autoScroll, setAutoScroll] = useState(true);
  const [currentGenerator, setCurrentGenerator] = useState<AiGenerator | null>(null);
  const [generators, setGenerators] = useState<AiGenerator[]>([]);
  const [loadingGenerators, setLoadingGenerators] = useState(false);
  const abortControllerRef = useRef<AbortController | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const messagesContainerRef = useRef<HTMLDivElement>(null);

  const currentSession = chatSessions.find(s => s.id === currentSessionId);

  // 智能滚动
  useEffect(() => {
    if (autoScroll && messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [currentSession?.messages, autoScroll]);

  // 监听滚动
  const handleScroll = () => {
    if (!messagesContainerRef.current) return;
    const { scrollTop, scrollHeight, clientHeight } = messagesContainerRef.current;
    const isNearBottom = scrollHeight - scrollTop - clientHeight < 100;
    setAutoScroll(isNearBottom);
    setShowScrollButton(!isNearBottom);
  };

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
        })
        .catch(err => {
          console.error('加载生成器失败:', err);
          antMessage.error('加载生成器失败');
        });
    }
  }, [searchParams]);

  // 加载生成器列表
  const loadGenerators = async () => {
    setLoadingGenerators(true);
    try {
      const data = await getAllGenerators();
      setGenerators(data);
    } catch (error) {
      console.error('加载生成器列表失败:', error);
      antMessage.error('加载生成器列表失败');
    } finally {
      setLoadingGenerators(false);
    }
  };

  // 从后端加载可用模型列表
  useEffect(() => {
    const loadModels = async () => {
      try {
        const models = await creditService.getAvailableModels();
        if (models && models.length > 0) {
          const modelOptions: ModelOption[] = models.map((m: AIModel) => ({
            id: String(m.id),
            name: m.displayName,
            value: m.modelId,
            description: m.description,
            costMultiplier: m.costMultiplier,
            temperature: m.temperature,
            isDefault: m.isDefault
          }));
          setModelList(modelOptions);
          
          const savedModel = localStorage.getItem('ai-chat-current-model');
          if (!savedModel) {
            const defaultModel = modelOptions.find(m => m.isDefault) || modelOptions[0];
            if (defaultModel) {
              setCurrentModel(defaultModel.value);
            }
          }
        }
      } catch (error) {
        console.error('加载模型列表失败:', error);
      }
    };
    loadModels();
  }, []);

  // 加载聊天记录
  useEffect(() => {
    const savedModel = localStorage.getItem('ai-chat-current-model');
    if (savedModel) {
      setCurrentModel(savedModel);
    }

    const savedSessions = localStorage.getItem('ai-chat-sessions');
    if (savedSessions) {
      try {
        const sessions = JSON.parse(savedSessions);
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

    const savedCurrentSessionId = localStorage.getItem('ai-chat-current-session-id');
    if (savedCurrentSessionId) {
      setCurrentSessionId(savedCurrentSessionId);
    }
  }, []);

  // 保存聊天会话
  useEffect(() => {
    if (chatSessions.length > 0) {
      try {
        localStorage.setItem('ai-chat-sessions', JSON.stringify(chatSessions));
      } catch (e) {
        console.error('Failed to save sessions:', e);
      }
    }
  }, [chatSessions]);

  useEffect(() => {
    if (currentSessionId) {
      localStorage.setItem('ai-chat-current-session-id', currentSessionId);
    }
  }, [currentSessionId]);

  const createNewChat = () => {
    const newSession: ChatSession = {
      id: Date.now().toString(),
      title: '新对话',
      messages: [],
      timestamp: new Date(),
      generatorId: currentGenerator?.id,
    };
    setChatSessions([newSession, ...chatSessions]);
    setCurrentSessionId(newSession.id);
  };

  const deleteChat = (sessionId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    const updatedSessions = chatSessions.filter(s => s.id !== sessionId);
    setChatSessions(updatedSessions);
    
    if (currentSessionId === sessionId) {
      if (updatedSessions.length > 0) {
        setCurrentSessionId(updatedSessions[0].id);
      } else {
        setCurrentSessionId(null);
      }
    }
    antMessage.success('对话已删除');
  };

  const copyMessage = (content: string) => {
    navigator.clipboard.writeText(content).then(() => {
      antMessage.success('已复制到剪贴板');
    }).catch(() => {
      antMessage.error('复制失败');
    });
  };

  const stopGeneration = () => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      setIsLoading(false);
      antMessage.info('已中断生成');
    }
  };

  const handleSendMessage = async () => {
    if (!inputValue.trim()) return;

    // 如果没有当前会话，自动创建一个
    let sessionId = currentSessionId;
    if (!sessionId) {
      const newSession: ChatSession = {
        id: Date.now().toString(),
        title: inputValue.trim().slice(0, 20),
        messages: [],
        timestamp: new Date(),
        generatorId: currentGenerator?.id,
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

    const aiMessageId = (Date.now() + 1).toString();
    const aiMessage: Message = {
      id: aiMessageId,
      role: 'assistant',
      content: '',
      timestamp: new Date(),
    };

    setChatSessions(prev =>
      prev.map(session =>
        session.id === sessionId
          ? { ...session, messages: [...session.messages, aiMessage] }
          : session
      )
    );

    try {
      const session = chatSessions.find(s => s.id === sessionId);
      
      let messages: Array<{ role: 'user' | 'assistant' | 'system', content: string }> = [];
      
      if (currentGenerator && session?.messages.length === 0) {
        messages.push({
          role: 'system',
          content: currentGenerator.prompt,
        });
      }
      
      messages = [
        ...messages,
        ...(session?.messages || []).map(msg => ({
          role: msg.role,
          content: msg.content,
        })),
        { role: 'user' as const, content: userInput },
      ];

      abortControllerRef.current = new AbortController();
      
      // 调用后端 API
      const response = await fetch('/api/ai/chat-stream', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${localStorage.getItem('token')}`,
        },
        body: JSON.stringify({
          model: currentModel,
          messages: messages,
          generatorId: currentGenerator?.id,
        }),
        signal: abortControllerRef.current.signal,
      });

      if (!response.ok) {
        throw new Error(`请求失败: ${response.status} ${response.statusText}`);
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
                const content = json.choices?.[0]?.delta?.content || json.content;
                
                if (content) {
                  fullContent += content;
                  
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
                // 可能是纯文本
                if (data && data !== '[DONE]') {
                  fullContent += data;
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

  const startEditMessage = (messageId: string, content: string) => {
    setEditingMessageId(messageId);
    setEditingContent(content);
  };

  const cancelEdit = () => {
    setEditingMessageId(null);
    setEditingContent('');
  };

  const saveEditAndResend = async (messageId: string) => {
    if (!editingContent.trim() || !currentSessionId) return;

    const session = chatSessions.find(s => s.id === currentSessionId);
    if (!session) return;

    const messageIndex = session.messages.findIndex(m => m.id === messageId);
    if (messageIndex === -1) return;

    const messagesBeforeEdit = session.messages.slice(0, messageIndex);
    
    setChatSessions(prev =>
      prev.map(s =>
        s.id === currentSessionId
          ? { ...s, messages: messagesBeforeEdit }
          : s
      )
    );

    setEditingMessageId(null);
    const newContent = editingContent.trim();
    setEditingContent('');

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

    setIsLoading(true);

    const aiMessageId = (Date.now() + 1).toString();
    const aiMessage: Message = {
      id: aiMessageId,
      role: 'assistant',
      content: '',
      timestamp: new Date(),
    };

    setChatSessions(prev =>
      prev.map(s =>
        s.id === currentSessionId
          ? { ...s, messages: [...messagesBeforeEdit, newUserMessage, aiMessage] }
          : s
      )
    );

    try {
      let messages: Array<{ role: 'user' | 'assistant' | 'system', content: string }> = [];
      
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

      abortControllerRef.current = new AbortController();
      
      const response = await fetch('/api/ai/chat-stream', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${localStorage.getItem('token')}`,
        },
        body: JSON.stringify({
          model: currentModel,
          messages: messages,
          generatorId: currentGenerator?.id,
        }),
        signal: abortControllerRef.current.signal,
      });

      if (!response.ok) {
        throw new Error(`请求失败: ${response.status} ${response.statusText}`);
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
                const content = json.choices?.[0]?.delta?.content || json.content;
                
                if (content) {
                  fullContent += content;
                  
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
                if (data && data !== '[DONE]') {
                  fullContent += data;
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

  // 选择生成器
  const handleSelectGenerator = (generator: AiGenerator) => {
    setCurrentGenerator(generator);
    setGeneratorModalVisible(false);
    antMessage.success(`已选择: ${generator.name}`);
  };

  // 选择模型
  const handleSelectModel = (model: ModelOption) => {
    setCurrentModel(model.value);
    localStorage.setItem('ai-chat-current-model', model.value);
    setModelModalVisible(false);
  };

  return (
    <div className="ai-chat-page">
      {/* 左侧对话列表 */}
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
              <div className="empty-hint">点击上方按钮开始新对话</div>
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
      </div>

      {/* 右侧对话区域 */}
      <div className="chat-main">
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
                <h2 className="welcome-title">
                  {currentGenerator ? currentGenerator.name : 'AI 智能助手'}
                </h2>
                <p className="welcome-description">
                  {currentGenerator 
                    ? currentGenerator.description 
                    : '你好！我是你的 AI 智能助手，可以帮助你完成各种创作任务。选择一个生成器开始对话吧！'
                  }
                </p>
                <div className="action-buttons">
                  <Button 
                    type="default" 
                    className="action-btn"
                    icon={<AppstoreOutlined />}
                    onClick={() => {
                      loadGenerators();
                      setGeneratorModalVisible(true);
                    }}
                  >
                    选择生成器
                  </Button>
                  <Button 
                    type="default" 
                    className="action-btn" 
                    icon={<PlusOutlined />}
                    onClick={createNewChat}
                  >
                    开始新对话
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

        {showScrollButton && (
          <Button
            className="scroll-to-bottom-btn"
            shape="circle"
            size="large"
            icon={<DownCircleOutlined />}
            onClick={scrollToBottom}
          />
        )}

        {/* 输入区域 */}
        <div className="chat-input-area">
          {currentGenerator && (
            <div className="generator-info-card">
              <div className="generator-info-content">
                <div className="generator-icon">
                  <RobotOutlined />
                </div>
                <div className="generator-details">
                  <div className="generator-name">{currentGenerator.name}</div>
                  <div className="generator-desc">{currentGenerator.description}</div>
                </div>
                <Button
                  type="text"
                  size="small"
                  icon={<CloseOutlined />}
                  onClick={() => setCurrentGenerator(null)}
                  className="generator-close-btn"
                />
              </div>
            </div>
          )}
          
          <div className="input-toolbar">
            <Button 
              className="toolbar-btn generator-btn"
              icon={<AppstoreOutlined />}
              onClick={() => {
                loadGenerators();
                setGeneratorModalVisible(true);
              }}
            >
              {currentGenerator ? currentGenerator.name : '选择生成器'}
            </Button>
            <Button 
              className="toolbar-btn model-btn"
              icon={<RobotOutlined />}
              onClick={() => setModelModalVisible(true)}
            >
              {modelList.find(m => m.value === currentModel)?.name || '默认模型'}
            </Button>
          </div>
          
          <div className="input-wrapper">
            <TextArea
              value={inputValue}
              onChange={e => setInputValue(e.target.value)}
              onKeyPress={handleKeyPress}
              placeholder="输入您的问题..."
              autoSize={{ minRows: 1, maxRows: 6 }}
              className="chat-input"
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
            Enter 发送 · Shift+Enter 换行
          </div>
        </div>
      </div>

      {/* 生成器选择弹窗 */}
      <Modal
        title={
          <div className="modal-title">
            <AppstoreOutlined className="modal-title-icon" />
            <span>选择生成器</span>
          </div>
        }
        open={generatorModalVisible}
        onCancel={() => setGeneratorModalVisible(false)}
        footer={null}
        width={600}
        className="generator-modal"
      >
        {loadingGenerators ? (
          <div className="loading-container">
            <Spin size="large" />
            <div className="loading-text">加载中...</div>
          </div>
        ) : generators.length === 0 ? (
          <Empty description="暂无可用生成器" />
        ) : (
          <div className="generator-list">
            {generators.map(generator => (
              <div
                key={generator.id}
                className={`generator-item ${currentGenerator?.id === generator.id ? 'active' : ''}`}
                onClick={() => handleSelectGenerator(generator)}
              >
                <div className="generator-item-content">
                  <div className="generator-item-name">{generator.name}</div>
                  <div className="generator-item-desc">{generator.description}</div>
                </div>
                {currentGenerator?.id === generator.id && (
                  <CheckOutlined className="generator-item-check" />
                )}
              </div>
            ))}
          </div>
        )}
      </Modal>

      {/* 模型选择弹窗 */}
      <Modal
        title={
          <div className="modal-title">
            <RobotOutlined className="modal-title-icon" />
            <span>选择模型</span>
          </div>
        }
        open={modelModalVisible}
        onCancel={() => setModelModalVisible(false)}
        footer={null}
        width={500}
        className="model-modal"
      >
        {modelList.length === 0 ? (
          <Empty description="暂无可用模型" />
        ) : (
          <div className="model-list">
            {modelList.map(model => (
              <div
                key={model.id}
                className={`model-item ${currentModel === model.value ? 'active' : ''}`}
                onClick={() => handleSelectModel(model)}
              >
                <div className="model-item-content">
                  <div className="model-item-name">{model.name}</div>
                  {model.description && (
                    <div className="model-item-desc">{model.description}</div>
                  )}
                </div>
                {currentModel === model.value && (
                  <CheckOutlined className="model-item-check" />
                )}
              </div>
            ))}
          </div>
        )}
      </Modal>
    </div>
  );
};

export default AIChatPage;
