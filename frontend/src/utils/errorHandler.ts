/**
 * 错误信息友好化处理工具
 * 将技术性错误信息转换为用户友好的提示
 */

// 错误信息映射表
const errorMessageMap: Record<string, string> = {
  // 429 限流相关
  '429': '当前请求繁忙，请稍后再试',
  'Too Many Requests': '当前请求繁忙，请稍后再试',
  '负载已饱和': '当前模型已饱和，请暂时切换模型后重试',
  '上游负载': '当前模型已饱和，请暂时切换模型后重试',
  '当前分组上游负载已饱和': '当前模型已饱和，请暂时切换模型后重试',
  'No available channel': '当前模型已饱和，请暂时切换模型后重试',
  'No available channel for model': '当前模型已饱和，请暂时切换模型后重试',
  'under group': '当前模型已饱和，请暂时切换模型后重试',
  'rate limit': '请求过于频繁，请稍后再试',
  'Rate Limit': '请求过于频繁，请稍后再试',
  
  // 超时相关
  'timeout': '请求超时，请稍后再试',
  'Timeout': '请求超时，请稍后再试',
  'ETIMEDOUT': '连接超时，请检查网络后重试',
  
  // 网络相关
  'Network Error': '网络连接失败，请检查网络',
  'Failed to fetch': '网络请求失败，请检查网络',
  'ECONNREFUSED': '无法连接到服务器，请稍后再试',
  'ENOTFOUND': '无法连接到服务器，请检查网络',
  
  // 认证相关
  '401': '登录已过期，请重新登录',
  'Unauthorized': '登录已过期，请重新登录',
  '403': '没有权限执行此操作',
  'Forbidden': '没有权限执行此操作',
  
  // 服务器错误
  '500': '服务器内部错误，请稍后再试',
  '502': '服务暂时不可用，请稍后再试',
  '503': '服务暂时不可用，请稍后再试',
  '504': '服务响应超时，请稍后再试',
  'Internal Server Error': '服务器内部错误，请稍后再试',
  
  // AI 相关
  'context_length_exceeded': '内容过长，请减少输入内容后重试',
  'invalid_api_key': 'AI 服务配置错误，请检查设置',
  'model_not_found': 'AI 模型不可用，请更换模型后重试',
  'insufficient_quota': 'AI 服务额度不足，请稍后再试',
  
  // 流式相关
  '流式AI调用失败': '生成失败，请稍后再试',
  'stream': '生成过程中断，请重试',
}

const INSUFFICIENT_CREDITS_MESSAGE = '字数点余额不足，请先充值'
const LEGACY_INSUFFICIENT_CREDITS_FRAGMENT = '\u701b\u6941\u669f\u9410'
const MODEL_SATURATED_MESSAGE = '当前模型已饱和，请暂时切换模型后重试'

const modelSaturationPatterns: RegExp[] = [
  /No available channel for model/i,
  /under group .*distributor/i,
  /当前分组上游负载已饱和/,
  /上游负载已饱和/,
  /负载已饱和/,
  /model.*saturated/i,
]

const extractErrorCode = (value: any): string => {
  if (typeof value?.code === 'string') return value.code
  if (typeof value?.error?.code === 'string') return value.error.code
  if (typeof value?.data?.code === 'string') return value.data.code
  if (typeof value?.data?.error?.code === 'string') return value.data.error.code
  return ''
}

const normalizeSpecialErrorMessage = (message: string, value?: any): string => {
  const errorCode = extractErrorCode(value)
  if (errorCode === 'INSUFFICIENT_CREDITS') {
    return INSUFFICIENT_CREDITS_MESSAGE
  }

  if (!message) {
    return ''
  }

  if (message.includes('字数点余额不足') || message.includes('字数点不足')) {
    return INSUFFICIENT_CREDITS_MESSAGE
  }

  // 后端源码若被按错误编码编译，会把“字数点余额不足，请先充值”编成这类乱码。
  if (message.includes(LEGACY_INSUFFICIENT_CREDITS_FRAGMENT)) {
    return INSUFFICIENT_CREDITS_MESSAGE
  }

  return message
}

const extractRawErrorString = (error: any): string => {
  if (typeof error === 'string') {
    return normalizeSpecialErrorMessage(error)
  }
  if (error?.message) {
    return normalizeSpecialErrorMessage(error.message, error)
  }
  if (error?.response?.data?.message) {
    return normalizeSpecialErrorMessage(error.response.data.message, error.response.data)
  }
  if (error?.response?.data?.error?.message) {
    return normalizeSpecialErrorMessage(error.response.data.error.message, error.response.data)
  }
  if (error?.response?.data?.error) {
    return normalizeSpecialErrorMessage(String(error.response.data.error), error.response.data)
  }

  return normalizeSpecialErrorMessage(String(error), error)
}

const resolveJsonMessage = (value: any): string => {
  if (!value) return ''
  if (typeof value === 'string') return normalizeSpecialErrorMessage(value)
  if (typeof value?.message === 'string') return normalizeSpecialErrorMessage(value.message, value)
  if (typeof value?.error === 'string') return normalizeSpecialErrorMessage(value.error, value)
  if (typeof value?.error?.message === 'string') return normalizeSpecialErrorMessage(value.error.message, value)
  if (typeof value?.data?.message === 'string') return normalizeSpecialErrorMessage(value.data.message, value)
  if (typeof value?.data?.error === 'string') return normalizeSpecialErrorMessage(value.data.error, value)
  if (typeof value?.data?.error?.message === 'string') return normalizeSpecialErrorMessage(value.data.error.message, value)
  return normalizeSpecialErrorMessage('', value)
}

export const extractHttpErrorMessage = async (
  response: Response,
  fallbackMessage?: string
): Promise<string> => {
  const fallback =
    response.status === 402
      ? INSUFFICIENT_CREDITS_MESSAGE
      : fallbackMessage || `请求失败 (${response.status})`

  try {
    const rawText = await response.text()
    if (!rawText?.trim()) return fallback

    const trimmed = rawText.trim()
    try {
      const parsed = JSON.parse(trimmed)
      const message = resolveJsonMessage(parsed)
      if (message) return message
    } catch {
      // 非 JSON 文本，继续直接返回
    }

    return normalizeSpecialErrorMessage(trimmed)
  } catch {
    return fallback
  }
}

/**
 * 将原始错误信息转换为用户友好的提示
 * @param error 原始错误（可以是 Error 对象、字符串或任意类型）
 * @param defaultMessage 默认错误信息
 * @returns 用户友好的错误提示
 */
export const formatErrorMessage = (error: any, defaultMessage: string = '操作失败，请稍后再试'): string => {
  // 获取错误信息字符串
  let errorStr = extractRawErrorString(error)
  
  // 尝试解析 JSON 格式的错误信息
  try {
    if (errorStr.includes('{') && errorStr.includes('}')) {
      const jsonMatch = errorStr.match(/\{[\s\S]*\}/)
      if (jsonMatch) {
        const parsed = JSON.parse(jsonMatch[0])
        if (parsed.error?.message) {
          errorStr = parsed.error.message
        } else if (parsed.message) {
          errorStr = parsed.message
        }
      }
    }
  } catch {
    // 解析失败，使用原始字符串
  }

  errorStr = normalizeSpecialErrorMessage(errorStr, error)

  // 优先识别“模型饱和/无可用通道”类错误
  if (modelSaturationPatterns.some((pattern) => pattern.test(errorStr))) {
    return MODEL_SATURATED_MESSAGE
  }
  
  // 遍历映射表，查找匹配的友好提示
  for (const [key, friendlyMessage] of Object.entries(errorMessageMap)) {
    if (errorStr.includes(key)) {
      return friendlyMessage
    }
  }
  
  // 如果错误信息过长或包含技术细节，返回默认信息
  if (errorStr.length > 100 || 
      errorStr.includes('Error:') || 
      errorStr.includes('Exception') ||
      errorStr.includes('at ') ||
      errorStr.includes('http') ||
      errorStr.includes('api')) {
    return defaultMessage
  }
  
  // 返回原始信息（如果足够简短且不含技术细节）
  return errorStr || defaultMessage
}

/**
 * 格式化 AI 生成相关的错误信息
 */
export const formatAIErrorMessage = (error: any): string => {
  return formatErrorMessage(error, '生成失败，请稍后再试')
}

/**
 * 格式化网络请求相关的错误信息
 */
export const formatNetworkErrorMessage = (error: any): string => {
  return formatErrorMessage(error, '网络请求失败，请检查网络后重试')
}

export default formatErrorMessage
