/**
 * 验证支付结果只采信服务端支付单状态，不采信可篡改的 URL 成功参数。
 */
// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const paymentApi = vi.hoisted(() => ({
  beginOrderPayment: vi.fn(),
  clearPaymentRecovery: vi.fn(),
  queryPayment: vi.fn(),
  readPaymentRecovery: vi.fn(),
}))
const route = vi.hoisted(() => ({
  query: { success: '1', paymentNo: 'PAY-1', id: '20' } as Record<string, string>,
}))

vi.mock('@/api/payment', () => paymentApi)
vi.mock('vue-router', () => ({
  RouterLink: { template: '<a><slot /></a>' },
  useRoute: () => route,
  useRouter: () => ({ replace: vi.fn() }),
}))

import PaymentResultView from '@/views/PaymentResultView.vue'

describe('支付结果页', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    route.query = { success: '1', paymentNo: 'PAY-1', id: '20' }
    sessionStorage.clear()
  })

  afterEach(() => vi.useRealTimers())

  it('不采信 success 查询参数，处理中显示服务端确认状态', async () => {
    paymentApi.queryPayment.mockResolvedValue({
      paymentNo: 'PAY-1', orderId: 20, status: 'PROCESSING', amountCent: 5_000,
      expiresAt: '2026-09-20T12:00:00', failureCode: null,
    })

    const wrapper = mount(PaymentResultView, {
      global: { stubs: {
        PageScaffold: { template: '<main><slot /></main>' },
        RouterLink: { template: '<a><slot /></a>' },
      } },
    })
    await flushPromises()

    expect(paymentApi.queryPayment).toHaveBeenCalledWith('PAY-1')
    expect(wrapper.text()).toContain('支付结果确认中')
    expect(wrapper.text()).not.toContain('支付成功')
    wrapper.unmount()
  })

  it('仅在服务端返回成功后展示支付成功', async () => {
    paymentApi.queryPayment.mockResolvedValue({
      paymentNo: 'PAY-1', orderId: 20, status: 'SUCCEEDED', amountCent: 5_000,
      expiresAt: '2026-09-20T12:00:00', failureCode: null,
    })

    const wrapper = mount(PaymentResultView, {
      global: { stubs: {
        PageScaffold: { template: '<main><slot /></main>' },
        RouterLink: { template: '<a><slot /></a>' },
      } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('支付成功')
    wrapper.unmount()
  })

  it('按 1 秒、2 秒退避轮询且 30 秒后提示稍后查看', async () => {
    paymentApi.queryPayment.mockResolvedValue({
      paymentNo: 'PAY-1', orderId: 20, status: 'PROCESSING', amountCent: 5_000,
      expiresAt: '2026-09-20T12:00:00', failureCode: null,
    })
    const wrapper = mount(PaymentResultView, {
      global: { stubs: {
        PageScaffold: { template: '<main><slot /></main>' },
        RouterLink: { template: '<a><slot /></a>' },
      } },
    })
    await flushPromises()
    expect(paymentApi.queryPayment).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(999)
    expect(paymentApi.queryPayment).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1)
    expect(paymentApi.queryPayment).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(2_000)
    expect(paymentApi.queryPayment).toHaveBeenCalledTimes(3)
    await vi.advanceTimersByTimeAsync(27_000)

    expect(wrapper.text()).toContain('支付仍在确认')
    expect(wrapper.text()).toContain('稍后从订单查看')
    wrapper.unmount()
  })
})
