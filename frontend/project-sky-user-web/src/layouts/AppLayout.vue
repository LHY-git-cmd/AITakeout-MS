<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { CircleUserRound, History, House, LogIn, MapPin, ShoppingBag, X } from '@lucide/vue'
import LoginDialog from '@/components/LoginDialog.vue'
import CartPanel from '@/components/CartPanel.vue'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { useUiStore } from '@/stores/ui'
import { orderSocket } from '@/services/orderSocket'

const route = useRoute()
const uiStore = useUiStore()
const authStore = useAuthStore()
const cartStore = useCartStore()

const navigation = [
  { label: '点餐', to: '/', icon: House, match: ['menu', 'checkout', 'payment-result'] },
  { label: '订单', to: '/orders', icon: History, match: ['orders', 'order-detail'] },
  { label: '我的', to: '/profile', icon: CircleUserRound, match: ['profile', 'addresses'] },
]

const pageTitle = computed(() => String(route.meta.title ?? '在线点餐'))
const isActive = (names: string[]) => names.includes(String(route.name))

function handleUnauthorized() {
  authStore.logout()
  uiStore.openLogin()
}

onMounted(() => {
  window.addEventListener('sky:unauthorized', handleUnauthorized)
  void authStore.restoreSession()
  if (authStore.token) orderSocket.connect(authStore.token)
})

onBeforeUnmount(() => {
  window.removeEventListener('sky:unauthorized', handleUnauthorized)
  orderSocket.disconnect()
})

watch(() => authStore.token, (token, previousToken) => {
  if (token) {
    void cartStore.initialize(true)
    orderSocket.connect(token)
  } else {
    orderSocket.disconnect()
    if (previousToken || !cartStore.items.length) cartStore.switchToGuest()
  }
}, { immediate: true })
</script>

<template>
  <div class="app-shell">
    <header class="site-header">
      <div class="site-header__inner">
        <RouterLink class="brand" to="/" aria-label="苍穹外卖首页">
          <span class="brand__mark">苍</span>
          <span class="brand__name">苍穹外卖</span>
        </RouterLink>

        <nav class="desktop-nav" aria-label="主导航">
          <RouterLink
            v-for="item in navigation"
            :key="item.to"
            :to="item.to"
            :class="['desktop-nav__link', { 'is-active': isActive(item.match) }]"
          >
            {{ item.label }}
          </RouterLink>
        </nav>

        <div class="header-actions">
          <RouterLink v-if="authStore.isAuthenticated" class="account-link" to="/profile">
            <CircleUserRound :size="18" aria-hidden="true" />
            <span>{{ authStore.displayName }}</span>
          </RouterLink>
          <button v-else class="account-link" type="button" @click="uiStore.openLogin">
            <LogIn :size="18" aria-hidden="true" />
            <span>登录</span>
          </button>
          <RouterLink class="location-link" to="/addresses">
            <MapPin :size="18" aria-hidden="true" />
            <span>收货地址</span>
          </RouterLink>
          <button class="cart-button" type="button" aria-label="打开购物车" @click="uiStore.openCart">
            <ShoppingBag :size="20" aria-hidden="true" />
            <span>购物车</span>
            <span class="cart-button__count">{{ cartStore.totalCount }}</span>
          </button>
        </div>
      </div>
    </header>

    <div class="mobile-titlebar">
      <h1>{{ pageTitle }}</h1>
    </div>

    <main class="page-content">
      <RouterView />
    </main>

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
      </RouterLink>
    </nav>
  </div>
</template>
