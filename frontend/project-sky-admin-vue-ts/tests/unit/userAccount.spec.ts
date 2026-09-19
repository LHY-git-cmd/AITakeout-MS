import Vue from 'vue'
import ElementUI from 'element-ui'
import { mount, Wrapper } from '@vue/test-utils'
import UserAccountView from '@/views/userAccount/index.vue'
import { adjustAccount, listUserAccounts } from '@/api/mockAccount'
import router from '@/router'
import { canAccessRoute } from '@/utils/routePermission'

Vue.use(ElementUI)

jest.mock('@/api/mockAccount', () => ({
  listUserAccounts: jest.fn(),
  adjustAccount: jest.fn(),
}))

const account = {
  id: 21,
  ownerId: 7,
  accountNo: 'USER-7',
  availableCent: 50000,
  frozenCent: 0,
  version: 0,
}

async function flushPromises() {
  await new Promise(resolve => setTimeout(resolve, 0))
}

async function mountView(): Promise<Wrapper<Vue>> {
  ;(listUserAccounts as jest.Mock).mockResolvedValue({ data: { code: 1, data: [account] } })
  const wrapper = mount(UserAccountView)
  await flushPromises()
  return wrapper
}

describe('UserAccountView', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  it('blocks zero-value adjustment and requires a reason with inline errors', async() => {
    const wrapper = await mountView()
    await wrapper.find('[data-test=adjust-open]').trigger('click')
    await wrapper.find('[data-test=adjust]').trigger('click')
    await Vue.nextTick()

    expect(wrapper.text()).toContain('调整金额不能为0')
    expect(wrapper.text()).toContain('请输入调账原因')
    expect(adjustAccount).not.toHaveBeenCalled()
    wrapper.destroy()
  })

  it('confirms the user, signed amount and reason before submitting', async() => {
    const wrapper = await mountView()
    const confirm = jest.fn().mockResolvedValue('confirm')
    ;(wrapper.vm as any).$confirm = confirm
    ;(adjustAccount as jest.Mock).mockResolvedValue({ data: { code: 1, data: {} } })
    await wrapper.find('[data-test=adjust-open]').trigger('click')
    ;(wrapper.vm as any).form.deltaCent = -1250
    ;(wrapper.vm as any).form.reason = '撤销误发补偿'
    await Vue.nextTick()
    await wrapper.find('[data-test=adjust]').trigger('click')
    await flushPromises()

    expect(confirm).toHaveBeenCalledWith(
      expect.stringContaining('用户 7'),
      '确认账户调账',
      expect.objectContaining({ type: 'warning' })
    )
    expect(confirm.mock.calls[0][0]).toContain('-12.50元')
    expect(confirm.mock.calls[0][0]).toContain('撤销误发补偿')
    expect(adjustAccount).toHaveBeenCalledTimes(1)
    wrapper.destroy()
  })

  it('keeps submit loading and blocks duplicate requests until completion', async() => {
    const wrapper = await mountView()
    ;(wrapper.vm as any).$confirm = jest.fn().mockResolvedValue('confirm')
    let resolveAdjustment: (value: unknown) => void = () => undefined
    ;(adjustAccount as jest.Mock).mockReturnValue(new Promise(resolve => { resolveAdjustment = resolve }))
    await wrapper.find('[data-test=adjust-open]').trigger('click')
    ;(wrapper.vm as any).form.deltaCent = 5000
    ;(wrapper.vm as any).form.reason = '活动补偿'
    await Vue.nextTick()
    const submit = wrapper.find('[data-test=adjust]')

    await submit.trigger('click')
    await flushPromises()
    await submit.trigger('click')

    expect((wrapper.vm as any).submitting).toBe(true)
    expect(adjustAccount).toHaveBeenCalledTimes(1)
    resolveAdjustment({ data: { code: 1, data: {} } })
    await flushPromises()
    expect((wrapper.vm as any).submitting).toBe(false)
    wrapper.destroy()
  })

  it('blocks duplicate confirmation dialogs while confirmation is pending', async() => {
    const wrapper = await mountView()
    let resolveConfirm: (value: unknown) => void = () => undefined
    const confirm = jest.fn().mockReturnValue(new Promise(resolve => { resolveConfirm = resolve }))
    ;(wrapper.vm as any).$confirm = confirm
    ;(adjustAccount as jest.Mock).mockResolvedValue({ data: { code: 1, data: {} } })
    await wrapper.find('[data-test=adjust-open]').trigger('click')
    ;(wrapper.vm as any).form.deltaCent = 5000
    ;(wrapper.vm as any).form.reason = '并发确认测试'
    await Vue.nextTick()
    const submit = wrapper.find('[data-test=adjust]')

    await submit.trigger('click')
    await flushPromises()
    await submit.trigger('click')

    expect((wrapper.vm as any).submitting).toBe(true)
    expect(confirm).toHaveBeenCalledTimes(1)
    expect(adjustAccount).not.toHaveBeenCalled()
    resolveConfirm('confirm')
    await flushPromises()
    expect(adjustAccount).toHaveBeenCalledTimes(1)
    wrapper.destroy()
  })

  it('does not report an error when the user cancels confirmation', async() => {
    const wrapper = await mountView()
    const error = jest.fn()
    ;(wrapper.vm as any).$message = { error, success: jest.fn() }
    ;(wrapper.vm as any).$confirm = jest.fn().mockRejectedValue('close')
    await wrapper.find('[data-test=adjust-open]').trigger('click')
    ;(wrapper.vm as any).form.deltaCent = 100
    ;(wrapper.vm as any).form.reason = '取消测试'
    await Vue.nextTick()
    await wrapper.find('[data-test=adjust]').trigger('click')
    await flushPromises()

    expect(error).not.toHaveBeenCalled()
    expect(adjustAccount).not.toHaveBeenCalled()
    wrapper.destroy()
  })

  it('restricts the account route to super administrators', () => {
    const root: any = (router as any).options.routes.find((route: any) => route.path === '/')
    const route = root.children.find((child: any) => child.name === 'UserAccount')
    expect(route.meta.roles).toEqual(['SUPER_ADMIN'])
    expect(canAccessRoute(route, ['ADMIN'])).toBe(false)
    expect(canAccessRoute(route, ['SUPER_ADMIN'])).toBe(true)
  })
})
