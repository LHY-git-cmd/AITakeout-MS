/** 验证个人资料与敏感安全设置页面只暴露受控字段，并在敏感变更后清理会话。 */
// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const profileApi = vi.hoisted(() => ({
  updateProfile: vi.fn(), uploadAvatar: vi.fn(), sendSecurityCode: vi.fn(),
  changePhone: vi.fn(), changePassword: vi.fn(),
}))
const auth = vi.hoisted(() => ({
  isAuthenticated: true,
  user: { id: 7, name: '小苍', phone: '13800138000', avatar: null as string | null },
  updateUser: vi.fn(), clearSession: vi.fn(),
}))
const ui = vi.hoisted(() => ({ openLogin: vi.fn() }))
const account = vi.hoisted(() => ({ reset: vi.fn() }))
const router = vi.hoisted(() => ({ replace: vi.fn() }))

vi.mock('@/api/profile', () => profileApi)
vi.mock('@/stores/auth', () => ({ useAuthStore: () => auth }))
vi.mock('@/stores/ui', () => ({ useUiStore: () => ui }))
vi.mock('@/stores/account', () => ({ useAccountStore: () => account }))
vi.mock('vue-router', () => ({ useRouter: () => router }))

import ProfileEditView from '@/views/ProfileEditView.vue'
import SecuritySettingsView from '@/views/SecuritySettingsView.vue'

const scaffold = { template: '<main><slot /></main>' }

describe('个人资料与安全设置', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    auth.isAuthenticated = true
    profileApi.updateProfile.mockResolvedValue({ ...auth.user, name: '新昵称' })
    profileApi.changePassword.mockResolvedValue(undefined)
  })

  it('资料页只允许昵称与本地图片，不提供身份证号或头像地址输入', async () => {
    const wrapper = mount(ProfileEditView, { global: { stubs: { PageScaffold: scaffold } } })

    expect(wrapper.find('input[type="file"]').attributes('accept')).toBe('image/jpeg,image/png,image/webp')
    expect(wrapper.find('input[type="url"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('身份证')
    await wrapper.get('#profile-name').setValue(' 新昵称 ')
    await wrapper.get('.settings-form').trigger('submit')
    await flushPromises()

    expect(profileApi.updateProfile).toHaveBeenCalledWith('新昵称')
    expect(auth.updateUser).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('密码修改成功后清空认证、账户状态并回到登录入口', async () => {
    const wrapper = mount(SecuritySettingsView, { global: { stubs: { PageScaffold: scaffold } } })
    await wrapper.get('#current-password').setValue('OldPass88')
    await wrapper.get('#new-password').setValue('NewPass99')
    await wrapper.get('#confirm-password').setValue('NewPass99')
    await wrapper.findAll('form')[1].trigger('submit')
    await flushPromises()

    expect(profileApi.changePassword).toHaveBeenCalledWith({
      currentPassword: 'OldPass88', code: undefined, newPassword: 'NewPass99',
    })
    expect(auth.clearSession).toHaveBeenCalled()
    expect(account.reset).toHaveBeenCalled()
    expect(ui.openLogin).toHaveBeenCalled()
    expect(router.replace).toHaveBeenCalledWith('/profile')
    wrapper.unmount()
  })
})
