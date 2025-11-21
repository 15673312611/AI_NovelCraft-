import React, { useState, useEffect, useRef } from 'react';
import { 
  Card, Button, Form, Input, Typography, Space, 
  Spin, Divider, Alert, Drawer, List, App, Badge, Modal, Tabs, Empty
} from 'antd';
import { 
  BookOutlined, RobotOutlined, 
  FileTextOutlined, HistoryOutlined, ArrowLeftOutlined, 
  CheckOutlined, ThunderboltOutlined, PlusOutlined, SaveOutlined,
  GlobalOutlined, HeartOutlined
} from '@ant-design/icons';
import { useParams, useLocation, useNavigate } from 'react-router-dom';
import api from '@/services/api';
import novelVolumeService, { NovelVolume, VolumeGuidanceRequest } from '../services/novelVolumeService';
import { withAIConfig, checkAIConfig, AI_CONFIG_ERROR_MESSAGE } from '../utils/aiRequest';
import './VolumeWritingStudio.css';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

const VolumeWritingStudio: React.FC = () => {
  const { message } = App.useApp();
  const location = useLocation();
  const searchParams = new URLSearchParams(location.search);
  const templateIdFromUrl = searchParams.get('templateId');
  
  const [currentVolume, setCurrentVolume] = useState<NovelVolume | null>(null);
  const [pageLoading, setPageLoading] = useState(true); // 鏁翠釜椤甸潰鍔犺浇鐘舵€?
  const [pageLoadError, setPageLoadError] = useState<string | null>(null); // 椤甸潰鍔犺浇閿欒
  const [loading, setLoading] = useState(false);
  const [currentContent, setCurrentContent] = useState('');
  const [progressHint, setProgressHint] = useState(''); // 鐢熸垚杩涘害鎻愮ず
  const [aiGuidance, setAiGuidance] = useState<any>(null);
  const [guidanceHistory, setGuidanceHistory] = useState<any[]>([]);
  const [wordCount, setWordCount] = useState(0);
  // 宸茬Щ闄ゅ墠绔蹇嗗簱骞查锛屼笂涓嬫枃鐢卞悗绔瀯寤?
  const [chapterNumber, setChapterNumber] = useState<number | null>(null);
  const [chapterTitle, setChapterTitle] = useState<string>('');
  const [userAdjustment, setUserAdjustment] = useState<string>('');
  const [chapterId, setChapterId] = useState<string | null>(null);
  const chapterIdRef = useRef<string | null>(null); // 鐢ㄤ簬鍦ㄩ棴鍖呬腑璁块棶鏈€鏂扮殑chapterId
  const chapterNumberRef = useRef<number | null>(null); // 鐢ㄤ簬鍦ㄩ棴鍖呬腑璁块棶鏈€鏂扮殑chapterNumber
  const [isStreaming, setIsStreaming] = useState(false);
  const streamingCompleteRef = useRef<boolean>(false); // 鏍囪娴佸紡鍐欎綔鏄惁鐪熸瀹屾垚锛堟敹鍒癱omplete浜嬩欢锛?
  const textareaRef = useRef<any>(null);
    const [aiDrawerVisible, setAiDrawerVisible] = useState(false); // AI鍐欎綔寮圭獥
    const [chapterPlotInput, setChapterPlotInput] = useState<string>(''); // 鏈珷鍓ф儏杈撳叆
    const [selectedModel, setSelectedModel] = useState<string>(''); // 閫夋嫨鐨凙I妯″瀷锛堢┖琛ㄧず浣跨敤鍚庣榛樿锛?
  
  // 鎶藉眽鐘舵€?
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
  
  // 鍗峰垏鎹㈢浉鍏崇姸鎬?
  const [allVolumes, setAllVolumes] = useState<NovelVolume[]>([]);
  const [novelInfo, setNovelInfo] = useState<any>(null);
  
  // 绔犺妭鍒楄〃鐩稿叧鐘舵€?
  const [chapterList, setChapterList] = useState<any[]>([]);
  const [chapterListLoading, setChapterListLoading] = useState(false);
  const [chapterDirHovered, setChapterDirHovered] = useState(false); // 绔犺妭鐩綍鎮仠鐘舵€?
  
  // 鑷姩淇濆瓨鐩稿叧鐘舵€?
  const autoSaveTimerRef = useRef<number | null>(null);
  const [lastSavedContent, setLastSavedContent] = useState<string>('');
  const [isSaving, setIsSaving] = useState(false);
  const [lastSaveTime, setLastSaveTime] = useState<string>('');
  const [isRemovingAITrace, setIsRemovingAITrace] = useState(false); // AI娑堢棔loading鐘舵€?
  const [aiTraceDrawerVisible, setAiTraceDrawerVisible] = useState(false); // AI娑堢棔鎶藉眽
  const [processedContent, setProcessedContent] = useState<string>(''); // AI娑堢棔澶勭悊鍚庣殑鍐呭
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | null>(
    templateIdFromUrl ? Number(templateIdFromUrl) : null
  ); // 閫変腑鐨勬ā鏉縄D锛堜粠URL鍙傛暟鍒濆鍖栵級
  const [templateModalVisible, setTemplateModalVisible] = useState(false); // 妯℃澘閫夋嫨寮圭獥
  const [templateModalTab, setTemplateModalTab] = useState('public'); // 妯℃澘寮圭獥褰撳墠tab
  const [publicTemplates, setPublicTemplates] = useState<any[]>([]); // 鍏紑妯℃澘
  const [favoriteTemplates, setFavoriteTemplates] = useState<any[]>([]); // 鏀惰棌妯℃澘
  const [customTemplates, setCustomTemplates] = useState<any[]>([]); // 鑷畾涔夋ā鏉?

  // 鎵归噺鍐欎綔鐩稿叧鐘舵€?
  const [batchWriting, setBatchWriting] = useState(false); // 鎵归噺鍐欎綔鐘舵€?
  const [batchProgress, setBatchProgress] = useState({ current: 0, total: 0 }); // 鎵归噺鍐欎綔杩涘害
  const [batchCancelled, setBatchCancelled] = useState(false); // 鎵归噺鍐欎綔鏄惁琚彇娑?
  const [batchModalVisible, setBatchModalVisible] = useState(false); // 鎵归噺鍐欎綔杩涘害寮圭獥

  const { novelId, volumeId} = useParams<{ novelId: string; volumeId: string }>();
  const navigate = useNavigate();

  // 涓€閿牸寮忓寲锛?
  // 1) 鍦ㄥ彞鏈爣鐐癸紙銆傦紵锛侊級绨囧悗鎹㈣
  // 2) 鑻ュ悗闈㈢揣璺熷悗寮曞彿锛?鎴?鎴栧父瑙佸紩鍙凤級锛屽垯鍦ㄥ紩鍙峰悗鎹㈣
  // 3) 鑻ュ嚭鐜板彞鏈爣鐐?+ 鏈紩鍙?+ 寮€寮曞彿锛? "锛夛紝鍒欏湪涓や釜寮曞彿涔嬮棿鎻掑叆涓€涓┖琛岋紙娈佃惤锛?
  // 4) 瑙勮寖锛氬湪鍙ユ湯鏍囩偣涓庢湯寮曞彿涔嬮棿淇濈暀涓€涓┖鏍硷紙濡傦細"鈥︹€︼紵 "锛?
  const formatChineseSentences = (input: string): string => {
    if (!input) return '';
    let text = input.replace(/\r\n?/g, '\n');
    // 浼樺厛澶勭悊锛氭爣鐐圭皣 + 鏈紩鍙?+ 寮€寮曞彿 -> 淇濈暀鏈紩鍙峰湪鏈锛屼箣鍚庣┖涓€琛屽啀寮€濮嬩笅涓€娈?
    text = text.replace(/([銆傦紵锛乚+)\s*(["'])\s*([""'])/g, '$1 $2\n\n$3');
    // 鍏舵锛氭爣鐐圭皣 + 鏈紩鍙凤紙鍚庨潰涓嶆槸寮€寮曞彿锛?> 鍦ㄦ湯寮曞彿鍚庢崲琛?
    text = text.replace(/([銆傦紵锛乚+)\s*(["'])(?!\s*[""'])\s*/g, '$1 $2\n');
    // 鍐嶈€咃細鏍囩偣绨囧悗鐩存帴鎹㈣锛堝悗闈㈡病鏈夋湯寮曞彿锛?
    text = text.replace(/([銆傦紵锛乚+)(?!\s*[""'])\s*/g, '$1\n');
    // 琛岀骇娓呯悊锛氬幓闄ゆ瘡琛岄閮ㄧ殑绌虹櫧锛堝惈鍏ㄨ绌烘牸锛夛紝浠ュ強琛屽熬绌虹櫧
    text = text
      .split('\n')
      .map(line => line.replace(/^[\t \u3000]+/g, '').replace(/\s+$/g, ''))
      .join('\n');
    return text;
  };

  useEffect(() => {
    // 椤甸潰鍒濆鍖?
    initializePage();
    
    // 浠庤矾鐢辩姸鎬佷腑鑾峰彇浼氳瘽鏁版嵁
    const state = location.state as any;
    if (state?.sessionData) {
      setAiGuidance(state.sessionData.aiGuidance);
    }
  }, []);


  // 鍔犺浇鎻愮ず璇嶆ā鏉垮垪琛?
  const loadPromptTemplates = async () => {
    try {
      // 鍔犺浇鍏紑妯℃澘
      const publicResponse = await api.get('/prompt-templates/public');
      setPublicTemplates(publicResponse.data || []);
      
      // 鍔犺浇鏀惰棌妯℃澘
      const favoriteResponse = await api.get('/prompt-templates/favorites');
      setFavoriteTemplates(favoriteResponse.data || []);
      
      // 鍔犺浇鑷畾涔夋ā鏉?
      const customResponse = await api.get('/prompt-templates/custom');
      setCustomTemplates(customResponse.data || []);
    } catch (error) {
      console.error('鍔犺浇鎻愮ず璇嶆ā鏉垮け璐?', error);
    }
  };

  // 鐩戝惉绔犺妭鍙峰彉鍖?妫€鏌ユ槸鍚﹂渶瑕佸垏鎹㈠嵎
  useEffect(() => {
    if (chapterNumber && currentVolume && chapterNumber > currentVolume.chapterEnd) {
      checkAndSwitchToNextVolume();
    }
  }, [chapterNumber]);

  // 鍔犺浇绔犺妭鍒楄〃
  useEffect(() => {
    if (currentVolume) {
      loadChapterList();
    }
  }, [currentVolume]);

  // 鍚屾 chapterId 鍒?ref锛岀‘淇濋棴鍖呬腑鑳借闂埌鏈€鏂板€?
  useEffect(() => {
    chapterIdRef.current = chapterId;
    console.log('馃攳 chapterId 宸叉洿鏂板埌 ref:', chapterId);
  }, [chapterId]);

  // 鍚屾 chapterNumber 鍒?ref锛岀‘淇濋棴鍖呬腑鑳借闂埌鏈€鏂板€?
  useEffect(() => {
    chapterNumberRef.current = chapterNumber;
    console.log('馃攳 chapterNumber 宸叉洿鏂板埌 ref:', chapterNumber);
  }, [chapterNumber]);

  // 鑷姩淇濆瓨鍔熻兘锛氬仠姝㈢紪鍐?绉掑悗鑷姩淇濆瓨
  useEffect(() => {
    console.log('馃攳 鑷姩淇濆瓨useEffect瑙﹀彂', {
      contentLength: currentContent?.length,
      lastSavedLength: lastSavedContent?.length,
      chapterTitle,
      chapterId,
      novelId,
      contentPreview: currentContent?.substring(0, 30)
    });
    
    // 娓呴櫎涔嬪墠鐨勫畾鏃跺櫒
    if (autoSaveTimerRef.current) {
      clearTimeout(autoSaveTimerRef.current);
      autoSaveTimerRef.current = null;
    }

    // 濡傛灉鍐呭涓虹┖鎴栦笌涓婃淇濆瓨鐨勫唴瀹圭浉鍚岋紝涓嶈Е鍙戣嚜鍔ㄤ繚瀛?
    if (!currentContent || currentContent === lastSavedContent) {
      console.log('鈴笍 璺宠繃淇濆瓨锛氬唴瀹逛负绌烘垨鏈彉鍖?, {
        hasContent: !!currentContent,
        isSame: currentContent === lastSavedContent
      });
      setIsSaving(false);
      return;
    }
    
    console.log('鈴?璁剧疆1绉掑悗鑷姩淇濆瓨瀹氭椂鍣?..');

    // 鏍囪涓?缂栬緫涓?锛堜笉鏄剧ず淇濆瓨鐘舵€侊級
    setIsSaving(false);

    // 璁剧疆鏂扮殑瀹氭椂鍣細鍋滄缂栧啓1绉掑悗鎵嶄繚瀛?
    const timer = window.setTimeout(async () => {
      try {
        setIsSaving(true);
        console.log('馃攧 寮€濮嬭嚜鍔ㄤ繚瀛樼珷鑺傚唴瀹?..', {
          chapterId,
          title: chapterTitle,
          contentLength: currentContent.length
        });
        
        const payload: any = {
          title: chapterTitle || `绗?{chapterNumber || '?'}绔燻,
          content: currentContent,
          chapterNumber: chapterNumber || undefined,
          novelId: novelId ? parseInt(novelId) : undefined
        };

        if (chapterId) {
          // 鏇存柊鐜版湁绔犺妭
          await api.put(`/chapters/${chapterId}`, payload);
          console.log('鉁?鑷姩淇濆瓨鎴愬姛 - 鏇存柊绔犺妭ID:', chapterId);
        } else {
          // 鍒涘缓鏂扮珷鑺?
          const res: any = await api.post('/chapters', payload);
          const created = res?.data || res;
          if (created?.id) {
            setChapterId(String(created.id));
            console.log('鉁?鑷姩淇濆瓨鎴愬姛 - 鍒涘缓鏂扮珷鑺侷D:', created.id);
          }
        }
        
        setLastSavedContent(currentContent);
        
        // 鏇存柊鏈€鍚庝繚瀛樻椂闂达紙骞存湀鏃?鏃跺垎绉掞級
        const now = new Date();
        const timeStr = `${now.getFullYear()}-${(now.getMonth() + 1).toString().padStart(2, '0')}-${now.getDate().toString().padStart(2, '0')} ${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`;
        setLastSaveTime(timeStr);
        console.log('鉁?淇濆瓨鏃堕棿宸叉洿鏂?', timeStr);
        
        setIsSaving(false);
      } catch (e: any) {
        console.error('鉂?鑷姩淇濆瓨澶辫触:', e);
        setIsSaving(false);
      }
    }, 1000);

    autoSaveTimerRef.current = timer;

    // 娓呯悊鍑芥暟
    return () => {
      if (autoSaveTimerRef.current) {
        clearTimeout(autoSaveTimerRef.current);
      }
    };
  }, [currentContent, chapterTitle, chapterId, lastSavedContent, novelId]);

  // 鍓嶇涓嶅啀鍔犺浇/鎸佷箙鍖栬蹇嗗簱


  useEffect(() => {
    // 璁＄畻瀛楁暟
    const count = currentContent.replace(/\s/g, '').length;
    setWordCount(count);

    // 濡傛灉姝ｅ湪娴佸紡杈撳嚭锛岃嚜鍔ㄦ粴鍔ㄥ埌搴曢儴
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
      
      // 鍒濆鍖栫珷鑺傚彿锛氫紭鍏堣鍙栨湰鍦板瓨鍌ㄧ殑褰撳墠绔犺妭锛屽叾娆″洖閫€鍒板嵎璧峰绔犺妭
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
      // 鍥為€€锛氬皾璇曚粠灏忚鍗峰垪琛ㄤ腑鏌ユ壘璇ュ嵎
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
            throw new Error('鏈湪鍗峰垪琛ㄤ腑鎵惧埌瀵瑰簲鍗?);
          }
        } else {
          throw new Error('缂哄皯灏忚ID鍙傛暟');
        }
      } catch (e: any) {
        throw new Error(e.message || '鍔犺浇鍗蜂俊鎭け璐?);
      }
    }
  };

  // 椤甸潰鍒濆鍖栵細鍔犺浇鎵€鏈夊繀瑕佹暟鎹?
  const initializePage = async () => {
    setPageLoading(true);
    setPageLoadError(null);
    
    try {
      // 1. 妫€鏌ュ繀瑕佸弬鏁?
      if (!novelId) {
        throw new Error('缂哄皯灏忚ID鍙傛暟');
      }
      if (!volumeId) {
        throw new Error('缂哄皯鍗稩D鍙傛暟');
      }
      
      // 2. 骞惰鍔犺浇鎵€鏈夋暟鎹?
      await Promise.all([
        loadVolumeById(volumeId),
        loadAllVolumes(),
        loadNovelInfo(),
        loadPromptTemplates()
      ]);
      
      // 3. 鍔犺浇绔犺妭鍒楄〃锛堜緷璧栦簬鍗锋暟鎹級
      const chapters = await loadChapterList();
      
      // 4. 鑷姩璺宠浆鍒版渶鏂扮珷鑺?
      if (chapters && chapters.length > 0) {
        // 鎵惧埌鏈€鏂扮殑绔犺妭锛堢珷鑺傚彿鏈€澶х殑锛?
        const latestChapter = chapters.reduce((latest: any, current: any) => {
          return (current.chapterNumber || 0) > (latest.chapterNumber || 0) ? current : latest;
        });
        
        console.log('馃攧 鑷姩鍔犺浇鏈€鏂扮珷鑺?', latestChapter.chapterNumber);
        await handleLoadChapter(latestChapter);
      } else {
        // 濡傛灉娌℃湁绔犺妭锛岃缃负鍗风殑璧峰绔犺妭鍙?
        if (currentVolume?.chapterStart) {
          setChapterNumber(currentVolume.chapterStart);
        }
      }
      
      // 5. 椤甸潰鍔犺浇瀹屾垚
      setPageLoading(false);
      
    } catch (error: any) {
      console.error('椤甸潰鍒濆鍖栧け璐?', error);
      setPageLoadError(error.message || '椤甸潰鍔犺浇澶辫触');
      setPageLoading(false);
    }
  };

  // 鍔犺浇鎵€鏈夊嵎鍒楄〃
  const loadAllVolumes = async () => {
    if (!novelId) return;
    
    try {
      const volumes = await novelVolumeService.getVolumesByNovelId(novelId);
      // 鎸夊嵎鍙锋帓搴?
      const sortedVolumes = volumes.sort((a: NovelVolume, b: NovelVolume) => a.volumeNumber - b.volumeNumber);
      setAllVolumes(sortedVolumes);
    } catch (error: any) {
      console.error('鍔犺浇鍗峰垪琛ㄥけ璐?', error);
    }
  };

  // 鍔犺浇灏忚淇℃伅
  const loadNovelInfo = async () => {
    if (!novelId) return;
    
    try {
      const response = await api.get(`/novels/${novelId}`);
      setNovelInfo(response.data);
    } catch (error) {
      console.error('鍔犺浇灏忚淇℃伅澶辫触:', error);
    }
  };

  // 鍔犺浇绔犺妭鍒楄〃
  const loadChapterList = async () => {
    if (!currentVolume || !novelId) return [];
    
    setChapterListLoading(true);
    try {
      // 鑾峰彇褰撳墠鍗疯寖鍥村唴鐨勬墍鏈夌珷鑺?
      const response = await api.get(`/chapters/novel/${novelId}?summary=true`);
      const chapters = response.data || response || [];
      
      // 绛涢€夊嚭褰撳墠鍗风殑绔犺妭
      const volumeChapters = chapters.filter((ch: any) => {
        const chNum = ch.chapterNumber || 0;
        return chNum >= currentVolume.chapterStart && chNum <= currentVolume.chapterEnd;
      });
      
      // 鎸夌珷鑺傚彿鎺掑簭
      const sortedChapters = volumeChapters.sort((a: any, b: any) => {
        return (a.chapterNumber || 0) - (b.chapterNumber || 0);
      });
      
      setChapterList(sortedChapters);
      return sortedChapters;
      
    } catch (error) {
      console.error('鍔犺浇绔犺妭鍒楄〃澶辫触:', error);
      return [];
    } finally {
      setChapterListLoading(false);
    }
  };

  // 鍔犺浇灏忚澶х翰
  const loadNovelOutline = async () => {
    if (!novelId) return;
    try {
      console.log('寮€濮嬪姞杞藉皬璇村ぇ绾? novelId:', novelId);
      const response = await api.get(`/novels/${novelId}`);
      
      // 澶勭悊鏁版嵁锛歛pi.get 鍙兘鐩存帴杩斿洖鏁版嵁锛屼篃鍙兘鍦?response.data 涓?
      const data = response.data || response;
      
      console.log('=== 鍝嶅簲鏁版嵁 ===', data);
      console.log('=== outline瀛楁 ===', data?.outline);
      console.log('outline绫诲瀷:', typeof data?.outline);
      console.log('outline闀垮害:', data?.outline?.length);
      
      if (data && data.outline && typeof data.outline === 'string' && data.outline.trim().length > 0) {
        console.log('鉁?澶х翰鍔犺浇鎴愬姛锛岄暱搴?', data.outline.length);
        setEditingOutline(data.outline);
        message.success('澶х翰鍔犺浇鎴愬姛');
      } else {
        console.log('鉂?澶х翰涓虹┖');
        setEditingOutline('鏆傛棤澶х翰锛岃鍏堝湪澶х翰椤甸潰鐢熸垚');
        message.warning('鏆傛棤澶х翰鍐呭');
      }
    } catch (error: any) {
      console.error('鍔犺浇灏忚澶х翰澶辫触:', error);
      message.error('鍔犺浇灏忚澶х翰澶辫触');
      setEditingOutline('鍔犺浇澶辫触锛岃閲嶈瘯');
    }
  };

  // 淇濆瓨灏忚澶х翰
  const handleSaveNovelOutline = async () => {
    if (!novelId) return;
    setOutlineLoading(true);
    try {
      console.log('淇濆瓨灏忚澶х翰:', { novelId, outline: editingOutline.substring(0, 100) + '...' });
      await api.put(`/novels/${novelId}`, {
        outline: editingOutline
      });
      message.success('灏忚澶х翰宸蹭繚瀛?);
      setOutlineDrawerVisible(false);
    } catch (error: any) {
      console.error('淇濆瓨灏忚澶х翰澶辫触:', error);
      message.error('淇濆瓨灏忚澶х翰澶辫触');
    } finally {
      setOutlineLoading(false);
    }
  };

  // AI浼樺寲灏忚澶х翰锛堟祦寮忥級
  const handleAIOptimizeNovelOutline = async () => {
    if (!novelId || !editingOutline) {
      message.warning('璇峰厛鍔犺浇澶х翰鍐呭');
      return;
    }

    // 妫€鏌I閰嶇疆
    if (!checkAIConfig()) {
      message.error(AI_CONFIG_ERROR_MESSAGE);
      return;
    }

    // 寮圭獥杈撳叆寤鸿
    const suggestion = await new Promise<string | null>((resolve) => {
      let inputValue = '';
      Modal.confirm({
        title: '馃摑 AI浼樺寲澶х翰',
        width: 600,
        content: (
          <div style={{ marginTop: '16px' }}>
            <div style={{ marginBottom: '12px', color: '#64748b', fontSize: '14px' }}>
              璇疯緭鍏ユ偍瀵瑰ぇ绾茬殑浼樺寲寤鸿锛孉I灏嗘牴鎹偍鐨勫缓璁繘琛屼紭鍖栵細
            </div>
            <Input.TextArea
              placeholder="渚嬪锛氬寮轰富瑙掔殑鎬ф牸濉戦€狅紝鍔犲叆鏇村鐨勬偓蹇靛厓绱?.."
              rows={4}
              onChange={(e) => { inputValue = e.target.value; }}
              style={{ fontSize: '14px' }}
            />
          </div>
        ),
        okText: '寮€濮嬩紭鍖?,
        cancelText: '鍙栨秷',
        onOk: () => resolve(inputValue || null),
        onCancel: () => resolve(null)
      });
    });

    if (!suggestion) return;

    setOutlineLoading(true);
    const originalOutline = editingOutline; // 淇濆瓨鍘熷鍐呭
    setEditingOutline(''); // 娓呯┖鍘熷唴瀹癸紝鍑嗗鎺ユ敹娴佸紡杈撳嚭
    
    try {
      message.loading({ content: 'AI姝ｅ湪浼樺寲澶х翰...', key: 'optimizing', duration: 0 });
      
      const token = localStorage.getItem('token');
      const requestBody = withAIConfig({
        novelId: parseInt(novelId),
        currentOutline: originalOutline, // 浣跨敤鍘熷鍐呭
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
        throw new Error('缃戠粶璇锋眰澶辫触');
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
            message.success('澶х翰浼樺寲瀹屾垚');
          } else if (eventName === 'error') {
            throw new Error(data);
          }
        }
      }
    } catch (error: any) {
      message.destroy('optimizing');
      console.error('AI浼樺寲澶х翰澶辫触:', error);
      message.error(error.message || 'AI浼樺寲澶х翰澶辫触');
      // 澶辫触鏃舵仮澶嶅師鍐呭
      setEditingOutline(originalOutline);
    } finally {
      setOutlineLoading(false);
    }
  };

  // 淇濆瓨鍗峰ぇ绾?
  const handleSaveVolumeOutline = async () => {
    if (!currentVolume) return;
    setOutlineLoading(true);
    try {
      await api.put(`/volumes/${currentVolume.id}`, {
        contentOutline: editingVolumeOutline
      });
      message.success('鍗峰ぇ绾插凡淇濆瓨');
      setVolumeOutlineDrawerVisible(false);
      // 鏇存柊褰撳墠鍗蜂俊鎭?
      const updated = { ...currentVolume, contentOutline: editingVolumeOutline };
      setCurrentVolume(updated);
    } catch (error) {
      console.error('淇濆瓨鍗峰ぇ绾插け璐?', error);
      message.error('淇濆瓨鍗峰ぇ绾插け璐?);
    } finally {
      setOutlineLoading(false);
    }
  };

  // AI浼樺寲鍗峰ぇ绾诧紙娴佸紡锛?
  const handleAIOptimizeVolumeOutline = async () => {
    if (!currentVolume || !editingVolumeOutline) {
      message.warning('璇峰厛鍔犺浇鍗峰ぇ绾插唴瀹?);
      return;
    }

    // 妫€鏌I閰嶇疆
    if (!checkAIConfig()) {
      message.error(AI_CONFIG_ERROR_MESSAGE);
      return;
    }

    // 寮圭獥杈撳叆寤鸿
    const suggestion = await new Promise<string | null>((resolve) => {
      let inputValue = '';
      Modal.confirm({
        title: `馃摉 AI浼樺寲绗?{currentVolume.volumeNumber}鍗峰ぇ绾瞏,
        width: 600,
        content: (
          <div style={{ marginTop: '16px' }}>
            <div style={{ marginBottom: '12px', color: '#64748b', fontSize: '14px' }}>
              璇疯緭鍏ユ偍瀵规湰鍗峰ぇ绾茬殑浼樺寲寤鸿锛孉I灏嗘牴鎹偍鐨勫缓璁繘琛屼紭鍖栵細
            </div>
            <Input.TextArea
              placeholder="渚嬪锛氬姞寮轰富瑙掍笌鍙嶆淳鐨勫鎶楋紝澧炲姞鎯呮劅鍐茬獊..."
              rows={4}
              onChange={(e) => { inputValue = e.target.value; }}
              style={{ fontSize: '14px' }}
            />
            <div style={{ marginTop: '12px', padding: '8px 12px', background: '#f1f5f9', borderRadius: '6px', fontSize: '13px', color: '#64748b' }}>
              <div><strong>鍗蜂俊鎭細</strong>绗瑊currentVolume.volumeNumber}鍗?/div>
              <div><strong>绔犺妭鑼冨洿锛?/strong>绗瑊currentVolume.chapterStart}-{currentVolume.chapterEnd}绔?/div>
            </div>
          </div>
        ),
        okText: '寮€濮嬩紭鍖?,
        cancelText: '鍙栨秷',
        onOk: () => resolve(inputValue || null),
        onCancel: () => resolve(null)
      });
    });

    if (!suggestion) return;

    setOutlineLoading(true);
    const originalOutline = editingVolumeOutline; // 淇濆瓨鍘熷鍐呭
    setEditingVolumeOutline(''); // 娓呯┖鍘熷唴瀹癸紝鍑嗗鎺ユ敹娴佸紡杈撳嚭
    
    try {
      message.loading({ content: 'AI姝ｅ湪浼樺寲鍗峰ぇ绾?..', key: 'optimizing-volume', duration: 0 });
      
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
        throw new Error('缃戠粶璇锋眰澶辫触');
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
            message.success('鍗峰ぇ绾蹭紭鍖栧畬鎴?);
            
            // 鏇存柊currentVolume鐨刢ontentOutline
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
      console.error('AI浼樺寲鍗峰ぇ绾插け璐?', error);
      message.error(error.message || 'AI浼樺寲鍗峰ぇ绾插け璐?);
      // 澶辫触鏃舵仮澶嶅師鍐呭
      setEditingVolumeOutline(originalOutline);
    } finally {
      setOutlineLoading(false);
    }
  };

  // 鏂板缓绔犺妭骞舵瑕?
  const handleCreateNewChapter = async () => {
    try {
      if (!chapterId) {
        message.warning('璇峰厛瀹屾垚褰撳墠绔犺妭鐨勫啓浣?);
        return;
      }

      if (!currentContent || currentContent.trim().length < 100) {
        message.warning('绔犺妭鍐呭澶皯锛屾棤娉曟柊寤?);
        return;
      }

      setLoading(true);
      message.loading({ content: '姝ｅ湪姒傝绔犺妭鍐呭锛岃绋嶅€?..', key: 'summarizing', duration: 0 });

      // 妫€鏌I閰嶇疆
      if (!checkAIConfig()) {
        message.warning('鏈厤缃瓵I锛屽皢浣跨敤鍚庣榛樿閰嶇疆姒傝绔犺妭');
      }

      // 璋冪敤姒傝鎺ュ彛锛堝悓姝ョ瓑寰呭畬鎴愶級锛屼紶閫扐I閰嶇疆
      const response = await fetch(`/api/chapters/${chapterId}/summarize`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(localStorage.getItem('token') ? { 'Authorization': `Bearer ${localStorage.getItem('token')}` } : {})
        },
        body: JSON.stringify(withAIConfig({}))
      });

      if (!response.ok) {
        throw new Error('姒傝绔犺妭澶辫触');
      }

      const result = await response.json();
      
      message.destroy('summarizing');
      
      // 鏄剧ず姒傝缁撴灉
      const characterCount = result.characterCount || 0;
      const eventCount = result.eventCount || 0;
      message.success(`绔犺妭姒傝瀹屾垚锛佹洿鏂颁簡${characterCount}涓鑹诧紝${eventCount}涓簨浠禶);

      // 鍒锋柊绔犺妭鍒楄〃
      await loadChapterList();

      // 娓呯┖褰撳墠缂栬緫鍣紝鍑嗗涓嬩竴绔?
      const nextChapterNumber = (chapterNumber || 0) + 1;
      
      setCurrentContent('');
      setChapterTitle('');
      setChapterId(null);
      setLastSavedContent('');
      setLastSaveTime('');
      
      // 绔嬪嵆鍒涘缓鏂扮珷鑺傜殑鏁版嵁搴撹褰曪紙閬垮厤椤甸潰娑堝け锛?
      try {
        const newChapterPayload = {
          title: `绗?{nextChapterNumber}绔燻,
          content: '', // 绌哄唴瀹?
          chapterNumber: nextChapterNumber,
          novelId: novelId ? parseInt(novelId) : undefined
        };
        
        console.log('馃攧 姝ｅ湪鍒涘缓鏂扮珷鑺傚埌鏁版嵁搴?', newChapterPayload);
        
        const response = await api.post('/chapters', newChapterPayload);
        const newChapter = response?.data || response;
        
        console.log('馃摑 鍒涘缓绔犺妭API鍝嶅簲:', newChapter);
        
        if (newChapter?.id) {
          console.log('鉁?鏂扮珷鑺傚凡鍒涘缓鍒版暟鎹簱:', {
            id: newChapter.id,
            title: newChapter.title,
            chapterNumber: newChapter.chapterNumber
          });
          
          // 璁剧疆鏂扮珷鑺傜殑鐘舵€?
          setChapterNumber(nextChapterNumber);
          setChapterId(String(newChapter.id));
          
          // 閲嶆柊鍔犺浇绔犺妭鍒楄〃锛岀‘淇濇柊绔犺妭鏄剧ず鍦ㄥ垪琛ㄤ腑
          console.log('馃攧 閲嶆柊鍔犺浇绔犺妭鍒楄〃...');
          await loadChapterList();
          
          message.success(`鉁?绗?{nextChapterNumber}绔犲凡鍒涘缓锛屽彲浠ュ紑濮嬪啓浣滀簡锛乣);
        } else {
          console.error('鉂?鍒涘缓绔犺妭澶辫触锛欰PI鍝嶅簲鏍煎紡寮傚父', response);
          throw new Error('鍒涘缓绔犺妭澶辫触锛氭湭杩斿洖绔犺妭ID');
        }
      } catch (createError: any) {
        console.error('鉂?鍒涘缓鏂扮珷鑺傚け璐?', {
          error: createError,
          message: createError?.message,
          response: createError?.response?.data
        });
        
        // 濡傛灉鍒涘缓澶辫触锛屽洖閫€鍒板師鏉ョ殑閫昏緫
        setTimeout(() => {
          setChapterNumber(nextChapterNumber);
          message.warning(`鈿狅笍 绔犺妭鍒涘缓鍙兘澶辫触锛屼絾鍙互寮€濮嬪啓绗?{nextChapterNumber}绔狅紙鍐欎綔鍚庝細鑷姩淇濆瓨锛塦);
        }, 100);
      }
    } catch (error: any) {
      message.destroy('summarizing');
      console.error('鏂板缓绔犺妭澶辫触:', error);
      message.error(error.message || '鏂板缓绔犺妭澶辫触');
    } finally {
      setLoading(false);
    }
  };

  // 妫€鏌ユ槸鍚﹂渶瑕佸垏鎹㈠埌涓嬩竴鍗?
  const checkAndSwitchToNextVolume = async () => {
    if (!currentVolume || !allVolumes.length) return;
    
    // 鎵惧埌涓嬩竴鍗?
    const sortedVolumes = [...allVolumes].sort((a, b) => a.volumeNumber - b.volumeNumber);
    const currentIndex = sortedVolumes.findIndex(v => v.id === currentVolume.id);
    
    if (currentIndex >= 0 && currentIndex < sortedVolumes.length - 1) {
      const nextVolume = sortedVolumes[currentIndex + 1];
      
      // 鎻愮ず鐢ㄦ埛
      message.success({
        content: `褰撳墠鍗峰凡瀹屾垚!鑷姩杩涘叆绗?{nextVolume.volumeNumber}鍗穈,
        duration: 3
      });
      
      // 璺宠浆鍒颁笅涓€鍗凤紙浣跨敤鏂扮殑鍐欎綔宸ヤ綔瀹わ級
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
        content: '馃帀 鎭枩!鏁撮儴灏忚宸插叏閮ㄥ畬鎴?',
        duration: 5
      });
    }
  };

  // 鎵归噺鍐欎綔澶勭悊鍑芥暟
  const handleBatchWriting = async () => {
    if (!chapterNumber) {
      message.warning('璇峰厛濉啓绔犺妭缂栧彿');
      return;
    }

    // 妫€鏌ュ綋鍓嶇姸鎬?
    console.log('鎵归噺鍐欎綔寮€濮嬪墠鐘舵€佹鏌?', {
      chapterNumber,
      chapterId,
      hasContent: !!currentContent,
      contentLength: currentContent?.length || 0
    });

    // 鏄剧ず纭瀵硅瘽妗?
    Modal.confirm({
      title: '鎵归噺鐢熸垚绔犺妭',
      content: (
        <div>
          <p>灏嗕粠绗瑊chapterNumber}绔犲紑濮嬶紝杩炵画鐢熸垚10绔犲唴瀹广€?/p>
          <p style={{ color: '#faad14' }}>鈿狅笍 姝よ繃绋嬪彲鑳介渶瑕佽緝闀挎椂闂达紝璇风‘淇濈綉缁滆繛鎺ョǔ瀹氥€?/p>
          <p>鎮ㄥ彲浠ラ殢鏃剁偣鍑?鍙栨秷"鎸夐挳涓鐢熸垚銆?/p>
        </div>
      ),
      okText: '寮€濮嬬敓鎴?,
      cancelText: '鍙栨秷',
      onOk: () => {
        console.log('鉁?鐢ㄦ埛纭寮€濮嬫壒閲忕敓鎴?);
        startBatchWriting();
      },
      onCancel: () => {
        console.log('鉂?鐢ㄦ埛鍙栨秷鎵归噺鐢熸垚');
        // 鏄庣‘澶勭悊鍙栨秷鎿嶄綔锛屼笉鎵ц浠讳綍鍐欎綔娴佺▼
      }
    });
  };

  // 寮€濮嬫壒閲忓啓浣?
  const startBatchWriting = async () => {
    setBatchWriting(true);
    setBatchCancelled(false);
    setBatchProgress({ current: 0, total: 10 });
    setBatchModalVisible(true);

    let successCount = 0;
    let failedChapters: number[] = [];
    
    // 淇濆瓨璧峰绔犺妭鍙凤紙閲嶈锛氫笉瑕佺敤chapterNumber锛屽畠浼氳handleCreateNewChapter鏀瑰彉锛?
    const startChapterNumber = chapterNumber || 0;

    try {
      for (let i = 0; i < 10; i++) {
        // 妫€鏌ユ槸鍚﹁鍙栨秷
        if (batchCancelled) {
          message.info(`鎵归噺鐢熸垚宸插彇娑堬紝宸叉垚鍔熺敓鎴?{successCount}绔燻);
          break;
        }

        // 浣跨敤璧峰绔犺妭鍙?+ i 璁＄畻褰撳墠绔犺妭鍙?
        const currentChapterNum = startChapterNumber + i;
        setBatchProgress({ current: i + 1, total: 10 });

        try {
          console.log(`\n=== 寮€濮嬬敓鎴愮${currentChapterNum}绔?===`);
          console.log('褰撳墠鐘舵€?', { chapterId, hasContent: !!currentContent, contentLength: currentContent?.length || 0 });

          // 姝ラ1: 鐐瑰嚮AI鍐欎綔鎸夐挳
          console.log(`姝ラ1: 寮€濮婣I鍐欎綔绗?{currentChapterNum}绔燻);
          await simulateAIWriting(currentChapterNum);

          // 姝ラ2: 绛夊緟鍐欎綔瀹屾垚
          console.log(`姝ラ2: 绛夊緟绗?{currentChapterNum}绔犲啓浣滃畬鎴?..`);
          await waitForWritingComplete();

          // 姝ラ3: 鍐欎綔瀹屾垚
          console.log(`绗?{currentChapterNum}绔犲啓浣滃畬鎴愶紝鍐呭闀垮害: ${currentContent?.length || 0}`);
          successCount++;

          // 姝ラ4: 濡傛灉涓嶆槸鏈€鍚庝竴绔狅紝鐐瑰嚮"鏂板缓绔犺妭"鎸夐挳
          if (i < 9 && !batchCancelled) {
            console.log(`姝ラ3: 鐐瑰嚮"鏂板缓绔犺妭"鎸夐挳锛屽噯澶囩${currentChapterNum + 1}绔?..`);
            
            // 閲嶈锛氬湪鐐瑰嚮鏂板缓绔犺妭鎸夐挳涔嬪墠锛屼粠ref璇诲彇褰撳墠绔犺妭鍙凤紙鏈€鍙潬锛?
            const currentChapterBeforeCreate = chapterNumberRef.current;
            console.log(`褰撳墠绔犺妭鍙?ref): ${currentChapterBeforeCreate}, 鏈熸湜鏂扮珷鑺傚彿: ${(currentChapterBeforeCreate || 0) + 1}`);
            
            // 鎵惧埌"鏂板缓绔犺妭"鎸夐挳骞剁偣鍑?
            await simulateClickNewChapterButton();
            
            // 绛夊緟姒傛嫭鍜岄〉闈㈠垏鎹㈠畬鎴愶紙浼犲叆鏈熸湜鐨勬柊绔犺妭鍙凤級
            const expectedNewChapterNumber = (currentChapterBeforeCreate || 0) + 1;
            console.log(`姝ラ4: 绛夊緟绗?{currentChapterNum}绔犳鎷畬鎴愬苟鍒囨崲鍒扮${expectedNewChapterNumber}绔?..`);
            await waitForNewChapterReady(expectedNewChapterNumber);
            
            console.log(`绗?{currentChapterNum}绔犳鎷畬鎴愶紝宸插垏鎹㈠埌绗?{expectedNewChapterNumber}绔犻〉闈);
            
            // 閲嶇疆娴佸紡瀹屾垚鏍囧織锛屽噯澶囦笅涓€绔犲啓浣?
            streamingCompleteRef.current = false;
            console.log('鉁?宸查噸缃祦寮忓畬鎴愭爣蹇楋紝鍑嗗寮€濮嬩笅涓€绔?);
          }

        } catch (chapterError: any) {
          console.error(`绗?{currentChapterNum}绔犵敓鎴愬け璐?`, chapterError);
          failedChapters.push(currentChapterNum);
          
          // 璇㈤棶鐢ㄦ埛鏄惁缁х画
          const shouldContinue = await new Promise<boolean>((resolve) => {
            Modal.confirm({
              title: '绔犺妭鐢熸垚澶辫触',
              content: (
                <div>
                  <p>绗瑊currentChapterNum}绔犵敓鎴愬け璐ワ細{chapterError.message}</p>
                  <p>鏄惁缁х画鐢熸垚鍓╀綑绔犺妭锛?/p>
                </div>
              ),
              okText: '缁х画鐢熸垚',
              cancelText: '鍋滄鐢熸垚',
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

      // 鏄剧ず鏈€缁堢粨鏋?
      if (!batchCancelled) {
        if (failedChapters.length === 0) {
          message.success('馃帀 鎵归噺鐢熸垚瀹屾垚锛佸凡鎴愬姛鐢熸垚10绔犲唴瀹?);
        } else {
          message.warning(
            `鎵归噺鐢熸垚瀹屾垚锛屾垚鍔熺敓鎴?{successCount}绔狅紝澶辫触${failedChapters.length}绔狅紙绗?{failedChapters.join('銆?)}绔狅級`
          );
        }
      }
    } catch (error: any) {
      console.error('鎵归噺鍐欎綔澶辫触:', error);
      message.error(error.message || '鎵归噺鍐欎綔杩囩▼涓嚭鐜颁弗閲嶉敊璇?);
    } finally {
      setBatchWriting(false);
      setBatchModalVisible(false);
      setBatchProgress({ current: 0, total: 0 });
    }
  };

  // 妯℃嫙AI鍐欎綔
  const simulateAIWriting = async (_chapterNum: number) => {
    return new Promise<void>((resolve, reject) => {
      try {
        // 瑙﹀彂AI鍐欎綔鎸夐挳
        const aiBtn = document.querySelector('[data-ai-write-trigger]') as HTMLButtonElement;
        if (aiBtn) {
          console.log('鐐瑰嚮AI鍐欎綔鎸夐挳');
          aiBtn.click();
          
          // 绛夊緟涓€涓嬬‘淇滱I鍐欎綔宸茬粡寮€濮嬶紙loading鍙樹负true锛?
          setTimeout(() => {
            console.log('AI鍐欎綔宸茶Е鍙?);
            resolve();
          }, 1000);
        } else {
          reject(new Error('鎵句笉鍒癆I鍐欎綔瑙﹀彂鍣?));
        }
      } catch (error) {
        reject(error);
      }
    });
  };

  // 绛夊緟鍐欎綔瀹屾垚锛堥€氳繃妫€鏌ユ祦寮忔帴鍙ｇ殑complete浜嬩欢锛?
  const waitForWritingComplete = async () => {
    return new Promise<void>((resolve) => {
      const checkComplete = () => {
        // 鐩存帴妫€鏌?streamingCompleteRef锛屼笉渚濊禆 hasStarted
        if (streamingCompleteRef.current) {
          // 鏀跺埌complete浜嬩欢锛屽啀绛夊緟2绉掔‘淇濊嚜鍔ㄤ繚瀛樺畬鎴?
          console.log('鉁?妫€娴嬪埌娴佸紡瀹屾垚鏍囧織锛屽啓浣滃凡瀹屾垚锛岀瓑寰呰嚜鍔ㄤ繚瀛?..');
          setTimeout(() => {
            console.log('鉁?鑷姩淇濆瓨搴旇宸插畬鎴?);
            resolve();
          }, 2000);
          return; // 閲嶈锛氱珛鍗宠繑鍥烇紝涓嶅啀缁х画妫€鏌?
        }
        
        // 濡傛灉琚彇娑堬紝鐩存帴杩斿洖
        if (batchCancelled) {
          console.log('鎵归噺鍐欎綔琚彇娑?);
          resolve();
          return;
        }
        
        // 缁х画绛夊緟锛堜笉璁剧疆瓒呮椂锛孉I鐢熸垚鏈潵灏辨參锛?
        setTimeout(checkComplete, 500);
      };
      
      // 寤惰繜寮€濮嬫鏌ワ紝缁橝I鍐欎綔涓€鐐规椂闂村惎鍔?
      setTimeout(checkComplete, 500);
    });
  };

  // 妯℃嫙鐐瑰嚮"鏂板缓绔犺妭"鎸夐挳锛堢瓑寰呮寜閽彲鐢級
  const simulateClickNewChapterButton = async () => {
    return new Promise<void>((resolve, reject) => {
      const startTime = Date.now();
      const timeout = 2 * 60 * 1000; // 2鍒嗛挓瓒呮椂锛堢瓑寰呰嚜鍔ㄤ繚瀛樺畬鎴愶級
      
      const tryClick = () => {
        const elapsed = Date.now() - startTime;
        
        try {
          // 鎵惧埌"鏂板缓绔犺妭"鎸夐挳
          const buttons = Array.from(document.querySelectorAll('button'));
          const newChapterBtn = buttons.find(btn => btn.textContent?.includes('鏂板缓绔犺妭')) as HTMLButtonElement;
          
          if (!newChapterBtn) {
            reject(new Error('鎵句笉鍒?鏂板缓绔犺妭"鎸夐挳'));
            return;
          }
          
          // 妫€鏌ユ寜閽槸鍚﹀彲鐢紙disabled={loading || !chapterId || !currentContent}锛?
          if (!newChapterBtn.disabled) {
            console.log('鎵惧埌"鏂板缓绔犺妭"鎸夐挳锛屾寜閽彲鐢紝鍑嗗鐐瑰嚮');
            newChapterBtn.click();
            
            // 绛夊緟涓€涓嬬‘淇濈偣鍑诲凡澶勭悊
            setTimeout(() => {
              console.log('"鏂板缓绔犺妭"鎸夐挳宸茬偣鍑?);
              resolve();
            }, 500);
          } else {
            // 鎸夐挳杩樿绂佺敤锛岀户缁瓑寰?
            console.log('鏂板缓绔犺妭鎸夐挳琚鐢紝绛夊緟鎸夐挳鍙敤...', { 
              loading, 
              chapterId: chapterIdRef.current, 
              hasContent: !!currentContent,
              elapsed 
            });
            
            if (elapsed > timeout) {
              reject(new Error('绛夊緟"鏂板缓绔犺妭"鎸夐挳鍙敤瓒呮椂锛堝彲鑳借嚜鍔ㄤ繚瀛樺け璐ワ級'));
            } else {
              setTimeout(tryClick, 500);
            }
          }
        } catch (error: any) {
          reject(error);
        }
      };
      
      // 寤惰繜寮€濮嬶紝缁欓〉闈竴鐐规椂闂存覆鏌?
      setTimeout(tryClick, 500);
    });
  };

  // 绛夊緟鏂扮珷鑺傞〉闈㈠噯澶囧ソ锛堟鎷畬鎴愶紝椤甸潰鍒囨崲瀹屾垚锛?
  const waitForNewChapterReady = async (expectedChapterNumber: number) => {
    return new Promise<void>((resolve) => {
      console.log(`waitForNewChapterReady: 绛夊緟绔犺妭鍙峰彉涓?${expectedChapterNumber}`);

      const checkReady = () => {
        const currentChapterNum = chapterNumberRef.current;
        console.log(`妫€鏌ユ柊绔犺妭鐘舵€? loading=${loading}, 褰撳墠绔犺妭鍙?ref)=${currentChapterNum}, 鏈熸湜绔犺妭鍙?${expectedChapterNumber}`);
        
        // 妫€鏌ユ潯浠讹細loading缁撴潫 涓?绔犺妭鍙峰凡鍙樹负鏈熸湜鍊?
        if (!loading && currentChapterNum === expectedChapterNumber) {
          console.log(`鉁?鏂扮珷鑺傞〉闈㈠凡鍑嗗濂斤紝绔犺妭鍙峰凡鏇存柊涓? ${currentChapterNum}`);
          resolve();
          return;
        }
        
        if (batchCancelled) {
          console.log('鎵归噺鍐欎綔琚彇娑?);
          resolve();
          return;
        }
        
        // 缁х画绛夊緟锛堜笉璁剧疆瓒呮椂锛屾鎷篃闇€瑕佹椂闂达級
        setTimeout(checkReady, 500);
      };
      
      // 寤惰繜寮€濮嬫鏌ワ紝纭繚姒傛嫭杩囩▼宸茬粡寮€濮?
      setTimeout(checkReady, 1000);
    });
  };

  // 鍙栨秷鎵归噺鍐欎綔
  const cancelBatchWriting = () => {
    setBatchCancelled(true);
    setBatchWriting(false);
    setBatchModalVisible(false);
    message.info('姝ｅ湪鍙栨秷鎵归噺鐢熸垚...');
  };


  // 鍒ゆ柇褰撳墠绔犺妭鏄惁鏄渶鏂扮珷鑺傦紙鍙湁鏈€鏂扮珷鑺傛墠鑳芥柊寤轰笅涓€绔狅級
  const isCurrentChapterLatest = () => {
    if (!chapterNumber || chapterList.length === 0) {
      return false;
    }
    
    // 鎵惧埌绔犺妭鍒楄〃涓渶澶х殑绔犺妭鍙?
    const maxChapterNumber = Math.max(...chapterList.map(ch => ch.chapterNumber || 0));
    
    // 褰撳墠绔犺妭鍙峰繀椤绘槸鏈€澶х殑
    return chapterNumber === maxChapterNumber;
  };

  // 鍔犺浇绔犺妭鍐呭
  const handleLoadChapter = async (chapter: any) => {
    try {
      setChapterNumber(chapter.chapterNumber);
      setChapterTitle(chapter.title || '');
      setCurrentContent(chapter.content || '');
      setChapterId(String(chapter.id));
      setLastSavedContent(chapter.content || ''); // 鍚屾鏈€鍚庝繚瀛樼殑鍐呭
      message.success('绔犺妭鍐呭宸插姞杞?);
    } catch (error) {
      console.error('鍔犺浇绔犺妭澶辫触:', error);
      message.error('鍔犺浇绔犺妭鍐呭澶辫触');
    }
  };

  // 璇锋眰AI鎸囧
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
      
      // 娣诲姞鍒板巻鍙茶褰?
      const historyItem = {
        timestamp: new Date().toLocaleString(),
        userInput: values.userInput,
        guidance: guidance,
        contentSnapshot: currentContent.substring(Math.max(0, currentContent.length - 200))
      };
      setGuidanceHistory(prev => [historyItem, ...prev]);
      
      setGuidanceDrawerVisible(false);
      guidanceForm.resetFields();
      message.success('AI鎸囧鐢熸垚鎴愬姛锛?);
    } catch (error: any) {
      message.error(error.response?.data?.message || 'AI鎸囧鐢熸垚澶辫触');
    } finally {
      setGuidanceLoading(false);
    }
  };

  // 鍔犺浇鍘嗗彶绔犺妭
  const loadHistoricalChapters = async () => {
    if (!novelId) return;
    
    try {
      setChaptersLoading(true);
      // 鑾峰彇灏忚鐨勬墍鏈夌珷鑺?
      const response = await api.get(`/chapters/novel/${novelId}?summary=true`);
      const chapters = response.data || response || [];
      
      // 鎸夌珷鑺傚彿鎺掑簭
      const sortedChapters = chapters.sort((a: any, b: any) => {
        return (a.chapterNumber || 0) - (b.chapterNumber || 0);
      });
      
      setHistoricalChapters(sortedChapters);
    } catch (error) {
      console.error('鍔犺浇鍘嗗彶绔犺妭澶辫触:', error);
      message.error('鍔犺浇绔犺妭鍒楄〃澶辫触');
    } finally {
      setChaptersLoading(false);
    }
  };

  return (
    <div className="volume-writing-studio" style={{ height: '100vh', display: 'flex', flexDirection: 'column' }}>
      {/* 椤堕儴宸ュ叿鏍?- 绮捐嚧璁捐 */}
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
            杩斿洖
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
                {novelInfo?.title || '鍒涗綔涓?..'}
              </div>
              <div style={{ 
                fontSize: '12px', 
                color: '#94a3b8',
                marginTop: '2px'
              }}>
                绗瑊currentVolume?.volumeNumber || '?'}鍗?路 绗瑊chapterNumber || '?'}绔?
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
            灏忚澶х翰
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
            鍗峰ぇ绾?
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
            鍘嗗彶
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
            AI鎸囧
          </Button>
        </Space>
      </div>

      {/* 椤甸潰鍔犺浇涓?*/}
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
          <div style={{ marginTop: 16, color: '#666', fontSize: '16px' }}>姝ｅ湪鍔犺浇鍐欎綔宸ヤ綔瀹?..</div>
          <div style={{ marginTop: 8, color: '#999', fontSize: '14px' }}>璇风◢鍊欙紝姝ｅ湪鍑嗗鎮ㄧ殑鍒涗綔鐜</div>
        </div>
      )}

      {/* 椤甸潰鍔犺浇澶辫触 */}
      {!pageLoading && pageLoadError && (
        <Card style={{ marginTop: 12 }}>
          <Alert
            type="error"
            showIcon
            message="椤甸潰鍔犺浇澶辫触"
            description={
              <div>
                <p>{pageLoadError}</p>
                <div style={{ marginTop: 12 }}>
                  <Button onClick={() => initializePage()} type="primary" style={{ marginRight: 8 }}>
                    閲嶆柊鍔犺浇
                  </Button>
                  <Button onClick={() => navigate(-1)}>
                    杩斿洖涓婁竴椤?
                  </Button>
                </div>
              </div>
            }
          />
        </Card>
      )}

      {/* 涓讳綋鍐呭鍖?- 宸﹀彸甯冨眬 */}
      {!pageLoading && !pageLoadError && currentVolume && (
        <div style={{ 
          flex: 1, 
          display: 'flex', 
          overflow: 'hidden',
          background: '#f8fafc'
        }}>
          {/* 宸︿晶锛氱珷鑺傚垪琛?*/}
          <div style={{
            width: '300px',
            background: '#ffffff',
            borderRight: '1px solid #e2e8f0',
            display: 'flex',
            flexDirection: 'column',
            flexShrink: 0,
            boxShadow: '1px 0 3px rgba(0, 0, 0, 0.02)'
          }}>
            {/* 绔犺妭鍒楄〃澶撮儴 */}
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
                      message.warning('璇峰厛瀹屾垚褰撳墠绔犺妭鐨勫垱浣?);
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
                  <span>绔犺妭鐩綍</span>
                  {chapterDirHovered && (
                    <span style={{
                      fontSize: '18px',
                      color: '#3b82f6',
                      marginLeft: '4px',
                      animation: 'fadeIn 0.2s ease-in'
                    }}>
                      鉃?
                    </span>
                  )}
                </div>

                {/* 鏂板缓绔犺妭鎸夐挳 */}
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
                  鏂板缓绔犺妭
                </Button>
              </div>
            </div>

            {/* 绔犺妭鍒楄〃鍐呭 */}
            <div style={{ 
              flex: 1, 
              overflowY: 'auto',
              padding: '12px',
              background: '#fafbfc'
            }}>
              {/* 宸叉湁绔犺妭鍒楄〃 */}
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
                      <span>绗瑊chapter.chapterNumber}绔?/span>
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
                      {chapter.title || '鏈懡鍚?}
                    </div>
                    <div style={{
                      fontSize: '10px',
                      color: '#94a3b8'
                    }}>
                      {chapter.wordCount || 0} 瀛?
                    </div>
                  </div>
                ))}
                
                {/* 褰撳墠鍒涗綔涓殑绔犺妭(濡傛灉涓嶅湪鍒楄〃涓? */}
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
                      绗瑊chapterNumber}绔?
                    </div>
                    <div style={{ 
                      fontSize: '11px', 
                      color: '#78350f',
                      marginBottom: '6px'
                    }}>
                      {chapterTitle || '鍒涗綔涓?..'}
                    </div>
                    <div style={{
                      fontSize: '10px',
                      color: '#a16207'
                    }}>
                      {wordCount} 瀛?
                    </div>
                  </div>
                )}
              </Spin>
            </div>
          </div>

          {/* 鍙充晶锛氱紪杈戝櫒 */}
          <div style={{ 
            flex: 1, 
            display: 'flex', 
            flexDirection: 'column',
            overflow: 'hidden'
          }}>
            {/* 缂栬緫鍣ㄥご閮?- 绱у噾涓€琛屽竷灞€ */}
            <div style={{
              padding: '12px 32px',
              background: 'linear-gradient(135deg, #ffffff 0%, #f8fafc 100%)',
              borderBottom: '1px solid #e2e8f0',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: '12px',
              flexShrink: 0,
              boxShadow: '0 1px 3px rgba(0, 0, 0, 0.04)'
            }}>
              {/* 宸︿晶锛氱珷鑺傚彿 + 鏍囬 */}
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flex: 1 }}>
                {/* 绔犺妭鍙?*/}
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '4px',
                  padding: '5px 12px',
                  background: 'linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%)',
                  borderRadius: '8px',
                  border: '2px solid #bfdbfe',
                  boxShadow: '0 2px 6px rgba(59, 130, 246, 0.12)',
                  height: '34px'
                }}>
                  <span style={{ fontSize: '12px', color: '#1e40af', fontWeight: 600 }}>绗?/span>
                  <Input
                    style={{ 
                      width: 45,
                      textAlign: 'center',
                      fontWeight: 700,
                      fontSize: '14px',
                      border: 'none',
                      background: 'transparent',
                      color: '#1e40af',
                      padding: 0,
                      height: '22px'
                    }}
                    value={chapterNumber ?? ''}
                    onChange={(e) => setChapterNumber(Number(e.target.value) || null)}
                    bordered={false}
                  />
                  <span style={{ fontSize: '12px', color: '#1e40af', fontWeight: 600 }}>绔?/span>
                </div>
                
                {/* 绔犺妭鏍囬 */}
                  <Input
                  style={{ 
                    flex: 1,
                    maxWidth: 280,
                    height: '34px',
                    borderRadius: '6px',
                    border: '1px solid #e2e8f0',
                    fontSize: '13px',
                    fontWeight: 500,
                    transition: 'all 0.2s'
                  }}
                    value={chapterTitle}
                    onChange={(e) => setChapterTitle(e.target.value)}
                  placeholder="绔犺妭鏍囬..."
                  onFocus={(e) => {
                    e.currentTarget.style.borderColor = '#3b82f6';
                    e.currentTarget.style.boxShadow = '0 0 0 2px rgba(59, 130, 246, 0.1)';
                  }}
                  onBlur={(e) => {
                    e.currentTarget.style.borderColor = '#e2e8f0';
                    e.currentTarget.style.boxShadow = 'none';
                  }}
                />
                
                {/* AI鍐欎綔鎸夐挳 - 閱掔洰璁捐 */}
                <Button 
                  type="primary" 
                  icon={<ThunderboltOutlined style={{ fontSize: '16px' }} />} 
                  size="large"
                  disabled={loading || isStreaming}
                  loading={loading}
                  onClick={() => {
                    if (!chapterNumber) {
                      message.warning('璇峰厛濉啓绔犺妭缂栧彿');
                      return;
                    }
                    setAiDrawerVisible(true);
                  }}
                  style={{ 
                    borderRadius: '8px',
                    height: '42px',
                    padding: '0 24px',
                    background: 'linear-gradient(135deg, #8b5cf6 0%, #7c3aed 100%)',
                    border: 'none',
                    fontWeight: 700,
                    fontSize: '14px',
                    boxShadow: '0 4px 16px rgba(139, 92, 246, 0.5)',
                    flexShrink: 0,
                    marginLeft: '12px',
                    transition: 'all 0.3s ease',
                    position: 'relative',
                    overflow: 'hidden'
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.transform = 'translateY(-2px)';
                    e.currentTarget.style.boxShadow = '0 6px 20px rgba(139, 92, 246, 0.6)';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.transform = 'translateY(0)';
                    e.currentTarget.style.boxShadow = '0 4px 16px rgba(139, 92, 246, 0.5)';
                  }}
                >
                  <span style={{ 
                    display: 'flex', 
                    alignItems: 'center', 
                    gap: '6px',
                    position: 'relative',
                    zIndex: 1
                  }}>
                    鉁?AI鍐欎綔
                  </span>
                </Button>

                {/* 涓€娆＄敓鎴?0绔犳寜閽?*/}
                <Button 
                  type="default" 
                  icon={<RobotOutlined style={{ fontSize: '16px' }} />} 
                  size="large"
                  disabled={loading || isStreaming || batchWriting}
                  loading={batchWriting}
                  onClick={handleBatchWriting}
                  style={{ 
                    borderRadius: '8px',
                    height: '42px',
                    padding: '0 20px',
                    background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
                    border: 'none',
                    color: 'white',
                    fontWeight: 700,
                    fontSize: '14px',
                    boxShadow: '0 4px 16px rgba(16, 185, 129, 0.4)',
                    flexShrink: 0,
                    marginLeft: '12px',
                    transition: 'all 0.3s ease',
                    position: 'relative',
                    overflow: 'hidden'
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.transform = 'translateY(-2px)';
                    e.currentTarget.style.boxShadow = '0 6px 20px rgba(16, 185, 129, 0.5)';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.transform = 'translateY(0)';
                    e.currentTarget.style.boxShadow = '0 4px 16px rgba(16, 185, 129, 0.4)';
                  }}
                >
                  <span style={{ 
                    display: 'flex', 
                    alignItems: 'center', 
                    gap: '6px',
                    position: 'relative',
                    zIndex: 1
                  }}>
                    馃殌 涓€娆＄敓鎴?0绔?
                  </span>
                </Button>
                
                {/* 鑷姩淇濆瓨鐘舵€佹彁绀?*/}
                {isSaving && (
                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '6px',
                    padding: '5px 12px',
                    background: '#fef3c7',
                    borderRadius: '6px',
                    fontSize: '12px',
                    color: '#92400e',
                    marginLeft: '8px'
                  }}>
                    <SaveOutlined style={{ fontSize: '12px' }} />
                    <span>淇濆瓨涓?..</span>
              </div>
                )}
                
                {!isSaving && lastSavedContent && currentContent === lastSavedContent && (
                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '6px',
                    padding: '5px 12px',
                    background: '#d1fae5',
                    borderRadius: '6px',
                    fontSize: '12px',
                    color: '#065f46',
                    marginLeft: '8px'
                  }}>
                    <CheckOutlined style={{ fontSize: '12px' }} />
                    <span>宸蹭繚瀛?/span>
              </div>
                )}
                
                {/* 瀛楁暟缁熻 - 绱у噾鏄剧ず */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginLeft: '8px' }}>
                  <div style={{ 
                    padding: '5px 10px',
                    background: 'linear-gradient(135deg, #f1f5f9 0%, #e2e8f0 100%)',
                    borderRadius: '5px',
                    border: '1px solid #cbd5e1',
                    fontSize: '11px',
                    color: '#475569',
                    height: '28px',
                    display: 'flex',
                    alignItems: 'center'
                  }}>
                    <Text strong style={{ color: '#1e293b', fontSize: '12px' }}>{wordCount}</Text> 瀛?
              </div>
                  <div style={{ 
                    padding: '5px 10px',
                    background: wordCount >= 3000 
                      ? 'linear-gradient(135deg, #dcfce7 0%, #bbf7d0 100%)' 
                      : 'linear-gradient(135deg, #fef3c7 0%, #fde68a 100%)',
                    borderRadius: '5px',
                    border: `1px solid ${wordCount >= 3000 ? '#86efac' : '#fbbf24'}`,
                    fontSize: '11px',
                    color: wordCount >= 3000 ? '#166534' : '#92400e',
                    fontWeight: 600,
                    height: '28px',
                    display: 'flex',
                    alignItems: 'center'
                  }}>
                    {Math.min(100, Math.round((wordCount / 3000) * 100))}%
                  </div>
                </div>
              </div>
              
            </div>
              
            {/* 缂栬緫鍣ㄤ富浣?*/}
            <div style={{ 
              flex: 1, 
              display: 'flex',
              flexDirection: 'column',
              background: '#ffffff',
              borderRadius: '12px',
              border: '1px solid #e2e8f0',
              overflow: 'hidden',
              margin: '0 32px 32px 32px',
              boxShadow: '0 4px 12px rgba(0, 0, 0, 0.05)'
            }}>
              {/* 鏍煎紡宸ュ叿鏍?*/}
              <div style={{
                padding: '12px 20px',
                background: 'linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%)',
                borderBottom: '1px solid #e2e8f0',
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                flexWrap: 'wrap'
              }}>
                <div style={{ fontSize: '12px', color: '#64748b', marginRight: '12px', fontWeight: '500' }}>
                  馃摑 鏍煎紡宸ュ叿锛?
                </div>
                
                <Button 
                  size="small" 
                  type={currentContent.includes('銆€銆€') ? 'primary' : 'default'}
                  onClick={() => {
                    const textarea = document.getElementById('streaming-textarea') as HTMLTextAreaElement;
                    if (textarea) {
                      const start = textarea.selectionStart;
                      const end = textarea.selectionEnd;
                      const selectedText = currentContent.substring(start, end);
                      
                      if (selectedText) {
                        // 涓洪€変腑鏂囨湰娣诲姞娈佃惤缂╄繘
                        const indentedText = selectedText.split('\n').map(line => 
                          line.trim() ? '銆€銆€' + line.replace(/^銆€銆€/, '') : line
                        ).join('\n');
                        
                        const newContent = currentContent.substring(0, start) + indentedText + currentContent.substring(end);
                        setCurrentContent(newContent);
                      } else {
                        // 鍦ㄥ厜鏍囦綅缃彃鍏ユ钀界缉杩?
                        const newContent = currentContent.substring(0, start) + '銆€銆€' + currentContent.substring(start);
                        setCurrentContent(newContent);
                        // 璁剧疆鍏夋爣浣嶇疆
                        setTimeout(() => {
                          textarea.selectionStart = textarea.selectionEnd = start + 2;
                          textarea.focus();
                        }, 0);
                      }
                    }
                  }}
                  style={{ fontSize: '12px', height: '28px' }}
                >
                  娈佃惤缂╄繘
                </Button>
                
                <Button 
                  size="small" 
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
                  style={{ fontSize: '12px', height: '28px' }}
                >
                  瀵硅瘽寮曞彿
                </Button>
                
                <Button 
                  size="small" 
                  onClick={() => {
                    const textarea = document.getElementById('streaming-textarea') as HTMLTextAreaElement;
                    if (textarea) {
                      const start = textarea.selectionStart;
                      const newContent = currentContent.substring(0, start) + '\n\n銆€銆€' + currentContent.substring(start);
                      setCurrentContent(newContent);
                      setTimeout(() => {
                        textarea.selectionStart = textarea.selectionEnd = start + 3;
                        textarea.focus();
                      }, 0);
                    }
                  }}
                  style={{ fontSize: '12px', height: '28px' }}
                >
                  鏂版钀?
                </Button>
                
                <Button 
                  size="small" 
                  onClick={() => {
                    if (!currentContent || currentContent.trim() === '') {
                      message.warning('璇峰厛杈撳叆鎴栫敓鎴愬唴瀹?);
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
                    message.success('鏍煎紡鍖栧畬鎴?);
                  }}
                  type="primary"
                  style={{ fontSize: '12px', height: '28px', marginLeft: '8px' }}
                >
                  涓€閿牸寮忓寲
                </Button>

                {/* AI娑堢棔鎸夐挳 */}
                <Button 
                  size="small"
                  disabled={!currentContent || isRemovingAITrace || loading || isStreaming}
                  onClick={() => {
                    if (!currentContent || currentContent.trim() === '') {
                      message.warning('璇峰厛杈撳叆鎴栫敓鎴愬唴瀹?);
                      return;
                    }
                    setProcessedContent('');
                    setAiTraceDrawerVisible(true);
                  }}
                  style={{ fontSize: '12px', height: '28px', marginLeft: '8px' }}
                >
                  馃Ч AI娑堢棔
                </Button>
                
                <div style={{ 
                  marginLeft: 'auto', 
                  fontSize: '12px', 
                  color: '#64748b', 
                  display: 'flex', 
                  alignItems: 'center', 
                  gap: '6px',
                  padding: '4px 12px',
                  background: lastSaveTime ? '#f0fdf4' : '#f8fafc',
                  borderRadius: '6px',
                  border: `1px solid ${lastSaveTime ? '#86efac' : '#e2e8f0'}`
                }}>
                  {lastSaveTime ? (
                    <>
                      <CheckOutlined style={{ fontSize: '12px', color: '#10b981' }} />
                      <span style={{ color: '#166534', fontWeight: 500 }}>鏈€鍚庝繚瀛橈細{lastSaveTime}</span>
                    </>
                  ) : (
                    <span style={{ color: '#94a3b8', fontSize: '11px' }}>鏈繚瀛?/span>
                  )}
                </div>
              </div>

              {/* 鏂囨湰缂栬緫鍣?*/}
              <textarea
                ref={textareaRef}
                id="streaming-textarea"
                className={`writing-textarea ${isStreaming ? 'streaming' : ''} ${progressHint && !currentContent ? 'progress-hint' : ''}`}
                value={progressHint && !currentContent ? progressHint : currentContent}
                onChange={(e) => setCurrentContent(e.target.value)}
                placeholder={isStreaming ? "AI姝ｅ湪涓烘偍鍐欎綔涓?.." : "寮€濮嬫偍鐨勫垱浣?.."}
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

            {/* 搴曢儴鐘舵€佹爮 */}
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
                  AI姝ｅ湪娴佸紡鍐欎綔涓?..
                    </Text>
                  </div>
                )}
              </div>
        </div>
      )}

        {/* 闅愯棌鐨凙I鍐欎綔瑙﹀彂鍣?*/}
        <button 
          data-ai-write-trigger
          style={{ display: 'none' }}
          onClick={async () => {
                    if (!novelId || !currentVolume) return;
                    if (!chapterNumber) {
                      message.warning('璇峰～鍐欑珷鑺傜紪鍙?);
                      return;
                    }
                    
                    // 鍏抽棴鎶藉眽
                    setAiDrawerVisible(false);
                    
                    // 璁板繂搴撲笂涓嬫枃浜ょ敱鍚庣鏋勫缓锛堝墠绔笉鍐嶄紶 memoryBank锛?
                    
                    try {
                      setLoading(true);
                      setIsStreaming(true);
                      streamingCompleteRef.current = false; // 閲嶇疆瀹屾垚鏍囧織
                      setCurrentContent(''); // 娓呯┖褰撳墠鍐呭锛屽噯澶囨帴鏀舵祦寮忚緭鍑?
                      setProgressHint('[ 姝ｅ湪杩炴帴AI鏈嶅姟... ]'); // 鍒濆鍖栬繘搴︽彁绀?
                      setLastSavedContent(''); // 娓呯┖宸蹭繚瀛樺唴瀹癸紝纭繚AI鍐欎綔瀹屾垚鍚庤Е鍙戣嚜鍔ㄤ繚瀛?
                      
                      const chapterPlan = {
                        chapterNumber,
                        title: chapterTitle || undefined,
                        type: '鍓ф儏',
                        coreEvent: chapterPlotInput || '鏍规嵁鍗疯鑼冧笌璁″垝鐢熸垚鏈珷鏍稿績浜嬩欢',
                        characterDevelopment: ['鎸夊嵎瑙勮寖鎺ㄨ繘瑙掕壊鍙戝睍'],
                        foreshadowing: '鎸夐渶鍩嬭鎴栧洖鏀?,
                        estimatedWords: 3000,
                        priority: 'high',
                        mood: 'normal'
                      };

                      // 妫€鏌I閰嶇疆
                      if (!checkAIConfig()) {
                        message.error(AI_CONFIG_ERROR_MESSAGE);
                        return;
                      }

                      // 浣跨敤fetch杩涜娴佸紡璇锋眰
                      const token = localStorage.getItem('token');
                      const requestBody = withAIConfig({
                        chapterPlan,
                        userAdjustment: userAdjustment || undefined,
                        model: selectedModel || undefined, // 浼犻€掗€夋嫨鐨勬ā鍨?
                        promptTemplateId: selectedTemplateId || undefined // 浼犻€掗€夋嫨鐨勬彁绀鸿瘝妯℃澘ID
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
                        throw new Error('鏃犳硶鑾峰彇鍝嶅簲娴?);
                      }

                      message.info('寮€濮婣I娴佸紡鍐欎綔...');

                      // 璇诲彇娴佸紡鍝嶅簲
                      let buffer = '';
                      let updatedMemoryBankData = null; // 鍓嶇涓嶅啀鎸佷箙鍖?鍥炰紶缁欏悗绔紝浠呯敤浜庡吋瀹瑰悗绔簨浠?
                      let accumulatedContent = ''; // 鐢ㄤ簬绱Н鍐呭
                      let currentEventType = ''; // 褰撳墠浜嬩欢绫诲瀷

                      console.log('寮€濮嬭鍙栨祦寮忓搷搴?..');

                      try {
                        while (true) {
                          const { done, value } = await reader.read();
                          
                          if (done) {
                            console.log('鉁?娴佽鍙栧畬鎴愶紙SSE杩炴帴鍏抽棴锛?);
                            setLoading(false);
                            setIsStreaming(false);
                            streamingCompleteRef.current = true; // 璁剧疆瀹屾垚鏍囧織
                            message.success('AI鍐欎綔瀹屾垚锛屽凡鏇存柊璁板繂搴撲笌涓€鑷存€?);
                            break;
                          }

                          const chunk = decoder.decode(value, { stream: true });
                          console.log('鎺ユ敹鍒版暟鎹潡:', chunk);
                          buffer += chunk;

                          // 澶勭悊浜嬩欢娴佹牸寮?
                          const lines = buffer.split('\n');
                          buffer = lines.pop() || ''; // 淇濈暀涓嶅畬鏁寸殑琛?

                          for (const line of lines) {
                            console.log('澶勭悊琛?', line);
                            console.log('褰撳墠content闀垮害:', currentContent.length);
                            
                            if (line.startsWith('data:')) {
                              // 澶勭悊 'data:' 鎴?'data: ' 涓ょ鏍煎紡
                              const data = line.startsWith('data: ') ? line.slice(6) : line.slice(5);
                              console.log('馃攳 鎻愬彇鐨勬暟鎹?', data);
                              console.log('馃攳 鏁版嵁闀垮害:', data.length);
                              console.log('馃攳 鏁版嵁绫诲瀷:', typeof data);
                              console.log('馃攳 褰撳墠浜嬩欢绫诲瀷:', currentEventType);
                              
                              if (data === '[DONE]') {
                                console.log('馃攳 妫€娴嬪埌缁撴潫鏍囪');
                                currentEventType = ''; // 閲嶇疆浜嬩欢绫诲瀷
                                continue;
                              }
                              
                              // 濡傛灉鏄痶itle浜嬩欢鐨勬暟鎹紝鐩存帴璁剧疆鏍囬
                              if (currentEventType === 'title') {
                                console.log('鉁?鍚庣鎻愬彇鐨勭珷鑺傛爣棰?', data);
                                setChapterTitle(data);
                                currentEventType = ''; // 閲嶇疆浜嬩欢绫诲瀷
                                continue;
                              }

                              // 鍏堝皾璇曚綔涓哄唴瀹瑰鐞嗭紝鏃犺鏄惁鑳借В鏋怞SON
                              let shouldAddContent = false;
                              let contentToAdd = '';

                              try {
                                const parsed = JSON.parse(data);
                                console.log('馃攳 鎴愬姛瑙ｆ瀽JSON:', parsed);

                                // 鐩存帴鏂囨湰/鏁板瓧/鏁扮粍锛屼綔涓哄唴瀹硅拷鍔狅紙淇鏁板瓧琚睆钄斤級
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
                                  // 澶勭悊杩涘害娑堟伅
                                  if (parsed.message && parsed.step) {
                                    console.log('馃攳 杩涘害娑堟伅:', parsed.message, parsed.step);
                                    if (parsed.step === 'generating_summary') {
                                      message.loading({ content: '姝ｅ湪鐢熸垚绔犺妭姒傛嫭...', key: 'summary' });
                                      setProgressHint('[ 姝ｅ湪鐢熸垚绔犺妭姒傛嫭... ]');
                                    } else if (parsed.step === 'saving_chapter') {
                                      message.loading({ content: '姝ｅ湪淇濆瓨绔犺妭...', key: 'saving' });
                                      setProgressHint('[ 姝ｅ湪淇濆瓨绔犺妭... ]');
                                    } else if (parsed.step === 'updating_memory') {
                                      message.loading({ content: '姝ｅ湪鏇存柊璁板繂搴?..', key: 'memory' });
                                      setProgressHint('[ 姝ｅ湪鏇存柊璁板繂搴?.. ]');
                                    } else if (parsed.step === 'final_coherence_check') {
                                      message.loading({ content: '姝ｅ湪杩涜杩炶疮鎬ф鏌?..', key: 'coherence' });
                                      setProgressHint('[ 姝ｅ湪杩涜杩炶疮鎬ф鏌?.. ]');
                                    } else {
                                      message.loading({ content: parsed.message, key: 'progress' });
                                      setProgressHint(`[ ${parsed.message} ]`);
                                    }
                                    shouldAddContent = false;
                                  } else if (parsed.type === 'content') {
                                    shouldAddContent = true;
                                    contentToAdd = parsed.content || '';
                                  } else if (parsed.type === 'complete') {
                                    if (parsed.updatedMemoryBank) {
                                      updatedMemoryBankData = parsed.updatedMemoryBank;
                                    }
                                    if (parsed.generatedContent) {
                                      shouldAddContent = true;
                                      contentToAdd = parsed.generatedContent || '';
                                    }
                                  } else {
                                    if (parsed.updatedMemoryBank) {
                                      updatedMemoryBankData = parsed.updatedMemoryBank;
                                    }
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
                                console.log('馃攳 JSON瑙ｆ瀽澶辫触锛屼綔涓虹函鏂囨湰澶勭悊');
                                // 璇嗗埆杩涘害娑堟伅骞惰缃彁绀猴紝鍏朵粬閮藉綋浣滃唴瀹?
                                const isProgress = data && /鏋勫缓瀹屾暣涓婁笅鏂噟涓婁笅鏂囨秷鎭瘄寮€濮嬪寮篈I鍐欎綔|鏇存柊璁板繂绠＄悊绯荤粺|璁板繂搴撳凡鏇存柊|璁板繂绯荤粺鏇存柊澶辫触|鍐欎綔瀹屾垚|preparing|writing|context_ready|memory_updated|memory_stats|updating_memory|complete|姝ｅ湪瑁呴厤璁板繂搴搢姝ｅ湪鍒嗘瀽鍓嶇疆绔犺妭|姝ｅ湪杩涜杩炶疮鎬ч妫€鏌姝ｅ湪鏋勫缓AI鍐欎綔鎻愮ず璇峾AI寮€濮嬪垱浣滀腑|姝ｅ湪淇濆瓨绔犺妭|姝ｅ湪鐢熸垚绔犺妭姒傛嫭|姝ｅ湪鏇存柊璁板繂搴搢馃|馃摎|馃攳|鈿馃|馃捑|馃摑/.test(data);
                                if (isProgress) {
                                  // 灏嗚繘搴︽秷鎭樉绀轰负鎻愮ず
                                  setProgressHint(`[ ${data.trim()} ]`);
                                  shouldAddContent = false;
                                } else if (data && data.trim() !== '' &&
                                    !data.includes('寮€濮嬪啓浣滅珷鑺?) &&
                                    !data.includes('鍑嗗鍐欎綔鐜') &&
                                    !data.includes('寮€濮婣I鍐欎綔') &&
                                    !data.includes('姝ｅ湪瑁呴厤') &&
                                    !data.includes('姝ｅ湪鍒嗘瀽') &&
                                    !data.includes('姝ｅ湪鏋勫缓') &&
                                    !data.includes('姝ｅ湪杩涜') &&
                                    !data.includes('姝ｅ湪淇濆瓨') &&
                                    !data.includes('姝ｅ湪鐢熸垚') &&
                                    !data.includes('姝ｅ湪鏇存柊')) {
                                  shouldAddContent = true;
                                  contentToAdd = data;
                                  console.log('馃攳 绾枃鏈唴瀹?', contentToAdd);
                                } else if (data && data.trim() !== '') {
                                  // 鍏朵粬"姝ｅ湪..."绫绘秷鎭篃鏄剧ず涓烘彁绀?
                                  setProgressHint(`[ ${data.trim()} ]`);
                                  shouldAddContent = false;
                                }
                              }

                              // 濡傛灉闇€瑕佹坊鍔犲唴瀹癸紝鐩存帴绱Н鏄剧ず
                              if (shouldAddContent && contentToAdd) {
                                console.log('鉁?鍑嗗娣诲姞鍐呭:', contentToAdd);
                                console.log('鉁?鍐呭闀垮害:', contentToAdd.length);
                                console.log('鉁?鍓?0瀛楃:', contentToAdd.substring(0, 50));

                                // 姝ｆ枃寮€濮嬬敓鎴愶紝娓呴櫎杩涘害鎻愮ず
                                if (progressHint && accumulatedContent.length === 0) {
                                  setProgressHint('');
                                }

                                // 鐩存帴绱Н鍐呭锛屼繚鎸佸師濮嬫牸寮忥紙Markdown锛?
                                accumulatedContent += contentToAdd;
                                
                                // 澶勭悊鏍煎紡鍖栨樉绀猴紙缁熶竴澶嶇敤涓€閿牸寮忓寲閫昏緫锛屽吋瀹硅法鍧楁爣鐐?寮曞彿瑙勫垯锛?
                                const displayContent = formatChineseSentences(accumulatedContent);

                                console.log('鉁?鍗冲皢璁剧疆鍐呭锛岀疮绉暱搴?', displayContent.length);
                                
                                // 绔嬪嵆鏇存柊state锛岃Е鍙慠eact閲嶆柊娓叉煋
                                setCurrentContent(displayContent);
                                
                                console.log('鉁?setCurrentContent宸茶皟鐢?);
                              } else {
                                console.log('馃攳 璺宠繃鍐呭锛宒ata:', data);
                              }
                            } else if (line.startsWith('event:')) {
                              // 澶勭悊 'event:' 鎴?'event: ' 涓ょ鏍煎紡
                              const eventType = line.startsWith('event: ') ? line.slice(7) : line.slice(6);
                              console.log('浜嬩欢绫诲瀷:', eventType);
                              currentEventType = eventType; // 璁板綍褰撳墠浜嬩欢绫诲瀷
                              
                              if (eventType === 'preparing') {
                                message.loading({ content: '姝ｅ湪鍑嗗鍐欎綔鐜...', key: 'preparing' });
                                setProgressHint('[ 姝ｅ湪鍑嗗鍐欎綔鐜... ]');
                              } else if (eventType === 'progress') {
                                // 澶勭悊杩涘害浜嬩欢锛屾樉绀哄脊鍑烘彁绀轰絾涓嶆樉绀哄湪鏂囨湰妗嗕腑
                                console.log('鏀跺埌杩涘害浜嬩欢');
                                // 涓嬩竴琛岀殑data涓寘鍚繘搴︿俊鎭紝鍏堢瓑寰呰В鏋?
                              } else if (eventType === 'writing') {
                                message.destroy('preparing');
                                message.loading({ content: '姝ｅ湪AI鍐欎綔涓?..', key: 'writing' });
                                setProgressHint('[ 姝ｅ湪鐢熸垚绔犺妭鍐呭... ]');
                              } else if (eventType === 'chunk') {
                                // chunk浜嬩欢鐨勬暟鎹湪涓嬩竴琛岀殑data:涓紝杩欓噷鍙槸鏍囪
                                console.log('妫€娴嬪埌chunk浜嬩欢锛岀瓑寰呮暟鎹?..');
                              } else if (eventType === 'title') {
                                // title浜嬩欢锛氬悗绔彁鍙栫殑绔犺妭鏍囬
                                // 鏍囬鍦ㄤ笅涓€琛岀殑data:涓紝鏍囪绛夊緟鎻愬彇
                                console.log('妫€娴嬪埌title浜嬩欢锛岀瓑寰呮爣棰樻暟鎹?..');
                              } else if (eventType === 'complete') {
                                message.destroy('writing');
                                message.destroy('summary');
                                message.destroy('saving');
                                message.destroy('memory');
                                message.destroy('coherence');
                                message.destroy('progress');
                                message.success('绔犺妭鍐欎綔瀹屾垚锛?);
                                streamingCompleteRef.current = true; // 璁剧疆瀹屾垚鏍囧織
                                setProgressHint(''); // 娓呴櫎杩涘害鎻愮ず
                              } else if (eventType === 'error') {
                                throw new Error('鍐欎綔杩囩▼涓嚭鐜伴敊璇?);
                              }
                            }
                          }
                        }

                        // 鍐呭宸插湪娴佸紡杩囩▼涓疄鏃舵洿鏂帮紝鏃犻渶鍐嶆鍚屾
                        console.log('娴佸紡瀹屾垚锛屾渶缁堝唴瀹归暱搴?', currentContent.length);
                        
                        // 澶勭悊瀹屾垚鍚庣殑璁板繂搴撴洿鏂帮紙鏀逛负浠呮洿鏂拌瘎鍒嗗睍绀猴紝涓嶅啀鍐欏洖鏈湴/浼犲弬涓庡悗绔級
                        // 鍓嶇涓嶅啀鏄剧ず涓€鑷存€ц瘎鍒嗭紝瀹屽叏鐢卞悗绔鐞?

                        // 鏍囬宸茬敱鍚庣寮傛鐢熸垚骞堕€氳繃title浜嬩欢鍙戦€侊紝鏃犻渶鍓嶇鑷姩鐢熸垚


                      } catch (streamError: any) {
                        console.error('娴佽鍙栭敊璇?', streamError);
                        setLoading(false);
                        setIsStreaming(false);
                        setProgressHint(''); // 娓呴櫎杩涘害鎻愮ず
                        message.destroy();
                        message.error('娴佸紡璇诲彇澶辫触');
                      } finally {
                        reader.releaseLock();
                      }

                    } catch (e: any) {
                      setLoading(false);
                      setIsStreaming(false);
                      setProgressHint(''); // 娓呴櫎杩涘害鎻愮ず
                      message.error(e?.message || 'AI鍐欎綔澶辫触');
                    }
                  }}
        />

      {/* AI鎸囧寤鸿鎶藉眽 */}
      <Drawer
        title="鉁?AI鍒涗綔寤鸿"
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
              {/* AI寤鸿鍐呭淇濈暀鍘熸湁閫昏緫 */}
            </Space>
          )}
        </Spin>
      </Drawer>

      {/* 鍘嗗彶绔犺妭鏌ョ湅鎶藉眽 */}
      <Drawer
        title={`馃摎 銆?{currentVolume?.title || ''}銆嬪巻鍙茬珷鑺俙}
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
              <Text type="secondary">鏆傛棤鍘嗗彶绔犺妭</Text>
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
                    message.success(`宸插姞杞界${chapter.chapterNumber}绔燻);
                  }}
                >
                  <List.Item.Meta
                    title={
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Badge count={chapter.chapterNumber} style={{ backgroundColor: '#52c41a' }} />
                        <Text strong>{chapter.title || '鏈懡鍚嶇珷鑺?}</Text>
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

      {/* 闅愯棌鐨勫彂甯冪珷鑺傛寜閽紙淇濈暀鍏煎鎬э級*/}
      <button 
        style={{ display: 'none' }}
        onClick={async () => {
                    if (!novelId) { message.warning('缂哄皯灏忚ID'); return; }
                    if (chapterNumber == null) { message.warning('缂哄皯绔犺妭缂栧彿'); return; }

                    try {
                      // 1) 鑻ユ棤绔犺妭ID锛屽厛鍒涘缓绔犺妭
                      let id = chapterId;
                      if (!id) {
                        const createPayload: any = {
                          novelId: parseInt(novelId),
                          title: chapterTitle || `绗?{chapterNumber}绔燻,
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
                        // 鑻ュ凡鏈夌珷鑺侷D锛岀‘淇濅繚瀛樻渶鏂版爣棰樹笌鍐呭
                        await api.put(`/chapters/${id}`, {
                          title: chapterTitle,
                          content: currentContent,
                          chapterNumber: chapterNumber
                        });
                      }

                      if (!id) { message.error('鏃犳硶纭锛氱珷鑺傛湭鍒涘缓鎴愬姛'); return; }

                      // 2) 鍙戝竷绔犺妭骞剁瓑寰匒I鐢熸垚鎽樿锛堝悗绔細鍚屾瑙﹀彂骞朵繚瀛樺埌 chapter_summaries锛?
                      message.loading({ content: '姝ｅ湪鎻愬彇鎽樿骞跺彂甯冩湰绔?..', key: 'publishing' });
                      try {
                        // 妫€鏌I閰嶇疆
                        if (!checkAIConfig()) {
                          message.warning('鏈厤缃瓵I锛屽皢璺宠繃鐢熸垚绔犺妭姒傝');
                        }
                        
                        // 浼犻€扐I閰嶇疆鍒板悗绔?
                        await api.post(`/chapters/${id}/publish`, withAIConfig({}));
                      } finally {
                        message.destroy('publishing');
                      }

                      // 3) 鏍囪鐘舵€佷负宸插畬鎴愶紙鍐椾綑锛屼絾淇濇寔涓庢棫閫昏緫鍏煎锛?
                      await api.put(`/chapters/${id}`, { status: 'COMPLETED' });

                      // 4) 鎺ㄨ繘鍒颁笅涓€绔狅紝娓呯┖杈撳叆锛屽苟鎸佷箙鍖栧綋鍓嶇珷鏁板埌鏈湴
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

                      // 閲嶆柊鍔犺浇绔犺妭鍒楄〃
                      loadChapterList();

                      message.success(`鏈珷宸插彂甯冨苟鐢熸垚鎽樿銆傝繘鍏ョ${nextChapter}绔燻);
                    } catch (e: any) {
                      message.error(e?.message || '纭鏈珷澶辫触');
                    }
                  }}
      />

      {/* 灏忚澶х翰鏌ョ湅/缂栬緫鎶藉眽 */}
      <Drawer
        title="灏忚澶х翰"
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
              AI浼樺寲
                          </Button>
            <Button 
              type="primary"
              onClick={handleSaveNovelOutline}
              loading={outlineLoading}
            >
              淇濆瓨
                          </Button>
                        </Space>
        }
      >
        <div style={{ marginBottom: '16px' }}>
                              <Alert
            message="鍦ㄨ繖閲屾煡鐪嬪拰缂栬緫灏忚鐨勬€讳綋澶х翰"
                                type="info"
            showIcon
            style={{ marginBottom: '16px' }}
          />
          <TextArea
            value={editingOutline}
            onChange={(e) => setEditingOutline(e.target.value)}
            placeholder="杈撳叆鎴栫矘璐村皬璇村ぇ绾?.."
            rows={20}
            style={{
              fontSize: '14px',
              lineHeight: '1.8',
              fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei"'
            }}
                                  />
                                </div>
      </Drawer>

      {/* 鍗峰ぇ绾叉煡鐪?缂栬緫鎶藉眽 */}
      <Drawer
        title={`绗?{currentVolume?.volumeNumber || ''}鍗峰ぇ绾瞏}
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
              AI浼樺寲
                                      </Button>
            <Button 
              type="primary"
              onClick={handleSaveVolumeOutline}
              loading={outlineLoading}
            >
              淇濆瓨
            </Button>
                                        </Space>
                                      }
      >
        <div style={{ marginBottom: '16px' }}>
                              <Alert
            message="鍦ㄨ繖閲屾煡鐪嬪拰缂栬緫褰撳墠鍗风殑璇︾粏澶х翰"
                                type="info"
                                showIcon
            style={{ marginBottom: '16px' }}
          />
          <TextArea
            value={editingVolumeOutline}
            onChange={(e) => setEditingVolumeOutline(e.target.value)}
            placeholder="杈撳叆鎴栫矘璐村嵎澶х翰..."
            rows={20}
            style={{
              fontSize: '14px',
              lineHeight: '1.8',
              fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei"'
            }}
          />
        </div>
      </Drawer>

      {/* AI鎸囧璇锋眰鎶藉眽 */}
      <Drawer
        title="璇锋眰AI鍐欎綔鎸囧"
        open={guidanceDrawerVisible}
        onClose={() => setGuidanceDrawerVisible(false)}
        width={500}
        extra={
          <Button 
            type="primary" 
            loading={guidanceLoading}
            onClick={() => guidanceForm.submit()}
          >
            鑾峰彇鎸囧
          </Button>
        }
      >
        <Alert
          message="AI灏嗗垎鏋愭偍褰撳墠鐨勫啓浣滃唴瀹癸紝骞舵彁渚涗釜鎬у寲鐨勬寚瀵煎缓璁?
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
            label="鎮ㄧ殑闂鎴栭渶姹?
            rules={[{ required: true, message: '璇锋弿杩版偍鐨勯渶姹? }]}
          >
            <TextArea
              rows={4}
              placeholder="渚嬪锛氭垜瑙夊緱杩欐瀵硅瘽鏈変簺骞虫贰锛屽浣曡瀹冩洿鏈夊紶鍔涳紵"
              showCount
              maxLength={500}
            />
          </Form.Item>
        </Form>

        <Divider />

        <div>
          <Title level={5}>褰撳墠鍐欎綔鐘舵€?/Title>
          <Text type="secondary">宸插啓瀛楁暟锛歿wordCount}</Text>
          <br />
          <Text type="secondary">
            鏈€鏂板唴瀹归瑙堬細{currentContent.substring(Math.max(0, currentContent.length - 100))}...
          </Text>
        </div>
      </Drawer>

      {/* 鎸囧鍘嗗彶鎶藉眽 */}
      <Drawer
        title="AI鎸囧鍘嗗彶"
        open={historyDrawerVisible}
        onClose={() => setHistoryDrawerVisible(false)}
        width={600}
      >
        {guidanceHistory.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '40px 0' }}>
            <Text type="secondary">鏆傛棤鎸囧鍘嗗彶</Text>
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
                    <Text strong>闂锛?/Text>{item.userInput}
                  </div>
                  <div style={{ marginBottom: 8 }}>
                    <Text strong>AI寤鸿锛?/Text>
                    {item.guidance.nextFocus && (
                      <Paragraph>{item.guidance.nextFocus}</Paragraph>
                    )}
                  </div>
                  <div>
                    <Text type="secondary" style={{ fontSize: '12px' }}>
                      鍐呭蹇収锛歿item.contentSnapshot}...
                    </Text>
                  </div>
                </Card>
              </List.Item>
            )}
          />
        )}
      </Drawer>

      {/* AI鍐欎綔鎶藉眽 */}
      <Drawer
        title={<span style={{ fontSize: '16px', fontWeight: 600 }}>鉁?AI鏅鸿兘鍐欎綔</span>}
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
            鏈珷鍓ф儏锛堝彲閫夛級
          </Text>
          <TextArea
            placeholder="鎻忚堪鏈珷鐨勬牳蹇冨墽鎯呫€佸啿绐佺偣銆佹儏鎰熷熀璋冪瓑...&#10;&#10;渚嬪锛?#10;- 涓昏鍙戠幇瀹濊棌绾跨储&#10;- 涓庡弽娲鹃娆℃闈氦閿?#10;- 鎻ず閲嶈涓栫晫瑙傝瀹?
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

        {/* 妯″瀷閫夋嫨 */}
        <div style={{ marginBottom: '20px' }}>
          <Text style={{ fontSize: '13px', color: '#64748b', display: 'block', marginBottom: '8px' }}>
            AI妯″瀷锛堝彲閫夛紝鐣欑┖浣跨敤榛樿锛?
          </Text>
          <Input
            placeholder="渚嬪: 闇€瑕佸綋鍓嶉€夋嫨鍘傚晢鐨勬ā鍨?
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

        {/* 鎻愮ず璇嶆ā鏉块€夋嫨 */}
        <div style={{ marginBottom: '20px' }}>
          <Text style={{ fontSize: '13px', color: '#64748b', display: 'block', marginBottom: '8px' }}>
            鎻愮ず璇嶆ā鏉匡紙鍙€夛紝鐣欑┖浣跨敤榛樿锛?
          </Text>
                    <Button 
            size="large"
            block
            onClick={() => setTemplateModalVisible(true)}
            style={{
              textAlign: 'left',
              height: '40px',
              fontSize: '14px',
              borderRadius: '8px',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center'
            }}
          >
            <span>
              {selectedTemplateId ? (
                <>
                  {(() => {
                    const allTemplates = [...publicTemplates, ...favoriteTemplates, ...customTemplates];
                    const selected = allTemplates.find((t: any) => t.id === selectedTemplateId);
                    return selected ? (
                      <>
                        {selected.type === 'official' ? '馃弳 ' : '鉁?'}
                        {selected.name}
                      </>
                    ) : '浣跨敤榛樿鎻愮ず璇?;
                  })()}
                </>
              ) : '浣跨敤榛樿鎻愮ず璇?}
            </span>
            <span style={{ color: '#1890ff' }}>閫夋嫨妯℃澘</span>
          </Button>
          
          {templateIdFromUrl && (
            <Alert
              type="info"
              message="宸蹭粠鎻愮ず璇嶅簱缁戝畾妯℃澘"
              description="鎮ㄤ粠鎻愮ず璇嶅簱閫夋嫨鐨勬ā鏉垮凡鑷姩搴旂敤浜庡綋鍓嶅垱浣?
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
            // 瑙﹀彂涓籄I鍐欎綔鎸夐挳鐨勭偣鍑?
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
          寮€濮嬬敓鎴?
                    </Button>
      </Drawer>

      {/* AI娑堢棔鎶藉眽 */}
      <Drawer
        title={<span style={{ fontSize: '16px', fontWeight: 600 }}>馃Ч AI娑堢棔澶勭悊</span>}
        placement="right"
        width={680}
        open={aiTraceDrawerVisible}
        onClose={() => setAiTraceDrawerVisible(false)}
        styles={{
          body: { padding: '24px', background: '#f8fafc' }
        }}
        extra={
          !isRemovingAITrace && processedContent && (
            <Button 
              type="primary"
              onClick={() => {
                setCurrentContent(processedContent);
                setAiTraceDrawerVisible(false);
                message.success('宸叉浛鎹㈠埌姝ｆ枃');
              }}
              style={{
                background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
                border: 'none',
                fontWeight: 600
              }}
            >
              鉁?鏇挎崲鍒版鏂?
            </Button>
          )
        }
      >
        <div style={{ marginBottom: '20px' }}>
          <div style={{ 
            fontSize: '13px', 
            color: '#64748b', 
            marginBottom: '12px',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center'
          }}>
            <span>馃摑 澶勭悊鍚庣殑鍐呭</span>
            {processedContent && (
              <span style={{ fontSize: '12px', color: '#10b981' }}>
                瀛楁暟: {processedContent.length}
              </span>
            )}
          </div>
                          <div style={{ 
            background: '#fff',
            borderRadius: '8px',
            padding: '16px',
            minHeight: '500px',
            maxHeight: '600px',
            overflowY: 'auto',
            border: '1px solid #e2e8f0',
            fontSize: '14px',
            lineHeight: '1.8',
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
            fontFamily: '"Microsoft YaHei", "PingFang SC", sans-serif',
            position: 'relative'
          }}>
            {processedContent ? (
              <>
                {processedContent}
                {isRemovingAITrace && (
                  <div style={{ 
                    display: 'inline-block', 
                    marginLeft: '4px',
                    animation: 'blink 1s infinite'
                  }}>鈻?/div>
                )}
              </>
            ) : isRemovingAITrace ? (
              <div style={{ textAlign: 'center', padding: '40px 0', color: '#94a3b8' }}>
                <Spin size="large" />
                <div style={{ marginTop: '16px' }}>姝ｅ湪AI娑堢棔澶勭悊涓?..</div>
              </div>
            ) : (
              <div style={{ textAlign: 'center', padding: '40px 0', color: '#94a3b8' }}>
                绛夊緟寮€濮嬪鐞?..
                          </div>
                        )}
                      </div>
        </div>

        <div style={{ marginTop: '20px' }}>
          <Button 
            type="primary"
            size="large"
            block
            loading={isRemovingAITrace}
            disabled={isRemovingAITrace}
            onClick={async () => {
              try {
                // 妫€鏌I閰嶇疆
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
                  throw new Error('鏃犳硶鑾峰彇鍝嶅簲娴?);
                }

                let buffer = '';
                let accumulated = '';
                let currentEvent = '';  // 璁板綍褰撳墠浜嬩欢鍚嶇О
                const progressRegex = /(姝ｅ湪AI娑堢棔澶勭悊涓璡.?\.?\.?|澶勭悊涓璡.?\.?\.?|processing|progress|寮€濮嬪鐞?/i;

                console.log('寮€濮嬭鍙朅I娑堢棔娴佸紡鍝嶅簲...');

                while (true) {
                  const { done, value } = await reader.read();
                  
                  if (done) {
                    console.log('AI娑堢棔娴佽鍙栧畬鎴?);
                    setIsRemovingAITrace(false);
                    message.success('AI娑堢棔瀹屾垚锛?);
                    break;
                  }

                  const chunk = decoder.decode(value, { stream: true });
                  buffer += chunk;

                  const lines = buffer.split('\n');
                  buffer = lines.pop() || '';

                  for (const line of lines) {
                    const trimmedLine = line.trim();
                    
                    // 妫€鏌ユ槸鍚︽槸浜嬩欢绫诲瀷琛?
                    if (trimmedLine.startsWith('event:')) {
                      currentEvent = trimmedLine.slice(6).trim();
                      console.log('浜嬩欢绫诲瀷:', currentEvent);
                      continue;
                    }
                    
                    // 澶勭悊鏁版嵁琛?
                    if (trimmedLine.startsWith('data:')) {
                      const data = trimmedLine.startsWith('data: ') ? trimmedLine.slice(6) : trimmedLine.slice(5);
                      
                      // 蹇界暐start銆乨one銆乪rror绛夋帶鍒朵簨浠剁殑鏁版嵁
                      if (currentEvent === 'start' || currentEvent === 'done' || currentEvent === 'error') {
                        console.log('蹇界暐鎺у埗浜嬩欢鏁版嵁:', currentEvent, data);
                        currentEvent = '';  // 閲嶇疆浜嬩欢绫诲瀷
                        continue;
                      }
                      
                      if (data === '[DONE]' || data.trim() === '[DONE]') {
                        console.log('妫€娴嬪埌缁撴潫鏍囪');
                        continue;
                      }

                      if (data.trim()) {
                        try {
                          const parsed = JSON.parse(data);
                          if (parsed && typeof parsed === 'object') {
                            const piece = parsed.content || parsed.delta || parsed.text || '';
                            if (piece && !progressRegex.test(String(piece))) {
                              accumulated += String(piece);
                              const sanitized = accumulated.replace(/(姝ｅ湪AI娑堢棔澶勭悊涓璡.?\.?\.?|澶勭悊涓璡.?\.?\.?|processing|progress|寮€濮嬪鐞?/gi, '');
                              setProcessedContent(sanitized);
                              console.log('绱Н鍐呭闀垮害:', sanitized.length);
                            }
                          }
                        } catch (err) {
                          // 绾枃鏈唴瀹?
                          if (!progressRegex.test(data)) {
                            accumulated += data;
                            const sanitized = accumulated.replace(/(姝ｅ湪AI娑堢棔澶勭悊涓璡.?\.?\.?|澶勭悊涓璡.?\.?\.?|processing|progress|寮€濮嬪鐞?/gi, '');
                            setProcessedContent(sanitized);
                          }
                        }
                      }
                      
                      // 閲嶇疆浜嬩欢绫诲瀷
                      currentEvent = '';
                    }
                  }
                }
              } catch (e: any) {
                console.error('AI娑堢棔澶辫触:', e);
                setIsRemovingAITrace(false);
                message.error(e?.message || 'AI娑堢棔澶辫触');
              }
            }}
            style={{
              borderRadius: '10px',
              height: '48px',
              background: 'linear-gradient(135deg, #8b5cf6 0%, #7c3aed 100%)',
              border: 'none',
              boxShadow: '0 4px 15px rgba(139, 92, 246, 0.4)',
              fontWeight: 600,
              fontSize: '15px'
            }}
          >
            {isRemovingAITrace ? '澶勭悊涓?..' : '寮€濮婣I娑堢棔'}
          </Button>
        </div>
      </Drawer>

      {/* 鎻愮ず璇嶆ā鏉块€夋嫨寮圭獥 */}
      <Modal
        title="閫夋嫨鎻愮ず璇嶆ā鏉?
        open={templateModalVisible}
        onCancel={() => setTemplateModalVisible(false)}
        width={900}
        footer={[
          <Button key="clear" onClick={() => {
            setSelectedTemplateId(null);
            setTemplateModalVisible(false);
            message.success('宸插垏鎹㈠埌榛樿鎻愮ず璇?);
          }}>
            浣跨敤榛樿
          </Button>,
          <Button key="close" type="primary" onClick={() => setTemplateModalVisible(false)}>
            纭畾
          </Button>
        ]}
      >
        <Tabs 
          activeKey={templateModalTab} 
          onChange={setTemplateModalTab}
          items={[
            {
              key: 'public',
              label: <span><GlobalOutlined /> 鍏紑妯℃澘</span>,
              children: (
                <div style={{ maxHeight: '500px', overflowY: 'auto', padding: '16px 0' }}>
                  {publicTemplates.length === 0 ? (
                    <Empty description="鏆傛棤鍏紑妯℃澘" />
                  ) : (
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '16px' }}>
                      {publicTemplates.map((template: any) => (
                        <div
                          key={template.id}
                          onClick={() => {
                            setSelectedTemplateId(template.id);
                            message.success(`宸查€夋嫨: ${template.name}`);
                          }}
                          style={{
                            padding: '16px',
                            border: selectedTemplateId === template.id ? '2px solid #1890ff' : '1px solid #e2e8f0',
                            borderRadius: '8px',
                            cursor: 'pointer',
                            background: selectedTemplateId === template.id ? '#e6f7ff' : 'white',
                            transition: 'all 0.3s ease'
                          }}
                        >
                          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                            <div style={{ fontWeight: 600, fontSize: '15px' }}>
                              馃弳 {template.name}
                            </div>
                            <div style={{ fontSize: '12px', color: '#94a3b8' }}>
                              {template.usageCount} 娆′娇鐢?
                            </div>
                          </div>
                          <div style={{ fontSize: '13px', color: '#64748b', lineHeight: '1.6' }}>
                            {template.description || '鏆傛棤鎻忚堪'}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )
            },
            {
              key: 'favorites',
              label: <span><HeartOutlined /> 鎴戠殑鏀惰棌</span>,
              children: (
                <div style={{ maxHeight: '500px', overflowY: 'auto', padding: '16px 0' }}>
                  {favoriteTemplates.length === 0 ? (
                    <Empty description="杩樻病鏈夋敹钘忎换浣曟ā鏉? />
                  ) : (
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '16px' }}>
                      {favoriteTemplates.map((template: any) => (
                        <div
                          key={template.id}
                          onClick={() => {
                            setSelectedTemplateId(template.id);
                            message.success(`宸查€夋嫨: ${template.name}`);
                          }}
                          style={{
                            padding: '16px',
                            border: selectedTemplateId === template.id ? '2px solid #1890ff' : '1px solid #e2e8f0',
                            borderRadius: '8px',
                            cursor: 'pointer',
                            background: selectedTemplateId === template.id ? '#e6f7ff' : 'white',
                            transition: 'all 0.3s ease'
                          }}
                        >
                          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                            <div style={{ fontWeight: 600, fontSize: '15px' }}>
                              {template.type === 'official' ? '馃弳' : '鉁?} {template.name}
                            </div>
                            <div style={{ fontSize: '12px', color: '#94a3b8' }}>
                              {template.usageCount} 娆′娇鐢?
                            </div>
                          </div>
                          <div style={{ fontSize: '13px', color: '#64748b', lineHeight: '1.6' }}>
                            {template.description || '鏆傛棤鎻忚堪'}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )
            },
            {
              key: 'custom',
              label: <span><FileTextOutlined /> 鑷畾涔夋ā鏉?/span>,
              children: (
                <div style={{ maxHeight: '500px', overflowY: 'auto', padding: '16px 0' }}>
                  {customTemplates.length === 0 ? (
                    <Empty description="杩樻病鏈夊垱寤鸿嚜瀹氫箟妯℃澘">
                      <Button type="primary" onClick={() => {
                        navigate('/prompt-library');
                      }}>
                        鍘诲垱寤烘ā鏉?
                      </Button>
                    </Empty>
                  ) : (
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '16px' }}>
                      {customTemplates.map((template: any) => (
                        <div
                          key={template.id}
                          onClick={() => {
                            setSelectedTemplateId(template.id);
                            message.success(`宸查€夋嫨: ${template.name}`);
                          }}
                          style={{
                            padding: '16px',
                            border: selectedTemplateId === template.id ? '2px solid #1890ff' : '1px solid #e2e8f0',
                            borderRadius: '8px',
                            cursor: 'pointer',
                            background: selectedTemplateId === template.id ? '#e6f7ff' : 'white',
                            transition: 'all 0.3s ease'
                          }}
                        >
                          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                            <div style={{ fontWeight: 600, fontSize: '15px' }}>
                              鉁?{template.name}
                            </div>
                            <div style={{ fontSize: '12px', color: '#94a3b8' }}>
                              {template.usageCount} 娆′娇鐢?
                            </div>
                          </div>
                          <div style={{ fontSize: '13px', color: '#64748b', lineHeight: '1.6' }}>
                            {template.description || '鏆傛棤鎻忚堪'}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )
            }
          ]}
        />
      </Modal>

      {/* 鎵归噺鍐欎綔杩涘害寮圭獥 */}
      <Modal
        title="鎵归噺鐢熸垚杩涘害"
        open={batchModalVisible}
        footer={[
          <Button key="cancel" danger onClick={cancelBatchWriting}>
            鍙栨秷鐢熸垚
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
            姝ｅ湪鐢熸垚绗?{batchProgress.current} / {batchProgress.total} 绔?
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
            {batchProgress.current === 0 ? '鍑嗗寮€濮?..' : 
             batchProgress.current === batchProgress.total ? '鍗冲皢瀹屾垚...' :
             `姝ｅ湪鐢熸垚绗?{batchProgress.current}绔犲唴瀹?..`}
          </div>
          <div style={{ marginTop: '16px', fontSize: '12px', color: '#999' }}>
            馃挕 姝よ繃绋嬪彲鑳介渶瑕佸嚑鍒嗛挓鏃堕棿锛岃鑰愬績绛夊緟
          </div>
        </div>
      </Modal>
    </div>
  );
};

export default VolumeWritingStudio;
