/**
 * AI配置类型定义
 */

export interface AIConfig {
  provider: 'deepseek' | 'qwen' | 'kimi' | 'custom';
  apiKey: string;
  model: string;
  baseUrl?: string;
}

/**
 * 自定义API配置项
 */
export interface CustomAPIConfig {
  id: string;
  name: string;
  baseUrl: string;
  apiKey: string;
  model: string;
  description?: string;
  createdAt: number;
  updatedAt: number;
}

export interface AIProvider {
  id: string;
  name: string;
  defaultBaseUrl: string;
  defaultModels: string[];
  icon?: string;
}

/**
 * 支持的AI服务商配置
 */
export const AI_PROVIDERS: AIProvider[] = [
  {
    id: 'deepseek',
    name: 'DeepSeek',
    defaultBaseUrl: 'https://api.deepseek.com',
    defaultModels: [
      'deepseek-chat',
      'deepseek-reasoner',  // R1推理模型
      'deepseek-coder'
    ]
  },
  {
    id: 'qwen',
    name: '通义千问',
    defaultBaseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    defaultModels: [
      'qwen-turbo',
      'qwen-plus',
      'qwen-max',
      'qwen-max-longcontext'
    ]
  },
  {
    id: 'kimi',
    name: 'Kimi (月之暗面)',
    defaultBaseUrl: 'https://api.moonshot.cn',
    defaultModels: [
      // K2系列 - 最新高性能模型
      'kimi-k2-turbo-preview',
      'kimi-k2-0905-preview',
      'kimi-k2-0711-preview',
      // Latest系列 - 通用模型
      'kimi-latest',
      'kimi-latest-128k',
      // 长思考模型
      'kimi-thinking-preview',
      // V1系列 - 经典模型（兼容）
      'moonshot-v1-128k',
      'moonshot-v1-auto'
    ]
  },
  {
    id: 'custom',
    name: '自定义',
    defaultBaseUrl: '',
    defaultModels: []
  }
];

/**
 * 获取默认AI配置
 */
export const getDefaultAIConfig = (): AIConfig => {
  return {
    provider: 'deepseek',
    apiKey: '',
    model: 'deepseek-chat',
    baseUrl: 'https://api.deepseek.com'
  };
};

/**
 * 根据provider获取配置信息
 */
export const getProviderInfo = (providerId: string): AIProvider | undefined => {
  return AI_PROVIDERS.find(p => p.id === providerId);
};
