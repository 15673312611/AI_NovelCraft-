/**
 * AI配置存储工具
 * 使用localStorage存储AI配置
 */

import { AIConfig, getDefaultAIConfig, CustomAPIConfig } from '../types/aiConfig';

const AI_CONFIG_KEY = 'novel-ai-config';
const CUSTOM_API_CONFIGS_KEY = 'novel-custom-api-configs';
const ACTIVE_CUSTOM_CONFIG_KEY = 'novel-active-custom-config';

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

// ==================== 自定义API配置管理 ====================

/**
 * 获取所有自定义API配置
 */
export const getCustomAPIConfigs = (): CustomAPIConfig[] => {
  try {
    const stored = localStorage.getItem(CUSTOM_API_CONFIGS_KEY);
    if (stored) {
      return JSON.parse(stored);
    }
  } catch (error) {
    console.error('读取自定义API配置失败:', error);
  }
  return [];
};

/**
 * 保存自定义API配置列表
 */
const saveCustomAPIConfigs = (configs: CustomAPIConfig[]): void => {
  try {
    localStorage.setItem(CUSTOM_API_CONFIGS_KEY, JSON.stringify(configs));
  } catch (error) {
    console.error('保存自定义API配置失败:', error);
  }
};

/**
 * 添加自定义API配置
 */
export const addCustomAPIConfig = (config: Omit<CustomAPIConfig, 'id' | 'createdAt' | 'updatedAt'>): CustomAPIConfig => {
  const configs = getCustomAPIConfigs();
  const now = Date.now();
  const newConfig: CustomAPIConfig = {
    ...config,
    id: `custom_${now}_${Math.random().toString(36).substr(2, 9)}`,
    createdAt: now,
    updatedAt: now
  };
  configs.push(newConfig);
  saveCustomAPIConfigs(configs);
  return newConfig;
};

/**
 * 更新自定义API配置
 */
export const updateCustomAPIConfig = (id: string, updates: Partial<Omit<CustomAPIConfig, 'id' | 'createdAt'>>): boolean => {
  const configs = getCustomAPIConfigs();
  const index = configs.findIndex(c => c.id === id);
  if (index === -1) {
    return false;
  }
  configs[index] = {
    ...configs[index],
    ...updates,
    updatedAt: Date.now()
  };
  saveCustomAPIConfigs(configs);
  return true;
};

/**
 * 删除自定义API配置
 */
export const deleteCustomAPIConfig = (id: string): boolean => {
  const configs = getCustomAPIConfigs();
  const filtered = configs.filter(c => c.id !== id);
  if (filtered.length === configs.length) {
    return false;
  }
  saveCustomAPIConfigs(filtered);
  
  // 如果删除的是当前激活的配置，清除激活状态
  const activeId = getActiveCustomConfigId();
  if (activeId === id) {
    clearActiveCustomConfigId();
  }
  return true;
};

/**
 * 根据ID获取配置
 */
export const getCustomAPIConfigById = (id: string): CustomAPIConfig | null => {
  const configs = getCustomAPIConfigs();
  return configs.find(c => c.id === id) || null;
};

/**
 * 设置当前激活的自定义配置ID
 */
export const setActiveCustomConfigId = (id: string): void => {
  try {
    localStorage.setItem(ACTIVE_CUSTOM_CONFIG_KEY, id);
  } catch (error) {
    console.error('保存激活配置ID失败:', error);
  }
};

/**
 * 获取当前激活的自定义配置ID
 */
export const getActiveCustomConfigId = (): string | null => {
  try {
    return localStorage.getItem(ACTIVE_CUSTOM_CONFIG_KEY);
  } catch (error) {
    console.error('读取激活配置ID失败:', error);
    return null;
  }
};

/**
 * 清除激活的自定义配置ID
 */
export const clearActiveCustomConfigId = (): void => {
  try {
    localStorage.removeItem(ACTIVE_CUSTOM_CONFIG_KEY);
  } catch (error) {
    console.error('清除激活配置ID失败:', error);
  }
};

/**
 * 获取当前激活的自定义配置
 */
export const getActiveCustomConfig = (): CustomAPIConfig | null => {
  const activeId = getActiveCustomConfigId();
  if (!activeId) {
    return null;
  }
  return getCustomAPIConfigById(activeId);
};
