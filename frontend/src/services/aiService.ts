/**
 * AI服务封装
 * 提供统一的AI接口调用，自动附加AI配置
 */

import api from './api';
import { withAIConfig, checkAIConfig } from '../utils/aiRequest';

class AIService {
  /**
   * 检查AI配置是否有效
   */
  checkConfig(): boolean {
    return checkAIConfig();
  }

  /**
   * AI消痕 - 流式
   * 注意：此方法返回Response对象，需要调用者自行处理流式数据
   */
  async removeTraceStream(content: string): Promise<Response> {
    const token = localStorage.getItem('token');
    const requestBody = withAIConfig({ content });
    
    return fetch('/api/ai/remove-trace-stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
        'Cache-Control': 'no-cache',
        ...(token ? { 'Authorization': `Bearer ${token}` } : {})
      },
      body: JSON.stringify(requestBody)
    });
  }

  /**
   * AI消痕 - 非流式
   */
  async removeTrace(content: string): Promise<any> {
    const requestBody = withAIConfig({ content });
    return api.post('/ai/remove-trace', requestBody);
  }

  async polishSelection(params: {
    fullContent: string
    selection: string
    instructions?: string
    chapterTitle?: string
  }): Promise<any> {
    const { fullContent, selection, instructions, chapterTitle } = params;
    const requestBody = withAIConfig({
      chapterContent: fullContent,
      selection,
      instructions,
      chapterTitle,
    });
    return api.post('/ai/polish-selection', requestBody);
  }

  /**
   * 生成卷大纲（如果需要AI）
   */
  async generateVolumeOutline(volumeId: string, userAdvice?: string): Promise<any> {
    const body = userAdvice && userAdvice.trim() ? { userAdvice } : {};
    const requestBody = withAIConfig(body);
    return api.post(`/volumes/${volumeId}/generate-outline`, requestBody);
  }

  /**
   * 生成下一步写作指导（如果需要AI）
   */
  async generateNextStepGuidance(volumeId: string, currentContent: string, userInput: string): Promise<any> {
    const requestBody = withAIConfig({
      currentContent,
      userInput
    });
    return api.post(`/volumes/${volumeId}/next-guidance`, requestBody);
  }

  /**
   * AI写作 - 流式
   * 注意：此方法返回Response对象，需要调用者自行处理流式数据
   */
  async writeChapterStream(
    novelId: string,
    chapterPlan: any,
    userAdjustment?: string,
    model?: string,
    promptTemplateId?: string
  ): Promise<Response> {
    const token = localStorage.getItem('token');
    const requestBody = withAIConfig({
      chapterPlan,
      userAdjustment: userAdjustment || undefined,
      model: model || undefined,
      promptTemplateId: promptTemplateId || undefined
    });
    
    return fetch(`/api/novel-craft/${novelId}/write-chapter-stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
        'Cache-Control': 'no-cache',
        ...(token ? { 'Authorization': `Bearer ${token}` } : {})
      },
      body: JSON.stringify(requestBody)
    });
  }

  /**
   * 形容词挖掘
   */
  async mineAdjectives(loops: number = 100, batchSize: number = 100, category?: string): Promise<any> {
    const requestBody = withAIConfig({
      loops,
      batchSize,
      category
    });
    return api.post('/ai-adjectives/mine', requestBody);
  }
}

export const aiService = new AIService();
export default aiService;
