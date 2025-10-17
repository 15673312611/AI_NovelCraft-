/**
 * AI配置存储工具
 * 使用localStorage存储AI配置
 */

import { AIConfig, getDefaultAIConfig } from '../types/aiConfig';

const AI_CONFIG_KEY = 'novel-ai-config';

/**
 * 保存AI配置到localStorage
 */
export const saveAIConfig = (config: AIConfig): void => {
  try {
    localStorage.setItem(AI_CONFIG_KEY, JSON.stringify(config));
  } catch (error) {
    console.error('保存AI配置失败:', error);
  }
};

/**
 * 从localStorage读取AI配置
 */
export const loadAIConfig = (): AIConfig => {
  try {
    const stored = localStorage.getItem(AI_CONFIG_KEY);
    if (stored) {
      const config = JSON.parse(stored);
      // 验证配置的有效性
      if (config.provider && config.apiKey && config.model) {
        return config;
      }
    }
  } catch (error) {
    console.error('读取AI配置失败:', error);
  }
  return getDefaultAIConfig();
};

/**
 * 清除AI配置
 */
export const clearAIConfig = (): void => {
  try {
    localStorage.removeItem(AI_CONFIG_KEY);
  } catch (error) {
    console.error('清除AI配置失败:', error);
  }
};

/**
 * 检查AI配置是否有效
 */
export const isAIConfigValid = (config: AIConfig): boolean => {
  return !!(
    config &&
    config.provider &&
    config.apiKey &&
    config.apiKey.trim() !== '' &&
    config.model &&
    config.model.trim() !== ''
  );
};
