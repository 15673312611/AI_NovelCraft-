import React, { useState, useEffect, useRef } from 'react';
import { 
  Card, Button, Form, Input, Typography, Space, 
  Spin, Divider, Alert, Drawer, List, App, Badge, Modal, Tabs, Empty
} from 'antd';
import { 
  BookOutlined, RobotOutlined, 
  FileTextOutlined, HistoryOutlined, ArrowLeftOutlined, 
  CheckOutlined, ThunderboltOutlined, PlusOutlined, SaveOutlined,
  GlobalOutlined, HeartOutlined, EyeOutlined
} from '@ant-design/icons';
import { useParams, useLocation, useNavigate } from 'react-router-dom';
import api from '@/services/api';
import novelVolumeService, { NovelVolume, VolumeGuidanceRequest } from '../services/novelVolumeService';
import { withAIConfig, checkAIConfig, AI_CONFIG_ERROR_MESSAGE } from '../utils/aiRequest';
import './VolumeWritingStudio.css';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

interface TemplateCardProps {
  template: any;
  selectedId: number | null;
  onSelect: (id: number) => void;
  icon?: React.ReactNode;
}

const TemplateCard: React.FC<TemplateCardProps> = ({ template, selectedId, onSelect, icon }) => {
  const isSelected = selectedId === template.id;
  
  return (
    <div
      onClick={() => onSelect(template.id)}
      style={{
        position: 'relative',
        borderRadius: '24px',
        background: isSelected ? 'white' : 'white',
        cursor: 'pointer',
        transition: 'all 0.4s cubic-bezier(0.19, 1, 0.22, 1)',
        border: 'none',
        // 高级阴影效果
        boxShadow: isSelected 
          ? '0 0 0 2px #7c3aed, 0 20px 40px -10px rgba(124, 58, 237, 0.3)' 
          : '0 0 0 1px #e2e8f0, 0 4px 6px -2px rgba(0,0,0,0.03)',
        transform: isSelected ? 'scale(1.02)' : 'scale(1)',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden'
      }}
      onMouseEnter={(e) => {
        if (!isSelected) {
          e.currentTarget.style.boxShadow = '0 0 0 1px #cbd5e1, 0 12px 24px -8px rgba(0,0,0,0.08)';
          e.currentTarget.style.transform = 'translateY(-4px)';
        }
      }}
      onMouseLeave={(e) => {
        if (!isSelected) {
          e.currentTarget.style.boxShadow = '0 0 0 1px #e2e8f0, 0 4px 6px -2px rgba(0,0,0,0.03)';
          e.currentTarget.style.transform = 'scale(1)';
        }
      }}
    >
      {/* 顶部装饰条 */}
      {isSelected && (
        <div style={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          height: '6px',
          background: 'linear-gradient(90deg, #8b5cf6, #ec4899)'
        }} />
      )}

      <div style={{ padding: '24px', display: 'flex', flexDirection: 'column', height: '100%' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '20px' }}>
          <div style={{ 
            width: '64px', 
            height: '64px', 
            borderRadius: '20px', 
            background: isSelected ? 'linear-gradient(135deg, #f5f3ff 0%, #ede9fe 100%)' : '#f8fafc',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '32px',
            boxShadow: isSelected ? 'inset 0 0 0 1px rgba(139, 92, 246, 0.1)' : 'none',
            border: isSelected ? 'none' : '1px solid #f1f5f9',
            transition: 'all 0.3s ease'
          }}>
            {icon || (template.type === 'official' ? '🏆' : '✨')}
          </div>
          
          <div style={{
            width: '28px',
            height: '28px',
            borderRadius: '50%',
            background: isSelected ? '#7c3aed' : 'transparent',
            border: isSelected ? 'none' : '2px solid #e2e8f0',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: 'white',
            transition: 'all 0.3s ease',
            boxShadow: isSelected ? '0 4px 10px rgba(124, 58, 237, 0.4)' : 'none'
          }}>
            {isSelected && <CheckOutlined style={{ fontSize: '14px', fontWeight: 'bold' }} />}
          </div>
        </div>
        
        <div style={{ marginBottom: '12px' }}>
          <div style={{ 
            fontWeight: 800, 
            fontSize: '18px', 
            color: '#0f172a', 
            marginBottom: '8px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            letterSpacing: '-0.02em'
          }}>
            {template.name}
            {template.type === 'official' && (
              <span style={{ 
                fontSize: '10px', 
                color: '#fff', 
                background: 'linear-gradient(135deg, #f59e0b, #d97706)',
                padding: '2px 8px', 
                borderRadius: '12px',
                fontWeight: 700,
                letterSpacing: '0.05em',
                textTransform: 'uppercase',
                boxShadow: '0 2px 4px rgba(245, 158, 11, 0.3)'
              }}>
                Official
              </span>
            )}
          </div>
          <div style={{ 
            fontSize: '14px', 
            color: '#64748b', 
            lineHeight: '1.6',
            display: '-webkit-box',
            WebkitLineClamp: 3,
            WebkitBoxOrient: 'vertical',
            overflow: 'hidden',
            minHeight: '66px' // 保证高度一致
          }}>
            {template.description || '这个模板暂时没有详细描述，但它一定很棒！'}
          </div>
        </div>

        <div style={{ 
          marginTop: 'auto', 
          paddingTop: '16px', 
          borderTop: '1px solid #f1f5f9',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          color: '#94a3b8',
          fontSize: '13px',
          fontWeight: 500
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <div style={{ 
              padding: '4px 8px', 
              background: '#f8fafc', 
              borderRadius: '6px', 
              color: '#64748b',
              display: 'flex',
              alignItems: 'center',
              gap: '4px'
            }}>
              <EyeOutlined /> {template.usageCount || 0}
            </div>
          </div>
          {isSelected && (
            <span style={{ color: '#7c3aed', fontWeight: 600 }}>当前选择</span>
          )}
        </div>
      </div>
    </div>
  );
};

const VolumeWritingStudio: React.FC = () => {
  const { message } = App.useApp();
  const location = useLocation();
  const searchParams = new URLSearchParams(location.search);
  const templateIdFromUrl = searchParams.get('templateId');
  
  const [currentVolume, setCurrentVolume] = useState<NovelVolume | null>(null);
  const [pageLoading, setPageLoading] = useState(true); // 整个页面加载状态
  const [pageLoadError, setPageLoadError] = useState<string | null>(null); // 页面加载错误
  const [loading, setLoading] = useState(false);
  const [currentContent, setCurrentContent] = useState('');
  const [progressHint, setProgressHint] = useState(''); // 生成进度提示
  const [aiGuidance, setAiGuidance] = useState<any>(null);
  const [guidanceHistory, setGuidanceHistory] = useState<any[]>([]);
  const [wordCount, setWordCount] = useState(0);
  // 已移除前端记忆库干预，上下文由后端构建
  const [chapterNumber, setChapterNumber] = useState<number | null>(null);
  const [chapterTitle, setChapterTitle] = useState<string>('');
  const [userAdjustment, setUserAdjustment] = useState<string>('');
  const [chapterId, setChapterId] = useState<string | null>(null);
  const chapterIdRef = useRef<string | null>(null); // 用于在闭包中访问最新的chapterId
  const chapterNumberRef = useRef<number | null>(null); // 用于在闭包中访问最新的chapterNumber
  const [isStreaming, setIsStreaming] = useState(false);
  const streamingCompleteRef = useRef<boolean>(false); // 标记流式写作是否真正完成（收到complete事件）
  const textareaRef = useRef<any>(null);
    const [aiDrawerVisible, setAiDrawerVisible] = useState(false); // AI写作弹窗
    const [chapterPlotInput, setChapterPlotInput] = useState<string>(''); // 本章剧情输入
    const [selectedModel, setSelectedModel] = useState<string>(''); // 选择的AI模型（空表示使用后端默认）
  
  // 抽屉状态
  const [guidanceDrawerVisible, setGuidanceDrawerVisible] = useState(false);
  const [historyDrawerVisible, setHistoryDrawerVisible] = useState(false);
  const [guidanceLoading, setGuidanceLoading] = useState(false);
  const [historicalChapters, setHistoricalChapters] = useState<any[]>([]);
  const [chaptersLoading, setChaptersLoading] = useState(false);
  const [outlineDrawerVisible, setOutlineDrawerVisible] = useState(false);
  const [volumeOutlineDrawerVisible, setVolumeOutlineDrawerVisible] = useState(false);
  const [editingOutline, setEditingOutline] = useState<string>('');
  const [editingVolumeOutline, setEditingVolumeOutline] = useState<string>('');
  const [outlineLoading, setOutlineLoading] = useState(false);
  const [guidanceForm] = Form.useForm();
  
  // 卷切换相关状态
  const [allVolumes, setAllVolumes] = useState<NovelVolume[]>([]);
  const [novelInfo, setNovelInfo] = useState<any>(null);
  
  // 章节列表相关状态
  const [chapterList, setChapterList] = useState<any[]>([]);
  const [chapterListLoading, setChapterListLoading] = useState(false);
  const [chapterDirHovered, setChapterDirHovered] = useState(false); // 章节目录悬停状态
  
  // 自动保存相关状态
  const autoSaveTimerRef = useRef<number | null>(null);
  const [lastSavedContent, setLastSavedContent] = useState<string>('');
  const [isSaving, setIsSaving] = useState(false);
  const [lastSaveTime, setLastSaveTime] = useState<string>('');
  const [isRemovingAITrace, setIsRemovingAITrace] = useState(false); // AI消痕loading状态
  const [aiTraceDrawerVisible, setAiTraceDrawerVisible] = useState(false); // AI消痕抽屉
  const [processedContent, setProcessedContent] = useState<string>(''); // AI消痕处理后的内容
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | null>(
    templateIdFromUrl ? Number(templateIdFromUrl) : null
  ); // 选中的模板ID（从URL参数初始化）
  const [templateModalVisible, setTemplateModalVisible] = useState(false); // 模板选择弹窗
  const [templateModalTab, setTemplateModalTab] = useState('public'); // 模板弹窗当前tab
  const [publicTemplates, setPublicTemplates] = useState<any[]>([]); // 公开模板
  const [favoriteTemplates, setFavoriteTemplates] = useState<any[]>([]); // 收藏模板
  const [customTemplates, setCustomTemplates] = useState<any[]>([]); // 自定义模板

  // 批量写作相关状态
  const [batchWriting, setBatchWriting] = useState(false); // 批量写作状态
  const [batchProgress, setBatchProgress] = useState({ current: 0, total: 0 }); // 批量写作进度
  const [batchCancelled, setBatchCancelled] = useState(false); // 批量写作是否被取消
  const [batchModalVisible, setBatchModalVisible] = useState(false); // 批量写作进度弹窗

  const { novelId, volumeId} = useParams<{ novelId: string; volumeId: string }>();
  const navigate = useNavigate();

  // 一键格式化：
  // 1) 在句末标点（。？！）簇后换行
  // 2) 若后面紧跟后引号（"或'或常见引号），则在引号后换行
  // 3) 若出现句末标点 + 末引号 + 开引号（" "），则在两个引号之间插入一个空行（段落）
  // 4) 规范：在句末标点与末引号之间保留一个空格（如："……？ "）
  const formatChineseSentences = (input: string): string => {
    if (!input) return '';
    let text = input.replace(/\r\n?/g, '\n');
    // 优先处理：标点簇 + 末引号 + 开引号 -> 保留末引号在本行，之后空一行再开始下一段
    text = text.replace(/([。？！]+)\s*(["'])\s*([""'])/g, '$1 $2\n\n$3');
    // 其次：标点簇 + 末引号（后面不是开引号）-> 在末引号后换行
    text = text.replace(/([。？！]+)\s*(["'])(?!\s*[""'])\s*/g, '$1 $2\n');
    // 再者：标点簇后直接换行（后面没有末引号）
    text = text.replace(/([。？！]+)(?!\s*[""'])\s*/g, '$1\n');
    // 行级清理：去除每行首部的空白（含全角空格），以及行尾空白
    text = text
      .split('\n')
      .map(line => line.replace(/^[\t \u3000]+/g, '').replace(/\s+$/g, ''))
      .join('\n');
    return text;
  };

  useEffect(() => {
    // 页面初始化
    initializePage();
    
    // 从路由状态中获取会话数据
    const state = location.state as any;
    if (state?.sessionData) {
      setAiGuidance(state.sessionData.aiGuidance);
    }
  }, []);


  // 加载提示词模板列表
  const loadPromptTemplates = async () => {
    try {
      // 加载公开模板
      const publicResponse = await api.get('/prompt-templates/public');
      setPublicTemplates(publicResponse.data || []);
      
      // 加载收藏模板
      const favoriteResponse = await api.get('/prompt-templates/favorites');
      setFavoriteTemplates(favoriteResponse.data || []);
      
      // 加载自定义模板
      const customResponse = await api.get('/prompt-templates/custom');
      setCustomTemplates(customResponse.data || []);
    } catch (error) {
      console.error('加载提示词模板失败:', error);
    }
  };

  // 监听章节号变化,检查是否需要切换卷
  useEffect(() => {
    if (chapterNumber && currentVolume && chapterNumber > currentVolume.chapterEnd) {
      checkAndSwitchToNextVolume();
    }
  }, [chapterNumber]);

  // 加载章节列表
  useEffect(() => {
    if (currentVolume) {
      loadChapterList();
    }
  }, [currentVolume]);

  // 同步 chapterId 到 ref，确保闭包中能访问到最新值
  useEffect(() => {
    chapterIdRef.current = chapterId;
    console.log('🔍 chapterId 已更新到 ref:', chapterId);
  }, [chapterId]);

  // 同步 chapterNumber 到 ref，确保闭包中能访问到最新值
  useEffect(() => {
    chapterNumberRef.current = chapterNumber;
    console.log('🔍 chapterNumber 已更新到 ref:', chapterNumber);
  }, [chapterNumber]);

  // 自动保存功能：停止编写1秒后自动保存
  useEffect(() => {
    console.log('🔍 自动保存useEffect触发', {
      contentLength: currentContent?.length,
      lastSavedLength: lastSavedContent?.length,
      chapterTitle,
      chapterId,
      novelId,
      contentPreview: currentContent?.substring(0, 30)
    });
    
    // 清除之前的定时器
    if (autoSaveTimerRef.current) {
      clearTimeout(autoSaveTimerRef.current);
      autoSaveTimerRef.current = null;
    }

    // 如果内容为空或与上次保存的内容相同，不触发自动保存
    if (!currentContent || currentContent === lastSavedContent) {
      console.log('⏭️ 跳过保存：内容为空或未变化', {
        hasContent: !!currentContent,
        isSame: currentContent === lastSavedContent
      });
      setIsSaving(false);
      return;
    }
    
    console.log('⏰ 设置1秒后自动保存定时器...');

    // 标记为"编辑中"（不显示保存状态）
    setIsSaving(false);

    // 设置新的定时器：停止编写1秒后才保存
    const timer = window.setTimeout(async () => {
      try {
        setIsSaving(true);
        console.log('🔄 开始自动保存章节内容...', {
          chapterId,
          title: chapterTitle,
          contentLength: currentContent.length
        });
        
        const payload: any = {
          title: chapterTitle || `第${chapterNumber || '?'}章`,
          content: currentContent,
          chapterNumber: chapterNumber || undefined,
          novelId: novelId ? parseInt(novelId) : undefined
        };

        if (chapterId) {
          // 更新现有章节
          await api.put(`/chapters/${chapterId}`, payload);
          console.log('✅ 自动保存成功 - 更新章节ID:', chapterId);
        } else {
          // 创建新章节
          const res: any = await api.post('/chapters', payload);
          const created = res?.data || res;
          if (created?.id) {
            setChapterId(String(created.id));
            console.log('✅ 自动保存成功 - 创建新章节ID:', created.id);
          }
        }
        
        setLastSavedContent(currentContent);
        
        // 更新最后保存时间（年月日 时分秒）
        const now = new Date();
        const timeStr = `${now.getFullYear()}-${(now.getMonth() + 1).toString().padStart(2, '0')}-${now.getDate().toString().padStart(2, '0')} ${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`;
        setLastSaveTime(timeStr);
        console.log('✅ 保存时间已更新:', timeStr);
        
        setIsSaving(false);
      } catch (e: any) {
        console.error('❌ 自动保存失败:', e);
        setIsSaving(false);
      }
    }, 1000);

    autoSaveTimerRef.current = timer;

    // 清理函数
    return () => {
      if (autoSaveTimerRef.current) {
        clearTimeout(autoSaveTimerRef.current);
      }
    };
  }, [currentContent, chapterTitle, chapterId, lastSavedContent, novelId]);

  // 前端不再加载/持久化记忆库


  useEffect(() => {
    // 计算字数
    const count = currentContent.replace(/\s/g, '').length;
    setWordCount(count);

    // 如果正在流式输出，自动滚动到底部
    if (isStreaming) {
      const textarea = document.querySelector('.writing-textarea') as HTMLTextAreaElement;
      if (textarea) {
        textarea.scrollTop = textarea.scrollHeight;
      }
    }
  }, [currentContent, isStreaming]);

  const loadVolumeById = async (volumeId: string) => {
    try {
      const volumeDetail = await novelVolumeService.getVolumeDetail(volumeId);
      const vol = volumeDetail.volume || volumeDetail;
      setCurrentVolume(vol);
      
      // 初始化章节号：优先读取本地存储的当前章节，其次回退到卷起始章节
      try {
        const saved = localStorage.getItem(`novel_workflow_${novelId}`);
        const parsed = saved ? JSON.parse(saved) : {};
        const savedCurrent = parsed?.workflow?.currentChapter;
        if (typeof savedCurrent === 'number' && (!vol?.chapterEnd || savedCurrent <= vol.chapterEnd)) {
          setChapterNumber(savedCurrent);
        } else if (vol?.chapterStart && typeof vol.chapterStart === 'number') {
          setChapterNumber(vol.chapterStart);
        }
      } catch {
        if (vol?.chapterStart && typeof vol.chapterStart === 'number') {
          setChapterNumber(vol.chapterStart);
        }
      }
    } catch (error: any) {
      // 回退：尝试从小说卷列表中查找该卷
      try {
        if (novelId) {
          const list = await novelVolumeService.getVolumesByNovelId(novelId);
          const found = list.find((v: any) => String(v.id) === String(volumeId));
          if (found) {
            setCurrentVolume(found as any);
            if ((found as any).chapterStart && typeof (found as any).chapterStart === 'number') {
              setChapterNumber((found as any).chapterStart);
            }
          } else {
            throw new Error('未在卷列表中找到对应卷');
          }
        } else {
          throw new Error('缺少小说ID参数');
        }
      } catch (e: any) {
        throw new Error(e.message || '加载卷信息失败');
      }
    }
  };

  // 页面初始化：加载所有必要数据
  const initializePage = async () => {
    setPageLoading(true);
    setPageLoadError(null);
    
    try {
      // 1. 检查必要参数
      if (!novelId) {
        throw new Error('缺少小说ID参数');
      }
      if (!volumeId) {
        throw new Error('缺少卷ID参数');
      }
      
      // 2. 并行加载所有数据
      await Promise.all([
        loadVolumeById(volumeId),
        loadAllVolumes(),
        loadNovelInfo(),
        loadPromptTemplates()
      ]);
      
      // 3. 加载章节列表（依赖于卷数据）
      const chapters = await loadChapterList();
      
      // 4. 自动跳转到最新章节
      if (chapters && chapters.length > 0) {
        // 找到最新的章节（章节号最大的）
        const latestChapter = chapters.reduce((latest: any, current: any) => {
          return (current.chapterNumber || 0) > (latest.chapterNumber || 0) ? current : latest;
        });
        
        console.log('🔄 自动加载最新章节:', latestChapter.chapterNumber);
        await handleLoadChapter(latestChapter);
      } else {
        // 如果没有章节，设置为卷的起始章节号
        if (currentVolume?.chapterStart) {
          setChapterNumber(currentVolume.chapterStart);
        }
      }
      
      // 5. 页面加载完成
      setPageLoading(false);
      
    } catch (error: any) {
      console.error('页面初始化失败:', error);
      setPageLoadError(error.message || '页面加载失败');
      setPageLoading(false);
    }
  };

  // 加载所有卷列表
  const loadAllVolumes = async () => {
    if (!novelId) return;
    
    try {
      const volumes = await novelVolumeService.getVolumesByNovelId(novelId);
      // 按卷号排序
      const sortedVolumes = volumes.sort((a: NovelVolume, b: NovelVolume) => a.volumeNumber - b.volumeNumber);
      setAllVolumes(sortedVolumes);
    } catch (error: any) {
      console.error('加载卷列表失败:', error);
    }
  };

  // 加载小说信息
  const loadNovelInfo = async () => {
    if (!novelId) return;
    
    try {
      const response = await api.get(`/novels/${novelId}`);
      setNovelInfo(response.data);
    } catch (error) {
      console.error('加载小说信息失败:', error);
    }
  };

  // 加载章节列表
  const loadChapterList = async () => {
    if (!currentVolume || !novelId) return [];
    
    setChapterListLoading(true);
    try {
      // 获取当前卷范围内的所有章节
      const response = await api.get(`/chapters/novel/${novelId}?summary=true`);
      const chapters = response.data || response || [];
      
      // 筛选出当前卷的章节
      const volumeChapters = chapters.filter((ch: any) => {
        const chNum = ch.chapterNumber || 0;
        return chNum >= currentVolume.chapterStart && chNum <= currentVolume.chapterEnd;
      });
      
      // 按章节号排序
      const sortedChapters = volumeChapters.sort((a: any, b: any) => {
        return (a.chapterNumber || 0) - (b.chapterNumber || 0);
      });
      
      setChapterList(sortedChapters);
      return sortedChapters;
      
    } catch (error) {
      console.error('加载章节列表失败:', error);
      return [];
    } finally {
      setChapterListLoading(false);
    }
  };

  // 加载小说大纲
  const loadNovelOutline = async () => {
    if (!novelId) return;
    try {
      console.log('开始加载小说大纲, novelId:', novelId);
      const response = await api.get(`/novels/${novelId}`);
      
      // 处理数据：api.get 可能直接返回数据，也可能在 response.data 中
      const data = response.data || response;
      
      console.log('=== 响应数据 ===', data);
      console.log('=== outline字段 ===', data?.outline);
      console.log('outline类型:', typeof data?.outline);
      console.log('outline长度:', data?.outline?.length);
      
      if (data && data.outline && typeof data.outline === 'string' && data.outline.trim().length > 0) {
        console.log('✅ 大纲加载成功，长度:', data.outline.length);
        setEditingOutline(data.outline);
        message.success('大纲加载成功');
      } else {
        console.log('❌ 大纲为空');
        setEditingOutline('暂无大纲，请先在大纲页面生成');
        message.warning('暂无大纲内容');
      }
    } catch (error: any) {
      console.error('加载小说大纲失败:', error);
      message.error('加载小说大纲失败');
      setEditingOutline('加载失败，请重试');
    }
  };

  // 保存小说大纲
  const handleSaveNovelOutline = async () => {
    if (!novelId) return;
    setOutlineLoading(true);
    try {
      console.log('保存小说大纲:', { novelId, outline: editingOutline.substring(0, 100) + '...' });
      await api.put(`/novels/${novelId}`, {
        outline: editingOutline
      });
      message.success('小说大纲已保存');
      setOutlineDrawerVisible(false);
    } catch (error: any) {
      console.error('保存小说大纲失败:', error);
      message.error('保存小说大纲失败');
    } finally {
      setOutlineLoading(false);
    }
  };

  // AI优化小说大纲（流式）
  const handleAIOptimizeNovelOutline = async () => {
    if (!novelId || !editingOutline) {
      message.warning('请先加载大纲内容');
      return;
    }

    // 检查AI配置
    if (!checkAIConfig()) {
      message.error(AI_CONFIG_ERROR_MESSAGE);
      return;
    }

    // 弹窗输入建议
    const suggestion = await new Promise<string | null>((resolve) => {
      let inputValue = '';
      Modal.confirm({
        title: '📝 AI优化大纲',
        width: 600,
        content: (
          <div style={{ marginTop: '16px' }}>
            <div style={{ marginBottom: '12px', color: '#64748b', fontSize: '14px' }}>
              请输入您对大纲的优化建议，AI将根据您的建议进行优化：
            </div>
            <Input.TextArea
              placeholder="例如：增强主角的性格塑造，加入更多的悬念元素..."
              rows={4}
              onChange={(e) => { inputValue = e.target.value; }}
              style={{ fontSize: '14px' }}
            />
          </div>
        ),
        okText: '开始优化',
        cancelText: '取消',
        onOk: () => resolve(inputValue || null),
        onCancel: () => resolve(null)
      });
    });

    if (!suggestion) return;

    setOutlineLoading(true);
    const originalOutline = editingOutline; // 保存原始内容
    setEditingOutline(''); // 清空原内容，准备接收流式输出
    
    try {
      message.loading({ content: 'AI正在优化大纲...', key: 'optimizing', duration: 0 });
      
      const token = localStorage.getItem('token');
      const requestBody = withAIConfig({
        novelId: parseInt(novelId),
        currentOutline: originalOutline, // 使用原始内容
        suggestion
      });
      
      const response = await fetch(`/api/outline/optimize-stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { 'Authorization': `Bearer ${token}` } : {})
        },
        body: JSON.stringify(requestBody),
      });

      if (!response.ok || !response.body) {
        throw new Error('网络请求失败');
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let streamedContent = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const events = buffer.split('\n\n');
        buffer = events.pop() || '';

        for (const event of events) {
          if (!event.trim()) continue;

          const lines = event.split('\n');
          let eventName = '';
          let data = '';

          for (const line of lines) {
            if (line.startsWith('event:')) {
              eventName = line.substring(6).trim();
            } else if (line.startsWith('data:')) {
              data = line.substring(5).trim();
            }
          }

          if (eventName === 'chunk') {
            streamedContent += data;
            setEditingOutline(streamedContent);
          } else if (eventName === 'done') {
            message.destroy('optimizing');
            message.success('大纲优化完成');
          } else if (eventName === 'error') {
            throw new Error(data);
          }
        }
      }
    } catch (error: any) {
      message.destroy('optimizing');
      console.error('AI优化大纲失败:', error);
      message.error(error.message || 'AI优化大纲失败');
      // 失败时恢复原内容
      setEditingOutline(originalOutline);
    } finally {
      setOutlineLoading(false);
    }
  };

  // 保存卷大纲
  const handleSaveVolumeOutline = async () => {
    if (!currentVolume) return;
    setOutlineLoading(true);
    try {
      await api.put(`/volumes/${currentVolume.id}`, {
        contentOutline: editingVolumeOutline
      });
      message.success('卷大纲已保存');
      setVolumeOutlineDrawerVisible(false);
      // 更新当前卷信息
      const updated = { ...currentVolume, contentOutline: editingVolumeOutline };
      setCurrentVolume(updated);
    } catch (error) {
      console.error('保存卷大纲失败:', error);
      message.error('保存卷大纲失败');
    } finally {
      setOutlineLoading(false);
    }
  };

  // AI优化卷大纲（流式）
  const handleAIOptimizeVolumeOutline = async () => {
    if (!currentVolume || !editingVolumeOutline) {
      message.warning('请先加载卷大纲内容');
      return;
    }

    // 检查AI配置
    if (!checkAIConfig()) {
      message.error(AI_CONFIG_ERROR_MESSAGE);
      return;
    }

    // 弹窗输入建议
    const suggestion = await new Promise<string | null>((resolve) => {
      let inputValue = '';
      Modal.confirm({
        title: `📖 AI优化第${currentVolume.volumeNumber}卷大纲`,
        width: 600,
        content: (
          <div style={{ marginTop: '16px' }}>
            <div style={{ marginBottom: '12px', color: '#64748b', fontSize: '14px' }}>
              请输入您对本卷大纲的优化建议，AI将根据您的建议进行优化：
            </div>
            <Input.TextArea
              placeholder="例如：加强主角与反派的对抗，增加情感冲突..."
              rows={4}
              onChange={(e) => { inputValue = e.target.value; }}
              style={{ fontSize: '14px' }}
            />
            <div style={{ marginTop: '12px', padding: '8px 12px', background: '#f1f5f9', borderRadius: '6px', fontSize: '13px', color: '#64748b' }}>
              <div><strong>卷信息：</strong>第{currentVolume.volumeNumber}卷</div>
              <div><strong>章节范围：</strong>第{currentVolume.chapterStart}-{currentVolume.chapterEnd}章</div>
            </div>
          </div>
        ),
        okText: '开始优化',
        cancelText: '取消',
        onOk: () => resolve(inputValue || null),
        onCancel: () => resolve(null)
      });
    });

    if (!suggestion) return;

    setOutlineLoading(true);
    const originalOutline = editingVolumeOutline; // 保存原始内容
    setEditingVolumeOutline(''); // 清空原内容，准备接收流式输出
    
    try {
      message.loading({ content: 'AI正在优化卷大纲...', key: 'optimizing-volume', duration: 0 });
      
      const token = localStorage.getItem('token');
      const requestBody = withAIConfig({
        currentOutline: originalOutline,
        suggestion,
        volumeInfo: {
          volumeNumber: currentVolume.volumeNumber,
          chapterStart: currentVolume.chapterStart,
          chapterEnd: currentVolume.chapterEnd,
          description: currentVolume.description
        }
      });
      
      const response = await fetch(`/api/volumes/${currentVolume.id}/optimize-outline-stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { 'Authorization': `Bearer ${token}` } : {})
        },
        body: JSON.stringify(requestBody),
      });

      if (!response.ok || !response.body) {
        throw new Error('网络请求失败');
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let streamedContent = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const events = buffer.split('\n\n');
        buffer = events.pop() || '';

        for (const event of events) {
          if (!event.trim()) continue;

          const lines = event.split('\n');
          let eventName = '';
          let data = '';

          for (const line of lines) {
            if (line.startsWith('event:')) {
              eventName = line.substring(6).trim();
            } else if (line.startsWith('data:')) {
              data = line.substring(5).trim();
            }
          }

          if (eventName === 'chunk') {
            streamedContent += data;
            setEditingVolumeOutline(streamedContent);
          } else if (eventName === 'done') {
            message.destroy('optimizing-volume');
            message.success('卷大纲优化完成');
            
            // 更新currentVolume的contentOutline
            if (currentVolume) {
              setCurrentVolume({
                ...currentVolume,
                contentOutline: streamedContent
              });
            }
          } else if (eventName === 'error') {
            throw new Error(data);
          }
        }
      }
    } catch (error: any) {
      message.destroy('optimizing-volume');
      console.error('AI优化卷大纲失败:', error);
      message.error(error.message || 'AI优化卷大纲失败');
      // 失败时恢复原内容
      setEditingVolumeOutline(originalOutline);
    } finally {
      setOutlineLoading(false);
    }
  };

  // 新建章节并概要
  const handleCreateNewChapter = async () => {
    try {
      if (!chapterId) {
        message.warning('请先完成当前章节的写作');
        return;
      }

      if (!currentContent || currentContent.trim().length < 100) {
        message.warning('章节内容太少，无法新建');
        return;
      }

      setLoading(true);
      message.loading({ content: '正在概要章节内容，请稍候...', key: 'summarizing', duration: 0 });

      // 检查AI配置
      if (!checkAIConfig()) {
        message.warning('未配置AI，将使用后端默认配置概要章节');
      }

      // 调用概要接口（同步等待完成），传递AI配置
      const response = await fetch(`/api/chapters/${chapterId}/summarize`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(localStorage.getItem('token') ? { 'Authorization': `Bearer ${localStorage.getItem('token')}` } : {})
        },
        body: JSON.stringify(withAIConfig({}))
      });

      if (!response.ok) {
        throw new Error('概要章节失败');
      }

      await response.json();
      
      message.destroy('summarizing');
      
      // 显示结果
      message.success('章节处理完成！');

      // 刷新章节列表
      await loadChapterList();

      // 清空当前编辑器，准备下一章
      const nextChapterNumber = (chapterNumber || 0) + 1;
      
      setCurrentContent('');
      setChapterTitle('');
      setChapterId(null);
      setLastSavedContent('');
      setLastSaveTime('');
      
      // 立即创建新章节的数据库记录（避免页面消失）
      try {
        const newChapterPayload = {
          title: `第${nextChapterNumber}章`,
          content: '', // 空内容
          chapterNumber: nextChapterNumber,
          novelId: novelId ? parseInt(novelId) : undefined
        };
        
        console.log('🔄 正在创建新章节到数据库:', newChapterPayload);
        
        const response = await api.post('/chapters', newChapterPayload);
        const newChapter = response?.data || response;
        
        console.log('📝 创建章节API响应:', newChapter);
        
        if (newChapter?.id) {
          console.log('✅ 新章节已创建到数据库:', {
            id: newChapter.id,
            title: newChapter.title,
            chapterNumber: newChapter.chapterNumber
          });
          
          // 设置新章节的状态
          setChapterNumber(nextChapterNumber);
          setChapterId(String(newChapter.id));
          
          // 重新加载章节列表，确保新章节显示在列表中
          console.log('🔄 重新加载章节列表...');
          await loadChapterList();
          
          message.success(`✅ 第${nextChapterNumber}章已创建，可以开始写作了！`);
        } else {
          console.error('❌ 创建章节失败：API响应格式异常', response);
          throw new Error('创建章节失败：未返回章节ID');
        }
      } catch (createError: any) {
        console.error('❌ 创建新章节失败:', {
          error: createError,
          message: createError?.message,
          response: createError?.response?.data
        });
        
        // 如果创建失败，回退到原来的逻辑
        setTimeout(() => {
          setChapterNumber(nextChapterNumber);
          message.warning(`⚠️ 章节创建可能失败，但可以开始写第${nextChapterNumber}章（写作后会自动保存）`);
        }, 100);
      }
    } catch (error: any) {
      message.destroy('summarizing');
      console.error('新建章节失败:', error);
      message.error(error.message || '新建章节失败');
    } finally {
      setLoading(false);
    }
  };

  // 检查是否需要切换到下一卷
  const checkAndSwitchToNextVolume = async () => {
    if (!currentVolume || !allVolumes.length) return;
    
    // 找到下一卷
    const sortedVolumes = [...allVolumes].sort((a, b) => a.volumeNumber - b.volumeNumber);
    const currentIndex = sortedVolumes.findIndex(v => v.id === currentVolume.id);
    
    if (currentIndex >= 0 && currentIndex < sortedVolumes.length - 1) {
      const nextVolume = sortedVolumes[currentIndex + 1];
      
      // 提示用户
      message.success({
        content: `当前卷已完成!自动进入第${nextVolume.volumeNumber}卷`,
        duration: 3
      });
      
      // 跳转到下一卷（使用新的写作工作室）
      setTimeout(() => {
        navigate(`/novels/${novelId}/writing-studio`, {
          state: {
            initialVolumeId: nextVolume.id,
            sessionData: null
          }
        });
      }, 1500);
    } else {
      message.success({
        content: '🎉 恭喜!整部小说已全部完成!',
        duration: 5
      });
    }
  };

  // 批量写作处理函数
  const handleBatchWriting = async () => {
    if (!chapterNumber) {
      message.warning('请先填写章节编号');
      return;
    }

    // 检查当前状态
    console.log('批量写作开始前状态检查:', {
      chapterNumber,
      chapterId,
      hasContent: !!currentContent,
      contentLength: currentContent?.length || 0
    });

    // 显示确认对话框
    Modal.confirm({
      title: '批量生成章节',
      content: (
        <div>
          <p>将从第{chapterNumber}章开始，连续生成10章内容。</p>
          <p style={{ color: '#faad14' }}>⚠️ 此过程可能需要较长时间，请确保网络连接稳定。</p>
          <p>您可以随时点击"取消"按钮中止生成。</p>
        </div>
      ),
      okText: '开始生成',
      cancelText: '取消',
      onOk: () => {
        console.log('✅ 用户确认开始批量生成');
        startBatchWriting();
      },
      onCancel: () => {
        console.log('❌ 用户取消批量生成');
        // 明确处理取消操作，不执行任何写作流程
      }
    });
  };

  // 开始批量写作
  const startBatchWriting = async () => {
    setBatchWriting(true);
    setBatchCancelled(false);
    setBatchProgress({ current: 0, total: 10 });
    setBatchModalVisible(true);

    let successCount = 0;
    let failedChapters: number[] = [];
    
    // 保存起始章节号（重要：不要用chapterNumber，它会被handleCreateNewChapter改变）
    const startChapterNumber = chapterNumber || 0;

    try {
      for (let i = 0; i < 10; i++) {
        // 检查是否被取消
        if (batchCancelled) {
          message.info(`批量生成已取消，已成功生成${successCount}章`);
          break;
        }

        // 使用起始章节号 + i 计算当前章节号
        const currentChapterNum = startChapterNumber + i;
        setBatchProgress({ current: i + 1, total: 10 });

        try {
          console.log(`\n=== 开始生成第${currentChapterNum}章 ===`);
          console.log('当前状态:', { chapterId, hasContent: !!currentContent, contentLength: currentContent?.length || 0 });

          // 步骤1: 点击AI写作按钮
          console.log(`步骤1: 开始AI写作第${currentChapterNum}章`);
          await simulateAIWriting(currentChapterNum);

          // 步骤2: 等待写作完成
          console.log(`步骤2: 等待第${currentChapterNum}章写作完成...`);
          await waitForWritingComplete();

          // 步骤3: 写作完成
          console.log(`第${currentChapterNum}章写作完成，内容长度: ${currentContent?.length || 0}`);
          successCount++;

          // 步骤4: 如果不是最后一章，点击"新建章节"按钮
          if (i < 9 && !batchCancelled) {
            console.log(`步骤3: 点击"新建章节"按钮，准备第${currentChapterNum + 1}章...`);
            
            // 重要：在点击新建章节按钮之前，从ref读取当前章节号（最可靠）
            const currentChapterBeforeCreate = chapterNumberRef.current;
            console.log(`当前章节号(ref): ${currentChapterBeforeCreate}, 期望新章节号: ${(currentChapterBeforeCreate || 0) + 1}`);
            
            // 找到"新建章节"按钮并点击
            await simulateClickNewChapterButton();
            
            // 等待概括和页面切换完成（传入期望的新章节号）
            const expectedNewChapterNumber = (currentChapterBeforeCreate || 0) + 1;
            console.log(`步骤4: 等待第${currentChapterNum}章概括完成并切换到第${expectedNewChapterNumber}章...`);
            await waitForNewChapterReady(expectedNewChapterNumber);
            
            console.log(`第${currentChapterNum}章概括完成，已切换到第${expectedNewChapterNumber}章页面`);
            
            // 重置流式完成标志，准备下一章写作
            streamingCompleteRef.current = false;
            console.log('✅ 已重置流式完成标志，准备开始下一章');
          }

        } catch (chapterError: any) {
          console.error(`第${currentChapterNum}章生成失败:`, chapterError);
          failedChapters.push(currentChapterNum);
          
          // 询问用户是否继续
          const shouldContinue = await new Promise<boolean>((resolve) => {
            Modal.confirm({
              title: '章节生成失败',
              content: (
                <div>
                  <p>第{currentChapterNum}章生成失败：{chapterError.message}</p>
                  <p>是否继续生成剩余章节？</p>
                </div>
              ),
              okText: '继续生成',
              cancelText: '停止生成',
              onOk: () => resolve(true),
              onCancel: () => resolve(false)
            });
          });

          if (!shouldContinue) {
            setBatchCancelled(true);
            break;
          }
        }
      }

      // 显示最终结果
      if (!batchCancelled) {
        if (failedChapters.length === 0) {
          message.success('🎉 批量生成完成！已成功生成10章内容');
        } else {
          message.warning(
            `批量生成完成，成功生成${successCount}章，失败${failedChapters.length}章（第${failedChapters.join('、')}章）`
          );
        }
      }
    } catch (error: any) {
      console.error('批量写作失败:', error);
      message.error(error.message || '批量写作过程中出现严重错误');
    } finally {
      setBatchWriting(false);
      setBatchModalVisible(false);
      setBatchProgress({ current: 0, total: 0 });
    }
  };

  // 模拟AI写作
  const simulateAIWriting = async (_chapterNum: number) => {
    return new Promise<void>((resolve, reject) => {
      try {
        // 触发AI写作按钮
        const aiBtn = document.querySelector('[data-ai-write-trigger]') as HTMLButtonElement;
        if (aiBtn) {
          console.log('点击AI写作按钮');
          aiBtn.click();
          
          // 等待一下确保AI写作已经开始（loading变为true）
          setTimeout(() => {
            console.log('AI写作已触发');
            resolve();
          }, 1000);
        } else {
          reject(new Error('找不到AI写作触发器'));
        }
      } catch (error) {
        reject(error);
      }
    });
  };

  // 等待写作完成（通过检查流式接口的complete事件）
  const waitForWritingComplete = async () => {
    return new Promise<void>((resolve) => {
      const checkComplete = () => {
        // 直接检查 streamingCompleteRef，不依赖 hasStarted
        if (streamingCompleteRef.current) {
          // 收到complete事件，再等待2秒确保自动保存完成
          console.log('✅ 检测到流式完成标志，写作已完成，等待自动保存...');
          setTimeout(() => {
            console.log('✅ 自动保存应该已完成');
            resolve();
          }, 2000);
          return; // 重要：立即返回，不再继续检查
        }
        
        // 如果被取消，直接返回
        if (batchCancelled) {
          console.log('批量写作被取消');
          resolve();
          return;
        }
        
        // 继续等待（不设置超时，AI生成本来就慢）
        setTimeout(checkComplete, 500);
      };
      
      // 延迟开始检查，给AI写作一点时间启动
      setTimeout(checkComplete, 500);
    });
  };

  // 模拟点击"新建章节"按钮（等待按钮可用）
  const simulateClickNewChapterButton = async () => {
    return new Promise<void>((resolve, reject) => {
      const startTime = Date.now();
      const timeout = 2 * 60 * 1000; // 2分钟超时（等待自动保存完成）
      
      const tryClick = () => {
        const elapsed = Date.now() - startTime;
        
        try {
          // 找到"新建章节"按钮
          const buttons = Array.from(document.querySelectorAll('button'));
          const newChapterBtn = buttons.find(btn => btn.textContent?.includes('新建章节')) as HTMLButtonElement;
          
          if (!newChapterBtn) {
            reject(new Error('找不到"新建章节"按钮'));
            return;
          }
          
          // 检查按钮是否可用（disabled={loading || !chapterId || !currentContent}）
          if (!newChapterBtn.disabled) {
            console.log('找到"新建章节"按钮，按钮可用，准备点击');
            newChapterBtn.click();
            
            // 等待一下确保点击已处理
            setTimeout(() => {
              console.log('"新建章节"按钮已点击');
              resolve();
            }, 500);
          } else {
            // 按钮还被禁用，继续等待
            console.log('新建章节按钮被禁用，等待按钮可用...', { 
              loading, 
              chapterId: chapterIdRef.current, 
              hasContent: !!currentContent,
              elapsed 
            });
            
            if (elapsed > timeout) {
              reject(new Error('等待"新建章节"按钮可用超时（可能自动保存失败）'));
            } else {
              setTimeout(tryClick, 500);
            }
          }
        } catch (error: any) {
          reject(error);
        }
      };
      
      // 延迟开始，给页面一点时间渲染
      setTimeout(tryClick, 500);
    });
  };

  // 等待新章节页面准备好（概括完成，页面切换完成）
  const waitForNewChapterReady = async (expectedChapterNumber: number) => {
    return new Promise<void>((resolve) => {
      console.log(`waitForNewChapterReady: 等待章节号变为 ${expectedChapterNumber}`);

      const checkReady = () => {
        const currentChapterNum = chapterNumberRef.current;
        console.log(`检查新章节状态: loading=${loading}, 当前章节号(ref)=${currentChapterNum}, 期望章节号=${expectedChapterNumber}`);
        
        // 检查条件：loading结束 且 章节号已变为期望值
        if (!loading && currentChapterNum === expectedChapterNumber) {
          console.log(`✅ 新章节页面已准备好，章节号已更新为: ${currentChapterNum}`);
          resolve();
          return;
        }
        
        if (batchCancelled) {
          console.log('批量写作被取消');
          resolve();
          return;
        }
        
        // 继续等待（不设置超时，概括也需要时间）
        setTimeout(checkReady, 500);
      };
      
      // 延迟开始检查，确保概括过程已经开始
      setTimeout(checkReady, 1000);
    });
  };

  // 取消批量写作
  const cancelBatchWriting = () => {
    setBatchCancelled(true);
    setBatchWriting(false);
    setBatchModalVisible(false);
    message.info('正在取消批量生成...');
  };


  // 判断当前章节是否是最新章节（只有最新章节才能新建下一章）
  const isCurrentChapterLatest = () => {
    if (!chapterNumber || chapterList.length === 0) {
      return false;
    }
    
    // 找到章节列表中最大的章节号
    const maxChapterNumber = Math.max(...chapterList.map(ch => ch.chapterNumber || 0));
    
    // 当前章节号必须是最大的
    return chapterNumber === maxChapterNumber;
  };

  // 加载章节内容
  const handleLoadChapter = async (chapter: any) => {
    try {
      setChapterNumber(chapter.chapterNumber);
      setChapterTitle(chapter.title || '');
      setCurrentContent(chapter.content || '');
      setChapterId(String(chapter.id));
      setLastSavedContent(chapter.content || ''); // 同步最后保存的内容
      message.success('章节内容已加载');
    } catch (error) {
      console.error('加载章节失败:', error);
      message.error('加载章节内容失败');
    }
  };

  // 请求AI指导
  const handleRequestGuidance = async (values: any) => {
    if (!currentVolume) return;
    
    setGuidanceLoading(true);
    try {
      const request: VolumeGuidanceRequest = {
        currentContent: currentContent,
        userInput: values.userInput
      };
      
      const guidance = await novelVolumeService.generateNextStepGuidance(currentVolume.id, request);
      setAiGuidance(guidance);
      
      // 添加到历史记录
      const historyItem = {
        timestamp: new Date().toLocaleString(),
        userInput: values.userInput,
        guidance: guidance,
        contentSnapshot: currentContent.substring(Math.max(0, currentContent.length - 200))
      };
      setGuidanceHistory(prev => [historyItem, ...prev]);
      
      setGuidanceDrawerVisible(false);
      guidanceForm.resetFields();
      message.success('AI指导生成成功！');
    } catch (error: any) {
      message.error(error.response?.data?.message || 'AI指导生成失败');
    } finally {
      setGuidanceLoading(false);
    }
  };

  // 加载历史章节
  const loadHistoricalChapters = async () => {
    if (!novelId) return;
    
    try {
      setChaptersLoading(true);
      // 获取小说的所有章节
      const response = await api.get(`/chapters/novel/${novelId}?summary=true`);
      const chapters = response.data || response || [];
      
      // 按章节号排序
      const sortedChapters = chapters.sort((a: any, b: any) => {
        return (a.chapterNumber || 0) - (b.chapterNumber || 0);
      });
      
      setHistoricalChapters(sortedChapters);
    } catch (error) {
      console.error('加载历史章节失败:', error);
      message.error('加载章节列表失败');
    } finally {
      setChaptersLoading(false);
    }
  };

  return (
    <div className="volume-writing-studio" style={{ height: '100vh', display: 'flex', flexDirection: 'column' }}>
      {/* 顶部工具栏 - 精致设计 */}
      <div style={{
        padding: '16px 32px',
        background: 'linear-gradient(180deg, #ffffff 0%, #fafbfc 100%)',
        borderBottom: '1px solid #e2e8f0',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexShrink: 0,
        boxShadow: '0 1px 3px rgba(0, 0, 0, 0.04)'
      }}>
        <Space size="large">
          <Button 
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate('/novels')}
            style={{
              border: '1px solid #e2e8f0',
              borderRadius: '8px',
              fontSize: '14px',
              color: '#64748b',
              background: '#ffffff',
              height: '36px',
              padding: '0 16px',
              transition: 'all 0.2s'
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.borderColor = '#cbd5e1';
              e.currentTarget.style.color = '#334155';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.borderColor = '#e2e8f0';
              e.currentTarget.style.color = '#64748b';
            }}
          >
            返回
          </Button>
          <Divider type="vertical" style={{ height: '28px', margin: 0, borderColor: '#e2e8f0' }} />
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div style={{
              width: '40px',
              height: '40px',
              borderRadius: '10px',
              background: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: '0 2px 8px rgba(59, 130, 246, 0.25)'
            }}>
              <BookOutlined style={{ fontSize: '20px', color: '#ffffff' }} />
            </div>
            <div>
              <div style={{ 
                fontSize: '16px', 
                fontWeight: 600, 
                color: '#0f172a',
                lineHeight: 1.2
              }}>
                {novelInfo?.title || '创作中...'}
              </div>
              <div style={{ 
                fontSize: '12px', 
                color: '#94a3b8',
                marginTop: '2px'
              }}>
                第{currentVolume?.volumeNumber || '?'}卷 · 第{chapterNumber || '?'}章
              </div>
            </div>
          </div>
        </Space>
        <Space size="middle">
          <Button 
            icon={<BookOutlined />}
            onClick={async () => {
              await loadNovelOutline();
              setOutlineDrawerVisible(true);
            }}
            style={{ 
              borderRadius: '8px',
              border: '1px solid #e2e8f0',
              height: '36px',
              background: '#ffffff',
              color: '#475569',
              fontWeight: 500
            }}
          >
            小说大纲
          </Button>
          <Button 
            icon={<FileTextOutlined />}
            onClick={() => {
              setEditingVolumeOutline(currentVolume?.contentOutline || '');
              setVolumeOutlineDrawerVisible(true);
            }}
            style={{ 
              borderRadius: '8px',
              border: '1px solid #e2e8f0',
              height: '36px',
              background: '#ffffff',
              color: '#475569',
              fontWeight: 500
            }}
          >
            卷大纲
          </Button>
          <Button 
            icon={<HistoryOutlined />}
            onClick={() => {
              loadHistoricalChapters();
              setHistoryDrawerVisible(true);
            }}
            style={{ 
              borderRadius: '8px',
              border: '1px solid #e2e8f0',
              height: '36px',
              background: '#ffffff',
              color: '#475569',
              fontWeight: 500
            }}
          >
            历史
          </Button>
          <Button 
            type="primary" 
            icon={<RobotOutlined />}
            onClick={() => setGuidanceDrawerVisible(true)}
            style={{
              borderRadius: '8px',
              background: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)',
              border: 'none',
              height: '36px',
              boxShadow: '0 2px 8px rgba(59, 130, 246, 0.25)',
              fontWeight: 500
            }}
          >
            AI指导
          </Button>
        </Space>
      </div>

      {/* 页面加载中 */}
      {pageLoading && (
        <div style={{ 
          display: 'flex', 
          flexDirection: 'column', 
          alignItems: 'center', 
          justifyContent: 'center',
          minHeight: '60vh',
          padding: '40px 20px' 
        }}>
          <Spin size="large" />
          <div style={{ marginTop: 16, color: '#666', fontSize: '16px' }}>正在加载写作工作室...</div>
          <div style={{ marginTop: 8, color: '#999', fontSize: '14px' }}>请稍候，正在准备您的创作环境</div>
        </div>
      )}

      {/* 页面加载失败 */}
      {!pageLoading && pageLoadError && (
        <Card style={{ marginTop: 12 }}>
          <Alert
            type="error"
            showIcon
            message="页面加载失败"
            description={
              <div>
                <p>{pageLoadError}</p>
                <div style={{ marginTop: 12 }}>
                  <Button onClick={() => initializePage()} type="primary" style={{ marginRight: 8 }}>
                    重新加载
                  </Button>
                  <Button onClick={() => navigate(-1)}>
                    返回上一页
                  </Button>
                </div>
              </div>
            }
          />
        </Card>
      )}

      {/* 主体内容区 - 左右布局 */}
      {!pageLoading && !pageLoadError && currentVolume && (
        <div style={{ 
          flex: 1, 
          display: 'flex', 
          overflow: 'hidden',
          background: '#f8fafc'
        }}>
          {/* 左侧：章节列表 */}
          <div style={{
            width: '300px',
            background: '#ffffff',
            borderRight: '1px solid #e2e8f0',
            display: 'flex',
            flexDirection: 'column',
            flexShrink: 0,
            boxShadow: '1px 0 3px rgba(0, 0, 0, 0.02)'
          }}>
            {/* 章节列表头部 */}
            <div style={{
              padding: '24px 20px',
              borderBottom: '1px solid #e2e8f0',
              background: 'linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%)'
            }}>
              <div style={{
                fontSize: '16px',
                fontWeight: 600,
                color: '#0f172a',
                marginBottom: '8px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between'
              }}>
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '10px',
                    cursor: 'pointer',
                    padding: '4px 8px',
                    borderRadius: '8px',
                    transition: 'all 0.2s',
                    background: chapterDirHovered ? 'rgba(59, 130, 246, 0.05)' : 'transparent'
                  }}
                  onMouseEnter={() => setChapterDirHovered(true)}
                  onMouseLeave={() => setChapterDirHovered(false)}
                  onClick={() => {
                    if (!loading && chapterId && currentContent && isCurrentChapterLatest()) {
                      handleCreateNewChapter();
                    } else {
                      message.warning('请先完成当前章节的创作');
                    }
                  }}
                >
                  <div style={{
                    width: '32px',
                    height: '32px',
                    borderRadius: '8px',
                    background: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    boxShadow: '0 2px 8px rgba(59, 130, 246, 0.25)'
                  }}>
                    <FileTextOutlined style={{ fontSize: '16px', color: '#ffffff' }} />
                  </div>
                  <span>章节目录</span>
                  {chapterDirHovered && (
                    <span style={{
                      fontSize: '18px',
                      color: '#3b82f6',
                      marginLeft: '4px',
                      animation: 'fadeIn 0.2s ease-in'
                    }}>
                      ➕
                    </span>
                  )}
                </div>

                {/* 新建章节按钮 */}
                <Button
                  type="primary"
                  icon={<PlusOutlined />}
                  size="small"
                  disabled={loading || !chapterId || !currentContent || !isCurrentChapterLatest()}
                  onClick={handleCreateNewChapter}
                  style={{
                    borderRadius: '6px',
                    height: '32px',
                    background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
                    border: 'none',
                    fontWeight: 600,
                    fontSize: '12px',
                    boxShadow: '0 2px 8px rgba(16, 185, 129, 0.4)'
                  }}
                >
                  新建章节
                </Button>
              </div>
            </div>

            {/* 章节列表内容 */}
            <div style={{ 
              flex: 1, 
              overflowY: 'auto',
              padding: '12px',
              background: '#fafbfc'
            }}>
              {/* 已有章节列表 */}
              <Spin spinning={chapterListLoading}>
                {chapterList.length > 0 && chapterList.map((chapter) => (
                  <div
                    key={chapter.id}
                    onClick={() => handleLoadChapter(chapter)}
                    style={{
                      padding: '14px 12px',
                      marginBottom: '6px',
                      borderRadius: '8px',
                      cursor: 'pointer',
                      background: chapterId === String(chapter.id) ? 'linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%)' : '#ffffff',
                      border: chapterId === String(chapter.id) ? '1px solid #3b82f6' : '1px solid #e2e8f0',
                      transition: 'all 0.2s',
                      position: 'relative'
                    }}
                    onMouseEnter={(e) => {
                      if (chapterId !== String(chapter.id)) {
                        e.currentTarget.style.background = '#f8fafc';
                        e.currentTarget.style.borderColor = '#cbd5e1';
                      }
                    }}
                    onMouseLeave={(e) => {
                      if (chapterId !== String(chapter.id)) {
                        e.currentTarget.style.background = '#ffffff';
                        e.currentTarget.style.borderColor = '#e2e8f0';
                      }
                    }}
                  >
                    <div style={{ 
                      fontSize: '13px', 
                      fontWeight: chapterId === String(chapter.id) ? 600 : 500,
                      color: chapterId === String(chapter.id) ? '#1e40af' : '#334155',
                      marginBottom: '6px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between'
                    }}>
                      <span>第{chapter.chapterNumber}章</span>
                      {chapter.status === 'PUBLISHED' && (
                        <CheckOutlined style={{ fontSize: '12px', color: '#10b981' }} />
                      )}
                      {chapter.status === 'DRAFT' && (
                        <Badge status="processing" />
                      )}
                    </div>
                    <div style={{ 
                      fontSize: '11px', 
                      color: '#64748b',
                      marginBottom: '6px',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap'
                    }}>
                      {chapter.title || '未命名'}
                    </div>
                    <div style={{
                      fontSize: '10px',
                      color: '#94a3b8'
                    }}>
                      {chapter.wordCount || 0} 字
                    </div>
                  </div>
                ))}
                
                {/* 当前创作中的章节(如果不在列表中) */}
                {chapterNumber && !chapterList.find(ch => ch.chapterNumber === chapterNumber) && (
                  <div
                    style={{
                      padding: '14px 12px',
                      marginBottom: '6px',
                      borderRadius: '8px',
                      background: 'linear-gradient(135deg, #fef3c7 0%, #fde68a 100%)',
                      border: '1px solid #f59e0b',
                      boxShadow: '0 2px 8px rgba(245, 158, 11, 0.15)'
                    }}
                  >
                    <div style={{ 
                      fontSize: '13px', 
                      fontWeight: 600,
                      color: '#92400e',
                      marginBottom: '6px',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '6px'
                    }}>
                      <span style={{
                        display: 'inline-block',
                        width: '6px',
                        height: '6px',
                        background: '#f59e0b',
                        borderRadius: '50%',
                        animation: 'pulse 2s infinite'
                      }}></span>
                      第{chapterNumber}章
                    </div>
                    <div style={{ 
                      fontSize: '11px', 
                      color: '#78350f',
                      marginBottom: '6px'
                    }}>
                      {chapterTitle || '创作中...'}
                    </div>
                    <div style={{
                      fontSize: '10px',
                      color: '#a16207'
                    }}>
                      {wordCount} 字
                    </div>
                  </div>
                )}
              </Spin>
            </div>
          </div>

          {/* 右侧：编辑器 */}
          <div style={{ 
            flex: 1, 
            display: 'flex', 
            flexDirection: 'column',
            overflow: 'hidden',
            background: 'linear-gradient(180deg, #fafbfc 0%, #f8fafc 100%)'
          }}>
            {/* 编辑器头部 - 精致优化布局 */}
            <div style={{
              padding: '16px 32px',
              background: 'linear-gradient(135deg, #ffffff 0%, #fafbfc 100%)',
              borderBottom: '2px solid #e2e8f0',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: '16px',
              flexShrink: 0,
              boxShadow: '0 2px 8px rgba(0, 0, 0, 0.06)'
            }}>
              {/* 左侧：章节号 + 标题 */}
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flex: 1 }}>
                {/* 章节号 - 增强设计 */}
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  padding: '8px 14px',
                  background: 'linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%)',
                  borderRadius: '10px',
                  border: '2px solid #bfdbfe',
                  boxShadow: '0 3px 10px rgba(59, 130, 246, 0.18)',
                  height: '40px',
                  transition: 'all 0.3s ease'
                }}>
                  <span style={{ fontSize: '13px', color: '#1e40af', fontWeight: 700 }}>第</span>
                  <Input
                    style={{ 
                      width: 50,
                      textAlign: 'center',
                      fontWeight: 800,
                      fontSize: '15px',
                      border: 'none',
                      background: 'transparent',
                      color: '#1e40af',
                      padding: 0,
                      height: '24px'
                    }}
                    value={chapterNumber ?? ''}
                    onChange={(e) => setChapterNumber(Number(e.target.value) || null)}
                    bordered={false}
                  />
                  <span style={{ fontSize: '13px', color: '#1e40af', fontWeight: 700 }}>章</span>
                </div>
                
                {/* 章节标题 - 优化样式 */}
                  <Input
                  style={{ 
                    flex: 1,
                    maxWidth: 320,
                    height: '40px',
                    borderRadius: '10px',
                    border: '2px solid #e2e8f0',
                    fontSize: '14px',
                    fontWeight: 600,
                    transition: 'all 0.3s ease',
                    background: '#ffffff',
                    boxShadow: '0 2px 6px rgba(0, 0, 0, 0.04)'
                  }}
                    value={chapterTitle}
                    onChange={(e) => setChapterTitle(e.target.value)}
                  placeholder="输入章节标题..."
                  onFocus={(e) => {
                    e.currentTarget.style.borderColor = '#3b82f6';
                    e.currentTarget.style.boxShadow = '0 0 0 3px rgba(59, 130, 246, 0.12)';
                  }}
                  onBlur={(e) => {
                    e.currentTarget.style.borderColor = '#e2e8f0';
                    e.currentTarget.style.boxShadow = '0 2px 6px rgba(0, 0, 0, 0.04)';
                  }}
                />
                
                {/* AI写作按钮 - 超级醒目设计 */}
                <Button 
                  type="primary" 
                  icon={<ThunderboltOutlined style={{ fontSize: '18px' }} />} 
                  size="large"
                  disabled={loading || isStreaming}
                  loading={loading}
                  onClick={() => {
                    if (!chapterNumber) {
                      message.warning('请先填写章节编号');
                      return;
                    }
                    setAiDrawerVisible(true);
                  }}
                  style={{ 
                    borderRadius: '12px',
                    height: '48px',
                    padding: '0 28px',
                    background: 'linear-gradient(135deg, #a855f7 0%, #8b5cf6 50%, #7c3aed 100%)',
                    border: 'none',
                    fontWeight: 700,
                    fontSize: '15px',
                    boxShadow: '0 6px 20px rgba(139, 92, 246, 0.6)',
                    flexShrink: 0,
                    marginLeft: '16px',
                    transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
                    position: 'relative',
                    overflow: 'hidden'
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.transform = 'translateY(-3px) scale(1.02)';
                    e.currentTarget.style.boxShadow = '0 10px 30px rgba(139, 92, 246, 0.7)';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.transform = 'translateY(0) scale(1)';
                    e.currentTarget.style.boxShadow = '0 6px 20px rgba(139, 92, 246, 0.6)';
                  }}
                >
                  <span style={{ 
                    display: 'flex', 
                    alignItems: 'center', 
                    gap: '8px',
                    position: 'relative',
                    zIndex: 1
                  }}>
                    ✨ AI 智能写作
                  </span>
                </Button>

                {/* 一次生成10章按钮 - 优化设计 */}
                <Button 
                  type="default" 
                  icon={<RobotOutlined style={{ fontSize: '18px' }} />} 
                  size="large"
                  disabled={loading || isStreaming || batchWriting}
                  loading={batchWriting}
                  onClick={handleBatchWriting}
                  style={{ 
                    borderRadius: '12px',
                    height: '48px',
                    padding: '0 24px',
                    background: 'linear-gradient(135deg, #14b8a6 0%, #10b981 50%, #059669 100%)',
                    border: 'none',
                    color: 'white',
                    fontWeight: 700,
                    fontSize: '15px',
                    boxShadow: '0 6px 20px rgba(16, 185, 129, 0.5)',
                    flexShrink: 0,
                    marginLeft: '12px',
                    transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
                    position: 'relative',
                    overflow: 'hidden'
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.transform = 'translateY(-3px) scale(1.02)';
                    e.currentTarget.style.boxShadow = '0 10px 30px rgba(16, 185, 129, 0.6)';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.transform = 'translateY(0) scale(1)';
                    e.currentTarget.style.boxShadow = '0 6px 20px rgba(16, 185, 129, 0.5)';
                  }}
                >
                  <span style={{ 
                    display: 'flex', 
                    alignItems: 'center', 
                    gap: '8px',
                    position: 'relative',
                    zIndex: 1
                  }}>
                    🚀 批量生成 10 章
                  </span>
                </Button>
                
                {/* 自动保存状态提示 - 优化设计 */}
                {isSaving && (
                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                    padding: '8px 16px',
                    background: 'linear-gradient(135deg, #fef3c7 0%, #fde68a 100%)',
                    borderRadius: '10px',
                    fontSize: '13px',
                    color: '#92400e',
                    marginLeft: '12px',
                    fontWeight: 600,
                    border: '2px solid #fbbf24',
                    boxShadow: '0 2px 8px rgba(251, 191, 36, 0.2)'
                  }}>
                    <SaveOutlined style={{ fontSize: '13px' }} spin />
                    <span>保存中...</span>
              </div>
                )}
                
                {!isSaving && lastSavedContent && currentContent === lastSavedContent && (
                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                    padding: '8px 16px',
                    background: 'linear-gradient(135deg, #d1fae5 0%, #a7f3d0 100%)',
                    borderRadius: '10px',
                    fontSize: '13px',
                    color: '#065f46',
                    marginLeft: '12px',
                    fontWeight: 600,
                    border: '2px solid #86efac',
                    boxShadow: '0 2px 8px rgba(134, 239, 172, 0.3)'
                  }}>
                    <CheckOutlined style={{ fontSize: '13px' }} />
                    <span>已保存</span>
              </div>
                )}
                
                {/* 字数统计 - 精美显示 */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginLeft: '16px' }}>
                  <div style={{ 
                    padding: '8px 14px',
                    background: 'linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%)',
                    borderRadius: '10px',
                    border: '2px solid #cbd5e1',
                    fontSize: '12px',
                    color: '#475569',
                    height: '36px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '6px',
                    boxShadow: '0 2px 6px rgba(0, 0, 0, 0.08)',
                    fontWeight: 600
                  }}>
                    <Text strong style={{ color: '#1e293b', fontSize: '14px' }}>{wordCount.toLocaleString()}</Text> 字
              </div>
                  <div style={{ 
                    padding: '8px 14px',
                    background: wordCount >= 3000 
                      ? 'linear-gradient(135deg, #d1fae5 0%, #a7f3d0 100%)' 
                      : 'linear-gradient(135deg, #fef3c7 0%, #fde68a 100%)',
                    borderRadius: '10px',
                    border: `2px solid ${wordCount >= 3000 ? '#86efac' : '#fbbf24'}`,
                    fontSize: '13px',
                    color: wordCount >= 3000 ? '#166534' : '#92400e',
                    fontWeight: 700,
                    height: '36px',
                    display: 'flex',
                    alignItems: 'center',
                    boxShadow: wordCount >= 3000 
                      ? '0 2px 8px rgba(134, 239, 172, 0.3)' 
                      : '0 2px 8px rgba(251, 191, 36, 0.3)'
                  }}>
                    {wordCount >= 3000 ? '✅' : '📝'} {Math.min(100, Math.round((wordCount / 3000) * 100))}%
                  </div>
                </div>
              </div>
              
            </div>
              
            {/* 编辑器主体 - 升级设计 */}
            <div style={{ 
              flex: 1, 
              display: 'flex',
              flexDirection: 'column',
              background: '#ffffff',
              borderRadius: '16px',
              border: '2px solid #e2e8f0',
              overflow: 'hidden',
              margin: '24px 32px 32px 32px',
              boxShadow: '0 8px 24px rgba(0, 0, 0, 0.08), 0 2px 8px rgba(0, 0, 0, 0.04)'
            }}>
              {/* 格式工具栏 - 精美设计 */}
              <div style={{
                padding: '16px 24px',
                background: 'linear-gradient(135deg, #fafbfc 0%, #f1f5f9 100%)',
                borderBottom: '2px solid #e2e8f0',
                display: 'flex',
                alignItems: 'center',
                gap: '10px',
                flexWrap: 'wrap',
                boxShadow: '0 2px 8px rgba(0, 0, 0, 0.04)'
              }}>
                <div style={{ 
                  fontSize: '13px', 
                  color: '#475569', 
                  marginRight: '16px', 
                  fontWeight: '600',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px'
                }}>
                  <span style={{
                    fontSize: '16px',
                    display: 'inline-block',
                    animation: 'pulse 2s infinite'
                  }}>📝</span>
                  <span>格式工具</span>
                </div>
                
                <Button 
                  size="middle" 
                  type={currentContent.includes('　　') ? 'primary' : 'default'}
                  onClick={() => {
                    const textarea = document.getElementById('streaming-textarea') as HTMLTextAreaElement;
                    if (textarea) {
                      const start = textarea.selectionStart;
                      const end = textarea.selectionEnd;
                      const selectedText = currentContent.substring(start, end);
                      
                      if (selectedText) {
                        // 为选中文本添加段落缩进
                        const indentedText = selectedText.split('\n').map(line => 
                          line.trim() ? '　　' + line.replace(/^　　/, '') : line
                        ).join('\n');
                        
                        const newContent = currentContent.substring(0, start) + indentedText + currentContent.substring(end);
                        setCurrentContent(newContent);
                      } else {
                        // 在光标位置插入段落缩进
                        const newContent = currentContent.substring(0, start) + '　　' + currentContent.substring(start);
                        setCurrentContent(newContent);
                        // 设置光标位置
                        setTimeout(() => {
                          textarea.selectionStart = textarea.selectionEnd = start + 2;
                          textarea.focus();
                        }, 0);
                      }
                    }
                  }}
                  style={{ 
                    fontSize: '13px', 
                    height: '34px',
                    borderRadius: '8px',
                    fontWeight: 500,
                    border: currentContent.includes('　　') ? 'none' : '1.5px solid #e2e8f0',
                    boxShadow: currentContent.includes('　　') ? '0 2px 8px rgba(59, 130, 246, 0.25)' : 'none'
                  }}
                >
                  段落缩进
                </Button>
                
                <Button 
                  size="middle" 
                  onClick={() => {
                    const textarea = document.getElementById('streaming-textarea') as HTMLTextAreaElement;
                    if (textarea) {
                      const start = textarea.selectionStart;
                      const end = textarea.selectionEnd;
                      const selectedText = currentContent.substring(start, end);
                      
                      if (selectedText) {
                        const dialogText = `"${selectedText}"`;
                        const newContent = currentContent.substring(0, start) + dialogText + currentContent.substring(end);
                        setCurrentContent(newContent);
                        setTimeout(() => {
                          textarea.selectionStart = textarea.selectionEnd = start + dialogText.length;
                          textarea.focus();
                        }, 0);
                      } else {
                        const newContent = currentContent.substring(0, start) + '""' + currentContent.substring(start);
                        setCurrentContent(newContent);
                        setTimeout(() => {
                          textarea.selectionStart = textarea.selectionEnd = start + 1;
                          textarea.focus();
                        }, 0);
                      }
                    }
                  }}
                  style={{ 
                    fontSize: '13px', 
                    height: '34px',
                    borderRadius: '8px',
                    fontWeight: 500,
                    border: '1.5px solid #e2e8f0'
                  }}
                >
                  对话引号
                </Button>
                
                <Button 
                  size="middle" 
                  onClick={() => {
                    const textarea = document.getElementById('streaming-textarea') as HTMLTextAreaElement;
                    if (textarea) {
                      const start = textarea.selectionStart;
                      const newContent = currentContent.substring(0, start) + '\n\n　　' + currentContent.substring(start);
                      setCurrentContent(newContent);
                      setTimeout(() => {
                        textarea.selectionStart = textarea.selectionEnd = start + 3;
                        textarea.focus();
                      }, 0);
                    }
                  }}
                  style={{ 
                    fontSize: '13px', 
                    height: '34px',
                    borderRadius: '8px',
                    fontWeight: 500,
                    border: '1.5px solid #e2e8f0'
                  }}
                >
                  新段落
                </Button>
                
                <Button 
                  size="middle" 
                  onClick={() => {
                    if (!currentContent || currentContent.trim() === '') {
                      message.warning('请先输入或生成内容');
                      return;
                    }
                    const textarea = document.getElementById('streaming-textarea') as HTMLTextAreaElement | null;
                    const source = currentContent;
                    if (textarea) {
                      const start = textarea.selectionStart;
                      const end = textarea.selectionEnd;
                      if (start !== end) {
                        const selected = source.substring(start, end);
                        const formatted = formatChineseSentences(selected);
                        const newContent = source.substring(0, start) + formatted + source.substring(end);
                        setCurrentContent(newContent);
                        setTimeout(() => {
                          textarea.selectionStart = start;
                          textarea.selectionEnd = start + formatted.length;
                          textarea.focus();
                        }, 0);
                      } else {
                        const formatted = formatChineseSentences(source);
                        setCurrentContent(formatted);
                        setTimeout(() => {
                          textarea.focus();
                        }, 0);
                      }
                    } else {
                      const formatted = formatChineseSentences(source);
                      setCurrentContent(formatted);
                    }
                    message.success('格式化完成');
                  }}
                  type="primary"
                  style={{ 
                    fontSize: '13px', 
                    height: '34px', 
                    marginLeft: '12px',
                    borderRadius: '8px',
                    fontWeight: 600,
                    background: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)',
                    border: 'none',
                    boxShadow: '0 2px 8px rgba(59, 130, 246, 0.3)'
                  }}
                >
                  ✨ 一键格式化
                </Button>

                {/* AI消痕按钮 */}
                <Button 
                  size="middle"
                  disabled={!currentContent || isRemovingAITrace || loading || isStreaming}
                  onClick={() => {
                    if (!currentContent || currentContent.trim() === '') {
                      message.warning('请先输入或生成内容');
                      return;
                    }
                    setProcessedContent('');
                    setAiTraceDrawerVisible(true);
                  }}
                  style={{ 
                    fontSize: '13px', 
                    height: '34px', 
                    marginLeft: '8px',
                    borderRadius: '8px',
                    fontWeight: 500,
                    border: '1.5px solid #e2e8f0',
                    background: '#ffffff'
                  }}
                >
                  🧹 AI消痕
                </Button>
                
                <div style={{ 
                  marginLeft: 'auto', 
                  fontSize: '13px', 
                  color: '#64748b', 
                  display: 'flex', 
                  alignItems: 'center', 
                  gap: '8px',
                  padding: '8px 14px',
                  background: lastSaveTime 
                    ? 'linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%)' 
                    : 'linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%)',
                  borderRadius: '8px',
                  border: `2px solid ${lastSaveTime ? '#86efac' : '#e2e8f0'}`,
                  boxShadow: lastSaveTime ? '0 2px 6px rgba(134, 239, 172, 0.2)' : 'none',
                  fontWeight: 500
                }}>
                  {lastSaveTime ? (
                    <>
                      <CheckOutlined style={{ fontSize: '13px', color: '#10b981' }} />
                      <span style={{ color: '#166534', fontWeight: 600 }}>最后保存：{lastSaveTime}</span>
                    </>
                  ) : (
                    <span style={{ color: '#94a3b8', fontSize: '12px', fontWeight: 500 }}>未保存</span>
                  )}
                </div>
              </div>

              {/* 文本编辑器 */}
              <textarea
                ref={textareaRef}
                id="streaming-textarea"
                className={`writing-textarea ${isStreaming ? 'streaming' : ''} ${progressHint && !currentContent ? 'progress-hint' : ''}`}
                value={progressHint && !currentContent ? progressHint : currentContent}
                onChange={(e) => setCurrentContent(e.target.value)}
                placeholder={isStreaming ? "AI正在为您写作中..." : "开始您的创作..."}
                style={{ 
                  flex: 1,
                  width: '100%',
                  fontSize: '17px', 
                  lineHeight: '2.2',
                  padding: '40px 80px',
                  border: 'none',
                  borderRadius: '0',
                  backgroundColor: '#ffffff',
                  cursor: isStreaming ? 'not-allowed' : 'text',
                  resize: 'none',
                  fontFamily: '"Source Han Serif CN", "Songti SC", "SimSun", serif',
                  outline: 'none',
                  boxShadow: 'none',
                  transition: 'all 0.3s',
                  letterSpacing: '0.5px',
                  color: progressHint && !currentContent ? '#94a3b8' : '#1a202c',
                  fontStyle: progressHint && !currentContent ? 'italic' : 'normal',
                  minHeight: 'calc(100vh - 300px)',
                  height: '100%'
                }}
                readOnly={isStreaming}
              />
              </div>

            {/* 底部状态栏 */}
            {isStreaming && (
                    <div style={{ 
                padding: '12px 32px',
                background: '#f0f9ff',
                borderTop: '1px solid #bfdbfe',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '8px',
                flexShrink: 0
              }}>
                <Spin size="small" />
                <Text style={{ fontSize: '13px', color: '#3b82f6', fontWeight: 500 }}>
                  AI正在流式写作中...
                    </Text>
                  </div>
                )}
              </div>
        </div>
      )}

        {/* 隐藏的AI写作触发器 */}
        <button 
          data-ai-write-trigger
          style={{ display: 'none' }}
          onClick={async () => {
                    if (!novelId || !currentVolume) return;
                    if (!chapterNumber) {
                      message.warning('请填写章节编号');
                      return;
                    }
                    
                    // 关闭抽屉
                    setAiDrawerVisible(false);
                    
                    // 记忆库上下文交由后端构建（前端不再传 memoryBank）
                    
                    try {
                      setLoading(true);
                      setIsStreaming(true);
                      streamingCompleteRef.current = false; // 重置完成标志
                      setCurrentContent(''); // 清空当前内容，准备接收流式输出
                      setProgressHint('[ 正在连接AI服务... ]'); // 初始化进度提示
                      setLastSavedContent(''); // 清空已保存内容，确保AI写作完成后触发自动保存
                      
                      const chapterPlan = {
                        chapterNumber,
                        title: chapterTitle || undefined,
                        type: '剧情',
                        coreEvent: chapterPlotInput || '根据卷规范与计划生成本章核心事件',
                        characterDevelopment: ['按卷规范推进角色发展'],
                        foreshadowing: '按需埋设或回收',
                        estimatedWords: 3000,
                        priority: 'high',
                        mood: 'normal'
                      };

                      // 检查AI配置
                      if (!checkAIConfig()) {
                        message.error(AI_CONFIG_ERROR_MESSAGE);
                        return;
                      }

                      // 使用fetch进行流式请求
                      const token = localStorage.getItem('token');
                      const requestBody = withAIConfig({
                        chapterPlan,
                        userAdjustment: userAdjustment || undefined,
                        model: selectedModel || undefined, // 传递选择的模型
                        promptTemplateId: selectedTemplateId || undefined // 传递选择的提示词模板ID
                      });
                      
                      const response = await fetch(`/api/novel-craft/${novelId}/write-chapter-stream`, {
                        method: 'POST',
                        headers: {
                          'Content-Type': 'application/json',
                          'Accept': 'text/event-stream',
                          'Cache-Control': 'no-cache',
                          ...(token ? { 'Authorization': `Bearer ${token}` } : {})
                        },
                        body: JSON.stringify(requestBody)
                      });

                      if (!response.ok) {
                        throw new Error(`HTTP error! status: ${response.status}`);
                      }

                      const reader = response.body?.getReader();
                      const decoder = new TextDecoder();

                      if (!reader) {
                        throw new Error('无法获取响应流');
                      }

                      message.info('开始AI流式写作...');

                      // 读取流式响应
                      let buffer = '';
                      let accumulatedContent = ''; // 用于累积内容
                      let currentEventType = ''; // 当前事件类型

                      console.log('开始读取流式响应...');

                      try {
                        while (true) {
                          const { done, value } = await reader.read();
                          
                          if (done) {
                            console.log('✅ 流读取完成（SSE连接关闭）');
                            setLoading(false);
                            setIsStreaming(false);
                            streamingCompleteRef.current = true; // 设置完成标志
                            message.success('AI写作完成，已更新记忆库与一致性');
                            break;
                          }

                          const chunk = decoder.decode(value, { stream: true });
                          console.log('接收到数据块:', chunk);
                          buffer += chunk;

                          // 处理事件流格式
                          const lines = buffer.split('\n');
                          buffer = lines.pop() || ''; // 保留不完整的行

                          for (const line of lines) {
                            console.log('处理行:', line);
                            console.log('当前content长度:', currentContent.length);
                            
                            if (line.startsWith('data:')) {
                              // 处理 'data:' 或 'data: ' 两种格式
                              const data = line.startsWith('data: ') ? line.slice(6) : line.slice(5);
                              console.log('🔍 提取的数据:', data);
                              console.log('🔍 数据长度:', data.length);
                              console.log('🔍 数据类型:', typeof data);
                              console.log('🔍 当前事件类型:', currentEventType);
                              
                              if (data === '[DONE]') {
                                console.log('🔍 检测到结束标记');
                                currentEventType = ''; // 重置事件类型
                                continue;
                              }
                              
                              // 如果是title事件的数据，直接设置标题
                              if (currentEventType === 'title') {
                                console.log('✅ 后端提取的章节标题:', data);
                                setChapterTitle(data);
                                currentEventType = ''; // 重置事件类型
                                continue;
                              }

                              // 先尝试作为内容处理，无论是否能解析JSON
                              let shouldAddContent = false;
                              let contentToAdd = '';

                              try {
                                const parsed = JSON.parse(data);
                                console.log('🔍 成功解析JSON:', parsed);

                                // 直接文本/数字/数组，作为内容追加（修复数字被屏蔽）
                                if (typeof parsed === 'string' || typeof parsed === 'number') {
                                  shouldAddContent = true;
                                  contentToAdd = String(parsed);
                                } else if (Array.isArray(parsed)) {
                                  const joined = parsed
                                    .map((v) => (typeof v === 'string' || typeof v === 'number') ? String(v) : '')
                                    .join('');
                                  if (joined) {
                                    shouldAddContent = true;
                                    contentToAdd = joined;
                                  }
                                } else if (parsed && typeof parsed === 'object') {
                                  // 处理进度消息
                                  if (parsed.message && parsed.step) {
                                    console.log('🔍 进度消息:', parsed.message, parsed.step);
                                    if (parsed.step === 'generating_summary') {
                                      // 跳过摘要生成提示
                                    } else if (parsed.step === 'saving_chapter') {
                                      message.loading({ content: '正在保存章节...', key: 'saving' });
                                      setProgressHint('[ 正在保存章节... ]');
                                    } else if (parsed.step === 'updating_memory') {
                                      message.loading({ content: '正在更新记忆库...', key: 'memory' });
                                      setProgressHint('[ 正在更新记忆库... ]');
                                    } else if (parsed.step === 'final_coherence_check') {
                                      message.loading({ content: '正在进行连贯性检查...', key: 'coherence' });
                                      setProgressHint('[ 正在进行连贯性检查... ]');
                                    } else {
                                      message.loading({ content: parsed.message, key: 'progress' });
                                      setProgressHint(`[ ${parsed.message} ]`);
                                    }
                                    shouldAddContent = false;
                                  } else if (parsed.type === 'content') {
                                    shouldAddContent = true;
                                    contentToAdd = parsed.content || '';
                                  } else if (parsed.type === 'complete') {
                                    if (parsed.generatedContent) {
                                      shouldAddContent = true;
                                      contentToAdd = parsed.generatedContent || '';
                                    }
                                  } else {
                                    if (parsed.generatedContent) {
                                      shouldAddContent = true;
                                      contentToAdd = parsed.generatedContent || '';
                                    } else if (parsed.content) {
                                      shouldAddContent = true;
                                      contentToAdd = parsed.content || '';
                                    }
                                  }
                                }
                              } catch (err) {
                                console.log('🔍 JSON解析失败，作为纯文本处理');
                                // 识别进度消息并设置提示，其他都当作内容
                                const isProgress = data && /构建完整上下文|上下文消息|开始增强AI写作|更新记忆管理系统|记忆库已更新|记忆系统更新失败|写作完成|preparing|writing|context_ready|memory_updated|memory_stats|updating_memory|complete|正在装配记忆库|正在分析前置章节|正在进行连贯性预检查|正在构建AI写作提示词|AI开始创作中|正在保存章节|正在生成章节概括|正在更新记忆库|🧠|📚|🔍|⚡|🤖|💾|📝/.test(data);
                                if (isProgress) {
                                  // 将进度消息显示为提示
                                  setProgressHint(`[ ${data.trim()} ]`);
                                  shouldAddContent = false;
                                } else if (data && data.trim() !== '' &&
                                    !data.includes('开始写作章节') &&
                                    !data.includes('准备写作环境') &&
                                    !data.includes('开始AI写作') &&
                                    !data.includes('正在装配') &&
                                    !data.includes('正在分析') &&
                                    !data.includes('正在构建') &&
                                    !data.includes('正在进行') &&
                                    !data.includes('正在保存') &&
                                    !data.includes('正在生成') &&
                                    !data.includes('正在更新')) {
                                  shouldAddContent = true;
                                  contentToAdd = data;
                                  console.log('🔍 纯文本内容:', contentToAdd);
                                } else if (data && data.trim() !== '') {
                                  // 其他"正在..."类消息也显示为提示
                                  setProgressHint(`[ ${data.trim()} ]`);
                                  shouldAddContent = false;
                                }
                              }

                              // 如果需要添加内容，直接累积显示
                              if (shouldAddContent && contentToAdd) {
                                console.log('✅ 准备添加内容:', contentToAdd);
                                console.log('✅ 内容长度:', contentToAdd.length);
                                console.log('✅ 前50字符:', contentToAdd.substring(0, 50));

                                // 正文开始生成，清除进度提示
                                if (progressHint && accumulatedContent.length === 0) {
                                  setProgressHint('');
                                }

                                // 直接累积内容，保持原始格式（Markdown）
                                accumulatedContent += contentToAdd;
                                
                                // 处理格式化显示（统一复用一键格式化逻辑，兼容跨块标点/引号规则）
                                const displayContent = formatChineseSentences(accumulatedContent);

                                console.log('✅ 即将设置内容，累积长度:', displayContent.length);
                                
                                // 立即更新state，触发React重新渲染
                                setCurrentContent(displayContent);
                                
                                console.log('✅ setCurrentContent已调用');
                              } else {
                                console.log('🔍 跳过内容，data:', data);
                              }
                            } else if (line.startsWith('event:')) {
                              // 处理 'event:' 或 'event: ' 两种格式
                              const eventType = line.startsWith('event: ') ? line.slice(7) : line.slice(6);
                              console.log('事件类型:', eventType);
                              currentEventType = eventType; // 记录当前事件类型
                              
                              if (eventType === 'preparing') {
                                message.loading({ content: '正在准备写作环境...', key: 'preparing' });
                                setProgressHint('[ 正在准备写作环境... ]');
                              } else if (eventType === 'progress') {
                                // 处理进度事件，显示弹出提示但不显示在文本框中
                                console.log('收到进度事件');
                                // 下一行的data中包含进度信息，先等待解析
                              } else if (eventType === 'writing') {
                                message.destroy('preparing');
                                message.loading({ content: '正在AI写作中...', key: 'writing' });
                                setProgressHint('[ 正在生成章节内容... ]');
                              } else if (eventType === 'chunk') {
                                // chunk事件的数据在下一行的data:中，这里只是标记
                                console.log('检测到chunk事件，等待数据...');
                              } else if (eventType === 'title') {
                                // title事件：后端提取的章节标题
                                // 标题在下一行的data:中，标记等待提取
                                console.log('检测到title事件，等待标题数据...');
                              } else if (eventType === 'complete') {
                                message.destroy('writing');
                                message.destroy('summary');
                                message.destroy('saving');
                                message.destroy('memory');
                                message.destroy('coherence');
                                message.destroy('progress');
                                message.success('章节写作完成！');
                                streamingCompleteRef.current = true; // 设置完成标志
                                setProgressHint(''); // 清除进度提示
                              } else if (eventType === 'error') {
                                throw new Error('写作过程中出现错误');
                              }
                            }
                          }
                        }

                        // 内容已在流式过程中实时更新，无需再次同步
                        console.log('流式完成，最终内容长度:', currentContent.length);
                        
                        // 处理完成后的记忆库更新（改为仅更新评分展示，不再写回本地/传参与后端）
                        // 前端不再显示一致性评分，完全由后端管理

                        // 标题已由后端异步生成并通过title事件发送，无需前端自动生成


                      } catch (streamError: any) {
                        console.error('流读取错误:', streamError);
                        setLoading(false);
                        setIsStreaming(false);
                        setProgressHint(''); // 清除进度提示
                        message.destroy();
                        message.error('流式读取失败');
                      } finally {
                        reader.releaseLock();
                      }

                    } catch (e: any) {
                      setLoading(false);
                      setIsStreaming(false);
                      setProgressHint(''); // 清除进度提示
                      message.error(e?.message || 'AI写作失败');
                    }
                  }}
        />

      {/* AI指导建议抽屉 */}
      <Drawer
        title="✨ AI创作建议"
        placement="right"
        width={500}
        open={guidanceDrawerVisible}
        onClose={() => setGuidanceDrawerVisible(false)}
        styles={{
          body: { padding: '24px' }
        }}
      >
        <Spin spinning={guidanceLoading}>
          {aiGuidance && (
            <Space direction="vertical" style={{ width: '100%' }} size="large">
              {/* AI建议内容保留原有逻辑 */}
            </Space>
          )}
        </Spin>
      </Drawer>

      {/* 历史章节查看抽屉 */}
      <Drawer
        title={`📚 《${currentVolume?.title || ''}》历史章节`}
        placement="left"
        width={480}
        open={historyDrawerVisible}
        onClose={() => setHistoryDrawerVisible(false)}
        styles={{
          body: { padding: '20px' }
        }}
      >
        <Spin spinning={chaptersLoading}>
          {!chaptersLoading && historicalChapters.length === 0 && (
            <div style={{ textAlign: 'center', padding: '40px 0' }}>
              <Text type="secondary">暂无历史章节</Text>
            </div>
          )}
          {!chaptersLoading && historicalChapters.length > 0 && (
            <List
              dataSource={historicalChapters}
              renderItem={(chapter: any) => (
                <List.Item 
                  key={chapter.id}
                  style={{ cursor: 'pointer', borderRadius: '8px', transition: 'all 0.2s' }}
                  onClick={() => {
                    setChapterNumber(chapter.chapterNumber);
                    setChapterTitle(chapter.title || '');
                    setCurrentContent(chapter.content || '');
                    setChapterId(String(chapter.id));
                    setHistoryDrawerVisible(false);
                    message.success(`已加载第${chapter.chapterNumber}章`);
                  }}
                >
                  <List.Item.Meta
                    title={
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Badge count={chapter.chapterNumber} style={{ backgroundColor: '#52c41a' }} />
                        <Text strong>{chapter.title || '未命名章节'}</Text>
                      </div>
                    }
                    description={
                      <div>
                        <Text type="secondary" style={{ fontSize: '12px' }}>
                          {new Date(chapter.createdAt).toLocaleDateString()}
                        </Text>
                        {chapter.content && (
                          <div style={{
                            marginTop: '8px',
                            padding: '8px',
                            background: '#f5f5f5',
                            borderRadius: '4px',
                            maxHeight: '60px',
                            overflow: 'hidden'
                          }}>
                            <Text ellipsis style={{ fontSize: '12px', color: '#666' }}>
                              {chapter.content.substring(0, 200)}...
                            </Text>
                          </div>
                        )}
                      </div>
                    }
                  />
                </List.Item>
              )}
            />
          )}
        </Spin>
      </Drawer>

      {/* 隐藏的发布章节按钮（保留兼容性）*/}
      <button 
        style={{ display: 'none' }}
        onClick={async () => {
                    if (!novelId) { message.warning('缺少小说ID'); return; }
                    if (chapterNumber == null) { message.warning('缺少章节编号'); return; }

                    try {
                      // 1) 若无章节ID，先创建章节
                      let id = chapterId;
                      if (!id) {
                        const createPayload: any = {
                          novelId: parseInt(novelId),
                          title: chapterTitle || `第${chapterNumber}章`,
                          content: currentContent || '',
                          chapterNumber: chapterNumber
                        };
                        const resCreate: any = await api.post('/chapters', createPayload);
                        const created = resCreate?.data || resCreate;
                        if (created?.id) {
                          id = String(created.id);
                          setChapterId(id);
                        }
                      } else {
                        // 若已有章节ID，确保保存最新标题与内容
                        await api.put(`/chapters/${id}`, {
                          title: chapterTitle,
                          content: currentContent,
                          chapterNumber: chapterNumber
                        });
                      }

                      if (!id) { message.error('无法确认：章节未创建成功'); return; }

                      // 2) 发布章节
                      message.loading({ content: '正在发布本章...', key: 'publishing' });
                      try {
                        // 检查AI配置
                        if (!checkAIConfig()) {
                          message.warning('未配置AI');
                        }
                        
                        // 传递AI配置到后端
                        await api.post(`/chapters/${id}/publish`, withAIConfig({}));
                      } finally {
                        message.destroy('publishing');
                      }

                      // 3) 标记状态为已完成（冗余，但保持与旧逻辑兼容）
                      await api.put(`/chapters/${id}`, { status: 'COMPLETED' });

                      // 4) 推进到下一章，清空输入，并持久化当前章数到本地
                      const nextChapter = (chapterNumber || 0) + 1;
                      setChapterNumber(nextChapter);
                      setChapterTitle('');
                      setUserAdjustment('');
                      setCurrentContent('');

                      try {
                        const key = `novel_workflow_${novelId}`;
                        const saved = localStorage.getItem(key);
                        const parsed = saved ? JSON.parse(saved) : {};
                        const next = {
                          ...parsed,
                          workflow: {
                            ...(parsed.workflow || {}),
                            currentChapter: nextChapter
                          }
                        };
                        localStorage.setItem(key, JSON.stringify(next));
                      } catch {}

                      // 重新加载章节列表
                      loadChapterList();

                      message.success(`本章已发布。进入第${nextChapter}章`);
                    } catch (e: any) {
                      message.error(e?.message || '确认本章失败');
                    }
                  }}
      />

      {/* 小说大纲查看/编辑抽屉 */}
      <Drawer
        title="小说大纲"
        placement="right"
        open={outlineDrawerVisible}
        onClose={() => setOutlineDrawerVisible(false)}
        width={700}
        extra={
                        <Space>
            <Button 
              icon={<RobotOutlined />}
              onClick={handleAIOptimizeNovelOutline}
              loading={outlineLoading}
            >
              AI优化
                          </Button>
            <Button 
              type="primary"
              onClick={handleSaveNovelOutline}
              loading={outlineLoading}
            >
              保存
                          </Button>
                        </Space>
        }
      >
        <div style={{ marginBottom: '16px' }}>
                              <Alert
            message="在这里查看和编辑小说的总体大纲"
                                type="info"
            showIcon
            style={{ marginBottom: '16px' }}
          />
          <TextArea
            value={editingOutline}
            onChange={(e) => setEditingOutline(e.target.value)}
            placeholder="输入或粘贴小说大纲..."
            rows={20}
            style={{
              fontSize: '14px',
              lineHeight: '1.8',
              fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei"'
            }}
                                  />
                                </div>
      </Drawer>

      {/* 卷大纲查看/编辑抽屉 */}
      <Drawer
        title={`第${currentVolume?.volumeNumber || ''}卷大纲`}
        placement="right"
        open={volumeOutlineDrawerVisible}
        onClose={() => setVolumeOutlineDrawerVisible(false)}
        width={700}
        extra={
          <Space>
                                      <Button 
              icon={<RobotOutlined />}
              onClick={handleAIOptimizeVolumeOutline}
              loading={outlineLoading}
            >
              AI优化
                                      </Button>
            <Button 
              type="primary"
              onClick={handleSaveVolumeOutline}
              loading={outlineLoading}
            >
              保存
            </Button>
                                        </Space>
                                      }
      >
        <div style={{ marginBottom: '16px' }}>
                              <Alert
            message="在这里查看和编辑当前卷的详细大纲"
                                type="info"
                                showIcon
            style={{ marginBottom: '16px' }}
          />
          <TextArea
            value={editingVolumeOutline}
            onChange={(e) => setEditingVolumeOutline(e.target.value)}
            placeholder="输入或粘贴卷大纲..."
            rows={20}
            style={{
              fontSize: '14px',
              lineHeight: '1.8',
              fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei"'
            }}
          />
        </div>
      </Drawer>

      {/* AI指导请求抽屉 */}
      <Drawer
        title="请求AI写作指导"
        open={guidanceDrawerVisible}
        onClose={() => setGuidanceDrawerVisible(false)}
        width={500}
        extra={
          <Button 
            type="primary" 
            loading={guidanceLoading}
            onClick={() => guidanceForm.submit()}
          >
            获取指导
          </Button>
        }
      >
        <Alert
          message="AI将分析您当前的写作内容，并提供个性化的指导建议"
          type="info"
          style={{ marginBottom: 16 }}
          showIcon
        />
        
        <Form
          form={guidanceForm}
          layout="vertical"
          onFinish={handleRequestGuidance}
        >
          <Form.Item
            name="userInput"
            label="您的问题或需求"
            rules={[{ required: true, message: '请描述您的需求' }]}
          >
            <TextArea
              rows={4}
              placeholder="例如：我觉得这段对话有些平淡，如何让它更有张力？"
              showCount
              maxLength={500}
            />
          </Form.Item>
        </Form>

        <Divider />

        <div>
          <Title level={5}>当前写作状态</Title>
          <Text type="secondary">已写字数：{wordCount}</Text>
          <br />
          <Text type="secondary">
            最新内容预览：{currentContent.substring(Math.max(0, currentContent.length - 100))}...
          </Text>
        </div>
      </Drawer>

      {/* 指导历史抽屉 */}
      <Drawer
        title="AI指导历史"
        open={historyDrawerVisible}
        onClose={() => setHistoryDrawerVisible(false)}
        width={600}
      >
        {guidanceHistory.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '40px 0' }}>
            <Text type="secondary">暂无指导历史</Text>
          </div>
        ) : (
          <List
            dataSource={guidanceHistory}
            renderItem={(item: any) => (
              <List.Item>
                <Card size="small" style={{ width: '100%' }}>
                  <div style={{ marginBottom: 8 }}>
                    <Text type="secondary">{item.timestamp}</Text>
                  </div>
                  <div style={{ marginBottom: 8 }}>
                    <Text strong>问题：</Text>{item.userInput}
                  </div>
                  <div style={{ marginBottom: 8 }}>
                    <Text strong>AI建议：</Text>
                    {item.guidance.nextFocus && (
                      <Paragraph>{item.guidance.nextFocus}</Paragraph>
                    )}
                  </div>
                  <div>
                    <Text type="secondary" style={{ fontSize: '12px' }}>
                      内容快照：{item.contentSnapshot}...
                    </Text>
                  </div>
                </Card>
              </List.Item>
            )}
          />
        )}
      </Drawer>

      {/* AI写作抽屉 */}
      <Drawer
        title={<span style={{ fontSize: '16px', fontWeight: 600 }}>✨ AI智能写作</span>}
        placement="right"
        width={480}
        open={aiDrawerVisible}
        onClose={() => setAiDrawerVisible(false)}
        styles={{
          body: { padding: '24px' }
        }}
      >
        <div style={{ marginBottom: '20px' }}>
          <Text style={{ fontSize: '13px', color: '#64748b', display: 'block', marginBottom: '8px' }}>
            本章剧情（可选）
          </Text>
          <TextArea
            placeholder="描述本章的核心剧情、冲突点、情感基调等...&#10;&#10;例如：&#10;- 主角发现宝藏线索&#10;- 与反派首次正面交锋&#10;- 揭示重要世界观设定"
            value={chapterPlotInput}
            onChange={(e) => setChapterPlotInput(e.target.value)}
            rows={12}
            style={{
              fontSize: '14px',
              lineHeight: '1.8',
              borderRadius: '8px'
            }}
          />
            </div>

        {/* 模型选择 */}
        <div style={{ marginBottom: '20px' }}>
          <Text style={{ fontSize: '13px', color: '#64748b', display: 'block', marginBottom: '8px' }}>
            AI模型（可选，留空使用默认）
          </Text>
          <Input
            placeholder="例如: 需要当前选择厂商的模型"
            value={selectedModel}
            onChange={(e) => setSelectedModel(e.target.value)}
            allowClear
            size="large"
            style={{
              fontSize: '14px',
              borderRadius: '8px'
            }}
          />
        </div>

        {/* 提示词模板选择 */}
        <div style={{ marginBottom: '20px' }}>
          <Text style={{ fontSize: '13px', color: '#64748b', display: 'block', marginBottom: '8px' }}>
            提示词模板（可选，留空使用默认）
          </Text>
          <Button 
            size="large"
            block
            onClick={() => setTemplateModalVisible(true)}
            style={{
              textAlign: 'left',
              height: '52px',
              fontSize: '14px',
              borderRadius: '12px',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              border: '1px solid #e2e8f0',
              background: 'white',
              padding: '0 16px',
              boxShadow: '0 2px 4px rgba(0,0,0,0.02)',
              transition: 'all 0.3s ease'
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.borderColor = '#8b5cf6';
              e.currentTarget.style.boxShadow = '0 4px 12px rgba(139, 92, 246, 0.1)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.borderColor = '#e2e8f0';
              e.currentTarget.style.boxShadow = '0 2px 4px rgba(0,0,0,0.02)';
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <div style={{ 
                width: '32px', 
                height: '32px', 
                borderRadius: '8px', 
                background: '#f3e8ff', 
                display: 'flex', 
                alignItems: 'center', 
                justifyContent: 'center',
                color: '#7c3aed'
              }}>
                <RobotOutlined />
              </div>
              <span style={{ color: selectedTemplateId ? '#1e293b' : '#64748b', fontWeight: 500 }}>
                {selectedTemplateId ? (
                  <>
                    {(() => {
                      const allTemplates = [...publicTemplates, ...favoriteTemplates, ...customTemplates];
                      const selected = allTemplates.find((t: any) => t.id === selectedTemplateId);
                      return selected ? selected.name : '已选择模板';
                    })()}
                  </>
                ) : '选择提示词模板...'}
              </span>
            </div>
            <div style={{ 
              color: '#8b5cf6', 
              fontSize: '12px', 
              background: '#f5f3ff', 
              padding: '4px 10px', 
              borderRadius: '20px', 
              fontWeight: 600 
            }}>
              更换
            </div>
          </Button>
          
          {templateIdFromUrl && (
            <Alert
              type="info"
              message="已从提示词库绑定模板"
              description="您从提示词库选择的模板已自动应用于当前创作"
              showIcon
              style={{ marginTop: '12px', fontSize: '13px' }}
            />
          )}
        </div>

                    <Button 
          type="primary" 
          icon={<RobotOutlined />} 
          size="large"
          block
          loading={loading}
          disabled={!chapterNumber}
                      onClick={() => {
            setAiDrawerVisible(false);
            // 触发主AI写作按钮的点击
            const aiBtn = document.querySelector('[data-ai-write-trigger]') as HTMLButtonElement;
            aiBtn?.click();
          }}
          style={{
            borderRadius: '10px',
            height: '48px',
            background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
            border: 'none',
            boxShadow: '0 4px 15px rgba(102, 126, 234, 0.4)',
            fontWeight: 600,
            fontSize: '15px'
          }}
        >
          开始生成
                    </Button>
      </Drawer>

      {/* AI消痕抽屉 */}
      {/* AI消痕抽屉 - 极简高级版 */}
      <Drawer
        open={aiTraceDrawerVisible}
        onClose={() => setAiTraceDrawerVisible(false)}
        width={500}
        placement="right"
        closable={false}
        mask={false}
        headerStyle={{ display: 'none' }}
        bodyStyle={{ padding: 0, overflow: 'hidden' }}
        style={{ 
          boxShadow: '-5px 0 30px rgba(0,0,0,0.08)',
          borderLeft: '1px solid rgba(0,0,0,0.03)'
        }}
      >
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          height: '100%',
          background: '#ffffff',
          position: 'relative'
        }}>
          {/* 极简 Header - 绿色系 */}
          <div style={{
            padding: '24px 28px',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexShrink: 0,
            background: 'rgba(255,255,255,0.8)',
            backdropFilter: 'blur(10px)',
            zIndex: 10
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <div style={{
                width: '36px',
                height: '36px',
                borderRadius: '10px',
                background: '#f0fdf4', // 极淡绿
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '18px'
              }}>
                🍃
              </div>
              <div>
                <div style={{ fontSize: '16px', fontWeight: 600, color: '#064e3b', letterSpacing: '-0.3px' }}>
                  AI 智能消痕
                </div>
                <div style={{ fontSize: '12px', color: '#10b981', marginTop: '2px' }}>
                  让文字更自然流畅
                </div>
              </div>
            </div>

            <button
              onClick={() => setAiTraceDrawerVisible(false)}
              style={{
                width: '32px',
                height: '32px',
                borderRadius: '50%',
                border: 'none',
                background: 'transparent',
                color: '#9ca3af',
                fontSize: '18px',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                transition: 'all 0.2s'
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = '#f3f4f6';
                e.currentTarget.style.color = '#111827';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = 'transparent';
                e.currentTarget.style.color = '#9ca3af';
              }}
            >
              ×
            </button>
          </div>

          {/* 内容区域 */}
          <div style={{
            flex: 1,
            overflowY: 'auto',
            padding: '0 28px 28px 28px',
            position: 'relative'
          }}>
            {isRemovingAITrace ? (
              <div style={{
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '24px',
                opacity: 0.8
              }}>
                <div style={{ position: 'relative', width: '60px', height: '60px' }}>
                  <div style={{
                    position: 'absolute',
                    inset: 0,
                    borderRadius: '50%',
                    border: '2px solid #e5e7eb',
                  }} />
                  <div style={{
                    position: 'absolute',
                    inset: 0,
                    borderRadius: '50%',
                    borderTop: '2px solid #10b981',
                    animation: 'spin 1s linear infinite'
                  }} />
                </div>
                
                <div style={{ textAlign: 'center' }}>
                  <div style={{ fontSize: '14px', fontWeight: 500, color: '#111827', marginBottom: '6px' }}>
                    正在润色...
                  </div>
                  <div style={{ color: '#9ca3af', fontSize: '12px' }}>
                    逐句优化，去除生硬感
                  </div>
                </div>
              </div>
            ) : processedContent ? (
              <div style={{ animation: 'fadeInUp 0.4s ease-out' }}>
                <div style={{ 
                  display: 'flex', 
                  justifyContent: 'space-between', 
                  alignItems: 'center',
                  marginBottom: '16px',
                  paddingBottom: '16px',
                  borderBottom: '1px dashed #e5e7eb'
                }}>
                  <div style={{ fontSize: '12px', color: '#6b7280', fontWeight: 500 }}>
                    处理结果预览
                  </div>
                  <div style={{ fontSize: '12px', color: '#10b981', background: '#ecfdf5', padding: '2px 8px', borderRadius: '10px' }}>
                    {processedContent.length} 字
                  </div>
                </div>

                <div style={{
                  fontSize: '15px',
                  lineHeight: '1.8',
                  color: '#374151',
                  whiteSpace: 'pre-wrap',
                  fontFamily: '"Chinese Quote", -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "Helvetica Neue", Helvetica, Arial, sans-serif'
                }}>
                  {processedContent}
                </div>
                
                <div style={{ height: '80px' }} />
              </div>
            ) : (
              <div style={{
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                <div style={{ 
                  width: '80px', 
                  height: '80px', 
                  background: '#f9fafb',
                  borderRadius: '50%',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  marginBottom: '24px'
                }}>
                  <span style={{ fontSize: '32px', opacity: 0.3 }}>🧹</span>
                </div>
                <div style={{ fontSize: '15px', fontWeight: 500, color: '#374151', marginBottom: '8px' }}>
                  准备消痕
                </div>
                <div style={{ fontSize: '13px', color: '#9ca3af', textAlign: 'center', maxWidth: '260px', lineHeight: '1.5' }}>
                  点击下方按钮，AI 将为您去除文字的机器味，使其更自然。
                </div>
              </div>
            )}
          </div>

          {/* 底部悬浮操作栏 */}
          <div style={{
            position: 'absolute',
            bottom: 0,
            left: 0,
            right: 0,
            padding: '20px 28px',
            background: 'rgba(255,255,255,0.9)',
            backdropFilter: 'blur(10px)',
            borderTop: '1px solid rgba(0,0,0,0.03)',
            display: 'flex',
            justifyContent: 'flex-end',
            gap: '12px'
          }}>
            {!isRemovingAITrace && !processedContent && (
               <button
               onClick={async () => {
                try {
                  if (!checkAIConfig()) {
                    message.error(AI_CONFIG_ERROR_MESSAGE);
                    return;
                  }
                  
                  setIsRemovingAITrace(true);
                  setProcessedContent('');
                  
                  const token = localStorage.getItem('token');
                  const requestBody = withAIConfig({
                    content: currentContent
                  });
                  
                  const response = await fetch('/api/ai/remove-trace-stream', {
                    method: 'POST',
                    headers: {
                      'Content-Type': 'application/json',
                      'Accept': 'text/event-stream',
                      'Cache-Control': 'no-cache',
                      ...(token ? { 'Authorization': `Bearer ${token}` } : {})
                    },
                    body: JSON.stringify(requestBody)
                  });

                  if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                  }

                  const reader = response.body?.getReader();
                  const decoder = new TextDecoder();

                  if (!reader) {
                    throw new Error('无法获取响应流');
                  }

                  let buffer = '';
                  let accumulated = '';
                  let currentEvent = '';
                  const progressRegex = /(正在AI消痕处理中\.?\.?\.?|处理中\.?\.?\.?|processing|progress|开始处理)/i;

                  console.log('开始读取AI消痕流式响应...');

                  while (true) {
                    const { done, value } = await reader.read();
                    
                    if (done) {
                      console.log('AI消痕流读取完成');
                      setIsRemovingAITrace(false);
                      message.success('AI消痕完成！');
                      break;
                    }

                    const chunk = decoder.decode(value, { stream: true });
                    buffer += chunk;

                    const lines = buffer.split('\n');
                    buffer = lines.pop() || '';

                    for (const line of lines) {
                      const trimmedLine = line.trim();
                      if (trimmedLine.startsWith('event:')) {
                        currentEvent = trimmedLine.slice(6).trim();
                        continue;
                      }
                      
                      if (trimmedLine.startsWith('data:')) {
                        const data = trimmedLine.startsWith('data: ') ? trimmedLine.slice(6) : trimmedLine.slice(5);
                        
                        if (currentEvent === 'start' || currentEvent === 'done' || currentEvent === 'error') {
                          currentEvent = '';
                          continue;
                        }
                        
                        if (data === '[DONE]' || data.trim() === '[DONE]') {
                          continue;
                        }

                        if (data.trim()) {
                          try {
                            const parsed = JSON.parse(data);
                            if (parsed && typeof parsed === 'object') {
                              const piece = parsed.content || parsed.delta || parsed.text || '';
                              if (piece && !progressRegex.test(String(piece))) {
                                accumulated += String(piece);
                                const sanitized = accumulated.replace(/(正在AI消痕处理中\.?\.?\.?|处理中\.?\.?\.?|processing|progress|开始处理)/gi, '');
                                setProcessedContent(sanitized);
                              }
                            }
                          } catch (err) {
                            if (!progressRegex.test(data)) {
                              accumulated += data;
                              const sanitized = accumulated.replace(/(正在AI消痕处理中\.?\.?\.?|处理中\.?\.?\.?|processing|progress|开始处理)/gi, '');
                              setProcessedContent(sanitized);
                            }
                          }
                        }
                        currentEvent = '';
                      }
                    }
                  }
                } catch (e: any) {
                  console.error('AI消痕失败:', e);
                  setIsRemovingAITrace(false);
                  message.error(e?.message || 'AI消痕失败');
                }
              }}
               style={{
                 height: '40px',
                 padding: '0 24px',
                 borderRadius: '20px',
                 border: 'none',
                 background: '#10b981', // 绿色主色调
                 color: '#ffffff',
                 fontSize: '13px',
                 fontWeight: 500,
                 cursor: 'pointer',
                 transition: 'all 0.2s',
                 boxShadow: '0 4px 12px rgba(16, 185, 129, 0.2)'
               }}
               onMouseEnter={(e) => {
                 e.currentTarget.style.transform = 'translateY(-1px)';
                 e.currentTarget.style.boxShadow = '0 6px 16px rgba(16, 185, 129, 0.3)';
               }}
               onMouseLeave={(e) => {
                 e.currentTarget.style.transform = 'translateY(0)';
                 e.currentTarget.style.boxShadow = '0 4px 12px rgba(16, 185, 129, 0.2)';
               }}
             >
               开始消痕
             </button>
            )}

            {processedContent && (
              <>
                <button
                  onClick={() => setAiTraceDrawerVisible(false)}
                  style={{
                    height: '40px',
                    padding: '0 20px',
                    borderRadius: '20px',
                    border: '1px solid #e5e7eb',
                    background: '#ffffff',
                    color: '#6b7280',
                    fontSize: '13px',
                    fontWeight: 500,
                    cursor: 'pointer',
                    transition: 'all 0.2s'
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.background = '#f9fafb';
                    e.currentTarget.style.color = '#374151';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.background = '#ffffff';
                    e.currentTarget.style.color = '#6b7280';
                  }}
                >
                  取消
                </button>
                <button
                  onClick={() => {
                    setCurrentContent(processedContent);
                    setAiTraceDrawerVisible(false);
                    message.success('已替换到正文');
                  }}
                  style={{
                    height: '40px',
                    padding: '0 24px',
                    borderRadius: '20px',
                    border: 'none',
                    background: '#10b981',
                    color: '#ffffff',
                    fontSize: '13px',
                    fontWeight: 500,
                    cursor: 'pointer',
                    transition: 'all 0.2s',
                    boxShadow: '0 4px 12px rgba(16, 185, 129, 0.2)',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '6px'
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.transform = 'translateY(-1px)';
                    e.currentTarget.style.boxShadow = '0 6px 16px rgba(16, 185, 129, 0.3)';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.transform = 'translateY(0)';
                    e.currentTarget.style.boxShadow = '0 4px 12px rgba(16, 185, 129, 0.2)';
                  }}
                >
                  <span>✓</span> 应用
                </button>
              </>
            )}
          </div>
        </div>
      </Drawer>

      {/* 提示词模板选择弹窗 */}
      <Modal
        title={null}
        open={templateModalVisible}
        onCancel={() => setTemplateModalVisible(false)}
        width={1080}
        centered
        closeIcon={
          <div style={{
            background: 'white',
            borderRadius: '50%',
            width: '32px',
            height: '32px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
            cursor: 'pointer',
            marginTop: '12px',
            marginRight: '12px'
          }}>
            <svg width="12" height="12" viewBox="0 0 12 12" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M1 1L11 11M1 11L11 1" stroke="#64748b" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </div>
        }
        bodyStyle={{ 
          maxHeight: '85vh', 
          height: '80vh',
          overflowY: 'hidden',
          padding: '0',
          borderRadius: '24px',
          background: '#f8fafc',
          display: 'flex',
          flexDirection: 'column'
        }}
        footer={null}
        style={{ top: '20px' }}
      >
        {/* Modal Header */}
        <div style={{
          padding: '32px 40px 24px 40px',
          background: 'white',
          borderBottom: '1px solid #f1f5f9',
          flexShrink: 0
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <h2 style={{ 
                margin: 0, 
                fontSize: '28px', 
                fontWeight: 800, 
                background: 'linear-gradient(135deg, #1e293b 0%, #334155 100%)',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent'
              }}>
                选择提示词模板
              </h2>
              <p style={{ margin: '8px 0 0', color: '#64748b', fontSize: '15px' }}>
                选择一个预设模板来优化您的 AI 写作体验，或者创建您自己的专属模板。
              </p>
            </div>
            
            <div style={{ display: 'flex', gap: '12px' }}>
              <Button 
                onClick={() => {
                  setSelectedTemplateId(null);
                  setTemplateModalVisible(false);
                  message.success('已切换到默认提示词');
                }}
                style={{ height: '44px', borderRadius: '12px', padding: '0 20px', border: '1px solid #e2e8f0' }}
              >
                使用默认
              </Button>
              <Button 
                type="primary" 
                onClick={() => setTemplateModalVisible(false)}
                style={{ 
                  height: '44px', 
                  borderRadius: '12px', 
                  padding: '0 32px',
                  background: 'linear-gradient(135deg, #7c3aed 0%, #6d28d9 100%)',
                  border: 'none',
                  boxShadow: '0 4px 12px rgba(124, 58, 237, 0.3)',
                  fontWeight: 600
                }}
              >
                确认选择
              </Button>
            </div>
          </div>

          <div style={{ marginTop: '24px' }}>
            <Tabs 
              activeKey={templateModalTab} 
              onChange={setTemplateModalTab}
              type="card"
              tabBarStyle={{ margin: 0, border: 'none' }}
              items={[
                {
                  key: 'public',
                  label: (
                    <span style={{ fontSize: '15px', padding: '4px 8px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <GlobalOutlined /> 公开模板
                    </span>
                  ),
                },
                {
                  key: 'favorites',
                  label: (
                    <span style={{ fontSize: '15px', padding: '4px 8px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <HeartOutlined /> 我的收藏
                    </span>
                  ),
                },
                {
                  key: 'custom',
                  label: (
                    <span style={{ fontSize: '15px', padding: '4px 8px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <FileTextOutlined /> 自定义模板
                    </span>
                  ),
                }
              ]}
            />
          </div>
        </div>

        {/* Modal Content - Scrollable Area */}
        <div style={{ 
          flex: 1, 
          overflowY: 'auto', 
          padding: '32px 40px',
          background: 'linear-gradient(to bottom, #f8fafc, #fff)'
        }}>
          {templateModalTab === 'public' && (
            publicTemplates.length === 0 ? (
              <Empty description="暂无公开模板" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: '24px' }}>
                {publicTemplates.map((template: any) => (
                  <TemplateCard
                    key={template.id}
                    template={template}
                    selectedId={selectedTemplateId}
                    onSelect={(id) => {
                      setSelectedTemplateId(id);
                      message.success(`已选择: ${template.name}`);
                    }}
                  />
                ))}
              </div>
            )
          )}
          
          {templateModalTab === 'favorites' && (
             favoriteTemplates.length === 0 ? (
              <Empty description="还没有收藏任何模板" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: '24px' }}>
                {favoriteTemplates.map((template: any) => (
                  <TemplateCard
                    key={template.id}
                    template={template}
                    selectedId={selectedTemplateId}
                    onSelect={(id) => {
                      setSelectedTemplateId(id);
                      message.success(`已选择: ${template.name}`);
                    }}
                  />
                ))}
              </div>
            )
          )}
          
          {templateModalTab === 'custom' && (
             customTemplates.length === 0 ? (
              <Empty description="还没有创建自定义模板" image={Empty.PRESENTED_IMAGE_SIMPLE}>
                <Button type="primary" onClick={() => {
                  navigate('/prompt-library');
                }} style={{ borderRadius: '8px', background: '#7c3aed', height: '40px', padding: '0 24px' }}>
                  立即创建
                </Button>
              </Empty>
            ) : (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: '24px' }}>
                {customTemplates.map((template: any) => (
                  <TemplateCard
                    key={template.id}
                    template={template}
                    selectedId={selectedTemplateId}
                    onSelect={(id) => {
                      setSelectedTemplateId(id);
                      message.success(`已选择: ${template.name}`);
                    }}
                    icon={<FileTextOutlined style={{ color: '#7c3aed' }} />}
                  />
                ))}
              </div>
            )
          )}
        </div>
      </Modal>

      {/* 批量写作进度弹窗 */}
      <Modal
        title="批量生成进度"
        open={batchModalVisible}
        footer={[
          <Button key="cancel" danger onClick={cancelBatchWriting}>
            取消生成
          </Button>
        ]}
        closable={false}
        maskClosable={false}
        width={500}
      >
        <div style={{ textAlign: 'center', padding: '20px 0' }}>
          <div style={{ marginBottom: '20px' }}>
            <RobotOutlined style={{ fontSize: '48px', color: '#10b981' }} />
          </div>
          <div style={{ marginBottom: '16px', fontSize: '16px', fontWeight: 600 }}>
            正在生成第 {batchProgress.current} / {batchProgress.total} 章
          </div>
          <div style={{ marginBottom: '20px' }}>
            <div style={{
              width: '100%',
              height: '8px',
              backgroundColor: '#f0f0f0',
              borderRadius: '4px',
              overflow: 'hidden'
            }}>
              <div style={{
                width: `${(batchProgress.current / batchProgress.total) * 100}%`,
                height: '100%',
                backgroundColor: '#10b981',
                transition: 'width 0.3s ease'
              }} />
            </div>
          </div>
          <div style={{ fontSize: '14px', color: '#666' }}>
            {batchProgress.current === 0 ? '准备开始...' : 
             batchProgress.current === batchProgress.total ? '即将完成...' :
             `正在生成第${batchProgress.current}章内容...`}
          </div>
          <div style={{ marginTop: '16px', fontSize: '12px', color: '#999' }}>
            💡 此过程可能需要几分钟时间，请耐心等待
          </div>
        </div>
      </Modal>
    </div>
  );
};

export default VolumeWritingStudio;
