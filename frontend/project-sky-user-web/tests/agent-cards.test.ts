/** 验证结构化商品卡片和确认弹窗不依赖Markdown提取业务字段。 */
// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AgentConfirmationDialog from '../src/components/agent/AgentConfirmationDialog.vue'
import AgentRecommendationCards from '../src/components/agent/AgentRecommendationCards.vue'

describe('Agent结构化卡片', () => {
  it('直接使用结构化商品ID、价格和按钮事件', async () => {
    const item = { id: 17, productType: 'dish', name: '清蒸鱼', price: 38, image: null }
    const wrapper = mount(AgentRecommendationCards, {
      props: { items: [item] },
      global: { stubs: { ProductImage: { template: '<div class="product-image" />' } } },
    })

    expect(wrapper.text()).toContain('清蒸鱼')
    expect(wrapper.text()).toContain('¥38.00')
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('add')?.[0]).toEqual([item])
  })

  it('确认操作只能通过弹窗按钮决策', async () => {
    const wrapper = mount(AgentConfirmationDialog, {
      props: { data: { operation: 'cancel_order', order_id: 9 } },
    })

    expect(wrapper.attributes('role')).toBe('presentation')
    expect(wrapper.get('[role="alertdialog"]').attributes('aria-modal')).toBe('true')
    await wrapper.findAll('button').at(-1)?.trigger('click')
    expect(wrapper.emitted('approve')).toHaveLength(1)
  })
})
