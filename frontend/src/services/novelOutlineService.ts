import api from './api';

export interface NovelOutline {
  id?: number;
  novelId: string;
  outline: string;
  status?: 'DRAFT' | 'CONFIRMED' | 'REVISED' | 'REVISING';
  plotStructure?: string;
  basicIdea?: string;
  targetWordCount?: number;
  targetChapterCount?: number;
  coreTheme?: string;
  mainCharacters?: string;
  worldSetting?: string;
  keyElements?: string;
  conflictTypes?: string;
}

export interface OutlineGenerationRequest {
  novelId: string;
  basicIdea: string;
  targetWordCount: number;
  targetChapterCount: number;
}

export interface OutlineRevisionRequest {
  feedback: string;
}

class NovelOutlineService {
  
  /**
   * 生成大纲
   */
  async generateOutline(request: OutlineGenerationRequest): Promise<NovelOutline> {
    try {
      const response = await api.post('/outline/generate-stream', request) as any;
      // 根据后端 API 返回的数据结构调整
      return {
        novelId: request.novelId,
        outline: response.plotStructure || response.outline || ''
      };
    } catch (error: any) {
      throw new Error(error.response?.data?.message || error.message || '生成大纲失败');
    }
  }

  /**
   * 更新大纲
   */
  async updateOutline(novelId: string, outline: string): Promise<void> {
    try {
      await api.put(`/outline/novel/${novelId}`, { outline });
      // 如果没有抛出异常，则认为成功
    } catch (error: any) {
      throw new Error(error.response?.data?.message || error.message || '更新大纲失败');
    }
  }

  /**
   * 修改大纲
   */
  async reviseOutline(outlineId: number, request: OutlineRevisionRequest): Promise<NovelOutline> {
    const response = await api.put(`/outline/${outlineId}/revise`, request);
    return response as unknown as NovelOutline;
  }

  /**
   * 确认大纲
   */
  async confirmOutline(outlineId: number): Promise<NovelOutline> {
    // 新流程不再使用 outlineId 确认，前端应直接读取 novels.outline
    throw new Error('confirmOutline 已废弃，请使用 novels.outline');
  }

  /**
   * 根据小说ID获取大纲
   */
  async getOutlineByNovelId(novelId: string): Promise<NovelOutline | null> {
    try {
      console.log('[getOutlineByNovelId] 开始获取大纲，novelId:', novelId);
      
      // 优先从 novel_outlines 表获取完整的大纲对象（包含status）
      const response = await api.get(`/outline/novel/${novelId}`) as any;
      console.log('[getOutlineByNovelId] novel_outlines 表返回:', response);
      
      // 如果返回的是完整的 NovelOutline 对象
      if (response && response.plotStructure && response.plotStructure.trim()) {
        console.log('[getOutlineByNovelId] 使用 novel_outlines 表数据，status:', response.status);
        return {
          novelId: String(response.novelId || novelId),
          outline: response.plotStructure,
          status: response.status || 'DRAFT',
          plotStructure: response.plotStructure
        };
      }
      
      console.log('[getOutlineByNovelId] novel_outlines 表无有效数据，尝试 novels 表');
      
      // 如果 novel_outlines 表没有有效数据，回退到旧的API（从 novels 表获取）
      const fallbackResponse = await api.get(`/novels/${novelId}/outline`) as any;
      console.log('[getOutlineByNovelId] novels 表返回:', fallbackResponse);
      
      const outlineText = typeof fallbackResponse?.outline === 'string' ? fallbackResponse.outline.trim() : '';
      if (!outlineText) {
        console.log('[getOutlineByNovelId] novels 表也无数据，返回 null');
        return null;
      }
      
      console.log('[getOutlineByNovelId] 使用 novels 表数据');
      return { 
        novelId: String(novelId), 
        outline: outlineText,
        plotStructure: outlineText,
        status: 'DRAFT' // 旧数据默认为草稿状态
      };
    } catch (error: any) {
      console.error('[getOutlineByNovelId] 获取大纲失败:', error);
      
      if (error.response?.status === 404) {
        // 如果 novel_outlines 表没有数据，尝试从 novels 表获取
        try {
          console.log('[getOutlineByNovelId] 404错误，尝试 novels 表');
          const fallbackResponse = await api.get(`/novels/${novelId}/outline`) as any;
          const outlineText = typeof fallbackResponse?.outline === 'string' ? fallbackResponse.outline.trim() : '';
          if (!outlineText) {
            return null;
          }
          return { 
            novelId: String(novelId), 
            outline: outlineText,
            plotStructure: outlineText,
            status: 'DRAFT'
          };
        } catch (fallbackError) {
          console.error('[getOutlineByNovelId] novels 表也获取失败:', fallbackError);
          return null;
        }
      }
      throw error;
    }
  }

  /**
   * 根据ID获取大纲详情
   */
  async getOutlineById(outlineId: number): Promise<NovelOutline | null> {
    throw new Error('getOutlineById 已废弃，请使用 getOutlineByNovelId');
  }
}

export const novelOutlineService = new NovelOutlineService();
export default novelOutlineService;