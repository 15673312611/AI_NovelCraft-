import api from './api'

interface ApiResponse<T> {
  success: boolean
  message?: string
  data: T
  code?: string
  timestamp?: number
}

interface LoginRequest {
  usernameOrEmail: string
  password: string
  rememberMe?: boolean
}

interface RegisterRequest {
  username: string
  email: string
  password: string
}

interface LoginResponse {
  user: {
    id: number
    username: string
    email: string
    status: string
    roles: string[]
    createdAt: string
    updatedAt: string
  }
  token: string
  tokenType?: string
}

interface UserProfile {
  id: number
  username: string
  email: string
  status: string
  roles: string[]
  createdAt: string
  updatedAt: string
}

class AuthService {
  async login(credentials: LoginRequest): Promise<LoginResponse> {
    const res = (await api.post('/auth/login', credentials)) as ApiResponse<LoginResponse>
    if (!res?.success) {
      throw new Error(res?.message || '登录失败')
    }
    return res.data
  }

  async register(userData: RegisterRequest): Promise<LoginResponse> {
    const res = (await api.post('/auth/register', userData)) as ApiResponse<LoginResponse>
    if (!res?.success) {
      throw new Error(res?.message || '注册失败')
    }
    return res.data
  }

  async getProfile(): Promise<UserProfile> {
    const res = (await api.get('/auth/profile')) as ApiResponse<UserProfile>
    if (!res?.success) {
      throw new Error(res?.message || '获取用户信息失败')
    }
    return res.data
  }

  async changePassword(passwordData: { oldPassword: string; newPassword: string }): Promise<void> {
    const res = (await api.put('/auth/change-password', passwordData)) as ApiResponse<unknown>
    if (res && res.success === false) {
      throw new Error(res.message || '修改密码失败')
    }
  }

  async logout(): Promise<void> {
    try {
      await api.post('/auth/logout')
    } catch (error) {
      // 即使API调用失败，也要清除本地token
      console.warn('Logout API call failed, but clearing local token')
    }
  }
}

export const authService = new AuthService()
