import React, { useState, useEffect } from 'react';
import {
  Card, Button, Form, Input, InputNumber, Typography, Space,
  Modal, Tag, Progress, Divider,
  Alert, Row, Col,
  FloatButton, notification, App as AntdApp
} from 'antd';
import {
  BookOutlined, PlusOutlined, EditOutlined,
  RobotOutlined,
  BarChartOutlined, BulbOutlined, SettingOutlined, ArrowRightOutlined,
  CheckCircleOutlined, ClockCircleOutlined, ExclamationCircleOutlined,
  ReloadOutlined, PlayCircleOutlined, EyeOutlined
} from '@ant-design/icons';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import novelVolumeService, { NovelVolume } from '../services/novelVolumeService';
import novelService, { Novel as NovelModel } from '../services/novelService';
import novelOutlineService, { NovelOutline as OutlineModel } from '../services/novelOutlineService';
import { aiTaskService } from '../services/aiTaskService';
import api from '../services/api';
import { checkAIConfig, withAIConfig, AI_CONFIG_ERROR_MESSAGE } from '../utils/aiRequest';
import './VolumeManagementPage.css';

const { Title, Text } = Typography;
const { TextArea } = Input;

interface LocationState {
  initialIdea?: string;
  autoGenerate?: boolean;
}

const VolumeManagementPage: React.FC = () => {
  const { message } = AntdApp.useApp();
  const [novel, setNovel] = useState<NovelModel | null>(null);
  const [volumes, setVolumes] = useState<NovelVolume[]>([]);
  const [loading, setLoading] = useState(false);
  const [currentStep, setCurrentStep] = useState(0);
  const [volumeStats, setVolumeStats] = useState<any>(null);

  // 大纲状态
  const [confirmedSuperOutline, setConfirmedSuperOutline] = useState<OutlineModel | null>(null);
  const [hasSuperOutline, setHasSuperOutline] = useState(false);
  const [currentSuperOutline, setCurrentSuperOutline] = useState<OutlineModel | null>(null);

  // 任务状态管理
  const [taskProgress, setTaskProgress] = useState<{percentage: number, message: string} | null>(null);
  const [currentTaskId, setCurrentTaskId] = useState<number | null>(null);
  const [stopPolling, setStopPolling] = useState<(() => void) | null>(null);
  const [isGenerating, setIsGenerating] = useState(false);

  // 大纲操作状态
  const [outlineForm] = Form.useForm();
  const [outlineUserAdvice, setOutlineUserAdvice] = useState('');
  const [isGeneratingOutline, setIsGeneratingOutline] = useState(false);
  const [isConfirmingOutline, setIsConfirmingOutline] = useState(false);

  // 卷操作状态
  const [volumeAdvices, setVolumeAdvices] = useState<Record<string, string>>({});
  const [isGeneratingVolumeOutlines, setIsGeneratingVolumeOutlines] = useState(false);
  const [generatingVolumeIds, setGeneratingVolumeIds] = useState<Set<string>>(new Set());
  // 批量异步任务：每卷任务进度
  const [volumeTasks, setVolumeTasks] = useState<Record<string, { taskId: number, progress: number, status: string, message?: string }>>({});
  const [taskStops, setTaskStops] = useState<Record<string, () => void>>({});

  // 弹窗状态
  const [generateModalVisible, setGenerateModalVisible] = useState(false);
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [selectedVolume, setSelectedVolume] = useState<NovelVolume | null>(null);
  const [quickStartVisible, setQuickStartVisible] = useState(false);
  const [outlineGenerationVisible, setOutlineGenerationVisible] = useState(false);

  const [generateForm] = Form.useForm();
  const [hasShownQuickStart, setHasShownQuickStart] = useState(false); // 标记是否已显示过弹窗
  
  // 卷详情页面状态
  const [isGeneratingSingleVolume, setIsGeneratingSingleVolume] = useState(false);
  const [streamingVolumeOutline, setStreamingVolumeOutline] = useState('');
  const [singleVolumeAdvice, setSingleVolumeAdvice] = useState('');
  const [adviceInputVisible, setAdviceInputVisible] = useState(false);

  // 总字数动态计算状态
  const [totalWords, setTotalWords] = useState(1500000); // 默认 500章 × 3000字 (快速开始弹窗)
  const [totalWordsGenerate, setTotalWordsGenerate] = useState(1500000); // 默认 500章 × 3000字 (生成大纲弹窗)

  const { novelId } = useParams<{ novelId: string }>();
  const navigate = useNavigate();
  const location = useLocation();





  useEffect(() => {
    if (novelId) {
      // 初始化状态恢复
      const initializeState = async () => {
        // 优先从后端获取状态
        const backendStep = await fetchCreationStageFromBackend();
        if (backendStep !== null) {
          console.log('[useEffect] 使用后端状态:', backendStep);
          setCurrentStep(backendStep);
        } else {
          // 后端获取失败时，尝试从本地存储恢复
          const restoredStep = restoreCreationState();
          console.log('[useEffect] 使用本地存储状态:', restoredStep);
          if (restoredStep !== currentStep) {
            setCurrentStep(restoredStep);
          }
        }

        // 检查是否正在生成卷规划（用于刷新后恢复轮询）
        const generatingMark = localStorage.getItem(`novel_${novelId}_generating_volumes`);
        if (generatingMark) {
          const startTime = parseInt(generatingMark);
          const elapsedMinutes = (Date.now() - startTime) / 1000 / 60;
          
          if (elapsedMinutes < 5) {
            // 5分钟内，认为还在生成，恢复轮询
            console.log('[恢复轮询] 检测到正在生成卷规划，恢复轮询...');
            setTimeout(() => {
              pollForVolumeGeneration().catch(err => {
                console.error('[恢复轮询] 轮询失败:', err);
              });
            }, 1000);
          } else {
            // 超过5分钟，可能已经失败或完成，清除标记
            console.log('[恢复轮询] 生成时间过长，清除标记');
            localStorage.removeItem(`novel_${novelId}_generating_volumes`);
          }
        }
      };

      initializeState();
      loadNovelInfo();
      loadVolumes();
      loadVolumeStats();
      checkSuperOutline();
      loadSuperOutlines();
      recoverTasks();
    }
  }, [novelId]);

  // 处理弹出配置弹窗：只在特定情况下弹出
  useEffect(() => {
    const state = location.state as LocationState;
    
    // 核心判断：只有从创建页面跳转来的才弹窗
    // 且必须没有大纲、没有正在生成、还没显示过弹窗
    const isFromCreate = !!state?.initialIdea;
    const shouldShowModal = isFromCreate && 
                           novel && 
                           !confirmedSuperOutline && 
                           !hasSuperOutline &&
                           !isGeneratingOutline && 
                           !hasShownQuickStart;
    
    console.log('[弹窗检查]', {
      isFromCreate,
      hasNovel: !!novel,
      hasConfirmedOutline: !!confirmedSuperOutline,
      hasSuperOutline,
      isGeneratingOutline,
      hasShownQuickStart,
      shouldShowModal
    });
    
    if (shouldShowModal) {
      console.log('[自动弹窗] ✅ 从创建页面跳转，弹出配置弹窗');
      
      // 填充表单
      outlineForm.setFieldsValue({
        basicIdea: state.initialIdea,
        targetChapters: 500,
        wordsPerChapter: 3000,
        targetWords: 500 * 3000, // 自动计算：500章 × 3000字/章 = 1500000字
        volumeCount: 5
      });
      
      // 显示配置弹窗
      setQuickStartVisible(true);
      // 标记已显示过弹窗（本次会话）
      setHasShownQuickStart(true);
      
      // 清除 location.state
      window.history.replaceState({}, document.title);
    }
  }, [novel, confirmedSuperOutline, hasSuperOutline, isGeneratingOutline, hasShownQuickStart, location]);

  // 组件卸载时停止轮询
  useEffect(() => {
    return () => {
      if (stopPolling) {
        stopPolling();
      }
    };
  }, [stopPolling]);

  // 多任务清理
  useEffect(() => {
    return () => {
      Object.values(taskStops).forEach(stop => {
        try { stop && stop(); } catch {}
      });
    };
  }, [taskStops]);

  const loadNovelInfo = async () => {
    if (!novelId) return;

    try {
      console.log('🔍 开始加载小说信息, novelId:', novelId);
      const novelData = await novelService.getById(novelId);
      console.log('🔍 小说信息加载成功:', novelData);
      setNovel(novelData);
    } catch (error: any) {
      console.error('❌ 加载小说信息失败:', error);
      message.error('加载小说信息失败');
    }
  };

  const loadVolumes = async () => {
    if (!novelId) return;

    setLoading(true);
    try {
      const volumesList = await novelVolumeService.getVolumesByNovelId(novelId);
      setVolumes(volumesList);

      // 改进的状态计算逻辑
      // 0 确认大纲 -> 1 生成卷和详细大纲 -> 2 开始写作
      const hasVolumes = volumesList.length > 0;
      const anyPlanned = volumesList.some(v => v.status === 'PLANNED');
      const anyInProgress = volumesList.some(v => v.status === 'IN_PROGRESS');
      const allCompleted = hasVolumes && volumesList.every(v => v.status === 'COMPLETED');
      const anyCompleted = volumesList.some(v => v.status === 'COMPLETED');
      const anyHasDetailedOutline = volumesList.some(v => v.contentOutline && v.contentOutline.length > 100);

      console.log('[loadVolumes] 卷状态分析:', {
        hasVolumes,
        anyPlanned,
        anyInProgress,
        anyCompleted,
        anyHasDetailedOutline,
        volumesCount: volumesList.length,
        volumes: volumesList.map(v => ({ id: v.id, status: v.status, hasOutline: !!(v.contentOutline && v.contentOutline.length > 100) }))
      });

      console.log('🔍 卷加载完成，等待小说数据和大纲数据后统一更新步骤');

      // 如果有卷且当前在第0步，自动切换到第1步
      if (hasVolumes && currentStep === 0) {
        console.log('[loadVolumes] 检测到卷已生成，自动切换到第1步');
        setCurrentStep(1);
        saveCreationState(1);
      }
    } catch (error: any) {
      console.error('❌ 加载卷列表失败:', error);
      message.error('加载卷列表失败');
    } finally {
      setLoading(false);
    }
  };

  const loadVolumeStats = async () => {
    if (!novelId) return;

    try {
      const stats = await novelVolumeService.getVolumeStats(novelId);
      setVolumeStats(stats);
    } catch (error: any) {
      console.warn('加载统计信息失败');
    }
  };

  // 检查大纲状态
  const checkSuperOutline = async () => {
    if (!novelId) return;

    console.log('🔍 checkSuperOutline 被调用，novelId:', novelId);

    try {
      const outline = await novelOutlineService.getOutlineByNovelId(novelId);
      console.log('🔍 获取到的大纲:', outline);

      // 检查大纲内容：优先检查 plotStructure，然后检查 outline 字段
      const outlineContent = (outline as any)?.plotStructure || (outline as any)?.outline;
      const hasOutline = !!(outline && outlineContent && outlineContent.trim());
      
      console.log('🔍 大纲内容检查:', {
        hasOutline,
        plotStructure: (outline as any)?.plotStructure?.substring(0, 100),
        outline: (outline as any)?.outline?.substring(0, 100)
      });
      
      if (hasOutline) {
        // 如果需要显示内容，将后端返回的大纲文本映射到 UI 期望的字段
        setHasSuperOutline(true);
        setCurrentSuperOutline({
          ...(outline as any),
          plotStructure: outlineContent
        } as any);

        // 重要：只有当status为CONFIRMED时，才设置为已确认状态
        const isConfirmed = (outline as any).status === 'CONFIRMED';
        if (isConfirmed) {
        setConfirmedSuperOutline({
          novelId: Number(novelId),
          outline: (outline as any).outline
        } as any);
          console.log('✅ 已检测到已确认的大纲');
        } else {
          setConfirmedSuperOutline(null);
          console.log('✅ 已检测到草稿大纲（未确认），用户可以查看、重新生成或确认');
        }
      } else {
        console.log('❌ 未找到大纲');
        setConfirmedSuperOutline(null);
        setHasSuperOutline(false);
        setCurrentSuperOutline(null);
      }
    } catch (error: any) {
      console.error('❌ 检查超级大纲状态失败:', error);
      setConfirmedSuperOutline(null);
      setHasSuperOutline(false);
      setCurrentSuperOutline(null);
    }
  };

  // 加载当前小说大纲
  const loadSuperOutlines = async () => {
    if (!novelId) return;

    try {
      const outline = await novelOutlineService.getOutlineByNovelId(novelId);
      const hasOutline = !!(outline && typeof (outline as any).outline === 'string' && (outline as any).outline.trim());
      if (hasOutline) {
        setCurrentSuperOutline({
          ...(outline as any),
          plotStructure: (outline as any).outline
        } as any);
      } else {
        setCurrentSuperOutline(null);
      }
    } catch (error: any) {
      console.warn('加载大纲失败');
      setCurrentSuperOutline(null);
    }
  };


  // 页面刷新后恢复任务
  const recoverTasks = async () => {
    const storedTasks = aiTaskService.getStoredTasks();
    const novelIdNum = parseInt(novelId!);

    // 查找当前小说的进行中任务（支持超级大纲生成、卷规划生成和卷大纲生成）
    const relevantTask = storedTasks.find(task =>
      task.novelId === novelIdNum &&
      (task.type === 'SUPER_OUTLINE_GENERATION' || task.type === 'VOLUME_GENERATION' || task.type === 'VOLUME_OUTLINE')
    );

    if (relevantTask) {
      try {
        const taskDetail = await aiTaskService.getAITaskById(relevantTask.taskId);

        if (taskDetail.status === 'RUNNING' || taskDetail.status === 'PENDING') {
          // 恢复任务轮询
          setCurrentTaskId(relevantTask.taskId);
          setIsGenerating(true);

          const taskTypeText = relevantTask.type === 'SUPER_OUTLINE_GENERATION' ? '超级大纲' :
                               (relevantTask.type === 'VOLUME_GENERATION' ? '卷规划' : '卷大纲');

          const stopPollingFn = aiTaskService.startPolling(
            relevantTask.taskId,
            (progress) => {
              setTaskProgress({
                percentage: progress.progressPercentage || 0,
                message: progress.message || '生成中...'
              });
            },
            () => {
              // 任务完成
              setTaskProgress({ percentage: 100, message: `${taskTypeText}生成完成！` });
              setCurrentTaskId(null);
              setIsGenerating(false);
              aiTaskService.removeStoredTask(relevantTask.taskId);

              message.success(`${taskTypeText}生成成功！`);
              loadVolumes();
            },
            (error) => {
              // 任务失败
              setTaskProgress(null);
              setCurrentTaskId(null);
              setIsGenerating(false);
              aiTaskService.removeStoredTask(relevantTask.taskId);
              message.error(`生成${taskTypeText}失败: ` + error);
            }
          );

          setStopPolling(() => stopPollingFn);
          message.info(`检测到进行中的${taskTypeText}生成任务，已自动恢复`);
        } else {
          // 清理已完成或失败的任务
          aiTaskService.removeStoredTask(relevantTask.taskId);
        }
      } catch (error) {
        // 任务可能已被删除，清理存储的任务信息
        aiTaskService.removeStoredTask(relevantTask.taskId);
      }
    }
  };





  // 确认大纲并生成卷规划（前端轮询卷列表直到出现）
  const confirmSuperOutline = async () => {
    console.log('🔍 confirmSuperOutline 被调用');
    console.log('🔍 currentSuperOutline:', currentSuperOutline);
    console.log('🔍 novelId:', novelId);

    if (!currentSuperOutline) {
      console.error('❌ currentSuperOutline 为 null，无法确认大纲');
      message.error('没有找到可确认的大纲，请先生成大纲');

      // 尝试重新加载大纲
      try {
        await checkSuperOutline();
        if (currentSuperOutline) {
          message.info('大纲已重新加载，请再次尝试确认');
        } else {
          message.error('仍然无法找到大纲，请检查页面状态');
        }
      } catch (error) {
        console.error('重新加载大纲失败:', error);
        message.error('重新加载大纲失败，请刷新页面重试');
      }
      return;
    }

    // 防止重复提交
    if (isConfirmingOutline) {
      message.warning('正在确认大纲，请勿重复提交');
      return;
    }

    setIsConfirmingOutline(true);

    try {
      // 1) 直接将当前大纲内容写入 novels.outline
      const outlineText = (currentSuperOutline as any).plotStructure || (currentSuperOutline as any).outline || '';
      if (!outlineText || !String(outlineText).trim()) {
        message.warning('大纲内容为空，请先生成或编辑大纲');
        return;
      }
      console.log('[confirmSuperOutline] 保存大纲到 novels.outline');
      await novelOutlineService.updateOutline(novelId!, outlineText);

      // 2) 尝试触发卷生成（优先确认大纲记录，失败则兜底触发）
      // 检查 AI 配置
      if (!checkAIConfig()) {
        message.warning('未配置AI服务，卷规划生成可能使用简化模式');
      }
      
      let triggered = false;
      try {
        console.log('[confirmSuperOutline] 尝试获取大纲记录并确认');
        const outlineRes: any = await api.get(`/outline/novel/${novelId}`);
        const outlineId = outlineRes?.id || outlineRes?.data?.id;
        if (outlineId) {
          console.log('[confirmSuperOutline] 确认大纲记录，outlineId=', outlineId);
          // 传递 AI 配置
          await api.put(`/outline/${outlineId}/confirm`, withAIConfig({}));
          message.success('大纲确认成功，已触发卷规划生成！');
          triggered = true;
        }
      } catch (err) {
        console.warn('[confirmSuperOutline] 获取大纲记录失败，将走兜底触发', err);
      }

      // 3) 若未触发，直接调用卷规划生成接口作为兜底
      if (!triggered) {
        // 优先使用用户设定的计划卷数，其次根据大纲长度估算
        let volumeCount = 5; // 默认5卷

        if (novel && novel.plannedVolumeCount && novel.plannedVolumeCount > 0) {
          // 第一优先级：使用用户输入的计划卷数
          volumeCount = novel.plannedVolumeCount;
          console.log('[confirmSuperOutline] 使用用户设定的计划卷数:', volumeCount);
        } else {
          // 第二优先级：根据大纲长度动态调整卷数
          const outlineLength = outlineText.length;
          if (outlineLength > 10000) {
            volumeCount = 8; // 长大纲分8卷
          } else if (outlineLength > 5000) {
            volumeCount = 6; // 中等大纲分6卷
          } else if (outlineLength > 2000) {
            volumeCount = 5; // 标准大纲分5卷
          } else {
            volumeCount = 3; // 短大纲分3卷
          }
          console.log('[confirmSuperOutline] 根据大纲长度估算卷数，大纲长度=', outlineLength, ', volumeCount=', volumeCount);
        }

        await api.post(`/volumes/${novelId}/generate-from-outline`, { volumeCount });
        message.success(`大纲确认成功，已触发卷规划生成（约${volumeCount}卷）！`);
      }

      setConfirmedSuperOutline({ novelId: Number(novelId), outline: outlineText } as any);
      setHasSuperOutline(true);

      // 启动轮询等待卷生成完成
      pollForVolumeGeneration();

    } catch (error: any) {
      console.error('❌ 确认大纲失败:', error);
      message.error(error.message || '确认大纲失败');
    } finally {
      setIsConfirmingOutline(false);
    }
  };

  // 轮询等待卷规划生成完成
  const pollForVolumeGeneration = async () => {
    let attempts = 0;
    const maxAttempts = 60; // 最多轮询2分钟

    // 标记正在生成卷（持久化到 localStorage）
    localStorage.setItem(`novel_${novelId}_generating_volumes`, Date.now().toString());
    console.log('[轮询] 开始轮询卷规划生成，已设置 localStorage 标记');

    return new Promise<void>((resolve, reject) => {
      const intervalId = setInterval(async () => {
        attempts++;
        try {
          // 更新进度
          const progress = Math.min(90, 10 + (attempts * 1.5));
          setTaskProgress({ percentage: progress, message: '生成卷规划中...' });

          // 检查卷列表
          // 加时间戳避免缓存，确保拿到最新结果
          let list: any[] = [];
          try {
            const res: any = await api.get(`/volumes/novel/${novelId}?_=${Date.now()}`);
            list = Array.isArray(res) ? res : (Array.isArray(res?.data) ? res.data : []);
          } catch (e) {
            // 兜底：走原服务（可能被缓存但尽量不抛错中断轮询）
            try {
              list = await novelVolumeService.getVolumesByNovelId(novelId!);
            } catch {}
          }
          if (Array.isArray(list) && list.length > 0) {
            clearInterval(intervalId);

            // 卷规划生成完成，清除标记
            localStorage.removeItem(`novel_${novelId}_generating_volumes`);
            console.log('[轮询] 卷规划生成完成，已清除 localStorage 标记');

            // 卷规划生成完成
            setTaskProgress({ percentage: 100, message: '卷规划生成完成！' });
            setVolumes(list);
            setCurrentStep(1);
            saveCreationState(1);
            loadVolumeStats();

            // 延迟清除进度条
            setTimeout(() => setTaskProgress(null), 2000);
            resolve();
          }
        } catch (error) {
          console.warn('轮询卷列表失败:', error);
        }

        // 超时处理
        if (attempts >= maxAttempts) {
          clearInterval(intervalId);
          localStorage.removeItem(`novel_${novelId}_generating_volumes`);
          console.log('[轮询] 轮询超时，已清除 localStorage 标记');
          setTaskProgress(null);
          message.warning('卷规划生成超时，请刷新查看是否已生成');
          reject(new Error('卷规划生成超时'));
        }
      }, 2000);
    });
  };

  // 取消卷生成任务
  const cancelVolumeGeneration = () => {
    if (novelId) {
      localStorage.removeItem(`novel_${novelId}_generating_volumes`);
      console.log('[取消生成] 已清除生成标记');
    }
    setTaskProgress(null);
    setIsGenerating(false);
    setCurrentTaskId(null);
    message.info('已取消生成任务');
    // 刷新页面以停止轮询
    window.location.reload();
  };

  // 生成卷规划（第一步改为流式生成大纲）
  const handleGenerateVolumes = async (values: any) => {
    if (!novelId) return;

    // 防止重复提交
    if (isGenerating || currentTaskId) {
      message.warning('任务正在进行中，请勿重复提交');
      return;
    }

    setLoading(true);
    setIsGenerating(true);
    try {
      // 先保存用户设定的卷数到小说对象
      if (values.volumeCount && novel) {
        try {
          console.log('[handleGenerateVolumes] 保存预期卷数到小说:', values.volumeCount);
          await api.put(`/novels/${novelId}`, {
            ...novel,
            plannedVolumeCount: values.volumeCount
          });
          // 更新本地小说对象
          setNovel({ ...novel, plannedVolumeCount: values.volumeCount } as any);
        } catch (error) {
          console.warn('[handleGenerateVolumes] 保存卷数失败，但继续生成大纲:', error);
        }
      }

      // 检查AI配置
      console.log('[handleGenerateVolumes] 检查AI配置...');
      const aiConfigValid = checkAIConfig();
      console.log('[handleGenerateVolumes] AI配置有效性:', aiConfigValid);
      
      if (!aiConfigValid) {
        // 显示详细的配置信息帮助调试
        const configFromStorage = localStorage.getItem('novel-ai-config');
        console.error('[handleGenerateVolumes] AI配置无效！localStorage内容:', configFromStorage);
        
        if (configFromStorage) {
          try {
            const parsedConfig = JSON.parse(configFromStorage);
            console.error('[handleGenerateVolumes] 解析后的配置:', parsedConfig);
            console.error('[handleGenerateVolumes] provider:', parsedConfig.provider);
            console.error('[handleGenerateVolumes] apiKey:', parsedConfig.apiKey ? '已设置（长度:' + parsedConfig.apiKey.length + '）' : '未设置');
            console.error('[handleGenerateVolumes] model:', parsedConfig.model);
            console.error('[handleGenerateVolumes] baseUrl:', parsedConfig.baseUrl);
          } catch (e) {
            console.error('[handleGenerateVolumes] 配置解析失败:', e);
          }
        } else {
          console.error('[handleGenerateVolumes] localStorage中没有找到AI配置');
        }
        
        message.error({
          content: AI_CONFIG_ERROR_MESSAGE + '（请检查浏览器控制台查看详细信息）',
          duration: 5
        });
        setIsGeneratingOutline(false);
        setLoading(false);
        setIsGenerating(false);
        return;
      }
      
      console.log('[handleGenerateVolumes] ✅ AI配置验证通过');

      // 流式生成大纲（SSE）
      setIsGeneratingOutline(true);
      setCurrentSuperOutline(null);

      const sseResp = await fetch(`/api/outline/generate-stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(localStorage.getItem('token') ? { 'Authorization': `Bearer ${localStorage.getItem('token')}` } : {})
        },
        body: JSON.stringify(withAIConfig({
          novelId: novelId,
          basicIdea: values.basicIdea,
          targetWordCount: values.targetWords || 1500000,
          targetChapterCount: values.targetChapters || 500
        }))
      });

      if (!sseResp.ok) {
        const errorText = await sseResp.text();
        throw new Error(`服务器错误 (${sseResp.status}): ${errorText}`);
      }

      const reader = (sseResp as any).body?.getReader();
      if (!reader) throw new Error('浏览器不支持流式读取');

      const decoder = new TextDecoder('utf-8');
      let buffer = '';
      let outlineIdFromSSE: number | null = null;
      let streamedText = '';
      setOutlineUserAdvice('');

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        const events = buffer.split('\n\n');
        buffer = events.pop() || '';

        for (const evt of events) {
          const lines = evt.split('\n');
          let eventName = 'message';
          let data = '';
          for (const line of lines) {
            if (line.startsWith('event:')) eventName = line.replace('event:', '').trim();
            if (line.startsWith('data:')) data += line.replace('data:', '').trim();
          }
          if (eventName === 'meta') {
            try {
              const meta = JSON.parse(data);
              outlineIdFromSSE = meta.outlineId;
            } catch {}
          } else if (eventName === 'chunk') {
            streamedText += data;
            setCurrentSuperOutline({
              id: outlineIdFromSSE || 0,
              novelId: novelId as any,
              basicIdea: values.basicIdea,
              coreTheme: '',
              mainCharacters: '',
              plotStructure: streamedText,
              worldSetting: '',
              keyElements: '',
              conflictTypes: '',
              targetChapterCount: values.targetChapters || 500,
              targetWordCount: values.targetWords || 1500000,
              status: 'DRAFT',
              feedbackHistory: '',
              revisionCount: 0,
              createdAt: new Date().toISOString(),
              updatedAt: new Date().toISOString(),
            } as any);
          } else if (eventName === 'done') {
            console.log('[SSE done] 生成完成，当前 currentSuperOutline:', currentSuperOutline);
            console.log('[SSE done] plotStructure 长度:', currentSuperOutline?.plotStructure?.length);
            
            setIsGeneratingOutline(false);
            setHasSuperOutline(true);
            
            // 后端流式生成时已自动保存到 novel_outlines 表（状态为DRAFT）
            // 前端只需提示用户生成完成即可
            console.log('✅ 大纲生成完成，后端已自动保存为草稿状态');
            message.success('大纲生成完成！您可以查看、修改或确认大纲');
            
            // 保持 currentSuperOutline，这样页面会继续显示大纲内容而不是回到"准备开始创作"状态
            // currentSuperOutline 已经在 chunk 事件中不断更新
            
            // 重新加载大纲数据，确保获取到最新的status
            setTimeout(() => {
              checkSuperOutline();
            }, 500);
          } else if (eventName === 'error') {
            throw new Error(data || '生成失败');
          }
        }
      }
    } catch (error: any) {
      console.error('[生成大纲失败]', error);
      const errorMsg = error.response?.data?.message || error.message || '生成大纲失败';
      message.error({
        content: errorMsg,
        duration: 5,
        style: { marginTop: '20vh' }
      });
      
      // 确保完全重置状态
      setIsGeneratingOutline(false);
      setCurrentSuperOutline(null);
      setLoading(false);
      setIsGenerating(false);
    } finally {
      // 防止状态残留
      setLoading(false);
      setIsGenerating(false);
    }
  };

  // 流式生成单个卷的详细大纲（用于卷详情页）
  const handleGenerateSingleVolumeOutlineStream = async () => {
    if (!selectedVolume) return;
    
    // 检查AI配置
    if (!checkAIConfig()) {
      message.error(AI_CONFIG_ERROR_MESSAGE);
      return;
    }
    
    // 清空旧内容，开始新生成
    setStreamingVolumeOutline('');
    setIsGeneratingSingleVolume(true);
    
    try {
      const requestBody = withAIConfig({
        userAdvice: singleVolumeAdvice || ''
      });
      
      const response = await fetch(`/api/volumes/${selectedVolume.id}/generate-outline-stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(localStorage.getItem('token') ? { 'Authorization': `Bearer ${localStorage.getItem('token')}` } : {})
        },
        body: JSON.stringify(requestBody)
      });

      if (!response.ok) {
        throw new Error('生成请求失败');
      }

      const reader = response.body?.getReader();
      if (!reader) throw new Error('浏览器不支持流式读取');

      const decoder = new TextDecoder('utf-8');
      let buffer = '';
      let accumulatedText = '';

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        
        buffer += decoder.decode(value, { stream: true });
        
        const events = buffer.split('\n\n');
        buffer = events.pop() || '';
        
        for (const evt of events) {
          if (!evt.trim()) continue;
          
          const lines = evt.split('\n');
          let eventName = 'message';
          let data = '';
          for (const line of lines) {
            if (line.startsWith('event:')) eventName = line.replace('event:', '').trim();
            if (line.startsWith('data:')) data += line.replace('data:', '').trim();
          }
          
          if (eventName === 'chunk') {
            accumulatedText += data;
            setStreamingVolumeOutline(accumulatedText);
          } else if (eventName === 'done') {
            message.success('卷大纲生成完成！');
            // 重新加载卷数据以获取最新的contentOutline
            await loadVolumes();
            // 更新selectedVolume
            if (selectedVolume) {
              const detail = await novelVolumeService.getVolumeDetail(selectedVolume.id);
              const updatedVolume = (detail && (detail.volume || (detail.data && detail.data.volume))) || null;
              if (updatedVolume) {
                setSelectedVolume(updatedVolume as NovelVolume);
              }
            }
          } else if (eventName === 'error') {
            throw new Error(data || '生成失败');
          }
        }
      }
    } catch (error: any) {
      console.error('流式生成失败:', error);
      message.error(error.message || '生成失败，请重试');
      setStreamingVolumeOutline(''); // 失败时清空
    } finally {
      setIsGeneratingSingleVolume(false);
    }
  };

  // 生成详细大纲（异步任务模式，避免超时）
  const handleGenerateOutline = async (volumeId: string) => {
    // 防止重复提交
    if (isGenerating || currentTaskId) {
      message.warning('任务正在进行中，请勿重复提交');
      return;
    }

    setLoading(true);
    setIsGenerating(true);
    try {
      const advice = volumeAdvices[volumeId] || '';

      // 尝试创建异步大纲生成任务
      const response = await fetch(`/api/volumes/${volumeId}/generate-outline-async`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(localStorage.getItem('token') ? { 'Authorization': `Bearer ${localStorage.getItem('token')}` } : {})
        },
        body: JSON.stringify({
          userAdvice: advice
        }),
      });

      const result = await response.json();

      if (result.code === 200 && (result.data.asyncTask || result.data.taskId)) {
        // 异步任务创建成功
        message.success('大纲生成任务已创建，正在后台处理...');

        setCurrentTaskId(result.data.taskId || result.data.asyncTask?.taskId);
        setTaskProgress({ percentage: 0, message: '开始生成卷大纲' });

        // 清理建议输入
        setVolumeAdvices(prev => {
          const next = { ...prev };
          delete next[volumeId];
          return next;
        });

        // 存储任务信息
        aiTaskService.storeTask(result.data.taskId || result.data.asyncTask?.taskId, 'VOLUME_OUTLINE', parseInt(novelId!));

        // 开始轮询任务进度
        const stopPollingFn = aiTaskService.startPolling(
          result.data.taskId || result.data.asyncTask?.taskId,
          (progress) => {
            setTaskProgress({
              percentage: progress.progressPercentage || 0,
              message: progress.message || '生成中...'
            });
          },
          () => {
            // 任务完成
            setTaskProgress({ percentage: 100, message: '卷大纲生成完成！' });
            setCurrentTaskId(null);
            setIsGenerating(false);
            aiTaskService.removeStoredTask(result.data.taskId);

            message.success('卷大纲生成成功！');
            loadVolumes(); // 重新加载卷信息
          },
          (error) => {
            // 任务失败
            setTaskProgress(null);
            setCurrentTaskId(null);
            setIsGenerating(false);
            aiTaskService.removeStoredTask(result.data.taskId);
            message.error('生成卷大纲失败: ' + error);
          }
        );

        setStopPolling(() => stopPollingFn);

      } else {
        // 如果不支持异步，回退到同步模式
        message.warning('后端暂不支持异步大纲生成，使用同步模式');
        setIsGenerating(false);

        await novelVolumeService.generateVolumeOutline(volumeId, advice);
        message.success('大纲生成成功！');
        loadVolumes(); // 重新加载卷信息
        setVolumeAdvices(prev => {
          const next = { ...prev };
          delete next[volumeId];
          return next;
        });
      }

    } catch (error: any) {
      message.error(error.response?.data?.message || error.message || '生成大纲失败');
      setIsGenerating(false);
    } finally {
      setLoading(false);
    }
  };

  const setAdviceForVolume = (volumeId: string, value: string) => {
    setVolumeAdvices(prev => ({ ...prev, [volumeId]: value }));
  };

  // 提取卷规范中的关键要点，便于卡片快速扫读
  const getOutlineHighlights = (outline?: string): string[] => {
    if (!outline || typeof outline !== 'string') return []
    try {
      // 优先尝试JSON解析，提取结构化要点
      const json = JSON.parse(outline)
      const highlights: string[] = []
      if (json.goals && Array.isArray(json.goals)) {
        highlights.push(`目标：${json.goals.slice(0, 1)[0]}`)
      }
      if (json.keyEvents && Array.isArray(json.keyEvents)) {
        highlights.push(`关键事件：${json.keyEvents.slice(0, 1)[0]}`)
      }
      if (json.characterArcs && Array.isArray(json.characterArcs)) {
        highlights.push(`角色弧线：${json.characterArcs.slice(0, 1)[0]}`)
      }
      if (json.foreshadowingPlan && Array.isArray(json.foreshadowingPlan)) {
        highlights.push(`伏笔：${json.foreshadowingPlan.slice(0, 1)[0]}`)
      }
      if (highlights.length > 0) {
        return highlights.slice(0, 3)
      }
    } catch {}
    try {
      const lines = outline.split(/\r?\n/).map(l => l.trim()).filter(Boolean)
      const keywords = ['关键', '目标', '转折', '角色', '伏笔', '高潮', '节奏']
      const bulletLike = lines.filter(l => /^[-*•·]/.test(l) || keywords.some(k => l.includes(k)))
      const unique: string[] = []
      for (const l of bulletLike) {
        const cleaned = l.replace(/^[-*•·]\s*/, '')
        if (cleaned && !unique.includes(cleaned)) unique.push(cleaned)
        if (unique.length >= 3) break
      }
      if (unique.length > 0) return unique
      // 兜底：取前3行
      return lines.slice(0, 3)
    } catch {
      return []
    }
  }

  // 开始写作
  const handleStartWriting = async (volumeId: string) => {
    setLoading(true);
    try {
      const sessionData = await novelVolumeService.startVolumeWriting(volumeId);

      // 跳转到写作页面，传递会话数据
      navigate(`/novels/${novelId}/volumes/${volumeId}/writing`, {
        state: {
          initialVolumeId: volumeId,
          sessionData: sessionData
        }
      });
    } catch (error: any) {
      message.error(error.response?.data?.message || '启动写作会话失败');
    } finally {
      setLoading(false);
    }
  };

  // 查看卷详情（打开时尝试获取最新数据，避免缓存未更新）
  const handleViewDetails = async (volume: NovelVolume) => {
    setSelectedVolume(volume);
    
    // 初始化建议值（从已有的建议中获取）
    setSingleVolumeAdvice(volumeAdvices[volume.id] || '');
    
    // 重置生成状态，但不清空已有大纲
    setStreamingVolumeOutline(''); // 清空流式缓存，让Modal显示volume自带的contentOutline
    setIsGeneratingSingleVolume(false);
    setAdviceInputVisible(false);
    
    try {
      const detail = await novelVolumeService.getVolumeDetail(volume.id);
      const latestVolume = (detail && (detail.volume || (detail.data && detail.data.volume))) || null;
      if (latestVolume) {
        setSelectedVolume(latestVolume as NovelVolume);
      }
    } catch (e) {
      console.error('获取卷详情失败:', e);
      // 忽略错误，保留现有数据
    }
    setDetailModalVisible(true);
  };

  // 删除卷
  const handleDeleteVolume = async (volumeId: string) => {
    try {
      await novelVolumeService.deleteVolume(volumeId);
      message.success('删除成功');
      loadVolumes();
      loadVolumeStats();
    } catch (error: any) {
      message.error(error.response?.data?.message || '删除失败');
    }
  };

  // 获取步骤状态
  const getStepStatus = (step: number) => {
    // 修复后的步骤状态逻辑
    if (step < currentStep) return 'finish';  // 已完成的步骤
    if (step === currentStep) return 'process';  // 当前进行的步骤
    return 'wait';  // 等待中的步骤
  };

  // 获取步骤的详细状态信息
  const getStepInfo = (step: number) => {
    const hasConfirmedOutline = !!confirmedSuperOutline;
    const hasVolumes = volumes.length > 0;
    const allHaveDetailedOutline = volumes.length > 0 && volumes.every(v => v.contentOutline && v.contentOutline.length > 100);

    switch (step) {
      case 0: // 生成大纲
        return {
          title: '生成大纲',
          description: hasConfirmedOutline ? '大纲已生成' : 'AI生成整体故事大纲',
          icon: <BulbOutlined />,
          status: hasConfirmedOutline ? 'finish' as const : (currentStep === 0 ? 'process' as const : 'wait' as const)
        };
      case 1: // 卷规划 + 详细大纲
        return {
          title: '卷规划 + 详细大纲',
          description: allHaveDetailedOutline ? '详细大纲已生成' : '生成卷规划并扩展详细大纲',
          icon: <RobotOutlined />,
          status: allHaveDetailedOutline ? 'finish' as const : (currentStep === 1 ? 'process' as const : 'wait' as const)
        };
      case 2: // 开始写作
        return {
          title: '开始写作',
          description: '基于详细大纲进行创作',
          icon: <EditOutlined />,
          status: (currentStep === 2 ? 'process' as const : 'wait' as const)
        };
      default:
        return {
          title: '未知步骤',
          description: '未知状态',
          icon: <BookOutlined />,
          status: 'wait' as const
        };
    }
  };

  // 保存创作状态到本地存储
  const saveCreationState = (step: number) => {
    try {
      const stateData = {
        currentStep: step,
        hasConfirmedOutline: !!confirmedSuperOutline,
        hasVolumes: volumes.length > 0,
        volumesCount: volumes.length,
        lastUpdated: Date.now()
      };
      localStorage.setItem(`novel_creation_state_${novelId}`, JSON.stringify(stateData));
      console.log('[saveCreationState] 创作状态已保存:', stateData);
    } catch (error) {
      console.warn('[saveCreationState] 保存创作状态失败:', error);
    }
  };

  // 从本地存储恢复创作状态
  const restoreCreationState = () => {
    try {
      const saved = localStorage.getItem(`novel_creation_state_${novelId}`);
      if (saved) {
        const stateData = JSON.parse(saved);
        console.log('[restoreCreationState] 恢复创作状态:', stateData);
        return stateData.currentStep || 0;
      }
    } catch (error) {
      console.warn('[restoreCreationState] 恢复创作状态失败:', error);
    }
    return 0;
  };

  // 从后端获取创作状态
  const fetchCreationStageFromBackend = async () => {
    try {
      // 暂时跳过后端API调用，因为存在权限问题
      // const response = await fetch(`/api/novels/${novelId}/creation-stage`);
      // if (response.ok) {
      //   const data = await response.json();
      //   console.log('[fetchCreationStageFromBackend] 后端创作状态:', data);
      //
      //   // 根据后端状态映射到前端步骤
      //   const stageToStepMap = {
      //     'OUTLINE_PENDING': 0,
      //     'OUTLINE_CONFIRMED': 1,
      //     'VOLUMES_GENERATED': 2,
      //     'DETAILED_OUTLINE_GENERATED': 2,
      //     'WRITING_IN_PROGRESS': 2,
      //     'WRITING_COMPLETED': 2
      //   };
      //
      //   const backendStep = stageToStepMap[data.creationStage] || 0;
      //   console.log(`[fetchCreationStageFromBackend] 映射步骤: ${data.creationStage} -> ${backendStep}`);
      //   return backendStep;
      // }
      console.log('[fetchCreationStageFromBackend] 暂时跳过后端API调用');
    } catch (error) {
      console.warn('[fetchCreationStageFromBackend] 获取后端创作状态失败:', error);
    }
    return null;
  };

  // 检查并更新流程步骤
  const updateProcessStep = () => {
    // 严格检查：只有 confirmedSuperOutline 不为空才认为大纲已确认
    // 不再依赖 novel.outline 字段，因为流式生成时会自动保存但状态可能是DRAFT
    const hasConfirmedOutline = !!confirmedSuperOutline;
    const hasVolumes = volumes.length > 0;
    const allHaveDetailedOutline = volumes.length > 0 && volumes.every(v => v.contentOutline && v.contentOutline.length > 100);

    console.log('[updateProcessStep] 状态检查:', {
      hasConfirmedOutline,
      hasVolumes,
      allHaveDetailedOutline,
      confirmedSuperOutline: !!confirmedSuperOutline,
      currentStep
    });

    let newStep = currentStep;

    // 改进的状态判断逻辑
    // 注意：不再自动进入步骤2（写作阶段），需要用户手动点击按钮
    if (!hasConfirmedOutline) {
      // 没有确认的大纲，停留在第一步（确认大纲）
      newStep = 0;
      console.log('[updateProcessStep] 无已确认大纲 -> 步骤0');
    } else if (hasConfirmedOutline && !hasVolumes) {
      // 有确认的大纲但没有卷，需要生成卷
      newStep = 1;
      console.log('[updateProcessStep] 有大纲但无卷 -> 步骤1');
    } else if (hasConfirmedOutline && hasVolumes) {
      // 有大纲和卷，停留在步骤1，无论详细大纲是否生成完成
      // 用户需要手动点击"开始写作"按钮才能进入步骤2
      newStep = 1;
      if (allHaveDetailedOutline) {
        console.log('[updateProcessStep] 所有卷详细大纲已完成，停留在步骤1等待用户确认');
      } else {
        console.log('[updateProcessStep] 有卷但详细大纲未完成 -> 步骤1');
      }
    }

    // 如果步骤有变化，更新状态并保存
    if (newStep !== currentStep) {
      console.log(`[updateProcessStep] 状态变化: ${currentStep} -> ${newStep}`, {
        hasConfirmedOutline,
        hasConfirmedOutlineState: !!confirmedSuperOutline,
        hasNovelOutline: !!(novel && (novel as any).outline && (novel as any).outline.trim().length > 0),
        hasVolumes,
        allHaveDetailedOutline,
        volumesCount: volumes.length,
        novelTitle: novel?.title || 'Loading...'
      });
      setCurrentStep(newStep);
      saveCreationState(newStep);
    } else {
      console.log(`[updateProcessStep] 步骤无变化，保持: ${currentStep}`, {
        hasConfirmedOutline,
        hasConfirmedOutlineState: !!confirmedSuperOutline,
        hasNovelOutline: !!(novel && (novel as any).outline && (novel as any).outline.trim().length > 0),
        hasVolumes,
        allHaveDetailedOutline,
        volumesCount: volumes.length
      });
    }
  };

  // 在相关状态变化后更新流程步骤
  useEffect(() => {
    // 确保数据已加载完成再更新步骤
    // 需要等待 novel 加载完成（不为 null）和 volumes 数据加载完成
    if (novel !== null && volumes.length >= 0) {
      console.log('🔍 数据加载完成，开始更新流程步骤', {
        novelLoaded: novel !== null,
        volumesLoaded: volumes.length >= 0,
        confirmedSuperOutlineExists: !!confirmedSuperOutline,
        novelHasOutline: !!(novel && (novel as any).outline && (novel as any).outline.trim().length > 0)
      });
      updateProcessStep();
    } else {
      console.log('🔍 等待数据加载完成...', {
        novelLoaded: novel !== null,
        volumesLoaded: volumes.length >= 0
      });
    }
  }, [confirmedSuperOutline, volumes, novel]);

  const getVolumeIcon = (volume: NovelVolume) => {
    switch (volume.status) {
      case 'COMPLETED':
        return <CheckCircleOutlined style={{ color: '#52c41a' }} />;
      case 'IN_PROGRESS':
        return <ClockCircleOutlined style={{ color: '#1890ff' }} />;
      case 'PLANNED':
        return <ExclamationCircleOutlined style={{ color: '#faad14' }} />;
      default:
        return <BookOutlined />;
    }
  };

  const showQuickStart = () => {
    setQuickStartVisible(true);
  };

  // 按建议重生大纲（走 /outline/{id}/revise）
  const regenerateSuperOutline = async () => {
    if (!currentSuperOutline || !outlineUserAdvice.trim()) {
      message.warning('请输入优化建议');
      return;
    }

    setIsGeneratingOutline(true);
    try {
      const result = await novelOutlineService.reviseOutline((currentSuperOutline as any).id, { feedback: outlineUserAdvice });
      if (result) {
        message.success('大纲重生成功！');
        setCurrentSuperOutline(result as any);
        setOutlineUserAdvice('');
        setOutlineGenerationVisible(false);
        // 重新加载数据
        loadSuperOutlines();
        checkSuperOutline();
      } else {
        throw new Error('重生大纲失败');
      }
    } catch (error: any) {
      message.error(error.message || '重新生成大纲失败');
    } finally {
      setIsGeneratingOutline(false);
    }
  };

  // 生成单个卷的详细大纲
  const generateVolumeDetailedOutline = async (volumeId: string) => {
    const volume = volumes.find(v => v.id === volumeId);
    if (!volume) {
      message.error('找不到指定的卷');
      return;
    }

    const advice = volumeAdvices[volumeId] || '';

    // 计算卷的章节数和字数信息
    const chapterCount = volume.chapterEnd - volume.chapterStart + 1;
    const estimatedWords = volume.estimatedWordCount || 0;
    const avgWordsPerChapter = chapterCount > 0 ? Math.round(estimatedWords / chapterCount) : 3000;

    // 构建增强的建议，包含章节和字数信息
    const enhancedAdvice = `
【卷基本信息】
- 卷标题：${volume.title}
- 章节范围：第${volume.chapterStart}章-第${volume.chapterEnd}章（共${chapterCount}章）
- 预估总字数：${estimatedWords}字
- 平均每章字数：${avgWordsPerChapter}字

【用户建议】
${advice || '请按照标准网文节奏生成详细大纲，确保每章都有明确的目标和钩子。'}

【生成要求】
请根据以上信息生成${chapterCount}章的详细大纲，每章控制在${avgWordsPerChapter}字左右，确保：
1. 每章都有明确的剧情推进和情感钩子
2. 每3-5章设计一个小高潮
3. 本卷的最终高潮安排在倒数第2-3章
4. 章节功能多样化：冲突爆发/成长展示/情感互动/智谋布局等
`.trim();

    setGeneratingVolumeIds(prev => new Set(prev).add(volumeId));
    try {
      const result = await novelVolumeService.generateVolumeOutline(volumeId, enhancedAdvice);

      message.success('卷详细大纲生成成功！');

      // 清理建议输入
      setVolumeAdvices(prev => {
        const next = { ...prev };
        delete next[volumeId];
        return next;
      });

      // 重新加载卷信息
      loadVolumes();
    } catch (error: any) {
      message.error(error.message || '生成卷详细大纲失败');
    } finally {
      setGeneratingVolumeIds(prev => {
        const next = new Set(prev);
        next.delete(volumeId);
        return next;
      });
    }
  };

  // 统一建议文本（来自顶部输入框已同步到每卷）
  const getUnifiedAdvice = (): string => {
    const advs = Object.values(volumeAdvices).filter(Boolean);
    // 去重合并
    const unique = Array.from(new Set(advs.map(a => a.trim())));
    return unique.join('; ');
  };

  // 批量生成（可选统一建议）
  const batchGenerateVolumeOutlines = async (withAdvice: boolean) => {
    if (volumes.length === 0) {
      message.warning('没有可生成的卷');
      return;
    }

    // 检查AI配置
    if (!checkAIConfig()) {
      message.error(AI_CONFIG_ERROR_MESSAGE);
      return;
    }

    setIsGeneratingVolumeOutlines(true);
    try {
      // 为每个卷构建增强的信息
      const enhancedVolumeData = volumes.map((volume, index) => {
        const chapterCount = volume.chapterEnd - volume.chapterStart + 1;
        const estimatedWords = volume.estimatedWordCount || 0;
        const avgWordsPerChapter = chapterCount > 0 ? Math.round(estimatedWords / chapterCount) : 3000;
        const userAdvice = volumeAdvices[volume.id] || '';

        return {
          volumeId: volume.id,
          volumeTitle: volume.title,
          volumeIndex: index + 1,
          chapterStart: volume.chapterStart,
          chapterEnd: volume.chapterEnd,
          chapterCount: chapterCount,
          estimatedWordCount: estimatedWords,
          avgWordsPerChapter: avgWordsPerChapter,
          userAdvice: withAdvice ? userAdvice : '',
          enhancedPrompt: `
【卷基本信息】
- 卷标题：${volume.title}
- 卷序号：第${index + 1}卷
- 章节范围：第${volume.chapterStart}章-第${volume.chapterEnd}章（共${chapterCount}章）
- 预估总字数：${estimatedWords}字
- 平均每章字数：${avgWordsPerChapter}字

【用户建议】
${withAdvice && userAdvice ? userAdvice : '请按照标准网文节奏生成详细大纲，确保每章都有明确的目标和钩子。'}

【生成要求】
请根据以上信息生成${chapterCount}章的详细大纲，每章控制在${avgWordsPerChapter}字左右，确保：
1. 每章都有明确的剧情推进和情感钩子
2. 每3-5章设计一个小高潮
3. 本卷的最终高潮安排在倒数第2-3章
4. 章节功能多样化：冲突爆发/成长展示/情感互动/智谋布局等
5. 与前后卷保持良好的承接关系
`.trim()
        };
      });

      const requestBody = withAIConfig({
        novelId: parseInt(novelId!),
        volumeIds: volumes.map(v => Number(v.id)),
        userAdvice: withAdvice ? getUnifiedAdvice() : ''
      });

      const response = await fetch(`/api/volumes/batch-generate-outlines`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(localStorage.getItem('token') ? { 'Authorization': `Bearer ${localStorage.getItem('token')}` } : {})
        },
        body: JSON.stringify(requestBody)
      });

      const result = await response.json();
      // 后端返回 { novelId, volumeCount, tasks: [{taskId, volumeId, volumeTitle}], mode }
      const tasks: any[] = result?.tasks || result?.data?.tasks || [];
      if (Array.isArray(tasks) && tasks.length > 0) {
        // 初始化任务状态
        const init: Record<string, { taskId: number, progress: number, status: string, message?: string }> = {};
        tasks.forEach(task => {
          const volumeId = String(task.volumeId);
          init[volumeId] = { 
            taskId: task.taskId, 
            progress: 0, 
            status: 'PENDING', 
            message: '任务已创建，等待开始...' 
          };
        });
        setVolumeTasks(prev => ({ ...prev, ...init }));

        message.success('批量大纲任务已创建，正在生成中...');

        // 启动真实任务轮询
        let completedCount = 0;
        try {
          tasks.forEach(task => {
            const taskId = task.taskId;
            const volumeId = String(task.volumeId);
            
            try {
              aiTaskService.storeTask(taskId, 'VOLUME_OUTLINE', parseInt(novelId!));
            } catch {}
            
            const stop = aiTaskService.startPolling(
              taskId,
              (progress) => {
                // 实时更新进度
                setVolumeTasks(prev => {
                  const next = { ...prev };
                  if (next[volumeId]) {
                    next[volumeId] = {
                      taskId: taskId,
                      progress: progress.percentage || 0,
                      status: progress.status || 'RUNNING',
                      message: progress.message || '生成中...'
                    };
                  }
                  return next;
                });
              },
              () => {
                // 单个任务完成
                completedCount++;
                setVolumeTasks(prev => {
                  const next = { ...prev };
                  if (next[volumeId]) {
                    next[volumeId] = {
                      taskId: taskId,
                      progress: 100,
                      status: 'COMPLETED',
                      message: '生成完成'
                    };
                  }
                  return next;
                });
                
                // 刷新卷列表
                loadVolumes();
                try { aiTaskService.removeStoredTask(taskId); } catch {}
                
                // 如果全部完成，显示成功消息
                if (completedCount === tasks.length) {
                  message.success('所有卷大纲生成完成！');
                }
              },
              (err) => {
                // 任务失败
                console.warn('生成任务失败:', err);
                setVolumeTasks(prev => {
                  const next = { ...prev };
                  if (next[volumeId]) {
                    next[volumeId] = {
                      taskId: taskId,
                      progress: 0,
                      status: 'FAILED',
                      message: err || '生成失败'
                    };
                  }
                  return next;
                });
                try { aiTaskService.removeStoredTask(taskId); } catch {}
              },
              2000  // 2秒轮询一次
            );
            // 保存停止函数以便取消
            setTaskStops(prev => ({ ...prev, [String(taskId)]: stop }));
          });
        } catch (e) {
          console.warn('启动批量任务轮询失败:', e);
        }

      } else {
        throw new Error(result?.message || '批量生成卷详细大纲失败');
      }
    } catch (error: any) {
      message.error(error.message || '批量生成卷详细大纲失败');
    } finally {
      setIsGeneratingVolumeOutlines(false);
    }
  };

  // 分别提供两个入口
  const generateAllVolumeOutlinesWithAdvice = () => batchGenerateVolumeOutlines(true);
  const generateAllVolumeOutlinesWithoutAdvice = () => batchGenerateVolumeOutlines(false);

  // 进入写作页面
  // 进入写作（单个卷）
  const enterWriting = (volume: NovelVolume) => {
    navigate(`/novels/${novelId}/volumes/${volume.id}/writing`, {
      state: {
        initialVolumeId: volume.id,
        sessionData: null
      }
    });
  };

  // 开始创作（从第一卷开始）
  const startWritingFromFirstVolume = () => {
    if (volumes.length === 0) {
      message.warning('请先生成卷规划');
      return;
    }
    
    // 找到第一卷（按volumeNumber排序）
    const sortedVolumes = [...volumes].sort((a, b) => a.volumeNumber - b.volumeNumber);
    const firstVolume = sortedVolumes[0];
    
    if (!firstVolume.contentOutline || firstVolume.contentOutline.length < 100) {
      message.warning('请先为第一卷生成详细大纲');
      return;
    }
    
    navigate(`/novels/${novelId}/volumes/${firstVolume.id}/writing`, {
      state: {
        initialVolumeId: firstVolume.id,
        sessionData: null
      }
    });
  };

  return (
    <div className="volume-management-page" style={{
      background: 'linear-gradient(180deg, #f7f9ff 0%, #ffffff 60%)',
      minHeight: '100%',
      padding: '8px 8px 24px'
    }}>
      {/* 顶部操作区已按需求删除，避免占用视野 */}

      {/* 顶部统计信息 */}

      {/* 简化的工作流程指示器 */}

      {/* 卷列表的展示仅保留在第二步与第三步中，避免重复 */}

      {/* 第一步：生成大纲 */}
      {currentStep === 0 && (
        <Card
          title={
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <div style={{
                width: '32px',
                height: '32px',
                borderRadius: '50%',
                background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: 'white',
                fontSize: '16px'
              }}>
                1
              </div>
              <span>📝 第一步：生成大纲</span>
                  </div>
                }
          style={{
            marginBottom: 24,
            border: 'none',
            boxShadow: '0 4px 20px rgba(0,0,0,0.08)',
            borderRadius: '16px'
          }}
        >
          {/* 任务进度条 */}
          {taskProgress && (
            <Alert
              message="任务进行中"
              description={
                <div>
                  <Progress
                    percent={taskProgress.percentage}
                    status={taskProgress.percentage === 100 ? 'success' : 'active'}
                    strokeColor={{
                      '0%': '#108ee9',
                      '100%': '#87d068',
                    }}
                  />
                  <div style={{ marginTop: 8, color: '#666' }}>
                    {taskProgress.message}
                  </div>
                </div>
              }
              type="info"
              showIcon
              style={{
                marginBottom: 24,
                borderRadius: '12px'
              }}
            />
          )}

          {!hasSuperOutline ? (
              <div style={{
              maxWidth: '1000px', 
              margin: '0 auto',
              padding: '40px 20px'
            }}>
              {/* 生成中状态 */}
              {isGeneratingOutline ? (
                <div>
                  {/* 精简的顶部提示 */}
                  <div style={{ 
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '12px',
                    marginBottom: '24px',
                    padding: '16px 24px',
                    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                    borderRadius: '12px',
                    color: 'white',
                    boxShadow: '0 4px 16px rgba(102, 126, 234, 0.25)'
                  }}>
                    <div style={{
                      width: '32px',
                      height: '32px',
                borderRadius: '50%',
                      background: 'rgba(255, 255, 255, 0.2)',
                      backdropFilter: 'blur(10px)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                      fontSize: '18px',
                      animation: 'pulse 2s infinite'
              }}>
                      ✨
              </div>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: '16px', fontWeight: 600, marginBottom: '2px' }}>
                        AI 正在为您生成大纲
                      </div>
                      <div style={{ fontSize: '13px', opacity: 0.9 }}>
                        请稍候，精彩的故事即将呈现...
                      </div>
                    </div>
                    <div style={{
                      width: '6px',
                      height: '6px',
                      borderRadius: '50%',
                      background: '#52c41a',
                      animation: 'blink 1.5s infinite',
                      boxShadow: '0 0 12px #52c41a'
                    }} />
                  </div>

                  {/* 大纲内容展示卡片 */}
              <Card
                style={{
                      borderRadius: '16px',
                      border: '1px solid #e2e8f0',
                      boxShadow: '0 4px 24px rgba(0, 0, 0, 0.06)',
                      overflow: 'hidden',
                      background: '#ffffff'
                    }}
                    bodyStyle={{ padding: 0 }}
                  >
                    {/* 卡片头部 */}
                    <div style={{
                      background: 'linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%)',
                      borderBottom: '2px solid #e2e8f0',
                      padding: '16px 24px',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '12px'
                    }}>
                      <div style={{
                        width: '36px',
                        height: '36px',
                        borderRadius: '10px',
                        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: '18px',
                        boxShadow: '0 2px 8px rgba(102, 126, 234, 0.3)'
                      }}>
                        📖
                      </div>
                      <div style={{ flex: 1 }}>
                        <div style={{ fontSize: '16px', fontWeight: 600, color: '#1e293b' }}>
                          故事大纲
                        </div>
                        <div style={{ fontSize: '12px', color: '#64748b', marginTop: '2px' }}>
                          AI 正在实时生成内容
                        </div>
                      </div>
                      <div style={{
                        padding: '4px 12px',
                        background: 'rgba(102, 126, 234, 0.1)',
                        borderRadius: '6px',
                        fontSize: '12px',
                        color: '#667eea',
                        fontWeight: 500
                      }}>
                        生成中
                      </div>
                    </div>

                    {/* 大纲内容区 */}
                    <div style={{
                      padding: '28px',
                      minHeight: '450px',
                      maxHeight: '650px',
                      overflowY: 'auto',
                      fontSize: '15px',
                      lineHeight: '1.9',
                      color: '#334155',
                      whiteSpace: 'pre-wrap',
                      fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Microsoft YaHei", sans-serif',
                      background: '#ffffff'
                    }}>
                      {(currentSuperOutline as any)?.plotStructure ? (
                        <div style={{ animation: 'fadeIn 0.3s ease-in' }}>
                          {(currentSuperOutline as any).plotStructure}
                        </div>
                      ) : (
                        <div style={{ 
                          textAlign: 'center', 
                          padding: '80px 20px',
                          color: '#94a3b8'
                        }}>
                          <div style={{ 
                            fontSize: '56px', 
                            marginBottom: '20px',
                            animation: 'pulse 2s infinite'
                          }}>
                            ⏳
                          </div>
                          <div style={{ 
                        fontSize: '16px',
                            fontWeight: 500,
                            color: '#64748b',
                            marginBottom: '8px'
                          }}>
                            AI 正在构思您的故事
                          </div>
                          <div style={{ 
                            fontSize: '13px',
                            color: '#94a3b8'
                          }}>
                            内容将实时显示在这里
                          </div>
                        </div>
                      )}
                    </div>
              </Card>
                </div>
              ) : (
                /* 等待生成状态 */
                <div style={{ textAlign: 'center', padding: '80px 20px' }}>
                  <div style={{
                    width: '120px',
                    height: '120px',
                    borderRadius: '50%',
                    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    margin: '0 auto 32px',
                    fontSize: '64px',
                    boxShadow: '0 12px 40px rgba(102, 126, 234, 0.3)'
                  }}>
                    🚀
                  </div>
                  <Title level={2} style={{ marginBottom: '16px', color: '#1f2937', fontSize: '32px' }}>
                    准备开始创作
                  </Title>
                  <Text style={{ fontSize: '16px', color: '#6b7280', display: 'block', marginBottom: '24px' }}>
                    请在弹窗中确认创作参数，AI 将为您生成完整的故事大纲
                  </Text>
                  <div style={{
                    padding: '20px 32px',
                    background: 'linear-gradient(135deg, #fef3c7 0%, #fde68a 100%)',
                    borderRadius: '12px',
                    border: '1px solid #fbbf24',
                    display: 'inline-block',
                    maxWidth: '600px',
                    fontSize: '14px',
                    color: '#92400e',
                    lineHeight: '1.6',
                    marginBottom: '24px'
                  }}>
                    💡 如果弹窗已关闭或生成失败，请点击下方按钮重新开始
                  </div>
                  <div>
                    <Button 
                      type="primary" 
                      size="large"
                      onClick={() => {
                        // 重置所有状态
                        setIsGeneratingOutline(false);
                        setCurrentSuperOutline(null);
                        setLoading(false);
                        setIsGenerating(false);
                        // 打开配置弹窗
                        setQuickStartVisible(true);
                        message.info('请重新配置参数');
                      }}
                      style={{
                        height: '48px',
                        fontSize: '16px',
                        borderRadius: '8px',
                        padding: '0 40px',
                        fontWeight: 500
                      }}
                    >
                      🔄 重新开始
                    </Button>
                  </div>
                </div>
              )}
            </div>
          ) : (
            /* 生成完成状态 - 使用和生成中一样的风格 */
            <div style={{ 
              maxWidth: '1000px', 
              margin: '0 auto',
              padding: '40px 20px'
            }}>
              {/* 成功提示横条 */}
              <div style={{ 
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '12px',
                marginBottom: '24px',
                padding: '16px 24px',
                background: 'linear-gradient(135deg, #52c41a 0%, #73d13d 100%)',
                  borderRadius: '12px',
                color: 'white',
                boxShadow: '0 4px 16px rgba(82, 196, 26, 0.25)'
              }}>
                <div style={{
                  width: '32px',
                  height: '32px',
                  borderRadius: '50%',
                  background: 'rgba(255, 255, 255, 0.2)',
                  backdropFilter: 'blur(10px)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: '18px'
                }}>
                  ✅
                </div>
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: '16px', fontWeight: 600, marginBottom: '2px' }}>
                    大纲生成完成
                  </div>
                  <div style={{ fontSize: '13px', opacity: 0.9 }}>
                    您可以查看大纲内容，或进行调整后确认
                  </div>
                </div>
              </div>

              {/* 大纲内容展示卡片 - 和生成中一样的风格 */}
                <Card
                  style={{
                  borderRadius: '16px',
                  border: '1px solid #e2e8f0',
                  boxShadow: '0 4px 24px rgba(0, 0, 0, 0.06)',
                  overflow: 'hidden',
                  background: '#ffffff',
                  marginBottom: '24px'
                }}
                bodyStyle={{ padding: 0 }}
              >
                {/* 卡片头部 */}
                <div style={{
                  background: 'linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%)',
                  borderBottom: '2px solid #e2e8f0',
                  padding: '16px 24px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px'
                }}>
                  <div style={{
                    width: '36px',
                    height: '36px',
                    borderRadius: '10px',
                    background: 'linear-gradient(135deg, #52c41a 0%, #73d13d 100%)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '18px',
                    boxShadow: '0 2px 8px rgba(82, 196, 26, 0.3)'
                  }}>
                    📖
                  </div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: '16px', fontWeight: 600, color: '#1e293b' }}>
                      故事大纲
                    </div>
                    <div style={{ fontSize: '12px', color: '#64748b', marginTop: '2px' }}>
                      AI 已为您生成完整大纲
                    </div>
                  </div>
                  <div style={{
                    padding: '4px 12px',
                    background: 'rgba(82, 196, 26, 0.1)',
                    borderRadius: '6px',
                    fontSize: '12px',
                    color: '#52c41a',
                    fontWeight: 500
                  }}>
                    已完成
                  </div>
                </div>

                {/* 大纲内容区 */}
                <div style={{
                  padding: '28px',
                  minHeight: '450px',
                  maxHeight: '650px',
                  overflowY: 'auto',
                  fontSize: '15px',
                  lineHeight: '1.9',
                  color: '#334155',
                  whiteSpace: 'pre-wrap',
                  fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Microsoft YaHei", sans-serif',
                  background: '#ffffff'
                }}>
                  {((currentSuperOutline as any)?.plotStructure) || '大纲内容加载中...'}
                </div>
                    </Card>

              {/* 操作按钮 */}
              <div style={{ textAlign: 'center' }}>
                <Space size="large">
                          <Button
                    icon={<ReloadOutlined />}
                    onClick={() => setOutlineGenerationVisible(true)}
                    size="large"
                    style={{
                      height: '48px',
                      padding: '0 28px',
                      borderRadius: '12px',
                      border: '1px solid #d9d9d9',
                      fontSize: '15px',
                      fontWeight: 500
                    }}
                  >
                    重新生成
                          </Button>
                          <Button
                            type="primary"
                    icon={<ArrowRightOutlined />}
                    onClick={confirmSuperOutline}
                    loading={isConfirmingOutline}
                    size="large"
                    style={{
                      height: '48px',
                      padding: '0 32px',
                      borderRadius: '12px',
                      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                      border: 'none',
                      boxShadow: '0 4px 15px rgba(102, 126, 234, 0.4)',
                      fontSize: '15px',
                      fontWeight: 500
                    }}
                  >
                    {isConfirmingOutline ? '正在确认...' : '确认大纲'}
                          </Button>
                        </Space>
              </div>
            </div>
          )}
                    </Card>
      )}

      {/* 第一步：生成卷规划（有大纲但没有卷） */}
      {currentStep === 1 && volumes.length === 0 && (
        <Card
          title={null}
          style={{
            marginBottom: 24,
            border: 'none',
            boxShadow: '0 4px 20px rgba(0,0,0,0.08)',
            borderRadius: '16px',
            overflow: 'hidden'
          }}
        >
            <div style={{
            maxWidth: '1000px', 
            margin: '0 auto',
            padding: '40px 20px'
          }}>
            {/* 生成中状态 */}
            {(isConfirmingOutline || (taskProgress && taskProgress.percentage > 0)) ? (
              <div>
                {/* 精简的顶部提示 */}
                <div style={{ 
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '12px',
                  marginBottom: '24px',
                  padding: '16px 24px',
              background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                  borderRadius: '12px',
                  color: 'white',
                  boxShadow: '0 4px 16px rgba(102, 126, 234, 0.25)'
                }}>
                  <div style={{
                    width: '32px',
                    height: '32px',
                    borderRadius: '50%',
                    background: 'rgba(255, 255, 255, 0.2)',
                    backdropFilter: 'blur(10px)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
                    fontSize: '18px',
                    animation: 'pulse 2s infinite'
            }}>
              📚
            </div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: '16px', fontWeight: 600, marginBottom: '2px' }}>
                      AI 正在为您生成卷规划
                    </div>
                    <div style={{ fontSize: '13px', opacity: 0.9 }}>
                      请稍候，正在智能规划章节结构...
                    </div>
                  </div>
                  <div style={{
                    width: '6px',
                    height: '6px',
                    borderRadius: '50%',
                    background: '#52c41a',
                    animation: 'blink 1.5s infinite',
                    boxShadow: '0 0 12px #52c41a'
                  }} />
                </div>

                {/* 进度卡片 */}
                <Card
                  style={{
                    borderRadius: '16px',
                    border: '1px solid #e2e8f0',
                    boxShadow: '0 4px 24px rgba(0, 0, 0, 0.06)',
                    overflow: 'hidden',
                    background: '#ffffff'
                  }}
                  bodyStyle={{ padding: 0 }}
                >
                  {/* 卡片头部 */}
                  <div style={{
                    background: 'linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%)',
                    borderBottom: '2px solid #e2e8f0',
                    padding: '16px 24px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '12px'
                  }}>
                    <div style={{
                      width: '36px',
                      height: '36px',
                      borderRadius: '10px',
                      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: '18px',
                      boxShadow: '0 2px 8px rgba(102, 126, 234, 0.3)'
                    }}>
                      📊
                    </div>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: '16px', fontWeight: 600, color: '#1e293b' }}>
                        生成进度
                      </div>
                      <div style={{ fontSize: '12px', color: '#64748b', marginTop: '2px' }}>
                        AI 正在实时生成卷规划
                      </div>
                    </div>
                    <div style={{
                      padding: '4px 12px',
                      background: 'rgba(102, 126, 234, 0.1)',
                      borderRadius: '6px',
                      fontSize: '12px',
                      color: '#667eea',
                      fontWeight: 500
                    }}>
                      生成中
                    </div>
                  </div>

                  {/* 进度内容区 */}
                  <div style={{
                    padding: '28px',
                    minHeight: '200px',
                    background: '#ffffff'
                  }}>
                    <Progress
                      percent={taskProgress?.percentage || 0}
                      status={(taskProgress?.percentage || 0) === 100 ? 'success' : 'active'}
                      strokeColor={{
                        '0%': '#667eea',
                        '100%': '#764ba2',
                      }}
                      strokeWidth={12}
                      style={{ marginBottom: 16 }}
                    />
                    <div style={{ 
                      textAlign: 'center',
                      fontSize: '15px',
                      color: '#334155',
                      fontWeight: 500
                    }}>
                      {taskProgress?.message || '正在分析大纲并生成卷规划...'}
                    </div>
                    
                    {/* 取消按钮 */}
                    <div style={{ textAlign: 'center', marginTop: '16px' }}>
                      <Button 
                        danger 
                        onClick={cancelVolumeGeneration}
                        style={{ 
                          borderRadius: '8px',
                          fontWeight: 500
                        }}
                      >
                        取消生成
                      </Button>
                    </div>
                    
                    {/* 提示信息 */}
                    <div style={{
                      marginTop: '24px',
                      padding: '16px',
                      background: '#fef3c7',
                      border: '1px solid #fde68a',
                      borderRadius: '8px',
                      fontSize: '13px',
                      color: '#92400e',
                      lineHeight: '1.6'
                    }}>
                      <strong>💡 温馨提示：</strong>卷规划生成中，AI 正在根据您确认的大纲智能划分章节结构，这可能需要1-2分钟，请耐心等待...
                  </div>
                  </div>
                </Card>
              </div>
            ) : (
              /* 等待开始状态 */
              <div style={{ textAlign: 'center', padding: '60px 20px' }}>
                <div style={{
                  width: '120px',
                  height: '120px',
                  borderRadius: '50%',
                  background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  margin: '0 auto 32px',
                  fontSize: '64px',
                  boxShadow: '0 12px 40px rgba(102, 126, 234, 0.3)'
                }}>
                  📚
                </div>
                <Title level={2} style={{ marginBottom: '16px', color: '#1f2937', fontSize: '32px' }}>
                  准备生成卷规划
                </Title>
                <Text style={{ fontSize: '16px', color: '#6b7280', display: 'block', marginBottom: '24px' }}>
                  AI 将根据您确认的大纲智能生成详细的卷规划
                </Text>
                
              <div style={{
                  marginTop: '32px',
                  padding: '20px',
                  background: '#f8fafc',
                borderRadius: '12px',
                  border: '1px solid #e2e8f0',
                  textAlign: 'left',
                  maxWidth: '600px',
                  margin: '32px auto 0'
                }}>
                  <div style={{ fontSize: '14px', color: '#475569', lineHeight: '1.8' }}>
                    <div style={{ marginBottom: '12px' }}>
                      <strong>📌 即将进行：</strong>
                    </div>
                    <div style={{ paddingLeft: '20px' }}>
                      • 分析大纲内容和结构<br/>
                      • 智能划分卷和章节<br/>
                      • 生成每卷的详细规划<br/>
                      • 确保情节连贯完整
                    </div>
                  </div>
                </div>
              </div>
            )}
          </div>
        </Card>
      )}

      {/* 第二步：卷规划 + 详细大纲 */}
      {currentStep === 1 && volumes.length > 0 && (
        <Card
          title={null}
          style={{
            marginBottom: 24,
            border: 'none',
            boxShadow: '0 4px 20px rgba(0,0,0,0.08)',
            borderRadius: '16px'
          }}
        >
                    <Alert
            message="卷规划已生成"
            description="现在可以为所有卷生成详细的剧情大纲，为写作做准备"
                      type="info"
                      showIcon
            style={{
              marginBottom: 24,
              borderRadius: '12px',
              border: '1px solid #91d5ff',
              background: '#f0f9ff'
            }}
          />

          {/* 批量生成总进度显示 */}
          {isGeneratingVolumeOutlines && (
            <Card
              style={{
                marginBottom: 24,
                borderRadius: '12px',
                border: '1px solid #1890ff',
                background: 'linear-gradient(135deg, #f0f9ff 0%, #e6f7ff 100%)'
              }}
            >
              <div style={{ textAlign: 'center' }}>
                <div style={{
                  fontSize: '18px',
                  fontWeight: 600,
                  color: '#1890ff',
                  marginBottom: 16,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '8px'
                }}>
                  <span style={{ fontSize: '24px' }}>🚀</span>
                  正在批量生成卷详细大纲
                </div>

                {/* 总进度条 */}
                <div style={{ marginBottom: 20 }}>
                  <Progress
                    percent={Math.round((Object.keys(volumeTasks).length / volumes.length) * 100)}
                    status="active"
                    strokeColor={{
                      '0%': '#1890ff',
                      '50%': '#40a9ff',
                      '100%': '#52c41a',
                    }}
                    strokeWidth={8}
                    style={{ marginBottom: 12 }}
                  />
                  <div style={{
                    fontSize: '16px',
                    fontWeight: 500,
                    color: '#1890ff',
                    marginBottom: 8
                  }}>
                    总进度：{Object.keys(volumeTasks).length} / {volumes.length} 个卷已完成
                  </div>
                  <div style={{ color: '#666', fontSize: '14px' }}>
                    {Object.keys(volumeTasks).length === volumes.length
                      ? '🎉 所有卷大纲生成完成！'
                      : `还有 ${volumes.length - Object.keys(volumeTasks).length} 个卷正在生成中...`
                    }
                  </div>
                </div>

                {/* 各卷详细进度 */}
                <div style={{
                  background: '#ffffff',
                  borderRadius: '8px',
                  padding: '16px',
                  border: '1px solid #e6f7ff'
                }}>
                  <div style={{
                    fontSize: '14px',
                    fontWeight: 500,
                    marginBottom: 12,
                    color: '#595959'
                  }}>
                    各卷生成状态：
                  </div>
                  <Row gutter={[8, 8]}>
                    {volumes.map((volume, index) => {
                      const isCompleted = volumeTasks[volume.id];
                      const isGenerating = generatingVolumeIds.has(volume.id);
                      return (
                        <Col span={6} key={volume.id}>
                          <div style={{
                            padding: '8px',
                            borderRadius: '6px',
                            textAlign: 'center',
                            fontSize: '12px',
                            background: isCompleted
                              ? '#f6ffed'
                              : isGenerating
                                ? '#fff7e6'
                                : '#f5f5f5',
                            border: `1px solid ${isCompleted
                              ? '#b7eb8f'
                              : isGenerating
                                ? '#ffd591'
                                : '#d9d9d9'}`,
                            color: isCompleted
                              ? '#52c41a'
                              : isGenerating
                                ? '#fa8c16'
                                : '#8c8c8c'
                          }}>
                            {isCompleted ? '✅' : isGenerating ? '⏳' : '⏸️'} 第{index + 1}卷
                          </div>
                        </Col>
                      );
                    })}
                  </Row>
                </div>
              </div>
            </Card>
          )}

          {/* 统一的用户建议输入 - 所有卷生成大纲后隐藏 */}
          {!volumes.every(v => v.contentOutline && v.contentOutline.length > 100) && (
          <Card
            size="small"
            style={{
              marginBottom: 24,
              background: '#ffffff',
              border: '1px solid #f0f0f0',
              borderRadius: '12px'
            }}
          >
                    <div>
              <Title level={5} style={{ marginBottom: 12 }}>
                💡 给所有卷的建议（可选）
              </Title>
              <Text style={{ display: 'block', marginBottom: 12, color: '#6b7280' }}>
                描述您希望如何改进卷的剧情，例如：加强角色冲突、增加悬念、调整节奏、突出主题等
                      </Text>
              <TextArea
                rows={4}
                placeholder="例如：希望每个卷都有明确的高潮转折，角色成长线要清晰，增加更多伏笔和悬念..."
                  value={volumes.length > 0 ? (volumeAdvices[volumes[0].id] || '') : ''}
                onChange={(e) => {
                  const advice = e.target.value;
                  const newAdvices: Record<string, string> = {};
                  volumes.forEach(volume => {
                    newAdvices[volume.id] = advice;
                  });
                  setVolumeAdvices(newAdvices);
                }}
                style={{
                  marginBottom: 12,
                  borderRadius: '8px'
                }}
                maxLength={500}
                showCount
                disabled={isGeneratingVolumeOutlines}
              />
              <div style={{ textAlign: 'right' }}>
                <Space size="middle">
                  <Button
                    type="primary"
                    icon={<RobotOutlined />}
                    loading={isGeneratingVolumeOutlines}
                    onClick={generateAllVolumeOutlinesWithAdvice}
                    disabled={volumes.every(v => v.contentOutline && v.contentOutline.length > 100) || isGeneratingVolumeOutlines}
                    size="middle"
                  >
                    {isGeneratingVolumeOutlines ? '生成中...' : '按建议生成所有卷大纲'}
                  </Button>
                  <Button
                    icon={<ReloadOutlined />}
                    loading={isGeneratingVolumeOutlines}
                    onClick={generateAllVolumeOutlinesWithoutAdvice}
                    disabled={volumes.every(v => v.contentOutline && v.contentOutline.length > 100) || isGeneratingVolumeOutlines}
                    size="middle"
                  >
                    {isGeneratingVolumeOutlines ? '生成中...' : '按原主题生成所有卷大纲'}
                    </Button>
                  </Space>
          </div>
            </div>
          </Card>
          )}

          {/* 卷列表 */}
          <div style={{ marginBottom: 24 }}>
            <Title level={5} style={{ marginBottom: 20, color: '#2c3e50' }}>
              📖 卷列表
            </Title>
            <Row gutter={[16, 16]}>
            {volumes.map((volume) => (
                <Col xs={24} sm={12} md={8} key={volume.id}>
                <Card
                  size="small"
                  hoverable
                  style={{
                    height: '100%',
                    border: '1px solid #edf2ff',
                    borderRadius: '14px',
                    background: 'linear-gradient(180deg,#ffffff 0%, #f6f7ff 100%)',
                    transition: 'all 0.25s ease',
                    boxShadow: '0 6px 16px rgba(102,126,234,0.10)',
                    cursor: 'pointer'
                   }}
                  bodyStyle={{ padding: '20px' }}
                >
                  <Card.Meta
                    title={
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
                        <span style={{ fontSize: '16px', fontWeight: 700, color: '#2c3e50', display: 'flex', alignItems: 'center', gap: 8 }}>
                          <span style={{
                            width: 28,
                            height: 28,
                            borderRadius: '50%',
                            background: 'linear-gradient(135deg,#667eea 0%, #764ba2 100%)',
                            color: '#fff',
                            display: 'inline-flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            fontSize: 14
                          }}>{volume.volumeNumber}</span>
                          <span style={{ fontSize: '16px', fontWeight: 700, color: '#2c3e50' }}>{volume.title}</span>
                        </span>
                        <Tag
                          color={volume.contentOutline && volume.contentOutline.length > 100 ? 'success' : 'warning'}
                          style={{ borderRadius: '12px', padding: '4px 12px' }}
                        >
                          {volume.contentOutline && volume.contentOutline.length > 100 ? '详细大纲已生成' : '需要生成详细大纲'}
                    </Tag>
                  </div>
                    }
                    description={
                      <div>
                        <div style={{ marginBottom: 12 }}>
                          <Text strong style={{ color: '#7f8c8d' }}>主题：</Text>
                          <Text style={{ color: '#2c3e50', marginLeft: 8 }}>{volume.theme}</Text>
                        </div>
                        <div style={{ marginBottom: 12 }}>
                          <Text strong style={{ color: '#7f8c8d' }}>描述：</Text>
                          <Text style={{ color: '#2c3e50', marginLeft: 8 }}>{volume.description}</Text>
                        </div>
                        <div style={{ marginBottom: 16 }}>
                          <Text strong style={{ color: '#7f8c8d' }}>字数：</Text>
                          <Text style={{ color: '#2c3e50', marginLeft: 8 }}>{volume.estimatedWordCount || 0} 字</Text>
                        </div>
                      </div>
                    }
                  />

                  {/* 每卷进度条（异步任务） */}
                  {volumeTasks[String(volume.id)] && (
                    <div style={{ marginTop: 12 }}>
                      <Progress
                        percent={Math.min(100, Math.max(0, volumeTasks[String(volume.id)].progress || 0))}
                        status={volumeTasks[String(volume.id)].status === 'FAILED' ? 'exception' : (volumeTasks[String(volume.id)].status === 'COMPLETED' ? 'success' : 'active')}
                      />
                      {volumeTasks[String(volume.id)].message && (
                        <Text type="secondary">{volumeTasks[String(volume.id)].message}</Text>
                      )}
                    </div>
                  )}

                  {/* 操作按钮 */}
                  <div style={{ textAlign: 'center', marginTop: 20 }}>
                    <Space size="small">
                          <Button
                            size="small"
                        icon={<EyeOutlined />}
                        onClick={() => handleViewDetails(volume)}
                        style={{
                          borderRadius: '8px',
                          border: '1px solid #3b82f6',
                          background: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)',
                          color: '#ffffff',
                          fontWeight: 500,
                          boxShadow: '0 2px 4px rgba(59, 130, 246, 0.2)'
                        }}
                      >
                        查看/修改
                          </Button>
                      {currentStep >= 2 && volume.contentOutline && volume.contentOutline.length > 100 && (
                          <Button
                            type="primary"
                            size="small"
                            icon={<PlayCircleOutlined />}
                          onClick={() => enterWriting(volume)}
                          style={{
                            borderRadius: '16px',
                            background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                            border: 'none',
                            boxShadow: '0 6px 14px rgba(102,126,234,0.35)'
                          }}
                        >
                          进入写作
                          </Button>
                        )}
                      </Space>
                  </div>
                </Card>
                    </Col>
              ))}
                  </Row>
          </div>

          {/* 开始创作按钮 */}
          <Divider />
          <div style={{ textAlign: 'center', padding: '16px 0' }}>
            <Button
              type="primary"
              size="large"
              icon={<EditOutlined />}
              onClick={startWritingFromFirstVolume}
              disabled={!(volumes.length > 0 && volumes.every(v => v.contentOutline && v.contentOutline.length > 100))}
              style={{
                height: '52px',
                padding: '0 40px',
                fontSize: '17px',
                fontWeight: 600,
                borderRadius: '26px',
                background: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)',
                border: 'none',
                boxShadow: '0 6px 20px rgba(59, 130, 246, 0.4)',
                transition: 'all 0.3s ease'
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.transform = 'translateY(-2px)';
                e.currentTarget.style.boxShadow = '0 8px 25px rgba(59, 130, 246, 0.5)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.transform = 'translateY(0)';
                e.currentTarget.style.boxShadow = '0 6px 20px rgba(59, 130, 246, 0.4)';
              }}
            >
              🚀 开始创作
                              </Button>
            <div style={{
              marginTop: '12px', 
              fontSize: '13px', 
              color: '#64748b',
              fontWeight: 400
            }}>
              将从第一卷开始，您可以在写作页面随时切换卷
            </div>
          </div>
      </Card>
      )}


      {/* 智能创作设置弹窗 */}
      <Modal
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <RobotOutlined style={{ color: '#1890ff' }} />
            配置AI创作工作室
          </div>
        }
        open={generateModalVisible}
        onCancel={() => setGenerateModalVisible(false)}
        onOk={() => generateForm.submit()}
        confirmLoading={loading}
        width={600}
      >
        <Form
          form={generateForm}
          layout="vertical"
          onFinish={handleGenerateVolumes}
          initialValues={{}}
        >
          <Form.Item
            name="basicIdea"
            label="基本创作构思"
            rules={[{ required: true, message: '请输入创作构思' }]}
          >
            <TextArea
              rows={6}
              placeholder="请详细描述您的小说构思，包括故事背景、主要角色、核心冲突、独特设定等...&#10;&#10;描述越详细，生成的卷规划越精准！"
              showCount
              maxLength={2000}
            />
          </Form.Item>

          {/* 移除启用超级大纲模式勾选 */}

          {/* 移除模式说明提示，统一为流式大纲生成 */}

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="targetChapters"
                label="目标章数"
                initialValue={500}
              >
                <InputNumber
                  min={50}
                  max={1000}
                  style={{ width: '100%' }}
                  addonAfter="章"
                  onChange={(value) => {
                    const wordsPerChapter = generateForm.getFieldValue('wordsPerChapter') || 3000;
                    const total = (value || 500) * wordsPerChapter;
                    setTotalWordsGenerate(total);
                    generateForm.setFieldValue('targetWords', total);
                  }}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="wordsPerChapter"
                label="每章字数"
                initialValue={3000}
              >
                <InputNumber
                  min={2000}
                  max={10000}
                  style={{ width: '100%' }}
                  addonAfter="字/章"
                  onChange={(value) => {
                    const chapters = generateForm.getFieldValue('targetChapters') || 500;
                    const total = (value || 3000) * chapters;
                    setTotalWordsGenerate(total);
                    generateForm.setFieldValue('targetWords', total);
                  }}
                />
              </Form.Item>
            </Col>
          </Row>

          {/* 显示计算出的总字数 */}
          <div style={{
            marginBottom: '16px',
            padding: '10px 12px',
            background: '#f0f9ff',
            borderRadius: '6px',
            border: '1px solid #bfdbfe',
            fontSize: '13px',
            color: '#1e40af',
          }}>
            <strong>📊 预计总字数：</strong>
            <span style={{ fontSize: '16px', fontWeight: 600, color: '#2563eb', marginLeft: '8px' }}>
              {(totalWordsGenerate / 10000).toFixed(1)}
            </span>
            <span> 万字</span>
            <span style={{ marginLeft: '8px', fontSize: '12px', color: '#3b82f6' }}>
              ({totalWordsGenerate.toLocaleString()}字)
            </span>
          </div>

          {/* 隐藏的总字数字段 */}
          <Form.Item name="targetWords" hidden initialValue={1500000}>
            <InputNumber />
          </Form.Item>

          <Form.Item
            name="volumeCount"
            label="预期卷数"
            rules={[{ required: true, message: '请选择卷数' }]}
            initialValue={5}
          >
            <InputNumber
              min={1}
              max={10}
              style={{ width: '100%' }}
                placeholder="建议5卷"
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 快速开始模态框 - 配置创作参数 */}
      <Modal
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div style={{
              width: '40px',
              height: '40px',
              borderRadius: '50%',
              background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '20px'
            }}>
              🚀
            </div>
            <div>
              <div style={{ fontSize: '18px', fontWeight: 600, color: '#1f2937' }}>开始您的创作之旅</div>
              <div style={{ fontSize: '13px', fontWeight: 400, color: '#6b7280', marginTop: '2px' }}>AI 将根据您的构思生成完整大纲</div>
            </div>
          </div>
        }
        open={quickStartVisible}
        onCancel={() => {
          setQuickStartVisible(false);
          // 如果正在生成中被取消，需要重置状态
          if (isGeneratingOutline) {
            setIsGeneratingOutline(false);
            setLoading(false);
            setIsGenerating(false);
            message.info('已取消大纲生成');
          }
        }}
        onOk={() => outlineForm.submit()}
        confirmLoading={isGeneratingOutline}
        okText={isGeneratingOutline ? '正在生成...' : '确认并生成大纲'}
        cancelText="取消"
        width={680}
        okButtonProps={{
          size: 'large',
          style: {
            background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
            border: 'none',
            height: '42px',
            fontSize: '15px',
            fontWeight: 500
          }
        }}
        cancelButtonProps={{
          size: 'large',
          style: { height: '42px' }
        }}
      >
        <div style={{ padding: '8px 0 16px' }}>
          <Alert
            message={
              <span style={{ fontSize: '14px', fontWeight: 500 }}>
                💡 设置您的创作目标
              </span>
            }
            description={
              <span style={{ fontSize: '13px' }}>
                请确认以下参数，AI将据此生成大纲和卷规划。您可以使用默认值，也可以根据需要调整。
              </span>
            }
            type="info"
            showIcon={false}
            style={{
              marginBottom: '24px',
              borderRadius: '8px',
              background: 'linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%)',
              border: '1px solid #bfdbfe'
            }}
          />

          <Form
            form={outlineForm}
            layout="vertical"
            onFinish={(values) => {
              handleGenerateVolumes(values);
                  setQuickStartVisible(false);
            }}
          >
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item
                  name="targetChapters"
                  label={<span style={{ fontSize: '14px', fontWeight: 500 }}>目标章数</span>}
                >
                  <InputNumber
                    min={50}
                    max={1000}
                    style={{ width: '100%', fontSize: '15px' }}
                    addonAfter="章"
                    size="large"
                    onChange={(value) => {
                      // 自动计算总字数
                      const wordsPerChapter = outlineForm.getFieldValue('wordsPerChapter') || 3000;
                      const total = (value || 500) * wordsPerChapter;
                      setTotalWords(total);
                      outlineForm.setFieldValue('targetWords', total);
                    }}
                  />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  name="wordsPerChapter"
                  label={<span style={{ fontSize: '14px', fontWeight: 500 }}>每章字数</span>}
                  initialValue={3000}
                >
                  <InputNumber
                    min={2000}
                    max={10000}
                    style={{ width: '100%', fontSize: '15px' }}
                    addonAfter="字/章"
                    size="large"
                    onChange={(value) => {
                      // 自动计算总字数
                      const chapters = outlineForm.getFieldValue('targetChapters') || 500;
                      const total = (value || 3000) * chapters;
                      setTotalWords(total);
                      outlineForm.setFieldValue('targetWords', total);
                    }}
                  />
                </Form.Item>
              </Col>
            </Row>

            {/* 显示计算出的总字数 */}
            <div style={{
              marginTop: '-8px',
              marginBottom: '16px',
              padding: '12px 16px',
              background: 'linear-gradient(135deg, #e0e7ff 0%, #f0f4ff 100%)',
              borderRadius: '8px',
              border: '1px solid #c7d2fe',
              fontSize: '14px',
              color: '#4338ca',
              display: 'flex',
              alignItems: 'center',
              gap: '8px'
            }}>
              <span style={{ fontWeight: 600 }}>📊 预计总字数：</span>
              <span style={{ fontSize: '18px', fontWeight: 700, color: '#4f46e5' }}>
                {(totalWords / 10000).toFixed(1)}
              </span>
              <span style={{ fontWeight: 600 }}>万字</span>
              <span style={{ marginLeft: '8px', fontSize: '12px', color: '#6366f1' }}>
                ({totalWords.toLocaleString()}字)
              </span>
            </div>

            {/* 隐藏的总字数字段，用于提交 */}
            <Form.Item name="targetWords" hidden initialValue={1500000}>
              <InputNumber />
            </Form.Item>

            <Form.Item
              name="volumeCount"
              label={<span style={{ fontSize: '14px', fontWeight: 500 }}>预期卷数</span>}
            >
              <InputNumber
                min={1}
                max={10}
                style={{ width: '100%', fontSize: '15px' }}
                placeholder="建议 5 卷"
                size="large"
                addonAfter="卷"
              />
            </Form.Item>

            {/* 隐藏的构思字段，从创建页面自动填充 */}
            <Form.Item name="basicIdea" hidden>
              <Input />
            </Form.Item>
          </Form>

          <div style={{
            marginTop: '16px',
            padding: '12px 16px',
            background: '#fef3c7',
            border: '1px solid #fde68a',
            borderRadius: '8px',
            fontSize: '13px',
            color: '#92400e',
            lineHeight: '1.6'
          }}>
            <strong>📌 提示：</strong>这些参数将影响 AI 生成的大纲结构和卷规划，您可以在后续流程中进一步调整具体内容。
          </div>
        </div>
      </Modal>

      {/* 卷详情弹窗 - 全新设计 */}
      <Modal
        open={detailModalVisible}
        onCancel={() => {
          setDetailModalVisible(false);
          setSingleVolumeAdvice('');
          setStreamingVolumeOutline('');
          setAdviceInputVisible(false);
        }}
        footer={null}
        width={900}
        centered
        styles={{
          body: { padding: 0, maxHeight: '80vh', overflowY: 'auto' }
        }}
        closeIcon={<span style={{ color: '#64748b', fontSize: '18px' }}>✕</span>}
      >
        {selectedVolume && (
          <div>
            {/* 头部区域 - 精简现代 */}
            <div style={{
              background: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)',
              padding: '32px',
              borderRadius: '16px 16px 0 0'
            }}>
              <div style={{
                display: 'flex',
                alignItems: 'flex-start',
                justifyContent: 'space-between',
                marginBottom: '20px'
              }}>
                <div>
                  <div style={{
                    fontSize: '12px',
                    color: 'rgba(255, 255, 255, 0.8)',
                    marginBottom: '6px',
                    fontWeight: 500,
                    letterSpacing: '0.5px'
                  }}>
                    VOL.{selectedVolume.volumeNumber}
                  </div>
                  <div style={{
                    fontSize: '26px',
                    fontWeight: 700,
                    color: '#ffffff',
                    marginBottom: '8px',
                    lineHeight: 1.3
                  }}>
                    {selectedVolume.title}
                  </div>
                  <div style={{
                    fontSize: '14px',
                    color: 'rgba(255, 255, 255, 0.9)',
                    fontWeight: 400
                  }}>
                    {selectedVolume.theme}
                  </div>
                </div>
                <Tag
                  color={novelVolumeService.getStatusColor(selectedVolume.status)}
                  style={{
                    margin: 0,
                    padding: '6px 14px',
                    fontSize: '12px',
                    fontWeight: 600,
                    borderRadius: '20px',
                    border: 'none'
                  }}
                >
                  {novelVolumeService.getStatusText(selectedVolume.status)}
                </Tag>
              </div>
              
              {/* 元信息 */}
              <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap' }}>
                <div style={{
                  background: 'rgba(255, 255, 255, 0.15)',
                  backdropFilter: 'blur(10px)',
                  padding: '8px 16px',
                  borderRadius: '10px',
                  fontSize: '13px',
                  color: 'white',
                  fontWeight: 500,
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px'
                }}>
                  <span style={{ opacity: 0.9 }}>📖</span>
                  <span>第{selectedVolume.chapterStart}-{selectedVolume.chapterEnd}章</span>
                </div>
                <div style={{
                  background: 'rgba(255, 255, 255, 0.15)',
                  backdropFilter: 'blur(10px)',
                  padding: '8px 16px',
                  borderRadius: '10px',
                  fontSize: '13px',
                  color: 'white',
                  fontWeight: 500,
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px'
                }}>
                  <span style={{ opacity: 0.9 }}>✍️</span>
                  <span>约{selectedVolume.estimatedWordCount}字</span>
                </div>
              </div>
            </div>

            {/* 内容区域 */}
            <div style={{ padding: '24px', background: '#f8fafc' }}>
              {/* 卷描述 */}
              <div style={{
                background: '#ffffff',
                borderRadius: '12px',
                padding: '20px',
                marginBottom: '16px',
                border: '1px solid #e2e8f0',
                boxShadow: '0 1px 3px rgba(0, 0, 0, 0.05)'
              }}>
                <div style={{
                  fontSize: '14px',
                  fontWeight: 600,
                  color: '#475569',
                  marginBottom: '12px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px'
                }}>
                  <span style={{ fontSize: '16px' }}>📄</span>
                  卷描述
                </div>
                <div style={{
                  fontSize: '14px',
                  lineHeight: 1.8,
                  color: '#334155'
                }}>
                  {selectedVolume.description}
                </div>
              </div>

              {/* 大纲内容区 */}
              <div style={{
                background: '#ffffff',
                borderRadius: '12px',
                border: '1px solid #e2e8f0',
                boxShadow: '0 1px 3px rgba(0, 0, 0, 0.05)',
                marginBottom: '16px',
                overflow: 'hidden'
              }}>
                <div style={{
                  padding: '20px',
                  borderBottom: '1px solid #e2e8f0',
                  background: '#f8fafc'
                }}>
                  <div style={{
                    fontSize: '14px',
                    fontWeight: 600,
                    color: '#475569',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px'
                  }}>
                    <span style={{ fontSize: '16px' }}>📖</span>
                    内容大纲
                    {isGeneratingSingleVolume && (
                      <Tag color="processing" style={{ marginLeft: '8px' }}>生成中...</Tag>
                    )}
                  </div>
                </div>

                <div style={{ padding: '20px' }}>
                  {/* 显示流式生成内容（优先） */}
                  {isGeneratingSingleVolume ? (
                    <div style={{
                      fontSize: '14px',
                      lineHeight: 1.9,
                      color: '#334155',
                      whiteSpace: 'pre-wrap',
                      background: '#f1f5f9',
                      padding: '16px',
                      borderRadius: '8px',
                      maxHeight: '500px',
                      overflowY: 'auto',
                      fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "Helvetica Neue", Helvetica, Arial, sans-serif',
                      border: '1px dashed #cbd5e1'
                    }}>
                      {streamingVolumeOutline || '正在生成...'}
                      <span style={{
                        display: 'inline-block',
                        width: '8px',
                        height: '16px',
                        background: '#3b82f6',
                        marginLeft: '2px',
                        animation: 'blink 1s infinite'
                      }} />
                    </div>
                  ) : streamingVolumeOutline ? (
                    <div style={{
                      fontSize: '14px',
                      lineHeight: 1.9,
                      color: '#334155',
                      whiteSpace: 'pre-wrap',
                      background: '#f8fafc',
                      padding: '16px',
                      borderRadius: '8px',
                      maxHeight: '500px',
                      overflowY: 'auto',
                      fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "Helvetica Neue", Helvetica, Arial, sans-serif'
                    }}>
                      {streamingVolumeOutline}
                    </div>
                  ) : (selectedVolume.contentOutline && selectedVolume.contentOutline.length > 100) ? (
                    <div style={{
                      fontSize: '14px',
                      lineHeight: 1.9,
                      color: '#334155',
                      whiteSpace: 'pre-wrap',
                      background: '#f8fafc',
                      padding: '16px',
                      borderRadius: '8px',
                      maxHeight: '500px',
                      overflowY: 'auto',
                      fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "Helvetica Neue", Helvetica, Arial, sans-serif'
                    }}>
                      {selectedVolume.contentOutline}
                    </div>
                  ) : (
                    <div style={{
                      padding: '60px 20px',
                      textAlign: 'center',
                      background: '#f8fafc',
                      borderRadius: '8px',
                      border: '1px dashed #cbd5e1'
                    }}>
                      <div style={{ fontSize: '48px', marginBottom: '16px', opacity: 0.6 }}>📝</div>
                      <div style={{ fontSize: '14px', color: '#64748b', marginBottom: '8px' }}>暂无详细大纲</div>
                      <div style={{ fontSize: '12px', color: '#94a3b8' }}>点击下方按钮生成卷大纲</div>
                    </div>
                  )}
                </div>
              </div>

              {/* 操作区 */}
              <div style={{
                background: '#ffffff',
                borderRadius: '12px',
                padding: '20px',
                border: '1px solid #e2e8f0',
                boxShadow: '0 1px 3px rgba(0, 0, 0, 0.05)'
              }}>
                {/* 标题 */}
                <div style={{
                  fontSize: '14px',
                  fontWeight: 600,
                  color: '#475569',
                  marginBottom: '16px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px'
                }}>
                  <span style={{ fontSize: '16px' }}>🤖</span>
                  AI 大纲生成
                </div>

                {/* 建议输入（可收起） */}
                {adviceInputVisible && (
                  <div style={{
                    marginBottom: '16px',
                    padding: '16px',
                    background: '#fef3c7',
                    borderRadius: '8px',
                    border: '1px solid #fde68a'
                  }}>
                    <div style={{
                      fontSize: '13px',
                      color: '#92400e',
                      marginBottom: '8px',
                      fontWeight: 500
                    }}>
                      💡 给出您的建议（可选）
                    </div>
                    <TextArea
                    rows={3}
                      placeholder="例如：强调人物成长线、在本卷引入关键反派、提升冲突密度..."
                      value={singleVolumeAdvice}
                      onChange={(e) => setSingleVolumeAdvice(e.target.value)}
                      style={{
                        borderRadius: '6px',
                        background: '#ffffff',
                        fontSize: '13px'
                      }}
                      maxLength={500}
                      showCount
                      disabled={isGeneratingSingleVolume}
                    />
                  </div>
                )}

                {/* 按钮组 */}
                <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
                  {!adviceInputVisible && (
                    <Button
                      onClick={() => setAdviceInputVisible(true)}
                      style={{
                        flex: '0 0 auto',
                        height: '40px',
                        borderRadius: '8px',
                        fontSize: '14px',
                        fontWeight: 500
                      }}
                      icon={<BulbOutlined />}
                    >
                      添加建议
                  </Button>
                  )}
                  <Button
                    type="primary"
                    icon={<RobotOutlined />}
                    loading={isGeneratingSingleVolume}
                    onClick={handleGenerateSingleVolumeOutlineStream}
                    style={{
                      flex: 1,
                      height: '40px',
                      borderRadius: '8px',
                      background: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)',
                      border: 'none',
                      fontSize: '14px',
                      fontWeight: 600
                    }}
                  >
                    {isGeneratingSingleVolume ? '生成中...' : (selectedVolume.contentOutline && selectedVolume.contentOutline.length > 100) ? '重新生成' : '生成大纲'}
                  </Button>
                </div>
              </div>
            </div>
          </div>
        )}
      </Modal>

      {/* 浮动操作按钮 */}
      {volumes.length > 0 && (
        <FloatButton.Group
          shape="circle"
          style={{ right: 24, bottom: 24 }}
        >
          <FloatButton
            icon={<SettingOutlined />}
            tooltip="设置"
            onClick={() => {
              // 可以添加设置功能
              notification.info({
                message: '设置功能',
                description: '敬请期待更多设置选项',
                placement: 'topRight'
              });
            }}
          />
          <FloatButton
            icon={<BarChartOutlined />}
            tooltip="统计报告"
            onClick={loadVolumeStats}
          />
          <FloatButton
            type="primary"
            icon={<PlusOutlined />}
            tooltip="添加新卷"
            onClick={() => setGenerateModalVisible(true)}
          />
        </FloatButton.Group>
      )}

      {/* 重新生成大纲弹窗 */}
      <Modal
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <ReloadOutlined style={{ color: '#1890ff' }} />
            重新生成大纲
          </div>
        }
        open={outlineGenerationVisible}
        onCancel={() => setOutlineGenerationVisible(false)}
        onOk={regenerateSuperOutline}
        confirmLoading={isGeneratingOutline}
        width={600}
      >
        <div style={{ padding: '20px 0' }}>
          <Alert
            message="💡 重新生成大纲"
            description="请描述您希望如何改进当前的大纲，AI将根据您的建议重新生成"
            type="info"
            showIcon
            style={{ marginBottom: 24 }}
          />

          <Form layout="vertical">
            <Form.Item label="用户建议" required>
              <TextArea
                rows={6}
                placeholder="请详细描述您希望如何改进大纲，例如：加强某个角色的戏份、增加更多冲突、调整故事节奏等..."
                value={outlineUserAdvice}
                onChange={(e) => setOutlineUserAdvice(e.target.value)}
                showCount
                maxLength={1000}
              />
            </Form.Item>
          </Form>

          <Alert
            message="注意事项"
            description="重新生成大纲会覆盖原有内容，请确保您的建议清晰明确"
            type="warning"
            showIcon
          />
        </div>
      </Modal>

      {/* 快速开始区域 - 仅在无卷时展示 */}
      {/* 已移除底部重复的"开始您的创作之旅"块 */}
    </div>
  );
};

export default VolumeManagementPage;






