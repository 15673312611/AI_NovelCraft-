/**
 * AI请求工具
 * 在请求体中自动附加AI配置
 */

import { loadAIConfig, isAIConfigValid } from './aiConfigStorage';
import { AIConfig } from '../types/aiConfig';

/**
 * 获取当前AI配置，如果无效则抛出错误
 */
export const getAIConfigOrThrow = (): AIConfig => {
  const config = loadAIConfig();
  if (!isAIConfigValid(config)) {
    throw new Error('请先在设置页面配置AI服务');
  }
  return config;
};

/**
 * 为请求体添加AI配置
 * @param body 原始请求体
 * @returns 添加了aiConfig字段的请求体
 */
export const withAIConfig = (body: any = {}): any => {
  const aiConfig = getAIConfigOrThrow();
  return {
    ...body,
    aiConfig: {
      provider: aiConfig.provider,
      apiKey: aiConfig.apiKey,
      model: aiConfig.model,
      baseUrl: aiConfig.baseUrl
    }
  };
};

/**
 * 检查AI配置是否已设置
 */
export const checkAIConfig = (): boolean => {
  const config = loadAIConfig();
  return isAIConfigValid(config);
};

/**
 * AI配置相关的错误提示
 */
export const AI_CONFIG_ERROR_MESSAGE = '请先在设置页面配置AI服务（DeepSeek、通义千问或Kimi）';
