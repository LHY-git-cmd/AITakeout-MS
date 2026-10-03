<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Bot, LoaderCircle, RotateCcw, Send, Sparkles } from '@lucide/vue'
import AgentCartChangeCard from '@/components/agent/AgentCartChangeCard.vue'
import AgentConfirmationDialog from '@/components/agent/AgentConfirmationDialog.vue'
import AgentFallback from '@/components/agent/AgentFallback.vue'
import AgentOrderStatusCard from '@/components/agent/AgentOrderStatusCard.vue'
import AgentRecommendationCards from '@/components/agent/AgentRecommendationCards.vue'
import { useAgentStore, type AgentChatBlock } from '@/stores/agent'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { useUiStore } from '@/stores/ui'
import type { MenuProduct } from '@/api/menu'

const route = useRoute()
const props = withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })
const agentStore = useAgentStore()
const authStore = useAuthStore()
const cartStore = useCartStore()
const uiStore = useUiStore()
const draft = ref('')
const feed = ref<HTMLElement | null>(null)
const confirmationBusy = ref(false)
const cardFeedback = ref('')

const suggestions = ['推荐两个人吃的清淡套餐，预算80元', '查看我的最近订单', '现在营业吗？']
const pendingConfirmation = computed(() =>
  agentStore.blocks.find((block) => block.kind === 'confirmation' && block.status === 'pending'))
const lastUserMessage = computed(() =>
  [...agentStore.blocks].reverse().find((block) => block.kind === 'message' && block.role === 'user')?.text ?? '')

function clientContext() {
  return {
    page: props.embedded ? 'menu' : 'assistant',
    source_path: props.embedded ? route.fullPath : String(route.query.from ?? '/assistant'),
    locale: navigator.language,
  }
}

function citationLabel(value: unknown) {
  const citation = value && typeof value === 'object' ? value as Record<string, unknown> : {}
  const name = String(citation.file_name ?? '公共知识')
  const version = citation.document_version == null ? '' : ` · v${citation.document_version}`
  return `${name}${version}`
}

async function send(message = draft.value) {
  const value = message.trim()
  if (!value || !authStore.isAuthenticated || agentStore.running) return
  draft.value = ''
  await agentStore.send(value, clientContext())
}

function handleComposerKey(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    void send()
  }
}

async function addProduct(item: Record<string, unknown>) {
  const type = item.productType === 'setmeal' ? 'setmeal' : 'dish'
  const baseProduct = {
    id: Number(item.id),
    categoryId: Number(item.categoryId ?? 0),
    name: String(item.name ?? '推荐商品'),
    price: Number(item.price ?? 0),
    image: item.image ? String(item.image) : null,
    description: item.description ? String(item.description) : null,
  }
  const product: MenuProduct = type === 'dish'
    ? { ...baseProduct, productType: 'dish', flavors: [] }
    : { ...baseProduct, productType: 'setmeal' }
  try {
    await cartStore.add(product)
    cardFeedback.value = `${product.name}已加入购物车`
  } catch {
    cardFeedback.value = '加购失败，请稍后重试'
  }
}

async function decide(block: AgentChatBlock, approved: boolean) {
  confirmationBusy.value = true
  try {
    await agentStore.decide(block, approved)
  } finally {
    confirmationBusy.value = false
  }
}

async function scrollToLatest() {
  await nextTick()
  feed.value?.scrollTo({ top: feed.value.scrollHeight, behavior: 'smooth' })
}

onMounted(() => {
  if (authStore.isAuthenticated) void agentStore.initialize()
})
watch(() => authStore.isAuthenticated, (authenticated) => {
  if (authenticated) void agentStore.initialize()
})
watch(() => agentStore.blocks.map((block) => `${block.id}:${block.text ?? ''}`).join('|'), scrollToLatest)
</script>

<template>
  <section :class="['agent-page', { 'is-embedded': embedded }]">
    <header v-if="!embedded" class="agent-page__header">
      <span><Sparkles :size="21" aria-hidden="true" /></span>
      <div><h1>饱饱助手</h1><p>菜品推荐、购物车和本人订单查询</p></div>
      <button v-if="agentStore.blocks.length" type="button" title="新建对话" @click="agentStore.resetConversation">
        <RotateCcw :size="18" aria-hidden="true" />新对话
      </button>
    </header>

    <div v-if="!authStore.isAuthenticated" class="agent-auth-state">
      <Bot :size="42" aria-hidden="true" />
      <h2>登录后开始对话</h2>
      <p>登录用于保护你的购物车、订单和售后信息，饱饱不会要求你提供密码。</p>
      <button type="button" @click="uiStore.openLogin">登录 / 注册</button>
    </div>

    <template v-else>
      <div ref="feed" class="agent-feed" aria-live="polite" aria-label="与饱饱助手的对话">
        <div v-if="!agentStore.blocks.length" class="agent-welcome">
          <span><Bot :size="28" aria-hidden="true" /></span>
          <h2>今天想吃点什么？</h2>
          <p>告诉我人数、预算和口味，我会先查询实时可售商品，再给你推荐。</p>
          <div>
            <button v-for="suggestion in suggestions" :key="suggestion" type="button" @click="send(suggestion)">{{ suggestion }}</button>
          </div>
        </div>

        <template v-for="block in agentStore.blocks" :key="block.id">
          <p v-if="block.kind === 'message'" :class="['agent-message', `is-${block.role}`]">{{ block.text }}</p>
          <AgentRecommendationCards v-else-if="block.kind === 'recommendations'" :items="block.items ?? []" :notices="block.notices" :excluded-items="block.excludedItems" @add="addProduct" />
          <AgentCartChangeCard v-else-if="block.kind === 'cart'" :data="block.data" />
          <AgentOrderStatusCard v-else-if="block.kind === 'order'" :tool-name="block.toolName" :data="block.data" />
          <section v-else-if="block.kind === 'citations'" class="agent-citations" aria-label="知识引用">
            <strong>参考资料</strong>
            <ul><li v-for="(citation, index) in (Array.isArray(block.data) ? block.data : [])" :key="index">
              {{ citationLabel(citation) }}
            </li></ul>
          </section>
          <AgentFallback v-else-if="block.kind === 'fallback'" :message="block.text" @retry="send(lastUserMessage)" />
        </template>

        <div v-if="agentStore.running" class="agent-progress" role="status">
          <LoaderCircle class="spin" :size="18" aria-hidden="true" />
          {{ agentStore.activeTool ? '正在查询实时业务信息…' : '饱饱正在思考…' }}
        </div>
      </div>

      <p class="sr-only" aria-live="polite">{{ cardFeedback }}</p>
      <form class="agent-composer" @submit.prevent="send()">
        <label class="sr-only" for="agent-message">给饱饱助手发送消息</label>
        <textarea id="agent-message" v-model="draft" rows="1" maxlength="4000" placeholder="问菜品、购物车或我的订单…"
          :disabled="agentStore.running" @keydown="handleComposerKey" />
        <button type="submit" :disabled="!draft.trim() || agentStore.running" aria-label="发送消息">
          <Send :size="20" aria-hidden="true" />
        </button>
        <small>AI 可能出错，价格、库存和订单状态以结构化卡片及业务页面为准。</small>
      </form>
    </template>

    <AgentConfirmationDialog v-if="pendingConfirmation" :data="pendingConfirmation.data" :busy="confirmationBusy"
      @approve="decide(pendingConfirmation, true)" @reject="decide(pendingConfirmation, false)" />
  </section>
</template>

<style scoped>
.agent-page { display: grid; width: min(100%, 900px); height: calc(100vh - var(--header-height) - 84px); min-height: 560px; margin: 0 auto; grid-template-rows: auto minmax(0, 1fr) auto; overflow: hidden; border: 1px solid var(--color-line); border-radius: 16px; background: var(--color-surface); box-shadow: var(--shadow-card); }
.agent-page__header { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 12px; padding: 16px 20px; border-bottom: 1px solid var(--color-line); background: #fbf6ec; }
.agent-page__header > span { display: grid; width: 42px; height: 42px; place-items: center; border-radius: 11px; color: #fff; background: var(--color-brand); }
.agent-page__header h1, .agent-page__header p { margin: 0; }
.agent-page__header h1 { font-family: var(--font-serif); font-size: 20px; }
.agent-page__header p { color: var(--color-muted); font-size: 12px; }
.agent-page__header button { display: inline-flex; min-height: 44px; align-items: center; gap: 6px; padding: 0 12px; border: 1px solid var(--color-line); border-radius: 8px; color: var(--color-muted); background: var(--color-surface); cursor: pointer; }
.agent-feed { display: flex; min-height: 0; flex-direction: column; gap: 12px; overflow-y: auto; padding: 22px; background: radial-gradient(90% 55% at 50% 0%, rgb(176 138 69 / 9%), transparent 65%), #fcf8f0; scroll-padding-bottom: 24px; }
.agent-welcome { display: grid; width: min(100%, 520px); margin: auto; justify-items: center; text-align: center; }
.agent-welcome > span { display: grid; width: 58px; height: 58px; place-items: center; border-radius: 16px; color: var(--color-brand-dark); background: var(--color-brand-soft); }
.agent-welcome h2 { margin: 14px 0 6px; font-family: var(--font-serif); }
.agent-welcome p { margin: 0; color: var(--color-muted); }
.agent-welcome > div { display: grid; width: 100%; gap: 8px; margin-top: 20px; }
.agent-welcome button { min-height: 44px; padding: 9px 12px; border: 1px solid var(--color-line); border-radius: 9px; color: var(--color-ink); background: var(--color-surface); cursor: pointer; }
.agent-welcome button:hover { border-color: var(--color-brand); color: var(--color-brand-dark); }
.agent-message { width: fit-content; max-width: min(78%, 640px); margin: 0; padding: 11px 14px; border-radius: 13px; white-space: pre-wrap; overflow-wrap: anywhere; }
.agent-message.is-user { align-self: flex-end; border-bottom-right-radius: 4px; color: #fff; background: var(--color-brand); }
.agent-message.is-assistant { align-self: flex-start; border: 1px solid var(--color-line); border-bottom-left-radius: 4px; background: var(--color-surface); }
.agent-progress { display: flex; width: fit-content; align-items: center; gap: 8px; padding: 9px 12px; border-radius: 9px; color: var(--color-muted); background: var(--color-surface); font-size: 13px; }
.agent-citations { align-self: flex-start; width: min(100%, 640px); padding: 10px 12px; border: 1px solid var(--color-line); border-radius: 9px; color: var(--color-muted); background: var(--color-surface); font-size: 12px; }
.agent-citations strong { color: var(--color-ink); }
.agent-citations ul { margin: 6px 0 0; padding-left: 18px; }
.agent-composer { display: grid; grid-template-columns: minmax(0, 1fr) 48px; gap: 9px; padding: 14px 18px 10px; border-top: 1px solid var(--color-line); background: var(--color-surface); }
.agent-composer textarea { min-height: 48px; max-height: 120px; resize: vertical; padding: 12px 13px; border: 1px solid var(--color-line); border-radius: 10px; color: var(--color-ink); background: #fffdf8; outline: none; }
.agent-composer textarea:focus { border-color: var(--color-brand); box-shadow: 0 0 0 3px rgb(176 58 46 / 12%); }
.agent-composer > button { display: grid; width: 48px; height: 48px; padding: 0; place-items: center; border: 0; border-radius: 10px; color: #fff; background: var(--color-brand); cursor: pointer; }
.agent-composer > button:disabled { opacity: .45; cursor: not-allowed; }
.agent-composer small { grid-column: 1 / -1; color: var(--color-muted); text-align: center; }
.agent-auth-state { display: grid; grid-row: 2; width: min(100%, 480px); margin: auto; justify-items: center; padding: 28px; text-align: center; }
.agent-auth-state h2 { margin: 12px 0 5px; }
.agent-auth-state p { margin: 0; color: var(--color-muted); }
.agent-auth-state button { min-height: 46px; margin-top: 20px; padding: 0 22px; border: 0; border-radius: 9px; color: #fff; background: var(--color-brand); font-weight: 750; cursor: pointer; }
.agent-page.is-embedded { width: 100%; height: 100%; min-height: 0; margin: 0; grid-template-rows: minmax(0, 1fr) auto; border: 0; border-radius: 0; box-shadow: none; }
.agent-page.is-embedded .agent-feed { padding: 14px 12px; overscroll-behavior: contain; scrollbar-gutter: stable; }
.agent-page.is-embedded .agent-welcome { align-content: center; padding: 12px 0; }
.agent-page.is-embedded .agent-welcome > span { width: 48px; height: 48px; border-radius: 13px; }
.agent-page.is-embedded .agent-welcome h2 { margin-top: 10px; font-size: 17px; }
.agent-page.is-embedded .agent-welcome p { font-size: 12px; }
.agent-page.is-embedded .agent-welcome > div { margin-top: 14px; }
.agent-page.is-embedded .agent-welcome button { font-size: 12px; }
.agent-page.is-embedded .agent-message { max-width: 90%; font-size: 13px; }
.agent-page.is-embedded .agent-composer { grid-template-columns: minmax(0, 1fr) 44px; padding: 10px; }
.agent-page.is-embedded .agent-composer textarea { min-height: 44px; padding: 10px 11px; font-size: 13px; resize: none; }
.agent-page.is-embedded .agent-composer > button { width: 44px; height: 44px; }
.agent-page.is-embedded .agent-composer small { font-size: 10px; }
.agent-page.is-embedded .agent-auth-state { grid-row: 1 / -1; padding: 20px; }
.agent-page.is-embedded .agent-auth-state h2 { font-size: 18px; }
.agent-page.is-embedded .agent-auth-state p { font-size: 12px; }
@media (max-width: 767px) { .agent-page { width: calc(100% + 32px); height: calc(100dvh - 54px - var(--mobile-nav-height) - 14px); min-height: 0; margin: -14px -16px 0; border-width: 0; border-radius: 0; } .agent-page__header { padding: 12px 16px; } .agent-page__header button { width: 44px; padding: 0; justify-content: center; font-size: 0; } .agent-feed { padding: 16px; } .agent-message { max-width: 88%; } .agent-composer { padding: 10px 12px calc(8px + env(safe-area-inset-bottom)); } }
</style>
