import { create } from 'zustand'

import {
  configureAuthRefresh,
  resumeAuthRefresh,
  suspendAuthRefresh,
} from '../../lib/api'
import {
  clearApiSession,
  login as loginRequest,
  logout as logoutRequest,
  refreshSession,
} from './auth-api'

type AuthStatus = 'anonymous' | 'authenticated' | 'unknown'

interface AuthState {
  accessToken: string | null
  status: AuthStatus
  initialize: () => Promise<void>
  refresh: () => Promise<string>
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  clearSession: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  status: 'unknown',
  initialize: async () => {
    try {
      const tokens = await refreshSession()
      set({ accessToken: tokens.accessToken, status: 'authenticated' })
    } catch {
      clearApiSession()
      set({ accessToken: null, status: 'anonymous' })
    }
  },
  refresh: async () => {
    try {
      const tokens = await refreshSession()
      set({ accessToken: tokens.accessToken, status: 'authenticated' })
      return tokens.accessToken
    } catch (error) {
      clearApiSession()
      set({ accessToken: null, status: 'anonymous' })
      throw error
    }
  },
  login: async (email, password) => {
    const tokens = await loginRequest(email, password)
    set({ accessToken: tokens.accessToken, status: 'authenticated' })
  },
  logout: async () => {
    await suspendAuthRefresh()
    try {
      await logoutRequest()
      clearApiSession()
      set({ accessToken: null, status: 'anonymous' })
    } finally {
      resumeAuthRefresh()
    }
  },
  clearSession: () => {
    clearApiSession()
    set({ accessToken: null, status: 'anonymous' })
  },
}))

configureAuthRefresh(
  () => useAuthStore.getState().refresh(),
  () => useAuthStore.getState().clearSession(),
)
