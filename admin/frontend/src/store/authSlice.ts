import { createSlice, PayloadAction } from '@reduxjs/toolkit'

interface AuthState {
  token: string | null
  user: any | null
  isAuthenticated: boolean
}

const initialState: AuthState = {
  token: localStorage.getItem('admin_token'),
  user: null,
  isAuthenticated: !!localStorage.getItem('admin_token'),
}

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    setCredentials: (state, action: PayloadAction<{ token: string; user: any }>) => {
      state.token = action.payload.token
      state.user = action.payload.user
      state.isAuthenticated = true
      localStorage.setItem('admin_token', action.payload.token)
    },
    logout: (state) => {
      state.token = null
      state.user = null
      state.isAuthenticated = false
      localStorage.removeItem('admin_token')
    },
  },
})

export const { setCredentials, logout } = authSlice.actions
export default authSlice.reducer
