/** 验证结算页只展示服务端试算金额，并为失败提供可恢复入口。 */
// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const addressApi = vi.hoisted(() => ({ getAddresses: vi.fn(), fullAddress: vi.fn(() => '北京市测试路 2 号') }))
const checkout = vi.hoisted(() => ({
  quote: {
    goodsAmountCent: 7900, packAmountCent: 100, deliveryFeeCent: 800, discountAmountCent: 0,
    amountCent: 8800, distanceMeters: 4200, pricingRuleVersion: 'delivery-v1',
    estimatedDeliveryTime: '2026-09-20T12:30:00', expiresAt: '2026-09-20T12:05:00', previewToken: 'signed',
    availableSlots: [{ start: '2026-09-20T13:00:00', end: '2026-09-20T13:30:00', label: '09月20日 13:00–13:30' }],
    items: [{ dishId: 1, setmealId: null, name: '鱼香肉丝', flavor: null, quantity: 1,
      unitPriceCent: 7900, subtotalCent: 7900, image: null }],
  },
  loading: false, submitting: false, error: '', submissionKey: '',
  preview: vi.fn(), submit: vi.fn(), reset: vi.fn(),
}))

vi.mock('@/api/address', () => addressApi)
vi.mock('@/api/payment', () => ({ beginOrderPayment: vi.fn() }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ isAuthenticated: true }) }))
vi.mock('@/stores/cart', () => ({ useCartStore: () => ({ items: [{ key: '1' }], totalCount: 1, markSubmitted: vi.fn() }) }))
vi.mock('@/stores/checkout', () => ({ useCheckoutStore: () => checkout }))
vi.mock('@/stores/ui', () => ({ useUiStore: () => ({ openLogin: vi.fn() }) }))
vi.mock('vue-router', () => ({
  RouterLink: { template: '<a><slot /></a>' },
  useRouter: () => ({ replace: vi.fn() }),
}))

import CheckoutView from '@/views/CheckoutView.vue'

describe('权威结算页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    checkout.error = ''
    addressApi.getAddresses.mockResolvedValue([{ id: 3, consignee: '张三', phone: '13800138000',
      detail: '测试路 2 号', isDefault: 1 }])
  })

  it('展示服务端试算的分段配送费与总额', async () => {
    const wrapper = mount(CheckoutView, { global: { stubs: {
      PageScaffold: { template: '<main><slot /></main>' },
      ProductImage: { template: '<div />' },
      RouterLink: { template: '<a><slot /></a>' },
    } } })
    await flushPromises()

    expect(checkout.preview).toHaveBeenCalledWith({ addressBookId: 3, deliveryMode: 'IMMEDIATE' })
    expect(wrapper.text()).toContain('配送费（4.2km）¥8.00')
    expect(wrapper.text()).toContain('合计¥88.00')
    expect(wrapper.text()).not.toContain('¥6.00')
    wrapper.unmount()
  })

  it('试算失败时保留页面并提供重新试算按钮', async () => {
    checkout.error = '地图服务暂时不可用，请重试'
    const wrapper = mount(CheckoutView, { global: { stubs: {
      PageScaffold: { template: '<main><slot /></main>' }, ProductImage: { template: '<div />' },
      RouterLink: { template: '<a><slot /></a>' },
    } } })
    await flushPromises()
    expect(wrapper.text()).toContain('地图服务暂时不可用，请重试')
    await wrapper.get('.checkout-error button').trigger('click')
    expect(checkout.preview).toHaveBeenCalled()
    wrapper.unmount()
  })
})
