import api from './api'
import { getClientIdentityHeaders } from './clientIdentityService'

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
  code?: string
  password: string
}

interface AuthUser {
  id: number
  username: string
  email: string
  nickname?: string
  avatarUrl?: string
  bio?: string
  status: string
  emailVerified?: boolean
  loginType?: string
  lastLoginAt?: string
  createdAt: string
  updatedAt: string
}

interface LoginResponse {
  user: AuthUser
  token: string
  tokenType?: string
}

type UserProfile = AuthUser

class AuthService {
  async login(credentials: LoginRequest): Promise<LoginResponse> {
    const headers = await getClientIdentityHeaders()
    const res = (await api.post('/auth/login', credentials, { headers })) as ApiResponse<LoginResponse>
    if (!res?.success) {
      throw new Error(res?.message || '登录失败')
    }
    return res.data
  }

  async register(userData: RegisterRequest): Promise<LoginResponse> {
    const headers = await getClientIdentityHeaders()
    const res = (await api.post('/auth/register', userData, { headers })) as ApiResponse<LoginResponse>
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
    } catch {
      console.warn('Logout API call failed, but clearing local token')
    }
  }
}

export const authService = new AuthService()
