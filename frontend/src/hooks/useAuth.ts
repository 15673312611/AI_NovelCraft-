import { useSelector, useDispatch } from 'react-redux'
import { useNavigate } from 'react-router-dom'
import { AppDispatch, RootState } from '@/store'
import { login, register, logout, getProfile } from '@/store/slices/authSlice'

export const useAuth = () => {
  const dispatch = useDispatch<AppDispatch>()
  const navigate = useNavigate()
  const { user, token, isAuthenticated, loading, error } = useSelector((state: RootState) => state.auth)

  const handleLogin = async (credentials: { usernameOrEmail: string; password: string; rememberMe?: boolean }) => {
    try {
      await dispatch(login(credentials)).unwrap()
      return { success: true }
    } catch (error) {
      return { success: false, error }
    }
  }

  const handleRegister = async (userData: { username: string; email: string; password: string }) => {
    try {
      await dispatch(register(userData)).unwrap()
      return { success: true }
    } catch (error) {
      return { success: false, error }
    }
  }

  const handleLogout = async () => {
    try {
      await dispatch(logout()).unwrap()
      navigate('/login')
      return { success: true }
    } catch (error) {
      return { success: false, error }
    }
  }

  const refreshProfile = async () => {
    try {
      await dispatch(getProfile()).unwrap()
      return { success: true }
    } catch (error) {
      return { success: false, error }
    }
  }

  const hasRole = (role: string): boolean => {
    return user?.roles?.includes(role) || false
  }

  const hasAnyRole = (roles: string[]): boolean => {
    return user?.roles?.some(role => roles.includes(role)) || false
  }

  const isAdmin = (): boolean => {
    return hasRole('ADMIN')
  }

  const isAuthor = (): boolean => {
    return hasRole('AUTHOR') || isAdmin()
  }

  return {
    user,
    token,
    isAuthenticated,
    loading,
    error,
    login: handleLogin,
    register: handleRegister,
    logout: handleLogout,
    refreshProfile,
    hasRole,
    hasAnyRole,
    isAdmin,
    isAuthor,
  }
} 