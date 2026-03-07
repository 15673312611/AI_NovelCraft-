import axios from 'axios'
import { message } from 'antd'

const request = axios.create({
  baseURL: '/admin',
  timeout: 30000,
})

request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('admin_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  (response) => {
    return response.data
  },
  (error) => {
    if (error.response) {
      const { status, data } = error.response
      
      // 提取后端返回的错误信息
      const errorMessage = data?.message || data?.error || getDefaultErrorMessage(status)
      
      if (status === 401) {
        message.error('未授权，请重新登录')
        localStorage.removeItem('admin_token')
        window.location.href = '/login'
      } else if (status === 403) {
        message.error('没有权限访问')
      } else {
        message.error(errorMessage)
      }
      
      // 创建一个包含后端错误信息的错误对象
      const enhancedError = new Error(errorMessage) as any
      enhancedError.response = error.response
      enhancedError.status = status
      enhancedError.data = data
      return Promise.reject(enhancedError)
    } else {
      const networkError = new Error('网络错误，请检查网络连接') as any
      networkError.isNetworkError = true
      message.error('网络错误')
      return Promise.reject(networkError)
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
    default:
      return `请求失败 (${status})`
  }
}

export default request
