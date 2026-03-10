/**
 * AI服务封装
 * 提供统一的AI接口调用，自动附加AI配置
 */

import api from './api';
import { withAIConfig } from '../utils/aiRequest';

class AIService {

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
   * AI纠错
   */
  async proofread(params: {
    content: string
    characterNames?: string[]
  }): Promise<any> {
    const requestBody = withAIConfig({
      content: params.content,
      characterNames: params.characterNames || []
    });
    return api.post('/ai/proofread', requestBody);
  }
}

export const aiService = new AIService();
export default aiService;
