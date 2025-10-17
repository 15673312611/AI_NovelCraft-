import api from './api';

export interface NovelVolume {
  id: string;
  novelId: string;
  title: string;
  theme: string;
  description: string;
  contentOutline: string;
  volumeNumber: number;
  chapterStart: number;
  chapterEnd: number;
  estimatedWordCount: number;
  actualWordCount: number;
  keyEvents: string;
  characterDevelopment: string;
  plotThreads: string;
  status: 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'REVISED';
  isAiGenerated: boolean;
  lastModifiedByAi: string;
  createdAt: string;
  updatedAt: string;
}

export interface VolumeGenerationRequest {
  basicIdea: string;
  volumeCount: number;
}

export interface VolumeUpdateRequest {
  title: string;
  theme: string;
  description: string;
  contentOutline: string;
}

export interface VolumeGuidanceRequest {
  currentContent: string;
  userInput: string;
}

export interface VolumeWordCountRequest {
  actualWordCount: number;
}

class NovelVolumeService {

  private unwrapResponse<T>(res: any): T {
    console.log('NovelVolumeService - unwrapResponse 输入:', res);
    
    // 检查是否是后端 Result 结构
    if (res && typeof res === 'object' && 'code' in res && 'data' in res) {
      console.log('NovelVolumeService - 检测到 Result 结构，提取 data:', res.data);
      return res.data as T;
    }
    
    // 如果不是 Result 结构，直接返回
    console.log('NovelVolumeService - 未检测到 Result 结构，直接返回:', res);
    return res as T;
  }

  /**
   * 为小说生成卷规划
   */
  async generateVolumePlans(novelId: string, request: VolumeGenerationRequest): Promise<NovelVolume[]> {
    const res = await api.post(`/volumes/${novelId}/generate-plans`, request);
    return this.unwrapResponse<NovelVolume[]>(res);
  }

  /**
   * 为指定卷生成详细大纲
   */
  async generateVolumeOutline(volumeId: string, userAdvice?: string): Promise<any> {
    const body = userAdvice && userAdvice.trim() ? { userAdvice } : {}
    const res = await api.post(`/volumes/${volumeId}/generate-outline`, body);
    return this.unwrapResponse<any>(res);
  }

  /**
   * 开始卷写作会话
   */
  async startVolumeWriting(volumeId: string): Promise<any> {
    const res = await api.post(`/volumes/${volumeId}/start-writing`);
    return this.unwrapResponse<any>(res);
  }

  /**
   * 生成下一步写作指导
   */
  async generateNextStepGuidance(volumeId: string, request: VolumeGuidanceRequest): Promise<any> {
    const res = await api.post(`/volumes/${volumeId}/next-guidance`, request);
    return this.unwrapResponse<any>(res);
  }

  /**
   * 获取小说的所有卷
   */
  async getVolumesByNovelId(novelId: string): Promise<NovelVolume[]> {
    const res = await api.get(`/volumes/novel/${novelId}`);
    return this.unwrapResponse<NovelVolume[]>(res);
  }

  /**
   * 获取卷详情
   */
  async getVolumeDetail(volumeId: string): Promise<any> {
    const res = await api.get(`/volumes/detail/${volumeId}`);
    return this.unwrapResponse<any>(res);
  }

  /**
   * 更新卷的实际字数
   */
  async updateActualWordCount(volumeId: string, request: VolumeWordCountRequest): Promise<void> {
    await api.put(`/volumes/${volumeId}/word-count`, request);
  }

  /**
   * 删除卷
   */
  async deleteVolume(volumeId: string): Promise<void> {
    await api.delete(`/volumes/${volumeId}`);
  }

  /**
   * 获取小说卷统计信息
   */
  async getVolumeStats(novelId: string): Promise<any> {
    const res = await api.get(`/volumes/${novelId}/stats`);
    return this.unwrapResponse<any>(res);
  }

  /**
   * 计算卷的进度
   */
  getVolumeProgress(volume: NovelVolume): number {
    if (!volume.estimatedWordCount || volume.estimatedWordCount === 0) {
      return 0;
    }
    return Math.round((volume.actualWordCount / volume.estimatedWordCount) * 100);
  }

  /**
   * 获取卷的章节数量
   */
  getChapterCount(volume: NovelVolume): number {
    if (!volume.chapterStart || !volume.chapterEnd) {
      return 0;
    }
    return volume.chapterEnd - volume.chapterStart + 1;
  }

  /**
   * 获取状态显示文本
   */
  getStatusText(status: NovelVolume['status']): string {
    const statusMap = {
      'PLANNED': '规划中',
      'IN_PROGRESS': '进行中',
      'COMPLETED': '已完成',
      'REVISED': '已修订'
    };
    return statusMap[status] || status;
  }

  /**
   * 获取状态颜色
   */
  getStatusColor(status: NovelVolume['status']): string {
    const colorMap = {
      'PLANNED': 'orange',
      'IN_PROGRESS': 'blue',
      'COMPLETED': 'green',
      'REVISED': 'purple'
    };
    return colorMap[status] || 'default';
  }
}

export const novelVolumeService = new NovelVolumeService();
export default novelVolumeService;