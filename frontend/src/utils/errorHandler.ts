/**
 * 错误信息友好化处理工具
 * 将技术性错误信息转换为用户友好的提示
 */

// 错误信息映射表
const errorMessageMap: Record<string, string> = {
  // 429 限流相关
  '429': '当前请求繁忙，请稍后再试',
  'Too Many Requests': '当前请求繁忙，请稍后再试',
  '负载已饱和': '当前服务繁忙，请稍后再试',
  '上游负载': '当前服务繁忙，请稍后再试',
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

/**
 * 将原始错误信息转换为用户友好的提示
 * @param error 原始错误（可以是 Error 对象、字符串或任意类型）
 * @param defaultMessage 默认错误信息
 * @returns 用户友好的错误提示
 */
export const formatErrorMessage = (error: any, defaultMessage: string = '操作失败，请稍后再试'): string => {
  // 获取错误信息字符串
  let errorStr = ''
  
  if (typeof error === 'string') {
    errorStr = error
  } else if (error?.message) {
    errorStr = error.message
  } else if (error?.response?.data?.message) {
    errorStr = error.response.data.message
  } else if (error?.response?.data?.error?.message) {
    errorStr = error.response.data.error.message
  } else {
    errorStr = String(error)
  }
  
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
