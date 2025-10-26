/**
 * AI请求工具
 * 在请求体中自动附加AI配置
 */

import { loadAIConfig, isAIConfigValid, getActiveCustomConfig } from './aiConfigStorage';
import { AIConfig } from '../types/aiConfig';

/**
 * 获取当前AI配置，如果无效则抛出错误
 * 优先使用自定义API配置，其次使用预设的AI服务配置
 */
export const getAIConfigOrThrow = (): AIConfig => {
  // 1. 优先检查自定义API配置
  const customConfig = getActiveCustomConfig();
  if (customConfig) {
    return {
      provider: 'custom',
      apiKey: customConfig.apiKey,
      model: customConfig.model,
      baseUrl: customConfig.baseUrl
    };
  }

  // 2. 使用传统的AI服务配置
  const config = loadAIConfig();
  if (!isAIConfigValid(config)) {
    throw new Error('请先在设置页面配置AI服务');
  }
  return config;
};

/**
 * 为请求体添加AI配置（扁平结构）
 * @param body 原始请求体
 * @returns 添加了AI配置字段的请求体
 */
export const withAIConfig = (body: any = {}): any => {
  const aiConfig = getAIConfigOrThrow();
  return {
    ...body,
    provider: aiConfig.provider,
    apiKey: aiConfig.apiKey,
    model: aiConfig.model,
    baseUrl: aiConfig.baseUrl
  };
};

/**
 * 检查AI配置是否已设置
 * 检查自定义配置或传统配置
 */
export const checkAIConfig = (): boolean => {
  // 检查是否有激活的自定义配置
  const customConfig = getActiveCustomConfig();
  if (customConfig) {
    return true;
  }
  
  // 检查传统配置
  const config = loadAIConfig();
  return isAIConfigValid(config);
};

/**
 * AI配置相关的错误提示
 */
export const AI_CONFIG_ERROR_MESSAGE = '请先在设置页面配置AI服务（DeepSeek、通义千问、Kimi或自定义API）';

/**
 * 获取当前使用的配置名称（用于显示）
 */
export const getCurrentConfigName = (): string => {
  const customConfig = getActiveCustomConfig();
  if (customConfig) {
    return `自定义: ${customConfig.name}`;
  }
  
  const config = loadAIConfig();
  if (isAIConfigValid(config)) {
    const providerNames: Record<string, string> = {
      'deepseek': 'DeepSeek',
      'qwen': '通义千问',
      'kimi': 'Kimi',
      'custom': '自定义'
    };
    return providerNames[config.provider] || config.provider;
  }
  
  return '未配置';
};
