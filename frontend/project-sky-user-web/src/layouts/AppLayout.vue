<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { Bell, Bot, CircleUserRound, History, House, ShoppingBag, UtensilsCrossed, X } from '@lucide/vue'
import LoginDialog from '@/components/LoginDialog.vue'
import CartPanel from '@/components/CartPanel.vue'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { useUiStore } from '@/stores/ui'
import { orderSocket } from '@/services/orderSocket'
import { useAccountStore } from '@/stores/account'
import { useNotificationStore } from '@/stores/notification'

const route = useRoute()
const uiStore = useUiStore()
const authStore = useAuthStore()
const accountStore = useAccountStore()
const cartStore = useCartStore()
const notificationStore = useNotificationStore()

const navigation = [
  { label: '点餐', to: '/', icon: House, match: ['menu', 'checkout', 'payment-result'] },
  { label: '订单', to: '/orders', icon: History, match: ['orders', 'order-detail'] },
  { label: '通知', to: '/notifications', icon: Bell, match: ['notifications', 'after-sales'] },
  { label: '我的', to: '/profile', icon: CircleUserRound, match: ['profile', 'addresses', 'wallet'] },
]

const pageTitle = computed(() => String(route.meta.title ?? '在线点餐'))
const showFloatingCart = computed(() => String(route.name) === 'menu')
const showAgentEntry = computed(() => String(route.name) !== 'assistant')
const isActive = (names: string[]) => names.includes(String(route.name))

function handleUnauthorized() {
  authStore.clearSession()
  uiStore.openLogin()
}

function handleRefreshedSession(event: Event) {
  authStore.setSession((event as CustomEvent<Parameters<typeof authStore.setSession>[0]>).detail)
}

function handleNotification() {
  void notificationStore.catchUp()
}

function handleCartChanged() {
  if (authStore.isAuthenticated) void cartStore.loadRemote()
}

onMounted(() => {
  window.addEventListener('sky:unauthorized', handleUnauthorized)
  window.addEventListener('sky:session-refreshed', handleRefreshedSession)
  window.addEventListener('sky:notification', handleNotification)
  window.addEventListener('sky:socket-connected', handleNotification)
  window.addEventListener('sky:cart-changed', handleCartChanged)
  void authStore.restoreSession()
  if (authStore.token) orderSocket.connect(authStore.token)
})

onBeforeUnmount(() => {
  window.removeEventListener('sky:unauthorized', handleUnauthorized)
  window.removeEventListener('sky:session-refreshed', handleRefreshedSession)
  window.removeEventListener('sky:notification', handleNotification)
  window.removeEventListener('sky:socket-connected', handleNotification)
  window.removeEventListener('sky:cart-changed', handleCartChanged)
  orderSocket.disconnect()
})

watch(() => authStore.token, (token, previousToken) => {
  if (token) {
    void cartStore.initialize(true)
    void notificationStore.refresh()
    orderSocket.connect(token)
  } else {
    orderSocket.disconnect()
    accountStore.reset()
    notificationStore.reset()
    if (previousToken || !cartStore.items.length) cartStore.switchToGuest()
  }
}, { immediate: true })
</script>

<template>
  <div class="app-shell">
    <header class="site-header">
      <div class="site-header__inner">
        <RouterLink class="brand" to="/" aria-label="智能点餐平台首页">
            <span class="brand__mark" aria-hidden="true"><UtensilsCrossed :size="21" /></span>
          <span class="brand__name">智能点餐平台</span>
        </RouterLink>

        <nav class="desktop-nav" aria-label="主导航">
          <template v-for="(item, index) in navigation" :key="item.to">
            <RouterLink
              :to="item.to"
              :class="['desktop-nav__link', { 'is-active': isActive(item.match) }]"
            >
              {{ item.label }}
              <span v-if="item.to === '/notifications' && notificationStore.unreadCount" class="nav-badge" :aria-label="`${notificationStore.unreadCount} 条未读通知`">{{ Math.min(notificationStore.unreadCount, 99) }}</span>
            </RouterLink>
            <button
              v-if="index === 0"
              class="desktop-nav__link desktop-nav__link--disabled"
              type="button"
              aria-label="分类功能暂未开放"
              disabled
            >
              <span>分类</span>
            </button>
          </template>
        </nav>

        <div aria-hidden="true" />
      </div>
    </header>

    <div class="mobile-titlebar">
      <h1>{{ pageTitle }}</h1>
    </div>

    <main class="page-content">
      <RouterView />
    </main>

    <button
      v-if="showFloatingCart"
      class="floating-cart"
      type="button"
      aria-label="打开购物车"
      @click="uiStore.openCart"
    >
      <ShoppingBag :size="26" aria-hidden="true" />
      <span v-if="cartStore.totalCount" class="floating-cart__count">{{ cartStore.totalCount }}</span>
    </button>

    <RouterLink
      v-if="showAgentEntry"
      class="agent-fab"
      :to="{ name: 'assistant', query: { from: route.fullPath } }"
      aria-label="打开饱饱助手"
    >
      <Bot :size="25" aria-hidden="true" />
      <span>问饱饱</span>
    </RouterLink>

    <Transition name="drawer">
      <div v-if="uiStore.cartOpen" class="drawer-layer" role="presentation" @click.self="uiStore.closeCart">
        <aside class="cart-drawer" role="dialog" aria-modal="true" aria-labelledby="cart-drawer-title">
          <div class="cart-drawer__header">
            <h2 id="cart-drawer-title"><ShoppingBag :size="21" /> 购物车</h2>
            <button type="button" aria-label="关闭购物车" title="关闭" @click="uiStore.closeCart">
              <X :size="21" aria-hidden="true" />
            </button>
          </div>
          <CartPanel />
        </aside>
      </div>
    </Transition>

    <LoginDialog />

    <nav class="mobile-nav" aria-label="主导航">
      <RouterLink
        v-for="item in navigation"
        :key="item.to"
        :to="item.to"
        :class="['mobile-nav__link', { 'is-active': isActive(item.match) }]"
      >
        <component :is="item.icon" :size="22" aria-hidden="true" />
        <span>{{ item.label }}</span>
        <span v-if="item.to === '/notifications' && notificationStore.unreadCount" class="nav-badge" :aria-label="`${notificationStore.unreadCount} 条未读通知`">{{ Math.min(notificationStore.unreadCount, 99) }}</span>
      </RouterLink>
    </nav>
  </div>
</template>
