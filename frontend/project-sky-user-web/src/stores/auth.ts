import { defineStore } from 'pinia'
import { getUserProfile, loginWithPassword, registerUser, type RegisterPayload, type UserProfile } from '@/api/auth'
import { ApiError } from '@/api/http'

const TOKEN_KEY = 'sky-user-token'
const PROFILE_KEY = 'sky-user-profile'

function readStoredProfile(): UserProfile | null {
  const value = localStorage.getItem(PROFILE_KEY)
  if (!value) return null
  try {
    return JSON.parse(value) as UserProfile
  } catch {
    localStorage.removeItem(PROFILE_KEY)
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY),
    user: readStoredProfile(),
    restoring: false,
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token),
    displayName: (state) => state.user?.name || state.user?.phone || '用户',
  },
  actions: {
    setSession(result: UserProfile & { token: string }) {
      this.token = result.token
      this.user = {
        id: result.id,
        name: result.name,
        phone: result.phone,
        avatar: result.avatar,
      }
      localStorage.setItem(TOKEN_KEY, result.token)
      localStorage.setItem(PROFILE_KEY, JSON.stringify(this.user))
    },
    async login(phone: string, password: string) {
      this.setSession(await loginWithPassword(phone, password))
    },
    async register(payload: RegisterPayload) {
      this.setSession(await registerUser(payload))
    },
    async restoreSession() {
      if (!this.token || this.restoring) return
      this.restoring = true
      try {
        const profile = await getUserProfile()
        this.user = profile
        localStorage.setItem(PROFILE_KEY, JSON.stringify(profile))
      } catch (error) {
        // Network failures keep the cached demo session; rejected server responses do not.
        if (error instanceof ApiError && error.status === 200) {
          this.logout()
        }
      } finally {
        this.restoring = false
      }
    },
    logout() {
      this.token = null
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(PROFILE_KEY)
    },
  },
})
