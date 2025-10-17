import api from './api'

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
    const response = await api.post('/auth/login', credentials)
    // 由于api.ts的响应拦截器已经返回了response.data，这里直接返回response
    return response
  }

  async register(userData: RegisterRequest): Promise<LoginResponse> {
    const response = await api.post('/auth/register', userData)
    // 由于api.ts的响应拦截器已经返回了response.data，这里直接返回response
    return response
  }

  async getProfile(): Promise<UserProfile> {
    const response = await api.get('/auth/profile')
    // 由于api.ts的响应拦截器已经返回了response.data，这里直接返回response
    return response
  }

  async updateProfile(profileData: Partial<UserProfile>): Promise<UserProfile> {
    const response = await api.put('/auth/profile', profileData)
    return response.data
  }

  async changePassword(passwordData: { oldPassword: string; newPassword: string }): Promise<void> {
    await api.put('/auth/change-password', passwordData)
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
    const response = await api.post('/auth/refresh')
    return response.data
  }

  async validateToken(): Promise<boolean> {
    try {
      await api.get('/auth/validate')
      return true
    } catch (error) {
      return false
    }
  }
}

export const authService = new AuthService() 