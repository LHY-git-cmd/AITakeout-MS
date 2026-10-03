/**
 * 验证菜单页 AI 面板直接承载完整对话，而不是只提供跳转入口。
 */
// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

const agentStore = {
  blocks: [],
  running: false,
  activeTool: '',
  initialize: vi.fn(),
  send: vi.fn(),
  decide: vi.fn(),
  resetConversation: vi.fn(),
}

vi.mock('@/stores/agent', () => ({ useAgentStore: () => agentStore }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ isAuthenticated: true }) }))
vi.mock('@/stores/cart', () => ({ useCartStore: () => ({ add: vi.fn() }) }))
vi.mock('@/stores/ui', () => ({ useUiStore: () => ({ openLogin: vi.fn() }) }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ fullPath: '/', query: {} }),
  useRouter: () => ({}),
  RouterLink: { template: '<a><slot /></a>' },
}))

import AiChatPanel from '@/components/AiChatPanel.vue'
import AgentView from '@/views/AgentView.vue'

const agentStubs = {
  AgentRecommendationCards: true,
  AgentCartChangeCard: true,
  AgentOrderStatusCard: true,
  AgentFallback: true,
  AgentConfirmationDialog: true,
}

describe('菜单页 AI 对话面板', () => {
  it('嵌入模式隐藏独立页标题并保留消息输入框', () => {
    const wrapper = mount(AgentView, {
      props: { embedded: true },
      global: { stubs: agentStubs },
    })

    expect(wrapper.classes()).toContain('is-embedded')
    expect(wrapper.find('.agent-page__header').exists()).toBe(false)
    expect(wrapper.get('textarea').attributes('placeholder')).toContain('问菜品')
  })

  it('AI 侧栏内直接渲染对话输入框', () => {
    const wrapper = mount(AiChatPanel, {
      props: { width: 320 },
      global: { stubs: agentStubs },
    })

    expect(wrapper.get('.ai-chat-panel textarea').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('开始对话')
  })
})
