import React, { useState, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import {
  Card, Button, Form, Input, InputNumber, Select, Typography, Space,
  Modal, Tag, Progress, Divider,
  Alert, Row, Col,
  FloatButton, notification, App as AntdApp, Tooltip
} from 'antd';
import {
  BookOutlined, PlusOutlined, EditOutlined,
  RobotOutlined,
  BarChartOutlined, BulbOutlined, SettingOutlined, ArrowRightOutlined,
  CheckCircleOutlined, ClockCircleOutlined, ExclamationCircleOutlined,
  ReloadOutlined, PlayCircleOutlined, EyeOutlined, InfoCircleOutlined, DownOutlined, StarFilled
} from '@ant-design/icons';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import novelVolumeService, { NovelVolume } from '../services/novelVolumeService';
import novelService, { Novel as NovelModel } from '../services/novelService';
import novelOutlineService, { NovelOutline as OutlineModel } from '../services/novelOutlineService';
import { aiTaskService } from '../services/aiTaskService';
import api from '../services/api';
import { checkAIConfig, withAIConfig, AI_CONFIG_ERROR_MESSAGE } from '../utils/aiRequest';
import { formatAIErrorMessage } from '../utils/errorHandler';
import './VolumeManagementPage.css';
import './VolumeManagementPage.apple.css';

const { Title, Text } = Typography;
const { TextArea } = Input;

interface LocationState {
  initialIdea?: string;
  autoGenerate?: boolean;
}

interface OutlineTemplateSelectProps {
  value?: number;
  onChange: (value: number | undefined) => void;
  options: any[];
  loading?: boolean;
}

const OutlineTemplateSelect: React.FC<OutlineTemplateSelectProps> = ({ value, onChange, options, loading }) => {
  const [isOpen, setIsOpen] = useState(false);
  const [position, setPosition] = useState({ top: 0, left: 0, width: 0 });
  const triggerRef = useRef<HTMLDivElement>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const updatePosition = () => {
      if (triggerRef.current && isOpen) {
        const rect = triggerRef.current.getBoundingClientRect();
        setPosition({
          top: rect.bottom + 8,
          left: rect.left,
          width: rect.width
        });
      }
    };

    updatePosition();
    window.addEventListener('resize', updatePosition);
    window.addEventListener('scroll', updatePosition, true);

    const handleClickOutside = (e: MouseEvent) => {
      if (
        triggerRef.current && 
        !triggerRef.current.contains(e.target as Node) &&
        dropdownRef.current &&
        !dropdownRef.current.contains(e.target as Node)
      ) {
        setIsOpen(false);
      }
    };

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }

    return () => {
      window.removeEventListener('resize', updatePosition);
      window.removeEventListener('scroll', updatePosition, true);
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isOpen]);

  const handleToggle = () => {
    if (!loading) {
      if (!isOpen && triggerRef.current) {
        const rect = triggerRef.current.getBoundingClientRect();
        setPosition({
          top: rect.bottom + 8,
          left: rect.left,
          width: rect.width
        });
      }
      setIsOpen(!isOpen);
    }
  };

  const selectedOption = options.find(o => o.id === value);

  return (
    <div className="apple-select">
      <div 
        ref={triggerRef}
        className={`apple-select-trigger ${isOpen ? 'open' : ''}`}
        onClick={handleToggle}
      >
        {selectedOption ? (
          <span className="apple-select-value">{selectedOption.name}</span>
        ) : (
          <span className="apple-select-placeholder">
            {loading ? '加载中...' : '默认使用系统模板'}
          </span>
        )}
        <DownOutlined className="apple-select-arrow" />
      </div>

      {isOpen && createPortal(
        <div 
          ref={dropdownRef}
          className="apple-select-dropdown-portal"
          style={{ 
            top: position.top, 
            left: position.left, 
            width: position.width 
          }}
        >
          <div 
            className={`apple-select-option ${!value ? 'selected' : ''}`}
            onClick={() => {
              onChange(undefined);
              setIsOpen(false);
            }}
          >
             <span className="apple-select-option-name">默认使用系统模板</span>
          </div>
          {options.map((option) => (
            <div
              key={option.id}
              className={`apple-select-option ${value === option.id ? 'selected' : ''}`}
              onClick={() => {
                onChange(option.id);
                setIsOpen(false);
              }}
            >
              {option.isDefault && <StarFilled className="apple-select-option-star" />}
              <span className="apple-select-option-name">{option.name}</span>
              {option.description && (
                <span className="apple-select-option-desc">{option.description}</span>
              )}
            </div>
          ))}
        </div>,
        document.body
      )}
    </div>
  );
};

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
  const [isEditingOutline, setIsEditingOutline] = useState(false);
  const [editedOutlineContent, setEditedOutlineContent] = useState('');

  // 卷操作状态
  const [volumeAdvices, setVolumeAdvices] = useState<Record<string, string>>({});
  const [isGeneratingVolumeOutlines, setIsGeneratingVolumeOutlines] = useState(false);
  const [generatingVolumeIds, setGeneratingVolumeIds] = useState<Set<string>>(new Set());
  // 批量异步任务：每卷任务进度
  const [volumeTasks, setVolumeTasks] = useState<Record<string, { taskId: number, progress: number, status: string, message?: string }>>({});
  const [taskStops, setTaskStops] = useState<Record<string, () => void>>({});
  
  // 重新生成卷状态
  const [isRegeneratingVolumes, setIsRegeneratingVolumes] = useState(false);
  const [regenerateProgress, setRegenerateProgress] = useState<{ percentage: number, message: string } | null>(null);
  const [fakeProgress, setFakeProgress] = useState(0); // 假进度条

  // 弹窗状态
  const [generateModalVisible, setGenerateModalVisible] = useState(false);
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [selectedVolume, setSelectedVolume] = useState<NovelVolume | null>(null);
  const [quickStartVisible, setQuickStartVisible] = useState(false);
  const [outlineGenerationVisible, setOutlineGenerationVisible] = useState(false);

  const [generateForm] = Form.useForm();
  const [hasShownQuickStart, setHasShownQuickStart] = useState(false); // 标记是否已显示过弹窗

  // 总字数动态计算状态
  const [totalWords, setTotalWords] = useState(600000); // 默认 300章 × 2000字 (快速开始弹窗)
  const [totalWordsGenerate, setTotalWordsGenerate] = useState(600000); // 默认 300章 × 2000字 (生成大纲弹窗)

  // 模板选择状态
  const [outlineTemplates, setOutlineTemplates] = useState<any[]>([]);
  const [loadingTemplates, setLoadingTemplates] = useState(false);
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | undefined>(undefined);

  const { novelId } = useParams<{ novelId: string }>();
  const navigate = useNavigate();
  const location = useLocation();





  useEffect(() => {
    if (novelId) {
      // 初始化状态恢复
      const initializeState = async () => {
        console.log('[初始化] 开始恢复状态...');

        // 先加载数据
        await Promise.all([
          loadNovelInfo(),
          loadVolumes(),
          loadVolumeStats(),
          checkSuperOutline(),
          loadSuperOutlines()
        ]);

        console.log('[初始化] 数据加载完成，开始恢复步骤状态');

        // 优先从后端获取状态
        const backendStep = await fetchCreationStageFromBackend();
        if (backendStep !== null) {
          console.log('[初始化] 使用后端状态:', backendStep);
          setCurrentStep(backendStep);
          saveCreationState(backendStep);
        } else {
          // 后端获取失败时，尝试从本地存储恢复
          const restoredStep = restoreCreationState();
          console.log('[初始化] 使用本地存储状态:', restoredStep);
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

        // 恢复任务
        recoverTasks();
      };

      initializeState();
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
      console.log('[自动弹窗] 从创建页面跳转，弹出配置弹窗');
      
      // 填充表单
      outlineForm.setFieldsValue({
        basicIdea: state.initialIdea,
        targetChapters: 300,
        wordsPerChapter: 2000,
        targetWords: 300 * 2000, // 自动计算：300章 × 2000字/章 = 600000字
        volumeCount: 5
      });
      
      // 显示配置弹窗
      setQuickStartVisible(true);
      loadOutlineTemplates();
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
      console.log('开始加载小说信息, novelId:', novelId);
      const novelData = await novelService.getById(novelId);
      console.log('小说信息加载成功:', novelData);
      setNovel(novelData);
    } catch (error: any) {
      console.error('加载小说信息失败:', error);
      message.error(error?.message || '加载小说信息失败');
    }
  };

  const loadVolumes = async () => {
    if (!novelId) return;

    setLoading(true);
    try {
      const volumesList = await novelVolumeService.getVolumesByNovelId(novelId);
      console.log('[loadVolumes] 成功加载卷列表，数量:', volumesList.length);
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

      console.log('卷加载完成，等待小说数据和大纲数据后统一更新步骤');

      // 注意：不在这里自动切换步骤，由 updateProcessStep 统一处理
      // 避免与状态恢复逻辑冲突
    } catch (error: any) {
      console.error('加载卷列表失败:', error);
      message.error(error?.message || '加载卷列表失败');
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

    console.log('checkSuperOutline 被调用，novelId:', novelId);

    try {
      const outline = await novelOutlineService.getOutlineByNovelId(novelId);
      console.log('获取到的大纲:', outline);

      // 检查大纲内容：优先检查 plotStructure，然后检查 outline 字段
      const outlineContent = (outline as any)?.plotStructure || (outline as any)?.outline;
      const hasOutline = !!(outline && outlineContent && outlineContent.trim());
      
      console.log('大纲内容检查:', {
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
          console.log('已检测到已确认的大纲');
        } else {
          setConfirmedSuperOutline(null);
          console.log('已检测到草稿大纲（未确认），用户可以查看、重新生成或确认');
        }
      } else {
        console.log('未找到大纲');
        setConfirmedSuperOutline(null);
        setHasSuperOutline(false);
        setCurrentSuperOutline(null);
      }
    } catch (error: any) {
      console.error('检查超级大纲状态失败:', error);
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





  // 开始编辑大纲
  const startEditingOutline = () => {
    const outlineContent = (currentSuperOutline as any)?.plotStructure || (currentSuperOutline as any)?.outline || '';
    setEditedOutlineContent(outlineContent);
    setIsEditingOutline(true);
  };

  // 取消编辑大纲
  const cancelEditingOutline = () => {
    setIsEditingOutline(false);
    setEditedOutlineContent('');
  };

  // 保存编辑后的大纲
  const saveEditedOutline = async () => {
    if (!editedOutlineContent || !editedOutlineContent.trim()) {
      message.warning('大纲内容不能为空');
      return;
    }

    try {
      setIsConfirmingOutline(true);
      
      // 保存到数据库
      await novelOutlineService.updateOutline(novelId!, editedOutlineContent);
      
      // 更新当前大纲对象
      setCurrentSuperOutline({
        ...currentSuperOutline,
        plotStructure: editedOutlineContent,
        outline: editedOutlineContent
      } as any);
      
      setIsEditingOutline(false);
      setEditedOutlineContent('');
      message.success('大纲保存成功！');
      
      // 重新加载大纲
      await loadSuperOutlines();
    } catch (error: any) {
      console.error('保存大纲失败:', error);
      message.error(error?.message || '保存大纲失败');
    } finally {
      setIsConfirmingOutline(false);
    }
  };

  // 确认大纲并生成卷规划（前端轮询卷列表直到出现）
  const confirmSuperOutline = async () => {
    // 立即滚动到顶部
    window.scrollTo({ top: 0, behavior: 'smooth' });

    console.log('confirmSuperOutline 被调用');
    console.log('currentSuperOutline:', currentSuperOutline);
    console.log('novelId:', novelId);

    if (!currentSuperOutline) {
      console.error('currentSuperOutline 为 null，无法确认大纲');
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
    // 立即显示进度条，让用户知道任务已开始
    setTaskProgress({ percentage: 0, message: '正在确认大纲...' });

    try {
      // 1) 直接将当前大纲内容写入 novels.outline
      const outlineText = (currentSuperOutline as any).plotStructure || (currentSuperOutline as any).outline || '';
      if (!outlineText || !String(outlineText).trim()) {
        message.warning('大纲内容为空，请先生成或编辑大纲');
        // 清理状态后返回
        setTaskProgress(null);
        setIsConfirmingOutline(false);
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
            volumeCount = 7; // 中等大纲分7卷
          } else if (outlineLength > 2000) {
            volumeCount = 5; // 标准大纲分5卷
          } else {
            volumeCount = 3; // 短大纲分3卷
          }
          console.log('[confirmSuperOutline] 根据大纲长度估算卷数，大纲长度=', outlineLength, ', volumeCount=', volumeCount);
        }

        await api.post(`/volumes/${novelId}/generate-from-outline`, withAIConfig({ volumeCount }));
        message.success(`大纲确认成功，已触发卷规划生成（约${volumeCount}卷）！`);
      }

      setConfirmedSuperOutline({ novelId: Number(novelId), outline: outlineText } as any);
      setHasSuperOutline(true);

      // 立即切换到步骤1（生成卷中）
      setCurrentStep(1);
      saveCreationState(1);
      console.log('[confirmSuperOutline] 已切换到步骤1（生成卷中）');

      // 启动轮询等待卷生成完成
      pollForVolumeGeneration().catch(error => {
        console.error('[confirmSuperOutline] 轮询失败:', error);
        setIsConfirmingOutline(false);
        setTaskProgress(null);
      });

    } catch (error: any) {
      console.error('确认大纲失败:', error);
      message.error(error.message || '确认大纲失败');
      // 报错时清理所有进行中状态
      setTaskProgress(null);
      setIsConfirmingOutline(false);
      // 清理可能已设置的 localStorage 标记
      if (novelId) {
        localStorage.removeItem(`novel_${novelId}_generating_volumes`);
      }
    }
    // 注意：不在 finally 中设置 setIsConfirmingOutline(false)，
    // 因为成功时会启动轮询，轮询完成后才需要清理该状态
  };

  // 轮询等待卷规划生成完成（直接跳到步骤2）
  const pollForVolumeGeneration = async () => {
    let attempts = 0;
    const maxAttempts = 120; // 最多轮询4分钟（120次 * 4秒 = 480秒）
    let consecutiveErrors = 0; // 连续错误计数
    const maxConsecutiveErrors = 3; // 最多允许3次连续错误

    // 标记正在生成卷（持久化到 localStorage）
    localStorage.setItem(`novel_${novelId}_generating_volumes`, Date.now().toString());
    console.log('[轮询] 开始轮询卷规划生成，已设置 localStorage 标记');

    return new Promise<void>((resolve, reject) => {
      const intervalId = setInterval(async () => {
        attempts++;
        try {
          // 更新进度 - 优化为3分钟模拟曲线，前期快后期慢，分段增长
          let progress = 0;
          if (attempts <= 8) { // 前32秒: 5% -> 30%
             progress = 5 + (attempts / 8) * 25; 
          } else if (attempts <= 25) { // 32-100秒: 30% -> 70%
             progress = 30 + ((attempts - 8) / 17) * 40;
          } else if (attempts <= 45) { // 100-180秒: 70% -> 95%
             progress = 70 + ((attempts - 25) / 20) * 25;
          } else { // >180秒: 95% -> 99%
             progress = 95 + Math.min(4, (attempts - 45) * 0.1);
          }
          
          setTaskProgress({ percentage: Math.floor(progress), message: '生成卷规划中...' });

          // 1. 首先检查是否有失败的 AI 任务（按 novelId + operationType 精确定位）
          try {
            const tasksResponse = await aiTaskService.getAITasks(
              0,
              10,
              undefined,
              undefined,
              parseInt(novelId!)
            );
            const tasks = tasksResponse?.content || [];

            // 只关注“基于确认大纲生成卷规划”这一类任务，避免被其他任务误伤
            const relatedTasks = tasks.filter((t: any) => {
              const rawParams = t?.parameters;
              if (!rawParams || typeof rawParams !== 'string') return false;
              try {
                const parsed = JSON.parse(rawParams);
                return parsed?.operationType === 'GENERATE_VOLUMES_FROM_CONFIRMED_OUTLINE';
              } catch {
                return false;
              }
            });

            const latestTask = relatedTasks.sort((a: any, b: any) => {
              return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
            })[0];

            if (latestTask) {
              console.log('[轮询] 卷规划任务状态:', latestTask.status, '错误信息:', latestTask.errorMessage);

              if (latestTask.status === 'FAILED' || latestTask.status === 'CANCELLED') {
                clearInterval(intervalId);
                localStorage.removeItem(`novel_${novelId}_generating_volumes`);
                setTaskProgress(null);
                setIsConfirmingOutline(false);

                const errorMsg = latestTask.errorMessage || '任务失败';
                message.error({
                  content: `卷规划生成失败：${errorMsg}`,
                  duration: 8
                });

                console.error('[轮询] 卷规划任务失败，错误信息:', errorMsg);
                reject(new Error(errorMsg));
                return;
              }
            }
          } catch (taskError) {
            console.warn('[轮询] 查询AI任务状态失败:', taskError);
            // 不阻断主流程，继续检查小说状态
          }

          // 2. 检查小说的创作阶段
          let novelInfo: any = null;
          try {
            const res: any = await api.get(`/novels/${novelId}?_=${Date.now()}`);
            novelInfo = res?.data || res;
            console.log('[轮询] 小说创作阶段:', novelInfo?.creationStage);
            consecutiveErrors = 0; // 请求成功，重置错误计数
          } catch (e) {
            console.warn('[轮询] 获取小说信息失败:', e);
            consecutiveErrors++;
            
            // 如果连续多次获取失败，可能是网络问题或服务异常
            if (consecutiveErrors >= maxConsecutiveErrors) {
              clearInterval(intervalId);
              localStorage.removeItem(`novel_${novelId}_generating_volumes`);
              setTaskProgress(null);
              setIsConfirmingOutline(false);
              
              message.error({
                content: '无法连接到服务器，请检查网络连接或稍后重试',
                duration: 8
              });
              
              reject(new Error('连续多次请求失败'));
              return;
            }
          }

          // 只有当创作阶段为 VOLUMES_GENERATED 时，才认为卷生成完成
          if (novelInfo && novelInfo.creationStage === 'VOLUMES_GENERATED') {
            console.log('[轮询] 检测到创作阶段已更新为 VOLUMES_GENERATED，开始加载卷列表...');

            // 再次查询卷列表，确保获取到所有卷
            let list: any[] = [];
            try {
              const res: any = await api.get(`/volumes/novel/${novelId}?_=${Date.now()}`);
              list = Array.isArray(res) ? res : (Array.isArray(res?.data) ? res.data : []);
              console.log('[轮询] 成功加载卷列表，数量:', list.length);
            } catch (e) {
              console.warn('[轮询] 加载卷列表失败:', e);
              // 兜底：走原服务
              try {
                list = await novelVolumeService.getVolumesByNovelId(novelId!);
                console.log('[轮询] 通过兜底服务加载卷列表，数量:', list.length);
              } catch {}
            }

            if (Array.isArray(list) && list.length > 0) {
              clearInterval(intervalId);

              // 卷规划生成完成，清除标记
              localStorage.removeItem(`novel_${novelId}_generating_volumes`);
              console.log('[轮询] 卷规划生成完成，已清除 localStorage 标记');

              // 卷规划生成完成，直接跳到步骤2（跳过步骤1的生成卷蓝图页面）
              setTaskProgress({ percentage: 100, message: '卷规划生成完成！' });
              setVolumes(list);
              setCurrentStep(2); // 直接进入步骤2
              saveCreationState(2);
              loadVolumeStats();

              // 延迟清除进度条
              setTimeout(() => setTaskProgress(null), 2000);
              resolve();
            } else {
              console.warn('[轮询] 创作阶段已更新但卷列表为空，继续轮询...');
            }
          }
        } catch (error) {
          console.warn('轮询失败:', error);
          consecutiveErrors++;
          
          // 连续错误处理
          if (consecutiveErrors >= maxConsecutiveErrors) {
            clearInterval(intervalId);
            localStorage.removeItem(`novel_${novelId}_generating_volumes`);
            setTaskProgress(null);
            setIsConfirmingOutline(false);
            
            message.error({
              content: '生成过程中出现异常，请刷新页面重试',
              duration: 8
            });
            
            reject(new Error('连续多次轮询失败'));
            return;
          }
        }

        // 超时处理
        if (attempts >= maxAttempts) {
          clearInterval(intervalId);
          localStorage.removeItem(`novel_${novelId}_generating_volumes`);
          console.log('[轮询] 轮询超时，已清除 localStorage 标记');
          setTaskProgress(null);
          setIsConfirmingOutline(false);
          
          message.warning({
            content: '卷规划生成超时，请刷新页面查看是否已生成，或重新尝试',
            duration: 8
          });
          
          reject(new Error('卷规划生成超时'));
        }
      }, 4000); // 每次轮询间隔4秒
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

  // 加载大纲模板列表
  const loadOutlineTemplates = async () => {
    setLoadingTemplates(true);
    try {
      const response = await api.get('/prompt-templates/category/outline');
      console.log('大纲模板响应:', response);

      // 兼容两种返回格式：
      // 1. Result格式: { code: 200, message: 'success', data: [...] }
      // 2. ApiResponse格式: { success: true, data: [...] }
      let templates = [];
      if (response && response.data) {
        // Result格式或ApiResponse格式
        templates = response.data;
      } else if (Array.isArray(response)) {
        // 直接返回数组
        templates = response;
      }

      console.log('解析后的模板列表:', templates);
      setOutlineTemplates(templates);

      // 如果有默认模板，自动选中
      const defaultTemplate = templates.find((t: any) => t.isDefault);
      if (defaultTemplate) {
        setSelectedTemplateId(defaultTemplate.id);
        outlineForm.setFieldValue('templateId', defaultTemplate.id);
      }
    } catch (error) {
      console.error('加载大纲模板失败:', error);
    } finally {
      setLoadingTemplates(false);
    }
  };

  // 生成卷规划（第一步改为流式生成大纲）
  const handleGenerateVolumes = async (values: any) => {
    // 立即滚动到顶部以便用户看到进度条
    window.scrollTo({ top: 0, behavior: 'smooth' });

    if (!novelId) return;

    // 防止重复提交
    if (isGenerating || currentTaskId) {
      message.warning('任务正在进行中，请勿重复提交');
      return;
    }

    setLoading(true);
    setIsGenerating(true);
    try {
      // 先保存用户设定的卷数和章节总数到小说对象
      if (novel) {
        try {
          const updateData: any = {};
          if (values.volumeCount) {
            updateData.plannedVolumeCount = values.volumeCount;
            console.log('[handleGenerateVolumes] 保存预期卷数到小说:', values.volumeCount);
          }
          if (values.targetChapters) {
            updateData.targetTotalChapters = values.targetChapters;
            console.log('[handleGenerateVolumes] 保存目标总章数到小说:', values.targetChapters);
          }
          if (values.targetWords) {
            updateData.totalWordTarget = values.targetWords;
            console.log('[handleGenerateVolumes] 保存目标总字数到小说:', values.targetWords);
          }
          // 同步保存每章字数到小说配置，供后续写作阶段使用
          if (values.wordsPerChapter) {
            updateData.wordsPerChapter = values.wordsPerChapter;
            console.log('[handleGenerateVolumes] 保存每章字数到小说:', values.wordsPerChapter);
          }
          
          if (Object.keys(updateData).length > 0) {
            await api.put(`/novels/${novelId}`, {
              ...novel,
              ...updateData
            });
            // 更新本地小说对象
            setNovel({ ...novel, ...updateData } as any);
          }
        } catch (error) {
          console.warn('[handleGenerateVolumes] 保存小说配置失败，但继续生成大纲:', error);
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
      
      console.log('[handleGenerateVolumes] AI配置验证通过');

      // 流式生成大纲（SSE）
      setIsGeneratingOutline(true);
      setCurrentSuperOutline(null);

      // 保存生成参数到 localStorage，供重新生成时使用
      const generationParams = {
        templateId: selectedTemplateId || values.templateId,
        outlineWordLimit: values.outlineWordLimit || 2000,
        volumeCount: values.volumeCount || 5
      };
      localStorage.setItem(`novel_${novelId}_generation_params`, JSON.stringify(generationParams));

      const sseResp = await fetch(`/api/outline/generate-stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(localStorage.getItem('token') ? { 'Authorization': `Bearer ${localStorage.getItem('token')}` } : {})
        },
        body: JSON.stringify(withAIConfig({
          novelId: novelId,
          basicIdea: values.basicIdea,
          targetWordCount: values.targetWords || 1000000,
          targetChapterCount: values.targetChapters || 300,
          templateId: generationParams.templateId,
          outlineWordLimit: generationParams.outlineWordLimit,
          volumeCount: generationParams.volumeCount
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
          } else if (eventName === 'done') {
            console.log('[SSE done] 生成完成，当前 currentSuperOutline:', currentSuperOutline);
            console.log('[SSE done] plotStructure 长度:', currentSuperOutline?.plotStructure?.length);
            
            setIsGeneratingOutline(false);
            setHasSuperOutline(true);
            
            // 后端流式生成时已自动保存到 novel_outlines 表（状态为DRAFT）
            // 前端只需提示用户生成完成即可
            console.log('大纲生成完成，后端已自动保存为草稿状态');
            message.success('大纲生成完成！您可以查看、修改或确认大纲');
            
            // 保持 currentSuperOutline，这样页面会继续显示大纲内容而不是回到"准备开始创作"状态
            // currentSuperOutline 已经在 chunk 事件中不断更新
            
            // 重新加载大纲数据，确保获取到最新的status
            setTimeout(() => {
              checkSuperOutline();
            }, 500);
          } else if (eventName === 'error') {
            throw new Error(data || '生成失败');
          } else if (eventName === 'chunk' || (eventName === 'message' && data)) {
            // 处理流式文本块（兼容 'chunk' 和默认 'message' 事件）
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
              targetChapterCount: values.targetChapters || 300,
              targetWordCount: values.targetWords || 1000000,
              status: 'DRAFT',
              feedbackHistory: '',
              revisionCount: 0,
              createdAt: new Date().toISOString(),
              updatedAt: new Date().toISOString(),
            } as any);
          }
        }
      }
    } catch (error: any) {
      console.error('[生成大纲失败]', error);
      message.error({
        content: formatAIErrorMessage(error),
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

  // 开始写作（跳转到新的写作工作室）
  const handleStartWriting = async (volumeId: string) => {
    setLoading(true);
    try {
      const sessionData = await novelVolumeService.startVolumeWriting(volumeId);

      // 跳转到新的writing-studio页面，传递会话数据
      navigate(`/novels/${novelId}/writing-studio`, {
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
  const fetchCreationStageFromBackend = async (): Promise<number | null> => {
    try {
      // 直接使用已加载的小说信息，或重新获取
      const res: any = await api.get(`/novels/${novelId}?_=${Date.now()}`);
      const novelInfo = res?.data || res;
      
      if (novelInfo && novelInfo.creationStage) {
        console.log('[fetchCreationStageFromBackend] 后端创作状态:', novelInfo.creationStage);
        
        // 根据后端状态映射到前端步骤
        const stageToStepMap: Record<string, number> = {
          'OUTLINE_PENDING': 0,
          'OUTLINE_CONFIRMED': 1,
          'VOLUMES_GENERATED': 2,
          'DETAILED_OUTLINE_GENERATED': 2,
          'WRITING_IN_PROGRESS': 2,
          'WRITING_COMPLETED': 2
        };
        
        const backendStep = stageToStepMap[novelInfo.creationStage] ?? 0;
        console.log(`[fetchCreationStageFromBackend] 映射步骤: ${novelInfo.creationStage} -> ${backendStep}`);
        return backendStep;
      }
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
    // 确认大纲后直接跳到步骤2，跳过步骤1（生成卷蓝图页面）
    if (!hasConfirmedOutline) {
      // 没有确认的大纲，停留在第一步（确认大纲）
      newStep = 0;
      console.log('[updateProcessStep] 无已确认大纲 -> 步骤0');
    } else if (hasConfirmedOutline && !hasVolumes) {
      // 有确认的大纲但没有卷，正在生成卷中，停留在步骤0显示进度
      newStep = 0;
      console.log('[updateProcessStep] 有大纲但无卷（生成中） -> 步骤0');
    } else if (hasConfirmedOutline && hasVolumes) {
      // 有大纲和卷，直接进入步骤2（写作阶段）
      newStep = 2;
      console.log('[updateProcessStep] 有大纲和卷 -> 步骤2（写作阶段）');
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
      console.log('数据加载完成，开始更新流程步骤', {
        novelLoaded: novel !== null,
        volumesLoaded: volumes.length >= 0,
        confirmedSuperOutlineExists: !!confirmedSuperOutline,
        novelHasOutline: !!(novel && (novel as any).outline && (novel as any).outline.trim().length > 0)
      });
      updateProcessStep();
    } else {
      console.log('等待数据加载完成...', {
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

  // 重新生成大纲（流式）- 直接使用之前的构思重新生成
  const regenerateSuperOutline = async () => {
    if (!currentSuperOutline) {
      message.warning('未找到大纲信息');
      return;
    }

    // 检查AI配置
    if (!checkAIConfig()) {
      message.error(AI_CONFIG_ERROR_MESSAGE);
      return;
    }

    setIsGeneratingOutline(true);
    setOutlineGenerationVisible(false);

    try {
      // 直接使用之前保存的构思重新生成，不需要用户再次输入
      // 从 currentSuperOutline 或 novel 中获取原始构思
      const basicIdea = currentSuperOutline.basicIdea || novel?.description || '';

      if (!basicIdea || !basicIdea.trim()) {
        message.warning('未找到原始构思，无法重新生成');
        setIsGeneratingOutline(false);
        return;
      }

      // 读取之前保存的生成参数（模板ID、大纲字数限制等）
      let savedParams: any = {};
      try {
        const paramsStr = localStorage.getItem(`novel_${novelId}_generation_params`);
        if (paramsStr) {
          savedParams = JSON.parse(paramsStr);
        }
      } catch (e) {
        console.warn('读取生成参数失败，使用默认值', e);
      }

      const sseResp = await fetch(`/api/outline/generate-stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(localStorage.getItem('token') ? { 'Authorization': `Bearer ${localStorage.getItem('token')}` } : {})
        },
        body: JSON.stringify(withAIConfig({
          novelId: novelId,
          basicIdea: basicIdea, // 使用之前的构思，不携带已生成的大纲内容
          targetWordCount: currentSuperOutline.targetWordCount || 1000000,
          targetChapterCount: currentSuperOutline.targetChapterCount || 500,
          // 使用之前保存的模板ID和大纲字数限制
          templateId: savedParams.templateId,
          outlineWordLimit: savedParams.outlineWordLimit || 2000,
          volumeCount: savedParams.volumeCount || 5
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
          } else if (eventName === 'done') {
            setIsGeneratingOutline(false);
            message.success('大纲重新生成完成！');
            setOutlineUserAdvice('');
            
            // 重新加载数据
            setTimeout(() => {
              checkSuperOutline();
            }, 500);
          } else if (eventName === 'error') {
            throw new Error(data || '生成失败');
          } else if (data) {
            // 默认的 message 事件（后端直接send纯文本）
            streamedText += data;
            setCurrentSuperOutline({
              ...(currentSuperOutline as any),
              plotStructure: streamedText,
            } as any);
          }
        }
      }
    } catch (error: any) {
      console.error('[重新生成大纲失败]', error);
      message.error(formatAIErrorMessage(error));
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
      message.error(formatAIErrorMessage(error));
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

        // 启动批量轮询（一次请求查询所有任务）
        const taskIds = tasks.map(t => t.taskId);
        const taskIdToVolumeId: Record<number, string> = {};
        tasks.forEach(task => {
          taskIdToVolumeId[task.taskId] = String(task.volumeId);
          try {
            aiTaskService.storeTask(task.taskId, 'VOLUME_OUTLINE', parseInt(novelId!));
          } catch {}
        });

        let completedCount = 0;
        let failedCount = 0;
        const allCompleted = () => completedCount + failedCount >= tasks.length;

        const batchPollingInterval = setInterval(async () => {
          try {
            // 一次请求查询所有任务状态
            const response = await fetch('/api/ai-tasks/batch-status', {
              method: 'POST',
              headers: {
                'Content-Type': 'application/json',
                ...(localStorage.getItem('token') ? { 'Authorization': `Bearer ${localStorage.getItem('token')}` } : {})
              },
              body: JSON.stringify({ taskIds })
            });

            if (!response.ok) {
              throw new Error('批量查询任务状态失败');
            }

            const statusMap = await response.json();

            // 更新所有任务的状态
            setVolumeTasks(prev => {
              const next = { ...prev };
              let newCompletedCount = 0;
              let newFailedCount = 0;

              Object.entries(statusMap).forEach(([taskIdStr, taskData]: [string, any]) => {
                const taskId = Number(taskIdStr);
                const volumeId = taskIdToVolumeId[taskId];
                
                if (volumeId && next[volumeId]) {
                  const status = taskData.status || 'RUNNING';
                  const progress = taskData.progressPercentage || taskData.percentage || 0;
                  
                  next[volumeId] = {
                    taskId: taskId,
                    progress: progress,
                    status: status,
                    message: taskData.message || (status === 'COMPLETED' ? '生成完成' : '生成中...')
                  };

                  if (status === 'COMPLETED') newCompletedCount++;
                  if (status === 'FAILED' || status === 'CANCELLED') newFailedCount++;
                }
              });

              // 更新完成计数
              if (newCompletedCount > completedCount) {
                completedCount = newCompletedCount;
              }
              if (newFailedCount > failedCount) {
                failedCount = newFailedCount;
              }

              return next;
            });

            // 检查是否全部完成
            if (allCompleted()) {
              clearInterval(batchPollingInterval);
              
              // 刷新卷列表
              loadVolumes();
              
              // 清理任务
              taskIds.forEach(taskId => {
                try { aiTaskService.removeStoredTask(taskId); } catch {}
              });

              if (failedCount > 0) {
                message.warning(`批量生成完成，${completedCount}个成功，${failedCount}个失败`);
              } else {
                message.success('所有卷大纲生成完成！');
              }
            }
          } catch (error) {
            console.warn('批量轮询失败:', error);
          }
        }, 3000); // 3秒轮询一次

        // 保存停止函数
        setTaskStops(prev => ({ ...prev, 'batch': () => clearInterval(batchPollingInterval) }));

      } else {
        throw new Error(result?.message || '批量生成卷详细大纲失败');
      }
    } catch (error: any) {
      message.error(formatAIErrorMessage(error));
    } finally {
      setIsGeneratingVolumeOutlines(false);
    }
  };

  // 分别提供两个入口
  const generateAllVolumeOutlinesWithAdvice = () => batchGenerateVolumeOutlines(true);
  const generateAllVolumeOutlinesWithoutAdvice = () => batchGenerateVolumeOutlines(false);

  // 进入写作页面（跳转到新的写作工作室）
  const enterWriting = (volume: NovelVolume) => {
    navigate(`/novels/${novelId}/writing-studio`, {
      state: {
        initialVolumeId: volume.id,
        sessionData: null
      }
    });
  };

  // 开始创作（跳转到新的写作工作室）
  const startWritingFromFirstVolume = () => {
    if (volumes.length === 0) {
      message.warning('请先生成卷规划');
      return;
    }

    // 找到第一卷（按volumeNumber排序）
    const sortedVolumes = [...volumes].sort((a, b) => a.volumeNumber - b.volumeNumber);
    const firstVolume = sortedVolumes[0];

    // 卷已经从大纲拆分出来，包含了必要的信息（title, theme, description），可以直接开始写作

    // 跳转到新的writing-studio页面
    navigate(`/novels/${novelId}/writing-studio`, {
      state: {
        initialVolumeId: firstVolume.id,
        sessionData: null
      }
    });
  };

  return (
    <div className="volume-management-page">
      {/* 顶部操作区已按需求删除，避免占用视野 */}

      {/* 顶部统计信息 */}

      {/* 简化的工作流程指示器 */}

      {/* 卷列表的展示仅保留在第二步与第三步中，避免重复 */}

      {/* 第一步：生成大纲 */}
      {currentStep === 0 && (
        <Card
          title={
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <div style={{
                width: '28px',
                height: '28px',
                borderRadius: '8px',
                background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: 'white',
                fontSize: '14px',
                fontWeight: 700,
                boxShadow: '0 4px 10px rgba(118, 75, 162, 0.3)'
              }}>
                1
              </div>
              <span style={{ fontSize: '18px', fontWeight: 600, color: '#1f2937' }}>
                生成小说大纲
              </span>
            </div>
          }
          style={{
            marginBottom: 24,
            border: 'none',
            boxShadow: '0 4px 20px rgba(0,0,0,0.08)',
            borderRadius: '16px'
          }}
        >
          {/* 任务进度条 - 美化版 */}
          {taskProgress && (
            <div style={{
              marginBottom: 24,
              background: '#ffffff',
              borderRadius: '16px',
              padding: '24px',
              boxShadow: '0 4px 20px rgba(0,0,0,0.05)',
              border: '1px solid rgba(226, 232, 240, 0.8)',
              position: 'relative',
              overflow: 'hidden'
            }}>
              {/* 背景装饰 */}
              <div style={{
                position: 'absolute',
                top: 0,
                right: 0,
                width: '150px',
                height: '150px',
                background: 'radial-gradient(circle, rgba(99, 102, 241, 0.05) 0%, rgba(255,255,255,0) 70%)',
                pointerEvents: 'none'
              }} />

              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                  <div style={{
                    width: '32px',
                    height: '32px',
                    borderRadius: '8px',
                    background: 'rgba(99, 102, 241, 0.1)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: '#6366f1'
                  }}>
                    <ReloadOutlined spin style={{ fontSize: '16px' }} />
                  </div>
                  <div>
                    <div style={{ fontWeight: 600, color: '#1f2937', fontSize: '15px' }}>任务进行中</div>
                    <div style={{ fontSize: '12px', color: '#6b7280' }}>AI 正在全力处理您的请求</div>
                  </div>
                </div>
                <div style={{ 
                  fontWeight: 700, 
                  color: '#6366f1', 
                  fontSize: '18px',
                  fontVariantNumeric: 'tabular-nums'
                }}>
                  {taskProgress.percentage}%
                </div>
              </div>
              
              <Progress
                percent={taskProgress.percentage}
                status="active"
                strokeColor={{ '0%': '#6366f1', '100%': '#8b5cf6' }}
                showInfo={false}
                trailColor="#f3f4f6"
                strokeWidth={10}
                style={{ marginBottom: '12px' }}
              />
              
              <div style={{ 
                display: 'flex', 
                alignItems: 'center', 
                gap: '8px', 
                fontSize: '13px', 
                color: '#4b5563',
                background: '#f8fafc',
                padding: '8px 12px',
                borderRadius: '8px',
                border: '1px solid #f1f5f9'
              }}>
                <RobotOutlined style={{ color: '#6366f1' }} />
                {taskProgress.message}
              </div>
            </div>
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
                  {/* 精简的顶部提示 - 苹果简约风格 */}
                  <div style={{ 
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '16px',
                    marginBottom: '28px',
                    padding: '20px 28px',
                    background: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)',
                    borderRadius: '16px',
                    color: 'white',
                    boxShadow: '0 4px 20px rgba(59, 130, 246, 0.25)'
                  }}>
                    <div style={{
                      width: '40px',
                      height: '40px',
                      borderRadius: '12px',
                      background: 'rgba(255, 255, 255, 0.2)',
                      backdropFilter: 'blur(10px)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: '20px',
                      animation: 'pulse 2s infinite'
                    }}>
                      <RobotOutlined style={{ fontSize: '18px', color: 'white' }} />
                    </div>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: '17px', fontWeight: 600, marginBottom: '4px' }}>
                        AI 正在为您生成大纲
                      </div>
                      <div style={{ fontSize: '14px', opacity: 0.9 }}>
                        请稍候，精彩的故事即将呈现...
                      </div>
                    </div>
                    <div style={{
                      width: '8px',
                      height: '8px',
                      borderRadius: '50%',
                      background: '#22c55e',
                      animation: 'blink 1.5s infinite',
                      boxShadow: '0 0 16px #22c55e'
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
                        background: 'var(--primary-600)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: '18px',
                        boxShadow: '0 2px 8px rgba(102, 126, 234, 0.3)'
                      }}>
                        <BookOutlined style={{ fontSize: '18px', color: 'white' }} />
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
                    <div className="outline-content">
                      {(currentSuperOutline as any)?.plotStructure ? (
                        <div className="outline-content-wrapper" style={{ animation: 'fadeIn 0.3s ease-in' }}>
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
                            <ClockCircleOutlined style={{ fontSize: 52 }} />
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
                <div style={{ textAlign: 'center', padding: '72px 20px' }}>
                  <div style={{
                    width: 56,
                    height: 56,
                    borderRadius: 16,
                    background: '#f8fafc',
                    border: '1px solid #e2e8f0',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    margin: '0 auto 18px'
                  }}>
                    <SettingOutlined style={{ fontSize: 24, color: '#334155' }} />
                  </div>
                  <Title level={3} style={{ marginBottom: 8, color: '#0f172a', fontSize: 22 }}>
                    开始创作前，请先设定参数
                  </Title>
                  <Text style={{ fontSize: 14, color: '#64748b', display: 'block', marginBottom: 20 }}>
                    系统将根据参数生成故事大纲与分卷规划。
                  </Text>
                  <Alert
                    type="info"
                    showIcon
                    message="如果弹窗已关闭，可点击下方按钮重新打开参数设置。"
                    style={{
                      maxWidth: 560,
                      margin: '0 auto 20px',
                      textAlign: 'left',
                      borderRadius: 12
                    }}
                  />
                  <div>
                    <Button
                      type="primary"
                      size="large"
                      icon={<SettingOutlined />}
                      onClick={() => {
                        // 重置所有状态
                        setIsGeneratingOutline(false);
                        setCurrentSuperOutline(null);
                        setLoading(false);
                        setIsGenerating(false);
                        // 打开配置弹窗
                        setQuickStartVisible(true);
                        loadOutlineTemplates();
                        message.info('请重新配置参数');
                      }}
                      style={{
                        height: 44,
                        fontSize: 15,
                        borderRadius: 12,
                        padding: '0 28px',
                        fontWeight: 600
                      }}
                    >
                      打开参数设置
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
                  <CheckCircleOutlined style={{ fontSize: 18, color: 'white' }} />
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
                    <BookOutlined style={{ fontSize: '18px', color: 'white' }} />
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
                {!isEditingOutline ? (
                  <div className="outline-content">
                    <div className="outline-content-wrapper">
                      {((currentSuperOutline as any)?.plotStructure) || '大纲内容加载中...'}
                    </div>
                  </div>
                ) : (
                  <div style={{ padding: '28px', background: '#ffffff' }}>
                    <TextArea
                      value={editedOutlineContent}
                      onChange={(e) => setEditedOutlineContent(e.target.value)}
                      placeholder="请输入大纲内容..."
                      style={{
                        minHeight: '450px',
                        maxHeight: '650px',
                        fontSize: '15px',
                        lineHeight: '1.9',
                        fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Microsoft YaHei", sans-serif',
                        border: '2px solid #e2e8f0',
                        borderRadius: '8px',
                        padding: '16px',
                        resize: 'vertical'
                      }}
                    />
                  </div>
                )}
                    </Card>

              {/* 操作按钮 */}
              <div style={{ textAlign: 'center' }}>
                {!isEditingOutline ? (
                  <Space size="large">
                    <Button
                      icon={<EditOutlined />}
                      onClick={startEditingOutline}
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
                      编辑大纲
                    </Button>
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
                        background: 'var(--primary-600)',
                        border: 'none',
                        boxShadow: '0 4px 15px rgba(102, 126, 234, 0.4)',
                        fontSize: '15px',
                        fontWeight: 500
                      }}
                    >
                      {isConfirmingOutline ? '正在确认...' : '确认大纲'}
                    </Button>
                  </Space>
                ) : (
                  <Space size="large">
                    <Button
                      onClick={cancelEditingOutline}
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
                      取消
                    </Button>
                    <Button
                      type="primary"
                      icon={<CheckCircleOutlined />}
                      onClick={saveEditedOutline}
                      loading={isConfirmingOutline}
                      size="large"
                      style={{
                        height: '48px',
                        padding: '0 32px',
                        borderRadius: '12px',
                        background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
                        border: 'none',
                        boxShadow: '0 4px 15px rgba(16, 185, 129, 0.4)',
                        fontSize: '15px',
                        fontWeight: 500
                      }}
                    >
                      {isConfirmingOutline ? '保存中...' : '保存大纲'}
                    </Button>
                  </Space>
                )}
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
              background: 'var(--primary-600)',
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
              <BarChartOutlined style={{ fontSize: 18, color: 'white' }} />
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
                      background: 'var(--primary-600)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: '18px',
                      boxShadow: '0 2px 8px rgba(102, 126, 234, 0.3)'
                    }}>
                      <BarChartOutlined style={{ fontSize: 18, color: 'white' }} />
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
                    
                    <Alert
                      type="info"
                      showIcon
                      message="卷规划生成中"
                      description="系统正在根据您确认的大纲划分卷与章节结构，通常需要 1-2 分钟，请耐心等待。"
                      style={{ marginTop: 24, borderRadius: 12 }}
                    />
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
                  background: 'var(--primary-600)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  margin: '0 auto 32px',
                  fontSize: '64px',
                  boxShadow: '0 12px 40px rgba(102, 126, 234, 0.3)'
                }}>
                  <BarChartOutlined style={{ fontSize: 54, color: 'white' }} />
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
                      <strong>即将进行：</strong>
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

      {/* 第二步：卷列表和写作 */}
      {currentStep === 2 && volumes.length > 0 && (
        <div style={{ position: 'relative', paddingBottom: '100px' }}>
          <Card
            title={null}
            style={{
              marginBottom: 20,
              border: '1px solid rgba(226, 232, 240, 0.8)',
              boxShadow: '0 4px 20px rgba(15, 23, 42, 0.04), 0 1px 3px rgba(15, 23, 42, 0.02)',
              borderRadius: '16px',
              background: 'linear-gradient(135deg, #ffffff 0%, #fefefe 100%)'
            }}
            bodyStyle={{ padding: '20px 24px' }}
          >
            {/* 卷列表 */}
            <div style={{ marginBottom: 20 }}>
              <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginBottom: 20,
                paddingBottom: 16,
                borderBottom: '2px solid #f1f5f9'
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
                  <div style={{
                    width: '42px',
                    height: '42px',
                    borderRadius: '12px',
                    background: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '20px',
                    boxShadow: '0 4px 14px rgba(59, 130, 246, 0.3)'
                  }}>
                    <BookOutlined style={{ fontSize: 20, color: 'white' }} />
                  </div>
                  <div>
                    <Title level={4} style={{ margin: 0, color: '#0f172a', fontWeight: 700, fontSize: '18px', letterSpacing: '-0.02em' }}>
                      卷列表
                    </Title>
                    <Text type="secondary" style={{ fontSize: '12px', color: '#64748b', fontWeight: 500 }}>
                      共 {volumes.length} 卷，点击卡片查看详情
                    </Text>
                  </div>
                </div>
                <Button
                  icon={<ReloadOutlined spin={isRegeneratingVolumes} />}
                  loading={isRegeneratingVolumes}
                  disabled={isRegeneratingVolumes}
                  style={{
                    borderRadius: '8px',
                    height: '36px',
                    padding: '0 16px',
                    border: '1.5px solid #e2e8f0',
                    fontWeight: 500,
                    fontSize: '13px',
                    transition: 'all 0.2s ease'
                  }}
                  onMouseEnter={(e) => {
                    if (!isRegeneratingVolumes) {
                      e.currentTarget.style.borderColor = '#3b82f6';
                      e.currentTarget.style.color = '#2563eb';
                      e.currentTarget.style.background = 'linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%)';
                    }
                  }}
                  onMouseLeave={(e) => {
                    if (!isRegeneratingVolumes) {
                      e.currentTarget.style.borderColor = '#e2e8f0';
                      e.currentTarget.style.color = '';
                      e.currentTarget.style.background = '';
                    }
                  }}
                  onClick={async (e) => {
                    e.stopPropagation();
                    Modal.confirm({
                      title: '重新生成卷规划',
                      content: '确定要重新生成所有卷的规划吗？这将清除现有卷及其章纲数据，并基于当前大纲重新拆分卷。',
                      okText: '确认重新生成',
                      cancelText: '取消',
                      okButtonProps: { danger: true },
                      onOk: async () => {
                        try {
                          setIsRegeneratingVolumes(true);
                          setFakeProgress(0);
                          setRegenerateProgress({ percentage: 0, message: '正在提交重新生成任务...' });
                          
                          // 启动假进度条动画：每500ms增加5-10%，最多到85%
                          const fakeProgressInterval = setInterval(() => {
                            setFakeProgress(prev => {
                              if (prev >= 85) {
                                clearInterval(fakeProgressInterval);
                                return prev;
                              }
                              const increment = Math.floor(Math.random() * 6) + 5; // 5-10%
                              return Math.min(prev + increment, 85);
                            });
                          }, 500);
                          
                          const response = await api.post(`/volumes/${novelId}/generate-from-outline`, withAIConfig({
                            volumeCount: volumes.length
                          }));
                          
                          if (response.data?.taskId) {
                            const taskId = response.data.taskId;
                            setRegenerateProgress({ percentage: 10, message: '已提交任务，正在清理旧数据...' });
                            
                            // 开始轮询任务状态
                            const pollInterval = setInterval(async () => {
                              try {
                                const taskResp = await api.get(`/ai-tasks/${taskId}`);
                                const task = taskResp.data || taskResp;
                                
                                // 更新进度：使用真实进度和假进度的较大值
                                if (task.progress !== undefined) {
                                  const realProgress = task.progress;
                                  setFakeProgress(prev => Math.max(prev, realProgress));
                                  setRegenerateProgress({
                                    percentage: Math.max(fakeProgress, realProgress),
                                    message: task.message || '正在重新生成卷规划...'
                                  });
                                }
                                
                                if (task.status === 'COMPLETED') {
                                  clearInterval(pollInterval);
                                  clearInterval(fakeProgressInterval);
                                  setFakeProgress(100);
                                  setRegenerateProgress({ percentage: 100, message: '重新生成完成！' });
                                  message.success('卷规划重新生成完成');
                                  
                                  // 延迟一下再刷新，让用户看到100%
                                  setTimeout(() => {
                                    setIsRegeneratingVolumes(false);
                                    setRegenerateProgress(null);
                                    setFakeProgress(0);
                                    loadVolumes();
                                  }, 1000);
                                } else if (task.status === 'FAILED') {
                                  clearInterval(pollInterval);
                                  clearInterval(fakeProgressInterval);
                                  setIsRegeneratingVolumes(false);
                                  setRegenerateProgress(null);
                                  setFakeProgress(0);
                                  message.error('卷规划生成失败: ' + (task.error || '未知错误'));
                                }
                              } catch (err) {
                                console.error('轮询任务状态失败:', err);
                              }
                            }, 2000);
                            
                            // 10分钟后停止轮询
                            setTimeout(() => {
                              clearInterval(pollInterval);
                              clearInterval(fakeProgressInterval);
                              if (isRegeneratingVolumes) {
                                setIsRegeneratingVolumes(false);
                                setRegenerateProgress(null);
                                setFakeProgress(0);
                                message.warning('任务超时，请刷新页面查看结果');
                              }
                            }, 600000);
                          } else {
                            clearInterval(fakeProgressInterval);
                            setFakeProgress(100);
                            setRegenerateProgress({ percentage: 100, message: '重新生成完成！' });
                            setTimeout(() => {
                              setIsRegeneratingVolumes(false);
                              setRegenerateProgress(null);
                              setFakeProgress(0);
                              loadVolumes();
                            }, 1000);
                          }
                        } catch (error: any) {
                          console.error('重新生成卷规划失败:', error);
                          setIsRegeneratingVolumes(false);
                          setRegenerateProgress(null);
                          setFakeProgress(0);
                          message.error(formatAIErrorMessage(error));
                        }
                      }
                    });
                  }}
                >
                  {isRegeneratingVolumes ? '重新生成中...' : '重新生成卷'}
                </Button>
              </div>

              {/* 卷列表容器 - 优化布局，一行4个 */}
              <div style={{
                maxHeight: 'calc(100vh - 350px)',
                overflowY: 'auto',
                paddingRight: '8px',
                paddingTop: '8px',
                marginBottom: '24px'
              }}>
                <div style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(4, 1fr)',
                  gap: '24px'
                }}>
              {volumes.map((volume) => (
                <Card
                  key={volume.id}
                  hoverable
                  onClick={() => handleViewDetails(volume)}
                  style={{
                    border: '1px solid rgba(226, 232, 240, 0.8)',
                    borderRadius: '14px',
                    background: 'linear-gradient(145deg, #ffffff 0%, #fafbfc 100%)',
                    transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
                    boxShadow: '0 2px 8px rgba(15, 23, 42, 0.04)',
                    cursor: 'pointer',
                    height: '100%',
                    position: 'relative',
                    overflow: 'hidden'
                  }}
                  bodyStyle={{ padding: '16px' }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.boxShadow = '0 12px 24px rgba(59, 130, 246, 0.12)';
                    e.currentTarget.style.transform = 'translateY(-4px)';
                    e.currentTarget.style.borderColor = 'rgba(59, 130, 246, 0.3)';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.boxShadow = '0 2px 8px rgba(15, 23, 42, 0.04)';
                    e.currentTarget.style.transform = 'translateY(0)';
                    e.currentTarget.style.borderColor = 'rgba(226, 232, 240, 0.8)';
                  }}
                >
                  {/* 顶部装饰条 */}
                  <div style={{
                    position: 'absolute',
                    top: 0,
                    left: 0,
                    right: 0,
                    height: '3px',
                    background: 'linear-gradient(90deg, #3b82f6 0%, #8b5cf6 50%, #ec4899 100%)'
                  }} />

                  {/* 标题行 - 紧凑版 */}
                  <div style={{ marginBottom: 12 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
                      <div style={{
                        width: 36,
                        height: 36,
                        borderRadius: '10px',
                        background: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)',
                        color: '#fff',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: 15,
                        fontWeight: 700,
                        flexShrink: 0,
                        boxShadow: '0 3px 10px rgba(59, 130, 246, 0.25)'
                      }}>
                        {volume.volumeNumber}
                      </div>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{
                          fontSize: '14px',
                          fontWeight: 700,
                          color: '#0f172a',
                          marginBottom: 4,
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                          letterSpacing: '-0.01em'
                        }}>
                          {volume.title}
                        </div>
                        <Tag
                          style={{
                            background: 'linear-gradient(135deg, #ecfdf5 0%, #d1fae5 100%)',
                            color: '#059669',
                            border: '1px solid #a7f3d0',
                            borderRadius: '6px',
                            padding: '1px 8px',
                            fontSize: '11px',
                            fontWeight: 600
                          }}
                        >
                          ✓ 已生成
                        </Tag>
                      </div>
                    </div>
                  </div>

                  {/* 内容区域 - 紧凑版 */}
                  <div style={{ marginBottom: 12 }}>
                    <div style={{ marginBottom: 10 }}>
                      <div style={{
                        fontSize: '11px',
                        color: '#64748b',
                        marginBottom: 4,
                        fontWeight: 600,
                        textTransform: 'uppercase',
                        letterSpacing: '0.05em'
                      }}>
                        主题
                      </div>
                      <div style={{
                        fontSize: '12px',
                        color: '#334155',
                        lineHeight: '1.5',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        display: '-webkit-box',
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: 'vertical'
                      }}>
                        {volume.theme}
                      </div>
                    </div>

                    <div>
                      <div style={{
                        fontSize: '11px',
                        color: '#64748b',
                        marginBottom: 4,
                        fontWeight: 600,
                        textTransform: 'uppercase',
                        letterSpacing: '0.05em'
                      }}>
                        大纲预览
                      </div>
                      <div style={{
                        fontSize: '11px',
                        color: '#64748b',
                        lineHeight: '1.5',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        display: '-webkit-box',
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: 'vertical'
                      }}>
                        {volume.contentOutline || '暂无大纲'}
                      </div>
                    </div>
                  </div>

                  {/* 底部信息 - 紧凑版 */}
                  <div style={{
                    paddingTop: 10,
                    borderTop: '1px solid #f1f5f9',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    marginBottom: 10
                  }}>
                    <Text style={{ fontSize: '11px', color: '#64748b', fontWeight: 500 }}>
                      预计字数
                    </Text>
                    <Text strong style={{ fontSize: '13px', color: '#3b82f6', fontWeight: 700 }}>
                      {(volume.estimatedWordCount || 0).toLocaleString()} 字
                    </Text>
                  </div>

                  {/* 操作按钮 - 紧凑版 */}
                  <div style={{
                    display: 'flex',
                    gap: '8px'
                  }}>
                    <Button
                      size="small"
                      icon={<EyeOutlined />}
                      onClick={(e) => {
                        e.stopPropagation();
                        handleViewDetails(volume);
                      }}
                      style={{
                        flex: 1,
                        borderRadius: '8px',
                        border: '1.5px solid #e2e8f0',
                        height: '32px',
                        fontSize: '12px',
                        fontWeight: 500,
                        transition: 'all 0.2s ease'
                      }}
                      onMouseEnter={(e) => {
                        e.stopPropagation();
                        e.currentTarget.style.borderColor = '#3b82f6';
                        e.currentTarget.style.color = '#2563eb';
                        e.currentTarget.style.background = 'linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%)';
                      }}
                      onMouseLeave={(e) => {
                        e.stopPropagation();
                        e.currentTarget.style.borderColor = '#e2e8f0';
                        e.currentTarget.style.color = '';
                        e.currentTarget.style.background = '';
                      }}
                    >
                      查看
                    </Button>
                    <Button
                      size="small"
                      type="primary"
                      icon={<EditOutlined />}
                      onClick={(e) => {
                        e.stopPropagation();
                        enterWriting(volume);
                      }}
                      style={{
                        flex: 1,
                        borderRadius: '8px',
                        height: '32px',
                        fontSize: '12px',
                        background: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)',
                        border: 'none',
                        fontWeight: 600,
                        boxShadow: '0 3px 8px rgba(59, 130, 246, 0.2)',
                        transition: 'all 0.2s ease'
                      }}
                      onMouseEnter={(e) => {
                        e.stopPropagation();
                        e.currentTarget.style.background = 'linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%)';
                        e.currentTarget.style.boxShadow = '0 4px 12px rgba(59, 130, 246, 0.3)';
                      }}
                      onMouseLeave={(e) => {
                        e.stopPropagation();
                        e.currentTarget.style.background = 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)';
                        e.currentTarget.style.boxShadow = '0 3px 8px rgba(59, 130, 246, 0.2)';
                      }}
                    >
                      写作
                    </Button>
                  </div>
                </Card>
              ))}
                </div>
              </div>
            </div>
          </Card>

          {/* 重新生成中的遮罩层 - 背景虚化 + 弹窗 */}
          {isRegeneratingVolumes && (
            <>
              {/* 背景虚化层 */}
              <div style={{
                position: 'fixed',
                top: 0,
                left: 0,
                right: 0,
                bottom: 0,
                background: 'rgba(0, 0, 0, 0.5)',
                backdropFilter: 'blur(8px)',
                WebkitBackdropFilter: 'blur(8px)',
                zIndex: 998,
                animation: 'fadeIn 0.3s ease-in-out'
              }} />
              
              {/* 中心弹窗 */}
              <div style={{
                position: 'fixed',
                top: '50%',
                left: '50%',
                transform: 'translate(-50%, -50%)',
                zIndex: 999,
                background: 'rgba(255, 255, 255, 0.98)',
                borderRadius: '20px',
                boxShadow: '0 25px 80px rgba(0, 0, 0, 0.4)',
                border: '1px solid rgba(102, 126, 234, 0.3)',
                minWidth: '520px',
                maxWidth: '600px',
                animation: 'slideIn 0.4s ease-out'
              }}>
                <div style={{
                  textAlign: 'center',
                  padding: '48px 40px'
                }}>
                  <ReloadOutlined spin style={{ 
                    fontSize: '64px', 
                    color: '#667eea', 
                    marginBottom: '28px',
                    filter: 'drop-shadow(0 4px 12px rgba(102, 126, 234, 0.3))'
                  }} />
                  <Title level={3} style={{ 
                    marginBottom: '20px', 
                    color: '#1f2937', 
                    fontWeight: 600,
                    fontSize: '24px'
                  }}>
                    正在重新生成卷规划
                  </Title>
                  <Text type="secondary" style={{ 
                    display: 'block', 
                    marginBottom: '36px', 
                    fontSize: '15px', 
                    lineHeight: '1.6',
                    color: '#6b7280'
                  }}>
                    {regenerateProgress?.message || '正在处理中，请稍候...'}
                  </Text>
                  
                  {/* 使用假进度条，平滑动画 */}
                  <Progress
                    percent={Math.max(fakeProgress, regenerateProgress?.percentage || 0)}
                    strokeColor={{
                      '0%': '#667eea',
                      '50%': '#764ba2',
                      '100%': '#f093fb'
                    }}
                    strokeWidth={12}
                    style={{ marginBottom: '28px' }}
                    strokeLinecap="round"
                    trailColor="#e5e7eb"
                  />
                  
                  <div style={{
                    background: 'linear-gradient(135deg, #f0f4ff 0%, #e8edff 100%)',
                    padding: '18px 24px',
                    borderRadius: '12px',
                    border: '1px solid #d0d9ff',
                    boxShadow: '0 2px 8px rgba(102, 126, 234, 0.1)'
                  }}>
                    <Text style={{ 
                      fontSize: '13px', 
                      color: '#4b5563', 
                      lineHeight: '1.8',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: '8px'
                    }}>
                      <BulbOutlined style={{ fontSize: 16, color: '#667eea' }} />
                      正在清理旧卷数据并基于当前大纲重新拆分，请勿关闭页面
                    </Text>
                  </div>
                </div>
              </div>
            </>
          )}

          {/* 开始创作按钮 - 固定在底部中间，美化版 */}
          <div style={{
            position: 'fixed',
            bottom: '32px',
            left: '50%',
            transform: 'translateX(-50%)',
            zIndex: 1000,
            textAlign: 'center'
          }}>
            <Button
              type="primary"
              size="large"
              icon={<PlayCircleOutlined />}
              onClick={startWritingFromFirstVolume}
              disabled={volumes.length === 0}
              style={{
                height: '52px',
                padding: '0 40px',
                fontSize: '16px',
                fontWeight: 600,
                borderRadius: '26px',
                background: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 50%, #1d4ed8 100%)',
                border: 'none',
                boxShadow: '0 8px 24px rgba(59, 130, 246, 0.35), 0 4px 12px rgba(37, 99, 235, 0.2)',
                transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
                display: 'flex',
                alignItems: 'center',
                gap: '8px'
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.transform = 'translateY(-2px)';
                e.currentTarget.style.boxShadow = '0 12px 32px rgba(59, 130, 246, 0.45), 0 6px 16px rgba(37, 99, 235, 0.25)';
                e.currentTarget.style.background = 'linear-gradient(135deg, #2563eb 0%, #1d4ed8 50%, #1e40af 100%)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.transform = 'translateY(0)';
                e.currentTarget.style.boxShadow = '0 8px 24px rgba(59, 130, 246, 0.35), 0 4px 12px rgba(37, 99, 235, 0.2)';
                e.currentTarget.style.background = 'linear-gradient(135deg, #3b82f6 0%, #2563eb 50%, #1d4ed8 100%)';
              }}
            >
              开始创作
            </Button>
          </div>
        </div>
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

          {/* 大纲字数限制 */}
          <Form.Item
            name="outlineWordLimit"
            label="大纲字数限制"
            initialValue={2000}
            tooltip="控制生成的大纲长度，建议2000-4000字"
          >
            <InputNumber
              min={1000}
              max={10000}
              step={500}
              style={{ width: '100%' }}
              addonAfter="字"
            />
          </Form.Item>

          {/* 移除启用超级大纲模式勾选 */}

          {/* 移除模式说明提示，统一为流式大纲生成 */}

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="targetChapters"
                label="目标章数"
                initialValue={300}
              >
                <InputNumber
                  min={50}
                  max={1000}
                  style={{ width: '100%' }}
                  addonAfter="章"
                  onChange={(value) => {
                    const wordsPerChapter = generateForm.getFieldValue('wordsPerChapter') || 2000;
                    const total = (value || 300) * wordsPerChapter;
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
                initialValue={2000}
              >
                <InputNumber
                  min={2000}
                  max={10000}
                  style={{ width: '100%' }}
                  addonAfter="字/章"
                  onChange={(value) => {
                    const chapters = generateForm.getFieldValue('targetChapters') || 300;
                    const total = (value || 2000) * chapters;
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
            <strong>预计总字数：</strong>
            <span style={{ fontSize: '16px', fontWeight: 600, color: '#2563eb', marginLeft: '8px' }}>
              {(totalWordsGenerate / 10000).toFixed(1)}
            </span>
            <span> 万字</span>
            <span style={{ marginLeft: '8px', fontSize: '12px', color: '#3b82f6' }}>
              ({totalWordsGenerate.toLocaleString()}字)
            </span>
          </div>

          {/* 隐藏的总字数字段 */}
          <Form.Item name="targetWords" hidden initialValue={1000000}>
            <InputNumber />
          </Form.Item>

          <Form.Item
            name="volumeCount"
            label="预期卷数"
            rules={[{ required: true, message: '请选择卷数' }]}
            initialValue={5}
          >
            <InputNumber
              min={3}
              max={8}
              style={{ width: '100%' }}
                placeholder="建议3-8卷，默认5卷"
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 快速开始模态框 - 配置创作参数 - 苹果简约风格 */}
      <Modal
        title={null}
        open={quickStartVisible}
        onCancel={() => {
          setQuickStartVisible(false);
          if (isGeneratingOutline) {
            setIsGeneratingOutline(false);
            setLoading(false);
            setIsGenerating(false);
            message.info('已取消大纲生成');
          }
        }}
        onOk={() => outlineForm.submit()}
        confirmLoading={isGeneratingOutline}
        okText={isGeneratingOutline ? '生成中...' : '开始生成'}
        cancelText="取消"
        width={580}
        centered
        okButtonProps={{
          size: 'large',
          style: {
            background: 'var(--primary-600)',
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
        <div style={{ padding: '0' }}>
          {/* 头部 - 简约风格 */}
          <div style={{ textAlign: 'center', marginBottom: 24 }}>
            <div style={{
              width: 48,
              height: 48,
              borderRadius: 14,
              background: '#f8fafc',
              border: '1px solid #e2e8f0',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 16px'
            }}>
              <SettingOutlined style={{ fontSize: 22, color: '#334155' }} />
            </div>
            <h2 style={{
              fontSize: 20,
              fontWeight: 600,
              color: '#0f172a',
              margin: '0 0 8px',
              letterSpacing: '-0.01em'
            }}>
              创作参数设置
            </h2>
            <p style={{
              fontSize: 13,
              color: '#64748b',
              margin: 0,
              lineHeight: 1.6
            }}>
              用于生成故事大纲与分卷规划，后续流程仍可继续调整。
            </p>
          </div>

          <Form
            form={outlineForm}
            layout="vertical"
            onFinish={(values) => {
              handleGenerateVolumes(values);
              setQuickStartVisible(false);
            }}
          >
            <Row gutter={20}>
              <Col span={12}>
                <Form.Item
                  name="targetChapters"
                  label={
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '4px' }}>
                      <span style={{ fontSize: '14px', fontWeight: 600, color: '#334155' }}>预期总章数</span>
                      <Tooltip title="整本小说预计包含的章节数量">
                        <InfoCircleOutlined style={{ color: '#94a3b8', fontSize: '13px' }} />
                      </Tooltip>
                    </div>
                  }
                  initialValue={300}
                >
                  <InputNumber
                    min={50}
                    max={1000}
                    style={{ width: '100%', borderRadius: '8px' }}
                    addonAfter={<span style={{ color: '#64748b' }}>章</span>}
                    size="large"
                    onChange={(value) => {
                      const wordsPerChapter = outlineForm.getFieldValue('wordsPerChapter') || 2000;
                      const total = (value || 300) * wordsPerChapter;
                      setTotalWords(total);
                      outlineForm.setFieldValue('targetWords', total);
                    }}
                  />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  name="wordsPerChapter"
                  label={
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '4px' }}>
                      <span style={{ fontSize: '14px', fontWeight: 600, color: '#334155' }}>单章平均字数</span>
                      <Tooltip title="每一章节的预估字数">
                        <InfoCircleOutlined style={{ color: '#94a3b8', fontSize: '13px' }} />
                      </Tooltip>
                    </div>
                  }
                  initialValue={2000}
                >
                  <InputNumber
                    min={2000}
                    max={10000}
                    style={{ width: '100%', borderRadius: '8px' }}
                    addonAfter={<span style={{ color: '#64748b' }}>字/章</span>}
                    size="large"
                    onChange={(value) => {
                      const chapters = outlineForm.getFieldValue('targetChapters') || 300;
                      const total = (value || 2000) * chapters;
                      setTotalWords(total);
                      outlineForm.setFieldValue('targetWords', total);
                    }}
                  />
                </Form.Item>
              </Col>
            </Row>

            {/* 统计显示 - 苹果风格优化 */}
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '16px 20px',
              background: 'linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%)',
              borderRadius: '12px',
              border: '1px solid #e2e8f0',
              marginBottom: '24px'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <div style={{ 
                  width: '36px', 
                  height: '36px', 
                  borderRadius: '10px', 
                  background: '#e0e7ff', 
                  color: '#4f46e5',
                  display: 'flex', 
                  alignItems: 'center', 
                  justifyContent: 'center',
                  fontSize: '18px'
                }}>
                  <BarChartOutlined style={{ fontSize: 18 }} />
                </div>
                <div>
                  <div style={{ fontSize: '12px', color: '#64748b', fontWeight: 500 }}>预计全书规模</div>
                  <div style={{ fontSize: '13px', color: '#475569' }}>根据章数与字数自动计算</div>
                </div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <div style={{ fontSize: '20px', fontWeight: 700, color: '#0f172a', lineHeight: 1 }}>
                  {(totalWords / 10000).toFixed(1)} <span style={{ fontSize: '14px', fontWeight: 500, color: '#64748b' }}>万字</span>
                </div>
                <div style={{ fontSize: '12px', color: '#94a3b8', marginTop: '4px' }}>
                  ≈ {totalWords.toLocaleString()} 字
                </div>
              </div>
            </div>

            <Form.Item name="targetWords" hidden initialValue={1000000}>
              <InputNumber />
            </Form.Item>

            <Form.Item
              name="volumeCount"
              label={
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '4px' }}>
                  <span style={{ fontSize: '14px', fontWeight: 600, color: '#334155' }}>规划分卷数</span>
                  <Tooltip title="将整本小说划分为几大卷">
                    <InfoCircleOutlined style={{ color: '#94a3b8', fontSize: '13px' }} />
                  </Tooltip>
                </div>
              }
              rules={[{ required: true, message: '请选择卷数' }]}
              initialValue={5}
            >
              <InputNumber
                min={3}
                max={8}
                style={{ width: '100%', borderRadius: '8px' }}
                placeholder="建议3-8卷，默认5卷"
                addonAfter={<span style={{ color: '#64748b' }}>卷</span>}
                size="large"
              />
            </Form.Item>
            {/* 模板选择器 - 使用原生 select（更稳定，避免某些环境下 antd Select 无法展开） */}
            <Form.Item 
              name="templateId" 
              label={
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '4px' }}>
                  <span style={{ fontSize: '14px', fontWeight: 600, color: '#334155' }}>大纲模板</span>
                  <Tooltip title="选择一个模板来指导大纲生成结构">
                    <InfoCircleOutlined style={{ color: '#94a3b8', fontSize: '13px' }} />
                  </Tooltip>
                </div>
              }
            >
              <OutlineTemplateSelect
                value={selectedTemplateId}
                options={outlineTemplates}
                loading={loadingTemplates}
                onChange={(val) => {
                  setSelectedTemplateId(val);
                  outlineForm.setFieldValue('templateId', val);
                }}
              />

              {(() => {
                const selected = Array.isArray(outlineTemplates)
                  ? outlineTemplates.find((t: any) => t?.id === selectedTemplateId)
                  : null;
                return selected?.description ? (
                  <div className="vm-template-hint">{selected.description}</div>
                ) : null;
              })()}
            </Form.Item>

            {/* 隐藏的构思字段，从创建页面自动填充 */}
            <Form.Item name="basicIdea" hidden>
              <Input />
            </Form.Item>
          </Form>

          <Alert
            type="info"
            showIcon
            message="这些参数将影响大纲与分卷规划的生成效果，后续流程仍可继续调整。"
            style={{ marginTop: 16, borderRadius: 12 }}
          />
        </div>
      </Modal>

      {/* 卷详情弹窗 - 全新设计 */}
      <Modal
        open={detailModalVisible}
        onCancel={() => {
          setDetailModalVisible(false);
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
              background: 'var(--gradient-primary)',
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
                  <BookOutlined style={{ opacity: 0.9 }} />
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
                  <EditOutlined style={{ opacity: 0.9 }} />
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
                  justifyContent: 'space-between'
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <BookOutlined style={{ fontSize: 16, color: '#475569' }} />
                    卷信息
                  </div>
                  <Button
                    type="link"
                    size="small"
                    icon={<EditOutlined />}
                    onClick={() => {
                      // 创建一个临时的表单实例
                      let editFormInstance: any = null;

                      Modal.confirm({
                        title: '编辑卷信息',
                        width: 600,
                        icon: null,
                        content: (
                          <Form
                            layout="vertical"
                            initialValues={{
                              title: selectedVolume.title,
                              theme: selectedVolume.theme,
                              description: selectedVolume.description
                            }}
                            ref={(form) => { editFormInstance = form; }}
                          >
                            <Form.Item
                              name="title"
                              label="卷标题"
                              rules={[{ required: true, message: '请输入卷标题' }]}
                            >
                              <Input placeholder="请输入卷标题" />
                            </Form.Item>
                            <Form.Item
                              name="theme"
                              label="主线"
                              rules={[{ required: true, message: '请输入主线' }]}
                            >
                              <Input.TextArea rows={2} placeholder="高度概括本卷主线" />
                            </Form.Item>
                            <Form.Item
                              name="description"
                              label="描述"
                              rules={[{ required: true, message: '请输入描述' }]}
                            >
                              <Input.TextArea rows={8} placeholder="详细描述本卷的核心看点、核心冲突、进度等" />
                            </Form.Item>
                          </Form>
                        ),
                        okText: '保存',
                        cancelText: '取消',
                        onOk: async () => {
                          if (editFormInstance) {
                            try {
                              const values = await editFormInstance.validateFields();
                              await novelVolumeService.updateVolume(selectedVolume.id, values);
                              message.success('卷信息已更新');
                              setDetailModalVisible(false);
                              loadVolumes();
                            } catch (error: any) {
                              if (error.errorFields) {
                                // 表单验证失败
                                throw error;
                              } else {
                                // API 调用失败
                                message.error(error.response?.data?.message || '更新失败');
                                throw error;
                              }
                            }
                          }
                        }
                      });
                    }}
                  >
                    编辑
                  </Button>
                </div>

                <div style={{ marginBottom: 16 }}>
                  <div style={{ fontSize: '13px', color: '#6b7280', marginBottom: 4, fontWeight: 500 }}>
                    卷标题
                  </div>
                  <div style={{ fontSize: '14px', color: '#374151', lineHeight: 1.6 }}>
                    {selectedVolume.title}
                  </div>
                </div>

                <div style={{ marginBottom: 16 }}>
                  <div style={{ fontSize: '13px', color: '#6b7280', marginBottom: 4, fontWeight: 500 }}>
                    主线
                  </div>
                  <div style={{ fontSize: '14px', color: '#374151', lineHeight: 1.6 }}>
                    {selectedVolume.theme}
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: '13px', color: '#6b7280', marginBottom: 4, fontWeight: 500 }}>
                    大纲
                  </div>
                  <div style={{
                    fontSize: '14px',
                    lineHeight: 1.8,
                    color: '#334155',
                    whiteSpace: 'pre-wrap',
                    wordWrap: 'break-word',
                    wordBreak: 'break-word',
                    maxHeight: '400px',
                    overflowY: 'auto',
                    padding: '12px',
                    background: '#f8fafc',
                    borderRadius: '8px',
                    border: '1px solid #e2e8f0'
                  }}>
                    {selectedVolume.contentOutline || '暂无大纲'}
                  </div>
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
        okText="确认重新生成"
        cancelText="取消"
        okButtonProps={{
          danger: true
        }}
      >
        <div style={{ padding: '20px 0' }}>
          <Alert
            message="确认重新生成大纲？"
            description={
              <div>
                <p style={{ marginBottom: 8 }}>将使用您之前输入的创作构思和选择的模板重新生成完整大纲。</p>
                <p style={{ marginBottom: 8 }}>• 原有大纲内容将被完全覆盖</p>
                <p style={{ marginBottom: 8 }}>• AI将从零开始生成新的大纲</p>
                <p style={{ marginBottom: 0 }}>• 生成过程采用流式输出，您可以实时看到进度</p>
              </div>
            }
            type="warning"
            showIcon
            style={{ marginBottom: 16 }}
          />

          {currentSuperOutline?.basicIdea && (
            <div style={{
              padding: '12px 16px',
              background: '#f5f5f5',
              borderRadius: '8px',
              marginBottom: 16
            }}>
              <div style={{ fontSize: '13px', color: '#666', marginBottom: 4 }}>原始构思：</div>
              <div style={{ fontSize: '14px', color: '#333', lineHeight: '1.6' }}>
                {currentSuperOutline.basicIdea.length > 200
                  ? currentSuperOutline.basicIdea.substring(0, 200) + '...'
                  : currentSuperOutline.basicIdea}
              </div>
            </div>
          )}

          {(() => {
            try {
              const paramsStr = localStorage.getItem(`novel_${novelId}_generation_params`);
              if (paramsStr) {
                const savedParams = JSON.parse(paramsStr);
                const template = outlineTemplates.find((t: any) => t.id === savedParams.templateId);
                if (template) {
                  return (
                    <div style={{
                      padding: '12px 16px',
                      background: '#e6f7ff',
                      borderRadius: '8px',
                      marginBottom: 16,
                      border: '1px solid #91d5ff'
                    }}>
                      <div style={{ fontSize: '13px', color: '#666', marginBottom: 4 }}>使用模板：</div>
                      <div style={{ fontSize: '14px', color: '#1890ff', fontWeight: 500 }}>
                        {template.name}
                      </div>
                      {template.description && (
                        <div style={{ fontSize: '12px', color: '#666', marginTop: 4 }}>
                          {template.description}
                        </div>
                      )}
                    </div>
                  );
                }
              }
            } catch (e) {
              console.warn('读取模板信息失败', e);
            }
            return null;
          })()}

          <Alert
            message="提示"
            description="如果您想修改构思或模板后再生成，可以点击下方“修改模板和参数”按钮，打开配置弹窗。"
            type="info"
            showIcon
          />

          <div style={{ marginTop: 16, textAlign: 'right' }}>
            <Button
              type="link"
              size="small"
              onClick={() => {
                try {
                  const paramsStr = localStorage.getItem(`novel_${novelId}_generation_params`);
                  let savedParams: any = {};
                  if (paramsStr) {
                    savedParams = JSON.parse(paramsStr);
                  }

                  const basicIdea = (currentSuperOutline as any)?.basicIdea || novel?.description || '';
                  const targetChapters = (novel as any)?.targetTotalChapters || savedParams.targetChapters || 300;
                  const wordsPerChapter = (novel as any)?.wordsPerChapter || savedParams.wordsPerChapter || 2000;
                  const targetWords = (novel as any)?.totalWordTarget || savedParams.targetWords || (targetChapters * wordsPerChapter);
                  const volumeCount = (novel as any)?.plannedVolumeCount || savedParams.volumeCount || 5;

                  outlineForm.setFieldsValue({
                    basicIdea,
                    targetChapters,
                    wordsPerChapter,
                    targetWords,
                    volumeCount,
                    templateId: savedParams.templateId
                  });

                  if (savedParams.templateId) {
                    setSelectedTemplateId(savedParams.templateId);
                  }
                } catch (e) {
                  console.warn('预填快速开始参数失败', e);
                }

                setOutlineGenerationVisible(false);
                setQuickStartVisible(true);
                loadOutlineTemplates();
              }}
            >
              修改模板和参数（打开配置弹窗）
            </Button>
          </div>
        </div>
      </Modal>

      {/* 快速开始区域 - 仅在无卷时展示 */}
      {/* 已移除底部重复的"开始您的创作之旅"块 */}
    </div>
  );
};

export default VolumeManagementPage;






