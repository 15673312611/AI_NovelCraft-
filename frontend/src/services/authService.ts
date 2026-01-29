import api from './api'

interface ApiResponse<T> {
  success: boolean
  message?: string
  data: T
  code?: string
  timestamp?: number
}

export interface LoginRequest {
  usernameOrEmail: string
  password: string
  rememberMe?: boolean
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
}

export interface LoginResponse {
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

export interface UserProfile {
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

  async updateProfile(profileData: Partial<UserProfile>): Promise<UserProfile> {
    const res = (await api.put('/auth/profile', profileData)) as ApiResponse<UserProfile>
    if (!res?.success) {
      throw new Error(res?.message || '更新用户信息失败')
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

  async refreshToken(): Promise<{ token: string }> {
    const res = (await api.post('/auth/refresh')) as ApiResponse<LoginResponse>
    if (!res?.success) {
      throw new Error(res?.message || '刷新Token失败')
    }
    return { token: res.data.token }
  }

  async validateToken(): Promise<boolean> {
    try {
      const res = (await api.get('/auth/validate')) as ApiResponse<unknown>
      return res?.success !== false
    } catch (error) {
      return false
    }
  }
}

export const authService = new AuthService()
