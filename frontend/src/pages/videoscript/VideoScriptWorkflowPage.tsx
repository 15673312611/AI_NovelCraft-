import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, Input, Spin, Tag, Tooltip, message } from 'antd';
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
  PauseCircleOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  VideoCameraOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import ReactMarkdown from 'react-markdown';
import api from '../../services/api';
import {
  videoScriptService,
  VideoScript,
  VideoScriptEpisode,
  VideoScriptLog,
  VideoScriptWorkflowStateResponse,
  WorkflowStep,
  WorkflowStepStatus,
} from '../../services/videoScriptService';

// 复用短篇工作流页面样式
import '../shortstory/ShortStoryWorkflowPage.css';

interface AIModel {
  id: number;
  modelId: string;
  displayName: string;
  provider: string;
  isDefault: boolean;
}

type CanvasNode = {
  key: string;
  title: string;
  subtitle?: string;
  status: WorkflowStepStatus;
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

function parseEpisodeStage(key: string): { episodeNumber: number; stage: string } | null {
  const m = key.match(/^EPISODE_(\d+)_(GENERATE|REVIEW|ANALYZE|DECIDE|COMMIT)$/);
  if (!m) return null;
  return { episodeNumber: Number(m[1]), stage: m[2] };
}

function clamp(n: number, min: number, max: number) {
  return Math.max(min, Math.min(max, n));
}

const VideoScriptWorkflowPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const scriptId = Number(id);

  const [script, setScript] = useState<VideoScript | null>(null);
  const [episodes, setEpisodes] = useState<VideoScriptEpisode[]>([]);
  const [logs, setLogs] = useState<VideoScriptLog[]>([]);
  const [workflow, setWorkflow] = useState<VideoScriptWorkflowStateResponse | null>(null);

  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);

  const [selectedEpisodeNumber, setSelectedEpisodeNumber] = useState<number | null>(null);
  const [selectedNodeKey, setSelectedNodeKey] = useState<string | null>(null);
  const [rightPanelTab, setRightPanelTab] = useState<'output' | 'logs'>('output');
  const [logSearch, setLogSearch] = useState('');

  // Episode content editor
  const [editingContent, setEditingContent] = useState<{ episodeNumber: number; content: string } | null>(null);
  const [savingContent, setSavingContent] = useState(false);
  const saveTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // 模型选择
  const [models, setModels] = useState<AIModel[]>([]);
  const [selectedModelId, setSelectedModelId] = useState<string | null>(null);
  const [loadingModels, setLoadingModels] = useState(false);

  // 剧本格式选择（每集正文结构）
  const [selectedScriptFormat, setSelectedScriptFormat] = useState<string | null>(null);

  // Canvas pan/zoom
  const viewportRef = useRef<HTMLDivElement | null>(null);
  const [pan, setPan] = useState({ x: 60, y: 40 });
  const [scale, setScale] = useState(1);
  const dragRef = useRef<null | { startX: number; startY: number; panX: number; panY: number }>(null);

  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const activeStep = workflow?.activeStep || script?.activeStep || null;
  const activeParsed = useMemo(() => (activeStep ? parseEpisodeStage(activeStep) : null), [activeStep]);

  const stepMap = useMemo(() => {
    const m = new Map<string, WorkflowStep>();
    (workflow?.steps || []).forEach((s) => m.set(s.key, s));
    return m;
  }, [workflow?.steps]);

  const episodeCount = script?.episodeCount ?? workflow?.episodeCount ?? 0;

  const episodeNumberInView = useMemo(() => {
    const fallback = activeParsed?.episodeNumber ?? Math.max(1, (script?.currentEpisode ?? 0) + 1);
    const n = selectedEpisodeNumber ?? fallback;
    if (!episodeCount) return n;
    return clamp(n, 1, Math.max(1, episodeCount));
  }, [activeParsed?.episodeNumber, script?.currentEpisode, selectedEpisodeNumber, episodeCount]);

  // active halo mapping
  const activeLoopNodeKey = useMemo(() => {
    if (!activeStep) return null;
    if (['STORY_SETTING', 'OUTLINE', 'HOOKS', 'PROLOGUE'].includes(activeStep)) return activeStep;
    const parsed = parseEpisodeStage(activeStep);
    if (parsed) return `LOOP_${parsed.stage}`;
    return null;
  }, [activeStep]);

  const saveEpisodeContent = useCallback(
    async (episodeNumber: number, content: string) => {
      if (!Number.isFinite(scriptId)) return;
      try {
        setSavingContent(true);
        await videoScriptService.updateEpisodeContent(scriptId, episodeNumber, content);
        setEpisodes((prev) =>
          prev.map((e) => (e.episodeNumber === episodeNumber ? { ...e, content, wordCount: content.length } : e))
        );
      } catch (e) {
        message.error('保存失败');
        console.error(e);
      } finally {
        setSavingContent(false);
      }
    },
    [scriptId]
  );

  const debouncedSave = useCallback(
    (episodeNumber: number, content: string) => {
      if (saveTimeoutRef.current) {
        clearTimeout(saveTimeoutRef.current);
      }
      saveTimeoutRef.current = setTimeout(() => {
        saveEpisodeContent(episodeNumber, content);
      }, 900);
    },
    [saveEpisodeContent]
  );

  const handleContentChange = useCallback(
    (episodeNumber: number, newContent: string) => {
      setEditingContent({ episodeNumber, content: newContent });
      debouncedSave(episodeNumber, newContent);
    },
    [debouncedSave]
  );

  const getEpisodeContent = useCallback(
    (ep: VideoScriptEpisode) => {
      if (editingContent && editingContent.episodeNumber === ep.episodeNumber) {
        return editingContent.content;
      }
      return ep.content || '';
    },
    [editingContent]
  );

  const nodes: CanvasNode[] = useMemo(() => {
    const gapX = 320;
    const gapY = 160;
    const startX = 80;
    const startY = 80;

    const list: CanvasNode[] = [];

    // global nodes
    const globalKeys: Array<'STORY_SETTING' | 'OUTLINE' | 'HOOKS'> = ['STORY_SETTING', 'OUTLINE', 'HOOKS'];
    globalKeys.forEach((k, idx) => {
      const s = stepMap.get(k);
      list.push({
        key: k,
        title: s?.name || k,
        subtitle: s?.description,
        status: (s?.status as WorkflowStepStatus) || 'PENDING',
        x: startX + idx * gapX,
        y: startY,
      });
    });

    // prologue in the middle
    const prologueStep = stepMap.get('PROLOGUE');
    list.push({
      key: 'PROLOGUE',
      title: prologueStep?.name || '生成导语',
      subtitle: prologueStep?.description || '黄金开头/开场钩子',
      status: (prologueStep?.status as WorkflowStepStatus) || 'PENDING',
      x: startX + gapX,
      y: startY + gapY,
    });

    // loop nodes (current episode in view)
    const stages: Array<'GENERATE' | 'REVIEW' | 'ANALYZE' | 'DECIDE' | 'COMMIT'> = [
      'GENERATE',
      'REVIEW',
      'ANALYZE',
      'DECIDE',
      'COMMIT',
    ];

    const getStageTitle = (st: string) =>
      ({
        GENERATE: 'AI 生成',
        REVIEW: '自动审核',
        ANALYZE: '连续性分析',
        DECIDE: '调整决策',
        COMMIT: '封装入库',
      }[st] || st);

    const getStageSubtitle = (st: string) =>
      ({
        GENERATE: '镜头脚本成稿',
        REVIEW: '打分与可拍检查',
        ANALYZE: '记忆/风险/后续',
        DECIDE: '更新大纲/看点',
        COMMIT: '推进到下一集',
      }[st] || '');

    stages.forEach((st, idx) => {
      const realKey = `EPISODE_${episodeNumberInView}_${st}`;
      const s = stepMap.get(realKey);

      // third row: 3 nodes, fourth row: 2 nodes centered
      const row = idx < 3 ? 2 : 3;
      const col = idx < 3 ? idx : idx - 3;
      const offsetX = row === 3 ? gapX / 2 : 0;

      list.push({
        key: `LOOP_${st}`,
        title: getStageTitle(st),
        subtitle: `第 ${episodeNumberInView} 集 · ${getStageSubtitle(st)}`,
        status: (s?.status as WorkflowStepStatus) || 'PENDING',
        x: startX + col * gapX + offsetX,
        y: startY + row * gapY,
      });
    });

    return list;
  }, [stepMap, episodeNumberInView]);

  const edges: CanvasEdge[] = useMemo(
    () => [
      { from: 'STORY_SETTING', to: 'OUTLINE', type: 'flow' },
      { from: 'OUTLINE', to: 'HOOKS', type: 'flow' },
      { from: 'HOOKS', to: 'PROLOGUE', type: 'flow' },
      { from: 'PROLOGUE', to: 'LOOP_GENERATE', type: 'flow' },
      { from: 'LOOP_GENERATE', to: 'LOOP_REVIEW', type: 'flow' },
      { from: 'LOOP_REVIEW', to: 'LOOP_ANALYZE', type: 'flow' },
      { from: 'LOOP_ANALYZE', to: 'LOOP_DECIDE', type: 'flow' },
      { from: 'LOOP_DECIDE', to: 'LOOP_COMMIT', type: 'flow' },
      { from: 'LOOP_COMMIT', to: 'LOOP_GENERATE', type: 'loop', label: '下一集' },
    ],
    []
  );

  const nodeByKey = useMemo(() => {
    const m = new Map<string, CanvasNode>();
    nodes.forEach((n) => m.set(n.key, n));
    return m;
  }, [nodes]);

  const worldSize = useMemo(() => {
    let maxX = 0;
    let maxY = 0;
    for (const n of nodes) {
      maxX = Math.max(maxX, n.x + NODE_W + 160);
      maxY = Math.max(maxY, n.y + NODE_H + 160);
    }
    return { width: Math.max(maxX, 1400), height: Math.max(maxY, 800) };
  }, [nodes]);

  const workflowProgress = useMemo(() => {
    const steps = workflow?.steps || [];
    if (steps.length === 0) return 0;
    const done = steps.filter((s) => s.status === 'COMPLETED').length;
    return Math.round((done / steps.length) * 100);
  }, [workflow?.steps]);

  const getNodeIcon = (key: string) => {
    if (key === 'STORY_SETTING') return <BulbOutlined style={{ fontSize: 16, color: '#6366f1' }} />;
    if (key === 'OUTLINE') return <FileTextOutlined style={{ fontSize: 16, color: '#8b5cf6' }} />;
    if (key === 'HOOKS') return <AimOutlined style={{ fontSize: 16, color: '#ec4899' }} />;
    if (key === 'PROLOGUE') return <VideoCameraOutlined style={{ fontSize: 16, color: '#f97316' }} />;
    if (key.includes('GENERATE')) return <FileTextOutlined style={{ fontSize: 16, color: '#3b82f6' }} />;
    if (key.includes('REVIEW')) return <SafetyCertificateOutlined style={{ fontSize: 16, color: '#f59e0b' }} />;
    if (key.includes('ANALYZE')) return <BarChartOutlined style={{ fontSize: 16, color: '#10b981' }} />;
    if (key.includes('DECIDE')) return <BulbOutlined style={{ fontSize: 16, color: '#6366f1' }} />;
    if (key.includes('COMMIT')) return <CheckCircleOutlined style={{ fontSize: 16, color: '#10b981' }} />;
    return <FileTextOutlined />;
  };

  const stopPolling = () => {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  };

  const loadDataSilent = async () => {
    if (!Number.isFinite(scriptId)) return;
    try {
      const [scriptRes, episodesRes, logsRes, wfRes] = await Promise.all([
        videoScriptService.get(scriptId),
        videoScriptService.getEpisodes(scriptId),
        videoScriptService.getLogs(scriptId, { page: 0, size: 200 }),
        videoScriptService.getWorkflowState(scriptId),
      ]);

      const rawScript = extractData(scriptRes);
      const rawEpisodes = extractData(episodesRes);
      const rawLogs = extractData(logsRes);
      const rawWf = extractData(wfRes);

      const nextScript: VideoScript = rawScript?.data ? rawScript.data : rawScript;
      const nextEpisodes: VideoScriptEpisode[] = Array.isArray(rawEpisodes) ? rawEpisodes : [];
      const nextWorkflow: VideoScriptWorkflowStateResponse = rawWf?.data ? rawWf.data : rawWf;
      const nextLogs: VideoScriptLog[] = Array.isArray(rawLogs) ? rawLogs : rawLogs?.content || [];

      setScript(nextScript);
      setEpisodes(nextEpisodes);
      setWorkflow(nextWorkflow);
      setLogs(nextLogs);

      const nextActive = (nextWorkflow?.activeStep || nextScript?.activeStep) as string | undefined;
      const parsed = nextActive ? parseEpisodeStage(nextActive) : null;

      setSelectedEpisodeNumber((prev) => prev ?? parsed?.episodeNumber ?? (nextScript?.currentEpisode ?? 0) + 1);
      setSelectedNodeKey((prev) => {
        if (prev) return prev;
        if (!nextActive) return 'HOOKS';
        if (['STORY_SETTING', 'OUTLINE', 'HOOKS', 'PROLOGUE'].includes(nextActive)) return nextActive;
        if (parsed) return `LOOP_${parsed.stage}`;
        return 'HOOKS';
      });
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
    if (!Number.isFinite(scriptId)) return;
    loadData();
    return () => stopPolling();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scriptId]);

  // 加载模型列表
  useEffect(() => {
    const loadModels = async () => {
      setLoadingModels(true);
      try {
        const res = await api.get<any[]>('/ai-tasks/models');
        const data = (res as any).data || res;
        const rawList = Array.isArray(data) ? data : [];

        const modelList: AIModel[] = rawList.map((m: any) => ({
          id: 0,
          modelId: m.id || m.modelId,
          displayName: m.name || m.displayName,
          provider: m.provider || '',
          isDefault: m.isDefault || false,
        }));
        setModels(modelList);
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
    const status = script?.status;
    const shouldPoll = status === 'WORKFLOW_RUNNING' || status === 'GENERATING_OUTLINE';
    const shouldStop = status === 'WORKFLOW_PAUSED' || status === 'COMPLETED' || status === 'FAILED';

    if (shouldPoll && !pollRef.current) {
      pollRef.current = setInterval(loadDataSilent, 3000);
    }
    if (shouldStop) {
      stopPolling();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [script?.status]);

  // 初始化/同步脚本格式选择（不覆盖用户手动选择）
  useEffect(() => {
    if (selectedScriptFormat) return;
    const serverFmt = String(script?.scriptFormat || '').trim();
    setSelectedScriptFormat(serverFmt || 'STORYBOARD');
  }, [script?.scriptFormat, selectedScriptFormat]);

  const handleStart = async (opts?: { modelId?: string; scriptFormat?: string }) => {
    if (!Number.isFinite(scriptId)) return;
    try {
      setActionLoading(true);

      const status = script?.status;
      const canUpdateConfig = status === 'DRAFT' || status === 'WORKFLOW_PAUSED';
      const cfg: any = {};
      if (opts?.modelId) cfg.modelId = opts.modelId;
      if (opts?.scriptFormat) cfg.scriptFormat = opts.scriptFormat;

      if (canUpdateConfig && Object.keys(cfg).length > 0) {
        await videoScriptService.updateConfig(scriptId, cfg);
      }

      await videoScriptService.start(scriptId);
      message.success('已开始/继续工作流');
      await loadDataSilent();
    } catch (e) {
      message.error((e as any)?.message || '启动失败');
    } finally {
      setActionLoading(false);
    }
  };

  const handlePause = async () => {
    if (!Number.isFinite(scriptId)) return;
    try {
      setActionLoading(true);
      await videoScriptService.pause(scriptId);
      message.success('已暂停');
      await loadDataSilent();
    } catch (e) {
      message.error((e as any)?.message || '暂停失败');
    } finally {
      setActionLoading(false);
    }
  };

  const handleRetry = async (episodeNumber: number) => {
    if (!Number.isFinite(scriptId)) return;
    try {
      setActionLoading(true);
      await videoScriptService.retry(scriptId, episodeNumber);
      message.success(`已请求重试第 ${episodeNumber} 集`);
      setSelectedEpisodeNumber(episodeNumber);
      setSelectedNodeKey('LOOP_GENERATE');
      await loadDataSilent();
    } catch (e) {
      message.error((e as any)?.message || '重试失败');
    } finally {
      setActionLoading(false);
    }
  };

  const handleExport = () => {
    if (!script) {
      message.warning('没有可导出的内容');
      return;
    }

    const completed = episodes
      .filter((e) => e.content && e.content.trim().length > 0)
      .sort((a, b) => a.episodeNumber - b.episodeNumber);

    if (completed.length === 0) {
      message.warning('没有已生成的剧集可导出');
      return;
    }

    let content = `${script.title}\n`;
    content += `${'='.repeat(40)}\n\n`;

    const fmtLabel = (fmt?: string) => {
      if (fmt === 'SCENE') return '集-场台本';
      if (fmt === 'STORYBOARD') return '分镜脚本';
      if (fmt === 'NARRATION') return '解说口播';
      return fmt || '-';
    };

    content += `【剧本格式】\n${fmtLabel(script.scriptFormat)}\n\n`;

    if (script.idea) {
      content += `【构思】\n${script.idea}\n\n`;
    }
    if (script.scriptSetting) {
      content += `【系列设定】\n${script.scriptSetting}\n\n`;
    }
    if (script.outline) {
      content += `【系列大纲】\n${script.outline}\n\n`;
    }
    if (script.prologue) {
      content += `【导语】\n${script.prologue}\n\n`;
    }

    content += `${'='.repeat(40)}\n\n`;

    completed.forEach((ep) => {
      content += `第 ${ep.episodeNumber} 集  ${ep.title || ''}\n`;
      content += `${'-'.repeat(30)}\n\n`;
      content += `${ep.content}\n\n\n`;
    });

    content += `${'='.repeat(40)}\n`;
    content += `导出时间：${dayjs().format('YYYY-MM-DD HH:mm:ss')}\n`;

    const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${script.title || '剧本系列'}_${dayjs().format('YYYYMMDD_HHmm')}.txt`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);

    message.success('已导出');
  };

  // Canvas interactions
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

    const dx = cx - pan.x;
    const dy = cy - pan.y;
    setScale(nextScale);
    setPan({ x: cx - dx * ratio, y: cy - dy * ratio });
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

    setPan({ x: centerX - nx * scale, y: centerY - ny * scale });
  };

  const makeEdgePath = (from: CanvasNode, to: CanvasNode) => {
    const fromRight = { x: from.x + NODE_W, y: from.y + NODE_H / 2 };
    const toLeft = { x: to.x, y: to.y + NODE_H / 2 };
    const mid = (fromRight.x + toLeft.x) / 2;
    return `M ${fromRight.x} ${fromRight.y} C ${mid} ${fromRight.y}, ${mid} ${toLeft.y}, ${toLeft.x} ${toLeft.y}`;
  };

  const selectedEpisode = useMemo(
    () => episodes.find((e) => e.episodeNumber === episodeNumberInView) || null,
    [episodes, episodeNumberInView]
  );

  const filteredLogs = useMemo(() => {
    const q = logSearch.trim().toLowerCase();
    const onlyEp = selectedNodeKey?.startsWith('LOOP_') && typeof episodeNumberInView === 'number';

    return (logs || []).filter((l) => {
      if (onlyEp && l.episodeNumber != null && l.episodeNumber !== episodeNumberInView) return false;
      if (!q) return true;
      return String(l.content || '').toLowerCase().includes(q);
    });
  }, [logs, logSearch, selectedNodeKey, episodeNumberInView]);

  const renderNodeOutput = () => {
    const key = selectedNodeKey;
    if (!key) return <div className="ssw-empty">请选择一个节点查看输出</div>;

    if (key === 'STORY_SETTING') {
      const md = script?.scriptSetting || '';
      return md ? (
        <div className="ssw-md">
          <ReactMarkdown>{md}</ReactMarkdown>
        </div>
      ) : (
        <div className="ssw-empty">系列设定尚未生成</div>
      );
    }

    if (key === 'OUTLINE') {
      const md = script?.outline || '';
      return md ? (
        <div className="ssw-md">
          <ReactMarkdown>{md}</ReactMarkdown>
        </div>
      ) : (
        <div className="ssw-empty">系列大纲尚未生成</div>
      );
    }

    if (key === 'HOOKS') {
      const hooks = safeJsonParse<any[]>(script?.hooksJson);
      if (!hooks || hooks.length === 0) {
        return <div className="ssw-empty">看点尚未生成</div>;
      }
      return (
        <div className="ssw-output">
          <div className="ssw-section">
            <div className="ssw-section-title">每集看点</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {hooks.map((h, idx) => (
                <div key={idx} className="ssw-card" style={{ padding: 12 }}>
                  <div style={{ fontWeight: 700 }}>
                    第 {h.episodeNumber ?? idx + 1} 集：{h.title || ''}
                  </div>
                  {h.core && <div style={{ marginTop: 6, color: '#334155' }}>{h.core}</div>}
                  {h.cliffhanger && <div style={{ marginTop: 6, color: '#64748b' }}>悬念：{h.cliffhanger}</div>}
                </div>
              ))}
            </div>
          </div>
        </div>
      );
    }

    if (key === 'PROLOGUE') {
      const md = script?.prologue || '';
      return md ? (
        <div className="ssw-md">
          <ReactMarkdown>{md}</ReactMarkdown>
        </div>
      ) : (
        <div className="ssw-empty">导语尚未生成</div>
      );
    }

    if (key === 'LOOP_GENERATE') {
      if (!selectedEpisode) return <div className="ssw-empty">未找到该集数据</div>;
      const content = getEpisodeContent(selectedEpisode);

      return (
        <div className="ssw-output">
          <div className="ssw-section">
            <div className="ssw-section-title">
              第 {selectedEpisode.episodeNumber} 集 · {selectedEpisode.title}
              {savingContent && <span style={{ marginLeft: 10, color: '#64748b' }}>保存中…</span>}
            </div>
            {selectedEpisode.brief && <div className="ssw-muted">核心：{selectedEpisode.brief}</div>}
            <div style={{ marginTop: 10 }}>
              <Input.TextArea
                value={content}
                onChange={(e) => handleContentChange(selectedEpisode.episodeNumber, e.target.value)}
                autoSize={{ minRows: 14, maxRows: 24 }}
                placeholder="本集脚本将在这里出现（可手动编辑，自动保存）"
              />
            </div>
          </div>
        </div>
      );
    }

    if (key === 'LOOP_REVIEW') {
      if (!selectedEpisode) return <div className="ssw-empty">未找到该集数据</div>;
      const review = safeJsonParse<any>(selectedEpisode.reviewResult);
      if (!review) return <div className="ssw-empty">该集尚无审稿结果</div>;

      return (
        <div className="ssw-output">
          <div className="ssw-section">
            <div className="ssw-section-title">审稿结果</div>
            <div style={{ display: 'flex', gap: 10, alignItems: 'center', marginBottom: 8 }}>
              <Tag color={Number(review.score) >= 7 ? 'success' : 'error'}>得分 {review.score}/10</Tag>
              <Tag color={review.passed ? 'success' : 'warning'}>{review.passed ? '通过' : '未通过'}</Tag>
            </div>
            {review.comments && <div style={{ marginBottom: 8 }}>评价：{review.comments}</div>}
            {review.suggestions && <div style={{ whiteSpace: 'pre-wrap' }}>建议：{review.suggestions}</div>}
          </div>
        </div>
      );
    }

    if (key === 'LOOP_ANALYZE' || key === 'LOOP_DECIDE') {
      if (!selectedEpisode) return <div className="ssw-empty">未找到该集数据</div>;
      const analysis = safeJsonParse<any>(selectedEpisode.analysisResult);
      if (!analysis) return <div className="ssw-empty">该集尚无分析结果</div>;

      return (
        <div className="ssw-output">
          <div className="ssw-section">
            <div className="ssw-section-title">连续性分析</div>
            {analysis.episodeSummary && (
              <div style={{ marginBottom: 10 }}>
                <div style={{ fontWeight: 700, marginBottom: 6 }}>剧情摘要（记忆）</div>
                <div style={{ whiteSpace: 'pre-wrap' }}>{analysis.episodeSummary}</div>
              </div>
            )}
            {analysis.nextEpisodeFocus && (
              <div style={{ marginBottom: 10 }}>
                <div style={{ fontWeight: 700, marginBottom: 6 }}>下一集重点</div>
                <div style={{ whiteSpace: 'pre-wrap' }}>{analysis.nextEpisodeFocus}</div>
              </div>
            )}
            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
              <Tag color={analysis.needOutlineUpdate ? 'warning' : 'default'}>
                大纲更新：{analysis.needOutlineUpdate ? '需要' : '不需要'}
              </Tag>
              <Tag color={analysis.needHookUpdate ? 'warning' : 'default'}>
                看点更新：{analysis.needHookUpdate ? '需要' : '不需要'}
              </Tag>
            </div>
            {analysis.outlineUpdateSuggestion && (
              <div style={{ marginTop: 10, whiteSpace: 'pre-wrap' }}>大纲建议：{analysis.outlineUpdateSuggestion}</div>
            )}
            {analysis.hookUpdateGuidance && (
              <div style={{ marginTop: 10, whiteSpace: 'pre-wrap' }}>看点建议：{analysis.hookUpdateGuidance}</div>
            )}
          </div>
        </div>
      );
    }

    if (key === 'LOOP_COMMIT') {
      if (!selectedEpisode) return <div className="ssw-empty">未找到该集数据</div>;
      return (
        <div className="ssw-output">
          <div className="ssw-section">
            <div className="ssw-section-title">封装入库</div>
            <div className="ssw-muted">状态：{selectedEpisode.status}</div>
            <div style={{ marginTop: 10 }}>
              {selectedEpisode.status === 'FAILED' ? (
                <Tag color="error">本集失败，可在左侧列表点击“重试”</Tag>
              ) : (
                <Tag color="success">本集已封装，继续生成将进入下一集</Tag>
              )}
            </div>
          </div>
        </div>
      );
    }

    return <div className="ssw-empty">未知节点</div>;
  };

  if (loading) {
    return (
      <div className="ssw-loading">
        <Spin size="large" />
      </div>
    );
  }

  if (!script) {
    return (
      <div className="ssw-loading">
        <div style={{ textAlign: 'center' }}>
          <div style={{ marginBottom: 12 }}>无法加载数据</div>
          <Button onClick={() => navigate('/video-scripts')}>返回列表</Button>
        </div>
      </div>
    );
  }

  const currentStatusTag = () => {
    const s = script.status;
    if (s === 'WORKFLOW_RUNNING') return <Tag color="processing">运行中</Tag>;
    if (s === 'GENERATING_OUTLINE') return <Tag color="processing">生成中</Tag>;
    if (s === 'WORKFLOW_PAUSED') return <Tag color="warning">已暂停</Tag>;
    if (s === 'COMPLETED') return <Tag color="success">已完成</Tag>;
    if (s === 'FAILED') return <Tag color="error">失败</Tag>;
    return <Tag>{s}</Tag>;
  };

  const modeLabel = script.mode === 'PURE_NARRATION' ? '纯解说' : '半解说';
  const formatLabel = (fmt?: string) => {
    if (fmt === 'SCENE') return '集-场台本';
    if (fmt === 'STORYBOARD') return '分镜脚本';
    if (fmt === 'NARRATION') return '解说口播';
    return fmt || '分镜脚本';
  };

  return (
    <div className="ssw-page">
      <div className="ssw-main-wrapper">
        <header className="ssw-topbar">
          <div className="ssw-topbar-left">
            <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/video-scripts')} />
            <div className="ssw-title">
              <div className="ssw-title-main">{script.title}</div>
              <div className="ssw-title-sub">
                {currentStatusTag()}
                <span className="ssw-dot">·</span>
                <span>进度 {workflowProgress}%</span>
                <span className="ssw-dot">·</span>
                <span className="ssw-muted">
                  {modeLabel} · {formatLabel(script.scriptFormat)} · 每集 {script.targetSeconds || 0}s · 镜头 {script.sceneCount || 0} · 已完成 {script.currentEpisode || 0}/
                  {script.episodeCount || 0} 集
                </span>
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
            <div className="ssw-left-head">剧集</div>

            <div className="ssw-left-item">
              <div className="ssw-left-item-title">系列信息</div>
              <div className="ssw-left-item-sub">
                模式：{modeLabel} · 格式：{formatLabel(script.scriptFormat)} · 计划：{script.episodeCount || 0} 集 · 当前：{script.currentEpisode || 0} 集
              </div>
            </div>

            <div className="ssw-left-divider">剧集列表</div>

            <div className="ssw-left-list ssw-left-list-with-footer">
              {episodes.map((ep) => {
                const isActive = episodeNumberInView === ep.episodeNumber;
                const failed = ep.status === 'FAILED';
                return (
                  <div
                    key={ep.episodeNumber}
                    className={isActive ? 'ssw-left-chapter active' : 'ssw-left-chapter'}
                    onClick={() => {
                      setSelectedEpisodeNumber(ep.episodeNumber);
                      setSelectedNodeKey('LOOP_GENERATE');
                      setRightPanelTab('output');
                    }}
                  >
                    <div className="ssw-left-chapter-main">
                      <span className="ssw-left-chapter-no">{String(ep.episodeNumber).padStart(2, '0')}</span>
                      <span className="ssw-left-chapter-title">{ep.title}</span>
                    </div>
                    <div className="ssw-left-chapter-sub" style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                      <Tag style={{ marginInlineEnd: 0 }}>{ep.status}</Tag>
                      {failed && (
                        <Button
                          size="small"
                          icon={<ReloadOutlined />}
                          loading={actionLoading}
                          onClick={(e) => {
                            e.stopPropagation();
                            handleRetry(ep.episodeNumber);
                          }}
                        >
                          重试
                        </Button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>

            <div className="ssw-left-footer">
              <Button icon={<DownloadOutlined />} onClick={handleExport} className="ssw-export-btn" block>
                导出系列
              </Button>
            </div>
          </aside>

          <main className="ssw-main">
            <div className="ssw-canvas">
              <div className="ssw-canvas-toolbar">
                <Tooltip title="定位到当前节点">
                  <Button
                    size="small"
                    icon={<AimOutlined />}
                    onClick={() => {
                      if (activeLoopNodeKey) centerOnNode(activeLoopNodeKey);
                    }}
                  />
                </Tooltip>
                <Tooltip title="放大">
                  <Button size="small" icon={<ZoomInOutlined />} onClick={() => setScale((s) => clamp(s * 1.12, 0.45, 1.9))} />
                </Tooltip>
                <Tooltip title="缩小">
                  <Button size="small" icon={<ZoomOutOutlined />} onClick={() => setScale((s) => clamp(s * 0.9, 0.45, 1.9))} />
                </Tooltip>
                <div className="ssw-canvas-toolbar-scale">{Math.round(scale * 100)}%</div>
              </div>

              <div className="ssw-canvas-viewport" ref={viewportRef} onPointerDown={onViewportPointerDown} onWheel={onViewportWheel}>
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
                      <marker id="arrow" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto" markerUnits="strokeWidth">
                        <path d="M0,0 L9,3 L0,6" fill="none" stroke="#cbd5e1" strokeWidth="1.5" />
                      </marker>
                    </defs>

                    {edges.map((e, idx) => {
                      const from = nodeByKey.get(e.from);
                      const to = nodeByKey.get(e.to);
                      if (!from || !to) return null;
                      const d = makeEdgePath(from, to);
                      return <path key={idx} d={d} className={`ssw-edge ${e.type}`} markerEnd={'url(#arrow)'} />;
                    })}
                  </svg>

                  {nodes.map((n) => {
                    const isActive = activeLoopNodeKey === n.key;
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
                        <div className="ssw-node-sub">{n.subtitle || ''}</div>
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>
          </main>
        </div>
      </div>

      <aside className="ssw-right">
        <div className="ssw-right-header">
          <div className="ssw-right-tabs">
            <button className={`ssw-tab-btn ${rightPanelTab === 'output' ? 'active' : ''}`} onClick={() => setRightPanelTab('output')}>
              <CodeOutlined />
              <span>输出</span>
            </button>
            <button className={`ssw-tab-btn ${rightPanelTab === 'logs' ? 'active' : ''}`} onClick={() => setRightPanelTab('logs')}>
              <HistoryOutlined />
              <span>日志</span>
              {filteredLogs.length > 0 && <span className="ssw-tab-badge">{filteredLogs.length}</span>}
            </button>
          </div>
        </div>

        <div className="ssw-right-content">
          {rightPanelTab === 'output' ? (
            <div className="ssw-output-panel">
              {selectedNodeKey ? (
                <>
                  <div className="ssw-output-header">
                    <div className="ssw-output-node-info">
                      {getNodeIcon(selectedNodeKey)}
                      <span className="ssw-output-node-name">{nodeByKey.get(selectedNodeKey)?.title || selectedNodeKey}</span>
                    </div>
                    <Tag color={STATUS_TAG_COLOR[nodeByKey.get(selectedNodeKey)?.status || 'PENDING']}>
                      {STATUS_LABEL[nodeByKey.get(selectedNodeKey)?.status || 'PENDING']}
                    </Tag>
                  </div>
                  <div className="ssw-output-body">{renderNodeOutput()}</div>
                </>
              ) : (
                <div className="ssw-empty-state">
                  <div className="empty-text">
                    <h3>选择节点查看输出</h3>
                    <p>点击画布中的节点卡片，查看该步骤的生成内容</p>
                  </div>
                </div>
              )}
            </div>
          ) : (
            <div className="ssw-logs-panel">
              {script?.status === 'DRAFT' ? (
                <div className="ssw-empty-state">
                  <div className="empty-text">
                    <h3>暂无运行日志</h3>
                    <p>开始生成后将显示工作流日志</p>
                  </div>
                </div>
              ) : (
                <>
                  <div style={{ padding: '12px 12px 0' }}>
                    <Input size="small" placeholder="搜索日志" value={logSearch} onChange={(e) => setLogSearch(e.target.value)} allowClear />
                  </div>
                  <div className="ssw-log-stream">
                    {filteredLogs.length === 0 ? (
                      <div className="ssw-empty" style={{ marginTop: 16 }}>
                        暂无日志
                      </div>
                    ) : (
                      filteredLogs.map((l) => (
                        <div key={l.id} className={`ssw-log-message type-${String(l.type || '').toLowerCase()}`}>
                          <div className="log-header">
                            <div className="log-info">
                              <span className={`log-dot status-${String(l.type || '').toLowerCase()}`} />
                              <span className="log-type">{String(l.type || '')}</span>
                              {l.episodeNumber != null && <span className="ssw-muted">· 第 {l.episodeNumber} 集</span>}
                            </div>
                            <span className="log-time">{dayjs(l.createdAt).format('HH:mm:ss')}</span>
                          </div>
                          <div className="log-content">{String(l.content || '')}</div>
                        </div>
                      ))
                    )}
                  </div>
                </>
              )}
            </div>
          )}
        </div>

        <div className="ssw-control-bar">
          {script?.status === 'DRAFT' || script?.status === 'WORKFLOW_PAUSED' ? (
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
                    models.map((m) => (
                      <option key={m.modelId} value={m.modelId}>
                        {m.displayName || m.modelId}
                      </option>
                    ))
                  )}
                </select>
              </div>

              <div className="model-selector-row">
                <label className="input-label">格式</label>
                <select
                  className="model-select-input"
                  value={selectedScriptFormat || 'STORYBOARD'}
                  onChange={(e) => setSelectedScriptFormat(e.target.value)}
                >
                  <option value="SCENE">集-场台本（真人短剧/影视）</option>
                  <option value="STORYBOARD">分镜脚本（漫剧/动态漫）</option>
                  <option value="NARRATION">解说口播文案（竖屏解说视频）</option>
                </select>
                <Tooltip title="复制该格式的空模板示例">
                  <Button
                    size="small"
                    onClick={async () => {
                      const fmt = (selectedScriptFormat || 'STORYBOARD').toUpperCase();
                      let example = '';
                      if (fmt === 'SCENE') {
                        example = `第01集 《标题》\n\n场1-1  内  夜  地点：______\n△（画面/动作）\n【SFX】风声（持续）\nA：台词……\nB（压低）：台词……\n\n场1-2  外  夜  地点：______\n△（冲突升级/反转）\nA OS：内心独白（可选）`;
                      } else if (fmt === 'NARRATION') {
                        example = `【本集标题】xxx\n【时长】X秒\n【解说文案】\n00:00 - 00:03：凌晨三点，我被一阵剧烈的震动惊醒。\n00:03 - 00:06：我猛地坐起来，发现整个房间都在晃动。\n...\n【结尾悬念】一句话\n【下一集引子】一句话`;
                      } else {
                        example = `镜头01  时长：3s  景别：特写  运镜：静止\n画面：______\n动效：______\n音效/BGM：【SFX】______\n台词：无\n\n镜头02  时长：5s  景别：中景  运镜：慢推\n画面：______\n音效/BGM：【BGM】冷氛围（轻）\n台词：\n- 角色A：台词……\n- 角色B（迟疑）：台词……`;
                      }
                      try {
                        await navigator.clipboard.writeText(example);
                        message.success('已复制格式示例');
                      } catch {
                        message.warning('复制失败，请手动选择文本复制');
                      }
                    }}
                  >
                    示例
                  </Button>
                </Tooltip>
              </div>

              <Button
                type="primary"
                size="large"
                icon={<PlayCircleOutlined />}
                loading={actionLoading}
                onClick={() =>
                  handleStart({
                    modelId: selectedModelId || undefined,
                    scriptFormat: selectedScriptFormat || undefined,
                  })
                }
                className={script?.status === 'DRAFT' ? 'start-btn-premium' : 'resume-btn-premium'}
                block
              >
                {script?.status === 'DRAFT' ? '开始生成' : '继续生成'}
              </Button>

              {script?.status !== 'DRAFT' && (
                <Button icon={<DownloadOutlined />} onClick={handleExport} block>
                  导出系列
                </Button>
              )}
            </div>
          ) : script?.status === 'WORKFLOW_RUNNING' || script?.status === 'GENERATING_OUTLINE' ? (
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
            <div className="control-content" style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {script?.status !== 'COMPLETED' ? (
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
              <Button icon={<DownloadOutlined />} onClick={handleExport} block>
                导出系列
              </Button>
            </div>
          )}
        </div>
      </aside>
    </div>
  );
};

export default VideoScriptWorkflowPage;
