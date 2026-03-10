import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, Spin, Tag, Tooltip, message } from 'antd';
import {
  AimOutlined,
  ArrowLeftOutlined,
  BarChartOutlined,
  BulbOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  CodeOutlined,
  DownloadOutlined,
  FileTextOutlined,
  HistoryOutlined,
  InfoCircleOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  RocketOutlined,
  SafetyCertificateOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import ReactMarkdown from 'react-markdown';
import {
  shortStoryService,
  ShortChapter,
  ShortNovel,
  WorkflowLog,
  WorkflowStateResponse,
  WorkflowStep,
  WorkflowStepStatus,
} from '../../services/shortStoryService';
import api from '../../services/api';
import './ShortStoryWorkflowPage.css';

interface AIModel {
  id: number;
  modelId: string;
  displayName: string;
  provider: string;
  isDefault: boolean;
}

type ViewMode = { type: 'workflow' } | { type: 'chapter'; chapterNumber: number };

type CanvasNode = {
  key: string;
  title: string;
  subtitle?: string;
  status: WorkflowStepStatus;
  chapterNumber?: number | null;
  x: number;
  y: number;
};

type CanvasEdge = {
  from: string;
  to: string;
  type: 'flow' | 'loop';
  label?: string;
};

const NODE_W = 280;
const NODE_H = 110;

const STATUS_LABEL: Record<WorkflowStepStatus, string> = {
  PENDING: '待命',
  RUNNING: '进行中',
  COMPLETED: '完成',
  FAILED: '失败',
};

const STATUS_TAG_COLOR: Record<WorkflowStepStatus, string> = {
  PENDING: 'default',
  RUNNING: 'processing',
  COMPLETED: 'success',
  FAILED: 'error',
};

function safeJsonParse<T>(raw?: string | null): T | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

function extractData(res: any) {
  if (!res) return res;
  if (res.data && typeof res.data === 'object' && !Array.isArray(res)) return res.data;
  if (Array.isArray(res.data)) return res.data;
  return res;
}

function parseChapterStage(key: string): { chapterNumber: number; stage: string } | null {
  const m = key.match(/^CHAPTER_(\d+)_(GENERATE|REVIEW|ANALYZE|DECIDE|COMMIT)$/);
  if (!m) return null;
  return { chapterNumber: Number(m[1]), stage: m[2] };
}

function clamp(n: number, min: number, max: number) {
  return Math.max(min, Math.min(max, n));
}

const ShortStoryWorkflowPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const novelId = Number(id);

  const [novel, setNovel] = useState<ShortNovel | null>(null);
  const [chapters, setChapters] = useState<ShortChapter[]>([]);
  const [logs, setLogs] = useState<WorkflowLog[]>([]);
  const [workflow, setWorkflow] = useState<WorkflowStateResponse | null>(null);

  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);

  const [view, setView] = useState<ViewMode>({ type: 'workflow' });
  const [selectedNodeKey, setSelectedNodeKey] = useState<string | null>(null);
  const [logSearch] = useState('');
  const [rightPanelTab, setRightPanelTab] = useState<'output' | 'logs'>('output');
  
  // 章节内容编辑状态
  const [editingContent, setEditingContent] = useState<{ chapterNumber: number; content: string } | null>(null);
  const [savingContent, setSavingContent] = useState(false);
  const saveTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  
  // 模型选择相关
  const [models, setModels] = useState<AIModel[]>([]);
  const [selectedModelId, setSelectedModelId] = useState<string | null>(null);
  const [loadingModels, setLoadingModels] = useState(false);

  // Canvas pan/zoom
  const viewportRef = useRef<HTMLDivElement | null>(null);
  const [pan, setPan] = useState({ x: 60, y: 40 });
  const [scale, setScale] = useState(1);
  const dragRef = useRef<null | { startX: number; startY: number; panX: number; panY: number }>(null);

  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const activeStep = workflow?.activeStep || novel?.activeStep || null;

  // 保存章节内容
  const saveChapterContent = useCallback(async (chapterNumber: number, content: string) => {
    if (!Number.isFinite(novelId)) return;
    try {
      setSavingContent(true);
      await shortStoryService.updateChapterContent(novelId, chapterNumber, content);
      // 更新本地章节数据
      setChapters(prev => prev.map(c => 
        c.chapterNumber === chapterNumber ? { ...c, content, wordCount: content.length } : c
      ));
    } catch (e) {
      message.error('保存失败');
      console.error(e);
    } finally {
      setSavingContent(false);
    }
  }, [novelId]);

  // 防抖保存
  const debouncedSave = useCallback((chapterNumber: number, content: string) => {
    if (saveTimeoutRef.current) {
      clearTimeout(saveTimeoutRef.current);
    }
    saveTimeoutRef.current = setTimeout(() => {
      saveChapterContent(chapterNumber, content);
    }, 1000); // 1秒后自动保存
  }, [saveChapterContent]);

  // 处理内容编辑
  const handleContentChange = useCallback((chapterNumber: number, newContent: string) => {
    setEditingContent({ chapterNumber, content: newContent });
    debouncedSave(chapterNumber, newContent);
  }, [debouncedSave]);

  // 获取章节显示内容（优先编辑中的内容）
  const getChapterContent = useCallback((ch: ShortChapter) => {
    if (editingContent && editingContent.chapterNumber === ch.chapterNumber) {
      return editingContent.content;
    }
    return ch.content || '';
  }, [editingContent]);

  const stepMap = useMemo(() => {
    const m = new Map<string, WorkflowStep>();
    (workflow?.steps || []).forEach((s) => m.set(s.key, s));
    return m;
  }, [workflow?.steps]);

  const currentChapterNum = novel?.currentChapter || 1;

  // 计算当前应该高亮的节点（映射到 Loop 节点）
  const activeLoopNodeKey = useMemo(() => {
    if (!activeStep) return null;
    if (['STORY_SETTING', 'OUTLINE', 'HOOKS', 'PROLOGUE'].includes(activeStep)) return activeStep;
    
    // 解析 CHAPTER_N_STAGE
    const parsed = parseChapterStage(activeStep);
    if (parsed) {
      return `LOOP_${parsed.stage}`; // 映射到通用节点
    }
    return null;
  }, [activeStep]);

  const nodes: CanvasNode[] = useMemo(() => {
    // 布局配置 - 三列布局
    const gapX = 320; // 水平间距
    const gapY = 160; // 垂直间距
    const startX = 80;
    const startY = 80;
    
    const list: CanvasNode[] = [];

    // 1. 固定前置节点 - 第一行，三个节点
    const globalKeys: Array<'STORY_SETTING' | 'OUTLINE' | 'HOOKS'> = ['STORY_SETTING', 'OUTLINE', 'HOOKS'];
    
    globalKeys.forEach((k, idx) => {
      const s = stepMap.get(k);
      list.push({
        key: k,
        title: s?.name || k,
        subtitle: s?.description,
        status: (s?.status as WorkflowStepStatus) || 'PENDING',
        chapterNumber: null,
        x: startX + idx * gapX,
        y: startY,
      });
    });

    // 2. 导语节点 - 第二行（居中单个节点）
    const prologueStep = stepMap.get('PROLOGUE');
    list.push({
      key: 'PROLOGUE',
      title: prologueStep?.name || '生成导语',
      subtitle: prologueStep?.description || '黄金开头',
      status: (prologueStep?.status as WorkflowStepStatus) || 'PENDING',
      chapterNumber: null,
      x: startX + gapX, // 居中
      y: startY + gapY,
    });

    // 3. 循环节点 - 第三行三个 + 第四行两个
    const stages: Array<'GENERATE' | 'REVIEW' | 'ANALYZE' | 'DECIDE' | 'COMMIT'> = [
      'GENERATE',
      'REVIEW',
      'ANALYZE',
      'DECIDE',
      'COMMIT',
    ];

    stages.forEach((st, idx) => {
      const realKey = `CHAPTER_${currentChapterNum}_${st}`;
      const s = stepMap.get(realKey);
      
      // 计算位置：第三行 3 个，第四行 2 个
      const row = idx < 3 ? 2 : 3;
      const col = idx < 3 ? idx : idx - 3;
      
      // 第四行居中对齐（只有 2 个节点）
      const offsetX = row === 3 ? gapX / 2 : 0;
      
      list.push({
        key: `LOOP_${st}`,
        title: getStageTitle(st),
        subtitle: `第 ${currentChapterNum} 章 · ${getStageSubtitle(st)}`,
        status: (s?.status as WorkflowStepStatus) || 'PENDING',
        chapterNumber: currentChapterNum,
        x: startX + col * gapX + offsetX,
        y: startY + row * gapY,
      });
    });

    return list;
  }, [stepMap, currentChapterNum]);

  const edges: CanvasEdge[] = useMemo(() => {
    return [
      // 第一行：前置节点
      { from: 'STORY_SETTING', to: 'OUTLINE', type: 'flow' },
      { from: 'OUTLINE', to: 'HOOKS', type: 'flow' },
      
      // 导语节点
      { from: 'HOOKS', to: 'PROLOGUE', type: 'flow' },
      
      // 进入循环
      { from: 'PROLOGUE', to: 'LOOP_GENERATE', type: 'flow' },
      
      // 第三行：循环节点 1-3
      { from: 'LOOP_GENERATE', to: 'LOOP_REVIEW', type: 'flow' },
      { from: 'LOOP_REVIEW', to: 'LOOP_ANALYZE', type: 'flow' },
      
      // 第三行 -> 第四行
      { from: 'LOOP_ANALYZE', to: 'LOOP_DECIDE', type: 'flow' },
      
      // 第四行
      { from: 'LOOP_DECIDE', to: 'LOOP_COMMIT', type: 'flow' },
      
      // 循环回连
      { from: 'LOOP_COMMIT', to: 'LOOP_GENERATE', type: 'loop', label: '下一章' },
    ];
  }, []);

  const nodeByKey = useMemo(() => {
    const m = new Map<string, CanvasNode>();
    nodes.forEach((n) => m.set(n.key, n));
    return m;
  }, [nodes]);

  // 辅助函数
  function getStageTitle(st: string) {
    const map: Record<string, string> = {
      GENERATE: 'AI 撰写',
      REVIEW: '自动审核',
      ANALYZE: '深度分析',
      DECIDE: '决策/优化',
      COMMIT: '定稿归档'
    };
    return map[st] || st;
  }

  function getStageSubtitle(st: string) {
     const map: Record<string, string> = {
      GENERATE: '生成正文与核心',
      REVIEW: '评分与基础检查',
      ANALYZE: '连贯性与大纲校准',
      DECIDE: '判断是否推进或重写',
      COMMIT: '写入数据库并更新大纲'
    };
    return map[st] || '';
  }

  const worldSize = useMemo(() => {
    let maxX = 0;
    let maxY = 0;
    for (const n of nodes) {
      maxX = Math.max(maxX, n.x + NODE_W + 160);
      maxY = Math.max(maxY, n.y + NODE_H + 160);
    }
    return { width: Math.max(maxX, 1400), height: Math.max(maxY, 800) };
  }, [nodes]);

  // Helper for Node Icons
  const getNodeIcon = (key: string) => {
    if (key === 'STORY_SETTING') return <BulbOutlined style={{ fontSize: 16, color: '#6366f1' }} />;
    if (key === 'OUTLINE') return <FileTextOutlined style={{ fontSize: 16, color: '#8b5cf6' }} />;
    if (key === 'HOOKS') return <AimOutlined style={{ fontSize: 16, color: '#ec4899' }} />;
    if (key === 'PROLOGUE') return <RocketOutlined style={{ fontSize: 16, color: '#f97316' }} />;
    if (key.includes('GENERATE')) return <FileTextOutlined style={{ fontSize: 16, color: '#3b82f6' }} />;
    if (key.includes('REVIEW')) return <SafetyCertificateOutlined style={{ fontSize: 16, color: '#f59e0b' }} />;
    if (key.includes('ANALYZE')) return <BarChartOutlined style={{ fontSize: 16, color: '#10b981' }} />;
    if (key.includes('DECIDE')) return <BulbOutlined style={{ fontSize: 16, color: '#6366f1' }} />;
    if (key.includes('COMMIT')) return <CheckCircleOutlined style={{ fontSize: 16, color: '#10b981' }} />;
    return <InfoCircleOutlined />;
  };

  const workflowProgress = useMemo(() => {
    const steps = workflow?.steps || [];
    if (steps.length === 0) return 0;
    const done = steps.filter((s) => s.status === 'COMPLETED').length;
    return Math.round((done / steps.length) * 100);
  }, [workflow?.steps]);

  // --- Data fetching & polling ---
  const stopPolling = () => {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  };

  const loadDataSilent = async () => {
    if (!Number.isFinite(novelId)) return;
    try {
      const [novelRes, chaptersRes, logsRes, wfRes] = await Promise.all([
        shortStoryService.get(novelId),
        shortStoryService.getChapters(novelId),
        shortStoryService.getLogs(novelId, 0, 200),
        shortStoryService.getWorkflowState(novelId),
      ]);

      const rawNovel = extractData(novelRes);
      const rawChapters = extractData(chaptersRes);
      const rawLogs = extractData(logsRes);
      const rawWf = extractData(wfRes);

      setNovel(rawNovel?.data ? rawNovel.data : rawNovel);
      setChapters(Array.isArray(rawChapters) ? rawChapters : []);
      setLogs(Array.isArray(rawLogs) ? rawLogs : rawLogs?.content || []);
      setWorkflow(rawWf?.data ? rawWf.data : rawWf);

      // 初次/无选择时，把选中节点对齐到当前 activeStep
      const nextActive = (rawWf?.activeStep || rawNovel?.activeStep) as string | undefined;
      setSelectedNodeKey((prev) => prev || nextActive || 'HOOKS');
    } catch (e) {
      console.error(e);
      stopPolling();
    }
  };

  const loadData = async () => {
    setLoading(true);
    try {
      await loadDataSilent();
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!Number.isFinite(novelId)) return;
    loadData();
    return () => stopPolling();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [novelId]);

  // 加载模型列表
  useEffect(() => {
    const loadModels = async () => {
      setLoadingModels(true);
      try {
        const res = await api.get<any[]>('/ai-tasks/models');
        const data = (res as any).data || res;
        const rawList = Array.isArray(data) ? data : [];
        
        // 转换 API 返回的格式到组件需要的格式
        const modelList: AIModel[] = rawList.map((m: any) => ({
          id: 0,
          modelId: m.id || m.modelId,
          displayName: m.name || m.displayName,
          provider: m.provider || '',
          isDefault: m.isDefault || false,
        }));
        setModels(modelList);
        
        // 设置默认模型（取第一个）
        if (modelList.length > 0) {
          setSelectedModelId(modelList[0].modelId);
        }
      } catch (e) {
        console.error('加载模型列表失败', e);
      } finally {
        setLoadingModels(false);
      }
    };
    loadModels();
  }, []);

  useEffect(() => {
    const status = novel?.status;
    const shouldPoll = status === 'WORKFLOW_RUNNING' || status === 'GENERATING_OUTLINE';
    const shouldStop = status === 'WORKFLOW_PAUSED' || status === 'COMPLETED' || status === 'FAILED';

    if (shouldPoll && !pollRef.current) {
      pollRef.current = setInterval(loadDataSilent, 3000);
    }
    if (shouldStop) {
      stopPolling();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [novel?.status]);

  // --- Actions ---
  const handleStart = async (modelId?: string) => {
    if (!Number.isFinite(novelId)) return;
    try {
      setActionLoading(true);
      // 如果是 DRAFT 状态且有选择模型，先保存模型配置
      if (novel?.status === 'DRAFT' && modelId) {
        await shortStoryService.updateConfig(novelId, { modelId });
      }
      await shortStoryService.start(novelId);
      message.success('已开始/继续工作流');
      await loadDataSilent();
    } catch (e) {
      message.error((e as any)?.message || '启动失败');
    } finally {
      setActionLoading(false);
    }
  };

  const handlePause = async () => {
    if (!Number.isFinite(novelId)) return;
    try {
      setActionLoading(true);
      await shortStoryService.pause(novelId);
      message.success('已暂停');
      await loadDataSilent();
    } catch (e) {
      message.error((e as any)?.message || '暂停失败');
    } finally {
      setActionLoading(false);
    }
  };

  const handleRetry = async (chapterNumber: number) => {
    if (!Number.isFinite(novelId)) return;
    try {
      setActionLoading(true);
      await shortStoryService.retry(novelId, chapterNumber);
      message.success(`已请求重试第 ${chapterNumber} 章`);
      await loadDataSilent();
      setView({ type: 'workflow' });
      setSelectedNodeKey(`CHAPTER_${chapterNumber}_GENERATE`);
    } catch (e) {
      message.error((e as any)?.message || '重试失败');
    } finally {
      setActionLoading(false);
    }
  };

  // 导出所有章节内容
  const handleExport = () => {
    if (!novel || !chapters.length) {
      message.warning('没有可导出的内容');
      return;
    }

    const completedChapters = chapters
      .filter((c) => c.content && c.content.trim().length > 0)
      .sort((a, b) => a.chapterNumber - b.chapterNumber);

    if (completedChapters.length === 0) {
      message.warning('没有已完成的章节可导出');
      return;
    }

    // 生成导出内容
    let content = `${novel.title}\n`;
    content += `${'='.repeat(40)}\n\n`;
    
    // 添加小说简介
    if (novel.idea) {
      content += `【简介】\n${novel.idea}\n\n`;
    }
    
    content += `${'='.repeat(40)}\n\n`;

    // 添加每个章节
    completedChapters.forEach((ch) => {
      content += `第 ${ch.chapterNumber} 章  ${ch.title || ''}\n`;
      content += `${'-'.repeat(30)}\n\n`;
      content += `${ch.content}\n\n\n`;
    });

    // 添加统计信息
    const totalWords = completedChapters.reduce((sum, ch) => sum + (ch.wordCount || 0), 0);
    content += `${'='.repeat(40)}\n`;
    content += `全文完\n`;
    content += `共 ${completedChapters.length} 章，${totalWords} 字\n`;
    content += `导出时间：${dayjs().format('YYYY-MM-DD HH:mm:ss')}\n`;

    // 创建 Blob 并下载
    const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${novel.title || '短篇小说'}_${dayjs().format('YYYYMMDD_HHmm')}.txt`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);

    message.success(`已导出 ${completedChapters.length} 章，共 ${totalWords} 字`);
  };

  // --- Canvas interactions ---
  useEffect(() => {
    const onMove = (ev: PointerEvent) => {
      if (!dragRef.current) return;
      const dx = ev.clientX - dragRef.current.startX;
      const dy = ev.clientY - dragRef.current.startY;
      setPan({ x: dragRef.current.panX + dx, y: dragRef.current.panY + dy });
    };
    const onUp = () => {
      dragRef.current = null;
    };

    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
    return () => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
    };
  }, []);

  const onViewportPointerDown = (e: React.PointerEvent) => {
    const target = e.target as HTMLElement;
    if (target.closest('.ssw-node')) return;
    dragRef.current = { startX: e.clientX, startY: e.clientY, panX: pan.x, panY: pan.y };
  };

  const onViewportWheel = (e: React.WheelEvent) => {
    e.preventDefault();
    const viewport = viewportRef.current;
    if (!viewport) return;

    const rect = viewport.getBoundingClientRect();
    const cx = e.clientX - rect.left;
    const cy = e.clientY - rect.top;

    const nextScale = clamp(scale * (e.deltaY > 0 ? 0.92 : 1.08), 0.45, 1.9);
    const ratio = nextScale / scale;

    // zoom around cursor
    const dx = cx - pan.x;
    const dy = cy - pan.y;
    const nextPan = {
      x: cx - dx * ratio,
      y: cy - dy * ratio,
    };

    setScale(nextScale);
    setPan(nextPan);
  };

  const centerOnNode = (nodeKey: string) => {
    const viewport = viewportRef.current;
    if (!viewport) return;
    const node = nodeByKey.get(nodeKey);
    if (!node) return;

    const rect = viewport.getBoundingClientRect();
    const centerX = rect.width / 2;
    const centerY = rect.height / 2;

    const nx = node.x + NODE_W / 2;
    const ny = node.y + NODE_H / 2;

    setPan({
      x: centerX - nx * scale,
      y: centerY - ny * scale,
    });
  };

  // 自动选择节点逻辑
  useEffect(() => {
     if (activeLoopNodeKey) {
       setSelectedNodeKey(activeLoopNodeKey);
     }
  }, [activeLoopNodeKey]);

  // 修改：filteredLogs 逻辑
  const filteredLogs = useMemo(() => {
    const q = logSearch.trim();
    let sel = selectedNodeKey;
    
    // 如果选中的是 Loop 节点，需要知道它代表当前第几章
    let chapterFilter: number | null = null;
    
    if (sel && sel.startsWith('LOOP_')) {
       chapterFilter = currentChapterNum;
    } else if (sel) {
       // 如果是具体 CHAPTER_N_KEY (虽然现在 canvas 只有 Loop 节点，但兼容一下)
       const p = parseChapterStage(sel);
       if (p) chapterFilter = p.chapterNumber;
    }

    return (logs || [])
      .filter((l) => {
        if (chapterFilter != null) {
          // 只看该章节日志，或者全局日志(null)
          return l.chapterNumber === chapterFilter || l.chapterNumber === null;
        }
        return true;
      })
      .filter((l) => {
        if (!q) return true;
        return (l.content || '').toLowerCase().includes(q.toLowerCase());
      });
  }, [logs, logSearch, selectedNodeKey, currentChapterNum]);

  const currentChapter = useMemo(() => {
    if (view.type !== 'chapter') return null;
    return chapters.find((c) => c.chapterNumber === view.chapterNumber) || null;
  }, [chapters, view]);

  // 选中节点时的处理
  // 如果是 Loop 节点，需要映射回当前章节的真实 Key 来获取数据
  const renderNodeOutput = () => {
    let key = selectedNodeKey;
    if (!key) return <div className="ssw-empty">请选择一个节点查看输出</div>;
    
    // 映射 Loop key -> Real key
    if (key.startsWith('LOOP_')) {
      const stage = key.replace('LOOP_', '');
      key = `CHAPTER_${currentChapterNum}_${stage}`;
    }

    if (key === 'STORY_SETTING') {
      const md = novel?.storySetting || '';
      return md ? (
        <div className="ssw-md">
          <ReactMarkdown>{md}</ReactMarkdown>
        </div>
      ) : (
        <div className="ssw-empty">故事设定尚未生成</div>
      );
    }

    if (key === 'OUTLINE') {
      const md = novel?.outline || '';
      return md ? (
        <div className="ssw-md">
          <ReactMarkdown>{md}</ReactMarkdown>
        </div>
      ) : (
        <div className="ssw-empty">大纲尚未生成</div>
      );
    }

    if (key === 'HOOKS') {
      const hooks = safeJsonParse<any[]>(novel?.hooksJson || null);
      const data = Array.isArray(hooks)
        ? hooks
        : chapters
            .filter((c) => c.chapterNumber >= 1)
            .map((c) => ({ chapterNumber: c.chapterNumber, title: c.title, core: c.brief }));

      return (
        <div className="ssw-hooks">
          <div className="ssw-hooks-tip">点击左侧章节可直接进入章节阅读；这里展示的是“看点标题 + 一句话核心”产物。</div>
          <div className="ssw-hooks-list">
            {data.map((h, idx) => (
              <div key={idx} className="ssw-hook-item">
                <div className="ssw-hook-head">
                  <span className="ssw-hook-no">第 {h.chapterNumber} 章</span>
                  <span className="ssw-hook-title">{h.title}</span>
                </div>
                <div className="ssw-hook-core">{h.core}</div>
                {Array.isArray(h.hookPoints) && h.hookPoints.length > 0 && (
                  <div className="ssw-hook-points">
                    {h.hookPoints.slice(0, 6).map((p: string, i: number) => (
                      <span key={i} className="ssw-chip">
                        {p}
                      </span>
                    ))}
                  </div>
                )}
                {h.cliffhanger && <div className="ssw-hook-cliff">结尾钩子：{h.cliffhanger}</div>}
              </div>
            ))}
          </div>
        </div>
      );
    }

    if (key === 'PROLOGUE') {
      const prologue = novel?.prologue || '';
      return prologue ? (
        <div className="ssw-output">
          <div className="ssw-section">
            <div className="ssw-section-title">🎬 导语（黄金开头）</div>
            <div className="ssw-md">
              <ReactMarkdown>{prologue}</ReactMarkdown>
            </div>
          </div>
          <div className="ssw-hooks-tip" style={{ marginTop: 12 }}>
            导语是故事的第一印象，用于吸引读者继续阅读。生成时会从 6 种钩子算法中选择最佳方案。
          </div>
        </div>
      ) : (
        <div className="ssw-empty">导语尚未生成</div>
      );
    }

    const parsed = parseChapterStage(key);
    if (!parsed) return <div className="ssw-empty">未知节点</div>;

    const ch = chapters.find((c) => c.chapterNumber === parsed.chapterNumber);
    if (!ch) return <div className="ssw-empty">章节数据不存在</div>;

    if (parsed.stage === 'GENERATE') {
      return (
        <div className="ssw-output">
          <div className="ssw-section">
            <div className="ssw-section-title">本章标题</div>
            <div className="ssw-kv">{ch.title}</div>
          </div>
          <div className="ssw-section">
            <div className="ssw-section-title">看点核心</div>
            <div className="ssw-kv">{ch.brief}</div>
          </div>
          {ch.lastAdjustment && (
            <div className="ssw-section">
              <div className="ssw-section-title">返工指令（上一轮审稿回灌）</div>
              <pre className="ssw-pre">{ch.lastAdjustment}</pre>
            </div>
          )}
          <div className="ssw-section ssw-section-content">
            <div className="ssw-section-title">
              最新正文输出
              {savingContent && <span className="ssw-saving-badge">保存中...</span>}
            </div>
            <textarea
              className="ssw-content-editor"
              value={getChapterContent(ch)}
              onChange={(e) => handleContentChange(ch.chapterNumber, e.target.value)}
              placeholder="暂无正文，可直接输入..."
            />
          </div>
          {ch.status === 'FAILED' && (
            <div className="ssw-warn">
              本章已失败，可在左侧章节列表点击“重试”。
            </div>
          )}
          <div className="ssw-actions-row">
            <Button size="small" onClick={() => setView({ type: 'chapter', chapterNumber: ch.chapterNumber })}>
              打开章节阅读
            </Button>
            {ch.status === 'FAILED' && (
              <Button size="small" danger onClick={() => handleRetry(ch.chapterNumber)}>
                重试生成
              </Button>
            )}
          </div>
        </div>
      );
    }

    if (parsed.stage === 'REVIEW') {
      const review = safeJsonParse<any>(ch.reviewResult || null);
      return (
        <div className="ssw-output">
          {!review ? (
            <div className="ssw-empty">暂无审稿结果</div>
          ) : (
            <>
              <div className="ssw-review-score">
                <span>评分</span>
                <span className="ssw-score">{review.score ?? '-'} / 10</span>
                <Tag color={review.passed ? 'success' : 'error'}>{review.passed ? '通过' : '未通过'}</Tag>
              </div>
              <div className="ssw-section">
                <div className="ssw-section-title">评价</div>
                <div className="ssw-kv">{review.comments || '-'}</div>
              </div>
              <div className="ssw-section">
                <div className="ssw-section-title">建议</div>
                <div className="ssw-kv">{review.suggestions || '-'}</div>
              </div>
            </>
          )}
           <div className="ssw-actions-row">
             <Button size="small" onClick={() => setView({ type: 'chapter', chapterNumber: ch.chapterNumber })}>
              查看章节
            </Button>
           </div>
        </div>
      );
    }

    if (parsed.stage === 'ANALYZE' || parsed.stage === 'DECIDE') {
      const analysis = safeJsonParse<any>(ch.analysisResult || null);
      return (
        <div className="ssw-output">
          {!analysis ? (
            <div className="ssw-empty">暂无分析结果</div>
          ) : (
            <>
              {/* 记忆锚点（剧情摘要） */}
              {analysis.chapterSummary && (
                <div className="ssw-section">
                  <div className="ssw-section-title">📌 记忆锚点（供后续章节使用）</div>
                  <div className="ssw-kv" style={{ background: '#f0fdf4', padding: '12px', borderRadius: '8px', border: '1px solid #bbf7d0' }}>
                    {analysis.chapterSummary}
                  </div>
                </div>
              )}
              <div className="ssw-section">
                <div className="ssw-section-title">本章看点</div>
                <div className="ssw-chips">
                  {(analysis.highlightPoints || []).slice(0, 10).map((p: string, i: number) => (
                    <span key={i} className="ssw-chip">{p}</span>
                  ))}
                </div>
              </div>
              <div className="ssw-section">
                <div className="ssw-section-title">连续性风险</div>
                <div className="ssw-kv">{(analysis.continuityRisks || []).join('；') || '-'}</div>
              </div>
              <div className="ssw-section">
                <div className="ssw-section-title">下一章推进目标</div>
                <div className="ssw-kv">{analysis.nextChapterFocus || '-'}</div>
              </div>
              <div className="ssw-section">
                <div className="ssw-section-title">是否需要调整</div>
                <div className="ssw-kv">
                  <Tag color={analysis.needOutlineUpdate ? 'warning' : 'default'}>
                    大纲：{analysis.needOutlineUpdate ? '建议更新' : '无需更新'}
                  </Tag>
                  <Tag color={analysis.needHookUpdate ? 'warning' : 'default'}>
                    后续看点：{analysis.needHookUpdate ? '建议更新' : '无需更新'}
                  </Tag>
                </div>
              </div>
              {(analysis.outlineUpdateSuggestion || analysis.hookUpdateGuidance) && (
                <div className="ssw-section">
                  <div className="ssw-section-title">建议</div>
                  <pre className="ssw-pre">{String(analysis.outlineUpdateSuggestion || analysis.hookUpdateGuidance)}</pre>
                </div>
              )}
            </>
          )}
        </div>
      );
    }

    if (parsed.stage === 'COMMIT') {
      return (
        <div className="ssw-output">
          <div className="ssw-section">
            <div className="ssw-section-title">章节状态</div>
            <div className="ssw-kv">
              <Tag>{ch.status}</Tag>
              <span style={{ marginLeft: 8 }}>字数：{ch.wordCount || 0}</span>
            </div>
          </div>
          <div className="ssw-section">
            <div className="ssw-section-title">说明</div>
            <div className="ssw-kv">此步骤代表章节封装完成并进入下一章循环。</div>
          </div>
        </div>
      );
    }

    return <div className="ssw-empty">暂无输出</div>;
  };

  const makeEdgePath = (from: CanvasNode, to: CanvasNode, edge: CanvasEdge) => {
    const fromRight = { x: from.x + NODE_W, y: from.y + NODE_H / 2 };
    const fromLeft = { x: from.x, y: from.y + NODE_H / 2 };
    const fromBottom = { x: from.x + NODE_W / 2, y: from.y + NODE_H };
    
    const toLeft = { x: to.x, y: to.y + NODE_H / 2 };
    const toTop = { x: to.x + NODE_W / 2, y: to.y };

    // 循环边：COMMIT -> GENERATE
    if (edge.type === 'loop') {
      // 从 COMMIT 的左侧出来，弯曲到 GENERATE 的左侧
      const sx = fromLeft.x;
      const sy = fromLeft.y;
      const ex = toLeft.x;
      const ey = toLeft.y;
      
      // 绘制一个向左弯曲的路径
      const curveX = Math.min(sx, ex) - 80;
      return `M ${sx} ${sy} C ${curveX} ${sy}, ${curveX} ${ey}, ${ex} ${ey}`;
    }

    // 同一行：右出左进
    if (Math.abs(from.y - to.y) < 80) {
      const sx = fromRight.x;
      const sy = fromRight.y;
      const ex = toLeft.x;
      const ey = toLeft.y;
      const mid = (sx + ex) / 2;
      return `M ${sx} ${sy} C ${mid} ${sy}, ${mid} ${ey}, ${ex} ${ey}`;
    }

    // 跨行：下出上进
    const sx = fromBottom.x;
    const sy = fromBottom.y;
    const ex = toTop.x;
    const ey = toTop.y;
    const midY = (sy + ey) / 2;
    return `M ${sx} ${sy} C ${sx} ${midY}, ${ex} ${midY}, ${ex} ${ey}`;
  };

  if (loading) {
    return (
      <div className="ssw-loading">
        <Spin size="large" />
      </div>
    );
  }

  if (!novel) {
    return (
      <div className="ssw-loading">
        <div style={{ textAlign: 'center' }}>
          <div style={{ marginBottom: 12 }}>无法加载短篇数据</div>
          <Button onClick={() => navigate('/short-stories')}>返回列表</Button>
        </div>
      </div>
    );
  }

  const currentStatusTag = () => {
    const s = novel.status;
    if (s === 'WORKFLOW_RUNNING') return <Tag color="processing">运行中</Tag>;
    if (s === 'GENERATING_OUTLINE') return <Tag color="processing">生成大纲</Tag>;
    if (s === 'WORKFLOW_PAUSED') return <Tag color="warning">已暂停</Tag>;
    if (s === 'COMPLETED') return <Tag color="success">已完成</Tag>;
    if (s === 'FAILED') return <Tag color="error">失败</Tag>;
    return <Tag>{s}</Tag>;
  };

  return (
    <div className="ssw-page">
      {/* 左中区域：包含 topbar + body */}
      <div className="ssw-main-wrapper">
        <header className="ssw-topbar">
          <div className="ssw-topbar-left">
            <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/short-stories')} />
            <div className="ssw-title">
              <div className="ssw-title-main">{novel.title}</div>
              <div className="ssw-title-sub">
                {currentStatusTag()}
                <span className="ssw-dot">·</span>
                <span>进度 {workflowProgress}%</span>
                {activeStep && (
                  <>
                    <span className="ssw-dot">·</span>
                    <span className="ssw-muted">{activeStep}</span>
                  </>
                )}
              </div>
            </div>
          </div>
        </header>

        <div className="ssw-body">
        <aside className="ssw-left">
          <div className="ssw-left-head">章节列表</div>

          <div
            className={
              view.type === 'workflow'
                ? 'ssw-left-item active'
                : 'ssw-left-item'
            }
            onClick={() => {
              setView({ type: 'workflow' });
              setSelectedNodeKey((k) => k || activeStep || 'HOOKS');
            }}
          >
            <div className="ssw-left-item-title">短篇工作流</div>
            <div className="ssw-left-item-sub">画布模式 · 节点可点开查看输出</div>
          </div>

          <div className="ssw-left-divider">章节</div>

          <div className="ssw-left-list ssw-left-list-with-footer">
            {chapters
              .filter((c) => c.chapterNumber >= 1)
              .map((c) => (
                <div
                  key={c.chapterNumber}
                  className={
                    view.type === 'chapter' && view.chapterNumber === c.chapterNumber
                      ? 'ssw-left-chapter active'
                      : 'ssw-left-chapter'
                  }
                  onClick={() => setView({ type: 'chapter', chapterNumber: c.chapterNumber })}
                >
                  <div className="ssw-left-chapter-main">
                    <span className="ssw-left-chapter-no">{c.chapterNumber}</span>
                    <span className="ssw-left-chapter-title">{c.title || `第${c.chapterNumber}章`}</span>
                  </div>
                  <div className="ssw-left-chapter-sub">
                    <Tag style={{ marginInlineEnd: 0 }}>
                      {c.status}
                    </Tag>
                    <span className="ssw-muted">{c.wordCount || 0}字</span>

                    {c.status === 'FAILED' && (
                      <Tooltip title="重试本章">
                        <Button
                          size="small"
                          icon={<ReloadOutlined />}
                          danger
                          style={{ marginLeft: 'auto' }}
                          loading={actionLoading}
                          onClick={(e) => {
                            e.stopPropagation();
                            handleRetry(c.chapterNumber);
                          }}
                        />
                      </Tooltip>
                    )}
                  </div>
                </div>
              ))}
          </div>

          {/* 导出按钮区域 */}
          <div className="ssw-left-footer">
            <Button
              icon={<DownloadOutlined />}
              onClick={handleExport}
              className="ssw-export-btn"
              block
            >
              导出小说
            </Button>
          </div>
        </aside>

        <main className="ssw-main">
          {view.type === 'workflow' ? (
            <div className="ssw-canvas">
              <div className="ssw-canvas-toolbar">
                <Tooltip title="定位到当前节点">
                  <Button
                    size="small"
                    icon={<AimOutlined />}
                    onClick={() => {
                      if (activeStep) centerOnNode(activeStep);
                    }}
                  />
                </Tooltip>
                <Tooltip title="放大">
                  <Button
                    size="small"
                    icon={<ZoomInOutlined />}
                    onClick={() => setScale((s) => clamp(s * 1.12, 0.45, 1.9))}
                  />
                </Tooltip>
                <Tooltip title="缩小">
                  <Button
                    size="small"
                    icon={<ZoomOutOutlined />}
                    onClick={() => setScale((s) => clamp(s * 0.9, 0.45, 1.9))}
                  />
                </Tooltip>
                <div className="ssw-canvas-toolbar-scale">{Math.round(scale * 100)}%</div>
              </div>

              <div
                className="ssw-canvas-viewport"
                ref={viewportRef}
                onPointerDown={onViewportPointerDown}
                onWheel={onViewportWheel}
              >
                <div
                  className="ssw-canvas-world"
                  style={{
                    width: worldSize.width,
                    height: worldSize.height,
                    transform: `translate(${pan.x}px, ${pan.y}px) scale(${scale})`,
                  }}
                >
                  <svg className="ssw-edges" width={worldSize.width} height={worldSize.height}>
                    <defs>
                      <marker
                        id="arrow"
                        markerWidth="10"
                        markerHeight="10"
                        refX="9"
                        refY="3"
                        orient="auto"
                        markerUnits="strokeWidth"
                      >
                        <path d="M0,0 L9,3 L0,6" fill="none" stroke="#cbd5e1" strokeWidth="1.5" />
                      </marker>
                    </defs>

                    {edges.map((e, idx) => {
                      const from = nodeByKey.get(e.from);
                      const to = nodeByKey.get(e.to);
                      if (!from || !to) return null;
                      const d = makeEdgePath(from, to, e);
                      const isActiveEdge = activeStep === e.to || activeStep === e.from;
                      return (
                        <path
                          key={idx}
                          d={d}
                          className={`ssw-edge ${e.type} ${isActiveEdge ? 'active' : ''}`}
                          markerEnd={e.type === 'flow' ? 'url(#arrow)' : undefined}
                        />
                      );
                    })}
                  </svg>

                  {nodes.map((n) => {
                    const isGlobalActive = activeStep === n.key; // 全局 active (Setting/Outline/Hooks)
                    const isLoopActive = activeLoopNodeKey === n.key; // Loop active
                    const isActive = isGlobalActive || isLoopActive;
                    
                    const isSelected = selectedNodeKey === n.key;

                    const status = n.status;
                    return (
                      <div
                        key={n.key}
                        className={`ssw-node status-${status.toLowerCase()} ${isActive ? 'active-halo' : ''} ${isSelected ? 'selected' : ''}`}
                        style={{ left: n.x, top: n.y, width: NODE_W, height: NODE_H }}
                        onClick={() => {
                          setSelectedNodeKey(n.key);
                          setRightPanelTab('output');
                        }}
                      >
                        {/* Halo effect for active node */}
                        {isActive && <div className="ssw-node-halo"></div>}

                        <div className="ssw-node-top">
                          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                            {getNodeIcon(n.key)}
                            <div className="ssw-node-title">{n.title}</div>
                          </div>
                          {status === 'RUNNING' && <Spin size="small" />}
                          {status === 'COMPLETED' && <CheckCircleOutlined style={{ color: '#10b981' }} />}
                          {status === 'FAILED' && <CloseCircleOutlined style={{ color: '#ef4444' }} />}
                        </div>
                        <div className="ssw-node-sub">{n.subtitle || (n.chapterNumber ? `第 ${n.chapterNumber} 章` : '')}</div>
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>
          ) : (
            <div className="ssw-reader">
              {!currentChapter ? (
                <div className="ssw-empty">章节不存在</div>
              ) : (
                <>
                  <div className="ssw-reader-head">
                    <div>
                      <div className="ssw-reader-title">
                        第 {currentChapter.chapterNumber} 章 · {currentChapter.title}
                      </div>
                      <div className="ssw-reader-sub">
                        <Tag>{currentChapter.status}</Tag>
                        <span className="ssw-muted">{currentChapter.wordCount || 0} 字</span>
                        <span className="ssw-muted">更新：{currentChapter.updatedAt ? dayjs(currentChapter.updatedAt).format('MM-DD HH:mm') : '-'}</span>
                      </div>
                    </div>
                    {currentChapter.status === 'FAILED' && (
                      <Button
                        danger
                        icon={<ReloadOutlined />}
                        loading={actionLoading}
                        onClick={() => handleRetry(currentChapter.chapterNumber)}
                      >
                        重试本章
                      </Button>
                    )}
                  </div>

                  <div className="ssw-reader-body">
                    <div className="ssw-section">
                      <div className="ssw-section-title">看点核心</div>
                      <div className="ssw-kv">{currentChapter.brief}</div>
                    </div>

                    {currentChapter.lastAdjustment && (
                      <div className="ssw-section">
                        <div className="ssw-section-title">返工指令</div>
                        <pre className="ssw-pre">{currentChapter.lastAdjustment}</pre>
                      </div>
                    )}

                    <div className="ssw-section">
                      <div className="ssw-section-title">正文</div>
                      {currentChapter.content ? (
                        <pre className="ssw-pre ssw-pre-reading">{currentChapter.content}</pre>
                      ) : (
                        <div className="ssw-empty">暂无正文</div>
                      )}
                    </div>

                    {currentChapter.reviewResult && (
                      <div className="ssw-section">
                        <div className="ssw-section-title">审稿</div>
                        <pre className="ssw-pre">{currentChapter.reviewResult}</pre>
                      </div>
                    )}

                    {currentChapter.analysisResult && (
                      <div className="ssw-section">
                        <div className="ssw-section-title">分析</div>
                        <pre className="ssw-pre">{currentChapter.analysisResult}</pre>
                      </div>
                    )}
                  </div>
                </>
              )}
            </div>
          )}
        </main>
        </div>
      </div>

      {/* 右侧面板：独立全高布局 */}
      <aside className="ssw-right">
          {/* 右侧面板头部 */}
          <div className="ssw-right-header">
            <div className="ssw-right-tabs">
              <button
                className={`ssw-tab-btn ${rightPanelTab === 'output' ? 'active' : ''}`}
                onClick={() => setRightPanelTab('output')}
              >
                <CodeOutlined />
                <span>输出</span>
              </button>
              <button
                className={`ssw-tab-btn ${rightPanelTab === 'logs' ? 'active' : ''}`}
                onClick={() => setRightPanelTab('logs')}
              >
                <HistoryOutlined />
                <span>日志</span>
                {filteredLogs.length > 0 && (
                  <span className="ssw-tab-badge">{filteredLogs.length}</span>
                )}
              </button>
            </div>
          </div>

          {/* 内容区域 */}
          <div className="ssw-right-content">
            {rightPanelTab === 'output' ? (
              /* 输出面板 */
              <div className="ssw-output-panel">
                {selectedNodeKey ? (
                  <>
                    <div className="ssw-output-header">
                      <div className="ssw-output-node-info">
                        {getNodeIcon(selectedNodeKey)}
                        <span className="ssw-output-node-name">
                          {nodeByKey.get(selectedNodeKey)?.title || selectedNodeKey}
                        </span>
                      </div>
                      <Tag color={STATUS_TAG_COLOR[nodeByKey.get(selectedNodeKey)?.status || 'PENDING']}>
                        {STATUS_LABEL[nodeByKey.get(selectedNodeKey)?.status || 'PENDING']}
                      </Tag>
                    </div>
                    <div className="ssw-output-body">
                      {renderNodeOutput()}
                    </div>
                  </>
                ) : (
                  <div className="ssw-empty-state">
                    <div className="empty-visual">
                      <div className="visual-main-circle">
                        <BulbOutlined />
                      </div>
                      <div className="visual-bg-circle circle-1" />
                      <div className="visual-bg-circle circle-2" />
                    </div>
                    <div className="empty-text">
                      <h3>选择节点查看输出</h3>
                      <p>点击左侧画布中的节点卡片，查看该步骤的生成内容</p>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              /* 日志面板 */
              <div className="ssw-logs-panel">
                {novel?.status === 'DRAFT' || filteredLogs.length === 0 ? (
                  <div className="ssw-empty-state">
                    <div className="empty-visual">
                      <div className="visual-main-circle">
                        <RocketOutlined />
                      </div>
                      <div className="visual-bg-circle circle-1" />
                      <div className="visual-bg-circle circle-2" />
                    </div>
                    <div className="empty-text">
                      <h3>暂无运行日志</h3>
                      <p>
                        {novel?.status === 'DRAFT'
                          ? '开始生成后将实时显示 AI 运行日志'
                          : '等待工作流运行...'}
                      </p>
                    </div>
                  </div>
                ) : (
                  <div className="ssw-log-stream">
                    {filteredLogs.map((l) => {
                      const logType = typeof l.type === 'object' ? JSON.stringify(l.type) : String(l.type || '');
                      const logContent = typeof l.content === 'object' ? JSON.stringify(l.content) : String(l.content || '');
                      
                      return (
                        <div key={l.id} className={`ssw-log-message type-${logType.toLowerCase()}`}>
                          <div className="log-header">
                            <div className="log-info">
                              <span className={`log-dot status-${logType.toLowerCase()}`} />
                              <span className="log-type">{logType}</span>
                              {l.chapterNumber != null && (
                                <span className="log-chapter">第 {l.chapterNumber} 章</span>
                              )}
                            </div>
                            <span className="log-time">{dayjs(l.createdAt).format('HH:mm:ss')}</span>
                          </div>
                          <div className="log-content">{logContent}</div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            )}
          </div>

          {/* 底部操作栏 */}
          <div className="ssw-control-bar">
            {novel?.status === 'DRAFT' ? (
              <div className="control-content control-content-vertical">
                <div className="model-selector-row">
                  <label className="input-label">模型</label>
                  <select
                    className="model-select-input"
                    value={selectedModelId || ''}
                    onChange={(e) => setSelectedModelId(e.target.value)}
                    disabled={loadingModels}
                  >
                    {loadingModels ? (
                      <option value="">加载中...</option>
                    ) : models.length === 0 ? (
                      <option value="">无模型</option>
                    ) : (
                      models.map(m => (
                        <option key={m.modelId} value={m.modelId}>
                          {m.displayName || m.modelId}
                        </option>
                      ))
                    )}
                  </select>
                </div>
                <Button
                  type="primary"
                  size="large"
                  icon={<PlayCircleOutlined />}
                  loading={actionLoading}
                  onClick={() => handleStart(selectedModelId || undefined)}
                  className="start-btn-premium"
                  block
                >
                  开始生成
                </Button>
              </div>
            ) : (novel?.status === 'WORKFLOW_RUNNING' || novel?.status === 'GENERATING_OUTLINE') ? (
              <div className="control-content">
                <Button
                  danger
                  size="large"
                  icon={<PauseCircleOutlined />}
                  loading={actionLoading}
                  onClick={handlePause}
                  className="pause-btn-premium"
                  block
                >
                  暂停生成
                </Button>
              </div>
            ) : (
              <div className="control-content">
                {novel?.status !== 'COMPLETED' ? (
                  <Button
                    type="primary"
                    size="large"
                    icon={<PlayCircleOutlined />}
                    loading={actionLoading}
                    onClick={() => handleStart()}
                    className="resume-btn-premium"
                    block
                  >
                    继续生成
                  </Button>
                ) : (
                  <div className="completed-badge">
                    <CheckCircleOutlined /> 已完成
                  </div>
                )}
              </div>
            )}
          </div>
        </aside>
    </div>
  );
};

export default ShortStoryWorkflowPage;
