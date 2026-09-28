import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, ApiRequestError } from './api'
import type { AuthResponse, Profile, SignupPayload } from './types'

const TOKEN_KEY = 'fitness.token'

type AuthContextValue = {
  token: string | null
  profile: Profile | null
  ready: boolean
  signup: (payload: SignupPayload) => Promise<void>
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  refresh: () => Promise<Profile | null>
  setProfile: (profile: Profile) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

function persistSession(response: AuthResponse, setToken: (token: string) => void, setProfile: (profile: Profile) => void) {
  localStorage.setItem(TOKEN_KEY, response.token)
  setToken(response.token)
  setProfile(response.profile)
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(null)
  const [profile, setProfile] = useState<Profile | null>(null)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    const stored = localStorage.getItem(TOKEN_KEY)
    if (!stored) {
      setReady(true)
      return
    }
    api<Profile>('/api/profile', {}, stored)
      .then((next) => {
        setToken(stored)
        setProfile(next)
      })
      .catch(() => {
        localStorage.removeItem(TOKEN_KEY)
      })
      .finally(() => setReady(true))
  }, [])

  const signup = useCallback(async (payload: SignupPayload) => {
    const response = await api<AuthResponse>('/api/auth/signup', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
    persistSession(response, setToken, setProfile)
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const response = await api<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    })
    persistSession(response, setToken, setProfile)
  }, [])

  const logout = useCallback(async () => {
    const current = token
    localStorage.removeItem(TOKEN_KEY)
    setToken(null)
    setProfile(null)
    if (!current) return
    try {
      await api<void>('/api/auth/logout', { method: 'POST' }, current)
    } catch {
      // Auth is already cleared locally. A failed logout call should not trap the user.
    }
  }, [token])

  const refresh = useCallback(async () => {
    if (!token) return null
    try {
      const next = await api<Profile>('/api/profile', {}, token)
      setProfile(next)
      return next
    } catch (error) {
      if (error instanceof ApiRequestError && (error.status === 401 || error.status === 403)) {
        localStorage.removeItem(TOKEN_KEY)
        setToken(null)
        setProfile(null)
        return null
      }
      throw error
    }
  }, [token])

  const value = useMemo<AuthContextValue>(() => ({
    token,
    profile,
    ready,
    signup,
    login,
    logout,
    refresh,
    setProfile,
  }), [token, profile, ready, signup, login, logout, refresh])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within AuthProvider')
  return context
}
