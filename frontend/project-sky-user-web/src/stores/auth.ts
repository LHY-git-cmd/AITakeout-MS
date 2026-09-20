import { defineStore } from 'pinia'
import {
  loginWithPassword,
  logoutAllDevices,
  logoutCurrentDevice,
  refreshSession,
  registerUser,
  type AuthSession,
  type RegisterPayload,
  type UserProfile,
} from '@/api/auth'
import { setAccessToken } from '@/api/http'

function clearSessionArtifacts() {
  sessionStorage.removeItem('sky-last-order')
  for (let index = sessionStorage.length - 1; index >= 0; index -= 1) {
    const key = sessionStorage.key(index)
    if (key?.startsWith('sky-payment-recovery:')) sessionStorage.removeItem(key)
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: null as string | null,
    user: null as UserProfile | null,
    restoring: false,
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token && state.user),
    displayName: (state) => state.user?.name || state.user?.phone || '用户',
  },
  actions: {
    updateUser(user: UserProfile) {
      this.user = user
    },
    setSession(session: AuthSession) {
      this.token = session.accessToken
      this.user = session.user
      setAccessToken(session.accessToken)
    },
    clearSession() {
      this.token = null
      this.user = null
      setAccessToken(null)
      clearSessionArtifacts()
    },
    async login(phone: string, password: string) {
      this.setSession(await loginWithPassword(phone, password))
    },
    async register(payload: RegisterPayload) {
      this.setSession(await registerUser(payload))
    },
    async restoreSession() {
      if (this.restoring || this.isAuthenticated) return
      this.restoring = true
      try {
        this.setSession(await refreshSession())
      } catch {
        // 首次打开页面且没有 Refresh Cookie 时保持游客状态，不主动打断用户。
        // 若刷新请求期间用户已完成登录或注册，不得用旧请求的失败结果覆盖新会话。
        if (!this.isAuthenticated) this.clearSession()
      } finally {
        this.restoring = false
      }
    },
    async logout(allDevices = false) {
      try {
        if (allDevices) await logoutAllDevices()
        else await logoutCurrentDevice()
      } finally {
        this.clearSession()
      }
    },
  },
})
