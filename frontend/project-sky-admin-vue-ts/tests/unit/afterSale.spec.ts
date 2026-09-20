import Vue from 'vue'
import ElementUI from 'element-ui'
import { mount } from '@vue/test-utils'
import AfterSaleDetail from '@/views/afterSale/detail.vue'
import { getAfterSale, reviewAfterSale } from '@/api/afterSale'

Vue.use(ElementUI)

jest.mock('@/api/afterSale', () => ({ getAfterSale: jest.fn(), reviewAfterSale: jest.fn(), retryRefund: jest.fn() }))

const record = { id: 7, requestNo: 'AS-7', orderId: 20, userId: 9, requestType: 'CANCELLATION',
  reason: '行程变化', status: 'PENDING', createTime: '2026-09-21T10:00:00' }

async function flushPromises() { await new Promise(resolve => setTimeout(resolve, 0)) }

describe('AfterSaleDetail', () => {
  beforeEach(() => { jest.clearAllMocks(); (getAfterSale as jest.Mock).mockResolvedValue({ data: { data: record } }) })

  it('拒绝时必须填写原因', async() => {
    const wrapper = mount(AfterSaleDetail, { mocks: { $route: { params: { id: '7' } }, $router: { push: jest.fn() } } })
    await flushPromises()
    await wrapper.find('[data-test=reject]').trigger('click')
    await Vue.nextTick()
    await wrapper.find('[data-test=reject-submit]').trigger('click')
    await Vue.nextTick()

    expect(wrapper.text()).toContain('请填写拒绝原因')
    expect(reviewAfterSale).not.toHaveBeenCalled()
    wrapper.destroy()
  })
})
