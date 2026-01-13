import axios, { AxiosInstance, AxiosResponse } from 'axios'

// 创建axios实例
const api: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 600000, // 10分钟超时（AI生成需要较长时间）
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器
api.interceptors.request.use(
  (config: any) => {
    const token = localStorage.getItem('token')
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  (response: AxiosResponse) => {
    return response.data
  },
  (error) => {
    if (error.response) {
      const { status, data } = error.response
      
      // 提取后端返回的错误信息
      const errorMessage = data?.message || data?.error || getDefaultErrorMessage(status)

      switch (status) {
        case 401:
          // 只有在不是登录/注册页面时才自动跳转
          if (!window.location.pathname.includes('/login') && 
              !window.location.pathname.includes('/register')) {
            console.error('认证失败，请重新登录')
            localStorage.removeItem('token')
            window.location.href = '/login'
          }
          break
        case 403:
          console.error('权限不足:', errorMessage)
          break
        case 404:
          console.error('请求的资源不存在:', errorMessage)
          break
        case 500:
          console.error('服务器内部错误:', errorMessage)
          break
        default:
          console.error('请求失败:', errorMessage)
      }
      
      // 创建一个包含后端错误信息的错误对象
      const enhancedError = new Error(errorMessage) as any
      enhancedError.response = error.response
      enhancedError.status = status
      enhancedError.data = data
      return Promise.reject(enhancedError)
    } else if (error.request) {
      const networkError = new Error('网络错误，请检查网络连接') as any
      networkError.isNetworkError = true
      return Promise.reject(networkError)
    } else {
      return Promise.reject(new Error('请求配置错误'))
    }
  }
)

// 根据状态码获取默认错误信息
function getDefaultErrorMessage(status: number): string {
  switch (status) {
    case 400:
      return '请求参数错误'
    case 401:
      return '认证失败，请重新登录'
    case 403:
      return '权限不足'
    case 404:
      return '请求的资源不存在'
    case 500:
      return '服务器内部错误'
    case 502:
      return '网关错误'
    case 503:
      return '服务暂时不可用'
    default:
      return `请求失败 (${status})`
  }
}

export default api 