import React, { useState, useEffect, useRef } from 'react';
import { Button, Input, Select, Tooltip, message as antMessage, Popconfirm } from 'antd';
import { 
  PlusOutlined, 
  DeleteOutlined, 
  UserOutlined, 
  RobotOutlined, 
  SendOutlined, 
  StopOutlined, 
  SettingOutlined,
  ThunderboltFilled,
  MessageOutlined
} from '@ant-design/icons';
import { useSearchParams } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
// @ts-ignore
import remarkGfm from 'remark-gfm';
// @ts-ignore
import rehypeHighlight from 'rehype-highlight';
import 'highlight.js/styles/github.css';
import './AIChatPage.css';

import { getGeneratorById, getAllGenerators, AiGenerator } from '../services/aiGeneratorService';
import { creditService, AIModel } from '../services/creditService';

const { TextArea } = Input;

interface Message {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: Date;
}

interface ChatSession {
  id: string;
  title: string;
  messages: Message[];
  generatorId?: number;
  updatedAt: Date;
}

const AIChatPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  
  // State
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [currentSessionId, setCurrentSessionId] = useState<string | null>(null);
  const [inputValue, setInputValue] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [models, setModels] = useState<AIModel[]>([]);
  const [currentModel, setCurrentModel] = useState<string>('');
  const [generators, setGenerators] = useState<AiGenerator[]>([]);
  const [currentGenerator, setCurrentGenerator] = useState<AiGenerator | null>(null);
  
  const abortControllerRef = useRef<AbortController | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const currentSession = sessions.find(s => s.id === currentSessionId);

  // Initialization
  useEffect(() => {
    loadSessions();
    loadModels();
    loadGenerators();
  }, []);

  // Handle URL params for generator
  useEffect(() => {
    const generatorId = searchParams.get('generatorId');
    if (generatorId) {
      getGeneratorById(Number(generatorId)).then(gen => {
        setCurrentGenerator(gen);
        // If we have a generator but no session, or want to start fresh, logic can go here
      }).catch(console.error);
    }
  }, [searchParams]);

  // Auto-scroll
  useEffect(() => {
    scrollToBottom();
  }, [currentSession?.messages, isLoading]);

  // Data Loading
  const loadSessions = () => {
    try {
      const saved = localStorage.getItem('ai_chat_sessions');
      if (saved) {
        const parsed = JSON.parse(saved);
        // Restore Dates
        const restored = parsed.map((s: any) => ({
          ...s,
          updatedAt: new Date(s.updatedAt),
          messages: s.messages.map((m: any) => ({ ...m, timestamp: new Date(m.timestamp) }))
        }));
        
        // Sort sessions by update time desc
        restored.sort((a: ChatSession, b: ChatSession) => b.updatedAt.getTime() - a.updatedAt.getTime());
        
        setSessions(restored);
        if (restored.length > 0) {
          const lastId = localStorage.getItem('ai_chat_current_id');
          if (lastId && restored.find((s: ChatSession) => s.id === lastId)) {
            setCurrentSessionId(lastId);
          } else {
            setCurrentSessionId(restored[0].id);
          }
        }
      }
    } catch (e) {
      console.error('Failed to load sessions', e);
    }
  };

  const loadModels = async () => {
    try {
      const data = await creditService.getAvailableModels();
      setModels(data);
      if (data.length > 0) {
        // Use saved model or default
        const savedModel = localStorage.getItem('ai_chat_model');
        const defaultModel = data.find(m => m.isDefault) || data[0];
        setCurrentModel(savedModel && data.find(m => m.modelId === savedModel) ? savedModel : defaultModel.modelId);
      }
    } catch (e) {
      console.error('Failed to load models', e);
    }
  };

  const loadGenerators = async () => {
    try {
      const data = await getAllGenerators();
      setGenerators(data);
    } catch (e) {
      console.error('Failed to load generators', e);
    }
  };

  // Persistence
  useEffect(() => {
    if (sessions.length > 0) {
      localStorage.setItem('ai_chat_sessions', JSON.stringify(sessions));
    }
    if (currentSessionId) {
      localStorage.setItem('ai_chat_current_id', currentSessionId);
    }
  }, [sessions, currentSessionId]);

  useEffect(() => {
    if (currentModel) {
      localStorage.setItem('ai_chat_model', currentModel);
    }
  }, [currentModel]);

  // Actions
  const createNewSession = () => {
    const newSession: ChatSession = {
      id: Date.now().toString(),
      title: '新对话',
      messages: [],
      updatedAt: new Date(),
      generatorId: currentGenerator?.id
    };
    setSessions(prev => [newSession, ...prev]);
    setCurrentSessionId(newSession.id);
  };

  const deleteSession = (id: string, e?: React.MouseEvent) => {
    e?.stopPropagation();
    const newSessions = sessions.filter(s => s.id !== id);
    setSessions(newSessions);
    if (currentSessionId === id) {
      setCurrentSessionId(newSessions.length > 0 ? newSessions[0].id : null);
    }
    // Update storage immediately if empty
    if (newSessions.length === 0) {
      localStorage.removeItem('ai_chat_sessions');
      localStorage.removeItem('ai_chat_current_id');
    }
  };

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  const stopGeneration = () => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      setIsLoading(false);
      antMessage.info('已停止生成');
    }
  };

  const handleSendMessage = async () => {
    if (!inputValue.trim()) return;
    
    let sessionId = currentSessionId;
    let sessionList = sessions;

    // Create session if none exists
    if (!sessionId) {
      const newSession: ChatSession = {
        id: Date.now().toString(),
        title: inputValue.slice(0, 15) || '新对话',
        messages: [],
        updatedAt: new Date(),
        generatorId: currentGenerator?.id
      };
      sessionId = newSession.id;
      sessionList = [newSession, ...sessions];
      setSessions(sessionList);
      setCurrentSessionId(sessionId);
    }

    const userMsg: Message = {
      id: Date.now().toString(),
      role: 'user',
      content: inputValue,
      timestamp: new Date()
    };

    // Update session with user message
    const updatedSessionsWithUser = sessionList.map(s => 
      s.id === sessionId 
        ? { 
            ...s, 
            messages: [...s.messages, userMsg],
            title: s.messages.length === 0 ? inputValue.slice(0, 15) : s.title,
            updatedAt: new Date()
          }
        : s
    );
    // Sort again to move active to top
    updatedSessionsWithUser.sort((a, b) => b.updatedAt.getTime() - a.updatedAt.getTime());

    setSessions(updatedSessionsWithUser);
    setInputValue('');
    setIsLoading(true);

    // Prepare AI placeholder
    const aiMsgId = (Date.now() + 1).toString();
    const aiMsg: Message = {
      id: aiMsgId,
      role: 'assistant',
      content: '',
      timestamp: new Date()
    };

    setSessions(prev => prev.map(s => s.id === sessionId ? { ...s, messages: [...s.messages, aiMsg] } : s));

    try {
      const session = updatedSessionsWithUser.find(s => s.id === sessionId);
      if (!session) return;

      // Construct payload
      let messagesPayload = [];
      // Add system prompt if generator exists
      if (currentGenerator) {
        messagesPayload.push({ role: 'system', content: currentGenerator.prompt });
      } else if (session.generatorId) {
         // Try to find the generator if it was set on session creation but currentGenerator changed
         const gen = generators.find(g => g.id === session.generatorId);
         if (gen) messagesPayload.push({ role: 'system', content: gen.prompt });
      }

      // Add history
      messagesPayload = [
        ...messagesPayload,
        ...session.messages.map(m => ({ role: m.role, content: m.content })),
        { role: 'user', content: userMsg.content }
      ];

      abortControllerRef.current = new AbortController();

      const response = await fetch('/api/ai/chat-stream', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${localStorage.getItem('token')}`
        },
        body: JSON.stringify({
          model: currentModel,
          messages: messagesPayload,
          generatorId: session.generatorId || currentGenerator?.id
        }),
        signal: abortControllerRef.current.signal
      });

      if (!response.ok) throw new Error('API request failed');

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
              const data = line.slice(6).trim();
              if (data === '[DONE]') continue;
              
              try {
                const json = JSON.parse(data);
                const delta = json.choices?.[0]?.delta?.content || json.content || '';
                fullContent += delta;

                // Update UI incrementally
                setSessions(prev => prev.map(s => 
                  s.id === sessionId 
                    ? {
                        ...s,
                        messages: s.messages.map(m => m.id === aiMsgId ? { ...m, content: fullContent } : m)
                      }
                    : s
                ));
              } catch (e) {
                // Ignore parse errors for partial chunks
              }
            }
          }
        }
      }
    } catch (error: any) {
      if (error.name !== 'AbortError') {
        console.error(error);
        antMessage.error('生成失败: ' + error.message);
        // Append error to message
        setSessions(prev => prev.map(s => 
          s.id === sessionId 
            ? {
                ...s,
                messages: s.messages.map(m => m.id === aiMsgId ? { ...m, content: '生成出错，请重试。' } : m)
              }
            : s
        ));
      }
    } finally {
      setIsLoading(false);
      abortControllerRef.current = null;
    }
  };

  // --- Render Helpers ---

  const formatDate = (date: Date) => {
    return date.toLocaleDateString() === new Date().toLocaleDateString()
      ? date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      : date.toLocaleDateString([], { month: 'short', day: 'numeric' });
  };

  const renderEmptyState = () => (
    <div className="chat-empty-state">
      <div className="empty-state-orb">
        <RobotOutlined />
      </div>
      <h2 className="empty-state-title">
        {currentGenerator ? currentGenerator.name : '你好，我是你的AI写作助手'}
      </h2>
      <p className="empty-state-subtitle">
        我可以帮你构思情节、润色描写、设计角色，或者陪你聊聊你的创作灵感。
      </p>
      
      <div className="empty-state-hints">
        {['帮我构思一个悬疑开头', '描写一个赛博朋克风格的城市', '给主角设计一个致命弱点', '解释一下"英雄之旅"结构'].map((hint, i) => (
          <div key={i} className="hint-chip" onClick={() => {
            setInputValue(hint);
            // Optional: auto send?
          }}>
            {hint}
          </div>
        ))}
      </div>
    </div>
  );

  return (
    <div className="chat-page-wrapper">
      {/* Sidebar */}
      <div className="chat-sidebar">
        <div className="sidebar-header">
          <div className="sidebar-brand">
            <div className="sidebar-brand-icon">
              <ThunderboltFilled />
            </div>
            <div className="sidebar-brand-text">AI 灵感空间</div>
          </div>
          <Button 
            className="new-chat-btn"
            icon={<PlusOutlined />} 
            onClick={createNewSession}
          >
            开启新对话
          </Button>
        </div>
        
        <div className="session-list-container">
          {sessions.length === 0 ? (
            <div className="session-empty">
              <MessageOutlined className="session-empty-icon" />
              <div className="session-empty-text">暂无历史记录</div>
            </div>
          ) : (
            sessions.map(session => (
              <div 
                key={session.id} 
                className={`session-card ${currentSessionId === session.id ? 'active' : ''}`}
                onClick={() => setCurrentSessionId(session.id)}
              >
                <div className="session-card-content">
                  <div className="session-card-title">{session.title}</div>
                  <div className="session-card-time">{formatDate(session.updatedAt)}</div>
                </div>
                <Popconfirm 
                  title="删除此对话?" 
                  onConfirm={(e) => deleteSession(session.id, e)} 
                  okText="删除" 
                  cancelText="取消"
                >
                  <DeleteOutlined 
                    className="session-delete-btn" 
                    onClick={(e) => e.stopPropagation()} 
                  />
                </Popconfirm>
              </div>
            ))
          )}
        </div>
      </div>
      
      {/* Main Chat */}
      <div className="chat-main">
        {/* Top Bar */}
        <div className="chat-topbar">
          <div className="topbar-left">
            <span className="topbar-model-label">MODEL</span>
            <Select 
              value={currentModel} 
              className="topbar-model-select"
              style={{ width: 220 }} 
              onChange={setCurrentModel}
              options={models.map(m => ({ label: m.displayName, value: m.modelId }))}
              variant="borderless"
            />
            {currentGenerator && (
               <div className="topbar-generator-tag">
                 <RobotOutlined /> {currentGenerator.name}
               </div>
            )}
          </div>
          <div className="topbar-right">
             <Tooltip title="设置">
               <Button className="topbar-settings-btn" icon={<SettingOutlined />} />
             </Tooltip>
          </div>
        </div>

        {/* Messages Area */}
        <div className="chat-messages-area">
          <div className="chat-messages-inner">
            {!currentSession || currentSession.messages.length === 0 ? (
              renderEmptyState()
            ) : (
              <>
                {currentSession.messages.map(msg => (
                  <div key={msg.id} className={`msg-row ${msg.role === 'user' ? 'user-row' : 'ai-row'}`}>
                    <div className={`msg-avatar ${msg.role === 'user' ? 'user-avatar' : 'ai-avatar'}`}>
                      {msg.role === 'user' ? <UserOutlined /> : <RobotOutlined />}
                    </div>
                    <div className="msg-bubble">
                      <div className="msg-bubble-inner">
                        {msg.role === 'assistant' ? (
                          <ReactMarkdown 
                            remarkPlugins={[remarkGfm]} 
                            rehypePlugins={[rehypeHighlight]}
                          >
                            {msg.content}
                          </ReactMarkdown>
                        ) : (
                          msg.content
                        )}
                      </div>
                      <div className="msg-time">{formatDate(msg.timestamp)}</div>
                    </div>
                  </div>
                ))}
                
                {isLoading && (
                  <div className="typing-indicator-row">
                    <div className="msg-avatar ai-avatar">
                      <RobotOutlined />
                    </div>
                    <div className="typing-dots">
                      <div className="typing-dot"></div>
                      <div className="typing-dot"></div>
                      <div className="typing-dot"></div>
                    </div>
                  </div>
                )}
                <div ref={messagesEndRef} />
              </>
            )}
          </div>
        </div>

        {/* Input Area */}
        <div className="chat-input-wrapper">
          <div className="chat-input-card">
            <TextArea 
              value={inputValue}
              onChange={e => setInputValue(e.target.value)}
              placeholder="输入你的问题或指令..."
              autoSize={{ minRows: 2, maxRows: 8 }}
              onKeyDown={e => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  handleSendMessage();
                }
              }}
            />
            <div className="input-bottom-bar">
              <div className="input-hint-text">
                <span style={{opacity: 0.6}}>支持 Markdown 格式</span>
                <span style={{margin: '0 8px', opacity: 0.3}}>|</span>
                <div style={{display:'flex', alignItems:'center', gap: 6}}>
                   <kbd>Enter</kbd> 发送
                   <kbd>Shift+Enter</kbd> 换行
                </div>
              </div>
              <div className="input-actions-group">
                {isLoading && (
                  <Button 
                    className="stop-gen-btn" 
                    danger 
                    icon={<StopOutlined />} 
                    onClick={stopGeneration}
                  >
                    停止
                  </Button>
                )}
                <Button 
                  className="send-btn"
                  type="primary" 
                  icon={<SendOutlined />} 
                  onClick={handleSendMessage}
                  disabled={!inputValue.trim() || isLoading}
                />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AIChatPage;
