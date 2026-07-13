import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, ApiError } from '../services/api'

export interface CurrentUser {
  id: number
  organizationId: number
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
}

export interface AuthState {
  me: CurrentUser | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  refresh: () => Promise<void>
}

export const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<CurrentUser | null>(null)
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    try {
      setMe(await api<CurrentUser>('/api/v1/auth/me'))
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) setMe(null)
      else throw error
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void refresh() }, [refresh])

  const login = useCallback(async (username: string, password: string) => {
    await api<CurrentUser>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    })
    await refresh()
  }, [refresh])

  const logout = useCallback(async () => {
    await api<void>('/api/v1/auth/logout', { method: 'POST' })
    setMe(null)
  }, [])

  const value = useMemo<AuthState>(() => ({ me, loading, login, logout, refresh }),
    [me, loading, login, logout, refresh])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth 必须在 AuthProvider 内使用')
  return value
}
