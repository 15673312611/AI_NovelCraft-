/**
 * AI请求工具
 * 简化版 - 不再需要前端配置AI服务
 * AI配置由后端统一管理，前端只需要发送请求即可
 */

/**
 * 检查AI服务是否可用（通过后端检查）
 * 现在总是返回true，因为配置在后端
 */
export const checkAIConfig = (): boolean => {
  return true;
};

/**
 * 为请求体添加空的AI配置占位符
 * 保持向后兼容，但实际配置由后端处理
 * @param body 原始请求体
 * @param overrides 可选的覆盖参数（model, temperature等）
 * @returns 请求体（不再添加AI配置）
 */
export const withAIConfig = (body: any = {}, overrides?: { model?: string; temperature?: number }): any => {
  // 不再添加AI配置，后端会使用系统配置
  return {
    ...body,
    // 可选：传递模型偏好，后端会验证
    ...(overrides?.model ? { preferredModel: overrides.model } : {}),
    // 可选：传递温度参数
    ...(overrides?.temperature !== undefined ? { temperature: overrides.temperature } : {})
  };
};

/**
 * AI配置相关的错误提示
 */
export const AI_CONFIG_ERROR_MESSAGE = '字数点余额不足，请联系管理员充值';

/**
 * 获取当前使用的配置名称（用于显示）
 * 现在显示"系统配置"
 */
export const getCurrentConfigName = (): string => {
  return '系统配置';
};

/**
 * 获取AI配置（兼容旧代码）
 * 返回空配置，实际由后端处理
 */
export const getAIConfigOrThrow = (): any => {
  return {
    provider: 'system',
    apiKey: '',
    model: '',
    baseUrl: ''
  };
};
