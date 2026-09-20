/** 验证订单详情严格区分退款失败和退款成功。 */
// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const orderApi = vi.hoisted(() => ({ getOrderDetail: vi.fn(), remindOrder: vi.fn(), repeatOrder: vi.fn() }))
const afterSaleApi = vi.hoisted(() => ({ getOrderTimeline: vi.fn(), getLatestAfterSale: vi.fn(), applyAfterSale: vi.fn() }))

vi.mock('@/api/order', () => orderApi)
vi.mock('@/api/aftersale', () => afterSaleApi)
vi.mock('@/api/payment', () => ({ beginOrderPayment: vi.fn() }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ isAuthenticated: true }) }))
vi.mock('@/stores/cart', () => ({ useCartStore: () => ({ loadRemote: vi.fn() }) }))
vi.mock('@/stores/ui', () => ({ useUiStore: () => ({ openCart: vi.fn(), openLogin: vi.fn() }) }))
vi.mock('vue-router', () => ({
  RouterLink: { template: '<a><slot /></a>' },
  useRoute: () => ({ params: { id: '7' } }),
  useRouter: () => ({ push: vi.fn() }),
}))

import OrderDetailView from '@/views/OrderDetailView.vue'

describe('订单退款状态', () => {
  beforeEach(() => {
    orderApi.getOrderDetail.mockResolvedValue({
      id: 7, number: 'O7', status: 6, payStatus: 1, payMethod: 1, amount: 88,
      orderTime: '2026-09-21 10:00', checkoutTime: null, estimatedDeliveryTime: null,
      deliveryTime: null, deliveryStatus: 1, packAmount: 1, tablewareNumber: 1,
      tablewareStatus: 0, remark: null, phone: '13800138000', address: '测试路', consignee: '张三',
      cancelReason: '用户取消', rejectionReason: null, orderDishes: null, orderDetailList: [],
    })
    afterSaleApi.getOrderTimeline.mockResolvedValue([])
    afterSaleApi.getLatestAfterSale.mockResolvedValue({
      id: 2, orderId: 7, requestType: 'CANCELLATION', reason: '行程变化', status: 'REFUND_FAILED',
      refundNo: 'REF-7', refundAmountCent: 8800, refundStatus: 'FAILED', reviewReason: null,
    })
  })

  it('退款失败时显示恢复提示且不显示已退款', async () => {
    const wrapper = mount(OrderDetailView, { global: { stubs: {
      PageScaffold: { template: '<main><slot name="action"/><slot /></main>' },
      OrderActions: { template: '<div />' }, OrderTimeline: { template: '<div />' },
      AfterSaleDialog: { template: '<div />' }, ProductImage: { template: '<div />' },
      RouterLink: { template: '<a><slot /></a>' },
    } } })
    await flushPromises()

    expect(wrapper.text()).toContain('退款失败')
    expect(wrapper.text()).toContain('系统会自动重试')
    expect(wrapper.text()).not.toContain('已退款')
    wrapper.unmount()
  })
})
