import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit'
import { authService } from '@/services/authService'

export interface User {
  id: number
  username: string
  email: string
  nickname?: string
  avatarUrl?: string
  bio?: string
  status: string
  emailVerified?: boolean
  lastLoginAt?: string
  createdAt: string
  updatedAt: string
}

export interface AuthState {
  user: User | null
  token: string | null
  isAuthenticated: boolean
  loading: boolean
  error: string | null
}

const initialState: AuthState = {
  user: null,
  token: localStorage.getItem('token'),
  isAuthenticated: !!localStorage.getItem('token'),
  loading: false,
  error: null,
}

export const login = createAsyncThunk(
  'auth/login',
  async (credentials: { usernameOrEmail: string; password: string }) => {
    const response = await authService.login(credentials)
    return response
  }
)

export const register = createAsyncThunk(
  'auth/register',
  async (userData: { username: string; email: string; password: string }) => {
    const response = await authService.register(userData)
    return response
  }
)

export const getProfile = createAsyncThunk('auth/getProfile', async () => {
  const response = await authService.getProfile()
  return response
})

export const logout = createAsyncThunk('auth/logout', async () => {
  await authService.logout()
})

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null
    },
    setToken: (state, action: PayloadAction<string>) => {
      state.token = action.payload
      state.isAuthenticated = true
      localStorage.setItem('token', action.payload)
    },
    clearAuth: (state) => {
      state.user = null
      state.token = null
      state.isAuthenticated = false
      state.error = null
      localStorage.removeItem('token')
    },
  },
  extraReducers: (builder) => {
    builder
      // Login
      .addCase(login.pending, (state) => {
        state.loading = true
        state.error = null
      })
      .addCase(login.fulfilled, (state, action) => {
        state.loading = false
        // 处理后端返回的ApiResponse结构
        const response = action.payload.data || action.payload
        state.user = response.user
        state.token = response.token
        state.isAuthenticated = true
        localStorage.setItem('token', response.token)
      })
      .addCase(login.rejected, (state, action) => {
        state.loading = false
        state.error = action.error.message || '登录失败'
      })
      // Register
      .addCase(register.pending, (state) => {
        state.loading = true
        state.error = null
      })
      .addCase(register.fulfilled, (state, action) => {
        state.loading = false
        // 处理后端返回的ApiResponse结构
        const response = action.payload.data || action.payload
        state.user = response.user
        state.token = response.token
        state.isAuthenticated = true
        localStorage.setItem('token', response.token)
      })
      .addCase(register.rejected, (state, action) => {
        state.loading = false
        state.error = action.error.message || '注册失败'
      })
      // Get Profile
      .addCase(getProfile.pending, (state) => {
        state.loading = true
      })
      .addCase(getProfile.fulfilled, (state, action) => {
        state.loading = false
        // 处理后端返回的ApiResponse结构
        const response = action.payload.data || action.payload
        state.user = response
      })
      .addCase(getProfile.rejected, (state) => {
        state.loading = false
        state.isAuthenticated = false
        state.token = null
        state.user = null
        localStorage.removeItem('token')
      })
      // Logout
      .addCase(logout.fulfilled, (state) => {
        state.user = null
        state.token = null
        state.isAuthenticated = false
        localStorage.removeItem('token')
      })
  },
})

export const { clearError, setToken, clearAuth } = authSlice.actions
export default authSlice.reducer 