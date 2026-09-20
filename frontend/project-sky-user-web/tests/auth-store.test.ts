/**
 * 验证正式账号只在内存保存 Access Token，并能通过 HttpOnly Refresh Cookie 恢复会话。
 */
// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const authApi = vi.hoisted(() => ({
  getUserProfile: vi.fn(),
  loginWithPassword: vi.fn(),
  logoutAllDevices: vi.fn(),
  logoutCurrentDevice: vi.fn(),
  refreshSession: vi.fn(),
  registerUser: vi.fn(),
  sendRegistrationCode: vi.fn(),
}))
const setAccessToken = vi.hoisted(() => vi.fn())

vi.mock('@/api/auth', () => authApi)
vi.mock('@/api/http', () => ({
  ApiError: class ApiError extends Error {
    constructor(message: string, public status?: number) { super(message) }
  },
  setAccessToken,
}))

import { useAuthStore } from '@/stores/auth'

const session = {
  accessToken: 'access-in-memory-only',
  user: { id: 7, name: '测试用户', phone: '13800138000', avatar: null },
}

describe('正式账号状态', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('登录会话不把 Access Token 或用户资料写入 localStorage', () => {
    const store = useAuthStore()

    store.setSession(session)

    expect(store.token).toBe(session.accessToken)
    expect(store.user).toEqual(session.user)
    expect(setAccessToken).toHaveBeenLastCalledWith(session.accessToken)
    expect(localStorage.getItem('sky-user-token')).toBeNull()
    expect(localStorage.getItem('sky-user-profile')).toBeNull()
  })

  it('页面刷新后通过 Refresh Cookie 恢复内存会话', async () => {
    authApi.refreshSession.mockResolvedValue(session)
    const store = useAuthStore()

    await store.restoreSession()

    expect(authApi.refreshSession).toHaveBeenCalledOnce()
    expect(store.isAuthenticated).toBe(true)
    expect(store.user?.phone).toBe('13800138000')
  })

  it('启动恢复失败不会覆盖期间完成的注册会话', async () => {
    let rejectRefresh: ((error: Error) => void) | undefined
    authApi.refreshSession.mockReturnValue(new Promise((_, reject) => { rejectRefresh = reject }))
    authApi.registerUser.mockResolvedValue(session)
    const store = useAuthStore()

    const restoring = store.restoreSession()
    await store.register({ name: '测试用户', phone: '13800138000', code: '246810', password: 'StrongPass8' })
    rejectRefresh?.(new Error('no refresh cookie'))
    await restoring

    expect(store.isAuthenticated).toBe(true)
    expect(store.token).toBe(session.accessToken)
  })
})
