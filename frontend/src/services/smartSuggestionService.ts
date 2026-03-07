import api from './api';
import { withAIConfig } from '../utils/aiRequest';

/**
 * 智能建议类型
 */
export type SuggestionType = 'grammar' | 'logic' | 'redundant' | 'improvement' | 'inconsistency' | 'style';

/**
 * 操作类型
 */
export type ActionType = 'replace' | 'delete' | 'insert';

/**
 * 严重程度
 */
export type SeverityType = 'high' | 'medium' | 'low';

/**
 * 智能建议项
 */
export interface SmartSuggestion {
  type: SuggestionType;
  action: ActionType;
  original: string;
  suggested: string | null;
  position: number;
  length: number;
  context: string;
  reason: string;
  severity: SeverityType;
}

/**
 * 智能建议响应
 */
export interface SmartSuggestionsResponse {
  suggestions: SmartSuggestion[];
  total: number;
}

/**
 * 智能建议服务
 */
class SmartSuggestionService {
  /**
   * 获取智能建议
   * @param content 待诊断的内容
   * @returns 建议列表
   */
  async getSmartSuggestions(content: string): Promise<SmartSuggestionsResponse> {
    const requestBody = withAIConfig({ content });
    // 智能建议需要更长的处理时间，设置120秒超时（2分钟）
    const response = await api.post('/ai/smart-suggestions', requestBody, {
      timeout: 120000  // 120秒 = 2分钟
    });
    return response as unknown as SmartSuggestionsResponse;
  }
}

export const smartSuggestionService = new SmartSuggestionService();
export default smartSuggestionService;
